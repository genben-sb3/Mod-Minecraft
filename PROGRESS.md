# HoloBuild — Fortschritt

Mod fuer Minecraft 1.21.1 + NeoForge 21.1.248 | Java 21 | Package `com.boyben.holobuild`
Plan: `C:\Users\ben_h\.claude\plans\hi-ich-bin-ben-lazy-simon.md`

## Stand des Testens
- ✅ **16 automatische Tests**, alle gruen (`.\gradlew test`)
  `BitArrayTest` (9, .litematic) und `VarIntStreamTest` (7, .schem),
  beide mit von Hand ausgerechneten Werten und mit Gegenprobe geprueft.
- ✅ **30.08.2026: Der Mod laeuft im Spiel!** Log zeigt "HoloBuild 0.1.0 (holobuild)"
  in der Mod-Liste und die eigene Startmeldung. **Keine einzige HoloBuild-Fehlerzeile.**
  (Die roten Fehler im Log sind Create: `create:analog_lever` an Positionen, wo
  inzwischen Luft ist - Folge des Aeronautics-Updates 1.3.1 -> 1.3.2, nicht unser Problem.)
- ⏳ Die einzelnen Funktionen (Hologramm, Tasten, Bauen, Undo) hat Ben noch nicht durchprobiert.

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
| 6 | Eingebaute Bau-Bibliothek (5 Bauwerke) |
| 6b | **Weltgrenzen-Absicherung** |

## Etappe 6b — Weltgrenzen  ✅ CODE FERTIG
Gefundener Fehler: `Level.setBlock` tut ausserhalb der Bauhoehe **stillschweigend
nichts** (Vanilla-Quelle Level.java Zeile 234). Ein Bauwerk nahe der Weltdecke
(Y=319) oder am Weltboden (Y=-64) waere also halb verschwunden, ohne Absturz und
ohne Meldung — der schlimmste Fehlertyp, weil man ewig nach der Ursache sucht.

- [x] `Placement.fitsInWorld/overshootAbove/overshootBelow`
- [x] Zauberstab prueft **vor** dem Bauen und sagt, um wieviele Bloecke es nicht passt
- [x] **Hologramm wird rot**, wenn es nicht passen wuerde — man sieht es vor dem Klick
- [x] `/holobuild save` prueft ebenfalls; sonst haette man ein Bauwerk aus Luft gespeichert

## Unterstuetzte Dateiformate
`.nbt` (Struktur-Block) · `.schem` (WorldEdit/Sponge) · `.litematic` (Litematica)

## Steuerung
| Eingabe | Wirkung |
|---|---|
| **G** | Bauplan auswaehlen (Stern = Favorit) |
| Zielen (Blaupausen-Stab) | Hologramm, cyan = passt, **rot = passt nicht**. Reichweite **32 Bloecke** statt 5 |
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
- Eigene Dateien gewinnen gegen mitgelieferte Bauwerke gleichen Namens.
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
- **D11**: Bau-Bibliothek, alles gruen im ersten Versuch.
- **D12**: Etappe 7 uebersprungen (braucht Bens Entscheidung), stattdessen den
  Weltgrenzen-Fehler gefunden und abgesichert. Wieder in den Vanilla-Quellen
  nachgesehen statt zu vermuten — `setBlock` gibt dort einfach `false` zurueck.

## Naechster Schritt — ENTSCHEIDUNG VON BEN NOETIG
Etappe 7 (Online-Bibliothek) braucht ein GitHub-Repo, das Ben gehoert.
Solange das nicht geklaert ist, macht es keinen Sinn, den Download-Code zu bauen.
Ansonsten: **testen im Spiel**.
