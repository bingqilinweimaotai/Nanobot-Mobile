package link

import (
	"bytes"
	"testing"
)

func TestEncryptRoundTrip(t *testing.T) {
	key, err := generateKey()
	if err != nil {
		t.Fatal(err)
	}
	plaintext := []byte("hello from the phone")
	packet, err := encrypt(key, plaintext)
	if err != nil {
		t.Fatal(err)
	}
	if bytes.Equal(packet, plaintext) {
		t.Fatal("ciphertext equals plaintext")
	}
	if len(packet) != nonceBytes+len(plaintext)+16 {
		t.Fatalf("packet length = %d", len(packet))
	}
	got, err := decrypt(key, packet)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(got, plaintext) {
		t.Fatalf("plaintext = %q", got)
	}
}

func TestEncryptUsesFreshNonce(t *testing.T) {
	key, _ := generateKey()
	first, _ := encrypt(key, []byte("same"))
	second, _ := encrypt(key, []byte("same"))
	if bytes.Equal(first, second) {
		t.Fatal("two encryptions produced the same packet")
	}
}

func TestDecryptRejectsTampering(t *testing.T) {
	key, _ := generateKey()
	packet, _ := encrypt(key, []byte("payload"))
	packet[len(packet)-1] ^= 0xff
	if _, err := decrypt(key, packet); err == nil {
		t.Fatal("tampered packet was accepted")
	}
}

func TestDecryptRejectsWrongKey(t *testing.T) {
	key, _ := generateKey()
	other, _ := generateKey()
	packet, _ := encrypt(key, []byte("payload"))
	if _, err := decrypt(other, packet); err == nil {
		t.Fatal("packet was accepted with the wrong key")
	}
}

func TestDecryptRejectsShortPacket(t *testing.T) {
	key, _ := generateKey()
	if _, err := decrypt(key, []byte("short")); err == nil {
		t.Fatal("short packet was accepted")
	}
}

func TestDecodeKeyAcceptsPadded(t *testing.T) {
	key, _ := generateKey()
	encoded := encodeKey(key)
	padded := base64URLPadded(encoded)
	decoded, err := decodeKey(padded)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(decoded, key) {
		t.Fatal("decoded key does not match")
	}
}

func base64URLPadded(value string) string {
	for len(value)%4 != 0 {
		value += "="
	}
	return value
}
