# HoloBuild — Fortschritt

Mod fuer Minecraft 1.21.1 + NeoForge 21.1.248 | Java 21 | Package `com.boyben.holobuild`
Plan: `C:\Users\ben_h\.claude\plans\hi-ich-bin-ben-lazy-simon.md`

## ⚠️ WICHTIGSTER PUNKT
**Sechs Etappen Code sind fertig und KEINE davon lief je in Minecraft.**
`latest.log` ist Stand 28.08. immer noch vom 27.08.
Ein Testlauf waere jetzt deutlich mehr wert als jedes weitere Feature.

## Fertig (Code, ungetestet)
| Etappe | Inhalt |
|---|---|
| 0 | JDK 21 portable, VS Code Java Pack, MDK, Git, README, MIT |
| 1 | Zauberstab platziert Strukturen, Kreativ-Check, serverseitig |
| 2 | Hologramm (Block-Geister + Umriss), R/M/Bild-Tasten, Undo |
| 3 | Auswahl-GUI (Taste G) mit Suchfeld |
| 4 | Animiertes Bauen, Config `blocksPerTick`, Sound gedrosselt |
| 5 | .schem-Import (Sponge Version 1-3) |
| 5b | **Auswahl-Stab + `/holobuild save` + `/holobuild list`** |

## Etappe 5b — Selbst speichern  ✅ CODE FERTIG
- [x] `item/SelectionWandItem.java` — Rechtsklick = Ecke 1, Shift+Rechtsklick = Ecke 2.
      Eigene Textur (bernsteinfarben statt cyan, damit man die Staebe unterscheidet).
- [x] `component/Selection.java` — beide Ecken als Data Component am Item, dadurch
      kennt der Client sie automatisch und kann die Box zeichnen.
- [x] `command/HoloBuildCommand.java` — `/holobuild save <name>`, `/holobuild list`.
      Nutzt Vanillas `fillFromWorld` + `save`, das Ergebnis ist eine ganz normale
      .nbt-Struktur, die auch der Struktur-Block und andere Mods lesen koennen.
      Entities werden **nicht** mitgespeichert (sonst waeren die Kuehe mit drin).
      Grenze 500.000 Bloecke.
- [x] Auswahl wird als bernsteinfarbene Box gezeichnet; bei nur einer gesetzten Ecke
      der einzelne Block, damit man sieht wo man war.

## Aufgeraeumt
`isSafeName` und `MAX_NAME_LENGTH` lagen doppelt vor (Payload + Command haetten sie
beide gebraucht). Jetzt nur noch in `SchematicLoader`. Zwei Kopien einer
Sicherheitspruefung sind eine zu viel — eine davon wird garantiert vergessen.

## Testdateien in der Instanz
- `test_haus.nbt` — 7x6x7, 270 Bloecke (Vanilla-Format)
- `test_turm.schem` — 5x9x5, 163 Bloecke (Sponge-Format)

## Steuerung
| Eingabe | Wirkung |
|---|---|
| **G** | Bauplan auswaehlen |
| Zielen (Blaupausen-Stab) | Hologramm + cyan Umriss |
| Rechtsklick | bauen |
| Shift + Rechtsklick | Bau abbrechen und/oder rueckgaengig |
| R / M | drehen / spiegeln |
| Bild hoch / runter | hoeher / tiefer |
| Rechtsklick (Auswahl-Stab) | Ecke 1 |
| Shift + Rechtsklick (Auswahl-Stab) | Ecke 2 |
| `/holobuild save <name>` | Auswahl als Schematic speichern |
| `/holobuild list` | alle Schematics auflisten |

## Wichtige Entscheidungen (nicht "aufraeumen"!)
- `Placement.transformed()` nutzt bewusst das **deprecated** `BlockState.rotate(Rotation)`,
  weil `placeInWorld` (Vanilla-Quelle Zeile 254) genau das aufruft. Reihenfolge
  **erst mirror, dann rotate**. Sonst weicht das Hologramm vom Ergebnis ab.
- Sofort-Modus nutzt Vanillas `placeInWorld` (Waterlogging + Processors),
  animierter Modus die eigene Blockliste. Beide muessen identisch transformieren.
- Ab 30.000 Bloecken nur Umriss statt Block-Geister (Bildrate).

## Bekannte Einschraenkungen
- GUI liest den Ordner des **Clients** — Singleplayer korrekt, echter Server nicht.
- Animierter Modus ohne Waterlogging und Structure Processors.
- `.schem` bringt keine Block-Entities mit (Kisteninhalte gehen verloren).

## Git
Lokal: Branch `main`. Remote `genben-sb3/Mod-Minecraft`.
**Push geht von hier aus NICHT** — GitHub verlangt Anmeldung, diese Umgebung kann
keine Anmeldefenster oeffnen. Ben muss in VS Code auf "Sync Changes" klicken.

## Log
- **D1**: Etappe 0 + 1. Java per ZIP statt winget (UAC haengt).
- **D2**: Fehler war EIN verlorener Backslash. Zeit im NBT-Pruefwerkzeug verloren:
  `out[r.name()] = payload(r, c)` wertet in Python die RECHTE Seite zuerst aus.
- **D3**: Etappe 2 halb. Git-Repo, README, Lizenz.
- **D4**: Block-Geister. Dekompilierte Quellen gelesen statt API zu raten.
- **D5**: Etappe 3 GUI aus einfachen Buttons statt ObjectSelectionList.
- **D6**: Etappe 4. `distSqr` gibt `double` nicht `long`.
- **D7**: .schem-Import. README mit perl-Befehl beschaedigt, neu geschrieben —
  bei mehrzeiligen Ersetzungen lieber die Datei ganz neu schreiben.
- **D8**: Auswahl-Stab + Befehle. Beide Builds gruen im ersten Versuch.

## Naechster Schritt
**Erst testen.** Danach `.litematic` lesen, dann Favoriten in der GUI.
