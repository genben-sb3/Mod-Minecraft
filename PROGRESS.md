# HoloBuild — Fortschritt

Mod fuer Minecraft 1.21.1 + NeoForge 21.1.248 | Java 21 | Package `com.boyben.holobuild`
Plan: `C:\Users\ben_h\.claude\plans\hi-ich-bin-ben-lazy-simon.md`

## Stand des Testens
- ✅ **16 automatische Tests**, alle gruen (`.\gradlew test`)
  `BitArrayTest` (9, .litematic) und `VarIntStreamTest` (7, .schem),
  beide mit von Hand ausgerechneten Werten und mit Gegenprobe geprueft.
- ❌ **Im Spiel lief noch nichts.** `latest.log` ist immer noch vom 27.08.

## Fertig (Code)
| Etappe | Inhalt |
|---|---|
| 0 | JDK 21 portable, VS Code Java Pack, MDK, Git, README, MIT |
| 1 | Zauberstab platziert Strukturen, Kreativ-Check, serverseitig |
| 2 | Hologramm (Block-Geister + Umriss), R/M/Bild-Tasten, Undo |
| 3 | Auswahl-GUI (Taste G) mit Suchfeld |
| 4 | Animiertes Bauen, Config `blocksPerTick` |
| 5 | .schem-Import (Sponge Version 1-3) |
| 5b | Auswahl-Stab + `/holobuild save` + `/holobuild list` |
| 5c | .litematic-Import + JUnit im Build |
| 5d | Favoriten in der GUI + varint-Dekoder getestet |
| 6 | **Eingebaute Bau-Bibliothek (5 Bauwerke)** |

## Etappe 6 — Bau-Bibliothek  ✅ CODE FERTIG
- [x] Fuenf Bauwerke in `src/main/resources/data/holobuild/structure/`:
      `cottage` 7x8x7 (169), `watchtower` 5x13x5 (218), `bridge` 5x6x15 (143),
      `well` 5x6x5 (73), `lighthouse` 7x17x7 (364 Bloecke).
      Erzeugt per Skript, alle fuenf als gueltiges NBT verifiziert.
      **Als Startpunkt gedacht — Ben kann sie jederzeit durch eigene ersetzen.**
- [x] `schematic/BuiltInLibrary.java` — liest aus der Mod-JAR ueber den
      ClassLoader. Funktioniert client- und serverseitig ohne Datapack-System.
      `index.txt` listet die Namen, dadurch muss beim Hinzufuegen kein Code geaendert werden.
- [x] `SchematicLoader.loadByName/allNames/exists/isBuiltIn` — **eigene Dateien
      gewinnen bei Namensgleichheit**, man kann also ein mitgeliefertes Bauwerk
      ueberschreiben, indem man eins gleichen Namens speichert.
- [x] GUI und `/holobuild list` markieren mitgelieferte mit "(mitgeliefert)"
- [x] Cache versteht beide Quellen (Zeitstempel -1 fuer eingebaute)

## Unterstuetzte Dateiformate
`.nbt` (Struktur-Block) · `.schem` (WorldEdit/Sponge) · `.litematic` (Litematica)

## Steuerung
| Eingabe | Wirkung |
|---|---|
| **G** | Bauplan auswaehlen (Stern = Favorit) |
| Zielen (Blaupausen-Stab) | Hologramm + cyan Umriss |
| Rechtsklick | bauen |
| Shift + Rechtsklick | abbrechen und/oder rueckgaengig |
| R / M | drehen / spiegeln |
| Bild hoch / runter | hoeher / tiefer |
| Rechtsklick (Auswahl-Stab) | Ecke 1 |
| Shift + Rechtsklick (Auswahl-Stab) | Ecke 2 |
| `/holobuild save <name>` | Auswahl speichern |
| `/holobuild list` | alle Schematics auflisten |

## Wichtige Entscheidungen (nicht "aufraeumen"!)
- `Placement.transformed()` nutzt bewusst das **deprecated** `BlockState.rotate(Rotation)`,
  weil `placeInWorld` genau das aufruft. Reihenfolge **erst mirror, dann rotate**.
- Fremdformate werden sofort ins Vanilla-Structure-Format umgeschrieben. Dadurch
  musste fuer .schem und .litematic **kein** anderer Bereich angefasst werden.
- Ab 30.000 Bloecken nur Umriss statt Block-Geister (Bildrate).

## ⚠️ WERKZEUG-FALLE (zweimal reingetappt!)
`perl -0pi -e` mit **mehrzeiligen** Suchmustern haengt den Ersatz an den
**Dateianfang**, statt zu ersetzen. Hat in D7 und D10 das README zerschossen.
**Regel: mehrzeilige Aenderungen an Textdateien immer per Neuschreiben der Datei.**
Einzeiliges `sed` ist unproblematisch.

## Bekannte Einschraenkungen
- GUI liest den Ordner des **Clients** — Singleplayer korrekt, echter Server nicht.
- Animierter Modus ohne Waterlogging und Structure Processors.
- `.schem` bringt keine Block-Entities mit (.litematic dagegen schon).
- Die mitgelieferten Bauwerke sind schlicht (per Skript erzeugt), nicht handgebaut.

## Git
Lokal: Branch `main`. Remote `genben-sb3/Mod-Minecraft`.
**Push geht von hier aus NICHT** — GitHub verlangt Anmeldung, diese Umgebung kann
keine Anmeldefenster oeffnen. Ben muss in VS Code auf "Sync Changes" klicken.

## Log
- **D1**: Etappe 0 + 1. Java per ZIP statt winget (UAC haengt).
- **D2**: Fehler war EIN verlorener Backslash.
- **D3**: Etappe 2 halb. Git-Repo, README, Lizenz.
- **D4**: Block-Geister. Dekompilierte Quellen gelesen statt API zu raten.
- **D5**: Etappe 3 GUI aus einfachen Buttons.
- **D6**: Etappe 4. `distSqr` gibt `double` nicht `long`.
- **D7**: .schem-Import. README mit perl beschaedigt.
- **D8**: Auswahl-Stab + Befehle.
- **D9**: .litematic + erste echte Tests, mit Gegenprobe.
- **D10**: Favoriten + varint-Tests. README erneut mit perl beschaedigt.
- **D11**: Bau-Bibliothek. Beide Builds gruen im ersten Versuch, keine Werkzeug-Pannen.

## Naechster Schritt
Testen im Spiel. Danach Etappe 7 (Online-Bibliothek) — dafuer braucht es aber
zuerst eine Entscheidung von Ben, weil ein GitHub-Repo dafuer noetig waere.
