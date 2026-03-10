# TileWhisper

Proximity voice chat for Old School RuneScape via RuneLite.

## What It Is

TileWhisper is a RuneLite plugin that lets you voice chat with nearby players. When you and another player are on the same OSRS world within a configurable tile range, you can hear each other speak. Audio volume fades as you walk farther away.

**Use Case:** Friends in the same clan, PvM squads, group activities — anyone with the plugin can join the same voice channel just by being nearby in-game.

## How It Works

1. **Audio Capture**: Your microphone is encoded using the Opus codec (32 kbps) and sent to a relay server
2. **Relay Server**: A WebSocket server routes audio between nearby players on the same world
3. **Audio Playback**: Received audio is decoded and played through your speakers with distance-based volume

**Privacy**: Audio is only shared with players who are nearby on the same OSRS world. The server routes audio by world and position — nothing is broadcast globally.

## Features

| Feature | Description |
|---------|-------------|
| **Push-to-Talk** | Hold `V` (configurable key) to transmit voice. On-screen indicator shows when you're broadcasting. |
| **Voice Activity Detection (VAD)** | Alternative mode that auto-transmits when microphone level exceeds a threshold. Adjustable sensitivity. |
| **Proximity Audio** | Hear players on the same world within configurable tile range (default: 15 tiles). |
| **Volume Fade** | Audio decreases linearly with distance using Chebyshev distance (matches OSRS movement). |
| **Per-Player Controls** | Mute or adjust volume (0-200%) for each player individually from the plugin panel. |
| **Speaking Indicators** | Green microphone icon appears over players who are currently talking. |
| **Cross-Platform** | Pure Java Concentus Opus codec works on Linux, macOS (including Apple Silicon), and Windows. |
| **Reliable Transport** | Java 11 WebSocket with semaphore-based send pipelining prevents dropped frames under latency. |

## Configuration

Access settings via **RuneLite → Configuration → TileWhisper**:

- **Voice Activation**: Push-to-Talk or Voice Activity Detection
- **Push to Talk**: Hold key to transmit (default: `V`)
- **VAD Threshold**: Microphone sensitivity for VAD mode (0-100, higher = less sensitive)
- **Voice Range**: Maximum tile distance to hear players (1-50, default: 15)
- **Microphone Volume %**: Input gain (0-200, default: 150)
- **Output Volume %**: Master speaker gain (0-200, default: 150)
- **Mute Incoming Audio**: Global mute for all received voice
- **Server URL**: WebSocket relay server address

## Requirements

- **RuneLite**: Latest stable release from [runelite.net](https://runelite.net/)
- **Java**: JDK 11+ (RuneLite requirement), JDK 21 recommended for development
- **Microphone**: Required for voice capture
- **Speakers**: Required for voice playback
- **Relay Server**: Required — see Server Setup section below

## Development

```bash
./gradlew build      # Compile
./gradlew run         # Run RuneLite dev mode with -ea enabled
./gradlew test        # Run unit tests
```

## Bolt Launcher Setup

Bolt is a Flatpak launcher that handles Jagex credentials. To run TileWhisper with Bolt:

### 1. Build the distribution JAR

```bash
./gradlew shadowJar
```

This creates `build/libs/TileWhisper-1.0-SNAPSHOT-all.jar`.

### 2. Install the Java wrapper

Bolt requires a wrapper script to enable assertions (`-ea`) which are needed for the dev launcher.

```bash
cp java-wrapper.sh ~/.var/app/com.adamcake.Bolt/data/bolt-launcher/java-wrapper.sh
chmod +x ~/.var/app/com.adamcake.Bolt/data/bolt-launcher/java-wrapper.sh
```

### 3. Configure Bolt

1. Open Bolt Launcher
2. Go to RuneLite settings
3. Enable "Use custom RuneLite JAR"
4. Set "Use custom RuneLite Jar" to: `/home/andy/projects/TileWhisper/build/libs/TileWhisper-1.0-SNAPSHOT-all.jar`
5. Set "RuneLite launch command" to: `/home/andy/.var/app/com.adamcake.Bolt/data/bolt-launcher/java-wrapper.sh %command%`
6. Save and restart Bolt

The wrapper script injects the `-ea` flag so RuneLite's plugin system can load the dev launcher.

## Standard RuneLite (Non-Bolt)

For standard RuneLite installation without Bolt:

```bash
./gradlew distJar        # Creates build/libs/TileWhisper-1.0-SNAPSHOT.jar
```

Place `TileWhisper-1.0-SNAPSHOT.jar` in RuneLite's plugin directory and restart RuneLite. The plugin will appear under **Configuration → Wrench icon → Plugin Hub**.

## Server Setup

TileWhisper requires a relay server to route audio between clients. A reference Node.js implementation is available at [TileWhisperServer](../TileWhisperServer).

**Server Requirements:**
- WebSocket support (Node.js `ws` package recommended)
- Routes audio by world number and tile position
- Forwards audio to all clients on the same world within a larger range (50 tiles recommended)
- Optional: `presence` messages to show nearby players in the plugin panel

**Protocol:**
- **Binary Audio**: `[world:4][x:4][y:4][plane:1][usernameLen:1][username:N][audio:M]` (little-endian)
- **JSON Presence**: `{"type":"presence","world":301,"x":3200,"y":3200,"plane":0,"username":"Player"}`
- **JSON Nearby**: `{"type":"nearby","players":[...]}`

## Technical Details

- **Audio Format**: 16kHz, 16-bit, mono PCM
- **Codec**: Opus at 32 kbps (VOIP application mode)
- **Frame Size**: 20ms (320 samples / 640 bytes PCM per frame)
- **Transport**: Java 11 `java.net.http.WebSocket`
- **VAD Algorithm**: RMS (Root Mean Square) with 500ms hold time to prevent speech clipping
- **Volume Calculation**: Linear falloff `1.0 - (distance / maxRange)`, clamped to [0, 1]
- **Send Pipelining**: Semaphore with 3 permits allows 3 concurrent sends without frame drops under typical network latency

## License

See project LICENSE file for license information.