#!/usr/bin/env sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$WRAPPER" ]; then
  exec java -classpath "$WRAPPER" org.gradle.wrapper.GradleWrapperMain "$@"
fi
GV=9.5.0
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/fsr-bootstrap/$GV"
DIST="$BASE/gradle-$GV"
ZIP="$BASE/gradle-$GV-bin.zip"
if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$BASE"
  URL="https://services.gradle.org/distributions/gradle-$GV-bin.zip"
  if command -v curl >/dev/null 2>&1; then curl -L --fail -o "$ZIP" "$URL"; else wget -O "$ZIP" "$URL"; fi
  unzip -q -o "$ZIP" -d "$BASE"
fi
exec "$DIST/bin/gradle" "$@"
