# Navine Client Addon Template

Minimal starter project for third-party Navine Client addons.

## Requirements

- JDK 25
- Minecraft 26.1.2
- Fabric Loader 0.19.3
- Navine Client jar in `libs/Navine-Client.jar`

## Setup

1. Copy this folder and rename the mod id in `fabric.mod.json`.
2. Place a built `Navine-Client.jar` in `libs/`.
3. Rename packages under `src/main/java` from `com.example.navineaddon` to your namespace.
4. Run `./gradlew build`.

## Registering your addon

`fabric.mod.json` must declare a `navine` entrypoint:

```json
"entrypoints": {
  "navine": [
    "com.example.navineaddon.NavineExampleAddon"
  ]
}
```

## API overview

Extend `nv.navineclient.NavineAddon` and implement:

- `onInitialize()` - register modules and commands
- `getPackage()` - unique addon id, e.g. `com.example.navineaddon`
- `onRegisterCategories()` - optional custom categories hook

Use `registerModule(Module)` and `registerCommand(Command)` inside `onInitialize()`.

Navine Client loads addons through Fabric entrypoints before `ModuleManager.init()`.

## Example files

- `NavineExampleAddon.java` - addon entry class
- `modules/ExampleModule.java` - sample toggle module with settings
- `commands/ExampleCommand.java` - sample chat command

## Policy

Addons may add modules, commands, and UI extensions. Navine Client does not support or document anti-cheat bypass, packet evasion, or stealth evasion features. Example addons must not include functional bypass logic.

## Testing

Install both Navine Client and your addon jar in the same mods folder, launch the game, and verify your module appears in ClickGUI under Misc.
