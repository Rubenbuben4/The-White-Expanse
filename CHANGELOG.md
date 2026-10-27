# Changelog – The White Expanse

Pre-Alpha: frühe Testversionen, vieles ist noch nicht im Spiel getestet. / Pre-alpha: early test versions, much of it is not tested in game yet.

## 0.2.80 (2026-10-27)

### Deutsch

**Eisriese**
- Vorbeischleichen: Der Riese nimmt Spieler über Sicht (Sichtfeld, Licht, Wetter) und Geräusche (Gehen, Rennen, Springen, Schleichen) wahr. Stufen: misstrauisch (bleibt stehen, schaut hin) → sucht (geht hin, schaut sich um) → Angriff. Ein Auge über dem Fadenkreuz zeigt seine Aufmerksamkeit.
- Schwerere Schritte: Boden bebt in 6 Blöcken um ihn, Staub, Schrittklang nach Untergrund, weit hörbar aber leiser mit Entfernung.
- Sein Brüllen wird mit der Entfernung leiser.
- Frost-Überfälle: Jede Welle flieht, wenn nur noch 40 % übrig sind (auch einzelne Riesen); die Welle zählt dann als geschlagen.
- Ich-Perspektive: Die Waffe in der Hand folgt den Fähigkeits-Animationen.

**Schöpfungskristalle (neu)**
- Kleiner Kristall (1×1×1, Radius 64) und großer Kristall (3×3×3, Radius 192) verwandeln das Biom um sich herum. Aufgeladen mit einem gravierten Eisriesen-Kern, der das Biom trägt.
- Eigene Textur, Sounds für Erwachen, Einschlafen und Summen, Zaubertisch-Glyphen.

**Frostziege (neu)**
- Ziegen im Frozen Realm werden zu reitbaren Frostziegen (zähmen wie ein Pferd, Sattel, Truhe), mit Sprint-Stoß und Hufeisen für mehr Sprunghöhe.

**Frozen Realm**
- Neues Biom Eishöhlen im oberen Untergrund: seltener, tiefer, mit Schnee, leichtem Nebel, Schneeflocken und Eiszapfen an Decke und Boden; normale und üppige Höhlen gibt es weiterhin.
- Meer: Tiefe richtet sich nach dem Abstand zur Küste, schmalerer Schelf, keine Vanilla-Eisberge und keine Steinkappen mehr auf Eisbergen.
- Verschneite Pflanzen, verschneites Laub (alle Laubarten), Schneefuß an Pflanzen, verschneite Ranken und Kürbisse, Eiszapfen.

**Schleime (Vanilla-Schleime und Magmawürfel)**
- Kriechen über den Boden (mit eigenem Glibber-Sound) und springen nur noch beim Jagen und ab und zu.
- Fressen langsam Items am Boden (Fleisch, Roh-Erz, Pflanzen, Feuer-Items), hungrige Schleime grasen Pflanzen ab (nur mit `mobGriefing`).
- Nahrung gibt Eigenschaften: Fleisch mehr Angriff, Erz mehr Rüstung, Pflanzen mehr Leben; das Gelee färbt sich danach.
- Satt wachsen sie (1 → 2 → 4), bei Größe 4 teilen sie sich (begrenzt, wenn schon viele in der Nähe sind). 3 Feuer-Items machen einen Schleim zum Magmawürfel.
- Hungrige Schleime jagen Monster und Dorfbewohner. Was ein Schleim tötet, liegt 1 Minute auf dem Rücken und wird ganz gefressen.
- Ist das Gefressene größer als der Schleim, zieht er sich darüber und wird dabei flacher.

**Sonstiges**
- Neue Waffennamen (Gletscherspalter, Siegelklinge, Winterzahn, Jarlsbeil), Tastentipps in Tooltips, Waffen-Drops, Rüstungsrezepte.

### English

**Ice Giant**
- Sneaking past: the giant notices players by sight (field of view, light, weather) and sound (walking, sprinting, jumping, sneaking). Stages: suspicious (stops and looks) → searching (walks over, looks around) → attack. An eye above the crosshair shows his awareness.
- Heavier footsteps: the ground shakes within 6 blocks, dust, step sound by ground type, heard far away but quieter with distance.
- His roar fades with distance.
- Frost raids: every wave flees when down to 40 % (lone giants too); the wave then counts as beaten.
- First person: the held weapon follows the ability animations.

**Creation crystals (new)**
- Small crystal (1×1×1, radius 64) and large crystal (3×3×3, radius 192) turn the biome around them. Charged with an engraved Ice Giant core that carries the biome.
- Own texture, sounds for awakening, falling asleep and humming, enchanting glyphs.

**Frost Goat (new)**
- Goats in the Frozen Realm become rideable Frost Goats (tamed like a horse, saddle, chest), with a dash and horseshoes for a higher jump.

**Frozen Realm**
- New Ice Caves biome in the upper underground: rarer, deeper, with snow, light haze, snowflakes and icicles on ceiling and floor; normal and lush caves still generate.
- Sea: depth follows the distance to the coast, narrower shelf, no vanilla icebergs and no stone caps on icebergs.
- Snowy plants, snowy leaves (all kinds), snow at the foot of plants, snowy vines and pumpkins, icicles.

**Slimes (vanilla slimes and magma cubes)**
- Crawl along the ground (with their own squelch sound) and only hop when hunting and now and then.
- Slowly eat items lying on the ground (meat, raw ore, plants, fire items); hungry slimes graze plants (only with `mobGriefing`).
- Food gives traits: meat more attack, ore more armour, plants more health; the jelly takes on the colour.
- When full they grow (1 → 2 → 4) and split at size 4 (limited when many are nearby). 3 fire items turn a slime into a magma cube.
- Hungry slimes hunt monsters and villagers. What a slime kills lies on its back for a minute and is eaten whole.
- If the meal is bigger than the slime, it stretches over it and gets flatter.

**Other**
- New weapon names (Glacier Cleaver, Sealblade, Winterfang, Jarl's Axe), key hints in tooltips, weapon drops, armour recipes.

## 0.2.72 (2026-10-22)

Erste Veröffentlichung des Quellcodes. / First public source release.
