# Architecture Reference

> Part of the Tarmin2 agent context system. See `AGENT.md` for the full file map.

---

## Package Map

```
com.bpm.minotaur/
├── Tarmin2.java                    ← Main Game class; bootstraps all Managers, owns AssetManager
├── screens/                        ← All UI screens (extend BaseScreen)
├── gamedata/                       ← Pure data objects (Player, Maze, Monster, Item, etc.)
│   ├── player/                     ← Player, PlayerStats, PlayerEquipment, Inventory
│   ├── monster/                    ← Monster, MonsterTemplate, MonsterVariant, MonsterDataManager
│   ├── item/                       ← Item, ItemTemplate, ItemVariant, ItemDataManager, ItemModifier
│   ├── effects/                    ← ActiveStatusEffect, StatusEffectType, EffectApplicationData
│   ├── dice/                       ← Die, DieDefinition, DieFace, DieFaceType, DieResult
│   ├── encounters/                 ← Encounter, EncounterChoice, EncounterResult, EncounterManager
│   ├── gore/                       ← GoreManager, Gib, BloodParticle, SurfaceDecal, WallDecal
│   ├── spells/                     ← SpellType, SpellEffect (interface), 6 concrete effects
│   └── spawntables/                ← SpawnTableData, SpawnTableEntry, WeightedRandomList
├── managers/                       ← 21 singleton + context managers
├── rendering/                      ← All renderers, overlays, HUD, animation
├── generation/                     ← Procedural maze/biome generators, factories, spawners
├── paperdoll/                      ← PaperDollWidget, VerletPhysics, DollFragment, SkeletonData
├── weather/                        ← WeatherManager, WeatherRenderer, WeatherType, WeatherIntensity
└── utils/                          ← DiceRoller, ShatterUtils, AY38914, JavaCVVideoPlayer
```

---

## Design Patterns

| Pattern | Where used |
|---|---|
| Singleton | All 21 Manager classes |
| Factory | `ItemDataManager`, `MonsterFactory`, `DiceFactory` |
| Strategy | Biome generators (`IChunkGenerator` implementations) |
| State Machine | `CombatManager.CombatState`, `Monster.MonsterState` |
| Command / Event | `GameEventManager` |
| Object Pool | `Gib`, `BloodParticle`, `SurfaceDecal`, `WallDecal`, `SpellParticle` |
| Template Method | `BaseScreen` for all UI screens |
| Observer (loose) | `GameEventManager` dispatches events; HUD/Overlays react |

---

## Class Inventory — All 165+ Classes

### Core Controllers
| Class | File | Purpose |
|---|---|---|
| `Tarmin2` | `Tarmin2.java` | Main Game; bootstraps Managers, owns AssetManager, routes screens |
| `BaseScreen` | `screens/BaseScreen.java` | Abstract base for all screens |
| `GameScreen` | `screens/GameScreen.java` | Primary gameplay loop; owns renderers, combat, player |
| `LoadingScreen` | `screens/LoadingScreen.java` | Asset loading |
| `MainMenuScreen` | `screens/MainMenuScreen.java` | Title/start screen |

### Screens
| Class | Purpose |
|---|---|
| `InventoryScreen` | Full inventory management UI |
| `CookingScreen` | Meal preparation (Gib + Kindling + Water) |
| `AlchemyScreen` | Potion creation |
| `CastleMapScreen` | World/overworld map |
| `GibCodexScreen` | Bestiary |
| `OssuaryScreen` | Death tracker / run history |
| `PaperdollEditorScreen` | Character appearance editor |
| `SettingsScreen` | Settings persistence |
| `GameOverScreen` | Defeat screen |

### Player System
| Class | Purpose |
|---|---|
| `Player` | Player instance — position, stats, equipment, inventory, spells |
| `PlayerStats` | WS, SS, Satiety, Hydration, Temperature, Toxicity, XP |
| `PlayerEquipment` | Equipment slots: head, right/left hand, torso, legs, boots, back, ring |
| `Inventory` | Slot-based item container — `pickup()`, `removeItem()`, `getAllItems()` |

### Monster System
| Class | Purpose |
|---|---|
| `Monster` | Instance: stats, grid position, AI state, sprite, loot |
| `MonsterTemplate` | JSON-loaded archetype data |
| `MonsterVariant` | Color-coded scaling variant (TAN→ORANGE→RED→BLUE) |
| `MonsterDataManager` | Loads templates from `monsters.json`, creates instances |
| `MonsterFactory` | Constructs `Monster` from template + variant |
| `MonsterSpriteData` | Sprite rendering metadata |
| `MonsterFamily` | Enum: UNDEAD, HUMANOID, BEAST, DEMON, CONSTRUCT, etc. |
| `MonsterColor` | Enum: TAN, ORANGE, RED, BLUE — maps to dungeon depth |
| `AttackDefinition` | Single attack action definition (damage, type, range) |
| `LootModifierManager` | Calculates drop rolls on monster death |

### Item System
| Class | Purpose |
|---|---|
| `Item` | Instance: type, color, modifiers, beatitude, condition |
| `ItemTemplate` | JSON-loaded item archetype |
| `ItemVariant` | Color-coded scaling variant |
| `ItemDataManager` | Loads `armor.json`, `weapons.json`, `items.json`; creates instances |
| `ItemModifier` | Enchantment struct: `ModifierType`, value, `displayName` |
| `ItemType` | Enum: 450+ item types |
| `ItemColor` | Enum: GRAY, WHITE, BLUE, etc. |
| `ItemCategory` | Enum: WEAPON, ARMOR, RING, CONSUMABLE, TREASURE, USEFUL |
| `Beatitude` | Enum: CURSED, UNCURSED, BLESSED |
| `PotionEffectType` | Enum: 8+ potion effects |
| `RingEffectType` | Enum: ring powers |
| `ScrollEffectType` | Enum: scroll spells |
| `WandEffectType` | Enum: wand powers |
| `ModifierType` | Enum: BONUS_DAMAGE, BONUS_AC, GRANT_INTRINSIC, etc. |

### Maze & World
| Class | Purpose |
|---|---|
| `Maze` | Dungeon instance: `wallData`, `gameObjects`, `monsters`, grid dimensions |
| `Door` | Interactive door — open/close state |
| `Gate` | Color-keyed locked gate |
| `Ladder` | Level descent trigger |
| `Window` | Decorative environment element |
| `Scenery` | Decorative prop, implements `Renderable` |
| `ScenerySpriteData` | Sprite data for scenery |
| `Projectile` | Flying projectile: position, direction, damage |
| `Pathfinder` | A* grid pathfinding for monsters |
| `MazeReachability` | Validates all walkable tiles are reachable |

### Status Effects
| Class | Purpose |
|---|---|
| `ActiveStatusEffect` | Live effect instance: type, duration, stacks |
| `StatusEffectType` | Enum: 30+ types (HASTE, SLOW, POISONED, TEMP_SPEED, SUPER_SPEED, etc.) |
| `EffectApplicationData` | Metadata for effect application calls |
| `GameEvent` | Text event with duration and display type |

### Dice System (Card Combat)
| Class | Purpose |
|---|---|
| `Die` | Single die instance |
| `DieDefinition` | Template: faces, count |
| `DieFace` | Face: `DieFaceType` + value |
| `DieFaceType` | Enum: ATTACK, DEFEND, SPECIAL, BLANK, etc. |
| `DieResult` | Result of one die roll |
| `DiceFactory` | Creates `Die` from `DieDefinition` |
| `DiceRoller` | Roll helper — single die or pool |
| `BoneTrait` | Trait that modifies dice behavior |
| `Artifact` | Special dice upgrade item |

### Encounters & Events
| Class | Purpose |
|---|---|
| `Encounter` | Event encounter data |
| `EncounterChoice` | Choice option within encounter |
| `EncounterResult` | Outcome of a choice |
| `EncounterManager` | Loads `encounters.json`, resolves triggers |

### Gore System
| Class | Purpose |
|---|---|
| `GoreManager` | Manages all gore — gibs, particles, decals, corpses |
| `Gib` | Body part instance (pooled): `GibType`, position, velocity |
| `GibType` | Enum: GIB_FLESH, GIB_BONE, GIB_ORGAN, GIB_BILE, etc. |
| `BloodParticle` | Pooled blood particle |
| `SurfaceDecal` | Pooled blood splatter on floor |
| `WallDecal` | Persistent blood splatter on wall |
| `BloodSpray` | Spray effect emitter |
| `CorpsePart` | Persistent debris/corpse remnant |

### Managers (21 Singletons + Context Managers)
| Class | Access pattern | Purpose |
|---|---|---|
| `CombatManager` | Via `GameScreen` (constructor-injected) | Turn-based combat resolution |
| `TurnManager` | Via `CombatManager` | Turn order and timing |
| `SpawnManager` | `SpawnManager.getInstance()` | Entity spawning on level init |
| `MonsterAiManager` | Via `GameScreen` | Monster AI, pathfinding, faction behavior |
| `SoundManager` | `SoundManager.getInstance()` | SFX playback |
| `MusicManager` | `MusicManager.getInstance()` | Background music |
| `AnimationManager` | Via `GameScreen` or `AnimationManager.getInstance()` | Entity animation states |
| `GameEventManager` | Via `GameScreen` | Text event dispatch |
| `StatusManager` | Per-entity (Player, Monster each have one) | Status effect tracking |
| `CookingManager` | `CookingManager.getInstance()` | Meal preparation |
| `CraftingManager` | `CraftingManager.getInstance()` | General crafting |
| `AlchemyManager` | `AlchemyManager.getInstance()` | Potion brewing |
| `DiscoveryManager` | `DiscoveryManager.getInstance()` | Identified items tracking |
| `SettingsManager` | `SettingsManager.getInstance()` | Settings persistence |
| `DebugManager` | `DebugManager.getInstance()` | Debug overlay and logging |
| `BalanceLogger` | `BalanceLogger.getInstance()` | Balance/session data logging |
| `DualLogger` | `DualLogger.getInstance()` | Console + file logging |
| `SpawnLogger` | `SpawnLogger.getInstance()` | Spawn event logging |
| `DoomManager` | Via `GameScreen` | Persistent hunger/doom mechanic |
| `BiomeManager` | `BiomeManager.getInstance()` | Biome state |
| `WorldManager` | Via `GameScreen` | Overworld state |
| `DayNightManager` | `DayNightManager.getInstance()` | Time cycle |
| `WeatherManager` | `WeatherManager.getInstance()` | Weather state |
| `StochasticManager` | Via `GameScreen` | Random event scheduling |

### Rendering
| Class | Purpose |
|---|---|
| `FirstPersonRenderer` | DDA raycasting — walls, doors, floor, ceiling, skybox; produces `depthBuffer` |
| `EntityRenderer` | Sprites sorted by Z, occluded against `depthBuffer` |
| `FirstPersonWeaponOverlay` | Weapon animation and hit feedback (NEW) |
| `Hud` | In-game HUD: WS/SS bars, minimap, events, inventory quick-access |
| `DebugRenderer` | Top-down orthographic debug view (toggle: F1) |
| `CombatDiceOverlay` | Dice roll UI during combat (NEW) |
| `EncounterWindow` | Event encounter dialog UI |
| `ShopkeeperWindow` | Shop UI |
| `SpellCastOverlay` | Spell feedback display |
| `MonsterRenderer` | Standalone monster preview renderer |
| `AnimationManager` | Manages per-entity animation state machines |
| `RetroTheme` | Color palette definitions |
| `SpellParticle` | Pooled visual spell particle |

### Paperdoll
| Class | Purpose |
|---|---|
| `PaperDollWidget` | Character appearance UI component |
| `DollFragment` | Visual body part |
| `FragmentResolver` | Loads fragment assets |
| `SkeletonData` | Attachment point definitions |
| `VerletPhysics` | Cloth/armor simulation |
| `VerletLink` | Physics constraint link |

### Generation
| Class | Purpose |
|---|---|
| `MazeChunkGenerator` | Main dungeon generator (room templates, corridors, stitching) |
| `ForestChunkGenerator` | Forest biome generator |
| `DesertChunkGenerator` | Desert biome generator |
| `IChunkGenerator` | Generator interface (Strategy pattern) |
| `MonsterFactory` | Constructs Monster instances |
| `MonsterSpawner` | Places monsters in maze grid |
| `ItemSpawner` | Places items in maze grid |
| `MazeReachability` | Connectivity validation |
| `Biome` | Enum: DUNGEON, FOREST, DESERT, CASTLE |
| `WorldConstants` | Generation constants |
| `NetHackRNG` | Custom deterministic RNG |
| `FastNoiseLite` | Noise function for terrain |

### Spells
| Class | Purpose |
|---|---|
| `SpellType` | Enum: all learnable spells |
| `SpellEffect` | Interface for spell resolution |
| `HealEffect` | Heal WS spell |
| `MagicArrowEffect` | Projectile spell |
| `TeleportEffect` | Blink/teleport |
| `DrainEffect` | Life drain |
| `ForcePushEffect` | Knockback |
| `IronSkinEffect` | Temporary defense buff |

### Utilities
| Class | Purpose |
|---|---|
| `DiceRoller` | Static roll helpers |
| `ShatterUtils` | Particle/shatter emitter |
| `AY38914` | Retro sound chip emulator |
| `JavaCVVideoPlayer` | Video cutscene playback |
| `HeadlessAnimationManager` | No-op stub for headless testing |
| `HeadlessSoundManager` | No-op stub for headless testing |

---

## Manager Initialization Order (in `Tarmin2.java`)

Managers with dependencies must be initialized after their dependencies. The approximate order:
1. `AssetManager` (LibGDX)
2. `SettingsManager`, `DiscoveryManager`
3. `ItemDataManager`, `MonsterDataManager`
4. `SoundManager`, `MusicManager`
5. `SpawnManager`, `BiomeManager`, `WorldManager`
6. `CombatManager`, `MonsterAiManager`
7. `GameEventManager`, `EncounterManager`
8. `GoreManager`, `AnimationManager`
9. Screen-level: `Hud`, `CombatDiceOverlay`, `FirstPersonWeaponOverlay`
