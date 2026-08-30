# HoloBuild — Stand

Mod für Minecraft 1.21.1 + NeoForge 21.1.248 | Java 21 | `com.boyben.holobuild`
Plan: `C:\Users\ben_h\.claude\plans\hi-ich-bin-ben-lazy-simon.md`
Veröffentlichen: `CURSEFORGE.md`

**Der Bau-Loop wurde am 30.08.2026 angehalten**, weil alles Verbliebene eine
Entscheidung oder einen Handgriff von Ben braucht. Was das ist, steht unten.

---

## Was der Mod kann

| Bereich | Inhalt |
|---|---|
| Vorschau | Durchscheinendes Hologramm vor dem Bauen, Umriss **rot** wenn es nicht in die Welt passt. Reichweite 32 statt 5 Blöcke. |
| Bauen | Animiert von unten nach oben mit Sound, oder sofort. Geschwindigkeit einstellbar. |
| Rückgängig | Shift + Rechtsklick, inklusive Kisteninhalten und Maschinen-Daten. |
| Formate | `.nbt` (Struktur-Block), `.schem` (WorldEdit), `.litematic` (Litematica) |
| Eigene Bauwerke | Auswahl-Stab (zwei Ecken) + `/holobuild save <name>` |
| Baukasten | `/holobuild random tower <name>` würfelt aus Modulen ein neues Gebäude |
| Bibliothek | Fünf mitgelieferte Bauwerke + fünf Turm-Module |
| Menü | Taste **G**: Suche, Favoriten ⭐, Ordner, Sortierung, Infos, 3D-Vorschau, Steuerung |
| Survival | Hologramm + Materialliste; optional Bauen aus dem Inventar (`survivalBuilding`) |
| Befehle | `build`, `save`, `list`, `random` — mit Operator-Rechten, auch im Survival |

## Tests

- **24 Unit-Tests** — `.\gradlew test`
  Bit-Packing (.litematic), varints (.schem), Namensprüfung gegen Path Traversal
- **16 GameTests im echten Server** — `.\gradlew runGameTestServer` (~10 Sekunden)
  Laden, Bauen, Undo, Drehen, animiertes Bauen über Ticks, Türen, Speichern und
  Wiederladen, Materialliste gegen Verbrauch, Bezahlen, Baukasten, beide
  Fremdformate, Befehle, Sicherheitsgrenzen

Alle Tests wurden gegengeprüft: mit absichtlich kaputtem Code müssen sie
fehlschlagen. Dabei kamen **vier wertlose Tests** heraus, die inzwischen
entweder repariert oder ehrlich beschriftet sind.

## Was die Tests gefunden haben

- **Speicherleck**: `UndoManager.forget()` existierte, wurde aber nie aufgerufen —
  Undo-Daten blieben nach dem Verlassen der Welt für immer im Speicher.
- **Format-Inkonsistenz**: Der `.litematic`-Leser nahm die Größe aus den belegten
  Blöcken statt aus der Datei. Da um `Größe/2` gedreht wird, hätte sich ein
  Bauwerk mit Luftrand um den falschen Punkt gedreht.
- **Weltgrenzen**: `Level.setBlock` tut außerhalb der Bauhöhe stillschweigend
  nichts. Ohne Prüfung wären Bauwerke halb erschienen, ohne jede Meldung.

## Wichtige Entscheidungen (nicht "aufräumen"!)

- `Placement.transformed()` nutzt bewusst das **abgekündigte**
  `BlockState.rotate(Rotation)`, weil `placeInWorld` genau das aufruft.
  Reihenfolge **erst mirror, dann rotate**. Sonst weicht die Vorschau vom
  Ergebnis ab.
- Fremdformate werden sofort ins Vanilla-Structure-Format umgeschrieben —
  deshalb musste für `.schem` und `.litematic` kein anderer Bereich angefasst werden.
- Reichweite als **Item-Attribut**, nicht als Eingriff am Spieler: gilt nur mit
  Stab in der Hand.
- `BlockCost` ist die einzige Stelle, die Blockkosten kennt. Materialliste und
  Verbrauch benutzen sie gemeinsam; ein GameTest prüft, dass sie übereinstimmen.
- Zwei unabhängige Verteidigungen gegen Path Traversal (`SchematicName.isSafe`
  und die Pfadprüfung in `findByName`). Ein GameTest belegt, dass jede allein greift.

## ⚠️ Werkzeug-Fallen

- `perl -0pi -e` mit **mehrzeiligen** Mustern hängt den Ersatz an den
  **Dateianfang**. Hat zweimal das README zerschossen.
  **Mehrzeilige Änderungen an Textdateien immer per Neuschreiben.**
- `winget` und `git push` scheitern hier an fehlenden Eingabemöglichkeiten
  (UAC, Anmeldefenster). Werkzeuge als portable ZIP, Push über VS Code.

---

## 👉 Was jetzt Ben braucht

**1. Die Oberfläche anschauen.** 40 Tests decken alles ab, was prüfbar ist — aber
3D-Vorschau, Material-Bildschirm und die Menü-Knöpfe hat noch nie jemand
gesehen. Render-Code kann fehlerfrei bauen und trotzdem falsch aussehen.
Instanz starten, **G** drücken, hinschauen.

**2. Der Push.** Alle Commits liegen bisher nur lokal. In VS Code unten links auf
"Sync Changes". Von hier aus geht es nicht — GitHub verlangt eine Anmeldung,
und diese Umgebung kann kein Anmeldefenster öffnen (zweimal bestätigt).

**3. Etappe 7, Online-Bibliothek.** Braucht ein **öffentliches** Repo; das
private `Mod-Minecraft` kann der Mod nicht lesen, ohne ein Passwort
mitzuliefern. Sauberer Weg: Code privat lassen, ein zweites öffentliches Repo
nur für die Bauwerke.

**4. CurseForge.** Siehe `CURSEFORGE.md` — Beschreibung fertig, Checkliste
dabei. Der Account muss einem Elternteil gehören (Altersgrenze 13).
