# HoloBuild

Preview any schematic as a hologram, then build it instantly in creative mode —
or watch it rise block by block.

**Minecraft 1.21.1 · NeoForge 21.1.248 · Java 21**

> Inspired by [Schematica](https://www.curseforge.com/minecraft/mc-mods/schematica)
> and [Litematica](https://www.curseforge.com/minecraft/mc-mods/litematica).
> HoloBuild shares their idea, not their code — everything here is written from scratch.

---

## What it does

Drop `.nbt` or `.schem` files into `holobuild/schematics/` in your instance folder,
press **G** to pick one, and right-click with the **Blueprint Wand**.

| | Feature |
|---|---|
| ✅ | See the whole build as a see-through hologram before you place it |
| ✅ | Watch it rise block by block, bottom up and middle outwards |
| ✅ | Rotate and mirror — turning happens around the **centre**, so it spins in place |
| ✅ | Undo with shift + right-click, including chest contents and machine data |
| ✅ | In-game picker with a search box |
| ✅ | Reads Minecraft `.nbt` structures and Sponge `.schem` files |
| ✅ | Creative mode only, so survival worlds stay safe |
| 📋 | Read `.litematic` files |
| 📋 | Save your own selection as a schematic |
| 📋 | Favourites, built-in building library, online library |

✅ done · 📋 planned

## Controls

| Input | Does |
|---|---|
| **G** | Choose a blueprint |
| Look at a block | Hologram and outline appear |
| Right-click | Build |
| Shift + right-click | Cancel a running build, then undo |
| **R** / **M** | Rotate / mirror |
| **Page Up / Down** | Move up / down |

## Settings

`config/holobuild-server.toml`:

- `blocksPerTick` — how fast it builds. Default 40 (about 800 blocks a second).
  Set to `0` to place everything at once, or `5` to really watch it grow.
- `placeSound` — block placing sounds while building.

**Works with [Create](https://www.curseforge.com/minecraft/mc-mods/create):**
Create saves its schematics in the same vanilla `.nbt` format, so HoloBuild reads
them directly.

## Building it yourself

```bash
./gradlew build          # produces build/libs/holobuild-<version>.jar
./gradlew runClient      # launches Minecraft with the mod loaded
```

You need **JDK 21** — Minecraft 1.21.1 will not run on anything else.

## Licence

[MIT](LICENSE) — use it, learn from it, build on it.

---

Made by **Ben** (genben-sb3), age 12, with a lot of curiosity.
