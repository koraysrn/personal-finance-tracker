#!/bin/sh
# ---------------------------------------------------------------------------
# Minimal Maven wrapper for Unix-like systems.
# Downloads a local Apache Maven distribution on first use, then delegates
# every call to it. No global Maven installation is required.
# ---------------------------------------------------------------------------
set -e

MVN_VERSION="3.9.9"
BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
MVN_DIR="$BASE_DIR/.mvn/apache-maven-$MVN_VERSION"
MVN_CMD="$MVN_DIR/bin/mvn"

if [ ! -x "$MVN_CMD" ]; then
  echo "Apache Maven $MVN_VERSION not found, downloading..."
  MVN_URL="https://archive.apache.org/dist/maven/maven-3/$MVN_VERSION/binaries/apache-maven-$MVN_VERSION-bin.zip"
  MVN_ZIP="$BASE_DIR/.mvn/apache-maven-$MVN_VERSION-bin.zip"
  mkdir -p "$BASE_DIR/.mvn"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$MVN_URL" -o "$MVN_ZIP"
  else
    wget -q "$MVN_URL" -O "$MVN_ZIP"
  fi
  unzip -q -o "$MVN_ZIP" -d "$BASE_DIR/.mvn"
fi

exec "$MVN_CMD" "$@"
