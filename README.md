# AleMine – alle meine Blöcke

Reiner **Client-Mod** für Minecraft **26.3 (Fabric)**: gräbt automatisch einen Bereich aus, ohne dass du etwas anfassen musst. Auf dem Server wird nichts installiert.

## Installation
1. Fabric Loader für 26.3 installieren, dazu **Fabric API** (0.165.x+26.3) in `.minecraft/mods`.
2. `alemine-1.0.0.jar` (aus den GitHub-Releases → `latest`) ebenfalls in `.minecraft/mods`.

## Benutzung
Stell dich an die Ecke, wo das Loch anfangen soll, und schau in die Richtung, in die es sich ausdehnen soll.

```
/alemine start <länge> <breite> <tiefe>
```
- **Länge** = nach vorne (Blickrichtung, wird auf N/O/S/W eingerastet)
- **Breite** = nach rechts
- **Tiefe** = nach unten, ab dem Block unter deinen Füßen

Beispiel: `/alemine start 8 8 10` → 8×8 Blöcke, 10 tief.

| Befehl / Taste | Funktion |
|---|---|
| `/alemine pause` oder **K** | Pause / Weiter |
| `/alemine stop` oder **J** | Abbrechen |
| `/alemine status` | Fortschritt |

Tasten änderbar unter Optionen → Steuerung → AleMine.

## So arbeitet er
- Gräbt 2 Blöcke unter sich weg, fällt rein, läuft dann in **Schlangenlinien** durch die Ebene: Kopf- und Fußblock vor sich abbauen, einen Schritt, am Reihenende drehen. Dann die nächsten 2 Ebenen.
- Nimmt automatisch das schnellste passende Werkzeug aus der Hotbar (fast kaputte werden übersprungen).
- **Stoppt sicher** bei: Lava/Wasser in der Nähe, Höhle/kein Boden, Bedrock, weniger als 3 Herzen, Feststecken.
- Kies/Sand, der nachrutscht, wird erneut abgebaut.

## Tipps
- **F3 + P** schaltet „Pause bei Fokusverlust“ aus – dann kannst du Minecraft in den Hintergrund legen.
- Genug Spitzhacken in die Hotbar legen; ein volles Inventar wird nicht geleert.
- Auf fremden Servern ist so etwas meist verboten – gedacht für Singleplayer und deinen eigenen Server.

## Entwickeln
`./gradlew build` (Java 25) → `build/libs/`. Hochladen per Doppelklick auf `Hochladen.bat`; GitHub Actions baut die .jar automatisch.
