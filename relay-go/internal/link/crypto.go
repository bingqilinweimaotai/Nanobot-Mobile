package link

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"fmt"
)

const (
	keyBytes   = 32
	nonceBytes = 12
)

var (
	errInvalidKey  = errors.New("pair key must be 32 bytes")
	errShortPacket = errors.New("relay frame is too short")
)

// generateKey returns a fresh 32-byte AES-GCM key.
func generateKey() ([]byte, error) {
	key := make([]byte, keyBytes)
	if _, err := rand.Read(key); err != nil {
		return nil, fmt.Errorf("could not generate a pair key: %w", err)
	}
	return key, nil
}

// encodeKey renders a key the way the pairing link expects it.
func encodeKey(key []byte) string {
	return base64.RawURLEncoding.EncodeToString(key)
}

// decodeKey parses a pairing key. Both padded and unpadded base64url are accepted because links
// have been copied through tools that add padding.
func decodeKey(text string) ([]byte, error) {
	key, err := base64.RawURLEncoding.DecodeString(text)
	if err != nil {
		if padded, paddedErr := base64.URLEncoding.DecodeString(text); paddedErr == nil {
			key, err = padded, nil
		}
	}
	if err != nil {
		return nil, errors.New("invalid pair key encoding")
	}
	if len(key) != keyBytes {
		return nil, errInvalidKey
	}
	return key, nil
}

// encrypt seals plaintext with AES-256-GCM and prefixes the random 12-byte nonce.
//
// This is the same construction the Kotlin tunnel uses, so a phone and this program can share one
// pairing key without the relay ever seeing plaintext.
func encrypt(key, plaintext []byte) ([]byte, error) {
	gcm, err := newGCM(key)
	if err != nil {
		return nil, err
	}
	nonce := make([]byte, nonceBytes)
	if _, err := rand.Read(nonce); err != nil {
		return nil, fmt.Errorf("could not generate a nonce: %w", err)
	}
	return gcm.Seal(nonce, nonce, plaintext, nil), nil
}

// decrypt opens a packet produced by encrypt.
func decrypt(key, packet []byte) ([]byte, error) {
	if len(packet) <= nonceBytes {
		return nil, errShortPacket
	}
	gcm, err := newGCM(key)
	if err != nil {
		return nil, err
	}
	nonce, ciphertext := packet[:nonceBytes], packet[nonceBytes:]
	plaintext, err := gcm.Open(nil, nonce, ciphertext, nil)
	if err != nil {
		return nil, errors.New("could not decrypt relay frame")
	}
	return plaintext, nil
}

func newGCM(key []byte) (cipher.AEAD, error) {
	if len(key) != keyBytes {
		return nil, errInvalidKey
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	return cipher.NewGCM(block)
}
