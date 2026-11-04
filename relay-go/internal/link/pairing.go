package link

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"net/url"
	"strconv"
	"strings"
)

// Pairing versions and kinds. These mirror the Kotlin tunnel classes exactly: the phone parses
// this payload with kotlinx.serialization, so field names and types are part of the wire contract.
const (
	PairingVersion = 1
	KindRelay      = "relay"
	KindDirect     = "direct"
)

// PairingOffer is what a QR code carries. It contains live credentials, so treat a pairing link the
// same way you would treat a password.
type PairingOffer struct {
	V           int    `json:"v"`
	Kind        string `json:"kind"`
	Name        string `json:"name"`
	Relay       string `json:"relay"`
	ServerID    string `json:"serverId"`
	ClientToken string `json:"clientToken"`
	// PairKey is a base64url 32-byte AES-GCM key shared by the phone and this program.
	PairKey     string `json:"pairKey"`
	Endpoint    string `json:"endpoint"`
	Secret      string `json:"secret"`
	GatewayPort *int   `json:"gatewayPort,omitempty"`
	Platform    string `json:"platform"`
}

// DisplayName is the label the phone shows for this host.
func (o PairingOffer) DisplayName() string {
	if name := strings.TrimSpace(o.Name); name != "" {
		return name
	}
	if o.Kind == KindDirect {
		if parsed, err := url.Parse(o.Endpoint); err == nil && parsed.Hostname() != "" {
			return parsed.Hostname()
		}
	}
	if len(o.ServerID) > 12 {
		return o.ServerID[:12]
	}
	if o.ServerID != "" {
		return o.ServerID
	}
	return "nanobot"
}

// Validate normalises and checks an offer.
func (o PairingOffer) Validate() (PairingOffer, error) {
	if o.V != PairingVersion {
		return o, fmt.Errorf("unsupported pairing version %d", o.V)
	}
	o.Kind = strings.ToLower(strings.TrimSpace(o.Kind))
	if o.Kind != KindRelay && o.Kind != KindDirect {
		return o, fmt.Errorf("unknown pairing kind: %s", o.Kind)
	}
	if o.GatewayPort != nil && (*o.GatewayPort < 1 || *o.GatewayPort > 65535) {
		return o, fmt.Errorf("invalid gateway port: %d", *o.GatewayPort)
	}
	switch o.Kind {
	case KindRelay:
		if !strings.HasPrefix(o.Relay, "ws://") && !strings.HasPrefix(o.Relay, "wss://") {
			return o, errors.New("relay address must start with ws:// or wss://")
		}
		if strings.TrimSpace(o.ServerID) == "" {
			return o, errors.New("pairing code has no server id")
		}
		if strings.TrimSpace(o.ClientToken) == "" {
			return o, errors.New("pairing code has no client token")
		}
		if strings.TrimSpace(o.PairKey) == "" {
			return o, errors.New("pairing code has no pair key")
		}
		if _, err := decodeKey(o.PairKey); err != nil {
			return o, err
		}
	case KindDirect:
		if !strings.HasPrefix(o.Endpoint, "http://") && !strings.HasPrefix(o.Endpoint, "https://") {
			return o, errors.New("gateway address must start with http:// or https://")
		}
	}
	o.Name = truncate(strings.TrimSpace(o.Name), 64)
	o.Relay = strings.TrimSpace(o.Relay)
	o.Endpoint = strings.TrimRight(strings.TrimSpace(o.Endpoint), "/")
	return o, nil
}

// EncodePairingLink renders the compact nanobot://pair?relay=… form. It carries only the fields the
// phone needs to connect: the base64url JSON offer is roughly twice as long, which pushes the
// terminal QR past the height of most terminal windows.
//
// Diagnostics that the app can do without (gatewayPort, platform) are omitted. The Android app
// accepts this form and the older ?offer= JSON form; ParsePairingLink accepts both as well.
func EncodePairingLink(offer PairingOffer) (string, error) {
	valid, err := offer.Validate()
	if err != nil {
		return "", err
	}

	var params []string
	if valid.Kind == KindDirect {
		params = append(params,
			"kind="+escapeQueryValue(valid.Kind),
			"endpoint="+escapeQueryValue(valid.Endpoint),
		)
	} else {
		params = append(params,
			"relay="+escapeQueryValue(valid.Relay),
			"server="+escapeQueryValue(valid.ServerID),
			"token="+escapeQueryValue(valid.ClientToken),
			"key="+escapeQueryValue(valid.PairKey),
		)
	}
	if valid.Secret != "" {
		params = append(params, "secret="+escapeQueryValue(valid.Secret))
	}
	if valid.Name != "" {
		params = append(params, "name="+escapeQueryValue(valid.Name))
	}
	return "nanobot://pair?" + strings.Join(params, "&"), nil
}

// escapeQueryValue percent-encodes everything a query cannot carry literally, then restores ':' and
// '/' because they are legal in a query and both parsers accept them. Keeping them bare saves 13
// characters for a relay URL, which is enough to drop the terminal QR by one version.
func escapeQueryValue(value string) string {
	escaped := url.QueryEscape(value)
	escaped = strings.ReplaceAll(escaped, "%3A", ":")
	escaped = strings.ReplaceAll(escaped, "%2F", "/")
	return escaped
}

// ParsePairingLink accepts the link this program prints, plus a bare base64 payload or plain JSON.
// It exists so that a pairing can be inspected or reused without a QR code.
func ParsePairingLink(raw string) (PairingOffer, error) {
	text := strings.TrimSpace(raw)
	if text == "" {
		return PairingOffer{}, errors.New("pairing link is empty")
	}
	if strings.HasPrefix(text, "{") {
		return decodeOffer([]byte(text))
	}
	if strings.HasPrefix(text, "nanobot://") ||
		strings.HasPrefix(text, "http://") || strings.HasPrefix(text, "https://") {
		parsed, err := url.Parse(text)
		if err != nil {
			return PairingOffer{}, errors.New("pairing link is not a valid URI")
		}
		params := parsed.Query()
		if offer := params.Get("offer"); offer != "" {
			var payload []byte
			if strings.HasPrefix(strings.TrimSpace(offer), "{") {
				payload = []byte(offer)
			} else {
				decoded, decodeErr := decodeBase64(offer)
				if decodeErr != nil {
					return PairingOffer{}, decodeErr
				}
				payload = decoded
			}
			return decodeOffer(payload)
		}
		if params.Has("relay") || params.Has("relayUrl") || params.Has("endpoint") {
			return offerFromQuery(params)
		}
		return PairingOffer{}, errors.New("pairing link has no offer")
	}
	payload, err := decodeBase64(text)
	if err != nil {
		return PairingOffer{}, errors.New("unsupported pairing link")
	}
	return decodeOffer(payload)
}

func decodeOffer(payload []byte) (PairingOffer, error) {
	var offer PairingOffer
	if err := json.Unmarshal(payload, &offer); err != nil {
		return PairingOffer{}, fmt.Errorf("pairing data is invalid: %w", err)
	}
	return offer.Validate()
}

// offerFromQuery reads the compact query form. The short key aliases are the ones the Android app
// uses; the long names stay accepted so links written by older tooling keep working.
func offerFromQuery(params url.Values) (PairingOffer, error) {
	endpoint := params.Get("endpoint")
	kind := params.Get("kind")
	if kind == "" {
		if endpoint != "" {
			kind = KindDirect
		} else {
			kind = KindRelay
		}
	}
	offer := PairingOffer{
		V:           PairingVersion,
		Kind:        kind,
		Name:        params.Get("name"),
		Relay:       firstNonEmpty(params.Get("relay"), params.Get("relayUrl")),
		ServerID:    firstNonEmpty(params.Get("server"), params.Get("serverId")),
		ClientToken: firstNonEmpty(params.Get("token"), params.Get("clientToken")),
		PairKey:     firstNonEmpty(params.Get("key"), params.Get("pairKey")),
		Endpoint:    endpoint,
		Secret:      params.Get("secret"),
		Platform:    params.Get("platform"),
	}
	if text := params.Get("port"); text != "" {
		port, err := strconv.Atoi(text)
		if err != nil {
			return PairingOffer{}, fmt.Errorf("invalid gateway port: %s", text)
		}
		offer.GatewayPort = &port
	}
	return offer.Validate()
}

func decodeBase64(value string) ([]byte, error) {
	value = strings.TrimSpace(value)
	if decoded, err := base64.RawURLEncoding.DecodeString(value); err == nil {
		return decoded, nil
	}
	if decoded, err := base64.URLEncoding.DecodeString(value); err == nil {
		return decoded, nil
	}
	if decoded, err := base64.StdEncoding.DecodeString(value); err == nil {
		return decoded, nil
	}
	return nil, errors.New("pairing data is not valid base64")
}

func truncate(value string, max int) string {
	if len(value) <= max {
		return value
	}
	return value[:max]
}
