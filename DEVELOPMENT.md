# TileWhisper — Development Guide

## Prerequisites

- Java 11+ (Java 25 works with `options.release.set(11)`)
- Gradle 9.4+ (required for Java 25 support)

## Building & Running

```bash
./gradlew build      # Compile
./gradlew run        # Launch RuneLite in developer mode
./gradlew test       # Run tests
```

## Relay Server

TileWhisper requires a relay server. A Node.js reference implementation is in the [TileWhisperServer](https://github.com/ahornerr/TileWhisperServer) repository.

```bash
cd server && node server.js   # Start relay server on port 8080
```

Set the **Server URL** config to `ws://localhost:8080` while developing locally.

## Running via Bolt Launcher

Bolt is a Flatpak launcher that handles Jagex credentials.

### 1. Build the shadow JAR

```bash
./gradlew shadowJar
```

Creates `build/libs/TileWhisper-1.0-SNAPSHOT-all.jar`.

### 2. Install the Java wrapper

Bolt requires a wrapper script to inject the `-ea` flag needed by the dev launcher.

**Linux/Unix:**
```bash
cp java-wrapper.sh ~/.var/app/com.adamcake.Bolt/data/bolt-launcher/java-wrapper.sh
chmod +x ~/.var/app/com.adamcake.Bolt/data/bolt-launcher/java-wrapper.sh
```

**Windows:**
```cmd
copy java-wrapper.bat %APPDATA%\Roaming\bolt-launcher\java-wrapper.bat
```

### 3. Configure Bolt

1. Open Bolt Launcher → RuneLite settings
2. Enable **Use custom RuneLite JAR**
3. Set JAR path to: `<repo>/build/libs/TileWhisper-1.0-SNAPSHOT-all.jar`
4. Set launch command to:
   - **Linux/Unix:** `<path-to>/java-wrapper.sh %command%`
   - **Windows:** `<path-to>\java-wrapper.bat %command%`
5. Save and restart Bolt

## Distribution JAR (non-Bolt)

For loading the plugin directly in a standard RuneLite installation:

```bash
./gradlew distJar   # Creates build/libs/TileWhisper-1.0-SNAPSHOT.jar
```

Place the JAR in RuneLite's plugin directory and restart.

## Protocol

- **Binary audio**: `[world:4][x:4][y:4][plane:1][usernameLen:1][username:N][audio:M]` (little-endian)
- **JSON presence**: `{"type":"presence","world":301,"x":3200,"y":3200,"plane":0,"username":"Player"}`
- **JSON nearby**: `{"type":"nearby","players":[...]}`

## Technical Details

- **Audio format**: 16kHz, 16-bit, mono PCM
- **Codec**: Opus at 32 kbps (VOIP application mode)
- **Frame size**: 20ms (320 samples / 640 bytes PCM per frame)
- **Transport**: Java 11 `java.net.http.WebSocket`
- **VAD**: RMS with 500ms hold time to prevent speech clipping
- **Volume**: Linear falloff `1.0 - (distance / maxRange)`, Chebyshev distance
- **Send pipelining**: Semaphore with 3 permits to prevent frame drops under latency
