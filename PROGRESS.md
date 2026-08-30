# HoloBuild — Fortschritt

Mod fuer Minecraft 1.21.1 + NeoForge 21.1.248 | Java 21 | Package `com.boyben.holobuild`
Plan: `C:\Users\ben_h\.claude\plans\hi-ich-bin-ben-lazy-simon.md`

## Stand des Testens
- ✅ **16 automatische Tests**, alle gruen (`.\gradlew test`)
  `BitArrayTest` (9, .litematic) und `VarIntStreamTest` (7, .schem),
  mit von Hand gerechneten Werten und mit Gegenprobe geprueft.
- ✅ **30.08.2026: Der Mod laeuft im Spiel.** Log zeigt "HoloBuild 0.1.0 (holobuild)"
  und die eigene Startmeldung. **Keine einzige HoloBuild-Fehlerzeile.**
  (Die roten Fehler im Log sind Create: `create:analog_lever` an Positionen mit Luft -
  Folge des Aeronautics-Updates 1.3.1 -> 1.3.2, nicht unser Problem.)
- ⏳ Die einzelnen Funktionen (Hologramm, Tasten, Bauen, Undo) noch nicht durchprobiert.

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
| 6b | Weltgrenzen-Absicherung (Hologramm wird rot) |
| 6c | Reichweite der Staebe 5 -> 32 Bloecke |
| 8 | Baukasten: Bauwerke automatisch zusammenwuerfeln |
| 8b | **Platzier-Reihenfolge + Nachbar-Update: Tueren, Bloementoepfe, Redstone** |

## Etappe 8 — Baukasten  ✅ CODE FERTIG
Bens ehrgeizigster Wunsch, umgesetzt nach demselben Prinzip wie Minecrafts
eigener Dorf-Generator: austauschbare Teile, die zu etwas zusammengesetzt werden,
das aussieht als waere es entworfen worden.

- [x] `generator/ModulePack.java` — findet die Teile **allein am Namen**:
      `<set>_base`, `<set>_mid_*` (beliebig viele), `<set>_top`.
      Ein neues Stockwerk hinzufuegen heisst also: Schematic unter passendem
      Namen speichern. Kein Code, keine Konfigurationsdatei.
      Prueft, dass alle Teile dieselbe Grundflaeche haben - sonst klare Fehlermeldung.
- [x] `generator/StackGenerator.java` — Sockel, dann pro Etage ein **neu
      gewuerfeltes** Mittelteil, dann Dach. 1 bis 12 Etagen.
- [x] `/holobuild random <set> <name> [etagen]` — ohne Etagenzahl 2-6 zufaellig
- [x] Fuenf Turm-Module mitgeliefert: `tower_base`, `tower_mid_1` (Fenster),
      `tower_mid_2` (Sims), `tower_mid_3` (Fachwerk), `tower_top` (Zinnen)
- [x] Ergebnis ist eine **ganz normale Schematic-Datei** — vorschaubar, drehbar,
      baubar, und von Hand nachbesserbar wenn eine Etage komisch geraten ist

Hinweis: Die Module tauchen auch einzeln im Auswahl-Menue auf. Das ist Absicht -
man kann sie anschauen und einzeln bauen. Wer sie nicht sehen will, sucht nach
dem Namen des fertigen Bauwerks.

## Unterstuetzte Dateiformate
`.nbt` (Struktur-Block) · `.schem` (WorldEdit/Sponge) · `.litematic` (Litematica)

## Steuerung
| Eingabe | Wirkung |
|---|---|
| **G** | Bauplan auswaehlen (Stern = Favorit) |
| Zielen (Blaupausen-Stab) | Hologramm, cyan = passt, **rot = passt nicht**. Reichweite **32 Bloecke** |
| Rechtsklick | bauen |
| Shift + Rechtsklick | abbrechen und/oder rueckgaengig |
| R / M | drehen / spiegeln |
| Bild hoch / runter | hoeher / tiefer |
| Rechtsklick (Auswahl-Stab) | Ecke 1 |
| Shift + Rechtsklick (Auswahl-Stab) | Ecke 2 |
| `/holobuild save <name>` | Auswahl speichern |
| `/holobuild list` | alle Schematics auflisten |
| `/holobuild build <name> [pos] [drehung]` | **bauen ohne Stab, auch im Survival** |
| `/holobuild random tower <name>` | **neuen Turm wuerfeln** |

## Wichtige Entscheidungen (nicht "aufraeumen"!)
- `Placement.transformed()` nutzt bewusst das **deprecated** `BlockState.rotate(Rotation)`,
  weil `placeInWorld` genau das aufruft. Reihenfolge **erst mirror, dann rotate**.
- Fremdformate werden sofort ins Vanilla-Structure-Format umgeschrieben.
- Reichweite als **Item-Attribut**, nicht als Eingriff am Spieler: gilt nur mit
  Stab in der Hand, verschwindet beim Weglegen.
- Eigene Dateien gewinnen gegen mitgelieferte Bauwerke gleichen Namens.

## ⚠️ WERKZEUG-FALLE (zweimal reingetappt!)
`perl -0pi -e` mit **mehrzeiligen** Suchmustern haengt den Ersatz an den
**Dateianfang**, statt zu ersetzen. Hat in D7 und D10 das README zerschossen.
**Regel: mehrzeilige Aenderungen an Textdateien immer per Neuschreiben der Datei.**

## Bekannte Einschraenkungen
- GUI liest den Ordner des **Clients** — Singleplayer korrekt, echter Server nicht.
- Animierter Modus ohne Waterlogging und Structure Processors.
- `.schem` bringt keine Block-Entities mit (.litematic dagegen schon).
- Mitgelieferte Bauwerke sind per Skript erzeugt, nicht handgebaut.

## Git
Lokal: Branch `main`. Remote `genben-sb3/Mod-Minecraft` (**privat**).
**Push geht von hier aus NICHT** — GitHub verlangt Anmeldung, diese Umgebung kann
keine Anmeldefenster oeffnen. Ben muss in VS Code auf "Sync Changes" klicken.

## Etappe 7 — braucht noch eine Entscheidung
Die Online-Bibliothek wuerde ein **oeffentliches** Repo brauchen; ein privates
kann der Mod nicht lesen, ohne ein Passwort mitzuliefern. Sauberer Weg waere:
Code privat lassen, ein zweites oeffentliches Repo nur fuer die Bauwerke.

## Log
- **D1-D6**: Etappen 0 bis 4. Java per ZIP statt winget (UAC).
- **D7**: .schem-Import. README mit perl beschaedigt.
- **D8**: Auswahl-Stab + Befehle.
- **D9**: .litematic + erste echte Tests, mit Gegenprobe.
- **D10**: Favoriten + varint-Tests. README erneut mit perl beschaedigt.
- **D11**: Bau-Bibliothek.
- **D12**: Weltgrenzen-Fehler gefunden und abgesichert.
- **D13**: Ben hat gestartet - **der Mod laeuft**. Reichweite auf 32 erhoeht.
- **D14**: Baukasten. Alle Builds gruen im ersten Versuch.

## Bens Wunschliste vom 30.08. (Reihenfolge nach Wert je Aufwand)
1. [x] Infos im Auswahl-Menue (Groesse, Bloecke, Hauptmaterial) — Tooltip pro Eintrag
2. [x] Befehle fuer jedes Bauwerk, auch im Survival (`/holobuild build <name>`)
3. [x] Drehen/Spiegeln/Hoehe direkt im Menue
4. [ ] Ordner und Sortierung
5. [ ] Survival: Materialliste + Hologramm
6. [ ] Survival: Bauen aus dem Inventar (umschaltbar)
7. [ ] 3D-Vorschau im Menue (aufwendigster Punkt, deshalb zuletzt)

## Naechster Schritt
Ben soll die Funktionen im Spiel durchprobieren, besonders
`/holobuild random tower meinturm`.
