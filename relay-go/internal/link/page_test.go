package link

import (
	"strings"
	"testing"
)

func TestPairingPageEmbedsTheQRCode(t *testing.T) {
	link := "nanobot://pair?relay=ws://relay.example.com/nanobot&server=server-123&token=token&key=key&secret=s"
	page, err := renderPairingPage(link, "Workshop PC")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(page, `src="data:image/png;base64,`) {
		t.Fatalf("page has no embedded QR image: %s", page)
	}
	if strings.Contains(page, "ZgotmplZ") {
		t.Fatalf("html/template rejected the QR data URI: %s", page)
	}
	if !strings.Contains(page, "nanobot://pair?relay=ws://relay.example.com/nanobot") {
		t.Fatal("page does not show the pairing link")
	}
}
