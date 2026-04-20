# Game Systems Reference

> Part of the Tarmin2 agent context system. See `AGENT.md` for the full file map.

---

## System Map — All Active Systems

| System | Entry point | Key dependencies | Data files |
|---|---|---|---|
| Combat | `CombatManager` | `Player`, `Monster`, `Maze`, `AnimationManager`, `SoundManager` | none (JSON for dice) |
| Monster AI | `MonsterAiManager` | `Maze.monsters`, `Player.position`, `Pathfinder` | none |
| Inventory | `Player.inventory`, `Maze.gameObjects` | `ItemDataManager` | `items.json`, `weapons.json`, `armor.json` |
| Equipment | `PlayerEquipment` | `Item`, `ItemModifier` | — |
| Spawning | `SpawnManager`, `MonsterSpawner`, `ItemSpawner` | `Maze`, `LevelBudget`, `SpawnTableData` | `spawntables.json` |
| Maze Generation | `MazeChunkGenerator` / `IChunkGenerator` | `Biome`, `NetHackRNG`, `MazeReachability` | none |
| Rendering | `FirstPersonRenderer` → `EntityRenderer` → overlays → `Hud` | `Maze`, `Player`, `depthBuffer` | texture atlases |
| Gore | `GoreManager` | `Gib` pool, `BloodParticle` pool, `WallDecal`, `SurfaceDecal` | gore textures |
| Status Effects | `StatusManager` (per-entity) | `ActiveStatusEffect`, `StatusEffectType` | none |
| Spells | `SpellEffect` implementations, `CombatManager` | `Player.knownSpells`, SS stat | none |
| Events/Encounters | `GameEventManager`, `EncounterManager` | `Maze.triggers`, `EncounterChoice` | `encounters.json` |
| Cooking | `CookingManager` | `Player.inventory`, `GibType` | none |
| Alchemy | `AlchemyManager` | `Player.inventory`, `PotionEffectType` | none |
| Survival Stats | `PlayerStats` via `GameScreen.update()` | `DoomManager`, `WeatherManager` | none |
| Weather | `WeatherManager`, `WeatherRenderer` | `DayNightManager`, `Biome` | skybox textures |
| Paperdoll | `PaperDollWidget` | `SkeletonData`, `VerletPhysics`, `PlayerEquipment` | `skeleton.json`, `segments.json` |
| Discovery | `DiscoveryManager` | `Item.ItemType`, `Beatitude` | none |

---

## 1. Survival Stat System

`PlayerStats` tracks six survival dimensions, all decaying or changing every game tick:

| Stat | Internal name | Range | Effect when depleted |
|---|---|---|---|
| Health | `warStrength` (WS) | 0–max | Death |
| Mana | `spiritualStrength` (SS) | 0–max | No spell casting |
| Hunger | `satiety` | 0–1000 | WS drain, movement penalty |
| Thirst | `hydration` | 0–1000 | SS drain, speed penalty |
| Temperature | `bodyTemp` | 32.0–41.0°C | Hypothermia / hyperthermia debuffs |
| Toxicity | `toxicity` | 0–100 | Glass cannon: +20% damage output, -20% defense |

**Toxicity** is unique — it is not purely negative. High toxicity enables a high-risk high-reward playstyle. Meals and certain potions can manipulate toxicity deliberately.

---

## 2. Monster AI System

`MonsterAiManager` runs every monster's AI each game tick (outside of combat):

**Behavior tiers:**
- **Idle** — random wander within patrol radius
- **Alerted** — pathfinds toward target (player or rival monster faction)
- **Fleeing** — (planned, not yet implemented) moves away from threats
- **Ambush** — (planned) waits at chokepoints

**Pathfinding:** `Pathfinder` implements A* on the maze grid. Falls back to greedy search when A* cost exceeds threshold. Monsters of opposing factions will path toward each other, enabling dynamic dungeon ecosystems independent of the player.

**Faction system:** `MonsterFamily` determines alliance. E.g., UNDEAD monsters will fight HUMANOID monsters on sight, regardless of player position.

**Grace flag:** `Monster.justSpawned` temporarily prevents newly spawned monsters from immediately aggressing to avoid spawn-camping scenarios. This is a known workaround — be careful modifying spawn/aggro logic.

---

## 3. Inventory & Equipment System

**Inventory (`Inventory.java`):**
- Fixed slot count
- `pickup(Item)` — adds item; returns false if full
- `removeItem(Item)` — removes by reference
- `getAllItems()` — returns `List<Item>`
- Items on the maze floor are stored in `Maze.gameObjects` at their `GridPoint2`

**Equipment slots (`PlayerEquipment.java`):**
```
RIGHT_HAND   ← weapons (melee, ranged)
LEFT_HAND    ← off-hand weapons, shields
HEAD         ← helmets
TORSO        ← chest armor
LEGS         ← leg armor
BOOTS        ← footwear
BACK         ← cloaks, packs
RING         ← ring (one slot)
```

**Item modifiers (`ItemModifier.java`):**
Each item can carry multiple `ItemModifier` entries. Fields: `ModifierType type`, `int value`, `String displayName`. Always use `ItemDataManager` to create items with correct modifiers — JSON defines base modifier lists.

---

## 4. Cooking & Alchemy

**Cooking (`CookingManager`):**
```
Recipe: 1 Gib (any GibType) + 1 Kindling + 1 Water → Meal
```
Meals provide temporary stat buffs and manipulate Toxicity. Different `GibType` combinations produce different meal effects. Accessible via `CookingScreen`.

**Alchemy (`AlchemyManager`):**
Combines ingredients to produce potions with `PotionEffectType` effects. High-risk potions (Feral Draught, Titan Sludge) drastically raise Toxicity for extreme combat bonuses.

---

## 5. Event & Encounter System

**Text events** (`GameEventManager`): Floating UI messages with duration. Triggered by: combat outcomes, stat thresholds, item use, room entry.

**Statue encounters** (`EncounterManager`): Grid-position-triggered encounters loaded from `encounters.json`. Present the player with `EncounterChoice` options that yield `EncounterResult` outcomes (heal, damage, spawn monster, grant item, alter dungeon state).

Statues render via `EntityRenderer` and pop into view based on distance vector — they must exist in `Maze.gameObjects` at a valid `GridPoint2`.

---

## 6. Spell System

Spells are learned by the player and stored in `Player.knownSpells` as `SpellType` enum values. Casting deducts SS (Spiritual Strength). Resolution is handled by the `SpellEffect` implementation matching the `SpellType`.

**Implemented spells:**

| SpellType | Effect class | Behavior |
|---|---|---|
| MAGIC_ARROW | `MagicArrowEffect` | Fires projectile, resolves via `CombatManager.HitResult` |
| HEAL | `HealEffect` | Restores WS |
| TELEPORT | `TeleportEffect` | Moves player to random valid grid tile |
| DRAIN | `DrainEffect` | Transfers WS from monster to player |
| FORCE_PUSH | `ForcePushEffect` | Knockback — moves monster in direction |
| IRON_SKIN | `IronSkinEffect` | Temporary AC buff via `StatusEffectType` |

---

## 7. Gore System

`GoreManager` owns all pooled gore objects. Called from `CombatManager` on kill.

**On monster death:**
1. `GoreManager.spawnGibs(monster, position)` — releases `Gib` instances from pool
2. `GoreManager.spawnBloodSpray(position, direction)` — emits `BloodParticle` pool objects
3. `GoreManager.addWallDecal(wallPos)` — stamps `WallDecal` on nearest wall
4. `CorpsePart` list updated — persistent debris

**Pooling:** All gore types implement LibGDX `Pool.Poolable`. Obtain via pool, never `new`. Pool size is pre-allocated — don't create new pools.

---

## 8. Status Effect System

Each `Player` and `Monster` has its own `StatusManager` instance.

**Common status effects:**
| Effect | Behavior |
|---|---|
| `HASTE` / `TEMP_SPEED` / `SUPER_SPEED` | Speed multiplier |
| `SLOW` / `SLOWED` | Speed divisor |
| `POISONED` | Periodic WS drain |
| `BLESSED` | Combat bonus |
| `CURSED` | Combat penalty |
| `INVISIBLE` | Monster detection range zero |
| `IRON_SKIN` | Temporary AC boost |

Effects track remaining duration in ticks. `StatusManager.tick()` decrements durations and removes expired effects.

---

## 9. Maze Generation

**Algorithm (`MazeChunkGenerator`):**
1. Select and randomly rotate 12×12 pre-designed room tiles from a template pool
2. Stitch rooms together using corridor templates ensuring connectivity
3. Validate with `MazeReachability` — if unreachable areas exist, regenerate
4. Populate via `SpawnManager` using `LevelBudget` and `SpawnTableData` (weighted random)

**Biome generators** all implement `IChunkGenerator`:
- `MazeChunkGenerator` — standard dungeon
- `ForestChunkGenerator` — open forest with natural obstacles
- `DesertChunkGenerator` — arid terrain

**Generation constants** in `WorldConstants`. RNG is `NetHackRNG` — deterministic, seeded per level.

---

## 10. Weather System

`WeatherManager` tracks current `WeatherType` and `WeatherIntensity`. `WeatherRenderer` applies visual effects each frame. Skybox texture swaps on weather change (e.g., `retroSkyboxNorthStorm`). `DayNightManager` drives the time cycle that influences weather transitions.

Weather currently affects visuals and could affect survival stats (temperature) in future iterations.
