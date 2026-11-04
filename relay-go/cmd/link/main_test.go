package main

import (
	"io"
	"os"
	"strings"
	"testing"

	"nanobot/relay/internal/link"
)

// The tiers exist so a 29-row terminal can still show the whole code. Keep their line counts from
// drifting when the link format or QR library changes.
func TestQRTiersFitShortTerminals(t *testing.T) {
	raw := "nanobot://pair?relay=ws://a000a000.aaaaaaaaaa.aa/nanobot&server=000000000000a0a0" +
		"&token=0a0aAA0aAaAA0A0AAAaAAAaAAaAaaAaAaAA0a0-aaA0" +
		"&key=a0aA0aaaAaaAAa0aaaAaA0aaAAAaa0AaaAaAaaAAAAA&secret=aaaaaaa-aaaaa-0000" +
		"&name=AAAAAAA-A0A00A0"
	qr, err := link.TerminalQR(raw, false)
	if err != nil {
		t.Fatal(err)
	}
	for _, test := range []struct{ trim, lines int }{
		{0, 31},
		{1, 29},
		{2, 27},
	} {
		trimmed := trimQRLines(qr, test.trim)
		lines := strings.Split(strings.TrimRight(trimmed, "\n"), "\n")
		if len(lines) != test.lines {
			t.Errorf("trim %d produced %d lines, want %d", test.trim, len(lines), test.lines)
		}
	}

	cases := []struct {
		name          string
		cols, rows    int
		wantOK        bool
		wantHeader    bool
		wantNoNewline bool
		wantLineCount int
	}{
		{"full on a tall window", 120, 33, true, true, false, 31},
		{"medium with header", 120, 31, true, true, false, 29},
		{"medium without header", 120, 30, true, false, false, 29},
		// A 29-row window exactly fits the medium code if the trailing newline is omitted.
		{"29-row window", 120, 29, true, false, true, 29},
		// Below 29 rows no variant keeps a quiet zone, so the pairing page is the only safe option.
		{"28-row window", 120, 28, false, false, false, 0},
		{"too narrow", 60, 40, false, false, false, 0},
	}
	for _, test := range cases {
		t.Run(test.name, func(t *testing.T) {
			tier, ok := chooseQRTier(qr, test.cols, test.rows)
			if ok != test.wantOK {
				t.Fatalf("ok = %v, want %v", ok, test.wantOK)
			}
			if !ok {
				return
			}
			if tier.header != test.wantHeader || tier.noNewline != test.wantNoNewline {
				t.Fatalf("header/noNewline = %v/%v, want %v/%v",
					tier.header, tier.noNewline, test.wantHeader, test.wantNoNewline)
			}
			lines := strings.Split(strings.TrimRight(tier.text, "\n"), "\n")
			if len(lines) != test.wantLineCount {
				t.Fatalf("tier has %d lines, want %d", len(lines), test.wantLineCount)
			}
		})
	}
}

// A release ships with a default relay so the binary can be double-clicked. Explicit choices and
// the address saved by a previous run must win over it, so a self-hosted user is never silently
// moved to the public relay.
func TestResolveRelayURL(t *testing.T) {
	old := defaultRelayURL
	defer func() { defaultRelayURL = old }()
	defaultRelayURL = "wss://built-in.example.com/nanobot"

	cases := []struct {
		name     string
		explicit string
		saved    string
		want     string
	}{
		{"explicit flag wins", "ws://127.0.0.1:8787", "wss://saved.example.com/nanobot", "ws://127.0.0.1:8787"},
		{"saved address beats the built-in relay", "", "wss://saved.example.com/nanobot", "wss://saved.example.com/nanobot"},
		{"built-in relay is the factory default", "", "", "wss://built-in.example.com/nanobot"},
		{"blank values are skipped", "  ", "  ", "wss://built-in.example.com/nanobot"},
	}
	for _, test := range cases {
		t.Run(test.name, func(t *testing.T) {
			if got := resolveRelayURL(test.explicit, test.saved); got != test.want {
				t.Fatalf("resolveRelayURL(%q, %q) = %q, want %q", test.explicit, test.saved, got, test.want)
			}
		})
	}
}

// A dark terminal inverts an uncolored QR, and the phone scanner cannot read the result. The
// printed code therefore forces a white background and black modules.
func TestPrintQRForcesLightBackground(t *testing.T) {
	old := os.Stdout
	read, write, err := os.Pipe()
	if err != nil {
		t.Fatal(err)
	}
	os.Stdout = write
	printQR("ab\ncd", false, false, true)
	_ = write.Close()
	os.Stdout = old

	out, err := io.ReadAll(read)
	if err != nil {
		t.Fatal(err)
	}
	if got, want := string(out), qrStyle+"ab\ncd"+qrReset+"\n"; got != want {
		t.Fatalf("output = %q, want %q", got, want)
	}
}
