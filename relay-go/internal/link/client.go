// Package link implements nanobot-link: the program a user runs on their own PC.
//
// It dials out to a relay (so the PC needs no inbound port), waits for paired phones, and forwards
// each stream to the local nanobot gateway. It never exposes an arbitrary target: every stream is
// pinned to the gateway address discovered from the nanobot config or given on the command line.
package link

import (
	"context"
	"crypto/ed25519"
	"encoding/base64"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"sync"
	"time"

	"github.com/coder/websocket"

	"nanobot/relay/internal/proto"
)

const (
	connectTimeout = 5 * time.Second
	writeTimeout   = 20 * time.Second
	readChunk      = 16 << 10
	// perStreamQueue bounds buffered writes towards the gateway. A phone that keeps pushing after
	// this is stalled, so the stream is closed instead of growing without bound.
	perStreamQueue = 64
	sendQueue      = 256
)

// Config is everything the client needs. The identity fields come from State.
type Config struct {
	Relay       string
	ServerID    string
	ClientToken string
	PairKey     []byte
	// RelayKey is the relay-wide admission secret for `shared-key` mode.
	RelayKey string
	// SignKey is the Ed25519 private key for `signed` admission mode.
	SignKey ed25519.PrivateKey

	Locator *GatewayLocator
	Log     *slog.Logger

	// OnConnected fires every time both relay channels are usable. Tests use it; the CLI uses it
	// to print a one-line status change.
	OnConnected func()
}

// Client keeps one relay session alive, reconnecting with backoff.
type Client struct {
	cfg  Config
	log  *slog.Logger
	ctrl string

	mu      sync.Mutex
	streams map[uint64]*stream
}

// NewClient validates the configuration and prepares the relay URLs.
func NewClient(cfg Config) (*Client, error) {
	if cfg.Locator == nil {
		return nil, errors.New("a gateway locator is required")
	}
	if cfg.ServerID == "" || cfg.ClientToken == "" {
		return nil, errors.New("the pairing identity is incomplete; rerun with --reset")
	}
	if len(cfg.PairKey) != keyBytes {
		return nil, errInvalidKey
	}
	if cfg.Log == nil {
		cfg.Log = slog.Default()
	}
	control, err := ControlURL(cfg.Relay)
	if err != nil {
		return nil, err
	}
	return &Client{
		cfg:     cfg,
		log:     cfg.Log,
		ctrl:    control,
		streams: make(map[uint64]*stream),
	}, nil
}

// Run connects and serves until ctx is cancelled. It never returns a reconnect error: a dropped
// relay is normal, and retrying is the whole point of the program.
func (c *Client) Run(ctx context.Context) {
	attempt := 0
	for {
		err := c.serve(ctx)
		c.closeAllStreams()
		if ctx.Err() != nil {
			return
		}
		attempt++
		delay := backoff(attempt)
		if err != nil {
			c.log.Warn("relay connection lost", "err", err, "retry_in", delay.String())
		} else {
			c.log.Info("relay connection closed", "retry_in", delay.String())
		}
		select {
		case <-ctx.Done():
			return
		case <-time.After(delay):
		}
	}
}

func (c *Client) serve(ctx context.Context) error {
	control, sid, err := c.dialControl(ctx)
	if err != nil {
		return err
	}
	defer control.CloseNow()

	dataURL, err := DataURL(c.ctrl, c.cfg.ServerID, proto.RoleAgent)
	if err != nil {
		return err
	}
	dialCtx, cancelDial := context.WithTimeout(ctx, connectTimeout)
	data, _, err := websocket.Dial(dialCtx, dataURL, &websocket.DialOptions{
		// The session id is a capability. Keep it out of the URL: proxies log URLs.
		HTTPHeader: http.Header{"X-Nanobot-Session": []string{sid}},
	})
	cancelDial()
	if err != nil {
		return fmt.Errorf("could not open the data channel: %w", err)
	}
	data.SetReadLimit(proto.MaxFrameSize)
	defer data.CloseNow()

	sessionCtx, cancel := context.WithCancel(ctx)
	defer cancel()

	writer := newFrameWriter(data, c.log)
	defer writer.stop()

	controlErr := make(chan error, 1)
	go func() {
		controlErr <- watchControl(sessionCtx, control, c.log)
	}()

	c.log.Info("connected to the relay", "relay", c.ctrl)
	if c.cfg.OnConnected != nil {
		c.cfg.OnConnected()
	}

	readErr := make(chan error, 1)
	go func() {
		readErr <- c.readFrames(sessionCtx, writer)
	}()

	select {
	case err := <-controlErr:
		cancel()
		return err
	case err := <-readErr:
		cancel()
		return err
	case <-ctx.Done():
		return nil
	}
}

// dialControl authenticates and returns the session id for the data channel.
func (c *Client) dialControl(ctx context.Context) (*websocket.Conn, string, error) {
	dialCtx, cancel := context.WithTimeout(ctx, connectTimeout)
	defer cancel()
	conn, _, err := websocket.Dial(dialCtx, c.ctrl, &websocket.DialOptions{})
	if err != nil {
		return nil, "", fmt.Errorf("could not reach the relay: %w", err)
	}
	conn.SetReadLimit(proto.MaxControlFrame)

	hello := proto.Message{
		T:        proto.MsgHello,
		V:        proto.Version,
		Role:     proto.RoleAgent,
		ServerID: c.cfg.ServerID,
		Token:    c.cfg.ClientToken,
		Key:      c.cfg.RelayKey,
	}
	if c.cfg.SignKey != nil {
		hello.Sig = base64.RawURLEncoding.EncodeToString(
			ed25519.Sign(c.cfg.SignKey, []byte(c.cfg.ServerID)),
		)
	}
	payload, err := proto.EncodeMessage(hello)
	if err != nil {
		_ = conn.CloseNow()
		return nil, "", err
	}
	writeCtx, cancelWrite := context.WithTimeout(ctx, writeTimeout)
	err = conn.Write(writeCtx, websocket.MessageText, payload)
	cancelWrite()
	if err != nil {
		_ = conn.CloseNow()
		return nil, "", fmt.Errorf("could not send hello: %w", err)
	}

	readCtx, cancelRead := context.WithTimeout(ctx, connectTimeout)
	typ, reply, err := conn.Read(readCtx)
	cancelRead()
	if err != nil {
		_ = conn.CloseNow()
		return nil, "", fmt.Errorf("the relay did not answer: %w", err)
	}
	if typ != websocket.MessageText {
		_ = conn.CloseNow()
		return nil, "", errors.New("the relay sent a non-text control frame")
	}
	message, err := proto.DecodeMessage(reply)
	if err != nil {
		_ = conn.CloseNow()
		return nil, "", err
	}
	switch message.T {
	case proto.MsgReady:
		if message.SID == "" {
			_ = conn.CloseNow()
			return nil, "", errors.New("the relay did not issue a session")
		}
		return conn, message.SID, nil
	case proto.MsgError:
		_ = conn.CloseNow()
		return nil, "", fmt.Errorf("the relay refused this PC: %s", message.Message)
	default:
		_ = conn.CloseNow()
		return nil, "", fmt.Errorf("unexpected relay reply %q", message.T)
	}
}

// watchControl keeps the control channel honest. The agent has no business receiving anything but
// an error or a close after ready, but reading is what detects a dead connection.
func watchControl(ctx context.Context, conn *websocket.Conn, log *slog.Logger) error {
	for {
		typ, payload, err := conn.Read(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			return fmt.Errorf("control channel closed: %w", err)
		}
		if typ != websocket.MessageText {
			continue
		}
		message, err := proto.DecodeMessage(payload)
		if err != nil {
			continue
		}
		if message.T == proto.MsgError {
			log.Error("relay refused the session", "code", message.Code, "message", message.Message)
			return fmt.Errorf("relay error: %s", message.Message)
		}
	}
}

func (c *Client) readFrames(ctx context.Context, writer *frameWriter) error {
	for {
		typ, payload, err := writer.conn.Read(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			return fmt.Errorf("data channel closed: %w", err)
		}
		if typ != websocket.MessageBinary {
			continue
		}
		frame, err := proto.DecodeFrame(payload)
		if err != nil {
			return fmt.Errorf("invalid frame: %w", err)
		}
		switch frame.Type {
		case proto.FrameOpen:
			c.openStream(writer, frame.StreamID)
		case proto.FrameData:
			c.deliver(frame)
		case proto.FrameClose:
			c.closeFromRelay(frame.StreamID)
		case proto.FrameAck, proto.FrameWindow:
			// The relay never sends these to an agent; ignore them for forward compatibility.
		}
	}
}

// openStream registers a stream and starts dialling the gateway. It returns immediately: a dial can
// take seconds, and blocking the read loop would stall every other stream.
func (c *Client) openStream(writer *frameWriter, id uint64) {
	s := &stream{
		id:        id,
		writer:    writer,
		toGateway: make(chan []byte, perStreamQueue),
		done:      make(chan struct{}),
	}
	c.mu.Lock()
	if _, exists := c.streams[id]; exists {
		c.mu.Unlock()
		writer.send(proto.CloseFrame(id, proto.CloseProtocol))
		return
	}
	c.streams[id] = s
	c.mu.Unlock()

	go c.runStream(s)
}

func (c *Client) runStream(s *stream) {
	target := c.cfg.Locator.Resolve()
	if target == nil {
		c.log.Warn("rejected a stream: the nanobot gateway port was not found",
			"stream", s.id, "hint", "check channels.websocket.port in the nanobot config or pass --port")
		c.finish(s, proto.CloseGatewayUnreachable, true)
		return
	}

	conn, err := net.DialTimeout("tcp", target.Addr(), connectTimeout)
	if err != nil {
		c.log.Warn("could not reach the nanobot gateway",
			"stream", s.id, "gateway", target.Addr(), "err", err)
		c.finish(s, proto.CloseGatewayUnreachable, true)
		return
	}
	if tcp, ok := conn.(*net.TCPConn); ok {
		_ = tcp.SetNoDelay(true)
	}
	if !s.attach(conn) {
		// The session or the stream ended while the dial was in flight.
		_ = conn.Close()
		return
	}

	if !s.writer.send(proto.Frame{Type: proto.FrameAck, StreamID: s.id}) {
		c.finish(s, proto.CloseNormal, false)
		return
	}
	c.log.Debug("stream opened", "stream", s.id, "gateway", target.Addr())

	go c.pumpGateway(s, conn)

	for {
		select {
		case <-s.done:
			return
		case chunk := <-s.toGateway:
			_ = conn.SetWriteDeadline(time.Now().Add(writeTimeout))
			if _, err := conn.Write(chunk); err != nil {
				c.log.Debug("gateway write failed", "stream", s.id, "err", err)
				c.finish(s, proto.CloseNormal, true)
				return
			}
		}
	}
}

// pumpGateway forwards gateway bytes to the phone, encrypted.
func (c *Client) pumpGateway(s *stream, conn net.Conn) {
	buffer := make([]byte, readChunk)
	for {
		read, err := conn.Read(buffer)
		if read > 0 {
			packet, encErr := encrypt(c.cfg.PairKey, buffer[:read])
			if encErr != nil {
				c.log.Error("could not encrypt a gateway frame", "err", encErr)
				c.finish(s, proto.CloseProtocol, true)
				return
			}
			if !s.writer.send(proto.Frame{
				Type:     proto.FrameData,
				StreamID: s.id,
				Payload:  packet,
			}) {
				c.finish(s, proto.CloseNormal, false)
				return
			}
		}
		if err != nil {
			c.finish(s, proto.CloseNormal, true)
			return
		}
	}
}

// deliver sends decrypted phone bytes to the gateway. The per-stream channel keeps ordering; a
// phone that outruns the gateway closes only its own stream.
func (c *Client) deliver(frame proto.Frame) {
	c.mu.Lock()
	s := c.streams[frame.StreamID]
	c.mu.Unlock()
	if s == nil {
		return
	}
	plaintext, err := decrypt(c.cfg.PairKey, frame.Payload)
	if err != nil {
		c.log.Warn("dropped an undecryptable frame", "stream", frame.StreamID, "err", err)
		c.finish(s, proto.CloseProtocol, true)
		return
	}
	select {
	case s.toGateway <- plaintext:
	default:
		c.log.Warn("the gateway is not keeping up", "stream", frame.StreamID)
		c.finish(s, proto.CloseQuota, true)
	}
}

func (c *Client) closeFromRelay(id uint64) {
	c.mu.Lock()
	s := c.streams[id]
	c.mu.Unlock()
	if s == nil {
		return
	}
	c.finish(s, proto.CloseNormal, false)
}

// finish tears a stream down exactly once. notify tells the phone why it ended; it is false when
// the phone or the relay already knows.
func (c *Client) finish(s *stream, reason byte, notify bool) {
	s.closeOnce.Do(func() {
		close(s.done)
		s.closeConn()
		c.mu.Lock()
		if current, ok := c.streams[s.id]; ok && current == s {
			delete(c.streams, s.id)
		}
		c.mu.Unlock()
		if notify {
			s.writer.send(proto.CloseFrame(s.id, reason))
		}
	})
}

func (c *Client) closeAllStreams() {
	c.mu.Lock()
	streams := make([]*stream, 0, len(c.streams))
	for _, s := range c.streams {
		streams = append(streams, s)
	}
	c.streams = make(map[uint64]*stream)
	c.mu.Unlock()
	for _, s := range streams {
		s.closeOnce.Do(func() {
			close(s.done)
			s.closeConn()
		})
	}
}

type stream struct {
	id     uint64
	writer *frameWriter

	toGateway chan []byte
	done      chan struct{}
	closeOnce sync.Once

	mu   sync.Mutex
	conn net.Conn
}

// attach stores the gateway connection unless the stream has already been torn down. The check and
// the store share the mutex closeConn takes, so a dial that finishes late can never be orphaned.
func (s *stream) attach(conn net.Conn) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	select {
	case <-s.done:
		return false
	default:
	}
	s.conn = conn
	return true
}

func (s *stream) closeConn() {
	s.mu.Lock()
	conn := s.conn
	s.conn = nil
	s.mu.Unlock()
	if conn != nil {
		_ = conn.Close()
	}
}

// frameWriter serialises writes to one data channel and bounds the queue. Without it a slow relay
// would either block the stream readers or grow memory without limit.
type frameWriter struct {
	conn  *websocket.Conn
	log   *slog.Logger
	queue chan []byte
	done  chan struct{}
	once  sync.Once
}

func newFrameWriter(conn *websocket.Conn, log *slog.Logger) *frameWriter {
	w := &frameWriter{
		conn:  conn,
		log:   log,
		queue: make(chan []byte, sendQueue),
		done:  make(chan struct{}),
	}
	go w.loop()
	return w
}

func (w *frameWriter) send(frame proto.Frame) bool {
	payload, err := proto.EncodeFrame(frame)
	if err != nil {
		return false
	}
	select {
	case <-w.done:
		return false
	case w.queue <- payload:
		return true
	default:
		w.log.Warn("dropping the relay connection: the send queue is full")
		w.fail()
		return false
	}
}

func (w *frameWriter) loop() {
	for {
		select {
		case <-w.done:
			return
		case payload := <-w.queue:
			select {
			case <-w.done:
				return
			default:
			}
			ctx, cancel := context.WithTimeout(context.Background(), writeTimeout)
			err := w.conn.Write(ctx, websocket.MessageBinary, payload)
			cancel()
			if err != nil {
				w.log.Debug("relay write failed", "err", err)
				w.fail()
				return
			}
		}
	}
}

// fail closes the socket so the read loop returns promptly.
func (w *frameWriter) fail() {
	w.once.Do(func() {
		close(w.done)
		_ = w.conn.CloseNow()
	})
}

func (w *frameWriter) stop() {
	w.once.Do(func() { close(w.done) })
}

func backoff(attempt int) time.Duration {
	steps := []time.Duration{
		1 * time.Second,
		2 * time.Second,
		4 * time.Second,
		8 * time.Second,
		15 * time.Second,
		30 * time.Second,
		60 * time.Second,
	}
	if attempt < 1 {
		attempt = 1
	}
	if attempt > len(steps) {
		return steps[len(steps)-1]
	}
	return steps[attempt-1]
}
