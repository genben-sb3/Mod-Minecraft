# HoloBuild — Fortschritt

Mod fuer Minecraft 1.21.1 + NeoForge 21.1.248 | Java 21 | Package `com.boyben.holobuild`
Plan: `C:\Users\ben_h\.claude\plans\hi-ich-bin-ben-lazy-simon.md`

## Etappe 0 — Werkzeuge  ✅ FERTIG
- [x] JDK 21 portable: `C:\Users\ben_h\AppData\Local\Programs\jdk-21.0.12.1+1`
- [x] VS Code Java Extension Pack (via `...\Microsoft VS Code\bin\code.cmd`)
- [x] NeoForge MDK `archive/1.21-mdg`, auf HoloBuild umgebaut
- [x] Git-Repo, README, MIT-Lizenz

## Etappe 1 — MVP "Es baut!"  ✅ CODE FERTIG, TEST IM SPIEL OFFEN
- [x] Zauberstab platziert .nbt-Strukturen, Kreativ-Check, nur serverseitig
- [x] Test-Haus (7x6x7, 270 Bloecke), als NBT verifiziert
- [ ] **IM SPIEL TESTEN — immer noch offen**

## Etappe 2 — Hologramm & Steuerung  ✅ CODE FERTIG (bis auf Block-Geister)
- [x] `component/BlueprintSettings.java` — Auswahl, Drehung, Spiegelung, Hoehe als
      Data Component (Minecraft synchronisiert das automatisch Client<->Server)
- [x] `schematic/Placement.java` — **gemeinsame** Positionsberechnung fuer Hologramm
      UND Platzieren. Wichtig: sonst driften Vorschau und Realitaet auseinander.
- [x] `schematic/SchematicCache.java` — Key = Dateiname + Aenderungszeit
- [x] `undo/UndoManager.java` — Shift+Rechtsklick, 5 Schritte, max 250.000 Bloecke,
      sichert auch Block-Entity-Daten (Kisten, Create-Maschinen)
- [x] `client/HologramRenderer.java` — cyan Umriss an der Zielstelle,
      RenderLevelStageEvent, Fehler werden abgefangen (nie das Spiel crashen lassen)
- [x] `network/AdjustBlueprintPayload.java` + `ModNetwork.java` — Tastendruck zum Server
- [x] `client/ModKeybinds.java` — R drehen, M spiegeln, Bild hoch/runter Hoehe
- [x] Drehung um die Mitte statt um die Ecke
- [ ] **Block-Geister** (durchscheinende Bloecke statt nur Umriss) — naechster Schritt
      Dafuer noetig: eigene Blockliste in LoadedSchematic, weil StructureTemplate
      seine Bloecke nicht oeffentlich rausgibt. Wird auch fuer Etappe 4 gebraucht.

## Steuerung (Stand jetzt)
| Eingabe | Wirkung |
|---|---|
| Rechtsklick | Bauwerk platzieren |
| Shift + Rechtsklick | Rueckgaengig |
| R | 90 Grad drehen |
| M | spiegeln |
| Bild hoch / runter | hoeher / tiefer |

## Git
Lokal: Branch `main`, mehrere Commits. Remote `genben-sb3/Mod-Minecraft` ist eingetragen.
**Push geht von hier aus NICHT** — GitHub fragt nach Anmeldung und diese Umgebung kann
keine Anmeldefenster oeffnen. Ben muss in VS Code auf "Sync Changes" klicken.
Bens echte E-Mail wurde aus allen Commits entfernt (jetzt GitHub-Schutzadresse
`278487459+genben-sb3@users.noreply.github.com`).

## Testen (wenn Ben Zeit hat)
1. Instanz BoyBen2026 starten, Kreativmodus
2. Tab "HoloBuild" -> Zauberstab -> auf einen Block zielen -> **cyan Umriss** erscheint
3. R druecken -> Umriss dreht sich, Meldung ueber der Hotbar
4. Rechtsklick -> Haus erscheint genau im Umriss
5. Shift+Rechtsklick -> Haus verschwindet wieder
6. `/gamemode survival` -> nichts geht mehr, rote Meldung

## Log
- **Durchlauf 1**: Etappe 0 + 1. Java per ZIP statt winget (UAC).
- **Durchlauf 2**: Build gruen. Fehler war EIN verlorener Backslash im Java-Escape.
  Zeit im NBT-Pruefwerkzeug verloren: `out[r.name()] = payload(r, c)` wertet in Python
  die RECHTE Seite zuerst aus. Datei war immer korrekt, nur der Pruefer kaputt.
- **Durchlauf 3**: Etappe 2 groesstenteils. Git-Repo, README, MIT-Lizenz.
  Zwei Deprecations behoben (`createDataComponents(String)`, `EventBusSubscriber.bus()`).
  Push scheitert an fehlender Anmeldemoeglichkeit — nicht weiter versucht.

## Naechster Schritt
1. Block-Geister: Blockliste beim Laden mitspeichern, dann durchscheinend rendern
2. Etappe 3: GUI zum Auswaehlen der Schematic
