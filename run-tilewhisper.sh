#!/bin/bash
# TileWhisper Launcher Script
# This script launches TileWhisper with assertions enabled, required by RuneLite's dev mode
# NOTE: Run this script from a normal terminal, NOT from Bolt's Flatpak environment

if [ -n "$FLATPAK_ID" ]; then
    echo "Error: This script cannot run inside Flatpak (Bolt is a Flatpak)"
    echo ""
    echo "Please run from a normal terminal:"
    echo "  cd /home/andy/projects/TileWhisper"
    echo "  ./run-tilewhisper.sh"
    echo ""
    echo "Or run the JAR directly:"
    echo "  java -ea -jar build/libs/TileWhisper-1.0-SNAPSHOT-all.jar --developer-mode --debug"
    exit 1
fi

echo "pwd: $(pwd)"

PROJECT_DIR="/home/andy/projects/TileWhisper"

if [ ! -d "$PROJECT_DIR" ]; then
    echo "Error: Cannot find TileWhisper project at $PROJECT_DIR"
    echo "Edit this script to set the correct PROJECT_DIR path"
    exit 1
fi

JAR_FILE="$PROJECT_DIR/build/libs/TileWhisper-1.0-SNAPSHOT-all.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: TileWhisper JAR not found at $JAR_FILE"
    echo "Please run: cd $PROJECT_DIR && ./gradlew shadowJar"
    exit 1
fi

echo "Starting TileWhisper (with -ea enabled)..."
java -ea \
  --add-opens java.desktop/com.apple.eawt=ALL-UNNAMED \
  --add-opens java.desktop/com.apple.eawt.event=ALL-UNNAMED \
  --add-opens java.desktop/com.apple.laf=ALL-UNNAMED \
  -jar "$JAR_FILE" --developer-mode --debug
