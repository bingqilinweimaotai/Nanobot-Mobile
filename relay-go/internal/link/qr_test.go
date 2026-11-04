package link

import (
	"strings"
	"testing"
)

func TestTerminalQRHasLightQuietZone(t *testing.T) {
	// A QR code's quiet zone is light. go-qrcode's ToSmallString draws it as blocks unless the
	// inverseColor flag is flipped, and a scanner cannot reliably read an inverted code.
	rendered, err := TerminalQR("nanobot://pair?offer=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", false)
	if err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimRight(rendered, "\n"), "\n")
	if len(lines) < 10 {
		t.Fatalf("rendered QR is only %d lines", len(lines))
	}
	for _, index := range []int{0, 1, len(lines) - 1} {
		if strings.TrimSpace(lines[index]) != "" {
			t.Fatalf("line %d is not blank: %q", index, lines[index])
		}
	}
}

func TestQRPNGIsAPNG(t *testing.T) {
	png, err := QRPNG("nanobot://pair?offer=test", 256)
	if err != nil {
		t.Fatal(err)
	}
	if len(png) < 8 || string(png[1:4]) != "PNG" {
		t.Fatalf("not a PNG: % x", png[:min(8, len(png))])
	}
}
