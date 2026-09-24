# Requirements: Reviving the Desert and Lakelands Biomes

**Status**: Not started. Deferred from the shelter biome portal work.
**Date**: September 2026
**Related Documents**:
- `docs/DEsign/Themed Chunk Contract.md`
- `docs/DEsign/Design Document_ Procedural Maze Chunk Architecture & Generation.md`
- `docs/asset-licenses.md`

---

## 1. Why this document exists

`Biome` declares seven values. The world can only ever produce five of them, and
only three are passable.

| Biome | Reachable? | Generator | Notes |
| :--- | :--- | :--- | :--- |
| MAZE | Yes | `MazeChunkGenerator` | The 21x21 dungeon core |
| FOREST | Yes | `ForestChunkGenerator` | Radius 11-15 ring |
| PLAINS | Yes | `ForestChunkGenerator` | **Borrows the forest generator** |
| OCEAN | Yes | none | Impassable, a world edge |
| MOUNTAINS | Yes | none | Impassable, a world edge |
| **DESERT** | **No** | `DesertChunkGenerator` (420 lines) | **Never registered, never selected: dead code** |
| **LAKELANDS** | **No** | none | Enum value only |

`BiomeManager.getBiome` has the desert and lakelands selection commented out at
the bottom of the file, with a note that it needs a second noise layer
(humidity). `WorldManager`'s constructor registers generators for MAZE, FOREST
and PLAINS only.

So the game ships a complete desert generator that nothing can reach, and two
biome portals cannot be offered for destinations that do not exist.

> [!IMPORTANT]
> The narrow fix -- uncomment the noise branches and register the desert
> generator -- is explicitly **not** what this document asks for. That would
> produce two biomes that generate forest-shaped or desert-shaped chunks with a
> different fog colour and nothing else, which is the "reskinned generic"
> failure the themed chunk contract already forbids. A biome the player cannot
> tell apart from another biome is not a biome.

---

## 2. Scope

**In scope**
1. Making DESERT and LAKELANDS reachable, with a humidity noise layer.
2. Giving each a generator that produces chunks distinguishable from forest at a glance and in play.
3. Prop, hazard and encounter content for both, drawn from the licensed packs.
4. Two further shelter portals (`PORTAL_DESERT`, `PORTAL_LAKELANDS`), reusing the frame built for Forest and Plains.
5. Giving PLAINS its own generator, or deliberately retiring PLAINS.

**Out of scope**
- OCEAN and MOUNTAINS. They are impassable world edges and should stay that way.
- Any change to the themed chunk system beyond adding biome-appropriate themes.

---

## 3. Biome selection

`BiomeManager` currently picks from one noise layer (elevation). Desert and
lakelands are humidity extremes, so they need a second.

```
elevation = noise(x, y)
humidity  = humidityNoise(x, y)     // second FastNoiseLite instance, different seed

if  elevation < OCEAN_THRESHOLD      -> OCEAN
if  elevation > MOUNTAIN_THRESHOLD   -> MOUNTAINS
if  humidity  < DESERT_THRESHOLD     -> DESERT
if  humidity  > LAKELANDS_THRESHOLD  -> LAKELANDS
otherwise                            -> PLAINS
```

**Requirements**
- Both new biomes must appear within 40 chunks of the origin for every world seed, or the portals that target them are dead buttons. `BiomePortalTest.portalDestinationsAreReachableBiomes` already enforces this shape and must be extended to cover them.
- Neither may generate inside the maze radius.
- Biome regions must be contiguous enough to walk across -- single-chunk islands of desert surrounded by plains read as noise artefacts, not terrain.

---

## 4. What a DESERT chunk contains

`DesertChunkGenerator` exists and already produces dunes, sandstone outcrops and
cactus scenery. It needs auditing against the current `IChunkGenerator`
contract, not rewriting from scratch. Specifically it predates the forest
overhaul and the prop catalogue, so it will not have:

- The connectivity guarantees `ForestChunkGenerator` gained (perimeter ring, gate-approach rescue corridor, trails wider than one tile).
- Prop catalogue integration (`Scenery.fromProp`) instead of hardcoded `SceneryType`.
- Gate approach protection.

**Content requirements**

| Aspect | Requirement |
| :--- | :--- |
| Layout | Open dunes with sightlines far longer than the forest's. The desert should feel exposed, where the forest feels enclosed. |
| Hazard | Heat. The player already has a `TEMP` stat and a `H2O` stat that nothing outdoors currently pressures. Daytime desert should drain water measurably faster; night should be survivable. This is the mechanic that makes the biome more than a palette. |
| Props | Synty `POLYGON_NatureBiomes` and Goblin War Camp: `SM_Env_Rock_*`, bleached `SM_Env_Bones_*`, `SM_Prop_Skull_*`. Kenney Nature Kit `.obj` cacti and dead trees. Bake through `tools/blender/bake_theme_props.py`. |
| Encounters | `GIANT_SCORPION`, `GIANT_SNAKE`, `BASILISK`, `MUMMY`, `TROGLODYTE` -- all already in `MonsterType` and none of them the forest's spiders and owlbears. |
| Landmarks | Buried ruins and bone fields, using the themed chunk prop system. |

---

## 5. What a LAKELANDS chunk contains

No generator exists. This is the larger half of the work.

| Aspect | Requirement |
| :--- | :--- |
| Layout | Water-dominant: islands and causeways rather than trails through trees. Traversal should be about finding the crossing, not finding the gap. |
| Hazard | Reuse the liquid system built for Flooded Caverns. Wading already costs a second turn tick, muck poisons, standing water rusts metal. Lakelands is where that system earns its keep outdoors. |
| Props | `SM_Env_Reeds_*`, `SM_Env_Algae_*`, `SM_Env_Lillypads_*`, `SM_Env_Swamp_Mound_*`, Alpine `SM_Env_Water_Plane_01`, `Gradient_Fog`. |
| Encounters | `ALLIGATOR`, `GIANT_SNAKE`, `GELATINOUS_CUBE`, `PURPLE_WORM`, `HARPY`. |
| Landmarks | Sunken structures reachable only by wading, rewarding the turn cost. |

> [!IMPORTANT]
> Lakelands must not simply be Flooded Caverns outdoors. The themed chunk is a
> sealed set piece with an objective; the biome is open terrain you cross. If
> they end up feeling the same, cut Lakelands and fold its ideas into the
> themed chunk instead.

---

## 5b. Satisfying the seven-slot themed chunk contract

A biome is open terrain, not a themed chunk, so it does not fill the contract
itself. But the themed chunks that generate *inside* it must, and today all six
themes are dungeon-flavoured. Each new biome therefore needs at least one theme
that belongs in it, and that theme must fill all seven slots of
`docs/DEsign/Themed Chunk Contract.md`.

### DESERT — proposed theme: "Buried Necropolis"

| Slot | Content |
| :--- | :--- |
| (a) Layout | Sunken tomb cut into a dune bowl; the entrance is the only break in the rim |
| (b) Props | `bone_pile`, `skull_pile`, `gravestone`, `cairn`, plus sandstone rubble |
| (c) Hazard | Heat plus collapsing sand tiles that cost a turn to wade out of |
| (d) Encounters | `MUMMY`, `BASILISK`, `GIANT_SCORPION`, `CLOAKED_SKELETON` |
| (e) Objective | Breach the sealed tomb door (key carried by the largest guardian) |
| (f) Reward | 2 Crests |
| (g) Signature | Bleached ochre fog, low sight range, dry wind stinger |

### LAKELANDS — proposed theme: "Drowned Causeway"

| Slot | Content |
| :--- | :--- |
| (a) Layout | A single sunken causeway between islands, partially submerged |
| (b) Props | `swamp_reeds`, `algae_mat`, `stalagmite`, `twisted_root` |
| (c) Hazard | Existing liquid system: wading costs a turn, muck poisons, water rusts |
| (d) Encounters | `ALLIGATOR`, `GIANT_SNAKE`, `GELATINOUS_CUBE`, `HARPY` |
| (e) Objective | Reach the shrine at the causeway's end before the tide-equivalent closes it |
| (f) Reward | 1 Crest |
| (g) Signature | Blue-green fog, short sight range, deep water stinger |

Both must also be added to `ChunkTheme` with a `minLevel`, and
`ThemeContractTest` will fail the build if any slot is left empty. Note that
`WorldManager.getChunkTheme` currently restricts themed chunks to the MAZE
biome; that restriction must be widened to a per-theme biome whitelist before
either of these can generate, or they will never appear.

---

## 6. PLAINS

PLAINS currently runs `ForestChunkGenerator`, so a plains chunk *is* a forest
chunk. Once humidity selection exists, PLAINS becomes the middle band between
desert and lakelands and needs a decision:

- **(a)** Give it a generator: open grassland, sparse cover, long sightlines, the traversal opposite of forest.
- **(b)** Retire it -- let the humidity midband be FOREST, and drop PLAINS from `Biome`.

(b) is cheaper and honest. Note the shelter already ships a `PORTAL_PLAINS`; if
PLAINS is retired that portal must be retargeted or removed, and
`BiomePortalTest` will fail loudly if it is not, which is the intended safety.

---

## 7. Portals

Once a biome is reachable and generates distinguishable chunks, adding its
portal is a data row plus a niche:

1. Add the item type to `Item.ItemType` and an entry to `assets/data/items.json`.
2. Add the `ShelterAltar.Station` value with its Crest cost and `requiredDepth`.
3. Add the `BiomePortal` enum row with its destination biome and tint.

The shelter gallery already reserves five niches for this reason, so no layout
change is needed until a sixth destination appears. Suggested staggering,
continuing the existing curve (Forest depth 3 / 25 Crests, Plains depth 5 / 35):

| Portal | Revealed at | Crest cost | Tint |
| :--- | :--- | :--- | :--- |
| Desert | deepest level 7 | 45 | sand `(0.95, 0.75, 0.40)` |
| Lakelands | deepest level 9 | 55 | cyan `(0.30, 0.80, 0.95)` |

---

## 8. Definition of done

- [ ] Humidity noise layer added; DESERT and LAKELANDS selectable and contiguous.
- [ ] Both guaranteed to exist within 40 chunks of origin for any seed, with a test.
- [ ] `DesertChunkGenerator` registered, audited against the current generator contract, and brought up to the forest's connectivity guarantees.
- [ ] `LakelandsChunkGenerator` written, with the same connectivity guarantees.
- [ ] Both biomes carry a hazard that changes how the player moves or fights, not just fog colour.
- [ ] Props baked and catalogued; encounter tables distinct from forest.
- [ ] PLAINS either has its own generator or is retired, and `PORTAL_PLAINS` follows that decision.
- [ ] Two further portals shipped.
- [ ] A connectivity test per new generator, matching `ForestConnectivityTest`, including its articulation-point budget.

---

## 9. Estimate and sequencing

Roughly three pieces, each independently shippable:

1. **Selection** -- humidity layer, thresholds, tests. Small. Unblocks everything else, and can land alone because an unregistered biome simply never gets picked.
2. **Desert** -- audit, register, modernise, content. Medium; the generator exists.
3. **Lakelands** -- new generator plus content. Largest; depends on nothing but (1).

Do not ship (1) alone without gating. `WorldManager.loadChunk` falls back to
`Biome.FOREST` when a biome has no registered generator, so a selectable
DESERT would silently produce forest chunks wearing a desert label -- the exact
reskinned-generic failure this document exists to prevent, and one that would
be easy to miss precisely because it looks like a working biome.
