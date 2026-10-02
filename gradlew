#!/usr/bin/env sh
set -eu
V=8.7
D="${GRADLE_USER_HOME:-$HOME/.gradle}/bootstrap"
H="$D/gradle-$V"
Z="$D/gradle-$V-bin.zip"
if [ ! -x "$H/bin/gradle" ]; then
  mkdir -p "$D"
  [ -f "$Z" ] || curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-$V-bin.zip" -o "$Z"
  unzip -q -o "$Z" -d "$D"
fi
exec "$H/bin/gradle" "$@"
