package relay

import (
	"strings"
	"time"
)

// Config holds the relay's runtime knobs. Zero values are replaced with the defaults below.
type Config struct {
	// PathPrefix mounts the relay under a prefix such as "/nanobot".
	PathPrefix string
	// TrustProxy makes the relay read the client address from the last X-Forwarded-For entry.
	// Enable it only when exactly one trusted proxy (Caddy) sits in front.
	TrustProxy bool

	MaxStreamsPerHost int
	MaxClientsPerHost int

	SendQueueBytes  int
	SendQueueFrames int
	WriteTimeout    time.Duration
	PingInterval    time.Duration
	PongTimeout     time.Duration
	HelloTimeout    time.Duration
}

func (c Config) withDefaults() Config {
	if c.MaxStreamsPerHost <= 0 {
		c.MaxStreamsPerHost = 64
	}
	if c.MaxClientsPerHost <= 0 {
		c.MaxClientsPerHost = 16
	}
	if c.SendQueueBytes <= 0 {
		// Four mebibytes is roughly 170 ms at a 200 Mbit/s peak, enough to absorb a few round
		// trips of jitter on a cross-border link. Queues are allocated on demand, not reserved.
		c.SendQueueBytes = 4 << 20
	}
	if c.SendQueueFrames <= 0 {
		c.SendQueueFrames = 512
	}
	if c.WriteTimeout <= 0 {
		c.WriteTimeout = 30 * time.Second
	}
	if c.PingInterval <= 0 {
		c.PingInterval = 25 * time.Second
	}
	if c.PongTimeout <= 0 {
		c.PongTimeout = 25 * time.Second
	}
	if c.HelloTimeout <= 0 {
		c.HelloTimeout = 10 * time.Second
	}
	return c
}

func (c Config) controlPath() string { return joinPath(c.PathPrefix, "/control") }
func (c Config) dataPath() string    { return joinPath(c.PathPrefix, "/data") }

func joinPath(prefix, suffix string) string {
	prefix = strings.TrimSuffix(strings.TrimSpace(prefix), "/")
	if prefix == "" {
		return suffix
	}
	if !strings.HasPrefix(prefix, "/") {
		prefix = "/" + prefix
	}
	return prefix + suffix
}
