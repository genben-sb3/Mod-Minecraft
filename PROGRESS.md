# HoloBuild — Fortschritt

Mod fuer Minecraft 1.21.1 + NeoForge 21.1.248 | Java 21 | Package `com.boyben.holobuild`
Plan: `C:\Users\ben_h\.claude\plans\hi-ich-bin-ben-lazy-simon.md`

## Etappe 0 — Werkzeuge  ✅ FERTIG
- [x] JDK 21 portable, VS Code Java Pack, NeoForge MDK, Git-Repo, README, MIT-Lizenz

## Etappe 1 — MVP "Es baut!"  ✅ CODE FERTIG, TEST IM SPIEL OFFEN
- [x] Zauberstab platziert .nbt-Strukturen, Kreativ-Check, nur serverseitig
- [x] Test-Haus (7x6x7, 270 Bloecke), als NBT verifiziert
- [ ] **IM SPIEL TESTEN — immer noch offen** (Log ist noch vom 27.08.)

## Etappe 2 — Hologramm & Steuerung  ✅ FERTIG
- [x] `component/BlueprintSettings.java` — Auswahl, Drehung, Spiegelung, Hoehe
- [x] `schematic/Placement.java` — gemeinsame Positionsberechnung fuer Vorschau UND Platzieren
- [x] `schematic/SchematicCache.java` — Key = Dateiname + Aenderungszeit
- [x] `undo/UndoManager.java` — Shift+Rechtsklick, 5 Schritte, max 250.000 Bloecke
- [x] `network/` + `client/ModKeybinds.java` — R drehen, M spiegeln, Bild hoch/runter
- [x] **`client/HologramRenderer.java` — durchscheinende Block-Geister + cyan Umriss**
- [x] **`client/GhostVertexConsumer.java` — erzwingt Transparenz auf jedem Vertex**
- [x] Blockliste wird beim Laden selbst aus dem NBT gelesen (palette + blocks),
      weil StructureTemplate seine Bloecke nicht rausgibt. Wird auch fuer Etappe 4 gebraucht.

## Wichtige Entscheidungen (nicht "aufraeumen"!)
- `Placement.transformed()` nutzt bewusst das **deprecated** `BlockState.rotate(Rotation)`,
  weil `StructureTemplate.placeInWorld` (Zeile 254 der Vanilla-Quelle) genau das aufruft.
  Die neue LevelAccessor-Variante wuerde Vorschau und Ergebnis auseinanderlaufen lassen.
- Reihenfolge ist **erst mirror, dann rotate** — ebenfalls exakt wie Vanilla.
- Ab 30.000 Bloecken wird nur noch der Umriss gezeichnet, sonst bricht die Bildrate ein.

## Steuerung
| Eingabe | Wirkung |
|---|---|
| Zielen | Hologramm + cyan Umriss erscheinen |
| Rechtsklick | Bauwerk platzieren |
| Shift + Rechtsklick | Rueckgaengig |
| R / M | drehen / spiegeln |
| Bild hoch / runter | hoeher / tiefer |

## Git
Lokal: Branch `main`. Remote `genben-sb3/Mod-Minecraft` eingetragen.
**Push geht von hier aus NICHT** — GitHub fragt nach Anmeldung, diese Umgebung kann keine
Anmeldefenster oeffnen. Ben muss in VS Code auf "Sync Changes" klicken.
Bens echte E-Mail wurde aus allen Commits entfernt (GitHub-Schutzadresse).

## Naechster Schritt: Etappe 3 — GUI
Bildschirm zum Auswaehlen der Schematic: Liste, Suchfeld, Favoriten, Groessenangabe.
Auswahl per Netzwerk-Payload zum Server (Muster steht schon in `network/`).

## Log
- **Durchlauf 1**: Etappe 0 + 1. Java per ZIP statt winget (UAC-Abfrage).
- **Durchlauf 2**: Build gruen. Fehler war EIN verlorener Backslash im Java-Escape.
  Zeit im NBT-Pruefwerkzeug verloren: `out[r.name()] = payload(r, c)` wertet in Python
  die RECHTE Seite zuerst aus. Die Datei war immer korrekt, nur der Pruefer kaputt.
- **Durchlauf 3**: Etappe 2 zur Haelfte (Umriss, Tasten, Undo). Git-Repo, README, Lizenz.
- **Durchlauf 4**: Block-Geister fertig. Statt die API zu raten, die dekompilierten
  Minecraft-Quellen aus `build/moddev/artifacts/neoforge-21.1.248-sources.jar` gelesen —
  das hat die exakte Transformation und die VertexConsumer-Schnittstelle geklaert.
  Merke: bei API-Unsicherheit dort nachsehen, nicht raten.
