#!/usr/bin/env bash
set -euo pipefail

port="${1:-8765}"
adb reverse "tcp:${port}" "tcp:${port}"
adb reverse --list
