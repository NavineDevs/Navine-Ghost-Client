# NavineClientAddon

Separate Gradle addon for Navine Client v1.6.5 (Minecraft 26.1.2, Fabric).

## Requirements

- JDK 25
- Built Navine Client jar in `libs/Navine-Client.jar`

## Setup

1. Build the main client from the parent folder:

```bat
cd ..
gradlew.bat build
```

2. Copy the client jar into this project:

```bat
mkdir libs
copy C:\Users\hitbo\Downloads\output\Navine-Client.jar libs\Navine-Client.jar
```

3. Build the addon:

```bat
cd NavineClientAddon
..\gradlew.bat build
```

Output: `NavineClientAddon/build/libs/NavineClientAddon-1.0.0.jar`

## Install

Place both jars in the same mods folder:

- `Navine-Client.jar`
- `NavineClientAddon-1.0.0.jar`

Launch the game, open ClickGUI (default `I`), and look for the **Navine Addon** category with the **Addon HUD** module.

Use **Addon Manager** on the main menu to enable or disable loaded addons.
