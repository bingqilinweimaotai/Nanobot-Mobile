package relay

import (
	"crypto/subtle"
	"sync"
	"sync/atomic"

	"nanobot/relay/internal/proto"
)

// sameToken compares pairing tokens without leaking their contents through timing.
func sameToken(a, b string) bool {
	return subtle.ConstantTimeCompare([]byte(a), []byte(b)) == 1
}

// peer is one authenticated side of a tunnel: the PC agent or a phone client.
//
// A peer owns one control connection and, once it dials /data with its session id, one data
// connection. Every stream between a client and an agent references both peers.
type peer struct {
	serverID string
	token    string
	role     string
	sid      string

	control *wsConn

	dataMu sync.RWMutex
	data   *wsConn

	dataReady atomic.Bool
	closed    atomic.Bool

	closeOnce sync.Once
}

func (p *peer) setData(c *wsConn) bool {
	p.dataMu.Lock()
	defer p.dataMu.Unlock()
	// closed is checked under the same lock that closeConns uses to detach the socket, so a data
	// channel can never attach to a peer that is already being torn down.
	if p.closed.Load() || p.data != nil {
		return false
	}
	p.data = c
	return true
}

func (p *peer) dataConn() *wsConn {
	p.dataMu.RLock()
	defer p.dataMu.RUnlock()
	return p.data
}

func (p *peer) sendFrame(f proto.Frame) bool {
	d := p.dataConn()
	if d == nil {
		return false
	}
	b, err := proto.EncodeFrame(f)
	if err != nil {
		return false
	}
	return d.send(b)
}

func (p *peer) sendPresence(online bool) bool {
	msg, err := proto.EncodeMessage(proto.Presence(online))
	if err != nil {
		return false
	}
	return p.control.send(msg)
}

// closeConns closes both sockets. The read loops exit and call dropPeer, which owns registry
// cleanup.
func (p *peer) closeConns() {
	p.closed.Store(true)
	p.dataReady.Store(false)
	p.dataMu.Lock()
	d := p.data
	p.data = nil
	p.dataMu.Unlock()
	if d != nil {
		d.closeNow()
	}
	if p.control != nil {
		p.control.closeNow()
	}
}

// stream is one TCP connection tunneled between a client and an agent.
type stream struct {
	id       uint64
	serverID string
	agent    *peer
	client   *peer
}

func (s *stream) other(p *peer) *peer {
	switch p {
	case s.agent:
		return s.client
	case s.client:
		return s.agent
	default:
		return nil
	}
}
