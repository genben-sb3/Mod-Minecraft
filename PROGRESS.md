# HoloBuild — Fortschritt

Mod fuer Minecraft 1.21.1 + NeoForge 21.1.248 | Java 21 | Package `com.boyben.holobuild`
Plan: `C:\Users\ben_h\.claude\plans\hi-ich-bin-ben-lazy-simon.md`

## ⚠️ WICHTIGSTER PUNKT
**Fuenf Etappen Code sind fertig und KEINE davon lief je in Minecraft.**
`latest.log` ist Stand 28.08. immer noch vom 27.08.
Je mehr ungetestet aufeinanderstapelt, desto schwerer wird die Fehlersuche.
Ein Testlauf waere jetzt mehr wert als jedes weitere Feature.

## Fertig (Code, ungetestet)
| Etappe | Inhalt |
|---|---|
| 0 | JDK 21 portable, VS Code Java Pack, MDK, Git, README, MIT |
| 1 | Zauberstab platziert .nbt-Strukturen, Kreativ-Check, serverseitig |
| 2 | Hologramm (Block-Geister + Umriss), R/M/Bild-Tasten, Undo |
| 3 | Auswahl-GUI (Taste G) mit Suchfeld, Path-Traversal-Schutz |
| 4 | Animiertes Bauen, Config `blocksPerTick`, Sound gedrosselt |
| 5 | **.schem-Import (Sponge, Version 1-3)** |

## Etappe 5 — Fremdformate  🔨 TEILWEISE
- [x] `format/RawSchematic.java` — gemeinsame Zwischenform fuer alle Formate
- [x] `format/SpongeSchematicReader.java` — liest .schem (WorldEdit & Schematic-Seiten).
      Palette ist Text -> Zahl, BlockData ist ein varint-Strom, Reihenfolge x/z/y.
      Unbekannte Bloecke werden zu Luft statt die Datei abzulehnen.
      Groessengrenze 20 Mio Bloecke gegen kaputte/boesartige Dateien.
- [x] `format/StructureNbt.java` — schreibt das Ergebnis im Vanilla-Structure-Format.
      **Der Kniff der Etappe**: dadurch sieht ein .schem fuer allen anderen Code
      aus wie eine normale Minecraft-Struktur. Null Aenderungen an Hologramm,
      Animation, Undo oder GUI noetig.
- [x] `SchematicLoader.findByName()` — **die einzige Stelle**, die einen Namen in
      einen Pfad verwandelt. Kennt alle Endungen und verlaesst den Ordner nie.
      SelectSchematicPayload und SchematicCache nutzen jetzt beide diese Stelle.
- [ ] `.litematic` lesen (bit-gepackte long-Arrays, mehrere Regionen)
- [ ] Auswahl-Stab (Ecke 1 / Ecke 2) zum Selbst-Speichern

## Testdateien in der Instanz
- `test_haus.nbt` — 7x6x7, 270 Bloecke (Vanilla-Format)
- `test_turm.schem` — 5x9x5, 163 Bloecke (Sponge-Format, mit Fenstern und Fackel)

## Wichtige Entscheidungen (nicht "aufraeumen"!)
- `Placement.transformed()` nutzt bewusst das **deprecated** `BlockState.rotate(Rotation)`,
  weil `placeInWorld` (Vanilla-Quelle Zeile 254) genau das aufruft. Reihenfolge
  **erst mirror, dann rotate**. Sonst weicht das Hologramm vom Ergebnis ab.
- Sofort-Modus nutzt Vanillas `placeInWorld` (kann Waterlogging + Processors),
  animierter Modus die eigene Blockliste. Beide muessen identisch transformieren.
- Ab 30.000 Bloecken nur Umriss statt Block-Geister (Bildrate).

## Bekannte Einschraenkungen
- GUI liest den Ordner des **Clients** — Singleplayer korrekt, echter Server nicht.
- Animierter Modus ohne Waterlogging und Structure Processors.
- `.schem` bringt keine Block-Entities mit (Kisteninhalte in .schem gehen verloren).

## Git
Lokal: Branch `main`. Remote `genben-sb3/Mod-Minecraft`.
**Push geht von hier aus NICHT** — GitHub verlangt Anmeldung, diese Umgebung kann
keine Anmeldefenster oeffnen. Zweimal bestaetigt, wird nicht erneut versucht.
Ben muss in VS Code auf "Sync Changes" klicken.

## Log
- **D1**: Etappe 0 + 1. Java per ZIP statt winget (UAC haengt).
- **D2**: Fehler war EIN verlorener Backslash. Zeit im NBT-Pruefwerkzeug verloren:
  `out[r.name()] = payload(r, c)` wertet in Python die RECHTE Seite zuerst aus.
- **D3**: Etappe 2 halb. Git-Repo, README, Lizenz.
- **D4**: Block-Geister. Dekompilierte Quellen gelesen statt API zu raten.
- **D5**: Etappe 3 GUI aus einfachen Buttons statt ObjectSelectionList.
- **D6**: Etappe 4. `distSqr` gibt `double` nicht `long`; Mittelpunkt jetzt per min/max.
- **D7**: .schem-Import. `holderLookup` gibt `HolderLookup<Block>`, nicht die
  RegistryLookup-Variante. Ausserdem README mit einem perl-Befehl beschaedigt
  (Ersetzung landete am Dateianfang) und neu geschrieben — bei mehrzeiligen
  Ersetzungen lieber die Datei ganz neu schreiben.

## Naechster Schritt
**Erst testen.** Danach: Auswahl-Stab zum Selbst-Speichern, dann `.litematic`.
