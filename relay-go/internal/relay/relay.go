// Package relay implements the nanobot relay v2 server.
//
// The relay is a rendezvous and forwarding service. It authenticates two peers (a PC agent and one
// or more phone clients), pairs them by server id, and forwards opaque frames between them. It
// never sees plaintext: data frames are encrypted with a pairing key that only the endpoints hold.
package relay

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/coder/websocket"

	"nanobot/relay/internal/admission"
	"nanobot/relay/internal/limits"
	"nanobot/relay/internal/proto"
)

const shardCount = 64

// Close codes in the private range, mirrored from the design document.
const (
	closeReplaced   = websocket.StatusCode(4001)
	closeBadRequest = websocket.StatusCode(4002)
	closeAuth       = websocket.StatusCode(4003)
	closeQuota      = websocket.StatusCode(4004)
	closeNotFound   = websocket.StatusCode(4005)
)

var (
	errTokenConflict = errors.New("server id is registered with a different token")
	errQuota         = errors.New("quota exceeded")
)

// Relay is the server. It owns no persistent state: restarting it drops connections, and both
// peers reconnect on their own.
type Relay struct {
	cfg       Config
	admission admission.Admission
	limiter   *limits.Limiter
	log       *slog.Logger

	shards    [shardCount]shard
	sidShards [shardCount]sidShard
}

type shard struct {
	mu          sync.RWMutex
	agents      map[string]*peer
	clients     map[string]map[*peer]struct{}
	streams     map[streamKey]*stream
	streamCount map[string]int
}

type sidShard struct {
	mu    sync.RWMutex
	peers map[string]*peer
}

type streamKey struct {
	serverID string
	id       uint64
}

// New builds a relay. admission and limiter must not be nil.
func New(cfg Config, adm admission.Admission, lim *limits.Limiter, log *slog.Logger) *Relay {
	if adm == nil {
		adm = admission.Open{}
	}
	if lim == nil {
		lim = limits.New(limits.Config{})
	}
	if log == nil {
		log = slog.Default()
	}
	r := &Relay{cfg: cfg.withDefaults(), admission: adm, limiter: lim, log: log}
	for i := range r.shards {
		r.shards[i].agents = make(map[string]*peer)
		r.shards[i].clients = make(map[string]map[*peer]struct{})
		r.shards[i].streams = make(map[streamKey]*stream)
		r.shards[i].streamCount = make(map[string]int)
	}
	for i := range r.sidShards {
		r.sidShards[i].peers = make(map[string]*peer)
	}
	return r
}

// Handler returns the HTTP surface: /healthz plus the control and data endpoints.
func (r *Relay) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc(r.cfg.controlPath(), r.ServeControl)
	mux.HandleFunc(r.cfg.dataPath(), r.ServeData)
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})
	return mux
}

// ServeControl authenticates a peer and keeps its control channel open.
func (r *Relay) ServeControl(w http.ResponseWriter, req *http.Request) {
	ip := r.clientIP(req)
	if !r.limiter.AllowHandshake(ip) {
		http.Error(w, "too many handshakes", http.StatusTooManyRequests)
		return
	}
	if !r.limiter.Acquire(ip) {
		http.Error(w, "too many connections", http.StatusTooManyRequests)
		return
	}
	defer r.limiter.Release(ip)

	ws, err := websocket.Accept(w, req, &websocket.AcceptOptions{})
	if err != nil {
		return
	}
	ws.SetReadLimit(proto.MaxControlFrame)

	hello, err := r.readHello(ws)
	if err != nil {
		_ = ws.Close(closeBadRequest, err.Error())
		return
	}
	if hello.V != proto.Version {
		_ = ws.Close(closeBadRequest, "unsupported protocol version")
		return
	}
	if hello.Role != proto.RoleAgent && hello.Role != proto.RoleClient {
		_ = ws.Close(closeBadRequest, "unknown role")
		return
	}
	if hello.ServerID == "" || hello.Token == "" {
		_ = ws.Close(closeBadRequest, "hello needs serverId and token")
		return
	}
	if err := r.admission.Allow(hello.ServerID, hello.Token, hello.Key, hello.Sig); err != nil {
		r.rejectControl(ws, proto.ErrAuth, "relay access denied", closeAuth)
		return
	}

	p := &peer{
		serverID: hello.ServerID,
		token:    hello.Token,
		role:     hello.Role,
		sid:      randomID(16),
	}
	p.control = newWSConn(ws, websocket.MessageText, r.cfg.SendQueueBytes, r.cfg.SendQueueFrames)

	switch p.role {
	case proto.RoleAgent:
		if err := r.registerAgent(p); err != nil {
			r.rejectControl(ws, proto.ErrAuth, err.Error(), closeAuth)
			return
		}
	case proto.RoleClient:
		if err := r.registerClient(p); err != nil {
			code := closeQuota
			kind := proto.ErrQuota
			if errors.Is(err, errTokenConflict) {
				code = closeAuth
				kind = proto.ErrAuth
			}
			r.rejectControl(ws, kind, err.Error(), code)
			return
		}
	}

	r.indexPeer(p)
	// Start the writer only after validation: the failure paths above write their error frame
	// directly to the socket and must not race with a writer goroutine.
	p.control.start(r.cfg.WriteTimeout, r.cfg.PingInterval, r.cfg.PongTimeout)

	ready, _ := proto.EncodeMessage(proto.Ready(p.sid))
	if !p.control.send(ready) {
		r.dropPeer(p, "could not send ready")
		return
	}
	if p.role == proto.RoleClient {
		p.sendPresence(r.agentOnline(p.serverID))
	}

	r.log.Info("peer registered", "role", p.role, "server", shortID(p.serverID))

	defer func() {
		_ = ws.CloseNow()
		r.dropPeer(p, "control channel closed")
	}()

	for {
		typ, _, err := ws.Read(context.Background())
		if err != nil {
			return
		}
		if typ != websocket.MessageText {
			_ = ws.Close(closeBadRequest, "control accepts text frames only")
			return
		}
		// After hello the control channel is relay-to-peer only. Anything else is ignored so a
		// newer client can add fields without breaking an older relay.
	}
}

// ServeData binds a peer's data channel to its authenticated control session.
func (r *Relay) ServeData(w http.ResponseWriter, req *http.Request) {
	query := req.URL.Query()
	// The session id is a short-lived capability. Keep it in a header rather than the query
	// string: reverse proxies log URLs by default, and a logged credential is a leaked one.
	sid := req.Header.Get("X-Nanobot-Session")
	serverID := query.Get("serverId")
	role := query.Get("role")

	p := r.peerBySID(sid)
	if p == nil || p.serverID != serverID || p.role != role || p.closed.Load() {
		http.Error(w, "unknown session", http.StatusBadRequest)
		return
	}

	ws, err := websocket.Accept(w, req, &websocket.AcceptOptions{})
	if err != nil {
		return
	}
	ws.SetReadLimit(proto.MaxFrameSize)

	c := newWSConn(ws, websocket.MessageBinary, r.cfg.SendQueueBytes, r.cfg.SendQueueFrames)
	if !p.setData(c) {
		_ = ws.Close(closeBadRequest, "data channel already attached")
		return
	}
	c.start(r.cfg.WriteTimeout, r.cfg.PingInterval, r.cfg.PongTimeout)

	defer func() {
		_ = ws.CloseNow()
		r.dropPeer(p, "data channel closed")
	}()

	r.dataAttached(p)

	for {
		typ, data, err := ws.Read(context.Background())
		if err != nil {
			return
		}
		if typ != websocket.MessageBinary {
			_ = ws.Close(closeBadRequest, "data accepts binary frames only")
			return
		}
		frame, err := proto.DecodeFrame(data)
		if err != nil {
			_ = ws.Close(closeBadRequest, "invalid frame")
			return
		}
		switch frame.Type {
		case proto.FrameOpen:
			r.handleOpen(p, frame)
		case proto.FrameData, proto.FrameAck, proto.FrameClose, proto.FrameWindow:
			r.route(p, frame)
		}
	}
}

// Shutdown closes every connected peer.
func (r *Relay) Shutdown() {
	var peers []*peer
	for i := range r.sidShards {
		r.sidShards[i].mu.RLock()
		for _, p := range r.sidShards[i].peers {
			peers = append(peers, p)
		}
		r.sidShards[i].mu.RUnlock()
	}
	for _, p := range peers {
		r.dropPeer(p, "relay shutting down")
	}
}

func (r *Relay) readHello(ws *websocket.Conn) (proto.Message, error) {
	ctx, cancel := context.WithTimeout(context.Background(), r.cfg.HelloTimeout)
	defer cancel()
	typ, data, err := ws.Read(ctx)
	if err != nil {
		return proto.Message{}, errors.New("expected a hello frame")
	}
	if typ != websocket.MessageText {
		return proto.Message{}, errors.New("hello must be a text frame")
	}
	msg, err := proto.DecodeMessage(data)
	if err != nil {
		return proto.Message{}, errors.New("hello is not valid JSON")
	}
	if msg.T != proto.MsgHello {
		return proto.Message{}, errors.New("the first frame must be hello")
	}
	return msg, nil
}

func (r *Relay) rejectControl(ws *websocket.Conn, kind, message string, code websocket.StatusCode) {
	ctx, cancel := context.WithTimeout(context.Background(), r.cfg.WriteTimeout)
	defer cancel()
	if payload, err := proto.EncodeMessage(proto.Error(kind, message)); err == nil {
		_ = ws.Write(ctx, websocket.MessageText, payload)
	}
	_ = ws.Close(code, message)
}

func (r *Relay) registerAgent(p *peer) error {
	sh := r.shardFor(p.serverID)

	var old *peer
	var mismatched []*peer
	sh.mu.Lock()
	if current := sh.agents[p.serverID]; current != nil {
		if !sameToken(current.token, p.token) {
			sh.mu.Unlock()
			return errTokenConflict
		}
		old = current
	}
	sh.agents[p.serverID] = p
	if set := sh.clients[p.serverID]; set != nil {
		for c := range set {
			if !sameToken(c.token, p.token) {
				mismatched = append(mismatched, c)
			}
		}
	}
	sh.mu.Unlock()

	if old != nil && old != p {
		r.dropPeer(old, "replaced by a newer agent")
	}
	// Deliver the error before closing the control socket; queued writes can be discarded on close.
	message, _ := proto.EncodeMessage(proto.Error(proto.ErrAuth, "pairing token is no longer valid on this PC"))
	var rejected sync.WaitGroup
	for _, c := range mismatched {
		rejected.Add(1)
		go func(c *peer) {
			defer rejected.Done()
			ctx, cancel := context.WithTimeout(context.Background(), time.Second)
			_ = c.control.ws.Write(ctx, websocket.MessageText, message)
			cancel()
			r.dropPeer(c, "token mismatch")
		}(c)
	}
	rejected.Wait()
	return nil
}

func (r *Relay) registerClient(p *peer) error {
	sh := r.shardFor(p.serverID)
	sh.mu.Lock()
	set := sh.clients[p.serverID]
	if set == nil {
		set = make(map[*peer]struct{})
		sh.clients[p.serverID] = set
	}
	if len(set) >= r.cfg.MaxClientsPerHost {
		sh.mu.Unlock()
		return errQuota
	}
	agent := sh.agents[p.serverID]
	if agent != nil && !sameToken(agent.token, p.token) {
		sh.mu.Unlock()
		return errTokenConflict
	}
	set[p] = struct{}{}
	sh.mu.Unlock()
	return nil
}

func (r *Relay) dataAttached(p *peer) {
	p.dataReady.Store(true)
	if p.role != proto.RoleAgent {
		return
	}
	sh := r.shardFor(p.serverID)
	sh.mu.RLock()
	clients := make([]*peer, 0, len(sh.clients[p.serverID]))
	for c := range sh.clients[p.serverID] {
		clients = append(clients, c)
	}
	sh.mu.RUnlock()
	for _, c := range clients {
		c.sendPresence(true)
	}
}

func (r *Relay) agentOnline(serverID string) bool {
	sh := r.shardFor(serverID)
	sh.mu.RLock()
	agent := sh.agents[serverID]
	sh.mu.RUnlock()
	return agent != nil && agent.dataReady.Load()
}

func (r *Relay) handleOpen(p *peer, frame proto.Frame) {
	if p.role != proto.RoleClient {
		_ = p.sendFrame(proto.CloseFrame(frame.StreamID, proto.CloseProtocol))
		return
	}
	sh := r.shardFor(p.serverID)
	key := streamKey{serverID: p.serverID, id: frame.StreamID}

	sh.mu.Lock()
	agent := sh.agents[p.serverID]
	if agent == nil || !agent.dataReady.Load() {
		sh.mu.Unlock()
		_ = p.sendFrame(proto.CloseFrame(frame.StreamID, proto.CloseAgentOffline))
		return
	}
	if _, exists := sh.streams[key]; exists {
		sh.mu.Unlock()
		_ = p.sendFrame(proto.CloseFrame(frame.StreamID, proto.CloseProtocol))
		return
	}
	if sh.streamCount[p.serverID] >= r.cfg.MaxStreamsPerHost {
		sh.mu.Unlock()
		_ = p.sendFrame(proto.CloseFrame(frame.StreamID, proto.CloseQuota))
		return
	}
	st := &stream{id: frame.StreamID, serverID: p.serverID, agent: agent, client: p}
	sh.streams[key] = st
	sh.streamCount[p.serverID]++
	sh.mu.Unlock()

	if !agent.sendFrame(frame) {
		r.removeStream(st)
		r.log.Warn("stream dropped: the agent could not accept OPEN",
			"server", shortID(p.serverID), "stream", frame.StreamID)
		_ = p.sendFrame(proto.CloseFrame(frame.StreamID, proto.CloseRelayUnavailable))
	}
}

// route forwards a per-stream frame to the opposite peer.
func (r *Relay) route(p *peer, frame proto.Frame) {
	sh := r.shardFor(p.serverID)
	key := streamKey{serverID: p.serverID, id: frame.StreamID}

	sh.mu.RLock()
	st := sh.streams[key]
	sh.mu.RUnlock()
	if st == nil {
		return
	}

	var target *peer
	switch p.role {
	case proto.RoleClient:
		if st.client != p {
			return
		}
		target = st.agent
	case proto.RoleAgent:
		if st.agent != p {
			return
		}
		target = st.client
	default:
		return
	}

	if frame.Type == proto.FrameClose {
		r.removeStream(st)
	}
	if target == nil || !target.sendFrame(frame) {
		r.removeStream(st)
		if target != nil {
			// Usually the peer's outbound queue is full: one stream outran the link. Only this
			// stream is dropped; the data channel and every other stream stay up.
			r.log.Warn("stream dropped: the peer could not accept a frame",
				"role", target.role, "server", shortID(target.serverID), "stream", frame.StreamID)
		}
		if frame.Type != proto.FrameClose {
			_ = p.sendFrame(proto.CloseFrame(frame.StreamID, proto.CloseRelayUnavailable))
		}
	}
}

func (r *Relay) removeStream(st *stream) {
	sh := r.shardFor(st.serverID)
	key := streamKey{serverID: st.serverID, id: st.id}
	sh.mu.Lock()
	if current, ok := sh.streams[key]; ok && current == st {
		delete(sh.streams, key)
		decrementStreamCount(sh, st.serverID)
	}
	sh.mu.Unlock()
}

// dropPeer removes a peer from every table and closes both of its sockets. It is idempotent.
func (r *Relay) dropPeer(p *peer, reason string) {
	p.closeOnce.Do(func() {
		sh := r.shardFor(p.serverID)

		sh.mu.Lock()
		if p.role == proto.RoleAgent {
			if sh.agents[p.serverID] == p {
				delete(sh.agents, p.serverID)
			}
		} else if set := sh.clients[p.serverID]; set != nil {
			delete(set, p)
			if len(set) == 0 {
				delete(sh.clients, p.serverID)
			}
		}

		var affected []*stream
		for key, st := range sh.streams {
			if st.agent == p || st.client == p {
				affected = append(affected, st)
				delete(sh.streams, key)
				decrementStreamCount(sh, p.serverID)
			}
		}

		var clients []*peer
		var online bool
		if p.role == proto.RoleAgent {
			for c := range sh.clients[p.serverID] {
				clients = append(clients, c)
			}
			if next := sh.agents[p.serverID]; next != nil {
				online = next.dataReady.Load()
			}
		}
		sh.mu.Unlock()

		p.closeConns()
		r.unindexPeer(p)

		for _, st := range affected {
			if other := st.other(p); other != nil {
				_ = other.sendFrame(proto.CloseFrame(st.id, proto.CloseRelayUnavailable))
			}
		}
		for _, c := range clients {
			c.sendPresence(online)
		}
		r.log.Info("peer gone", "role", p.role, "server", shortID(p.serverID), "reason", reason)
	})
}

func (r *Relay) shardFor(serverID string) *shard {
	return &r.shards[fnv1a(serverID)%shardCount]
}

func (r *Relay) indexPeer(p *peer) {
	sh := &r.sidShards[fnv1a(p.sid)%shardCount]
	sh.mu.Lock()
	sh.peers[p.sid] = p
	sh.mu.Unlock()
}

func (r *Relay) unindexPeer(p *peer) {
	sh := &r.sidShards[fnv1a(p.sid)%shardCount]
	sh.mu.Lock()
	if sh.peers[p.sid] == p {
		delete(sh.peers, p.sid)
	}
	sh.mu.Unlock()
}

func (r *Relay) peerBySID(sid string) *peer {
	if sid == "" {
		return nil
	}
	sh := &r.sidShards[fnv1a(sid)%shardCount]
	sh.mu.RLock()
	p := sh.peers[sid]
	sh.mu.RUnlock()
	return p
}

func (r *Relay) clientIP(req *http.Request) string {
	if r.cfg.TrustProxy {
		if xff := req.Header.Get("X-Forwarded-For"); xff != "" {
			parts := strings.Split(xff, ",")
			if last := strings.TrimSpace(parts[len(parts)-1]); last != "" {
				return last
			}
		}
	}
	host, _, err := net.SplitHostPort(req.RemoteAddr)
	if err != nil {
		return req.RemoteAddr
	}
	return host
}

func decrementStreamCount(sh *shard, serverID string) {
	switch n := sh.streamCount[serverID]; {
	case n <= 1:
		delete(sh.streamCount, serverID)
	default:
		sh.streamCount[serverID] = n - 1
	}
}

func randomID(bytes int) string {
	buf := make([]byte, bytes)
	if _, err := rand.Read(buf); err != nil {
		panic("crypto/rand is unavailable: " + err.Error())
	}
	return base64.RawURLEncoding.EncodeToString(buf)
}

func shortID(id string) string {
	if len(id) <= 8 {
		return id
	}
	return id[:8]
}

func fnv1a(s string) uint32 {
	const (
		offset = 2166136261
		prime  = 16777619
	)
	h := uint32(offset)
	for i := 0; i < len(s); i++ {
		h ^= uint32(s[i])
		h *= prime
	}
	return h
}
