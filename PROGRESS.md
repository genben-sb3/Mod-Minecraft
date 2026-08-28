# HoloBuild — Fortschritt

Mod fuer Minecraft 1.21.1 + NeoForge 21.1.248 | Java 21 | Package `com.boyben.holobuild`
Plan: `C:\Users\ben_h\.claude\plans\hi-ich-bin-ben-lazy-simon.md`

## Etappe 0 — Werkzeuge  ✅
JDK 21 portable, VS Code Java Pack, NeoForge MDK, Git-Repo, README, MIT-Lizenz.

## Etappe 1 — MVP "Es baut!"  ✅ CODE FERTIG
Zauberstab platziert .nbt-Strukturen, Kreativ-Check, nur serverseitig.
Test-Haus (7x6x7, 270 Bloecke) liegt in der Instanz.

## Etappe 2 — Hologramm & Steuerung  ✅ CODE FERTIG
Durchscheinende Block-Geister + cyan Umriss, Drehen/Spiegeln/Hoehe per Taste,
Undo per Shift+Rechtsklick inkl. Block-Entity-Daten.

## Etappe 3 — Auswahl-GUI  ✅ CODE FERTIG
- [x] `client/SchematicScreen.java` — Taste **G** oeffnet die Auswahl.
      Suchfeld, Dateiliste mit Groessenangabe, Mausrad blaettert, 8 Zeilen pro Seite.
      Bewusst aus einfachen Buttons gebaut statt ObjectSelectionList: weniger
      API-Abhaengigkeiten, gleiche Bedienung.
      Zeigt bei leerem Ordner den Pfad an, statt nur ein leeres Feld.
- [x] `network/SelectSchematicPayload.java` — Auswahl geht zum Server
- [ ] Favoriten (Stern) — verschoben auf spaeter
- [ ] Vorschaubilder — sehr aufwaendig, spaeter entscheiden

## Sicherheit (wichtig!)
`SelectSchematicPayload` prueft den Namen **zweifach**, bevor er in einen Dateipfad geht:
1. Nur Buchstaben, Zahlen, Leerzeichen, `_`, `-`, `.` erlaubt; kein `..`
2. Der aufgeloeste Pfad muss wirklich im Schematics-Ordner liegen und eine Datei sein
Grund: Ein veraenderter Client koennte sonst einen Namen wie `../../../irgendwas`
schicken. Auf einem Server waere das ein echtes Loch.

## Wichtige Entscheidungen (nicht "aufraeumen"!)
- `Placement.transformed()` nutzt bewusst das **deprecated** `BlockState.rotate(Rotation)`,
  weil `StructureTemplate.placeInWorld` (Vanilla-Quelle Zeile 254) genau das aufruft.
  Reihenfolge **erst mirror, dann rotate** — ebenfalls exakt wie Vanilla.
- Ab 30.000 Bloecken nur noch Umriss statt Block-Geister (Bildrate).

## Steuerung
| Eingabe | Wirkung |
|---|---|
| **G** | Bauplan auswaehlen (GUI) |
| Zielen | Hologramm + Umriss |
| Rechtsklick | platzieren |
| Shift + Rechtsklick | rueckgaengig |
| R / M | drehen / spiegeln |
| Bild hoch / runter | hoeher / tiefer |

## Bekannte Einschraenkung
Die GUI liest den Schematics-Ordner des **Clients**. In Singleplayer ist das derselbe
Ordner wie beim Server, also korrekt. Auf einem echten Mehrspieler-Server wuerde der
Client Dateien anzeigen, die der Server nicht hat. Muss geloest werden, bevor
Mehrspieler unterstuetzt wird — Server schickt dann seine Liste.

## Git
Lokal: Branch `main`. Remote `genben-sb3/Mod-Minecraft` eingetragen.
**Push geht von hier aus NICHT** — GitHub verlangt Anmeldung, diese Umgebung kann keine
Anmeldefenster oeffnen. Zweimal bestaetigt, wird nicht erneut versucht.
Ben muss in VS Code auf "Sync Changes" klicken.

## TESTEN — steht weiterhin komplett aus
Stand 28.08. ist `latest.log` noch vom 27.08., der Mod lief also nie.
1. Instanz BoyBen2026 starten, Kreativmodus
2. **G** druecken -> Auswahl mit `test_haus`
3. Zielen -> durchscheinendes Haus erscheint
4. **R** -> dreht sich mit
5. Rechtsklick -> gebaut. Shift+Rechtsklick -> weg.
6. `/gamemode survival` -> nichts geht, rote Meldung

## Log
- **Durchlauf 1**: Etappe 0 + 1. Java per ZIP statt winget (UAC-Abfrage haengt).
- **Durchlauf 2**: Build gruen. Fehler war EIN verlorener Backslash im Java-Escape.
  Zeit im NBT-Pruefwerkzeug verloren: `out[r.name()] = payload(r, c)` wertet in Python
  die RECHTE Seite zuerst aus. Die Datei war immer korrekt, nur der Pruefer kaputt.
- **Durchlauf 3**: Etappe 2 halb (Umriss, Tasten, Undo). Git-Repo, README, Lizenz.
- **Durchlauf 4**: Block-Geister. Statt API zu raten die dekompilierten Quellen aus
  `build/moddev/artifacts/neoforge-21.1.248-sources.jar` gelesen — hat die exakte
  Transformation und die VertexConsumer-Schnittstelle geklaert. Merke: dort nachsehen.
- **Durchlauf 5**: Etappe 3 GUI. ObjectSelectionList war API-technisch fummelig,
  daher eigene Button-Liste — schneller fertig und robuster.

## Naechster Schritt: Etappe 4 — animiertes Bauen
Bloecke sortiert (erst Hoehe, dann Abstand zur Mitte) in eine Warteschlange,
im `ServerTickEvent.Post` pro Tick N Stueck setzen, mit Platzier-Sound.
Die Blockliste dafuer liegt schon in `LoadedSchematic.blocks()`.
