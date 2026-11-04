// Package proto defines the relay v2 wire format.
//
// A data channel carries many streams over one WebSocket. Every binary message is one frame:
//
//	+--------+--------------------+------------------+
//	| type   | streamId (8B, BE)  | payload          |
//	| 1 byte |                    |                  |
//	+--------+--------------------+------------------+
//
// The relay never inspects payloads: DATA carries ciphertext produced with the pair key that only
// the phone and the PC agent hold.
package proto

import (
	"encoding/binary"
	"errors"
	"fmt"
)

const (
	// Version is the protocol version carried by the hello message.
	Version = 2

	// HeaderSize is the fixed frame header length.
	HeaderSize = 9

	// MaxFrameSize bounds a single data message. Files are streamed in chunks, so this limits a
	// chunk, never the size of an attachment.
	MaxFrameSize = 256 << 10
)

// FrameType identifies what a data-channel frame does.
type FrameType byte

const (
	// FrameOpen asks the agent to open a stream to the local gateway.
	FrameOpen FrameType = 0x01
	// FrameData carries end-to-end encrypted bytes.
	FrameData FrameType = 0x02
	// FrameClose ends one stream. The payload is a single reason byte.
	FrameClose FrameType = 0x03
	// FrameAck tells the client the agent connected to the gateway.
	FrameAck FrameType = 0x04
	// FrameWindow is reserved for credit-based flow control. Relays forward it untouched and
	// endpoints may ignore it until the feature ships.
	FrameWindow FrameType = 0x05
)

// Valid reports whether the type belongs to protocol v2.
func (t FrameType) Valid() bool {
	switch t {
	case FrameOpen, FrameData, FrameClose, FrameAck, FrameWindow:
		return true
	default:
		return false
	}
}

// Frame is one data-channel message.
//
// Payload aliases the read buffer returned by the WebSocket library. Callers must copy it if they
// keep it past the next read.
type Frame struct {
	Type     FrameType
	StreamID uint64
	Payload  []byte
}

// Close reasons carried in a FrameClose payload.
const (
	CloseNormal             byte = 0
	CloseGatewayUnreachable byte = 1
	CloseAgentOffline       byte = 2
	CloseQuota              byte = 3
	CloseProtocol           byte = 4
	CloseRelayUnavailable   byte = 5
)

// ReasonText renders a close reason for logs and tests.
func ReasonText(code byte) string {
	switch code {
	case CloseNormal:
		return "normal"
	case CloseGatewayUnreachable:
		return "gateway unreachable"
	case CloseAgentOffline:
		return "pc agent offline"
	case CloseQuota:
		return "quota exceeded"
	case CloseProtocol:
		return "protocol error"
	case CloseRelayUnavailable:
		return "relay stream unavailable"
	default:
		return fmt.Sprintf("unknown reason %d", code)
	}
}

var (
	// ErrFrameTooShort means the message is missing part of the header.
	ErrFrameTooShort = errors.New("frame is shorter than the 9-byte header")
	// ErrFrameTooLarge means the message exceeds MaxFrameSize.
	ErrFrameTooLarge = errors.New("frame exceeds the size limit")
	// ErrUnknownFrameType means the type byte is not part of protocol v2.
	ErrUnknownFrameType = errors.New("unknown frame type")
)

// DecodeFrame parses one data-channel message.
func DecodeFrame(b []byte) (Frame, error) {
	if len(b) < HeaderSize {
		return Frame{}, ErrFrameTooShort
	}
	if len(b) > MaxFrameSize {
		return Frame{}, ErrFrameTooLarge
	}
	t := FrameType(b[0])
	if !t.Valid() {
		return Frame{}, fmt.Errorf("%w: 0x%02x", ErrUnknownFrameType, b[0])
	}
	return Frame{
		Type:     t,
		StreamID: binary.BigEndian.Uint64(b[1:HeaderSize]),
		Payload:  b[HeaderSize:],
	}, nil
}

// EncodeFrame serialises a frame into a new buffer.
func EncodeFrame(f Frame) ([]byte, error) {
	if !f.Type.Valid() {
		return nil, fmt.Errorf("%w: 0x%02x", ErrUnknownFrameType, byte(f.Type))
	}
	if len(f.Payload)+HeaderSize > MaxFrameSize {
		return nil, ErrFrameTooLarge
	}
	b := make([]byte, HeaderSize+len(f.Payload))
	b[0] = byte(f.Type)
	binary.BigEndian.PutUint64(b[1:HeaderSize], f.StreamID)
	copy(b[HeaderSize:], f.Payload)
	return b, nil
}

// CloseFrame builds a terminating frame for one stream.
func CloseFrame(streamID uint64, reason byte) Frame {
	return Frame{Type: FrameClose, StreamID: streamID, Payload: []byte{reason}}
}
