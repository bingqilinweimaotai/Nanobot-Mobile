# syntax=docker/dockerfile:1
FROM golang:1.23-alpine AS build

WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/nanobot-relay ./cmd/relay

FROM gcr.io/distroless/static-debian12:nonroot

COPY --from=build /out/nanobot-relay /usr/local/bin/nanobot-relay
USER nonroot:nonroot

ENV RELAY_LISTEN=0.0.0.0:8787
EXPOSE 8787

ENTRYPOINT ["/usr/local/bin/nanobot-relay"]
