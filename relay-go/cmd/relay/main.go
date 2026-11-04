// Command relay runs the nanobot relay v2 server.
//
// The relay terminates no TLS of its own: run it behind Caddy (or another proxy) and give agents
// the wss:// URL. It keeps no state on disk and never inspects tunnel payloads.
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	"nanobot/relay/internal/admission"
	"nanobot/relay/internal/limits"
	"nanobot/relay/internal/relay"
)

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, "nanobot-relay:", err)
		os.Exit(1)
	}
}

func run() error {
	listen := flag.String("listen", envString("RELAY_LISTEN", "127.0.0.1:8787"),
		"address to listen on")
	path := flag.String("path", envString("RELAY_PATH", ""),
		"mount the relay under a path prefix, e.g. /nanobot")
	publicURL := flag.String("public-url", envString("RELAY_PUBLIC_URL", ""),
		"URL printed for agents, e.g. wss://relay.example.com/nanobot")
	mode := flag.String("mode", envString("RELAY_MODE", "shared-key"),
		"admission mode: open, shared-key, or signed")
	trustProxy := flag.Bool("trust-proxy", envBool("RELAY_TRUST_PROXY", false),
		"read the client IP from the last X-Forwarded-For entry (only behind one trusted proxy)")
	maxStreams := flag.Int("max-streams", envInt("RELAY_MAX_STREAMS_PER_HOST", 64),
		"concurrent streams allowed per server id")
	maxClients := flag.Int("max-clients", envInt("RELAY_MAX_CLIENTS_PER_HOST", 16),
		"concurrent client connections allowed per server id")
	sendQueueBytes := flag.Int("send-queue-bytes", envInt("RELAY_SEND_QUEUE_BYTES", 4<<20),
		"outbound queue limit per connection, in bytes")
	sendQueueFrames := flag.Int("send-queue-frames", envInt("RELAY_SEND_QUEUE_FRAMES", 512),
		"outbound queue limit per connection, in frames")
	writeTimeout := flag.Duration("write-timeout", envDuration("RELAY_WRITE_TIMEOUT", 30*time.Second),
		"deadline for one WebSocket write")
	pingInterval := flag.Duration("ping-interval", envDuration("RELAY_PING_INTERVAL", 25*time.Second),
		"interval between keepalive pings")
	pongTimeout := flag.Duration("pong-timeout", envDuration("RELAY_PONG_TIMEOUT", 25*time.Second),
		"how long to wait for a pong before dropping a peer")
	debug := flag.Bool("debug", envBool("RELAY_DEBUG", false), "verbose logging")
	flag.Parse()

	level := slog.LevelInfo
	if *debug {
		level = slog.LevelDebug
	}
	log := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: level}))

	adm, err := buildAdmission(strings.ToLower(*mode), log)
	if err != nil {
		return err
	}

	cfg := relay.Config{
		PathPrefix:        *path,
		TrustProxy:        *trustProxy,
		MaxStreamsPerHost: *maxStreams,
		MaxClientsPerHost: *maxClients,
		SendQueueBytes:    *sendQueueBytes,
		SendQueueFrames:   *sendQueueFrames,
		WriteTimeout:      *writeTimeout,
		PingInterval:      *pingInterval,
		PongTimeout:       *pongTimeout,
	}
	lim := limits.New(limits.Config{
		HandshakesPerMinute: envInt("RELAY_HANDSHAKES_PER_MINUTE", 60),
		MaxPreAuth:          envInt("RELAY_MAX_PREAUTH_PER_IP", 20),
	})
	server := relay.New(cfg, adm, lim, log)

	httpServer := &http.Server{
		Addr:              *listen,
		Handler:           server.Handler(),
		ReadHeaderTimeout: 10 * time.Second,
		IdleTimeout:       120 * time.Second,
		ErrorLog:          slog.NewLogLogger(log.Handler(), slog.LevelWarn),
	}

	controlPath := "/control"
	dataPath := "/data"
	if prefix := strings.TrimSuffix(strings.TrimSpace(*path), "/"); prefix != "" {
		if !strings.HasPrefix(prefix, "/") {
			prefix = "/" + prefix
		}
		controlPath = prefix + controlPath
		dataPath = prefix + dataPath
	}

	log.Info("nanobot relay starting",
		"listen", *listen, "control", controlPath, "data", dataPath, "mode", *mode)
	if *publicURL != "" {
		log.Info("give agents this URL", "relay", *publicURL)
	}
	if _, ok := adm.(admission.Open); ok {
		log.Warn("admission is OPEN: anyone who can reach this port may use the relay")
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	go func() {
		<-ctx.Done()
		log.Info("shutting down")
		// WebSocket handlers are hijacked connections, so close them before asking the HTTP
		// server to drain.
		server.Shutdown()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = httpServer.Shutdown(shutdownCtx)
	}()

	if err := httpServer.ListenAndServe(); !errors.Is(err, http.ErrServerClosed) {
		return err
	}
	return nil
}

func buildAdmission(mode string, log *slog.Logger) (admission.Admission, error) {
	switch mode {
	case "open":
		return admission.Open{}, nil
	case "shared-key":
		key := os.Getenv("RELAY_KEY")
		adm, err := admission.NewSharedKey(key)
		if err != nil {
			return nil, errors.New(
				"RELAY_MODE=shared-key requires RELAY_KEY (use --mode open for local development)")
		}
		return adm, nil
	case "signed":
		pub := os.Getenv("RELAY_PUBLIC_KEY")
		adm, err := admission.NewSigned(pub)
		if err != nil {
			return nil, fmt.Errorf("RELAY_MODE=signed: %w", err)
		}
		return adm, nil
	default:
		return nil, fmt.Errorf("unknown admission mode %q", mode)
	}
}

func envString(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

func envInt(name string, fallback int) int {
	value := os.Getenv(name)
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func envBool(name string, fallback bool) bool {
	value := os.Getenv(name)
	if value == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func envDuration(name string, fallback time.Duration) time.Duration {
	value := os.Getenv(name)
	if value == "" {
		return fallback
	}
	parsed, err := time.ParseDuration(value)
	if err != nil {
		return fallback
	}
	return parsed
}
