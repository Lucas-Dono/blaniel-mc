# Blaniel MC - Minecraft Mod

Fabric 1.20.1 mod that brings [Blaniel](https://blaniel.com) emotional AI agents into Minecraft as NPCs with full conversation capabilities.

## Features

- **AI NPCs** — Spawn agents powered by Blaniel's emotional engine
- **Real-time Chat** — Talk to NPCs with full emotional context
- **Personality System** — Each NPC has unique personality traits and behaviors
- **Social Groups** — NPCs form social groups and interact with each other
- **Proximity Chat** — NPCs chat when players are nearby
- **Custom Skins** — Dynamic skin generation for NPCs
- **Emotional Expressions** — NPCs express emotions through chat bubbles and behaviors

## Requirements

- Minecraft 1.20.1
- Fabric Loader 0.15+
- Fabric API
- Java 17+

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.20.1
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and place in `.minecraft/mods/`
3. Download the latest `blaniel-mc-*.jar` from [Releases](https://github.com/Lucas-Dono/blaniel-mc/releases)
4. Place the jar in `.minecraft/mods/`
5. Configure the mod (see below)

## Configuration

Create or edit `.minecraft/config/blaniel-mc.properties`:

```properties
# Blaniel API connection
blaniel.api.url=http://localhost:3000
blaniel.api.key=your-api-key

# NPC settings
blaniel.npc.spawn-limit=50
blaniel.npc.chat-range=10
blaniel.npc.proximity-check-interval=5000
```

## Building from Source

```bash
git clone https://github.com/Lucas-Dono/blaniel-mc.git
cd blaniel-mc
./gradlew build
# Output: build/libs/blaniel-mc-*.jar
```

## Commands

| Command | Description |
|---------|-------------|
| `/blaniel spawn <agent-id>` | Spawn an AI NPC |
| `/blaniel remove <npc-id>` | Remove an NPC |
| `/blaniel list` | List all NPCs |
| `/blaniel reload` | Reload configuration |

## Related Projects

| Repository | Description | License |
|------------|-------------|---------|
| [blaniel](https://github.com/Lucas-Dono/blaniel) | Core emotional AI engine | BSL 1.1 |
| [blaniel-sdk](https://github.com/Lucas-Dono/blaniel-sdk) | Rust NPC SDK | BSL 1.1 |
| [blaniel-mobile](https://github.com/Lucas-Dono/blaniel-mobile) | React Native + Expo mobile app | MIT |
| [blaniel-mc](https://github.com/Lucas-Dono/blaniel-mc) | This repo — Minecraft mod | MIT |

## License

MIT — see [LICENSE](LICENSE) for details.
