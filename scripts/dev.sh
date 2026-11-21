#!/bin/sh
# Local development only: no publishing, uploads, or installation into game instances.
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.gradle-user-home}"

action=${1:-check}
target=${2:-26.3}
case "$target" in
    26.2|26.3) ;;
    *) echo 'Supported Minecraft targets: 26.2, 26.3' >&2; exit 2 ;;
esac

if [ "$action" = test ]; then
    exec npm test
fi

if [ -z "${JAVA_HOME:-}" ] && [ "$(uname -s)" = Darwin ]; then
    JAVA_HOME=$(/usr/libexec/java_home -F -v 25 2>/dev/null || true)
    if [ -z "$JAVA_HOME" ]; then
        candidate="$HOME/Library/Application Support/PrismLauncher/java/java-runtime-epsilon"
        if [ -x "$candidate/bin/javac" ]; then JAVA_HOME=$candidate; fi
    fi
    export JAVA_HOME
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if ! command -v javac >/dev/null 2>&1 || ! javac -version 2>&1 | grep -Eq '^javac 25([. ]|$)'; then
    echo 'Set JAVA_HOME to a JDK 25 installation (including javac), then retry.' >&2
    exit 1
fi

case "$action" in
    check)
        java -version
        javac -version
        node --version
        python3 --version
        printf 'Gradle cache: %s\nMinecraft target: %s\n' "$GRADLE_USER_HOME" "$target"
        ;;
    build) exec ./gradlew build "-Pminecraft_version=$target" ;;
    client) exec ./gradlew runClient "-Pminecraft_version=$target" ;;
    standalone) exec python3 scripts/build-standalone.py --minecraft-version "$target" ;;
    backend) exec python3 scripts/test-backend.py --minecraft-version "$target" ;;
    *) echo 'Usage: sh scripts/dev.sh {check|test|build|client|standalone|backend} [26.2|26.3]' >&2; exit 2 ;;
esac
