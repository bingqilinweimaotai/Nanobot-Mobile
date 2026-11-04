package relay

import (
	"testing"
	"time"
)

func TestByteQueueFIFO(t *testing.T) {
	q := newByteQueue(1024, 4)
	if !q.push([]byte("a")) || !q.push([]byte("b")) {
		t.Fatal("push rejected")
	}
	first, ok := q.pop()
	if !ok || string(first) != "a" {
		t.Fatalf("first = %q ok=%v", first, ok)
	}
	second, ok := q.pop()
	if !ok || string(second) != "b" {
		t.Fatalf("second = %q ok=%v", second, ok)
	}
}

func TestByteQueueFrameLimit(t *testing.T) {
	q := newByteQueue(1024, 2)
	if !q.push([]byte("a")) || !q.push([]byte("b")) {
		t.Fatal("two frames rejected")
	}
	if q.push([]byte("c")) {
		t.Fatal("frame limit not enforced")
	}
}

func TestByteQueueByteLimit(t *testing.T) {
	q := newByteQueue(10, 100)
	if !q.push(make([]byte, 10)) {
		t.Fatal("first frame rejected")
	}
	if q.push(make([]byte, 1)) {
		t.Fatal("byte limit not enforced")
	}
	q.pop()
	if !q.push(make([]byte, 1)) {
		t.Fatal("space was not reclaimed after pop")
	}
}

func TestByteQueueCloseUnblocksPop(t *testing.T) {
	q := newByteQueue(10, 1)
	done := make(chan struct{})
	go func() {
		if _, ok := q.pop(); ok {
			t.Error("pop returned a frame after close")
		}
		close(done)
	}()
	time.Sleep(20 * time.Millisecond)
	q.close()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("pop stayed blocked after close")
	}
	if q.push([]byte("x")) {
		t.Fatal("push accepted after close")
	}
}
