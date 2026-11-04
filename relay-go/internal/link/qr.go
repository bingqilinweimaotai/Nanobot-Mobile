package link

import (
	"encoding/base64"
	"fmt"

	qrcode "github.com/skip2/go-qrcode"
)

// TerminalQR renders the pairing link as half-block characters. It is the fallback for headless
// machines where no browser is available. darkTerminal draws light modules on a dark background.
//
// Low error correction keeps the symbol one version smaller than Medium, which is four terminal
// rows — the difference between fitting and not fitting on a laptop screen. The PNG used by the
// pairing page keeps Medium because it has room for it.
//
// go-qrcode's string renderers use their inverseColor flag the opposite way from their PNG
// renderer: `false` prints the quiet zone as blocks. Pass the negation so the common case prints a
// normal dark-on-light code.
func TerminalQR(link string, darkTerminal bool) (string, error) {
	code, err := qrcode.New(link, qrcode.Low)
	if err != nil {
		return "", fmt.Errorf("could not build the QR code: %w", err)
	}
	return code.ToSmallString(!darkTerminal), nil
}

// QRPNG renders the pairing link as a PNG, embedded in the local pairing page.
func QRPNG(link string, size int) ([]byte, error) {
	code, err := qrcode.New(link, qrcode.Medium)
	if err != nil {
		return nil, fmt.Errorf("could not build the QR code: %w", err)
	}
	if size <= 0 {
		size = 512
	}
	png, err := code.PNG(size)
	if err != nil {
		return nil, fmt.Errorf("could not render the QR code: %w", err)
	}
	return png, nil
}

func pngDataURI(png []byte) string {
	return "data:image/png;base64," + base64.StdEncoding.EncodeToString(png)
}
