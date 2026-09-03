# HoloBuild — Stand

Mod für Minecraft 1.21.1 + NeoForge 21.1.248 | Java 21 | `com.boyben.holobuild`
Plan: `C:\Users\ben_h\.claude\plans\hi-ich-bin-ben-lazy-simon.md`
Veröffentlichen: `CURSEFORGE.md`

**Der Bau-Loop läuft** (alle 15 Minuten), an Bens Wunschliste vom 01.09.2026.
Reihenfolge: erst reparieren, dann Neues. Siehe „Etappenplan" unten.

---

## Was der Mod kann

| Bereich | Inhalt |
|---|---|
| Vorschau | Durchscheinendes Hologramm vor dem Bauen, Umriss **rot** wenn es nicht in die Welt passt. Reichweite 32 statt 5 Blöcke. |
| Bauen | Animiert von unten nach oben mit Sound, oder sofort. Geschwindigkeit einstellbar. |
| Rückgängig | Shift + Rechtsklick, inklusive Kisteninhalten und Maschinen-Daten. |
| Formate | `.nbt` (Struktur-Block), `.schem` (WorldEdit), `.litematic` (Litematica) |
| Eigene Bauwerke | Auswahl-Stab (zwei Ecken) + `/holobuild save <name>` |
| Speicher-Optionen | Luft / Kisteninhalte / Rahmen einzeln an und aus, `/holobuild options` |
| Baukasten | `/holobuild random tower <name>` würfelt aus Modulen ein neues Gebäude |
| Bibliothek | Fünf mitgelieferte Bauwerke + fünf Turm-Module |
| Menü | Taste **G**: Suche, Favoriten ⭐, Ordner, Sortierung, Infos, Steuerung |
| Anleitung | Zehn Seiten im Spiel, Knopf unten im Menü — kein Nachschlagen im Netz nötig |
| Herstellen | Alle drei Stäbe craftbar: Amethyst + Kern + Stock. Diamant / Eisen / Spitzhacke als Kern |
| Baugrund-Stab | Räumt den Platz frei, den das gewählte Bauwerk füllen würde — mit grüner Vorschau und Rückgängig |
| Vorschau im Menü | Drehendes 3D-Modell — auch schon beim **Drüberfahren**, ohne Klick |
| Tooltips | Beide Stäbe erklären beim Drüberfahren ihre Steuerung und ihren Zustand |
| Survival | Hologramm + Materialliste; optional Bauen aus dem Inventar (`survivalBuilding`) |
| Materialprüfung | Zu wenig Blöcke? Der Stab nennt das Fehlende und wartet — nochmal klicken baut trotzdem |
| Befehle | `build`, `save`, `list`, `random`, `options` — mit Operator-Rechten, auch im Survival |

## Tests

- **60 Unit-Tests** — `.\gradlew test`
  Bit-Packing (.litematic), varints (.schem), Namensprüfung gegen Path Traversal,
  Speicher-Filter (Luft/Inhalte/Entities), Vollständigkeit der Sprachdateien,
  Zeilen-Trefferrechnung der Liste, Aufbau und Zeilenlänge der Anleitung,
  Animations-Zeitrechnung
- **27 GameTests im echten Server** — `.\gradlew runGameTestServer` (~10 Sekunden)
  Laden, Bauen, Undo, Drehen, animiertes Bauen über Ticks, Türen, Speichern und
  Wiederladen, Luft-Weglassen in beide Richtungen, abgelehnte Blöcke,
  Cache-Kapazität, Crafting-Rezepte (auch: falsches Muster ergibt nichts),
  Speicherbarkeit der Bau-Zusage, Freiräumen und sein Rückgängig,
  Materialliste gegen Verbrauch, Bezahlen, Baukasten, beide
  Fremdformate, Befehle, Sicherheitsgrenzen

Alle Tests werden gegengeprüft: mit absichtlich kaputtem Code **müssen** sie
fehlschlagen. Wer das nicht macht, sammelt Tests, die nichts prüfen — fünf
solche Fälle sind hier schon aufgeflogen, einer davon am 01.09.2026 an
brandneuem Code.

Umgekehrt gilt auch: **eine Sabotage, die keinen Test umwirft, ist nicht
automatisch eine Lücke.** Am 01.09.2026 blieb `(intoList + 1) / rowHeight`
grün — zu Recht, denn die Lücken-Regel fängt genau die Pixel ab, an denen sich
das anders verhalten hätte. Die Sabotage war ungültig, nicht der Test lückenhaft.

## Was die Tests gefunden haben

- **Speicherleck**: `UndoManager.forget()` existierte, wurde aber nie aufgerufen —
  Undo-Daten blieben nach dem Verlassen der Welt für immer im Speicher.
- **Format-Inkonsistenz**: Der `.litematic`-Leser nahm die Größe aus den belegten
  Blöcken statt aus der Datei. Da um `Größe/2` gedreht wird, hätte sich ein
  Bauwerk mit Luftrand um den falschen Punkt gedreht.
- **Weltgrenzen**: `Level.setBlock` tut außerhalb der Bauhöhe stillschweigend
  nichts. Ohne Prüfung wären Bauwerke halb erschienen, ohne jede Meldung.
- **Luft als Block**: Gespeicherte Bauwerke enthielten die Luft dazwischen und
  stanzten sich damit einen Kasten ins Gelände, statt sich hineinzusetzen.
- **Verschwundene Blöcke**: Was außerhalb geladener Chunks lag, wurde übersprungen —
  ohne Zählung, ohne Meldung. Ein Bauwerk konnte mit einer fehlenden Wand
  herauskommen und niemand erfuhr davon.
- **`setBlock` sagt zweimal dasselbe**: Es meldet `false` sowohl bei „verweigert"
  als auch bei „war schon richtig". Wer beides gleich behandelt, meldet ein
  Bauwerk auf passendem Untergrund als voller Löcher. Vom Test an frischem Code
  gefunden, keine Stunde alt.

## Wichtige Entscheidungen (nicht "aufräumen"!)

- `Placement.transformed()` nutzt bewusst das **abgekündigte**
  `BlockState.rotate(Rotation)`, weil `placeInWorld` genau das aufruft.
  Reihenfolge **erst mirror, dann rotate**. Sonst weicht die Vorschau vom
  Ergebnis ab.
- Fremdformate werden sofort ins Vanilla-Structure-Format umgeschrieben —
  deshalb musste für `.schem` und `.litematic` kein anderer Bereich angefasst werden.
- **Gespeichert wird in zwei Schritten**: Vanillas `fillFromWorld` liest die Welt
  (das kann es zuverlässig, inklusive Kisteninhalten), danach wirft
  `StructureFilter` aus dem fertigen NBT, was nicht mit soll. Vanilla kann nur
  *einen* Blocktyp auslassen — zu wenig. Der Filter erkennt Luft am Namen im
  Palette-Eintrag und braucht deshalb weder Welt noch Registry: testbar ohne Spiel.
- **Vor `setBlock` wird gefragt, ob der Block schon stimmt.** Sieht überflüssig
  aus, ist es nicht: sonst zählt jedes Bauwerk auf passendem Untergrund seine
  eigenen Blöcke als abgelehnt.
- **Der `SchematicCache` hält mehrere Bauwerke, nicht eines.** Sobald das Menü
  beim Drüberfahren eine Vorschau zeigt, wollen Hologramm und Vorschau zwei
  verschiedene Dateien gleichzeitig. Mit einem Platz würfen die beiden sich
  sechzigmal pro Sekunde gegenseitig hinaus. Die Grenze zählt **Blöcke**, nicht
  Einträge — acht Hütten sind nichts, eine Kathedrale ist viel.
- **200 ms Wartezeit vor der Hover-Vorschau.** Ohne sie liest ein Mauswisch über
  die Liste jede Datei, an der er vorbeikommt.
- `BuildAnimator.Report` hält die Zahlen des letzten Baus fest. Eine Zahl, die nur
  in einer Chat-Meldung steht, kann niemand prüfen — weder ein Test noch später
  ein Bildschirm.
- **Die Anleitung zählt ihre Textschlüssel einzeln auf**, statt sie aus einer
  Schleife zusammenzusetzen. `LanguageFilesTest` sucht die Schlüssel im Quelltext;
  ein aus `"seite" + i` gebauter Name rutscht daran vorbei und fällt erst im Spiel
  als Rohtext auf.
- **Die Anleitung bricht Zeilen um, statt auf passende Länge zu hoffen.** Deutsch
  braucht für dasselbe mehr Buchstaben, und die Menügröße kann jeder ändern —
  Zeichen abzählen wäre nur für eine Sprache in einer Größe richtig.
- `Fade` rechnet Animations-Fortschritt aus, `ListLayout` Zeilentreffer — beide
  ohne Minecraft-Klassen. Zeichnen lässt sich nicht testen, rechnen schon, und
  genau da geht es schief: ein Fortschritt über 1 lässt eine Farbe überlaufen,
  eine Dauer von 0 teilt durch null. Nichts davon stürzt ab, es sieht nur kaputt aus.
- `ListLayout` liegt außerhalb des Bildschirms und ohne Minecraft-Klassen. Ein
  `Screen` lässt sich in keinem Test bauen, diese Rechnerei schon — und sie ist
  reines Off-by-one-Gelände.
- **Der Baugrund-Stab kam aus der Luft-Änderung.** Seit gespeicherte Bauwerke die
  Luft nicht mehr mitbringen, setzen sie sich ins Gelände — gut in der Ebene, am
  Hang steht der Hügel im Wohnzimmer. Statt die Luft zurückzuholen, wurde das
  Freiräumen ein eigenes Werkzeug: eine Entscheidung des Spielers statt eine der Datei.
- **Die Bau-Zusage liegt auf dem Stab, nicht in einer Spielerliste.** Genau so eine
  Liste war das Undo-Leck: nichts hat sie je geleert. Daten auf einem Gegenstand
  brauchen kein Aufräumen — sie gehen, wenn der Gegenstand geht.
- Speicher-Optionen leben **auf dem Stab**, nicht in der Config: eine Einstellung
  pro Spieler statt einer fürs ganze Spiel, und im Tooltip sichtbar statt in einer
  Datei versteckt.
- Reichweite als **Item-Attribut**, nicht als Eingriff am Spieler: gilt nur mit
  Stab in der Hand.
- `BlockCost` ist die einzige Stelle, die Blockkosten kennt.
- Zwei unabhängige Verteidigungen gegen Path Traversal (`SchematicName.isSafe`
  und die Pfadprüfung in `findByName`). Ein GameTest belegt, dass jede allein greift.

## ⚠️ Werkzeug-Fallen

- `perl -0pi -e` mit **mehrzeiligen** Mustern hängt den Ersatz an den
  **Dateianfang**. Hat zweimal das README zerschossen.
  **Mehrzeilige Änderungen an Textdateien immer per Neuschreiben.**
- **Kein Python auf diesem Rechner.** Für kleine Skripte `node` benutzen — aber
  deutsche Anführungszeichen („ ") in einem `node -e` aus der Bash heraus
  zerlegen den Aufruf. Bei deutschem Text lieber die ganze Datei neu schreiben.
- `winget` scheitert an UAC. Werkzeuge als portable ZIP.
- Unit-Tests laufen seit dem `unitTest`-Block in `build.gradle` **nicht mehr im
  Projektordner**, sondern in `build/minecraft-junit`. Wer aus einem Test eine
  Datei lesen will, bekommt den Pfad über `systemProperty` gereicht.

---

## Etappenplan (Bens Liste vom 01.09.2026)

### A — Reparieren & Feinschliff ✅
- [x] **Tooltips über beiden Stäben** — erklären Rechtsklick und Shift+Rechtsklick.
      Das war der „kaputte" Auswahl-Stab: er ging immer, nur sagte es niemand.
- [x] **Luft nicht mitspeichern** (`StructureFilter`), abschaltbar
- [x] **Kisteninhalte, Repeater-Stellungen, Shulker-Kisten** — einzeln umschaltbar
      über `/holobuild options`, gespeichert auf dem Stab
- [x] **Alle Blöcke beim Bauen** — was außerhalb geladener Chunks oder außerhalb
      der Welt lag, verschwand wortlos. Wird jetzt gezählt und gemeldet.

### B — Bedienung ✅
- [x] **3D-Vorschau beim Drüberfahren** in der Liste, mit 200 ms Wartezeit und
      Beschriftung darunter. Dafür musste erst der Cache mehr als ein Bauwerk
      halten können.
- [x] **Anleitung im Spiel** — sieben Seiten, Knopf „Anleitung“ unten im Menü,
      Blättern mit Pfeiltasten oder Maus. Merkt sich die Seite.
- [x] **Cleaneres Design, Sound und Animationen** — Panels mit Rahmen hinter Liste
      und Vorschau, der gewählte Eintrag ist als ▶ in Gold markiert, Klänge beim
      Wählen / Favorisieren / Blättern, Anleitung blendet Seiten ein.

### C — Survival ✅
- [x] **Crafting-Rezepte** — beide Stäbe teilen eine Form (Amethyst oben rechts,
      Kern in der Mitte, Stock unten links). Der Kern setzt den Preis: Eisen für
      den harmlosen Auswahl-Stab, Diamant für den, der ganze Häuser setzt.
- [x] **Survival-Anforderungen** — wer nicht genug Material hat, bekommt erst
      gesagt was fehlt (die drei größten Posten plus „und N weitere Sorten“).
      Ein zweiter Klick innerhalb von 10 Sekunden baut trotzdem, so weit es reicht.
- [x] **Baugrund-Stab** — räumt genau den Kasten frei, den der gewählte Bauplan
      füllen würde. Grüne Umriss-Vorschau vorher, Rückgängig hinterher,
      Kreativmodus only.

### D — Editor  ← *hier geht es weiter*
- [ ] Drehbares 3D-Modell des Bauwerks, **Blöcke löschen und bearbeiten**, abspeichern

### E — Teilen
- [ ] Einfacher Weg, Bauwerke mit anderen zu tauschen

---

## 👉 Was nur Ben kann

**1. Hinschauen.** Die Oberfläche — 3D-Vorschau, Hover-Vorschau,
Material-Bildschirm, Menü-Knöpfe und die neuen Tooltips — hat noch nie jemand im
Spiel gesehen. Render-Code kann fehlerfrei kompilieren und trotzdem falsch
aussehen. Stand 01.09.2026: Bens letzte Spielsitzung war am 30.08. um 21:24.

**2. Etappe 7, Online-Bibliothek.** Braucht ein **öffentliches** Repo; das
private `Mod-Minecraft` kann der Mod nicht lesen, ohne ein Passwort
mitzuliefern. Sauberer Weg: Code privat lassen, ein zweites öffentliches Repo
nur für die Bauwerke.

**3. CurseForge.** Siehe `CURSEFORGE.md` — Beschreibung fertig, Checkliste
dabei. Der Account muss einem Elternteil gehören (Altersgrenze 13).

*Der Push geht seit 01.09.2026 von hier aus — Ben hat sich in VS Code
angemeldet, seitdem liegen die Zugangsdaten im Credential Manager.*
