package admission

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"testing"
)

func TestOpenAllowsEverything(t *testing.T) {
	if err := (Open{}).Allow("any", "any", "any", "any"); err != nil {
		t.Fatalf("open mode denied: %v", err)
	}
}

func TestSharedKey(t *testing.T) {
	a, err := NewSharedKey("s3cret")
	if err != nil {
		t.Fatal(err)
	}
	if err := a.Allow("srv", "tok", "s3cret", ""); err != nil {
		t.Fatalf("valid key denied: %v", err)
	}
	if err := a.Allow("srv", "tok", "wrong", ""); !errors.Is(err, ErrDenied) {
		t.Fatalf("wrong key err = %v", err)
	}
	if err := a.Allow("srv", "tok", "", ""); !errors.Is(err, ErrDenied) {
		t.Fatalf("empty key err = %v", err)
	}
}

func TestSharedKeyRequiresSecret(t *testing.T) {
	if _, err := NewSharedKey(""); err == nil {
		t.Fatal("empty relay key accepted")
	}
}

func TestSigned(t *testing.T) {
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	encoded := base64.RawURLEncoding.EncodeToString(pub)
	a, err := NewSigned(encoded)
	if err != nil {
		t.Fatal(err)
	}
	sig := Sign(priv, "issued-host")
	if err := a.Allow("issued-host", "", "", sig); err != nil {
		t.Fatalf("valid signature rejected: %v", err)
	}
	if err := a.Allow("other-host", "", "", sig); !errors.Is(err, ErrDenied) {
		t.Fatalf("signature for another host accepted: %v", err)
	}
	if err := a.Allow("issued-host", "", "", ""); !errors.Is(err, ErrDenied) {
		t.Fatalf("missing signature accepted: %v", err)
	}
}

func TestSignedRejectsBadKey(t *testing.T) {
	if _, err := NewSigned("not-base64!"); err == nil {
		t.Fatal("invalid base64 accepted")
	}
	if _, err := NewSigned(base64.RawURLEncoding.EncodeToString([]byte("short"))); err == nil {
		t.Fatal("short key accepted")
	}
}
