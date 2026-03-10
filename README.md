# TileWhisper

Proximity voice chat for Old School RuneScape via RuneLite.

## Features

- **Push-to-Talk** or **Voice Activity Detection** (VAD)
- **Proximity audio** — hear players on the same world within configurable tile range
- **Volume fade** — audio decreases linearly with distance
- **Per-player controls** — mute and adjust volume for each player
- **Speaking indicators** — green microphone icon over players who are talking
- **Concentus Opus codec** — pure Java, works on macOS Apple Silicon

## Development

```bash
./gradlew build      # Compile
./gradlew run         # Run RuneLite dev mode
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

Place `TileWhisper-1.0-SNAPSHOT.jar` in RuneLite's plugin directory and restart RuneLite.

## Server Setup

TileWhisper requires a relay server. See [TileWhisperServer](../TileWhisperServer) for Node.js server implementation.