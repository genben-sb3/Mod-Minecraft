# HoloBuild — Fortschritt

Mod fuer Minecraft 1.21.1 + NeoForge 21.1.248 | Java 21 | Package `com.boyben.holobuild`
Plan: `C:\Users\ben_h\.claude\plans\hi-ich-bin-ben-lazy-simon.md`

## Stand des Testens
- ✅ **16 automatische Tests**, alle gruen (`.\gradlew test`)
  - `BitArrayTest` (9) — Bit-Packing der .litematic-Dateien
  - `VarIntStreamTest` (7) — varints der .schem-Dateien
  - Beide mit von Hand ausgerechneten Werten, nicht nur Round-Trip.
    Gegenprobe gemacht: mit kaputtem Code schlagen die Tests fehl.
- ❌ **Im Spiel lief noch nichts.** `latest.log` ist immer noch vom 27.08.
  Getestet sind die zwei Dateiformat-Dekoder. Alles andere ist nur kompiliert.

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
| 5c | .litematic-Import + JUnit im Build |
| 5d | **Favoriten in der GUI + varint-Dekoder getestet** |

## Etappe 5d — Favoriten  ✅ CODE FERTIG
- [x] `client/Favourites.java` — Sternchen pro Schematic, gespeichert als
      `holobuild/favourites.txt`, ein Name pro Zeile. Von Hand editierbar.
      Rein clientseitig — der Server hat damit nichts zu tun.
- [x] `SchematicScreen` — Stern-Knopf links neben jedem Eintrag, markierte
      Eintraege stehen oben. Anzeige welcher Ausschnitt gerade sichtbar ist.
- [x] `format/VarIntStream.java` — die varint-Dekodierung aus dem .schem-Reader
      herausgezogen, **bewusst ohne Minecraft-Klassen**, damit testbar.
      Gleiche Ueberlegung wie bei `BitArray`: ein falscher Shift stuerzt nicht ab,
      er liefert einfach ab da nur noch falsche Bloecke.

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
**Dateianfang**, statt zu ersetzen. Hat in D7 und D10 jeweils das README zerschossen.
**Regel: mehrzeilige Aenderungen an Textdateien immer per Neuschreiben der Datei.**
Einzeiliges `sed` ist unproblematisch.

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
- **D7**: .schem-Import. README mit perl beschaedigt.
- **D8**: Auswahl-Stab + Befehle, beide Builds gruen im ersten Versuch.
- **D9**: .litematic + erste echte Tests, mit Gegenprobe.
- **D10**: Favoriten + varint-Tests. README **erneut** mit perl beschaedigt,
  obwohl die Lehre aus D7 schon hier stand. Jetzt als Werkzeug-Falle oben notiert.

## Naechster Schritt
Testen im Spiel. Danach: eingebaute Bau-Bibliothek (Etappe 6).
