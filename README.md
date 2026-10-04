# Navine Client

Fabric utility client for Minecraft 26.1.2.

## Build

```bat
gradlew.bat build deploy
```

The deploy task copies `Navine-Client.jar` to your CurseForge instance mods folder.

## Built-in Pathfinder

Navine Client ships with its own pathfinding engine. No external mod is required.

### Usage

- `.baritone goto <x> <z>` or `.baritone goto <x> <y> <z>` - walk to coordinates (relative `~` supported)
- `.baritone mine <block> [count]` - locate and mine blocks (for example `.baritone mine diamond_ore`)
- `.baritone follow <player>` - follow a player
- `.baritone stop` - stop the current task
- `.baritone status` - show the current task
- Aliases: `.goto`, `.bt`, `.path`

The **Pathfinder** module (Movement category) controls behavior: Break Blocks lets the pathfinder break obstructions, Bridge places blocks over gaps.
