package link

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

// GatewayTarget is a locally reachable nanobot gateway.
type GatewayTarget struct {
	Host   string
	Port   int
	Secret string
	// Source names the file the values came from, for diagnostics.
	Source string
}

// Addr is host:port.
func (t GatewayTarget) Addr() string {
	return fmt.Sprintf("%s:%d", t.Host, t.Port)
}

const gatewayCacheTTL = 2 * time.Second

// GatewayLocator finds the gateway port and token-issue secret in nanobot's own config.
//
// The port is deliberately not assumed to be 8765: it is read from channels.websocket.port. The
// health port in ~/.nanobot/run/gateway.json is a different listener and is never used.
type GatewayLocator struct {
	explicitPort   *int
	explicitSecret string
	explicitConfig string

	mu       sync.Mutex
	cached   *GatewayTarget
	cachedAt time.Time
	cachedOK bool
}

// NewGatewayLocator takes optional overrides. Zero values mean "read the config".
func NewGatewayLocator(port *int, secret, configPath string) *GatewayLocator {
	return &GatewayLocator{
		explicitPort:   port,
		explicitSecret: strings.TrimSpace(secret),
		explicitConfig: strings.TrimSpace(configPath),
	}
}

// Resolve returns the gateway to forward to, or nil when no port can be found.
func (l *GatewayLocator) Resolve() *GatewayTarget {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := time.Now()
	if l.cachedOK && now.Sub(l.cachedAt) < gatewayCacheTTL {
		return l.cached
	}
	found := l.discover()
	l.cached = found
	l.cachedAt = now
	l.cachedOK = true
	return found
}

func (l *GatewayLocator) discover() *GatewayTarget {
	for _, path := range l.candidates() {
		info, err := os.Stat(path)
		if err != nil || info.IsDir() {
			continue
		}
		text, err := os.ReadFile(path)
		if err != nil {
			continue
		}
		parsed, ok := parseGatewayConfig(string(text))
		if !ok {
			continue
		}
		port := 0
		switch {
		case l.explicitPort != nil:
			port = *l.explicitPort
		case parsed.Port != nil:
			port = *parsed.Port
		default:
			continue
		}
		secret := l.explicitSecret
		if secret == "" {
			secret = parsed.Secret
		}
		return &GatewayTarget{
			Host:   connectHost(parsed.Host),
			Port:   port,
			Secret: secret,
			Source: path,
		}
	}
	if l.explicitPort != nil {
		return &GatewayTarget{
			Host:   connectHost(""),
			Port:   *l.explicitPort,
			Secret: l.explicitSecret,
			Source: "command line",
		}
	}
	return nil
}

func (l *GatewayLocator) candidates() []string {
	var paths []string
	if l.explicitConfig != "" {
		paths = append(paths, l.explicitConfig)
	}
	if fromEnv := strings.TrimSpace(os.Getenv("NANOBOT_CONFIG")); fromEnv != "" {
		paths = append(paths, fromEnv)
	}
	if l.explicitConfig == "" && strings.TrimSpace(os.Getenv("NANOBOT_CONFIG")) == "" {
		paths = append(paths, DefaultConfigPaths()...)
	}
	return dedupe(paths)
}

// DefaultConfigPaths lists the places nanobot keeps its config, in search order.
func DefaultConfigPaths() []string {
	var paths []string
	if home, err := os.UserHomeDir(); err == nil && home != "" {
		paths = append(paths,
			filepath.Join(home, ".nanobot", "config.json"),
			filepath.Join(home, ".nanobot", "config.yaml"),
			filepath.Join(home, ".nanobot", "config.yml"),
			filepath.Join(home, ".config", "nanobot", "config.json"),
			filepath.Join(home, ".config", "nanobot", "config.yaml"),
			filepath.Join(home, ".config", "nanobot", "config.yml"),
		)
	}
	return append(paths,
		"nanobot.json",
		"nanobot.config.json",
		filepath.Join("config", "nanobot.json"),
	)
}

type parsedGateway struct {
	Host   string
	Port   *int
	Secret string
}

func parseGatewayConfig(text string) (parsedGateway, bool) {
	if parsed, ok := parseGatewayJSON(text); ok {
		return parsed, true
	}
	return parseGatewayYAML(text)
}

func parseGatewayJSON(text string) (parsedGateway, bool) {
	decoder := json.NewDecoder(strings.NewReader(text))
	decoder.UseNumber()
	var root any
	if err := decoder.Decode(&root); err != nil {
		return parsedGateway{}, false
	}
	websocket := findObject(root, "websocket")
	if websocket == nil {
		return parsedGateway{}, false
	}
	parsed := parsedGateway{
		Host: jsonString(websocket["host"]),
		Port: jsonPort(websocket["port"]),
		Secret: firstNonEmpty(
			jsonString(websocket["tokenIssueSecret"]),
			jsonString(websocket["secret"]),
			jsonString(websocket["token"]),
		),
	}
	return parsed, parsed.Port != nil || parsed.Secret != ""
}

// findObject returns the first nested object stored under key, mirroring the Kotlin search so that
// a config's exact shape does not matter.
func findObject(value any, key string) map[string]any {
	object, ok := value.(map[string]any)
	if !ok {
		return nil
	}
	if found, ok := object[key].(map[string]any); ok {
		return found
	}
	for _, child := range object {
		if found := findObject(child, key); found != nil {
			return found
		}
	}
	return nil
}

func jsonString(value any) string {
	switch typed := value.(type) {
	case string:
		return strings.TrimSpace(typed)
	case json.Number:
		return strings.TrimSpace(typed.String())
	default:
		return ""
	}
}

func jsonPort(value any) *int {
	text := jsonString(value)
	if text == "" {
		return nil
	}
	port, err := strconv.Atoi(text)
	if err != nil || port < 1 || port > 65535 {
		return nil
	}
	return &port
}

// parseGatewayYAML is a minimal reader: nanobot configs are normally JSON, but config.yaml is also
// accepted. It understands nested `key: value` mappings only, which covers
// channels.websocket.port and channels.websocket.tokenIssueSecret.
//
// ponytail: a real YAML parser would add a dependency for two scalar lookups.
func parseGatewayYAML(text string) (parsedGateway, bool) {
	type entry struct {
		path  []string
		value string
	}
	type frame struct {
		indent int
		key    string
	}

	var entries []entry
	var stack []frame
	for _, raw := range strings.Split(text, "\n") {
		line := raw
		if index := strings.IndexByte(line, '#'); index >= 0 {
			line = line[:index]
		}
		line = strings.TrimRight(line, " \t\r")
		if strings.TrimSpace(line) == "" {
			continue
		}
		indent := len(line) - len(strings.TrimLeft(line, " \t"))
		trimmed := strings.TrimSpace(line)
		if strings.HasPrefix(trimmed, "- ") {
			continue
		}
		separator := strings.IndexByte(trimmed, ':')
		if separator <= 0 {
			continue
		}
		key := strings.TrimSpace(trimmed[:separator])
		value := strings.TrimSpace(trimmed[separator+1:])
		for len(stack) > 0 && stack[len(stack)-1].indent >= indent {
			stack = stack[:len(stack)-1]
		}
		if value == "" {
			stack = append(stack, frame{indent: indent, key: key})
			continue
		}
		path := make([]string, 0, len(stack)+1)
		for _, parent := range stack {
			path = append(path, parent.key)
		}
		entries = append(entries, entry{path: append(path, key), value: unquoteYAML(value)})
	}

	var parsed parsedGateway
	found := false
	for _, item := range entries {
		if len(item.path) < 3 || item.path[len(item.path)-2] != "websocket" {
			continue
		}
		last := item.path[len(item.path)-1]
		switch last {
		case "port":
			if parsed.Port == nil {
				if port, err := strconv.Atoi(item.value); err == nil && port >= 1 && port <= 65535 {
					parsed.Port = &port
				}
			}
			found = true
		case "host":
			if parsed.Host == "" {
				parsed.Host = item.value
			}
			found = true
		case "tokenIssueSecret", "secret", "token":
			if parsed.Secret == "" {
				parsed.Secret = item.value
			}
			found = true
		}
	}
	if !found {
		return parsedGateway{}, false
	}
	return parsed, parsed.Port != nil || parsed.Secret != ""
}

func unquoteYAML(value string) string {
	value = strings.TrimSpace(value)
	if len(value) >= 2 {
		if (value[0] == '"' && value[len(value)-1] == '"') ||
			(value[0] == '\'' && value[len(value)-1] == '\'') {
			return value[1 : len(value)-1]
		}
	}
	return value
}

// connectHost turns a listen address into an address that can be dialled.
func connectHost(configured string) string {
	host := strings.TrimSpace(configured)
	switch host {
	case "", "0.0.0.0", "::", "[::]", "localhost":
		return "127.0.0.1"
	default:
		return host
	}
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value != "" {
			return value
		}
	}
	return ""
}

func dedupe(values []string) []string {
	seen := make(map[string]struct{}, len(values))
	result := make([]string, 0, len(values))
	for _, value := range values {
		if value == "" {
			continue
		}
		if _, exists := seen[value]; exists {
			continue
		}
		seen[value] = struct{}{}
		result = append(result, value)
	}
	return result
}
