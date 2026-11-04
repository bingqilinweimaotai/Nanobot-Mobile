package link

import (
	"os"
	"path/filepath"
	"strconv"
	"testing"
)

func writeConfig(t *testing.T, name, content string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), name)
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}

func resolveWith(t *testing.T, port *int, secret, path string) *GatewayTarget {
	t.Helper()
	return NewGatewayLocator(port, secret, path).Resolve()
}

func TestGatewayFromJSON(t *testing.T) {
	path := writeConfig(t, "config.json", `{
	  "channels": {
	    "websocket": {
	      "host": "0.0.0.0",
	      "port": 9123,
	      "tokenIssueSecret": "from-json"
	    }
	  }
	}`)
	target := resolveWith(t, nil, "", path)
	if target == nil {
		t.Fatal("no target")
	}
	if target.Host != "127.0.0.1" || target.Port != 9123 || target.Secret != "from-json" {
		t.Fatalf("target = %+v", target)
	}
	if target.Source != path {
		t.Fatalf("source = %q", target.Source)
	}
}

func TestGatewayFromJSONStringPort(t *testing.T) {
	path := writeConfig(t, "config.json", `{"websocket": {"port": "7654"}}`)
	target := resolveWith(t, nil, "", path)
	if target == nil || target.Port != 7654 {
		t.Fatalf("target = %+v", target)
	}
}

func TestGatewaySecretFallbacks(t *testing.T) {
	for name, key := range map[string]string{
		"tokenIssueSecret": "tokenIssueSecret",
		"secret":           "secret",
		"token":            "token",
	} {
		t.Run(key, func(t *testing.T) {
			path := writeConfig(t, name+".json",
				`{"channels":{"websocket":{"port":9000,"`+key+`":"value"}}}`)
			target := resolveWith(t, nil, "", path)
			if target == nil || target.Secret != "value" {
				t.Fatalf("target = %+v", target)
			}
		})
	}
}

func TestGatewayFromYAML(t *testing.T) {
	path := writeConfig(t, "config.yaml", `# nanobot config
channels:
  websocket:
    host: "localhost"
    port: 9200
    tokenIssueSecret: 'yaml-secret'
  other:
    port: 1
`)
	target := resolveWith(t, nil, "", path)
	if target == nil {
		t.Fatal("no target")
	}
	if target.Host != "127.0.0.1" || target.Port != 9200 || target.Secret != "yaml-secret" {
		t.Fatalf("target = %+v", target)
	}
}

func TestGatewayExplicitOverrides(t *testing.T) {
	path := writeConfig(t, "config.json",
		`{"channels":{"websocket":{"port":9000,"tokenIssueSecret":"config-secret","host":"10.0.0.5"}}}`)
	port := 9999
	target := resolveWith(t, &port, "flag-secret", path)
	if target == nil {
		t.Fatal("no target")
	}
	if target.Port != 9999 || target.Secret != "flag-secret" || target.Host != "10.0.0.5" {
		t.Fatalf("target = %+v", target)
	}
}

func TestGatewayExplicitPortWithoutConfig(t *testing.T) {
	port := 8765
	target := resolveWith(t, &port, "", filepath.Join(t.TempDir(), "missing.json"))
	if target == nil {
		t.Fatal("an explicit port must work without a config file")
	}
	if target.Host != "127.0.0.1" || target.Port != 8765 || target.Source != "command line" {
		t.Fatalf("target = %+v", target)
	}
}

func TestGatewayMissingPort(t *testing.T) {
	path := writeConfig(t, "config.json", `{"channels":{"websocket":{"tokenIssueSecret":"x"}}}`)
	if target := resolveWith(t, nil, "", path); target != nil {
		t.Fatalf("target = %+v, want nil without a port", target)
	}
}

func TestGatewayIgnoresUnrelatedPort(t *testing.T) {
	// The health listener is not the WebSocket gateway.
	path := writeConfig(t, "config.json", `{"gateway":{"healthPort":8766}}`)
	if target := resolveWith(t, nil, "", path); target != nil {
		t.Fatalf("target = %+v", target)
	}
}

func TestConnectHost(t *testing.T) {
	cases := map[string]string{
		"":          "127.0.0.1",
		"0.0.0.0":   "127.0.0.1",
		"::":        "127.0.0.1",
		"[::]":      "127.0.0.1",
		"localhost": "127.0.0.1",
		"10.0.0.2":  "10.0.0.2",
	}
	for input, want := range cases {
		if got := connectHost(input); got != want {
			t.Errorf("connectHost(%q) = %q, want %q", input, got, want)
		}
	}
}

func TestGatewayCaches(t *testing.T) {
	path := writeConfig(t, "config.json", `{"channels":{"websocket":{"port":9000}}}`)
	locator := NewGatewayLocator(nil, "", path)
	first := locator.Resolve()
	if first == nil {
		t.Fatal("no target")
	}
	// Change the file; the cache is 2 seconds, so the stale value is expected within that window.
	if err := os.WriteFile(path, []byte(`{"channels":{"websocket":{"port":`+strconv.Itoa(9100)+`}}}`), 0o600); err != nil {
		t.Fatal(err)
	}
	second := locator.Resolve()
	if second == nil || second.Port != first.Port {
		t.Fatalf("cache did not hold: %+v", second)
	}
}
