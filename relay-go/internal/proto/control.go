package proto

import (
	"encoding/json"
	"errors"
)

// MaxControlFrame bounds a control message. Control frames are small JSON objects; anything larger
// is a protocol violation.
const MaxControlFrame = 4 << 10

// Control message types.
const (
	MsgHello    = "hello"
	MsgReady    = "ready"
	MsgPresence = "presence"
	MsgError    = "error"
)

// Error codes carried in Message.Code.
const (
	ErrAuth       = "auth"
	ErrQuota      = "quota"
	ErrBadRequest = "bad_request"
	ErrConflict   = "conflict"
)

// Roles.
const (
	RoleAgent  = "agent"
	RoleClient = "client"
)

// Message is a control-channel frame. Fields are omitted when empty so the wire stays small.
type Message struct {
	T        string `json:"t"`
	V        int    `json:"v,omitempty"`
	Role     string `json:"role,omitempty"`
	ServerID string `json:"serverId,omitempty"`
	Token    string `json:"token,omitempty"`
	Key      string `json:"key,omitempty"`
	Sig      string `json:"sig,omitempty"`
	SID      string `json:"sid,omitempty"`
	Agent    *bool  `json:"agent,omitempty"`
	Code     string `json:"code,omitempty"`
	Message  string `json:"message,omitempty"`
}

// EncodeMessage serialises a control frame.
func EncodeMessage(m Message) ([]byte, error) {
	b, err := json.Marshal(m)
	if err != nil {
		return nil, err
	}
	if len(b) > MaxControlFrame {
		return nil, errors.New("control message exceeds the size limit")
	}
	return b, nil
}

// DecodeMessage parses a control frame. Unknown fields and types are tolerated so that older
// peers can talk to a newer relay.
func DecodeMessage(b []byte) (Message, error) {
	if len(b) == 0 {
		return Message{}, errors.New("empty control message")
	}
	if len(b) > MaxControlFrame {
		return Message{}, errors.New("control message exceeds the size limit")
	}
	var m Message
	if err := json.Unmarshal(b, &m); err != nil {
		return Message{}, err
	}
	return m, nil
}

// Ready builds the reply that hands a peer its data-channel session id.
func Ready(sid string) Message { return Message{T: MsgReady, SID: sid} }

// Presence tells a client whether its PC agent can currently accept streams.
func Presence(online bool) Message { return Message{T: MsgPresence, Agent: &online} }

// Error builds a terminal control error.
func Error(code, message string) Message {
	return Message{T: MsgError, Code: code, Message: message}
}
