#!/bin/bash
# Java wrapper that enables assertions and forwards all args to actual java binary

# Bolt passes java binary path as $1, then remaining args
JAVA_BIN="$1"
shift  # Remove first arg (java path), keep rest for JVM

# Run with -ea and forward remaining JVM arguments
exec "$JAVA_BIN" -ea "$@"
