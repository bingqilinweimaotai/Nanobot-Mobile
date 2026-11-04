// Package limits protects the relay from connection floods without punishing users who share a
// carrier-grade NAT address.
package limits

import (
	"sync"
	"time"
)

// Config controls the per-IP guard rails.
type Config struct {
	// HandshakesPerMinute caps how many WebSocket upgrades one IP may start per minute. It only
	// applies before authentication.
	HandshakesPerMinute int
	// MaxPreAuth caps concurrent unauthenticated connections per IP.
	MaxPreAuth int
	// TTL is how long an idle entry is kept before it is swept.
	TTL time.Duration
	// Now is injectable for tests.
	Now func() time.Time
}

// Limiter is a small fixed-window rate limiter keyed by IP.
//
// Quotas that are meant to be fair per user do not live here: server ids are the unit for that.
// This limiter exists only to absorb floods and scanning.
type Limiter struct {
	cfg Config

	mu      sync.Mutex
	entries map[string]*entry
	lastGC  time.Time
}

type entry struct {
	windowStart time.Time
	handshakes  int
	preauth     int
	lastSeen    time.Time
}

// New builds a limiter. Zero values are replaced with sane defaults.
func New(cfg Config) *Limiter {
	if cfg.HandshakesPerMinute <= 0 {
		cfg.HandshakesPerMinute = 60
	}
	if cfg.MaxPreAuth <= 0 {
		cfg.MaxPreAuth = 20
	}
	if cfg.TTL <= 0 {
		cfg.TTL = time.Minute
	}
	if cfg.Now == nil {
		cfg.Now = time.Now
	}
	return &Limiter{cfg: cfg, entries: make(map[string]*entry)}
}

// AllowHandshake records one upgrade attempt and reports whether it may proceed.
func (l *Limiter) AllowHandshake(ip string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := l.cfg.Now()
	l.sweepLocked(now)
	e := l.entryLocked(ip, now)
	if now.Sub(e.windowStart) >= time.Minute {
		e.windowStart = now
		e.handshakes = 0
	}
	e.lastSeen = now
	if e.handshakes >= l.cfg.HandshakesPerMinute {
		return false
	}
	e.handshakes++
	return true
}

// Acquire reserves one unauthenticated connection slot for ip.
func (l *Limiter) Acquire(ip string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := l.cfg.Now()
	l.sweepLocked(now)
	e := l.entryLocked(ip, now)
	e.lastSeen = now
	if e.preauth >= l.cfg.MaxPreAuth {
		return false
	}
	e.preauth++
	return true
}

// Release returns a slot taken by Acquire. It is safe to call after the entry was swept.
func (l *Limiter) Release(ip string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	e := l.entries[ip]
	if e == nil {
		return
	}
	if e.preauth > 0 {
		e.preauth--
	}
	e.lastSeen = l.cfg.Now()
}

func (l *Limiter) entryLocked(ip string, now time.Time) *entry {
	e := l.entries[ip]
	if e == nil {
		e = &entry{windowStart: now, lastSeen: now}
		l.entries[ip] = e
	}
	return e
}

// sweepLocked drops idle entries a few times per TTL so a flood of unique addresses cannot grow the
// map without bound.
func (l *Limiter) sweepLocked(now time.Time) {
	if now.Sub(l.lastGC) < l.cfg.TTL {
		return
	}
	l.lastGC = now
	for ip, e := range l.entries {
		if e.preauth == 0 && now.Sub(e.lastSeen) > l.cfg.TTL {
			delete(l.entries, ip)
		}
	}
}
