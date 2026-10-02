#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.7"
BOOTSTRAP_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/bootstrap"
GRADLE_HOME="$BOOTSTRAP_DIR/gradle-$GRADLE_VERSION"
ARCHIVE="$BOOTSTRAP_DIR/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$BOOTSTRAP_DIR"
  if [ ! -f "$ARCHIVE" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    curl -fL --retry 3 --retry-delay 2 \
      "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" \
      -o "$ARCHIVE"
  fi
  rm -rf "$GRADLE_HOME"
  unzip -q "$ARCHIVE" -d "$BOOTSTRAP_DIR"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
