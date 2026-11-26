#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
case "${1:-arm64-v8a}" in
    arm64-v8a)
        variant=arm64
        digest=c11b570621a67cadc718bc42376d9482a09336159b3dc5bcb43cc9634a12a97f
        ;;
    x86_64)
        variant=x64
        digest=779b42587fed0820c4589e57356e3916d778304d216f9caedae9bf89b521fcc6
        ;;
    *) echo "Usage: bash android/fetch-node.sh [arm64-v8a|x86_64]" >&2; exit 2 ;;
esac

archive="libnode_android_${variant}.tar.gz"
temp="$(mktemp -d)"
trap 'rm -rf "$temp"' EXIT
curl --fail --location --retry 2 --silent --show-error \
    "https://github.com/lakutata/libnode/releases/download/v24.20.0/$archive" -o "$temp/$archive"
printf '%s  %s\n' "$digest" "$temp/$archive" | sha256sum -c -
mkdir -p node-runtime
tar -xzf "$temp/$archive" -C node-runtime
