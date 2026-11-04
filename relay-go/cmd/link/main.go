// Command link is nanobot-link: the program a user runs on the PC that hosts nanobot.
//
// It prints a QR code, then keeps an outbound connection to the relay. Phones that scanned the code
// reach the local nanobot gateway through that connection. The PC needs no inbound port, no public
// IP, and no VPN.
package main

import (
	"context"
	"crypto/ed25519"
	"encoding/base64"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"syscall"

	"golang.org/x/term"

	"nanobot/relay/internal/link"
)

const version = "0.1.0"

// defaultRelayURL is the relay this build points at when the user gives no --relay and has not
// saved one. Packagers set it at build time:
//
//	go build -ldflags "-X main.defaultRelayURL=wss://relay.example.com/nanobot" ./cmd/link
//
// An empty value keeps the relay-agnostic behaviour: a first run without --relay stops with an
// error instead of connecting somewhere the user did not choose.
var defaultRelayURL = ""

func main() {
	if err := run(os.Args[1:]); err != nil {
		if errors.Is(err, flag.ErrHelp) {
			return
		}
		fmt.Fprintf(os.Stderr, "nanobot-link: %v\n", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	flags := flag.NewFlagSet("nanobot-link", flag.ContinueOnError)
	flags.SetOutput(os.Stderr)

	relay := flags.String("relay", env("NANOBOT_RELAY"), "relay control URL, e.g. wss://relay.example.com/nanobot (default: the relay built into this binary)")
	relayKey := flags.String("relay-key", env("NANOBOT_RELAY_KEY"), "relay shared admission key (env NANOBOT_RELAY_KEY)")
	signKey := flags.String("sign-key", env("NANOBOT_SIGN_KEY"), "Ed25519 private key for signed admission: base64, or a file path")
	portText := flags.String("port", env("NANOBOT_PORT"), "nanobot WebSocket port (default: channels.websocket.port)")
	secret := flags.String("secret", env("NANOBOT_SECRET"), "gateway tokenIssueSecret (default: from the nanobot config)")
	configPath := flags.String("config", env("NANOBOT_CONFIG"), "nanobot config file (default: ~/.nanobot/config.json)")
	name := flags.String("name", env("NANOBOT_NAME"), "name shown on the phone (default: this computer's name)")
	reset := flags.Bool("reset", false, "new server id, client token, and pair key (re-pair every phone)")
	printOnly := flags.Bool("print", false, "print the pairing link and QR, then exit")
	noQR := flags.Bool("no-qr", false, "do not print a terminal QR code")
	inverseQR := flags.Bool("inverse-qr", false, "draw the QR for a dark background without forcing colors")
	noBrowser := flags.Bool("no-browser", false, "do not open the pairing page in a browser")
	debug := flags.Bool("debug", false, "verbose logs")
	showVersion := flags.Bool("version", false, "print the version and exit")

	flags.Usage = func() {
		fmt.Fprintf(flags.Output(), `nanobot-link %s — connect nanobot for Android to this PC

Usage: nanobot-link [options]

  --relay <url>     Relay control URL, e.g. wss://relay.example.com/nanobot
                    (env NANOBOT_RELAY; defaults to the address saved by a
                    previous run, then the one built into this binary)
  --relay-key <key> Shared admission key for a relay running in shared-key mode
                    (env NANOBOT_RELAY_KEY)
  --sign-key <key>  Ed25519 private key for a relay running in signed mode:
                    base64url seed, or a file containing one (env NANOBOT_SIGN_KEY)
  --port <port>     nanobot WebSocket port (default: channels.websocket.port)
  --secret <text>   gateway tokenIssueSecret (default: from the nanobot config)
  --config <path>   nanobot config file (default: ~/.nanobot/config.json)
  --name <name>     Name shown on the phone (default: this computer's name)
  --reset           New server id, client token, and pair key (re-pair every phone)
  --print           Print the pairing link and QR, then exit
  --no-qr           Do not print a terminal QR code
  --inverse-qr      Draw the QR for a dark background without forcing colors
                    (only needed if this terminal ignores ANSI colors)
  --no-browser      Do not open the pairing page in a browser
  --debug           Verbose logs
  --version         Print the version and exit
  -h, --help        Show this help

nanobot-link reads channels.websocket.port and channels.websocket.tokenIssueSecret
from the nanobot config. It never assumes port 8765 and does not use the health
port from ~/.nanobot/run/gateway.json.

The pairing link contains live credentials. Treat it like a password and share it
only with your own phone.
`, version)
	}

	if err := flags.Parse(args); err != nil {
		return err
	}
	if flags.NArg() > 0 {
		return fmt.Errorf("unexpected argument: %s", flags.Arg(0))
	}
	if *showVersion {
		fmt.Println("nanobot-link", version)
		return nil
	}

	var explicitPort *int
	if strings.TrimSpace(*portText) != "" {
		value, err := strconv.Atoi(strings.TrimSpace(*portText))
		if err != nil || value < 1 || value > 65535 {
			return errors.New("--port must be a number between 1 and 65535")
		}
		explicitPort = &value
	}

	logLevel := slog.LevelInfo
	if *debug {
		logLevel = slog.LevelDebug
	}
	log := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: logLevel}))

	state, err := link.LoadOrCreate(*reset)
	if err != nil {
		return err
	}

	relayURL := resolveRelayURL(*relay, state.Relay)
	if relayURL == "" {
		fmt.Fprint(os.Stderr, noRelayHelp())
		return errors.New("no relay URL configured")
	}
	controlURL, err := link.ControlURL(relayURL)
	if err != nil {
		return err
	}
	// Remember an address the user chose, so the next run needs no flag. The built-in default is
	// deliberately not persisted: a later release may ship a different one, and a saved copy would
	// otherwise mask it forever.
	if explicitRelay := strings.TrimSpace(*relay); explicitRelay != "" && explicitRelay != state.Relay {
		state.Relay = explicitRelay
		if err := state.Save(); err != nil {
			log.Warn("could not remember the relay URL", "err", err)
		}
	}

	locator := link.NewGatewayLocator(explicitPort, *secret, *configPath)
	target := locator.Resolve()
	if target == nil {
		fmt.Fprintf(os.Stderr,
			"nanobot-link: could not find channels.websocket.port in the nanobot config.\n"+
				"Pass --port <port> or --config <path>.\nLooked in: %s\n",
			strings.Join(link.DefaultConfigPaths(), ", "))
		return errors.New("no nanobot gateway found")
	}

	pairKey, err := decodePairKey(state.PairKey)
	if err != nil {
		return err
	}
	displayName := firstNonEmpty(strings.TrimSpace(*name), defaultName())

	offer := link.PairingOffer{
		V:           link.PairingVersion,
		Kind:        link.KindRelay,
		Name:        displayName,
		Relay:       relayURL,
		ServerID:    state.ServerID,
		ClientToken: state.ClientToken,
		PairKey:     state.PairKey,
		Secret:      target.Secret,
		GatewayPort: &target.Port,
		Platform:    platformName(),
	}
	pairingLink, err := link.EncodePairingLink(offer)
	if err != nil {
		return fmt.Errorf("could not build the pairing link: %w", err)
	}

	printBanner(displayName, target, controlURL, pairingLink)
	if target.Secret == "" {
		fmt.Println("  warning: no gateway secret found; set channels.websocket.tokenIssueSecret")
		fmt.Println()
	}

	qrText, qrErr := link.TerminalQR(pairingLink, *inverseQR)
	if qrErr != nil {
		log.Warn("could not render the terminal QR code", "err", qrErr)
	}

	if path, err := link.WriteCredential("pairing-link.txt", pairingLink+"\n"); err == nil {
		fmt.Println("Pairing link saved to", path)
	}
	if qrText != "" {
		if path, err := link.WriteCredential("pairing-qr.txt", qrText); err == nil {
			fmt.Println("QR text saved to", path)
		}
	}
	pagePath, pageErr := link.PairingPage(pairingLink, displayName)
	if pageErr == nil {
		fmt.Println("Pairing page saved to", pagePath)
	}
	fmt.Println()

	if *printOnly {
		printTerminalQR(qrText, *inverseQR, pagePath, *noQR)
		return nil
	}

	if pageErr == nil && !*noBrowser {
		switch {
		case link.IsRemoteSession():
			fmt.Println("No desktop session detected; open the pairing page on a machine with a browser,")
			fmt.Println("or scan the terminal QR code below.")
		case link.OpenBrowser(pagePath) == nil:
			fmt.Println("Opened the pairing page in your browser.")
		default:
			fmt.Println("Open the pairing page in a browser to scan the QR code.")
		}
		fmt.Println()
	}

	signPrivateKey, err := loadSignKey(*signKey)
	if err != nil {
		return err
	}

	// Print the QR after the connection lines, so nothing scrolls it off screen while scanning.
	var qrOnce sync.Once
	client, err := link.NewClient(link.Config{
		Relay:       relayURL,
		ServerID:    state.ServerID,
		ClientToken: state.ClientToken,
		PairKey:     pairKey,
		RelayKey:    strings.TrimSpace(*relayKey),
		SignKey:     signPrivateKey,
		Locator:     locator,
		Log:         log,
		OnConnected: func() {
			fmt.Printf("Ready. Waiting for a paired phone. Gateway: %s\n", target.Addr())
			qrOnce.Do(func() {
				printTerminalQR(qrText, *inverseQR, pagePath, *noQR)
			})
		},
	})
	if err != nil {
		return err
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	fmt.Println("nanobot-link is running. Keep this window open; press Ctrl+C to stop.")
	client.Run(ctx)
	fmt.Println("Stopped.")
	return nil
}

// printTerminalQR prints the QR only when the visible terminal can show it whole. A QR taller than
// the window cannot be scanned: scrolling shows one part and hides another.
//
// The full symbol carries a four-module quiet zone on every side. The medium tier drops one
// half-block line from the top and bottom, leaving two modules of quiet zone, which scanners still
// accept. The borderless variant is never used: a code with no quiet zone does not decode. A window
// that is exactly filled gets no trailing newline, which would otherwise scroll the top row away.
// There is no smaller standard QR encoding of these credentials, so windows below 29 rows use the
// pairing page.
func printTerminalQR(qrText string, darkTerminal bool, pagePath string, disabled bool) {
	if disabled || qrText == "" {
		return
	}
	cols, rows, err := term.GetSize(int(os.Stdout.Fd()))
	if err != nil {
		printQR(trimQRLines(qrText, 0), true, false, false)
		return
	}
	// Paint the code black-on-white so its polarity does not depend on the terminal theme. Without
	// this, a dark terminal renders the light modules as blocks and inverts the code, and the
	// zxing-based phone scanner refuses inverted codes. --inverse-qr keeps the uncolored rendering
	// for terminals that ignore ANSI escapes.
	color := !darkTerminal && enableANSI()
	if tier, ok := chooseQRTier(qrText, cols, rows); ok {
		printQR(tier.text, tier.header, tier.noNewline, color)
		return
	}

	// Nothing fits: report the smallest variant instead of printing a partial code.
	qr := trimQRLines(qrText, 1) // medium QR, no header, no trailing newline
	width, height := qrDimensions(qr)
	needed := height
	fmt.Printf("The QR needs %d columns x %d lines; this terminal is %dx%d, so it would be cut off.\n",
		width, needed, cols, rows)
	if pagePath != "" {
		fmt.Printf("Open %s in a browser to scan it, or enlarge the terminal to at least %dx%d.\n",
			pagePath, width+1, needed)
	} else {
		fmt.Printf("Enlarge the terminal to at least %dx%d and run nanobot-link again.\n",
			width+1, needed)
	}
}

type qrTier struct {
	text      string
	header    bool
	noNewline bool
}

// chooseQRTier picks the largest QR that fits the visible window. Larger variants keep more of the
// quiet zone, which scanners prefer. A window exactly filled by the code gets no trailing newline;
// that newline would scroll the top row off. Trimming the border is never safe: a code with no
// quiet zone fails to decode, so short windows fall back to the pairing page.
func chooseQRTier(qrText string, cols, rows int) (qrTier, bool) {
	tiers := []struct {
		trim      int
		header    bool
		noNewline bool
	}{
		{0, true, false},  // 31 lines, full quiet zone
		{1, true, false},  // 29 lines, two modules of quiet zone
		{1, false, false}, // 29 lines, no header
		{1, false, true},  // 29 lines, exactly filling the window
	}
	for _, tier := range tiers {
		qr := trimQRLines(qrText, tier.trim)
		width, height := qrDimensions(qr)
		needed := height
		if !tier.noNewline {
			needed++ // the newline after the last line
		}
		if tier.header {
			needed++
		}
		if width < cols && needed <= rows {
			return qrTier{
				text:      qr,
				header:    tier.header,
				noNewline: tier.noNewline,
			}, true
		}
	}
	return qrTier{}, false
}

// trimQRLines drops n half-block lines (two module rows each) from the top and bottom of the code.
func trimQRLines(qrText string, n int) string {
	if n <= 0 {
		return qrText
	}
	lines := strings.Split(strings.TrimRight(qrText, "\n"), "\n")
	if len(lines) <= 2*n+1 {
		return qrText
	}
	return strings.Join(lines[n:len(lines)-n], "\n")
}

// qrStyle and qrReset force a white background with black modules. The QR's polarity is then
// independent of the terminal theme, which matters because phone scanners reject inverted codes.
const (
	qrStyle = "\x1b[30;47m"
	qrReset = "\x1b[0m"
)

func printQR(qr string, header, noNewline, color bool) {
	if header {
		fmt.Println("Scan this with nanobot for Android:")
	}
	qr = strings.TrimRight(qr, "\n")
	if color {
		fmt.Print(qrStyle)
	}
	fmt.Print(qr)
	if color {
		fmt.Print(qrReset)
	}
	if !noNewline {
		fmt.Println()
	}
}

func qrDimensions(qrText string) (width, height int) {
	for _, line := range strings.Split(strings.TrimRight(qrText, "\n"), "\n") {
		if runes := len([]rune(line)); runes > width {
			width = runes
		}
		height++
	}
	return width, height
}

func printBanner(name string, target *link.GatewayTarget, controlURL, pairingLink string) {
	fmt.Printf("\nnanobot-link %s\n\n", version)
	fmt.Printf("  PC:      %s\n", name)
	fmt.Printf("  gateway: %s  (from %s)\n", target.Addr(), target.Source)
	fmt.Printf("  relay:   %s\n", controlURL)
	fmt.Printf("  state:   %s\n", statePathOrUnknown())
	fmt.Println()
	fmt.Println("Pairing link (treat it like a password):")
	fmt.Println(pairingLink)
	fmt.Println()
}

func statePathOrUnknown() string {
	path, err := link.StatePath()
	if err != nil {
		return "(unknown)"
	}
	return path
}

func noRelayHelp() string {
	return `nanobot-link: no relay URL configured.

Pass --relay wss://relay.example.com/nanobot or set NANOBOT_RELAY.
The URL is saved after the first run. Append the mount prefix if the relay is
mounted below the domain root, e.g. wss://relay.example.com/nanobot.
`
}

func decodePairKey(encoded string) ([]byte, error) {
	key, err := base64.RawURLEncoding.DecodeString(encoded)
	if err != nil {
		if padded, paddedErr := base64.URLEncoding.DecodeString(encoded); paddedErr == nil {
			key = padded
		} else {
			return nil, errors.New("the stored pair key is not valid base64url; run with --reset")
		}
	}
	if len(key) != 32 {
		return nil, errors.New("the stored pair key is not 32 bytes; run with --reset")
	}
	return key, nil
}

// loadSignKey accepts a base64url Ed25519 seed (32 bytes) or private key (64 bytes), or a path to a
// file containing one. The relay only ever sees the signature.
func loadSignKey(value string) (ed25519.PrivateKey, error) {
	value = strings.TrimSpace(value)
	if value == "" {
		return nil, nil
	}
	if data, err := os.ReadFile(value); err == nil {
		value = strings.TrimSpace(string(data))
	}
	raw, err := base64.RawURLEncoding.DecodeString(value)
	if err != nil {
		if padded, paddedErr := base64.URLEncoding.DecodeString(value); paddedErr == nil {
			raw = padded
		} else {
			return nil, errors.New("--sign-key is not valid base64url")
		}
	}
	switch len(raw) {
	case ed25519.SeedSize:
		return ed25519.NewKeyFromSeed(raw), nil
	case ed25519.PrivateKeySize:
		return ed25519.PrivateKey(raw), nil
	default:
		return nil, errors.New("--sign-key must be a 32-byte seed or a 64-byte private key")
	}
}

func defaultName() string {
	if hostname, err := os.Hostname(); err == nil && strings.TrimSpace(hostname) != "" {
		return strings.TrimSpace(hostname)
	}
	return "PC"
}

func platformName() string {
	switch runtime.GOOS {
	case "windows":
		return "Windows"
	case "darwin":
		return "macOS"
	case "linux":
		return "Linux"
	default:
		return runtime.GOOS
	}
}

func env(name string) string {
	return strings.TrimSpace(os.Getenv(name))
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value != "" {
			return value
		}
	}
	return ""
}

// resolveRelayURL picks the relay to use: an explicit --relay (or NANOBOT_RELAY), then the address
// saved by a previous run, then the address compiled into this build. The saved address must beat
// the built-in default, or a new release would silently move a user off a self-hosted relay.
func resolveRelayURL(explicit, saved string) string {
	return firstNonEmpty(strings.TrimSpace(explicit), strings.TrimSpace(saved), strings.TrimSpace(defaultRelayURL))
}
