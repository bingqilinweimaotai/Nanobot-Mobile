package admission

import (
	"crypto/ed25519"
	"crypto/subtle"
	"encoding/base64"
	"errors"
	"fmt"
)

// ErrDenied is returned when a peer may not use the relay.
var ErrDenied = errors.New("relay access denied")

// Admission decides whether a hello from an agent or a client is allowed.
//
// The relay knows nothing about user accounts. Whatever proof a peer carries lives with the peer;
// admission only verifies it. See the README for the trade-offs of each mode.
type Admission interface {
	Allow(serverID, token, key, sig string) error
}

// Open accepts every peer. It exists for local development and must never be the production mode.
type Open struct{}

// Allow implements Admission.
func (Open) Allow(string, string, string, string) error { return nil }

// SharedKey requires a relay-wide secret that ships with the agent. It stops casual abuse, but
// anyone who extracts the secret from the agent binary can reuse it.
type SharedKey struct {
	key []byte
}

// NewSharedKey builds a SharedKey admission from a plain secret.
func NewSharedKey(key string) (*SharedKey, error) {
	if key == "" {
		return nil, errors.New("relay key must not be empty")
	}
	return &SharedKey{key: []byte(key)}, nil
}

// Allow implements Admission.
func (s *SharedKey) Allow(_ string, _ string, key string, _ string) error {
	if subtle.ConstantTimeCompare([]byte(key), s.key) != 1 {
		return ErrDenied
	}
	return nil
}

// Signed verifies an Ed25519 signature over the server id. The relay only needs the public key, so
// it stays stateless while still being able to tell issued hosts from self-generated ones.
type Signed struct {
	publicKey ed25519.PublicKey
}

// NewSigned builds a Signed admission from a base64url Ed25519 public key.
func NewSigned(encoded string) (*Signed, error) {
	raw, err := base64.RawURLEncoding.DecodeString(encoded)
	if err != nil {
		return nil, fmt.Errorf("relay public key is not base64url: %w", err)
	}
	if len(raw) != ed25519.PublicKeySize {
		return nil, fmt.Errorf("relay public key must be %d bytes", ed25519.PublicKeySize)
	}
	return &Signed{publicKey: ed25519.PublicKey(raw)}, nil
}

// Allow implements Admission.
func (s *Signed) Allow(serverID, _ string, _ string, sig string) error {
	signature, err := base64.RawURLEncoding.DecodeString(sig)
	if err != nil || len(signature) != ed25519.SignatureSize {
		return ErrDenied
	}
	if !ed25519.Verify(s.publicKey, []byte(serverID), signature) {
		return ErrDenied
	}
	return nil
}

// Sign is a helper for the issuer side. It is not used by the relay.
func Sign(privateKey ed25519.PrivateKey, serverID string) string {
	return base64.RawURLEncoding.EncodeToString(ed25519.Sign(privateKey, []byte(serverID)))
}
