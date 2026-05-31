# ElytraWEEE

Automatic elytra deploy for **Minecraft 26.1.2** (Fabric). Hold any firework rocket and jump — your elytra is equipped automatically. One jump is enough.

## Features

- **Jump to deploy** — while holding any firework rocket, jump and your elytra is equipped instantly. Single-jump or double-jump (configurable).
- **Safe chestplate swap** — your chestplate is moved into your inventory, never dropped. All swaps go through normal inventory actions, so they work in singleplayer and on servers.
- **Land-first swap back** — after you land, your chestplate is put back on once you are no longer holding a firework. Keep holding a firework after landing to stay in the elytra and take off again. Swap-back never happens mid-air.
- **Configurable** — in-game settings via Cloth Config, reachable through Mod Menu, with a Settings tab and an Info tab.

## Requirements

- Minecraft 26.1.2 (Fabric)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Cloth Config](https://modrinth.com/mod/cloth-config)
- [Mod Menu](https://modrinth.com/mod/modmenu) (optional, to open the settings screen)

## Usage

1. Keep an elytra and a firework rocket in your inventory.
2. Hold a firework rocket and jump — the elytra is equipped automatically.
3. Glide and boost as usual.
4. Land, then switch off the firework — your chestplate goes back on.

## Settings

Open **Mod Menu → ElytraWEEE → Settings**:

- **Enable ElytraWEEE** — master switch.
- **Jump mode** — single jump or double jump.
- **Swap chestplate back after landing (no firework)** — restore your chestplate after landing, once you stop holding a firework.
- **Always swap chestplate back the instant you land** — restore your chestplate the moment you land, even while still holding a firework (off by default).

## Building

```
./gradlew build
```

The built jar is produced in `build/libs/`. Requires JDK 25.

## License

[MIT](LICENSE)
