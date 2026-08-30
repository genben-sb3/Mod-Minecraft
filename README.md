# HoloBuild

Preview any schematic as a hologram, then build it instantly in creative mode —
or watch it rise block by block.

**Minecraft 1.21.1 · NeoForge 21.1.248 · Java 21**

> Inspired by [Schematica](https://www.curseforge.com/minecraft/mc-mods/schematica)
> and [Litematica](https://www.curseforge.com/minecraft/mc-mods/litematica).
> HoloBuild shares their idea, not their code — everything here is written from scratch.

---

## What it does

Drop `.nbt`, `.schem` or `.litematic` files into `holobuild/schematics/` in your
instance folder, press **G** to pick one, and right-click with the **Blueprint Wand**.

| | Feature |
|---|---|
| ✅ | See the whole build as a see-through hologram before you place it |
| ✅ | Watch it rise block by block, bottom up and middle outwards |
| ✅ | Rotate and mirror — turning happens around the **centre**, so it spins in place |
| ✅ | Undo with shift + right-click, including chest contents and machine data |
| ✅ | In-game picker with a search box and favourites |
| ✅ | Reads `.nbt`, `.schem` and `.litematic` files |
| ✅ | Save your own builds with the Selection Wand |
| ✅ | Creative mode only, so survival worlds stay safe |
| 📋 | Built-in building library, online library |

✅ done · 📋 planned

## Controls

| Input | Does |
|---|---|
| **G** | Choose a blueprint |
| Look at a block | Hologram and outline appear (reach is raised to 32 blocks while holding a wand) |
| Right-click | Build |
| Shift + right-click | Cancel a running build, then undo |
| **R** / **M** | Rotate / mirror |
| **Page Up / Down** | Move up / down |
| Right-click *(Selection Wand)* | Set corner 1 |
| Shift + right-click *(Selection Wand)* | Set corner 2 |
| `/holobuild save <name>` | Save the marked area as a schematic |
| `/holobuild list` | List every schematic |

Starred schematics stay at the top of the picker. The list lives in
`holobuild/favourites.txt`, one name per line, so you can edit it by hand.

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
./gradlew test           # runs the unit tests
./gradlew runClient      # launches Minecraft with the mod loaded
```

You need **JDK 21** — Minecraft 1.21.1 will not run on anything else.

### Tests

The two file-format decoders are the easiest places to introduce a bug that
never crashes and simply produces the wrong blocks, so both are covered by
plain unit tests that run without Minecraft:

- `BitArrayTest` — the bit packing behind `.litematic`
- `VarIntStreamTest` — the variable-length numbers behind `.schem`

## Licence

[MIT](LICENSE) — use it, learn from it, build on it.

---

Made by **Ben** (genben-sb3), age 12, with a lot of curiosity.
