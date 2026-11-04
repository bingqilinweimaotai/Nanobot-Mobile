package relay

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"
	"time"

	"github.com/coder/websocket"

	"nanobot/relay/internal/admission"
	"nanobot/relay/internal/limits"
	"nanobot/relay/internal/proto"
)

const testTimeout = 5 * time.Second

func newTestRelay(t *testing.T, cfg Config) (*Relay, *httptest.Server) {
	t.Helper()
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	lim := limits.New(limits.Config{HandshakesPerMinute: 10_000, MaxPreAuth: 10_000})
	r := New(cfg, admission.Open{}, lim, log)
	srv := httptest.NewServer(r.Handler())
	t.Cleanup(func() {
		r.Shutdown()
		srv.Close()
	})
	return r, srv
}

type testPeer struct {
	ctrl *websocket.Conn
	data *websocket.Conn
	sid  string
}

func dialPeer(t *testing.T, base, role, serverID, token string) *testPeer {
	t.Helper()
	ctrl, first := dialControl(t, base, role, serverID, token, "")
	if first.T != proto.MsgReady {
		t.Fatalf("expected ready, got %+v", first)
	}
	data := dialData(t, base, first.SID, serverID, role)
	return &testPeer{ctrl: ctrl, data: data, sid: first.SID}
}

func dialControl(t *testing.T, base, role, serverID, token, key string) (*websocket.Conn, proto.Message) {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()
	conn, _, err := websocket.Dial(ctx, base+"/control", nil)
	if err != nil {
		t.Fatalf("dial control: %v", err)
	}
	t.Cleanup(func() { _ = conn.CloseNow() })

	hello, err := proto.EncodeMessage(proto.Message{
		T:        proto.MsgHello,
		V:        proto.Version,
		Role:     role,
		ServerID: serverID,
		Token:    token,
		Key:      key,
	})
	if err != nil {
		t.Fatal(err)
	}
	writeCtx, writeCancel := context.WithTimeout(context.Background(), testTimeout)
	defer writeCancel()
	if err := conn.Write(writeCtx, websocket.MessageText, hello); err != nil {
		t.Fatalf("write hello: %v", err)
	}
	return conn, readControl(t, conn)
}

func dialData(t *testing.T, base, sid, serverID, role string) *websocket.Conn {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()
	endpoint := fmt.Sprintf("%s/data?serverId=%s&role=%s",
		base, url.QueryEscape(serverID), url.QueryEscape(role))
	opts := &websocket.DialOptions{
		HTTPHeader: http.Header{"X-Nanobot-Session": []string{sid}},
	}
	conn, _, err := websocket.Dial(ctx, endpoint, opts)
	if err != nil {
		t.Fatalf("dial data: %v", err)
	}
	t.Cleanup(func() { _ = conn.CloseNow() })
	return conn
}

func readControl(t *testing.T, conn *websocket.Conn) proto.Message {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()
	typ, data, err := conn.Read(ctx)
	if err != nil {
		t.Fatalf("read control: %v", err)
	}
	if typ != websocket.MessageText {
		t.Fatalf("control frame type = %v", typ)
	}
	msg, err := proto.DecodeMessage(data)
	if err != nil {
		t.Fatalf("decode control: %v", err)
	}
	return msg
}

func readFrame(t *testing.T, conn *websocket.Conn) proto.Frame {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()
	typ, data, err := conn.Read(ctx)
	if err != nil {
		t.Fatalf("read frame: %v", err)
	}
	if typ != websocket.MessageBinary {
		t.Fatalf("data frame type = %v", typ)
	}
	frame, err := proto.DecodeFrame(data)
	if err != nil {
		t.Fatalf("decode frame: %v", err)
	}
	return frame
}

func writeFrame(t *testing.T, conn *websocket.Conn, frame proto.Frame) {
	t.Helper()
	payload, err := proto.EncodeFrame(frame)
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()
	if err := conn.Write(ctx, websocket.MessageBinary, payload); err != nil {
		t.Fatalf("write frame: %v", err)
	}
}

func expectFrame(t *testing.T, conn *websocket.Conn, kind proto.FrameType, streamID uint64) proto.Frame {
	t.Helper()
	frame := readFrame(t, conn)
	if frame.Type != kind || frame.StreamID != streamID {
		t.Fatalf("frame = type %v id %d, want type %v id %d",
			frame.Type, frame.StreamID, kind, streamID)
	}
	return frame
}

// waitAgentReady closes a race between dialing the agent's data channel and the relay marking it
// ready. The Dial call returns as soon as the upgrade completes.
func waitAgentReady(t *testing.T, r *Relay, serverID string) {
	t.Helper()
	deadline := time.Now().Add(testTimeout)
	for time.Now().Before(deadline) {
		if r.agentOnline(serverID) {
			return
		}
		time.Sleep(5 * time.Millisecond)
	}
	t.Fatal("agent data channel never became ready")
}

func TestForwardsStream(t *testing.T) {
	r, srv := newTestRelay(t, Config{})
	agent := dialPeer(t, srv.URL, proto.RoleAgent, "srv-1", "tok")
	waitAgentReady(t, r, "srv-1")
	client := dialPeer(t, srv.URL, proto.RoleClient, "srv-1", "tok")

	writeFrame(t, client.data, proto.Frame{Type: proto.FrameOpen, StreamID: 1})
	expectFrame(t, agent.data, proto.FrameOpen, 1)

	writeFrame(t, agent.data, proto.Frame{Type: proto.FrameAck, StreamID: 1})
	expectFrame(t, client.data, proto.FrameAck, 1)

	writeFrame(t, client.data, proto.Frame{Type: proto.FrameData, StreamID: 1, Payload: []byte("hello")})
	if got := expectFrame(t, agent.data, proto.FrameData, 1); string(got.Payload) != "hello" {
		t.Fatalf("payload = %q", got.Payload)
	}

	writeFrame(t, agent.data, proto.Frame{Type: proto.FrameData, StreamID: 1, Payload: []byte("world")})
	if got := expectFrame(t, client.data, proto.FrameData, 1); string(got.Payload) != "world" {
		t.Fatalf("payload = %q", got.Payload)
	}

	writeFrame(t, client.data, proto.CloseFrame(1, proto.CloseNormal))
	if got := expectFrame(t, agent.data, proto.FrameClose, 1); len(got.Payload) != 1 {
		t.Fatalf("close payload = %v", got.Payload)
	}
}

func TestStreamsAreIndependent(t *testing.T) {
	r, srv := newTestRelay(t, Config{})
	agent := dialPeer(t, srv.URL, proto.RoleAgent, "srv-2", "tok")
	waitAgentReady(t, r, "srv-2")
	client := dialPeer(t, srv.URL, proto.RoleClient, "srv-2", "tok")

	writeFrame(t, client.data, proto.Frame{Type: proto.FrameOpen, StreamID: 1})
	writeFrame(t, client.data, proto.Frame{Type: proto.FrameOpen, StreamID: 2})
	expectFrame(t, agent.data, proto.FrameOpen, 1)
	expectFrame(t, agent.data, proto.FrameOpen, 2)

	writeFrame(t, client.data, proto.Frame{Type: proto.FrameData, StreamID: 1, Payload: []byte("one")})
	writeFrame(t, client.data, proto.Frame{Type: proto.FrameData, StreamID: 2, Payload: []byte("two")})
	if got := expectFrame(t, agent.data, proto.FrameData, 1); string(got.Payload) != "one" {
		t.Fatalf("stream 1 payload = %q", got.Payload)
	}
	if got := expectFrame(t, agent.data, proto.FrameData, 2); string(got.Payload) != "two" {
		t.Fatalf("stream 2 payload = %q", got.Payload)
	}

	writeFrame(t, client.data, proto.CloseFrame(1, proto.CloseNormal))
	expectFrame(t, agent.data, proto.FrameClose, 1)

	// Stream 2 must still work after stream 1 was closed.
	writeFrame(t, agent.data, proto.Frame{Type: proto.FrameData, StreamID: 2, Payload: []byte("still here")})
	if got := expectFrame(t, client.data, proto.FrameData, 2); string(got.Payload) != "still here" {
		t.Fatalf("stream 2 payload = %q", got.Payload)
	}
}

func TestAgentOfflineRejectsOpen(t *testing.T) {
	_, srv := newTestRelay(t, Config{})
	client := dialPeer(t, srv.URL, proto.RoleClient, "srv-3", "tok")

	writeFrame(t, client.data, proto.Frame{Type: proto.FrameOpen, StreamID: 7})
	frame := expectFrame(t, client.data, proto.FrameClose, 7)
	if len(frame.Payload) != 1 || frame.Payload[0] != proto.CloseAgentOffline {
		t.Fatalf("reason = %v", frame.Payload)
	}
}

func TestClientWaitsForAgent(t *testing.T) {
	r, srv := newTestRelay(t, Config{})
	client := dialPeer(t, srv.URL, proto.RoleClient, "srv-4", "tok")

	if msg := readControl(t, client.ctrl); msg.T != proto.MsgPresence || msg.Agent == nil || *msg.Agent {
		t.Fatalf("first presence = %+v", msg)
	}

	agent := dialPeer(t, srv.URL, proto.RoleAgent, "srv-4", "tok")
	waitAgentReady(t, r, "srv-4")

	msg := readControl(t, client.ctrl)
	if msg.T != proto.MsgPresence || msg.Agent == nil || !*msg.Agent {
		t.Fatalf("presence after agent connected = %+v", msg)
	}

	writeFrame(t, client.data, proto.Frame{Type: proto.FrameOpen, StreamID: 1})
	expectFrame(t, agent.data, proto.FrameOpen, 1)
}

func TestTokenMismatchRejected(t *testing.T) {
	_, srv := newTestRelay(t, Config{})
	_ = dialPeer(t, srv.URL, proto.RoleAgent, "srv-5", "token-a")

	_, first := dialControl(t, srv.URL, proto.RoleClient, "srv-5", "token-b", "")
	if first.T != proto.MsgError || first.Code != proto.ErrAuth {
		t.Fatalf("client with a wrong token got %+v", first)
	}

	// A client that connected before the agent must also be rejected when the agent shows up.
	_, first = dialControl(t, srv.URL, proto.RoleClient, "srv-6", "token-c", "")
	if first.T != proto.MsgReady {
		t.Fatalf("early client got %+v", first)
	}
	waiting := dialPeer(t, srv.URL, proto.RoleClient, "srv-6", "token-c")
	// The client is told the agent is offline first; the rejection comes when the agent appears.
	if msg := readControl(t, waiting.ctrl); msg.T != proto.MsgPresence {
		t.Fatalf("expected presence, got %+v", msg)
	}
	_ = dialPeer(t, srv.URL, proto.RoleAgent, "srv-6", "token-d")

	msg := readControl(t, waiting.ctrl)
	if msg.T != proto.MsgError || msg.Code != proto.ErrAuth {
		t.Fatalf("waiting client got %+v", msg)
	}
}

func TestAgentReconnectReplacesOld(t *testing.T) {
	_, srv := newTestRelay(t, Config{})
	first := dialPeer(t, srv.URL, proto.RoleAgent, "srv-7", "tok")
	_ = dialPeer(t, srv.URL, proto.RoleAgent, "srv-7", "tok")

	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()
	if _, _, err := first.ctrl.Read(ctx); err == nil {
		t.Fatal("the replaced agent's control channel stayed open")
	}
}

func TestStreamQuota(t *testing.T) {
	r, srv := newTestRelay(t, Config{MaxStreamsPerHost: 2})
	agent := dialPeer(t, srv.URL, proto.RoleAgent, "srv-8", "tok")
	waitAgentReady(t, r, "srv-8")
	client := dialPeer(t, srv.URL, proto.RoleClient, "srv-8", "tok")

	writeFrame(t, client.data, proto.Frame{Type: proto.FrameOpen, StreamID: 1})
	writeFrame(t, client.data, proto.Frame{Type: proto.FrameOpen, StreamID: 2})
	expectFrame(t, agent.data, proto.FrameOpen, 1)
	expectFrame(t, agent.data, proto.FrameOpen, 2)

	writeFrame(t, client.data, proto.Frame{Type: proto.FrameOpen, StreamID: 3})
	frame := expectFrame(t, client.data, proto.FrameClose, 3)
	if len(frame.Payload) != 1 || frame.Payload[0] != proto.CloseQuota {
		t.Fatalf("reason = %v", frame.Payload)
	}
}

func TestClientQuota(t *testing.T) {
	_, srv := newTestRelay(t, Config{MaxClientsPerHost: 1})
	_ = dialPeer(t, srv.URL, proto.RoleClient, "srv-9", "tok")
	_, first := dialControl(t, srv.URL, proto.RoleClient, "srv-9", "tok", "")
	if first.T != proto.MsgError || first.Code != proto.ErrQuota {
		t.Fatalf("second client got %+v", first)
	}
}

func TestUnknownSessionRejected(t *testing.T) {
	_, srv := newTestRelay(t, Config{})
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()
	opts := &websocket.DialOptions{
		HTTPHeader: http.Header{"X-Nanobot-Session": []string{"forged"}},
	}
	_, resp, err := websocket.Dial(ctx, srv.URL+"/data?serverId=x&role=client", opts)
	if err == nil {
		t.Fatal("a forged session id was accepted")
	}
	if resp != nil && resp.StatusCode != 400 {
		t.Fatalf("status = %d, want 400", resp.StatusCode)
	}
}

func TestControlRequiresHello(t *testing.T) {
	_, srv := newTestRelay(t, Config{})
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()
	conn, _, err := websocket.Dial(ctx, srv.URL+"/control", nil)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.CloseNow()
	if err := conn.Write(ctx, websocket.MessageText, []byte(`{"t":"open"}`)); err != nil {
		t.Fatal(err)
	}
	if _, _, err := conn.Read(ctx); err == nil {
		t.Fatal("a non-hello first frame was accepted")
	}
}

func TestRelayKeyAdmission(t *testing.T) {
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	adm, err := admission.NewSharedKey("relay-secret")
	if err != nil {
		t.Fatal(err)
	}
	r := New(Config{}, adm, limits.New(limits.Config{HandshakesPerMinute: 1000, MaxPreAuth: 1000}), log)
	srv := httptest.NewServer(r.Handler())
	t.Cleanup(func() { r.Shutdown(); srv.Close() })

	_, first := dialControl(t, srv.URL, proto.RoleAgent, "srv-10", "tok", "")
	if first.T != proto.MsgError || first.Code != proto.ErrAuth {
		t.Fatalf("missing relay key got %+v", first)
	}

	_, first = dialControl(t, srv.URL, proto.RoleAgent, "srv-10", "tok", "relay-secret")
	if first.T != proto.MsgReady {
		t.Fatalf("valid relay key got %+v", first)
	}
}
