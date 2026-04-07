# Blaniel Minecraft Mod

Fabric mod for Minecraft 1.20.1 that brings AI-powered NPCs into your world. Each villager connects to a [Blaniel](https://github.com/Lucas-Dono/blaniel) agent with emotions, memory, and contextual dialogue.

This mod serves as a **working proof of concept** for the [Blaniel SDK](https://github.com/Lucas-Dono/blaniel-sdk) — demonstrating how to integrate AI NPCs into a real game.

## Features

- **AI-powered villagers** — Each villager is backed by a Blaniel agent with personality and memory
- **In-game chat** — Talk to NPCs directly through Minecraft commands
- **Emotional sync** — Agent emotions affect villager behavior
- **Simple commands** — Spawn, assign, and chat with a few commands
- **Configurable API** — Point to your own Blaniel instance

## Requirements

- Minecraft 1.20.1
- Fabric Loader 0.15.6+
- Fabric API 0.92.0+
- Java 21+ (compile target: Java 17)

## Installation

1. Download the latest release JAR
2. Place it in `.minecraft/mods/`
3. Launch Minecraft with Fabric profile
4. Configure the API:
   ```
   /blaniel config apiUrl http://your-blaniel-instance:3000
   /blaniel config apiKey your-api-key
   ```

## Usage

```
# List your available agents
/blaniel list

# Spawn a villager linked to an agent
/blaniel spawn <agentId>

# Talk to the nearest Blaniel villager
/blaniel chat Hey, what do you know about this place?

# View current config
/blaniel config
```

| Command | Description |
|---------|-------------|
| `/blaniel spawn <agentId>` | Spawn a villager linked to the given agent |
| `/blaniel list` | List your available agents |
| `/blaniel assign <agentId>` | Assign an agent to the nearest villager |
| `/blaniel chat <message>` | Chat with the nearest Blaniel villager |
| `/blaniel config` | Show current configuration |
| `/blaniel config apiUrl <url>` | Set the API URL |
| `/blaniel config apiKey <key>` | Set the API key |

## Building from Source

```bash
git clone https://github.com/Lucas-Dono/blaniel-mc.git
cd blaniel-mc
./gradlew build
```

The JAR will be at `build/libs/blaniel-mc-0.1.0-alpha.jar`.

### Run in Development

```bash
./gradlew runClient
```

## Project Structure

```
src/
├── main/java/com/blaniel/minecraft/
│   ├── BlanielMod.java                 # Main mod entry
│   ├── config/BlanielConfig.java       # Configuration management
│   ├── entity/BlanielVillagerEntity.java  # Custom villager entity
│   ├── network/BlanielAPIClient.java   # HTTP client for Blaniel API
│   └── command/BlanielCommands.java    # Command registration
└── client/java/com/blaniel/minecraft/
    ├── BlanielModClient.java           # Client-side entry
    └── render/BlanielVillagerRenderer.java  # Custom renderer
```

## Configuration

Config file: `.minecraft/config/blaniel-mc.json`

```json
{
  "apiUrl": "http://localhost:3000",
  "apiKey": "your-api-key",
  "apiEnabled": true
}
```

## Roadmap

- [x] MVP with basic commands
- [x] In-game chat via `/blaniel chat`
- [ ] Interactive chat GUI
- [ ] Emotion-to-animation sync
- [ ] LLM-driven intelligent movement
- [ ] Daily routines (schedule-based)
- [ ] Multiple simultaneous agents
- [ ] Per-agent custom skins
- [ ] Voice integration (TTS)

## Related Projects

- **[Blaniel Platform](https://github.com/Lucas-Dono/blaniel)** — The AI agent engine (emotions, memory, worlds)
- **[Blaniel SDK](https://github.com/Lucas-Dono/blaniel-sdk)** — Rust SDK for AI NPCs (Python, C#, C FFI, Unity)
- **[Blaniel Mobile](https://github.com/Lucas-Dono/blaniel-mobile)** — React Native mobile app

## License

MIT — see [LICENSE](LICENSE).

Copyright (c) 2024-2026 Lucas Donadello
