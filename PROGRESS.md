# HoloBuild — Fortschritt

Mod fuer Minecraft 1.21.1 + NeoForge 21.1.248 | Java 21 | Package `com.boyben.holobuild`
Plan: `C:\Users\ben_h\.claude\plans\hi-ich-bin-ben-lazy-simon.md`

## Stand des Testens
- ✅ **9 automatische Tests** fuer die Bit-Arithmetik der .litematic-Dateien, alle gruen.
      Gegenprobe gemacht: mit absichtlich kaputtem Code schlagen 3 davon fehl,
      die Tests pruefen also wirklich etwas.
- ❌ **Alles andere lief nie in Minecraft.** `latest.log` ist immer noch vom 27.08.
      Sieben Etappen Code, davon getestet: die Bit-Arithmetik. Sonst nichts.

## Fertig (Code)
| Etappe | Inhalt |
|---|---|
| 0 | JDK 21 portable, VS Code Java Pack, MDK, Git, README, MIT |
| 1 | Zauberstab platziert Strukturen, Kreativ-Check, serverseitig |
| 2 | Hologramm (Block-Geister + Umriss), R/M/Bild-Tasten, Undo |
| 3 | Auswahl-GUI (Taste G) mit Suchfeld |
| 4 | Animiertes Bauen, Config `blocksPerTick`, Sound gedrosselt |
| 5 | .schem-Import (Sponge Version 1-3) |
| 5b | Auswahl-Stab + `/holobuild save` + `/holobuild list` |
| 5c | **.litematic-Import + Unit-Tests + JUnit im Build** |

## Etappe 5c — .litematic  ✅ CODE FERTIG
- [x] `format/BitArray.java` — die Bit-Arithmetik, **bewusst ohne jede
      Minecraft-Klasse**, damit sie ohne Spiel testbar ist.
      Litematica packt Bits durchgehend; ein Eintrag kann in einem long anfangen
      und im naechsten enden. Minecraft selbst polstert seit 1.16 jeden long —
      wer das verwechselt, kriegt statt eines Gebaeudes Konfetti.
- [x] `format/LitematicaReader.java` — mehrere Regionen, eigene Palette je Region,
      **negative Groessen** (dann ist die angegebene Position die ferne Ecke),
      TileEntities (Kisteninhalte) werden zugeordnet.
      Alle Regionen werden am Ende auf 0,0,0 normalisiert.
- [x] `src/test/java/.../BitArrayTest.java` — 9 Tests, darunter drei mit **von Hand
      ausgerechneten** Bitmustern. Wichtig: reine Round-Trip-Tests (pack gegen get)
      waeren gruen geblieben, wenn beide denselben Denkfehler haetten.
- [x] JUnit 5 im `build.gradle`, `./gradlew test` laeuft

## Steuerung
| Eingabe | Wirkung |
|---|---|
| **G** | Bauplan auswaehlen |
| Zielen (Blaupausen-Stab) | Hologramm + cyan Umriss |
| Rechtsklick | bauen |
| Shift + Rechtsklick | abbrechen und/oder rueckgaengig |
| R / M | drehen / spiegeln |
| Bild hoch / runter | hoeher / tiefer |
| Rechtsklick (Auswahl-Stab) | Ecke 1 |
| Shift + Rechtsklick (Auswahl-Stab) | Ecke 2 |
| `/holobuild save <name>` | Auswahl speichern |
| `/holobuild list` | alle Schematics auflisten |

## Unterstuetzte Dateiformate
`.nbt` (Struktur-Block) · `.schem` (WorldEdit/Sponge) · `.litematic` (Litematica)

## Wichtige Entscheidungen (nicht "aufraeumen"!)
- `Placement.transformed()` nutzt bewusst das **deprecated** `BlockState.rotate(Rotation)`,
  weil `placeInWorld` genau das aufruft. Reihenfolge **erst mirror, dann rotate**.
- Fremdformate werden sofort ins Vanilla-Structure-Format umgeschrieben. Dadurch
  musste fuer .schem und .litematic **kein** anderer Bereich angefasst werden.
- Ab 30.000 Bloecken nur Umriss statt Block-Geister (Bildrate).

## Bekannte Einschraenkungen
- GUI liest den Ordner des **Clients** — Singleplayer korrekt, echter Server nicht.
- Animierter Modus ohne Waterlogging und Structure Processors.
- `.schem` bringt keine Block-Entities mit (.litematic dagegen schon).

## Git
Lokal: Branch `main`. Remote `genben-sb3/Mod-Minecraft`.
**Push geht von hier aus NICHT** — GitHub verlangt Anmeldung, diese Umgebung kann
keine Anmeldefenster oeffnen. Ben muss in VS Code auf "Sync Changes" klicken.

## Log
- **D1**: Etappe 0 + 1. Java per ZIP statt winget (UAC haengt).
- **D2**: Fehler war EIN verlorener Backslash. Zeit im NBT-Pruefwerkzeug verloren.
- **D3**: Etappe 2 halb. Git-Repo, README, Lizenz.
- **D4**: Block-Geister. Dekompilierte Quellen gelesen statt API zu raten.
- **D5**: Etappe 3 GUI aus einfachen Buttons.
- **D6**: Etappe 4. `distSqr` gibt `double` nicht `long`.
- **D7**: .schem-Import. README mit perl-Befehl beschaedigt, neu geschrieben.
- **D8**: Auswahl-Stab + Befehle, beide Builds gruen im ersten Versuch.
- **D9**: .litematic + erste echte Tests. Bit-Arithmetik in eine Minecraft-freie
  Klasse gezogen, damit sie ueberhaupt testbar ist. Gegenprobe mit absichtlich
  kaputtem Code bestaetigt, dass die Tests greifen.

## Naechster Schritt
Testen im Spiel. Danach: Favoriten in der GUI, eingebaute Bau-Bibliothek.
