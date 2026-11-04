package relay

import (
	"context"
	"sync"
	"time"

	"github.com/coder/websocket"
)

// wsConn wraps one WebSocket with a bounded outbound queue and a single writer goroutine.
//
// coder/websocket allows concurrent reads and writes, but serialising writes here gives us a place
// to apply the queue bound and the write deadline.
type wsConn struct {
	ws      *websocket.Conn
	msgType websocket.MessageType
	queue   *byteQueue

	done chan struct{}
	once sync.Once
}

func newWSConn(ws *websocket.Conn, msgType websocket.MessageType, maxBytes, maxFrames int) *wsConn {
	return &wsConn{
		ws:      ws,
		msgType: msgType,
		queue:   newByteQueue(maxBytes, maxFrames),
		done:    make(chan struct{}),
	}
}

func (c *wsConn) start(writeTimeout, pingInterval, pongTimeout time.Duration) {
	go c.writeLoop(writeTimeout)
	go c.pingLoop(pingInterval, pongTimeout)
}

func (c *wsConn) writeLoop(timeout time.Duration) {
	for {
		b, ok := c.queue.pop()
		if !ok {
			return
		}
		ctx, cancel := context.WithTimeout(context.Background(), timeout)
		err := c.ws.Write(ctx, c.msgType, b)
		cancel()
		if err != nil {
			c.closeNow()
			return
		}
	}
}

func (c *wsConn) pingLoop(interval, timeout time.Duration) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-c.done:
			return
		case <-ticker.C:
			ctx, cancel := context.WithTimeout(context.Background(), timeout)
			err := c.ws.Ping(ctx)
			cancel()
			if err != nil {
				c.closeNow()
				return
			}
		}
	}
}

// send queues a message and reports whether it was accepted.
//
// A full data queue means one stream is outrunning the peer at the other end. The caller closes
// that one stream (route and handleOpen both do); tearing down the multiplexed connection would
// take every other stream with it. A full control queue is not a backpressure problem — control
// messages are tiny, so it can only mean the peer has stopped reading and the connection is dead.
func (c *wsConn) send(b []byte) bool {
	select {
	case <-c.done:
		return false
	default:
	}
	if c.queue.push(b) {
		return true
	}
	if c.msgType == websocket.MessageText {
		c.closeNow()
	}
	return false
}

func (c *wsConn) closeNow() {
	c.once.Do(func() {
		close(c.done)
		c.queue.close()
		_ = c.ws.CloseNow()
	})
}
