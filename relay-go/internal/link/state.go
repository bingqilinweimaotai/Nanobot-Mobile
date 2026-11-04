package link

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// State is the identity of one PC. It must survive restarts: the phone stores these values when it
// scans a pairing code, so rotating them silently breaks every existing pairing.
type State struct {
	Version     int    `json:"version"`
	ServerID    string `json:"serverId"`
	ClientToken string `json:"clientToken"`
	PairKey     string `json:"pairKey"`
	Relay       string `json:"relay"`
}

// stateDir returns ~/.nanobot/link.
func stateDir() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil || strings.TrimSpace(home) == "" {
		return ".nanobot-link", nil
	}
	return filepath.Join(home, ".nanobot", "link"), nil
}

// StatePath is the absolute path of state.json, for messages shown to the user.
func StatePath() (string, error) {
	dir, err := stateDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "state.json"), nil
}

// LoadOrCreate reads the identity, creating it on first run. reset rotates the identity but keeps
// the relay URL, which is a deployment setting rather than a secret.
func LoadOrCreate(reset bool) (State, error) {
	path, err := StatePath()
	if err != nil {
		return State{}, err
	}
	existing := readState(path)
	if existing.ServerID == "" && existing.ClientToken == "" && existing.PairKey == "" {
		// Import the identity of the Kotlin agent when this is the first Go run. Re-pairing every
		// phone on upgrade would be a pointless chore.
		if legacy, legacyErr := legacyStatePath(); legacyErr == nil {
			existing = readState(legacy)
		}
	}
	state := existing
	if reset {
		state = State{Relay: existing.Relay}
	}
	state.Version = 1
	if state.ServerID == "" {
		state.ServerID = randomHex(8)
	}
	if state.ClientToken == "" {
		state.ClientToken = randomToken()
	}
	if state.PairKey == "" {
		key, keyErr := generateKey()
		if keyErr != nil {
			return State{}, keyErr
		}
		state.PairKey = encodeKey(key)
	} else if _, keyErr := decodeKey(state.PairKey); keyErr != nil {
		key, generateErr := generateKey()
		if generateErr != nil {
			return State{}, generateErr
		}
		state.PairKey = encodeKey(key)
	}
	if err := saveState(path, state); err != nil {
		return State{}, err
	}
	return state, nil
}

// Save writes the identity back, used when the user supplies a relay URL that should be remembered.
func (s State) Save() error {
	path, err := StatePath()
	if err != nil {
		return err
	}
	return saveState(path, s)
}

func readState(path string) State {
	data, err := os.ReadFile(path)
	if err != nil {
		return State{}
	}
	var state State
	if err := json.Unmarshal(data, &state); err != nil {
		return State{}
	}
	return state
}

func saveState(path string, state State) error {
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return fmt.Errorf("could not create %s: %w", dir, err)
	}
	// The state file holds the pairing key, so it is a credential file.
	_ = os.Chmod(dir, 0o700)

	data, err := json.MarshalIndent(state, "", "  ")
	if err != nil {
		return err
	}
	temporary := path + ".tmp"
	if err := os.WriteFile(temporary, data, 0o600); err != nil {
		return fmt.Errorf("could not write %s: %w", temporary, err)
	}
	_ = os.Chmod(temporary, 0o600)
	if err := os.Rename(temporary, path); err != nil {
		_ = os.Remove(temporary)
		return fmt.Errorf("could not write %s: %w", path, err)
	}
	_ = os.Chmod(path, 0o600)
	return nil
}

// WriteCredential stores a generated pairing artifact next to the state file.
func WriteCredential(name, content string) (string, error) {
	dir, err := stateDir()
	if err != nil {
		return "", err
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return "", err
	}
	path := filepath.Join(dir, name)
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		return "", err
	}
	_ = os.Chmod(path, 0o600)
	return path, nil
}

// legacyStatePath is the Kotlin agent's identity file.
func legacyStatePath() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil || home == "" {
		return "", fmt.Errorf("no home directory")
	}
	return filepath.Join(home, ".nanobot", "android", "agent.json"), nil
}

func randomHex(bytes int) string {
	buf := make([]byte, bytes)
	if _, err := rand.Read(buf); err != nil {
		panic("crypto/rand is unavailable: " + err.Error())
	}
	return hex.EncodeToString(buf)
}

func randomToken() string {
	buf := make([]byte, 32)
	if _, err := rand.Read(buf); err != nil {
		panic("crypto/rand is unavailable: " + err.Error())
	}
	return encodeKey(buf)
}
