package link

import (
	"bytes"
	"html/template"
	"os"
	"os/exec"
	"runtime"
	"strings"
)

const pairingPageName = "pairing.html"

// PairingPage writes a self-contained HTML page with the QR code. The page contains a live
// credential, so it is written with owner-only permissions and never uploaded anywhere.
func PairingPage(link, name string) (string, error) {
	page, err := renderPairingPage(link, name)
	if err != nil {
		return "", err
	}
	return WriteCredential(pairingPageName, page)
}

func renderPairingPage(link, name string) (string, error) {
	png, err := QRPNG(link, 512)
	if err != nil {
		return "", err
	}
	var page bytes.Buffer
	if err := pairingTemplate.Execute(&page, struct {
		Name string
		Link string
		// template.URL marks the generated data: URI as trusted; without it html/template
		// rewrites the src to #ZgotmplZ and the browser shows no image.
		QR template.URL
	}{
		Name: name,
		Link: link,
		QR:   template.URL(pngDataURI(png)),
	}); err != nil {
		return "", err
	}
	return page.String(), nil
}

// OpenBrowser opens a local file in the user's browser. It is best-effort: on a headless machine
// there is no browser, and the terminal QR is the fallback.
func OpenBrowser(path string) error {
	var command *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		command = exec.Command("cmd", "/c", "start", "", path)
	case "darwin":
		command = exec.Command("open", path)
	default:
		command = exec.Command("xdg-open", path)
	}
	return command.Start()
}

// IsRemoteSession reports whether opening a browser is likely to show something to the user. A SSH
// session on a server has no display; printing the path is more useful than a failed xdg-open.
func IsRemoteSession() bool {
	if runtime.GOOS == "windows" || runtime.GOOS == "darwin" {
		return false
	}
	return strings.TrimSpace(os.Getenv("DISPLAY")) == "" &&
		strings.TrimSpace(os.Getenv("WAYLAND_DISPLAY")) == ""
}

var pairingTemplate = template.Must(template.New("pairing").Parse(`<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Connect nanobot for Android</title>
<style>
  :root { color-scheme: light dark; }
  * { box-sizing: border-box; }
  body {
    margin: 0; min-height: 100vh; display: grid; place-items: center;
    font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
    background: Canvas; color: CanvasText;
  }
  main { width: min(92vw, 30rem); padding: 2rem 1.5rem 2.5rem; text-align: center; }
  h1 { margin: 0 0 .25rem; font-size: 1.35rem; font-weight: 650; }
  .host { margin: 0 0 1.5rem; opacity: .65; font-size: .95rem; }
  .qr {
    display: inline-block; padding: 1rem; border-radius: 1rem;
    background: #fff; box-shadow: 0 1px 3px rgb(0 0 0 / .18);
  }
  .qr img { display: block; width: min(68vw, 18rem); height: auto; }
  .steps { margin: 1.5rem 0 0; padding: 0; list-style: none; text-align: left; }
  .steps li { margin: .45rem 0; line-height: 1.45; font-size: .95rem; }
  .steps b { font-weight: 600; }
  details { margin-top: 1.5rem; text-align: left; font-size: .9rem; }
  summary { cursor: pointer; opacity: .75; }
  textarea {
    width: 100%; height: 6.5rem; margin-top: .6rem; padding: .6rem;
    font: .78rem/1.4 ui-monospace, SFMono-Regular, Consolas, monospace;
    border: 1px solid color-mix(in srgb, CanvasText 22%, transparent);
    border-radius: .5rem; background: color-mix(in srgb, CanvasText 5%, Canvas);
    color: inherit; resize: vertical;
  }
  .warn {
    margin: 1.5rem 0 0; padding: .7rem .85rem; border-radius: .6rem;
    font-size: .82rem; line-height: 1.45; text-align: left;
    background: color-mix(in srgb, #d97706 14%, Canvas);
    border: 1px solid color-mix(in srgb, #d97706 35%, transparent);
  }
</style>
</head>
<body>
<main>
  <h1>Connect nanobot for Android</h1>
  <p class="host">{{.Name}}</p>
  <div class="qr"><img alt="Pairing QR code" src="{{.QR}}"></div>
  <ol class="steps">
    <li><b>1.</b> Open nanobot for Android.</li>
    <li><b>2.</b> Tap the host capsule at the bottom of the side bar.</li>
    <li><b>3.</b> Choose <b>Scan pairing code</b> and point the camera here.</li>
  </ol>
  <details>
    <summary>Show the pairing link</summary>
    <textarea readonly spellcheck="false" onclick="this.select()">{{.Link}}</textarea>
  </details>
  <p class="warn">This code is a password. Anyone who scans it can reach this PC through the relay. Share it only with your own phone, and press <b>Reset</b> in nanobot-link if it leaks.</p>
</main>
</body>
</html>
`))
