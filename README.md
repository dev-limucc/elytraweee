# ElytraWEEE 🪂

[![build](https://github.com/dev-limucc/elytraweee/actions/workflows/build.yml/badge.svg)](https://github.com/dev-limucc/elytraweee/actions/workflows/build.yml)

**Hold a firework. Jump. Fly.** Your elytra equips itself and you start gliding instantly — chestplate swapped out safely, never dropped, and put back when you land.

Client-only Fabric mod for **Minecraft 26.1.2**. Works on servers, no server install needed.

<p align="center">
  <img src="https://cdn.modrinth.com/data/vbRcbu5L/images/86fa980f832659d152922d03744ec00b83bff039.webp" alt="Auto-elytra via firework — glide on demand" width="80%" />
</p>

---

## ⚡ What it does

- **Jump + firework → instant flight.** One jump. You glide right away — no second tap.
- **Land → chestplate comes back.** Drop the firework after landing and your chestplate returns automatically.
- **Grace window.** Jumped a hair too early? Grab the firework within ~0.5s and it still launches.
- **Never drops your gear.** All swaps are safe. No chestplate? The elytra just goes back where it came from.

## 🗡️ Fast swap (mace PvP)

One key to flip **elytra ⇄ chestplate instantly — even mid-air.**

- Swap to chestplate, hit, swap back → **you keep gliding** (no re-jump).
- Works even with auto-deploy off.

## ⌨️ Keybinds

Under **Options → Controls → ElytraWEEE** (all unbound by default — set your own):

- **Fast swap** — elytra ⇄ chestplate, anytime
- **Toggle on/off** — flip the mod without a menu
- **Open settings** — config screen in-game

## 🎛️ Settings

**Mod Menu → ElytraWEEE** — tabs for General, Auto-Deploy, Swap-Back, Fast Swap, Info.

> 🧠 The mod only manages an elytra **it** deployed. Put one on yourself? It leaves your gear alone.

---

## 🔧 Build

```
./gradlew build
```

Needs Java 25. Optional deps: Cloth Config + Mod Menu.

## 📦 Requirements

- Minecraft 26.1.2 (Fabric)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Cloth Config](https://modrinth.com/mod/cloth-config)
- [Mod Menu](https://modrinth.com/mod/modmenu) *(optional — opens the settings screen)*

**by dev-limucc · [MIT](LICENSE)**

## Gallery

<p align="center">
  <img src="https://cdn.modrinth.com/data/vbRcbu5L/images/2db18b7ecfef040234adac560b33f3f1d74d70a1.png" alt="ElytraWEEE config screen" width="70%" />
</p>
