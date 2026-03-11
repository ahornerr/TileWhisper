#!/bin/bash
# TileWhisper Launcher Script
# This script launches TileWhisper with assertions enabled, required by RuneLite's dev mode
# NOTE: Run this script from a normal terminal, NOT from Bolt's Flatpak environment

if [ -n "$FLATPAK_ID" ]; then
    echo "Error: This script cannot run inside Flatpak (Bolt is a Flatpak)"
    echo ""
    echo "Please run from a normal terminal:"
    echo "  cd /path/to/TileWhisper"
    echo "  ./run-tilewhisper.sh"
    echo ""
    echo "Or run the JAR directly:"
    echo "  java -ea -jar build/libs/TileWhisper-1.0-SNAPSHOT-all.jar --developer-mode --debug"
    exit 1
fi

echo "pwd: $(pwd)"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

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
JAVA_ARGS="-ea"
if [ "$(uname)" = "Darwin" ]; then
    JAVA_ARGS="$JAVA_ARGS --add-opens java.desktop/com.apple.eawt=ALL-UNNAMED"
    JAVA_ARGS="$JAVA_ARGS --add-opens java.desktop/com.apple.eawt.event=ALL-UNNAMED"
    JAVA_ARGS="$JAVA_ARGS --add-opens java.desktop/com.apple.laf=ALL-UNNAMED"

    # On macOS, a bare `java` process cannot request microphone permission without an
    # app bundle containing NSMicrophoneUsageDescription in its Info.plist.
    # We create a minimal .app wrapper and launch through it so macOS shows the mic dialog.
    APP_BUNDLE="$PROJECT_DIR/TileWhisper.app"
    APP_MACOS="$APP_BUNDLE/Contents/MacOS"
    APP_PLIST="$APP_BUNDLE/Contents/Info.plist"

    mkdir -p "$APP_MACOS"

    # Write Info.plist with microphone usage description
    cat > "$APP_PLIST" << 'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleIdentifier</key>
    <string>com.tilewhisper.TileWhisper</string>
    <key>CFBundleName</key>
    <string>TileWhisper</string>
    <key>CFBundleExecutable</key>
    <string>TileWhisper</string>
    <key>CFBundleVersion</key>
    <string>1.0</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>NSMicrophoneUsageDescription</key>
    <string>TileWhisper uses your microphone for in-game voice chat.</string>
    <key>LSUIElement</key>
    <false/>
</dict>
</plist>
PLIST

    # Write launcher script inside the bundle (logs to file so we can tail from terminal)
    JAVA_BIN="$(which java)"
    LOG_FILE="/tmp/tilewhisper.log"
    cat > "$APP_MACOS/TileWhisper" << LAUNCHER
#!/bin/bash
exec "$JAVA_BIN" $JAVA_ARGS -jar "$JAR_FILE" --developer-mode --debug > "$LOG_FILE" 2>&1
LAUNCHER
    chmod +x "$APP_MACOS/TileWhisper"

    echo "Launching via .app bundle for macOS microphone permission..."
    echo "(Logs → $LOG_FILE)"
    > "$LOG_FILE"  # truncate log
    open -W "$APP_BUNDLE" &
    OPEN_PID=$!
    # Stream log output to this terminal while the app runs
    tail -f "$LOG_FILE" &
    TAIL_PID=$!
    wait $OPEN_PID
    sleep 0.5
    kill $TAIL_PID 2>/dev/null
    exit 0
fi

java $JAVA_ARGS -jar "$JAR_FILE" --developer-mode --debug
