package link

import (
	"encoding/base64"
	"encoding/json"
	"reflect"
	"strings"
	"testing"
)

func sampleOffer(t *testing.T) PairingOffer {
	t.Helper()
	key, err := generateKey()
	if err != nil {
		t.Fatal(err)
	}
	port := 9001
	return PairingOffer{
		V:           PairingVersion,
		Kind:        KindRelay,
		Name:        "Workshop PC",
		Relay:       "wss://relay.example.com/nanobot",
		ServerID:    "0123456789abcdef",
		ClientToken: "token-value",
		PairKey:     encodeKey(key),
		Secret:      "gateway-secret",
		GatewayPort: &port,
		Platform:    "Linux",
	}
}

func TestPairingLinkRoundTrip(t *testing.T) {
	offer := sampleOffer(t)
	link, err := EncodePairingLink(offer)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(link, "nanobot://pair?relay=") {
		t.Fatalf("link = %q", link)
	}
	if strings.Contains(link, "offer=") {
		t.Fatalf("link still carries the bulky JSON offer: %q", link)
	}
	decoded, err := ParsePairingLink(link)
	if err != nil {
		t.Fatal(err)
	}
	// The compact form drops diagnostics the phone does not need; everything else must survive.
	want := offer
	want.GatewayPort = nil
	want.Platform = ""
	if !reflect.DeepEqual(decoded, want) {
		t.Fatalf("decoded = %+v, want %+v", decoded, want)
	}
}

// The terminal QR is only usable when the whole symbol fits on screen. Keep the link short enough
// that the default 61x31 rendering still fits a normal terminal window.
func TestPairingLinkStaysTerminalSized(t *testing.T) {
	port := 8765
	offer := PairingOffer{
		V:           PairingVersion,
		Kind:        KindRelay,
		Name:        "AAAAAAA-A0A00A0",
		Relay:       "ws://a000a000.aaaaaaaaaa.aa/nanobot",
		ServerID:    "000000000000a0a0",
		ClientToken: "0a0aAA0aAaAA0A0AAAaAAAaAAaAaaAaAaAA0a0-aaA0",
		PairKey:     "a0aA0aaaAaaAAa0aaaAaA0aaAAAaa0AaaAaAaaAAAAA",
		Secret:      "aaaaaaa-aaaaa-0000",
		GatewayPort: &port,
		Platform:    "Windows",
	}
	link, err := EncodePairingLink(offer)
	if err != nil {
		t.Fatal(err)
	}
	if len(link) > 230 {
		t.Fatalf("pairing link is %d bytes, want <= 230: %s", len(link), link)
	}
	qr, err := TerminalQR(link, false)
	if err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimRight(qr, "\n"), "\n")
	width := 0
	for _, line := range lines {
		if runes := len([]rune(line)); runes > width {
			width = runes
		}
	}
	if len(lines) > 31 || width > 61 {
		t.Fatalf("terminal QR is %dx%d, want at most 61x31", width, len(lines))
	}
}

func TestPairingLinkEscapesQueryValues(t *testing.T) {
	offer := sampleOffer(t)
	offer.Secret = "a&b=c+d#e f%g"
	offer.Name = "工作 PC"
	link, err := EncodePairingLink(offer)
	if err != nil {
		t.Fatal(err)
	}
	decoded, err := ParsePairingLink(link)
	if err != nil {
		t.Fatal(err)
	}
	if decoded.Secret != offer.Secret {
		t.Fatalf("secret = %q, want %q", decoded.Secret, offer.Secret)
	}
	if decoded.Name != offer.Name {
		t.Fatalf("name = %q, want %q", decoded.Name, offer.Name)
	}
}

func TestDirectPairingLinkRoundTrip(t *testing.T) {
	offer := PairingOffer{
		V:        PairingVersion,
		Kind:     KindDirect,
		Name:     "Home",
		Endpoint: "http://192.168.1.5:8765",
		Secret:   "gateway-secret",
	}
	link, err := EncodePairingLink(offer)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(link, "kind=direct") || !strings.Contains(link, "endpoint=http://192.168.1.5:8765") {
		t.Fatalf("link = %q", link)
	}
	decoded, err := ParsePairingLink(link)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(decoded, offer) {
		t.Fatalf("decoded = %+v, want %+v", decoded, offer)
	}
}

// Links printed by earlier versions carry base64url JSON in ?offer=; keep parsing them so an
// already-printed pairing page does not break after an upgrade.
func TestPairingLinkAcceptsLegacyJSONOffer(t *testing.T) {
	offer := sampleOffer(t)
	payload, err := json.Marshal(offer)
	if err != nil {
		t.Fatal(err)
	}
	link := "nanobot://pair?offer=" + base64.RawURLEncoding.EncodeToString(payload)
	decoded, err := ParsePairingLink(link)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(decoded, offer) {
		t.Fatalf("decoded = %+v, want %+v", decoded, offer)
	}
}

func TestPairingLinkAcceptsBareBase64(t *testing.T) {
	offer := sampleOffer(t)
	payload, err := json.Marshal(offer)
	if err != nil {
		t.Fatal(err)
	}
	bare := base64.RawURLEncoding.EncodeToString(payload)
	decoded, err := ParsePairingLink(bare)
	if err != nil {
		t.Fatal(err)
	}
	if decoded.ServerID != offer.ServerID {
		t.Fatalf("serverId = %q", decoded.ServerID)
	}
}

func TestPairingValidation(t *testing.T) {
	cases := map[string]func(*PairingOffer){
		"bad version":       func(o *PairingOffer) { o.V = 2 },
		"bad kind":          func(o *PairingOffer) { o.Kind = "carrier-pigeon" },
		"bad relay scheme":  func(o *PairingOffer) { o.Relay = "https://relay.example.com" },
		"missing server id": func(o *PairingOffer) { o.ServerID = "" },
		"missing token":     func(o *PairingOffer) { o.ClientToken = "" },
		"missing pair key":  func(o *PairingOffer) { o.PairKey = "" },
		"short pair key":    func(o *PairingOffer) { o.PairKey = base64.RawURLEncoding.EncodeToString([]byte("short")) },
		"bad port":          func(o *PairingOffer) { port := 70000; o.GatewayPort = &port },
	}
	for name, mutate := range cases {
		t.Run(name, func(t *testing.T) {
			offer := sampleOffer(t)
			mutate(&offer)
			if _, err := EncodePairingLink(offer); err == nil {
				t.Fatal("invalid offer was accepted")
			}
		})
	}
}

func TestPairingDisplayName(t *testing.T) {
	offer := sampleOffer(t)
	if got := offer.DisplayName(); got != "Workshop PC" {
		t.Fatalf("display name = %q", got)
	}
	offer.Name = "  "
	if got := offer.DisplayName(); got != "0123456789ab" {
		t.Fatalf("display name = %q", got)
	}
}
