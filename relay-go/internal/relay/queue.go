package relay

import (
	"sync"
)

type byteQueue struct {
	mu     sync.Mutex
	cond   *sync.Cond
	items  [][]byte
	bytes  int
	maxB   int
	maxN   int
	closed bool
}

func newByteQueue(maxBytes, maxFrames int) *byteQueue {
	q := &byteQueue{maxB: maxBytes, maxN: maxFrames}
	q.cond = sync.NewCond(&q.mu)
	return q
}

// push enqueues a frame without ever blocking the caller. A full queue is a failure signal: the
// reader must not stall behind a slow peer, otherwise one bad connection would stop every stream
// sharing it.
func (q *byteQueue) push(b []byte) bool {
	q.mu.Lock()
	defer q.mu.Unlock()
	if q.closed {
		return false
	}
	if len(q.items) >= q.maxN || q.bytes+len(b) > q.maxB {
		return false
	}
	q.items = append(q.items, b)
	q.bytes += len(b)
	q.cond.Signal()
	return true
}

func (q *byteQueue) pop() ([]byte, bool) {
	q.mu.Lock()
	defer q.mu.Unlock()
	for len(q.items) == 0 && !q.closed {
		q.cond.Wait()
	}
	if len(q.items) == 0 {
		return nil, false
	}
	b := q.items[0]
	q.items[0] = nil
	q.items = q.items[1:]
	q.bytes -= len(b)
	return b, true
}

func (q *byteQueue) close() {
	q.mu.Lock()
	q.closed = true
	q.items = nil
	q.bytes = 0
	q.mu.Unlock()
	q.cond.Broadcast()
}
