package link

import (
	"errors"
	"fmt"
	"net/url"
	"strings"
)

const (
	controlPath = "/control"
	dataPath    = "/data"
)

// ControlURL normalises the relay address. Users configure wss://relay.example.com/nanobot and this
// turns it into wss://relay.example.com/nanobot/control, keeping the mount prefix.
func ControlURL(base string) (string, error) {
	parsed, err := url.Parse(strings.TrimSpace(base))
	if err != nil {
		return "", errors.New("relay address is not a valid URI")
	}
	if parsed.Scheme != "ws" && parsed.Scheme != "wss" {
		return "", errors.New("relay address must start with ws:// or wss://")
	}
	if parsed.Host == "" {
		return "", errors.New("relay address has no host")
	}
	path := parsed.Path
	switch {
	case path == "" || path == "/":
		path = controlPath
	case strings.HasSuffix(path, controlPath):
		// already a control URL
	default:
		path = strings.TrimRight(path, "/") + controlPath
	}
	parsed.Path = path
	parsed.RawQuery = ""
	parsed.Fragment = ""
	return parsed.String(), nil
}

// DataURL builds the data-channel URL next to the control URL, so a relay mounted at
// /nanobot/control gets /nanobot/data.
func DataURL(control string, serverID, role string) (string, error) {
	parsed, err := url.Parse(control)
	if err != nil {
		return "", fmt.Errorf("invalid control URL: %w", err)
	}
	if strings.HasSuffix(parsed.Path, controlPath) {
		parsed.Path = strings.TrimSuffix(parsed.Path, controlPath) + dataPath
	} else {
		parsed.Path = dataPath
	}
	query := url.Values{}
	query.Set("serverId", serverID)
	query.Set("role", role)
	parsed.RawQuery = query.Encode()
	parsed.Fragment = ""
	return parsed.String(), nil
}
