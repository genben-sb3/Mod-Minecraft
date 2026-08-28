# HoloBuild — Fortschritt

Mod fuer Minecraft 1.21.1 + NeoForge 21.1.248 | Java 21 | Package `com.boyben.holobuild`
Plan: `C:\Users\ben_h\.claude\plans\hi-ich-bin-ben-lazy-simon.md`

## Etappe 0 — Werkzeuge  ✅ FERTIG
- [x] JDK 21 portable: `C:\Users\ben_h\AppData\Local\Programs\jdk-21.0.12.1+1`
      (winget hing an UAC -> ZIP braucht keine Adminrechte). JAVA_HOME + PATH gesetzt.
- [x] VS Code Java Extension Pack (via `...\Microsoft VS Code\bin\code.cmd`, nicht `Code.exe`)
- [x] NeoForge MDK `archive/1.21-mdg` (ModDevGradle), auf HoloBuild umgebaut
- [x] `.\gradlew build` laeuft durch
- [x] Git-Repo angelegt, erster Commit (28 Dateien)

## Etappe 1 — MVP "Es baut!"  ✅ CODE FERTIG, TEST IM SPIEL OFFEN
- [x] Zauberstab, Rechtsklick platziert Struktur, Kreativ-Check, nur serverseitig
- [x] SchematicLoader (.nbt via StructureTemplate), Textur, Modell, lang de + en
- [x] Test-Haus `test_haus.nbt` (7x6x7, 270 Bloecke) erzeugt und als NBT verifiziert
- [x] JAR + Test-Haus in Instanz BoyBen2026 kopiert
- [ ] **IM SPIEL TESTEN — noch nicht passiert**

## Etappe 2 — Hologramm & Steuerung  🔨 HALB FERTIG
- [x] `component/BlueprintSettings.java` — Auswahl + Drehung + Spiegelung + Hoehe
      als Data Component am Item (Minecraft synchronisiert das automatisch
      Client<->Server, genau was der Hologramm-Renderer braucht)
- [x] `registry/ModComponents.java` — registriert, deprecated-Warnung behoben
      (`createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID)`)
- [x] `schematic/SchematicCache.java` — Cache-Key = Dateiname + Aenderungszeit,
      dadurch invalidiert eine bearbeitete Datei sich selbst
- [x] `undo/UndoManager.java` — Shift+Rechtsklick, bis 5 Schritte, max 250.000 Bloecke,
      sichert auch Block-Entity-Daten (Kisteninhalte, Create-Maschinen)
- [x] Zauberstab dreht um die **Mitte** (setRotationPivot), nicht um die Ecke
- [ ] **Hologramm-Rendering** (RenderLevelStageEvent) — das Kernstueck, noch offen
- [ ] **Tastenkuerzel** zum Drehen / Spiegeln / Hoehe verschieben — noch offen

## Git
Lokales Repo steht, Branch `main`. **Noch kein Remote** — Ben muss entscheiden,
wohin gepusht wird (GitHub-Konto noetig, Altersgrenze 13 wie bei CurseForge).

## Bens Instanz BoyBen2026
Create 6.0.10, Create Aeronautics 1.3.1, Sable 2.0.5. Welt: "Create".
Create speichert Schematics im selben Vanilla-.nbt-Format -> HoloBuild kann sie direkt lesen.

## Testen (wenn Ben Zeit hat)
1. Instanz starten, Welt "Create", Kreativmodus
2. Tab "HoloBuild" -> Blaupausen-Zauberstab -> Rechtsklick -> Haus erscheint
3. Shift+Rechtsklick -> Haus verschwindet wieder (Undo)
4. `/gamemode survival` -> darf nichts tun, rote Meldung

## Log
- **Durchlauf 1**: Etappe 0 + 1 geschrieben. Java ueber ZIP statt winget.
- **Durchlauf 2**: Build gruen. Erster Fehler war EIN verlorener Backslash in einem
  Java-Escape. Die drei verdaechtigen NeoForge-APIs waren alle korrekt.
  Viel Zeit im NBT-Pruefwerkzeug verloren: `out[r.name()] = payload(r, c)` wertet in
  Python die RECHTE Seite zuerst aus -> las Wert vor Name. Die Datei war immer korrekt.
- **Durchlauf 3**: Etappe 2 zur Haelfte. Data Components, Undo, Drehung um die Mitte.
  Git-Repo initialisiert. Deprecation behoben. Build gruen.

## Naechster Schritt
1. Hologramm-Rendering (RenderLevelStageEvent, gecachter VertexBuffer)
2. Tastenkuerzel zum Drehen
3. Sobald Ben ein GitHub-Repo nennt: Remote setzen und pushen
