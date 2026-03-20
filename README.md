# TileWhisper

Proximity voice chat for Old School RuneScape via RuneLite.

## What It Is

TileWhisper is a RuneLite plugin that lets you voice chat with nearby players. When you and another player are on the same OSRS world within a configurable tile range, you can hear each other speak. Volume fades as you walk farther away.

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
| **Per-Player Controls** | Mute or adjust volume (0–200%) for each player individually from the plugin panel. |
| **Speaking Indicators** | Green microphone icon appears over players who are currently talking. |
| **Cross-Platform** | Pure Java Concentus Opus codec works on Linux, macOS (including Apple Silicon), and Windows. |
| **Reliable Transport** | Java 11 WebSocket with semaphore-based send pipelining prevents dropped frames under latency. |

## Configuration

Access settings via **RuneLite → Configuration → TileWhisper**:

- **Voice Activation**: Push-to-Talk or Voice Activity Detection
- **Push to Talk**: Hold key to transmit (default: `V`)
- **VAD Threshold**: Microphone sensitivity for VAD mode (0–100, higher = less sensitive)
- **Voice Range**: Maximum tile distance to hear players (1–50, default: 15)
- **Microphone Volume %**: Input gain (0–200, default: 150)
- **Output Volume %**: Master speaker gain (0–200, default: 150)
- **Mute Incoming Audio**: Global mute for all received voice
- **Server URL**: WebSocket relay server address

## License

BSD 2-Clause — see [LICENSE](LICENSE).
