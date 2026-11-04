package link

import "testing"

func TestControlURL(t *testing.T) {
	cases := []struct {
		base    string
		control string
		data    string
	}{
		{
			base:    "wss://relay.example.com",
			control: "wss://relay.example.com/control",
			data:    "wss://relay.example.com/data?role=agent&serverId=srv",
		},
		{
			base:    "wss://relay.example.com/nanobot",
			control: "wss://relay.example.com/nanobot/control",
			data:    "wss://relay.example.com/nanobot/data?role=agent&serverId=srv",
		},
		{
			base:    "wss://relay.example.com/nanobot/",
			control: "wss://relay.example.com/nanobot/control",
			data:    "wss://relay.example.com/nanobot/data?role=agent&serverId=srv",
		},
		{
			base:    "ws://127.0.0.1:8787/nanobot/control",
			control: "ws://127.0.0.1:8787/nanobot/control",
			data:    "ws://127.0.0.1:8787/nanobot/data?role=agent&serverId=srv",
		},
	}
	for _, test := range cases {
		control, err := ControlURL(test.base)
		if err != nil {
			t.Fatalf("ControlURL(%q): %v", test.base, err)
		}
		if control != test.control {
			t.Errorf("ControlURL(%q) = %q, want %q", test.base, control, test.control)
		}
		data, err := DataURL(control, "srv", "agent")
		if err != nil {
			t.Fatalf("DataURL(%q): %v", control, err)
		}
		if data != test.data {
			t.Errorf("DataURL(%q) = %q, want %q", control, data, test.data)
		}
	}
}

func TestControlURLRejectsBadInput(t *testing.T) {
	for _, value := range []string{"", "https://relay.example.com", "relay.example.com", "wss://"} {
		if _, err := ControlURL(value); err == nil {
			t.Errorf("ControlURL(%q) was accepted", value)
		}
	}
}
