package relay

import (
	"context"
	"testing"
	"time"

	"github.com/coder/websocket"

	"nanobot/relay/internal/proto"
)

// A client that uploads faster than the agent can read must lose only its own stream. Before this
// behaviour existed, the relay's full outbound queue closed the whole data channel, which dropped
// every other stream sharing it (including the phone's long-lived chat WebSocket).
func TestFullQueueDropsOnlyThatStream(t *testing.T) {
	r, srv := newTestRelay(t, Config{SendQueueBytes: 64 << 10, SendQueueFrames: 16})
	agent := dialPeer(t, srv.URL, proto.RoleAgent, "queue-full", "tok")
	waitAgentReady(t, r, "queue-full")
	agent.data.SetReadLimit(proto.MaxFrameSize)
	client := dialPeer(t, srv.URL, proto.RoleClient, "queue-full", "tok")

	writeFrame(t, client.data, proto.Frame{Type: proto.FrameOpen, StreamID: 1})
	expectFrame(t, agent.data, proto.FrameOpen, 1)

	// The agent stops reading, so the relay's queue towards it fills. Push far more than the queue
	// plus the socket buffers can hold.
	payload := make([]byte, 200<<10)
	for i := 0; i < 60; i++ {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		err := client.data.Write(ctx, websocket.MessageBinary,
			encodeFrame(t, proto.Frame{Type: proto.FrameData, StreamID: 1, Payload: payload}))
		cancel()
		if err != nil {
			break
		}
	}

	frame := readUntil(t, client.data, func(f proto.Frame) bool {
		return f.Type == proto.FrameClose && f.StreamID == 1
	})
	if len(frame.Payload) != 1 || frame.Payload[0] != proto.CloseRelayUnavailable {
		t.Fatalf("close reason = %v, want relay unavailable", frame.Payload)
	}
	if !r.agentOnline("queue-full") {
		t.Fatal("a full outbound queue killed the agent's whole data channel")
	}

	// Replace the congested agent with a fresh one so the rest of the test is not stuck behind the
	// backlog the old connection already accepted.
	agent = dialPeer(t, srv.URL, proto.RoleAgent, "queue-full", "tok")
	waitAgentReady(t, r, "queue-full")
	agent.data.SetReadLimit(proto.MaxFrameSize)

	writeFrame(t, client.data, proto.Frame{Type: proto.FrameOpen, StreamID: 2})
	expectFrame(t, agent.data, proto.FrameOpen, 2)
	writeFrame(t, agent.data, proto.Frame{Type: proto.FrameAck, StreamID: 2})
	expectFrame(t, client.data, proto.FrameAck, 2)

	writeFrame(t, client.data, proto.Frame{Type: proto.FrameData, StreamID: 2, Payload: []byte("still here")})
	if got := expectFrame(t, agent.data, proto.FrameData, 2); string(got.Payload) != "still here" {
		t.Fatalf("stream 2 payload = %q", got.Payload)
	}
}

func encodeFrame(t *testing.T, f proto.Frame) []byte {
	t.Helper()
	b, err := proto.EncodeFrame(f)
	if err != nil {
		t.Fatal(err)
	}
	return b
}

// readUntil reads data frames until want matches one. It is safe to time out here: the test fails
// instead of continuing with a disconnected socket.
func readUntil(t *testing.T, conn *websocket.Conn, want func(proto.Frame) bool) proto.Frame {
	t.Helper()
	deadline := time.Now().Add(testTimeout)
	for time.Now().Before(deadline) {
		ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
		typ, data, err := conn.Read(ctx)
		cancel()
		if err != nil {
			t.Fatalf("read frame: %v", err)
		}
		if typ != websocket.MessageBinary {
			continue
		}
		frame, err := proto.DecodeFrame(data)
		if err != nil {
			t.Fatalf("decode frame: %v", err)
		}
		if want(frame) {
			return frame
		}
	}
	t.Fatal("expected frame never arrived")
	return proto.Frame{}
}
