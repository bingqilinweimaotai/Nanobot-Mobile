RELAY := bin/nanobot-relay
LINK := bin/nanobot-link

# Relay baked into nanobot-link so shipped builds work without --relay. The real address is never
# committed; pass it explicitly when building a release:
#   make build-link-all DEFAULT_RELAY=wss://relay.example.com/nanobot
# CI reads it from a repository variable. An empty value produces a relay-agnostic binary.
DEFAULT_RELAY ?=
LINK_LDFLAGS := -s -w -X main.defaultRelayURL=$(DEFAULT_RELAY)

.PHONY: build build-relay build-link build-linux build-link-all test race vet clean

# Build both programs for the current platform. Useful while developing.
build: build-relay build-link

build-relay:
	CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o $(RELAY) ./cmd/relay

build-link:
	CGO_ENABLED=0 go build -trimpath -ldflags="$(LINK_LDFLAGS)" -o $(LINK) ./cmd/link

# The relay runs on a Linux server. CGO is disabled so the result is a static binary that works on
# any distribution, including Alpine.
build-linux:
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags="-s -w" \
		-o bin/nanobot-relay-linux-amd64 ./cmd/relay
	CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath -ldflags="-s -w" \
		-o bin/nanobot-relay-linux-arm64 ./cmd/relay

# nanobot-link ships to users, so build every desktop platform. LINK_LDFLAGS bakes in the default
# relay; the binary is static on every target, so it needs no runtime installed on the user's PC.
build-link-all:
	CGO_ENABLED=0 GOOS=windows GOARCH=amd64 go build -trimpath -ldflags="$(LINK_LDFLAGS)" \
		-o bin/nanobot-link-windows-amd64.exe ./cmd/link
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags="$(LINK_LDFLAGS)" \
		-o bin/nanobot-link-linux-amd64 ./cmd/link
	CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath -ldflags="$(LINK_LDFLAGS)" \
		-o bin/nanobot-link-linux-arm64 ./cmd/link
	CGO_ENABLED=0 GOOS=darwin GOARCH=amd64 go build -trimpath -ldflags="$(LINK_LDFLAGS)" \
		-o bin/nanobot-link-darwin-amd64 ./cmd/link
	CGO_ENABLED=0 GOOS=darwin GOARCH=arm64 go build -trimpath -ldflags="$(LINK_LDFLAGS)" \
		-o bin/nanobot-link-darwin-arm64 ./cmd/link

test:
	go test ./...

race:
	go test -race ./...

vet:
	go vet ./...

clean:
	rm -rf bin
