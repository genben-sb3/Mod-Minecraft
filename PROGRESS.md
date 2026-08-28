# HoloBuild — Fortschritt

Mod fuer Minecraft 1.21.1 + NeoForge 21.1.248 | Java 21 | Package `com.boyben.holobuild`
Plan: `C:\Users\ben_h\.claude\plans\hi-ich-bin-ben-lazy-simon.md`

## Etappe 0 — Werkzeuge  ✅ FERTIG
- [x] JDK 21 (Temurin 21.0.12.1) portable nach
      `C:\Users\ben_h\AppData\Local\Programs\jdk-21.0.12.1+1`, JAVA_HOME + PATH gesetzt
      (winget hing an einer UAC-Abfrage -> ZIP-Variante braucht keine Adminrechte)
- [x] VS Code Java Extension Pack (redhat.java, gradle, debug, test, maven)
      installiert ueber `...\Microsoft VS Code\bin\code.cmd` (nicht `Code.exe`!)
- [x] NeoForge MDK, Branch `archive/1.21-mdg` (ModDevGradle, ist fuer MC 1.21.1)
- [x] Auf HoloBuild umgebaut, neo_version auf 21.1.248 = Bens Instanz
- [x] `.\gradlew build` laeuft durch

## Etappe 1 — MVP "Es baut!"  ✅ CODE FERTIG, TEST IM SPIEL OFFEN
- [x] Alle 6 Klassen, Textur, Modell, lang en_us + de_de
- [x] `neoforge.mods.toml` korrekt befuellt inkl. Credit an Schematica & Litematica
- [x] `holobuild-0.1.0.jar` gebaut (16 KB), Inhalt geprueft
- [x] Test-Haus `test_haus.nbt` erzeugt (7x6x7, 270 Bloecke) und als gueltiges
      Structure-NBT verifiziert (DataVersion 3955)
- [x] JAR + Test-Haus in die Instanz BoyBen2026 kopiert
- [ ] **IM SPIEL TESTEN — Ben ist dran**

## Bens Instanz BoyBen2026
Andere Mods: Create 6.0.10, Create Aeronautics 1.3.1, Sable 2.0.5. Eine Welt: "Create".
Wichtig: Create speichert Schematics im selben Vanilla-.nbt-Format -> HoloBuild kann
Create-Schematics direkt lesen. Das ist ein Verkaufsargument fuer spaeter.

## Was beim Testen passieren soll
1. Instanz BoyBen2026 starten, Welt "Create" im **Kreativmodus**
2. Kreativ-Inventar -> Tab "HoloBuild" -> Blaupausen-Zauberstab
3. Rechtsklick auf einen Block -> Haus erscheint, gruene Chat-Meldung
4. Gegenprobe: `/gamemode survival` -> Rechtsklick darf NICHTS tun, rote Meldung

## Log
- **28.08.2026, Durchlauf 1**: Etappe 0 + Etappe 1 komplett geschrieben.
- **28.08.2026, Durchlauf 2**: Erster Build scheiterte an EINEM Zeichen — `"(?i)\.nbt$"`,
  ungueltiges Java-Escape, weil ein Backslash beim Schreiben der Datei verloren ging.
  Stelle regexfrei umgeschrieben (endsWith/substring), Build gruen.
  Die drei API-Stellen, die ich im Verdacht hatte (`registerItem`, `holderLookup`,
  `getList`), waren **alle korrekt** — kein NeoForge-Problem.
  Danach viel Zeit im NBT-Pruefwerkzeug verloren: erst PowerShell-Replace an CRLF
  gescheitert, dann ein Python-Fallstrick — `out[r.name()] = payload(r, c)` wertet die
  RECHTE Seite zuerst aus, las also Wert vor Name. Die erzeugte Datei war die ganze Zeit
  korrekt; nur der Pruefer war kaputt.

## Naechster Schritt
Ben testet im Spiel. Danach Etappe 2 (Hologramm-Vorschau + Undo).
Wenn der Test scheitert: `Instances\BoyBen2026\logs\latest.log` ansehen.
