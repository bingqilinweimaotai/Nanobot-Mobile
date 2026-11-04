package limits

import (
	"testing"
	"time"
)

type clock struct{ t time.Time }

func (c *clock) now() time.Time          { return c.t }
func (c *clock) advance(d time.Duration) { c.t = c.t.Add(d) }
func newClock() *clock                   { return &clock{t: time.Unix(1_700_000_000, 0)} }

func TestHandshakeRate(t *testing.T) {
	c := newClock()
	l := New(Config{HandshakesPerMinute: 3, Now: c.now})
	for i := 0; i < 3; i++ {
		if !l.AllowHandshake("1.2.3.4") {
			t.Fatalf("handshake %d rejected", i)
		}
	}
	if l.AllowHandshake("1.2.3.4") {
		t.Fatal("fourth handshake allowed")
	}
	if !l.AllowHandshake("5.6.7.8") {
		t.Fatal("a different IP was affected")
	}
	c.advance(time.Minute)
	if !l.AllowHandshake("1.2.3.4") {
		t.Fatal("window did not reset")
	}
}

func TestPreAuthConcurrency(t *testing.T) {
	c := newClock()
	l := New(Config{MaxPreAuth: 2, Now: c.now})
	if !l.Acquire("ip") || !l.Acquire("ip") {
		t.Fatal("first two slots rejected")
	}
	if l.Acquire("ip") {
		t.Fatal("third slot allowed")
	}
	l.Release("ip")
	if !l.Acquire("ip") {
		t.Fatal("released slot not reusable")
	}
}

func TestReleaseWithoutAcquireIsSafe(t *testing.T) {
	l := New(Config{Now: newClock().now})
	l.Release("never-seen")
}

func TestEntriesAreSwept(t *testing.T) {
	c := newClock()
	l := New(Config{TTL: time.Minute, Now: c.now})
	l.AllowHandshake("a")
	l.AllowHandshake("b")
	if len(l.entries) != 2 {
		t.Fatalf("entries = %d", len(l.entries))
	}
	c.advance(2 * time.Minute)
	l.AllowHandshake("c")
	if len(l.entries) != 1 {
		t.Fatalf("entries after sweep = %d, want 1", len(l.entries))
	}
}

func TestSweepKeepsActiveConnections(t *testing.T) {
	c := newClock()
	l := New(Config{TTL: time.Minute, Now: c.now})
	l.AllowHandshake("active")
	l.Acquire("active")
	c.advance(2 * time.Minute)
	l.AllowHandshake("other")
	if _, ok := l.entries["active"]; !ok {
		t.Fatal("an in-flight connection was swept")
	}
}
