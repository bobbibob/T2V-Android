#!/usr/bin/env bash
set -euo pipefail

FFMPEG_TAG="n7.1.1"
FFMPEG_COMMIT="db69d06eeeab4f46da15030a80d539efb4503ca8"
LAME_VERSION="3.100"
LAME_SHA256="ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e"
ANDROID_API="24"
OUTPUT_DIR="${1:-app/src/main/jniLibs/arm64-v8a}"
NDK_ROOT="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
PROJECT_DIR="$(pwd)"

if [[ -z "$NDK_ROOT" || ! -d "$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64" ]]; then
  echo "ANDROID_NDK_HOME must point to an Android NDK with a Linux LLVM toolchain" >&2
  exit 1
fi

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
git clone --depth 1 --branch "$FFMPEG_TAG" https://github.com/FFmpeg/FFmpeg.git "$WORK_DIR/ffmpeg"
ACTUAL_COMMIT="$(git -C "$WORK_DIR/ffmpeg" rev-parse HEAD)"
if [[ "$ACTUAL_COMMIT" != "$FFMPEG_COMMIT" ]]; then
  echo "FFmpeg source verification failed: expected $FFMPEG_COMMIT, got $ACTUAL_COMMIT" >&2
  exit 1
fi

TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64"
PREFIX="$WORK_DIR/install"
LAME_PREFIX="$WORK_DIR/lame-install"
LAME_ARCHIVE="$WORK_DIR/lame-${LAME_VERSION}.tar.gz"

curl --fail --location --retry 3 \
  "https://downloads.sourceforge.net/project/lame/lame/${LAME_VERSION}/lame-${LAME_VERSION}.tar.gz" \
  --output "$LAME_ARCHIVE"
echo "${LAME_SHA256}  ${LAME_ARCHIVE}" | sha256sum --check
tar -xzf "$LAME_ARCHIVE" -C "$WORK_DIR"
cd "$WORK_DIR/lame-${LAME_VERSION}"
CC="$TOOLCHAIN/bin/aarch64-linux-android${ANDROID_API}-clang" \
AR="$TOOLCHAIN/bin/llvm-ar" \
RANLIB="$TOOLCHAIN/bin/llvm-ranlib" \
CFLAGS="-O3 -fPIC" \
./configure \
  --host=aarch64-linux-android \
  --prefix="$LAME_PREFIX" \
  --disable-shared \
  --enable-static \
  --disable-frontend \
  --with-pic
make -j"$(nproc)"
make install

cd "$WORK_DIR/ffmpeg"
./configure \
  --prefix="$PREFIX" \
  --target-os=android \
  --arch=aarch64 \
  --cpu=armv8-a \
  --enable-cross-compile \
  --cc="$TOOLCHAIN/bin/aarch64-linux-android${ANDROID_API}-clang" \
  --cxx="$TOOLCHAIN/bin/aarch64-linux-android${ANDROID_API}-clang++" \
  --ar="$TOOLCHAIN/bin/llvm-ar" \
  --nm="$TOOLCHAIN/bin/llvm-nm" \
  --ranlib="$TOOLCHAIN/bin/llvm-ranlib" \
  --strip="$TOOLCHAIN/bin/llvm-strip" \
  --disable-shared \
  --enable-static \
  --disable-doc \
  --disable-debug \
  --disable-ffplay \
  --disable-ffprobe \
  --enable-ffmpeg \
  --disable-network \
  --disable-autodetect \
  --enable-gpl \
  --enable-libmp3lame \
  --extra-cflags="-I$LAME_PREFIX/include" \
  --extra-ldflags="-L$LAME_PREFIX/lib" \
  --enable-small
make -j"$(nproc)" ffmpeg

mkdir -p "$PROJECT_DIR/$OUTPUT_DIR"
cp ffmpeg "$PROJECT_DIR/$OUTPUT_DIR/libffmpeg_exec.so"
"$TOOLCHAIN/bin/llvm-strip" "$PROJECT_DIR/$OUTPUT_DIR/libffmpeg_exec.so"
sha256sum "$PROJECT_DIR/$OUTPUT_DIR/libffmpeg_exec.so"
