package proto

import (
	"bytes"
	"errors"
	"testing"
)

func TestFrameRoundTrip(t *testing.T) {
	cases := []Frame{
		{Type: FrameOpen, StreamID: 1},
		{Type: FrameData, StreamID: 42, Payload: []byte("hello")},
		{Type: FrameClose, StreamID: 7, Payload: []byte{CloseGatewayUnreachable}},
		{Type: FrameAck, StreamID: 1 << 63},
		{Type: FrameWindow, StreamID: 9, Payload: []byte{0, 0, 1, 0}},
	}
	for _, want := range cases {
		encoded, err := EncodeFrame(want)
		if err != nil {
			t.Fatalf("encode %v: %v", want, err)
		}
		if len(encoded) != HeaderSize+len(want.Payload) {
			t.Fatalf("encoded length = %d", len(encoded))
		}
		got, err := DecodeFrame(encoded)
		if err != nil {
			t.Fatalf("decode %v: %v", want, err)
		}
		if got.Type != want.Type || got.StreamID != want.StreamID {
			t.Fatalf("got %v want %v", got, want)
		}
		if !bytes.Equal(got.Payload, want.Payload) {
			t.Fatalf("payload = %q want %q", got.Payload, want.Payload)
		}
	}
}

func TestDecodeRejectsShortFrame(t *testing.T) {
	for _, b := range [][]byte{nil, {}, {byte(FrameData)}, make([]byte, HeaderSize-1)} {
		if _, err := DecodeFrame(b); !errors.Is(err, ErrFrameTooShort) {
			t.Fatalf("len=%d err=%v", len(b), err)
		}
	}
}

func TestDecodeRejectsUnknownType(t *testing.T) {
	b := make([]byte, HeaderSize)
	b[0] = 0x7f
	if _, err := DecodeFrame(b); !errors.Is(err, ErrUnknownFrameType) {
		t.Fatalf("err = %v", err)
	}
}

func TestEncodeRejectsOversize(t *testing.T) {
	f := Frame{
		Type:     FrameData,
		StreamID: 1,
		Payload:  make([]byte, MaxFrameSize),
	}
	if _, err := EncodeFrame(f); !errors.Is(err, ErrFrameTooLarge) {
		t.Fatalf("err = %v", err)
	}
}

func TestDecodeAcceptsLargestFrame(t *testing.T) {
	b := make([]byte, MaxFrameSize)
	b[0] = byte(FrameData)
	if _, err := DecodeFrame(b); err != nil {
		t.Fatalf("largest frame rejected: %v", err)
	}
	b = append(b, 0)
	if _, err := DecodeFrame(b); !errors.Is(err, ErrFrameTooLarge) {
		t.Fatalf("oversize frame accepted: %v", err)
	}
}

func TestControlRoundTrip(t *testing.T) {
	online := true
	messages := []Message{
		{T: MsgHello, V: Version, Role: RoleAgent, ServerID: "abc", Token: "tok", Key: "k"},
		Ready("sid"),
		Presence(online),
		Error(ErrAuth, "bad token"),
	}
	for _, m := range messages {
		b, err := EncodeMessage(m)
		if err != nil {
			t.Fatalf("encode: %v", err)
		}
		got, err := DecodeMessage(b)
		if err != nil {
			t.Fatalf("decode: %v", err)
		}
		if got.T != m.T || got.SID != m.SID {
			t.Fatalf("got %+v want %+v", got, m)
		}
	}
}

func TestPresenceCarriesFalse(t *testing.T) {
	b, err := EncodeMessage(Presence(false))
	if err != nil {
		t.Fatal(err)
	}
	got, err := DecodeMessage(b)
	if err != nil {
		t.Fatal(err)
	}
	if got.Agent == nil || *got.Agent {
		t.Fatalf("presence = %+v", got)
	}
}

func TestDecodeControlRejectsOversize(t *testing.T) {
	if _, err := DecodeMessage(make([]byte, MaxControlFrame+1)); err == nil {
		t.Fatal("oversize control message accepted")
	}
}

func TestCloseFrameReason(t *testing.T) {
	f := CloseFrame(5, CloseQuota)
	if f.Type != FrameClose || f.StreamID != 5 || len(f.Payload) != 1 || f.Payload[0] != CloseQuota {
		t.Fatalf("frame = %+v", f)
	}
}
