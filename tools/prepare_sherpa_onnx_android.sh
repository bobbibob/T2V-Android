#!/usr/bin/env bash
set -euo pipefail

VERSION="1.13.4"
ARCHIVE="sherpa-onnx-v${VERSION}-android.tar.bz2"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}/${ARCHIVE}"
SHA256="7983fc3de23f6e64148f2fb05fa94a2efaa8c0516cc1573383dc5c7d4d2a43b0"
DESTINATION="app/src/main/jniLibs/arm64-v8a"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

curl --fail --location --retry 3 "$URL" --output "$TEMP_DIR/$ARCHIVE"
echo "$SHA256  $TEMP_DIR/$ARCHIVE" | sha256sum --check -
tar -xjf "$TEMP_DIR/$ARCHIVE" -C "$TEMP_DIR"
mkdir -p "$DESTINATION"
cp "$TEMP_DIR"/jniLibs/arm64-v8a/*.so "$DESTINATION/"

test -s "$DESTINATION/libsherpa-onnx-jni.so"
test -s "$DESTINATION/libonnxruntime.so"
