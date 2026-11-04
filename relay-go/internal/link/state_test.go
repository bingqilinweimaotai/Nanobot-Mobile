package link

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func isolateHome(t *testing.T) string {
	t.Helper()
	home := t.TempDir()
	t.Setenv("HOME", home)
	t.Setenv("USERPROFILE", home)
	return home
}

func TestLoadOrCreateIsStable(t *testing.T) {
	home := isolateHome(t)
	first, err := LoadOrCreate(false)
	if err != nil {
		t.Fatal(err)
	}
	if first.ServerID == "" || first.ClientToken == "" || first.PairKey == "" {
		t.Fatalf("identity is incomplete: %+v", first)
	}
	if len(first.ServerID) != 16 {
		t.Fatalf("server id = %q", first.ServerID)
	}
	if _, err := decodeKey(first.PairKey); err != nil {
		t.Fatalf("pair key: %v", err)
	}

	second, err := LoadOrCreate(false)
	if err != nil {
		t.Fatal(err)
	}
	if second != first {
		t.Fatalf("identity changed across runs:\n%+v\n%+v", first, second)
	}

	path := filepath.Join(home, ".nanobot", "link", "state.json")
	if _, err := os.Stat(path); err != nil {
		t.Fatalf("state file: %v", err)
	}
}

func TestResetRotatesIdentityButKeepsRelay(t *testing.T) {
	isolateHome(t)
	first, err := LoadOrCreate(false)
	if err != nil {
		t.Fatal(err)
	}
	first.Relay = "wss://relay.example.com/nanobot"
	if err := first.Save(); err != nil {
		t.Fatal(err)
	}

	rotated, err := LoadOrCreate(true)
	if err != nil {
		t.Fatal(err)
	}
	if rotated.ServerID == first.ServerID ||
		rotated.ClientToken == first.ClientToken ||
		rotated.PairKey == first.PairKey {
		t.Fatal("reset did not rotate the identity")
	}
	if rotated.Relay != first.Relay {
		t.Fatalf("relay = %q, want %q", rotated.Relay, first.Relay)
	}
}

func TestImportsLegacyIdentity(t *testing.T) {
	home := isolateHome(t)
	legacyDir := filepath.Join(home, ".nanobot", "android")
	if err := os.MkdirAll(legacyDir, 0o700); err != nil {
		t.Fatal(err)
	}
	key, _ := generateKey()
	legacy := map[string]string{
		"serverId":    "legacy-server-id",
		"clientToken": "legacy-token",
		"pairKey":     encodeKey(key),
		"relay":       "wss://relay.example.com/nanobot",
	}
	data, _ := json.Marshal(legacy)
	if err := os.WriteFile(filepath.Join(legacyDir, "agent.json"), data, 0o600); err != nil {
		t.Fatal(err)
	}

	state, err := LoadOrCreate(false)
	if err != nil {
		t.Fatal(err)
	}
	if state.ServerID != "legacy-server-id" ||
		state.ClientToken != "legacy-token" ||
		state.PairKey != encodeKey(key) {
		t.Fatalf("legacy identity was not imported: %+v", state)
	}
}

func TestCorruptStateIsReplaced(t *testing.T) {
	home := isolateHome(t)
	dir := filepath.Join(home, ".nanobot", "link")
	if err := os.MkdirAll(dir, 0o700); err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(dir, "state.json")
	if err := os.WriteFile(path, []byte("{ not json"), 0o600); err != nil {
		t.Fatal(err)
	}
	state, err := LoadOrCreate(false)
	if err != nil {
		t.Fatal(err)
	}
	if state.ServerID == "" || state.PairKey == "" {
		t.Fatalf("state = %+v", state)
	}
}

func TestStateWithBrokenPairKeyIsRepaired(t *testing.T) {
	home := isolateHome(t)
	dir := filepath.Join(home, ".nanobot", "link")
	if err := os.MkdirAll(dir, 0o700); err != nil {
		t.Fatal(err)
	}
	raw, _ := json.Marshal(State{ServerID: "keep-me", ClientToken: "keep-token", PairKey: "AAAA"})
	if err := os.WriteFile(filepath.Join(dir, "state.json"), raw, 0o600); err != nil {
		t.Fatal(err)
	}
	state, err := LoadOrCreate(false)
	if err != nil {
		t.Fatal(err)
	}
	if state.ServerID != "keep-me" || state.ClientToken != "keep-token" {
		t.Fatalf("identity changed: %+v", state)
	}
	if _, err := decodeKey(state.PairKey); err != nil {
		t.Fatalf("pair key was not repaired: %v", err)
	}
}
