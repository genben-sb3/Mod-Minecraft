# HoloBuild — Fortschritt

Mod fuer Minecraft 1.21.1 + NeoForge 21.1.248 | Java 21 | Package `com.boyben.holobuild`
Plan: `C:\Users\ben_h\.claude\plans\hi-ich-bin-ben-lazy-simon.md`

## ⚠️ WICHTIGSTER PUNKT
**Vier Etappen Code sind fertig und KEINE davon lief je in Minecraft.**
`latest.log` ist Stand 28.08. immer noch vom 27.08. Je mehr ungetestet aufeinander
stapelt, desto schwerer wird die Fehlersuche, wenn dann doch etwas klemmt.
Der naechste sinnvolle Schritt ist ein Testlauf, nicht noch eine Etappe.

## Etappe 0 — Werkzeuge  ✅
JDK 21 portable, VS Code Java Pack, NeoForge MDK, Git-Repo, README, MIT-Lizenz.

## Etappe 1 — MVP  ✅ CODE FERTIG
Zauberstab platziert .nbt-Strukturen, Kreativ-Check, nur serverseitig.
Test-Haus (7x6x7, 270 Bloecke) liegt in der Instanz.

## Etappe 2 — Hologramm  ✅ CODE FERTIG
Durchscheinende Block-Geister + cyan Umriss, R/M/Bild-Tasten, Undo per Shift+Rechtsklick.

## Etappe 3 — Auswahl-GUI  ✅ CODE FERTIG
Taste **G**, Suchfeld, Dateiliste mit Groesse, Mausrad blaettert.
Namenspruefung gegen Path Traversal im `SelectSchematicPayload`.

## Etappe 4 — Animiertes Bauen  ✅ CODE FERTIG
- [x] `Config.java` — `blocksPerTick` (Standard 40, also ~800 Bloecke/Sekunde),
      `placeSound`. Server-Config. **0 = alles sofort, ohne Animation.**
- [x] `build/BuildAnimator.java` — eine Warteschlange pro Spieler, im
      `ServerTickEvent.Post` werden pro Tick N Bloecke gesetzt.
      Sortierung: erst Hoehe, dann Abstand zur Mitte -> das Gebaeude waechst nach oben.
      Sound gedrosselt auf max 3 pro Tick, sonst Krach.
      Nicht geladene Chunks werden uebersprungen statt erzwungen zu laden.
      Job endet automatisch, wenn der Spieler die Welt verlaesst.
- [x] Shift+Rechtsklick bricht einen laufenden Bau ab, **bevor** Undo laeuft
      (sonst wuerden sich Bauen und Rueckgaengig gegenseitig bekaempfen)

## Wichtige Entscheidungen (nicht "aufraeumen"!)
- `Placement.transformed()` nutzt bewusst das **deprecated** `BlockState.rotate(Rotation)`,
  weil `placeInWorld` (Vanilla-Quelle Zeile 254) genau das aufruft. Reihenfolge
  **erst mirror, dann rotate**. Sonst weicht das Hologramm vom Ergebnis ab.
- Sofort-Modus (`blocksPerTick=0`) nutzt Vanillas `placeInWorld`, weil das zusaetzlich
  Waterlogging und Structure Processors erledigt. Der animierte Modus nutzt unsere
  eigene Blockliste — deshalb ist es wichtig, dass beide identisch transformieren.
- Ab 30.000 Bloecken nur noch Umriss statt Block-Geister (Bildrate).

## Steuerung
| Eingabe | Wirkung |
|---|---|
| **G** | Bauplan auswaehlen |
| Zielen | Hologramm + Umriss |
| Rechtsklick | bauen (animiert) |
| Shift + Rechtsklick | abbrechen und/oder rueckgaengig |
| R / M | drehen / spiegeln |
| Bild hoch / runter | hoeher / tiefer |

## Bekannte Einschraenkungen
- Die GUI liest den Ordner des **Clients**. Singleplayer korrekt; auf einem echten
  Server muesste der Server seine Liste schicken.
- Der animierte Modus setzt keine Wasser-Logik und keine Structure Processors um.

## Git
Lokal: Branch `main`. Remote `genben-sb3/Mod-Minecraft`.
**Push geht von hier aus NICHT** — GitHub verlangt Anmeldung, diese Umgebung kann keine
Anmeldefenster oeffnen. Zweimal bestaetigt, wird nicht erneut versucht.
Ben muss in VS Code auf "Sync Changes" klicken.

## Log
- **Durchlauf 1**: Etappe 0 + 1. Java per ZIP statt winget (UAC haengt).
- **Durchlauf 2**: Fehler war EIN verlorener Backslash. Zeit im NBT-Pruefwerkzeug
  verloren: `out[r.name()] = payload(r, c)` wertet in Python die RECHTE Seite zuerst aus.
- **Durchlauf 3**: Etappe 2 halb. Git-Repo, README, Lizenz.
- **Durchlauf 4**: Block-Geister. Dekompilierte Quellen aus
  `build/moddev/artifacts/neoforge-21.1.248-sources.jar` gelesen statt API zu raten.
- **Durchlauf 5**: Etappe 3 GUI aus einfachen Buttons statt ObjectSelectionList.
- **Durchlauf 6**: Etappe 4. Zwei Fehler: `distSqr` gibt `double` nicht `long`, und die
  Mittelpunktberechnung war unnoetig ueber BoundingBox gebaut - jetzt direkt min/max.
  `getSoundType()` auf die kontextbewusste NeoForge-Variante umgestellt.
  Build ist jetzt komplett warnungsfrei.

## Naechster Schritt
**Erst testen.** Danach Etappe 5: `.schem` und `.litematic` lesen, eigene Auswahl
speichern (zweites Item "Auswahl-Stab" mit Ecke 1 / Ecke 2).
