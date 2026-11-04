package link

import (
	"context"
	"io"
	"log/slog"
	"net"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/coder/websocket"

	"nanobot/relay/internal/admission"
	"nanobot/relay/internal/limits"
	"nanobot/relay/internal/proto"
	relaypkg "nanobot/relay/internal/relay"
)

const itTimeout = 5 * time.Second

func silentLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}

// startEchoGateway stands in for the nanobot gateway: it echoes every byte it receives.
func startEchoGateway(t *testing.T) int {
	t.Helper()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = listener.Close() })
	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			go func() {
				defer conn.Close()
				_, _ = io.Copy(conn, conn)
			}()
		}
	}()
	return listener.Addr().(*net.TCPAddr).Port
}

func startRelay(t *testing.T) *httptest.Server {
	t.Helper()
	server := relaypkg.New(
		relaypkg.Config{},
		admission.Open{},
		limits.New(limits.Config{HandshakesPerMinute: 10_000, MaxPreAuth: 10_000}),
		silentLogger(),
	)
	httpServer := httptest.NewServer(server.Handler())
	t.Cleanup(func() {
		server.Shutdown()
		httpServer.Close()
	})
	return httpServer
}

func wsURL(httpServer *httptest.Server) string {
	return "ws" + strings.TrimPrefix(httpServer.URL, "http")
}

type linkFixture struct {
	client  *Client
	pairKey []byte
}

func startLink(t *testing.T, relayURL string, gatewayPort int) linkFixture {
	t.Helper()
	key, err := generateKey()
	if err != nil {
		t.Fatal(err)
	}
	port := gatewayPort
	locator := NewGatewayLocator(&port, "", filepath.Join(t.TempDir(), "missing.json"))
	connected := make(chan struct{}, 1)
	client, err := NewClient(Config{
		Relay:       relayURL,
		ServerID:    "test-server",
		ClientToken: "test-token",
		PairKey:     key,
		Locator:     locator,
		Log:         silentLogger(),
		OnConnected: func() {
			select {
			case connected <- struct{}{}:
			default:
			}
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	go client.Run(ctx)

	select {
	case <-connected:
	case <-time.After(itTimeout):
		t.Fatal("nanobot-link never reached the relay")
	}
	return linkFixture{client: client, pairKey: key}
}

type phone struct {
	control *websocket.Conn
	data    *websocket.Conn
}

func dialPhone(t *testing.T, base string) *phone {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), itTimeout)
	defer cancel()

	control, _, err := websocket.Dial(ctx, base+"/control", nil)
	if err != nil {
		t.Fatalf("dial control: %v", err)
	}
	t.Cleanup(func() { _ = control.CloseNow() })

	hello, _ := proto.EncodeMessage(proto.Message{
		T:        proto.MsgHello,
		V:        proto.Version,
		Role:     proto.RoleClient,
		ServerID: "test-server",
		Token:    "test-token",
	})
	if err := control.Write(ctx, websocket.MessageText, hello); err != nil {
		t.Fatalf("write hello: %v", err)
	}

	ready := readControl(t, control)
	if ready.T != proto.MsgReady || ready.SID == "" {
		t.Fatalf("ready = %+v", ready)
	}
	waitAgentOnline(t, control)

	dataURL := base + "/data?serverId=test-server&role=client"
	data, _, err := websocket.Dial(ctx, dataURL, &websocket.DialOptions{
		HTTPHeader: http.Header{"X-Nanobot-Session": []string{ready.SID}},
	})
	if err != nil {
		t.Fatalf("dial data: %v", err)
	}
	t.Cleanup(func() { _ = data.CloseNow() })
	return &phone{control: control, data: data}
}

func waitAgentOnline(t *testing.T, control *websocket.Conn) {
	t.Helper()
	deadline := time.Now().Add(itTimeout)
	for time.Now().Before(deadline) {
		ctx, cancel := context.WithDeadline(context.Background(), deadline)
		_, payload, err := control.Read(ctx)
		cancel()
		if err != nil {
			t.Fatalf("waiting for the agent: %v", err)
		}
		message, err := proto.DecodeMessage(payload)
		if err != nil {
			continue
		}
		if message.T == proto.MsgPresence && message.Agent != nil && *message.Agent {
			return
		}
	}
	t.Fatal("the agent never came online")
}

func readControl(t *testing.T, conn *websocket.Conn) proto.Message {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), itTimeout)
	defer cancel()
	_, payload, err := conn.Read(ctx)
	if err != nil {
		t.Fatalf("read control: %v", err)
	}
	message, err := proto.DecodeMessage(payload)
	if err != nil {
		t.Fatalf("decode control: %v", err)
	}
	return message
}

func writeFrame(t *testing.T, conn *websocket.Conn, frame proto.Frame) {
	t.Helper()
	payload, err := proto.EncodeFrame(frame)
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), itTimeout)
	defer cancel()
	if err := conn.Write(ctx, websocket.MessageBinary, payload); err != nil {
		t.Fatalf("write frame: %v", err)
	}
}

func readFrame(t *testing.T, conn *websocket.Conn) proto.Frame {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), itTimeout)
	defer cancel()
	_, payload, err := conn.Read(ctx)
	if err != nil {
		t.Fatalf("read frame: %v", err)
	}
	frame, err := proto.DecodeFrame(payload)
	if err != nil {
		t.Fatalf("decode frame: %v", err)
	}
	return frame
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

func TestForwardsEncryptedStreamToGateway(t *testing.T) {
	gatewayPort := startEchoGateway(t)
	relayServer := startRelay(t)
	fixture := startLink(t, wsURL(relayServer), gatewayPort)
	phoneClient := dialPhone(t, wsURL(relayServer))

	writeFrame(t, phoneClient.data, proto.Frame{Type: proto.FrameOpen, StreamID: 1})
	expectFrame(t, phoneClient.data, proto.FrameAck, 1)

	packet, err := encrypt(fixture.pairKey, []byte("hello gateway"))
	if err != nil {
		t.Fatal(err)
	}
	writeFrame(t, phoneClient.data, proto.Frame{Type: proto.FrameData, StreamID: 1, Payload: packet})

	frame := expectFrame(t, phoneClient.data, proto.FrameData, 1)
	plaintext, err := decrypt(fixture.pairKey, frame.Payload)
	if err != nil {
		t.Fatal(err)
	}
	if string(plaintext) != "hello gateway" {
		t.Fatalf("echo = %q", plaintext)
	}

	writeFrame(t, phoneClient.data, proto.CloseFrame(1, proto.CloseNormal))
}

func TestUndecryptableFrameClosesOnlyThatStream(t *testing.T) {
	gatewayPort := startEchoGateway(t)
	relayServer := startRelay(t)
	startLink(t, wsURL(relayServer), gatewayPort)
	phoneClient := dialPhone(t, wsURL(relayServer))

	writeFrame(t, phoneClient.data, proto.Frame{Type: proto.FrameOpen, StreamID: 4})
	expectFrame(t, phoneClient.data, proto.FrameAck, 4)

	writeFrame(t, phoneClient.data, proto.Frame{
		Type:     proto.FrameData,
		StreamID: 4,
		Payload:  []byte("clearly not AES-GCM"),
	})
	frame := expectFrame(t, phoneClient.data, proto.FrameClose, 4)
	if len(frame.Payload) != 1 || frame.Payload[0] != proto.CloseProtocol {
		t.Fatalf("close payload = %v", frame.Payload)
	}
}

func TestGatewayDownIsReported(t *testing.T) {
	// Reserve a port, then close it so nothing listens there.
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	deadPort := listener.Addr().(*net.TCPAddr).Port
	_ = listener.Close()

	relayServer := startRelay(t)
	startLink(t, wsURL(relayServer), deadPort)
	phoneClient := dialPhone(t, wsURL(relayServer))

	writeFrame(t, phoneClient.data, proto.Frame{Type: proto.FrameOpen, StreamID: 9})
	frame := expectFrame(t, phoneClient.data, proto.FrameClose, 9)
	if len(frame.Payload) != 1 || frame.Payload[0] != proto.CloseGatewayUnreachable {
		t.Fatalf("close payload = %v", frame.Payload)
	}
}

func TestPairingLinkFromStateIsReachable(t *testing.T) {
	// A pairing link built from real state must carry everything the phone needs.
	gatewayPort := startEchoGateway(t)
	relayServer := startRelay(t)
	key, _ := generateKey()
	port := gatewayPort
	offer := PairingOffer{
		V:           PairingVersion,
		Kind:        KindRelay,
		Name:        "Test PC",
		Relay:       wsURL(relayServer),
		ServerID:    "test-server",
		ClientToken: "test-token",
		PairKey:     encodeKey(key),
		Secret:      "secret",
		GatewayPort: &port,
		Platform:    "Linux",
	}
	linkText, err := EncodePairingLink(offer)
	if err != nil {
		t.Fatal(err)
	}
	decoded, err := ParsePairingLink(linkText)
	if err != nil {
		t.Fatal(err)
	}
	if decoded.Relay != offer.Relay || decoded.PairKey != offer.PairKey {
		t.Fatalf("decoded = %+v", decoded)
	}
}
