# nanobot relay v2 and nanobot-link

Two programs live in this module:

| Program | Runs on | Purpose |
|---|---|---|
| `nanobot-relay` | your server | Rendezvous and forwarding; knows no plaintext |
| `nanobot-link` | the user's PC | Prints the pairing QR and forwards phone streams to `127.0.0.1:<gateway>` |

The relay pairs a PC with one or more phones and forwards opaque frames between them. Tunnel
payloads are encrypted end to end with a pairing key that only the phone and nanobot-link hold, so
the relay never sees prompts, replies, or gateway secrets.

This directory replaces the Kotlin `:relay` and `:agent` modules. The Go implementations are the
ones to ship; the Kotlin relay stays only as a reference until the app moves to protocol v2.

## Protocol at a glance

Every peer opens **two** WebSocket connections:

| Channel | Frames | Purpose |
|---|---|---|
| `/control` | text JSON | hello, ready, presence, error |
| `/data` | binary | all streams, multiplexed |

A data frame is 9 bytes of header plus an opaque payload:

```
+--------+--------------------+------------------+
| type   | streamId (8B, BE)  | payload          |
+--------+--------------------+------------------+
  0x01 OPEN   0x02 DATA   0x03 CLOSE   0x04 ACK   0x05 WINDOW (reserved)
```

Authentication happens inside the first control frame, never in a URL: proxy access logs record
URLs, and the pairing token is a credential. The relay replies with a one-time `sid`; the data
connection presents it in the `X-Nanobot-Session` header, with `?serverId=...&role=...` identifying
the session. Headers are not written to access logs by default.

### Stream lifecycle

```
client  -> OPEN(streamId)
relay   -> forwards OPEN to the agent
agent   -> connects to 127.0.0.1:<gateway port>
           success: ACK          failure: CLOSE(reason)
both    -> DATA...
either  -> CLOSE, which the relay forwards and forgets
```

A client opens a stream for every TCP connection its local proxy accepts. That is what keeps
`NanobotGateway` unchanged: it still talks plain HTTP and WebSocket to `127.0.0.1:<port>`.

## Build

The relay runs on Linux; build the Linux binary from any machine (cross-compilation needs no
toolchain on the server):

```bash
make build-linux
# or, if make is not installed:
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags="-s -w" \
  -o bin/nanobot-relay-linux-amd64 ./cmd/relay
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath -ldflags="-s -w" \
  -o bin/nanobot-relay-linux-arm64 ./cmd/relay
```

`amd64` covers most cloud servers. `arm64` is for Graviton, Oracle's free tier, and ARM Tencent
Cloud instances.

`CGO_ENABLED=0` makes both binaries static, so they run unchanged on glibc and musl distributions.
Copy the matching one to the server; the server does not need Go installed.

If you prefer containers, `docker build -t nanobot-relay .` produces the same binary on a
distroless base.

`make build` builds both programs for the current platform, which is only useful for local testing.
On Windows that produces `nanobot-relay.exe`, which will not run on a Linux server.

nanobot-link ships to users, so it is built for every desktop platform:

```bash
make build-link-all
# bin/nanobot-link-windows-amd64.exe
# bin/nanobot-link-linux-amd64      bin/nanobot-link-linux-arm64
# bin/nanobot-link-darwin-amd64     bin/nanobot-link-darwin-arm64
```

The binaries are static (`CGO_ENABLED=0`), so a user needs nothing installed beyond the executable.

## nanobot-link

The user runs this on the PC that hosts nanobot:

```bash
./nanobot-link --relay wss://relay.example.com/nanobot
```

It prints a pairing link, a terminal QR code, and the path to a local pairing page, then connects
out to the relay and waits. The PC therefore needs no inbound port, no public IP, and no VPN.

```text
  PC:      Workshop PC
  gateway: 127.0.0.1:8765  (from /home/me/.nanobot/config.json)
  relay:   wss://relay.example.com/nanobot/control
```

The gateway port and secret are read from nanobot's own config, exactly as the Kotlin agent did:
`channels.websocket.port` and `channels.websocket.tokenIssueSecret`. Port 8765 is never assumed, and
the health port in `~/.nanobot/run/gateway.json` is never used.

| Flag | Env | Meaning |
|---|---|---|
| `--relay` | `NANOBOT_RELAY` | Relay URL; saved after the first run |
| `--relay-key` | `NANOBOT_RELAY_KEY` | Admission key for a relay in `shared-key` mode |
| `--sign-key` | `NANOBOT_SIGN_KEY` | Ed25519 seed or file for a relay in `signed` mode |
| `--port` | `NANOBOT_PORT` | Override `channels.websocket.port` |
| `--secret` | `NANOBOT_SECRET` | Override `tokenIssueSecret` |
| `--config` | `NANOBOT_CONFIG` | Explicit nanobot config file |
| `--name` | `NANOBOT_NAME` | Name shown on the phone |
| `--reset` | — | Rotate the identity and re-pair every phone |
| `--print` | — | Print the pairing artifacts, then exit |
| `--no-qr`, `--inverse-qr` | — | Skip the terminal QR, or draw it for a dark background without forcing ANSI colors |
| `--no-browser` | — | Do not open the pairing page |
| `--debug` | — | Verbose logs |

The terminal QR is printed black-on-white so its polarity does not depend on the terminal theme; a
dark terminal would otherwise invert it, and phone scanners reject inverted QR codes. `--inverse-qr`
exists only for terminals that ignore ANSI escapes. If the terminal is too short to show the whole
code with its quiet zone, nanobot-link points at the pairing page instead of printing a partial QR.

### Files

Everything lives under `~/.nanobot/link/`:

| File | Contents |
|---|---|
| `state.json` | Server id, client token, pair key, relay URL (owner-only, `0600`) |
| `pairing-link.txt` | The current pairing link |
| `pairing-qr.txt` | The terminal QR as text |
| `pairing.html` | Self-contained pairing page with the QR image |

On first run, nanobot-link imports the identity left by the Kotlin agent at
`~/.nanobot/android/agent.json`, so existing pairings keep working after the upgrade.

A pairing link is a password. Anyone who scans it can reach this PC through the relay. If it leaks,
run `nanobot-link --reset` and pair the phone again. The link carries the gateway secret and the
pair key; the relay URL carries nothing secret, but it is public by nature.

## Quick start

```bash
# local development, no admission checks
go run ./cmd/relay --mode open --listen 127.0.0.1:8787

# production shape (Linux binary, behind Caddy)
export RELAY_MODE=shared-key
export RELAY_KEY="$(openssl rand -base64 32)"
export RELAY_PATH=/nanobot
export RELAY_PUBLIC_URL=wss://relay.example.com/nanobot
./nanobot-relay-linux-amd64 --listen 127.0.0.1:8787
```

Put Caddy in front for TLS:

```caddyfile
relay.example.com {
    reverse_proxy 127.0.0.1:8787
}
```

nanobot-link then uses `wss://relay.example.com/nanobot`. The relay needs no inbound port other
than the proxy's 443.
## Configuration

| Variable | Flag | Default | Meaning |
|---|---|---|---|
| `RELAY_LISTEN` | `--listen` | `127.0.0.1:8787` | Bind address |
| `RELAY_PATH` | `--path` | empty | Mount prefix, e.g. `/nanobot` |
| `RELAY_PUBLIC_URL` | `--public-url` | empty | URL printed at startup |
| `RELAY_MODE` | `--mode` | `shared-key` | `open`, `shared-key`, `signed` |
| `RELAY_KEY` | — | — | Shared secret for `shared-key` mode |
| `RELAY_PUBLIC_KEY` | — | — | Ed25519 public key for `signed` mode |
| `RELAY_TRUST_PROXY` | `--trust-proxy` | `false` | Read the client IP from `X-Forwarded-For` |
| `RELAY_MAX_STREAMS_PER_HOST` | `--max-streams` | `64` | Concurrent streams per server id |
| `RELAY_MAX_CLIENTS_PER_HOST` | `--max-clients` | `16` | Concurrent phones per server id |
| `RELAY_SEND_QUEUE_BYTES` | `--send-queue-bytes` | `4194304` | Outbound queue per connection, bytes |
| `RELAY_SEND_QUEUE_FRAMES` | `--send-queue-frames` | `512` | Outbound queue per connection, frames |
| `RELAY_WRITE_TIMEOUT` | `--write-timeout` | `30s` | Deadline for one WebSocket write |
| `RELAY_PING_INTERVAL` | `--ping-interval` | `25s` | Keepalive ping interval |
| `RELAY_PONG_TIMEOUT` | `--pong-timeout` | `25s` | Pong deadline before a peer is dropped |
| `RELAY_HANDSHAKES_PER_MINUTE` | — | `60` | Per-IP upgrade attempts |
| `RELAY_MAX_PREAUTH_PER_IP` | — | `20` | Per-IP unauthenticated connections |

Secrets are read from the environment only, never from flags: command lines are visible in `ps` and
end up in shell history. `shared-key` mode refuses to start without `RELAY_KEY`; use `--mode open`
only for local testing.

### Admission modes

| Mode | What the peer presents | Trade-off |
|---|---|---|
| `open` | nothing | Development only |
| `shared-key` | a relay-wide secret shipped with the agent | Stops casual abuse; extractable from the agent binary |
| `signed` | an Ed25519 signature over the server id | Relay stores only a public key; needs an issuer |

Quotas are keyed by **server id**, not by IP: the server id is the unit that maps to one user, and
carrier-grade NAT makes IP-based quotas both unfair and easy to rotate around. Per-IP limits exist
only to absorb floods before authentication.

## Deployment

- `Dockerfile` builds a distroless image with a static binary.
- `Caddyfile.example` terminates TLS and forwards WebSocket upgrades.
- `nanobot-relay.service` is a hardened systemd unit (`LimitNOFILE=65535`).
- `relay.env.example` shows the environment file layout.

Run the relay on its own small machine. It is a public-facing pipe: do not co-locate anything
sensitive with it, and treat its IP as public — every pairing link contains the relay address.

## Testing

```bash
go test ./...
go vet ./...
go test -race ./...   # needs cgo and a C compiler
```

Integration tests start the real HTTP handler and speak the wire protocol as a fake agent and a
fake client. They cover stream forwarding, interleaved streams, close propagation, agent presence,
token mismatch, session forgery, reconnection, quotas, and admission. nanobot-link's tests drive the
same relay with a fake gateway and a fake phone, including encryption, gateway-down reporting, and
undecryptable frames.

## Known limitations

- **No v1 compatibility.** The Kotlin relay and agent speak a different protocol. Existing pairings
  must be recreated when the app moves to v2. The identity file, however, is imported.
- **No service installer for nanobot-link.** Users start it from a terminal. A Windows service or a
  systemd `--user` unit is the next ergonomic step.
- **No credit-based flow control yet.** `WINDOW` is reserved in the frame format but endpoints
  ignore it. A single slow stream can therefore push a connection's queue to its limit. The relay
  now drops only that stream (with a warning log) instead of closing the multiplexed data channel,
  so the chat WebSocket and every other stream survive. That keeps the tunnel up, but a sustained
  speed mismatch still fails the transfer itself: end-to-end backpressure is the next step before
  relying on large-file transfers.
- **No bandwidth shaping.** Caps are per server id on stream count and client count only. Use the
  provider's bandwidth limits and per-host quotas until real usage data justifies a shaper.
- **No metrics endpoint.** Logs plus `/healthz` are all there is; add Prometheus when operating more
  than one relay.

## Layout

```
cmd/relay/          relay entry point
cmd/link/           nanobot-link entry point (the PC program)
internal/proto/     frame and control-message codecs, shared by both
internal/relay/     sessions, streams, forwarding, backpressure
internal/link/      relay client, mux streams, gateway forwarding, crypto, pairing, QR
internal/admission/ open, shared-key, signed admission
internal/limits/    per-IP flood protection
```
