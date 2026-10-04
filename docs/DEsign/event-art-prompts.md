# Choice Event Art Prompts

One prompt per choice event (`assets/data/events.json`) and one per biome background. Each prompt
is self-contained: paste it whole into the image generator. The shared style block is repeated
in every prompt on purpose, because image generators keep no memory between requests.

The house style comes from the two existing event images,
`assets/images/events/broken_mirror.png` and `assets/images/events/statue_cry.png`.

## Delivery

- **Event images**: 1408 x 752 PNG (1.87:1), saved to the path given under each prompt.
- **Backgrounds**: 1920 x 1080 PNG, saved to `assets/images/events/backgrounds/<biome>.png`.
  They sit behind the event window, dimmed, so they should be low-detail and dark at the centre.
- Crop off any generator watermark. Both existing images carry a sparkle mark in the bottom-right
  corner that should be cropped out as well.
- Until an image exists the game shows a per-biome placeholder, so art can land in any order.

## The Archivist

When the protagonist appears: an old man with a long white beard and a weathered, stern face, in a
hooded olive-green cloak over a plain brown tunic, a leather belt, brown trousers and boots. Most
scenes are better without him; the player is looking through his eyes.

---

## Event images

### 1. The Broken Mirror

Path: `images/events/broken_mirror.png` (already exists)

### 2. The Weeping Statue

Path: `images/events/statue_cry.png` (already exists)

### 3. The Corpse in Oakhaven Robes

Path: `images/events/oakhaven_corpse.png`

```
16-bit pixel art scene, detailed SNES-era fantasy style, wide 1.87:1 composition, side-lit, rich dithered shading, no text, no UI, no watermark. Setting: a narrow corridor of a hedge maze with tall moss-green walls dripping with slime, a stormy purple sky with streaks of red sunset overhead, and far on the horizon a dark stepped stone tower in front of grey mountains. Subject: a desiccated corpse slumped against the left wall, wearing tattered grey scholar's robes with a faded tree crest on the breast. Its gaunt face has a long white beard and looks unsettlingly like an old hooded traveller. One bony hand clutches a folded parchment map whose ink lines seem to squirm, rendered as faint glowing dark-blue strokes. A small leather satchel lies at its side. Mood: eerie, quiet, sorrowful. Muted greens and browns with the map as the one cold accent.
```

### 4. The Chitinous Mound

Path: `images/events/chitinous_mound.png`

```
16-bit pixel art scene, detailed SNES-era fantasy style, wide 1.87:1 composition, side-lit, rich dithered shading, no text, no UI, no watermark. Setting: a humid forest trail hemmed in by dense dark-green trees and ferns, shafts of hazy light through the canopy, a stormy purple sky glimpsed between the leaves, and a dark stepped stone tower faint on the far horizon. Subject: a huge mound of packed earth and amber resin blocking the trail, riddled with tunnels, crawling with giant red-brown ants with clicking mandibles. Near the summit, half swallowed by resin, the hilt and crossguard of a fine sword catch the light. Mood: oppressive heat, menace, temptation. Earth browns, amber highlights, insect reds.
```

### 5. The Trader of Roots

Path: `images/events/trader_of_roots.png`

```
16-bit pixel art scene, detailed SNES-era fantasy style, wide 1.87:1 composition, side-lit, rich dithered shading, no text, no UI, no watermark. Setting: a forest clearing ringed by gnarled trees with huge exposed roots, soft green light, a stormy purple sky through the canopy. Subject: a tall humanoid figure made of bark and moss sits cross-legged among the roots, with hollow eyes glowing faintly green and long twig fingers. Spread on a flat root in front of it are its wares: a ring on a leaf, a small bundle of herbs, a stoppered vial of red liquid, and a carved wooden token. Mood: uncanny, patient, a bargain with a cost. Deep greens and browns with small glowing accents.
```

### 6. The Thorn-Snared Scorpion

Path: `images/events/snared_scorpion.png`

```
16-bit pixel art scene, detailed SNES-era fantasy style, wide 1.87:1 composition, side-lit, rich dithered shading, no text, no UI, no watermark. Setting: a forest path where the walls are woven from living black-green thorn vines, a stormy purple sky above with a red sunset band. Subject: a giant dark-brown scorpion thrashing in the middle of the path, its legs and tail wrapped tight in thorny vines that are visibly constricting, small beads of dark ichor on the thorns. Beyond it the path is choked with more vines. Mood: tense, violent, a puzzle in the way. Dark greens, glossy brown chitin, red accents.
```

### 7. The Mirage Vendor

Path: `images/events/mirage_vendor.png`

```
16-bit pixel art scene, detailed SNES-era fantasy style, wide 1.87:1 composition, side-lit, rich dithered shading, no text, no UI, no watermark. Setting: open desert dunes under a blazing orange-purple dusk sky, heat shimmer rising from the sand, a dark stepped stone tower far on the horizon. Subject: a smiling merchant in layered desert robes sits under a red-and-cream striped awning that casts no shadow. Before him on a rug lie impossibly fine wares: a jewelled sword, gold rings, a crown. The edges of the merchant and his stall waver and smear like a heat mirage, and the rug's fringe looks faintly like teeth. Mood: too good to be true, a trap. Warm sands, rich reds and golds.
```

### 8. The Smoky Bottle

Path: `images/events/smoky_bottle.png`

```
16-bit pixel art scene, detailed SNES-era fantasy style, wide 1.87:1 composition, side-lit, rich dithered shading, no text, no UI, no watermark. Setting: rolling desert dunes at twilight, a purple sky fading to red at the horizon, a few dry rocks in the foreground. Subject: a stoppered bottle of dark smoked glass half buried in the sand in the foreground, ornate brass cap, and inside the glass coils of grey-violet smoke that seem to form a face pressing against it. A faint warm glow comes from the bottle. Mood: mysterious, tempting, dangerous. Sandy ochres, violet smoke, brass highlights.
```

### 9. The Lightning Spire

Path: `images/events/lightning_spire.png`

```
16-bit pixel art scene, detailed SNES-era fantasy style, wide 1.87:1 composition, side-lit, rich dithered shading, no text, no UI, no watermark. Setting: a desert under a dark, roiling storm sky with no rain. Subject: a tall jagged spire of black iron jutting from the dunes, wreathed in continuous crackling blue-white lightning. The sand around its base has fused into a ring of green-black glass with cracks. Scattered charred scraps of parchment lie around it. Mood: raw power, danger, awe. Black iron, electric blue-white, glassy greens.
```

### 10. The Reflection of Elara

Path: `images/events/reflection_of_elara.png`

```
16-bit pixel art scene, detailed SNES-era fantasy style, wide 1.87:1 composition, side-lit, rich dithered shading, no text, no UI, no watermark. Setting: a still, dark pool among reeds and wet stones in misty lakelands at dusk, a purple sky with a red band over distant water. Subject: in the mirror-still surface of the pool, the reflection of a young woman with long dark hair in a simple pale dress, weeping, one hand reaching up toward the surface. No one stands on the bank above her: only the reflection exists. Faint ripples spread from her fingertips. Mood: grief, longing, something wrong. Cold blues and teals with a soft pale glow on the woman.
```

### 11. The Coral Altar

Path: `images/events/coral_altar.png`

```
16-bit pixel art scene, detailed SNES-era fantasy style, wide 1.87:1 composition, side-lit, rich dithered shading, no text, no UI, no watermark. Setting: shallow lakeland water lapping over smooth stones, reeds and mist, a purple dusk sky. Subject: an altar of pale pink-white branching coral rising from the shallows, completely dry despite the water around it. On its top lie crusted old offerings: coins, a corroded ring, small bones, a guttered candle. Something long and dark moves just under the water nearby, barely visible. Mood: sacred, ancient, watched. Pale coral, cool blue water, dull gold accents.
```

### 12. The Drowned Library

Path: `images/events/drowned_library.png`

```
16-bit pixel art scene, detailed SNES-era fantasy style, wide 1.87:1 composition, side-lit, rich dithered shading, no text, no UI, no watermark. Setting: the interior of a flooded stone chamber, knee-deep black water reflecting faint light from a crack in the ceiling. Subject: tall leaning wooden shelves of swollen, water-stained books standing in the water, a few books floating. The spines bear a small embossed tree crest. Loose pages drift on the surface. Mood: loss, melancholy, the remains of a home. Dark blues, sodden browns, faint warm light on the pages.
```

### 13. The Drowning Pool

Path: `images/events/drowning_pool.png`

```
16-bit pixel art scene, detailed SNES-era fantasy style, wide 1.87:1 composition, side-lit, rich dithered shading, no text, no UI, no watermark. Setting: a lakeland path that drops away into a flooded hollow, murky green-blue water, reeds and roots at the edges, a purple sky with red sunset. Subject: seen through the murky water, on a stone ledge far down at the bottom, a sealed glass jar holding a leather-bound book, faintly glowing. The water surface is still and dark. Mood: temptation, danger, cold. Murky teals, deep blues, a small warm glow from the jar.
```

### 14. The Blood Circle

Path: `images/events/blood_circle.png`

```
16-bit pixel art scene, detailed SNES-era fantasy style, wide 1.87:1 composition, side-lit, rich dithered shading, no text, no UI, no watermark. Setting: a deep underground maze corridor of cut grey stone, torchlight from one side, darkness at the far end. Subject: a circle of angular runes drawn on the flagstone floor in dark dried blood, faintly pulsing red. In its centre lies a neat, deliberate pile of bones topped by a skull. The air above the circle shimmers with a faint red haze. Mood: forbidden, ritual, power for a price. Cold greys, deep reds, warm torch orange.
```

---

## Biome backgrounds

These are dimmed behind the event window. Keep them atmospheric and low in detail, with no focal
subject in the centre.

### Maze

Path: `images/events/backgrounds/maze.png`

```
16-bit pixel art background, detailed SNES-era fantasy style, 16:9, no text, no UI, no watermark, no characters. A long receding corridor of a hedge maze with tall moss-green walls dripping with slime, under a stormy purple sky streaked with red sunset, a dark stepped stone tower faint on the far horizon in front of grey mountains. Low contrast, darker toward the centre, atmospheric haze, suitable as a dimmed backdrop behind a dialog window.
```

### Forest

Path: `images/events/backgrounds/forest.png`

```
16-bit pixel art background, detailed SNES-era fantasy style, 16:9, no text, no UI, no watermark, no characters. A dense old forest with huge dark trunks, ferns and hanging moss, hazy green light through the canopy, a stormy purple sky in small gaps above. Low contrast, darker toward the centre, atmospheric haze, suitable as a dimmed backdrop behind a dialog window.
```

### Desert

Path: `images/events/backgrounds/desert.png`

```
16-bit pixel art background, detailed SNES-era fantasy style, 16:9, no text, no UI, no watermark, no characters. Rolling desert dunes at dusk under a purple sky fading to deep red at the horizon, heat shimmer, a dark stepped stone tower tiny on the far horizon. Low contrast, darker toward the centre, atmospheric haze, suitable as a dimmed backdrop behind a dialog window.
```

### Lakelands

Path: `images/events/backgrounds/lakelands.png`

```
16-bit pixel art background, detailed SNES-era fantasy style, 16:9, no text, no UI, no watermark, no characters. Misty wetlands with still dark water, reeds, mossy stones and drowned trees, a purple dusk sky with a thin red band over distant water. Low contrast, darker toward the centre, atmospheric haze, suitable as a dimmed backdrop behind a dialog window.
```
