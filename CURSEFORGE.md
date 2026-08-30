# Veröffentlichen auf CurseForge

Diese Datei ist Bens Spickzettel für Etappe 9. Der obere Teil ist die fertige
Beschreibung zum Kopieren, der untere die Checkliste.

---

## ⚠️ Zuerst: Was noch fehlt

**Der Account.** CurseForge verlangt mindestens 13 Jahre. Zum Bauen und Spielen
ist das egal, aber zum Hochladen muss ein Elternteil den Account anlegen und
besitzen. Das ist keine Formsache — es steht in den Nutzungsbedingungen.

**Ein echter Testlauf.** Stand 30.08.2026 sind 37 automatische Tests grün, aber
die Dinge, die man nur sehen kann, hat noch niemand angeschaut: die
3D-Vorschau, der Material-Bildschirm, die Knöpfe im Auswahl-Menü. Einen Mod zu
veröffentlichen, dessen Oberfläche nie jemand gesehen hat, geht schief.

**Screenshots.** Mindestens drei: das Hologramm vor dem Bauen, ein Bauwerk beim
Wachsen, das Auswahl-Menü mit der Vorschau. Ein kurzes GIF vom animierten
Bauen wäre das beste Werbemittel, das dieser Mod hat.

**Die Version.** `mod_version` steht in `gradle.properties` noch auf `0.1.0`.
Für eine erste Veröffentlichung passt `1.0.0` — aber erst nach dem Testlauf.

---

## Beschreibung zum Kopieren

*(CurseForge kann Markdown. Alles ab hier einfügen.)*

### HoloBuild

**See it before you build it.**

Pick a schematic, look where you want it, and a see-through hologram shows you
exactly what you are about to place — turn it, mirror it, move it up and down
until it sits right. Then one click, and it rises out of the ground block by
block.

#### What it does

- **Hologram preview.** The whole building, see-through, before a single block
  is placed. The outline turns **red** if it would poke through the world
  ceiling or floor — so you find out before, not after.
- **Watch it grow.** Buildings rise from the bottom up and from the middle
  outwards, with placing sounds. Speed is adjustable; set it to 0 for instant.
- **Undo.** Shift + right-click puts everything back, chest contents included.
- **Reads every common format.** `.nbt` from the structure block, `.schem` from
  WorldEdit and the schematic sites, and `.litematic` from Litematica.
- **Save your own.** Mark two corners with the Selection Wand, then
  `/holobuild save myhouse`. The file is a plain Minecraft structure — the
  structure block and other mods can read it too.
- **Roll a new building.** `/holobuild random tower mytower` puts together a
  base, some randomly chosen floors and a roof. Add your own floor shapes by
  saving them under the right name — no config, no code.
- **Ships with five buildings** so there is something to try straight away:
  cottage, watchtower, bridge, well and lighthouse.
- **Survival too.** The hologram and a material list showing what you still
  need. Optionally the wand builds in survival as well, paying for every block
  out of your inventory.

#### Controls

| Input | Does |
|---|---|
| **G** | Choose a blueprint |
| Look at a block | Hologram appears (reach is raised to 32 blocks) |
| Right-click | Build |
| Shift + right-click | Cancel and undo |
| **R** / **M** | Rotate / mirror |
| **Page Up / Down** | Move up / down |

Commands: `/holobuild build`, `save`, `list`, `random`.

#### Works with Create

Create saves its schematics in the same `.nbt` format, so HoloBuild reads them
directly — no converting.

#### Credits

Inspired by [Schematica](https://www.curseforge.com/minecraft/mc-mods/schematica)
and [Litematica](https://www.curseforge.com/minecraft/mc-mods/litematica).
HoloBuild shares their idea, not their code — everything here is written from
scratch. Thank you to both for showing what a building tool can be.

Made by Ben, age 12. This is my first mod.

---

## Checkliste

- [ ] Elternteil legt den CurseForge-Account an
- [ ] Im Spiel durchprobieren: Menü, Vorschau, Material-Bildschirm, Survival
- [ ] Drei Screenshots und möglichst ein GIF vom animierten Bauen
- [ ] `mod_version` in `gradle.properties` auf `1.0.0`
- [ ] `.\gradlew build` und `.\gradlew runGameTestServer` — beide grün
- [ ] Prüfen, ob der Name "HoloBuild" auf CurseForge noch frei ist
- [ ] Projekt anlegen: Kategorie **Map and Information**, Lizenz **MIT**
- [ ] Logo hochladen (`src/main/resources/holobuild_logo.png`)
- [ ] `build/libs/holobuild-1.0.0.jar` hochladen, Spielversion 1.21.1, NeoForge

## Was zur Veröffentlichung dazugehört

Sobald der Mod öffentlich ist, laden ihn Leute herunter und melden Fehler. Das
ist der Sinn der Sache, aber es heißt auch: Es kommen Fragen. Ein Mod ohne
Antwort auf Fragen wirkt verlassen, und dann probiert ihn niemand mehr aus.
Lieber später veröffentlichen und dann da sein, als früh und dann still.
