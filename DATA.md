# Data Files Reference

> Part of the Tarmin2 agent context system. See `AGENT.md` for the full file map.

---

## Overview

All game data is defined in JSON files under `assets/data/`. LibGDX's `com.badlogic.gdx.utils.Json` handles deserialization. The deserializer is **field-name-sensitive** — field names in JSON must match Java class fields exactly.

**Loading manager:** `ItemDataManager` (items, weapons, armor), `MonsterDataManager` (monsters), `EncounterManager` (encounters), `SpawnManager` (spawn tables).

---

## File Summary

| File | Size | Manager | Purpose |
|---|---|---|---|
| `weapons.json` | ~550KB | `ItemDataManager` | 200+ weapon templates |
| `armor.json` | ~250KB | `ItemDataManager` | 80+ armor templates |
| `items.json` | ~130KB | `ItemDataManager` | Consumables, treasure, tools |
| `monsters.json` | ~69KB | `MonsterDataManager` | Monster archetypes |
| `spawntables.json` | ~54KB | `SpawnManager` | Weighted loot/spawn tables |
| `encounters.json` | ~3KB | `EncounterManager` | Statue/event encounters |
| `paperdoll_calibration.json` | ~40KB | `CalibrationStore` | Where each paperdoll layer sits on the body |
| `paperdoll_layers.json` | ~30KB | `PaperdollLayerMap` | Item key → paperdoll layer |
| `skeleton.json` | ~1.8KB | `PaperDollWidget` | Paperdoll attachment points (legacy widget) |
| `segments.json` | ~1.4KB | Rendering | Sprite segment definitions |

> **Note:** `weapons.json` at 550KB is loaded entirely at startup. Parse errors produce opaque LibGDX exceptions. Validate JSON structure carefully before editing.

> **Exception to the LibGDX `Json` rule:** the two `paperdoll_*.json` files are parsed
> with `JsonReader`/`JsonValue` and written by hand, not through `com.badlogic.gdx.utils.Json`.
> `Json` serialises floats as `{"class":"java.lang.Float","value":797}` — see `skeleton.json`
> for what that produces. These two files are meant to be a hand-edit escape hatch and to
> diff readably, which those wrappers defeat. `tools/bake_paperdoll_layers.py` writes the
> same format from Python; `CalibrationFormatTest` pins the two writers together.

---

## `weapons.json` — Weapon Template Schema

```json
{
  "weapons": [
    {
      "itemType": "SWORD_LONGSWORD",       // Matches ItemType enum exactly
      "name": "Longsword",
      "category": "WEAPON",                // ItemCategory enum
      "variants": [
        {
          "color": "GRAY",                 // ItemColor enum
          "baseDamage": 6,
          "range": 1,
          "weight": 30,
          "value": 50,
          "spriteId": "sword_longsword",
          "modifiers": [
            {
              "type": "BONUS_DAMAGE",      // ModifierType enum
              "value": 0,
              "displayName": ""
            }
          ]
        }
      ],
      "twoHanded": false,
      "weaponClass": "MELEE",              // MELEE, RANGED, SPIRITUAL
      "attackSpeed": 1.0
    }
  ]
}
```

---

## `armor.json` — Armor Template Schema

```json
{
  "armor": [
    {
      "itemType": "ARMOR_LEATHER_CHEST",
      "name": "Leather Chestguard",
      "category": "ARMOR",
      "slot": "TORSO",                     // Equipment slot
      "variants": [
        {
          "color": "GRAY",
          "baseAC": 2,
          "weight": 20,
          "value": 30,
          "spriteId": "armor_leather_chest",
          "modifiers": []
        }
      ]
    }
  ]
}
```

> `armor.json` is being actively restructured on the `feature/card-combat` branch. The stat system may have new fields not yet documented here — read the file directly before editing.

---

## `monsters.json` — Monster Template Schema

```json
{
  "monsters": [
    {
      "monsterType": "SKELETON",           // MonsterType enum
      "name": "Skeleton",
      "family": "UNDEAD",                  // MonsterFamily enum
      "baseWS": 10,
      "baseSS": 0,
      "baseMoveSpeed": 10,
      "variants": [
        {
          "color": "TAN",                  // MonsterColor enum
          "wsMultiplier": 1.0,
          "damageMultiplier": 1.0,
          "xpReward": 25,
          "lootTableId": "skeleton_tan"
        }
      ],
      "attacks": [
        {
          "name": "Claw",
          "damageType": "PHYSICAL",        // DamageType enum
          "baseDamage": 4,
          "range": 1,
          "attackSpeed": 1.0
        }
      ],
      "spriteData": {
        "spriteId": "monster_skeleton",
        "frameCount": 4,
        "animationSpeed": 0.15
      },
      "gibTypes": ["GIB_BONE", "GIB_FLESH"] // GibType enum values
    }
  ]
}
```

---

## `items.json` — Item Template Schema

```json
{
  "items": [
    {
      "itemType": "POTION_HEALING",
      "name": "Healing Potion",
      "category": "CONSUMABLE",
      "potionEffect": "HEALING",           // PotionEffectType enum
      "variants": [
        {
          "color": "GRAY",
          "effectValue": 20,
          "weight": 5,
          "value": 100,
          "spriteId": "potion_red"
        }
      ],
      "stackable": true,
      "maxStack": 5,
      "beatitude": "UNCURSED"             // Beatitude enum default
    }
  ]
}
```

---

## `spawntables.json` — Spawn Table Schema

```json
{
  "tables": [
    {
      "tableId": "dungeon_level_1",
      "entries": [
        {
          "itemType": "SKELETON",          // Monster or item type
          "entryType": "MONSTER",          // MONSTER or ITEM
          "weight": 100,
          "minCount": 1,
          "maxCount": 3,
          "color": "TAN"
        }
      ]
    }
  ]
}
```

`WeightedRandomList` selects entries proportionally. `LevelBudget` defines how many total spawns occur per level before the table is exhausted.

---

## `encounters.json` — Encounter Schema

```json
{
  "encounters": [
    {
      "id": "statue_fortune",
      "name": "Fortune Statue",
      "description": "A crumbling statue offers you a choice...",
      "imageId": "statue_fortune",
      "choices": [
        {
          "label": "Pray",
          "resultType": "HEAL",            // EncounterResult type
          "resultValue": 15,
          "riskChance": 0.1,
          "riskResultType": "DAMAGE",
          "riskResultValue": 10
        },
        {
          "label": "Ignore",
          "resultType": "NONE"
        }
      ]
    }
  ]
}
```

Encounters are placed in the maze by `SpawnManager` at valid `GridPoint2` positions, stored as triggers in `Maze.gameObjects`.

---

## Adding New Data

1. Add the new entry to the appropriate JSON file following the schema exactly
2. Add the new enum value to the relevant Java enum (`ItemType`, `MonsterType`, etc.)
3. If adding new fields to an existing schema, add the corresponding field to the Java template class with an appropriate default value (LibGDX Json will leave unmatched fields at their default)
4. If adding a new template class, register it with the `Json` instance in the loading Manager
5. Run the game and verify the asset loads without a `SerializationException`

---

## Asset File Locations

```
assets/
├── data/           ← JSON config files (above)
├── images/
│   ├── weapons/    ← Weapon sprites (PNG)
│   ├── armor/      ← Armor sprites (PNG)
│   ├── monsters/   ← Monster sprites (PNG, multi-frame)
│   ├── items/      ← Item sprites (PNG)
│   ├── gore/       ← Gib and blood textures (PNG)
│   ├── debris/     ← Environment props (PNG)
│   ├── events/     ← Encounter background images (PNG)
│   ├── portrait/   ← Character portraits (PNG)
│   ├── skybox/     ← Skybox panoramas (PNG)
│   └── story/      ← Narrative art (PNG)
├── sounds/         ← SFX and music (OGG/WAV)
├── fonts/          ← BitmapFont files (.fnt + PNG)
├── models/         ← 3D model files (.g3db)
├── packed/         ← Texture atlas files (.atlas + PNG) ← generated, do not edit manually
└── saves/          ← Save game data
```

Texture atlases in `packed/` are generated by the LibGDX TexturePacker tool. Individual sprites in `images/` are the source of truth — pack them to regenerate atlases.

---

## `paperdoll_calibration.json` — Paperdoll Layer Placement

Where every paperdoll layer sits on the character. Read by `CalibrationStore`, written by
`tools/bake_paperdoll_layers.py`.

Layers are baked **normalised** — cropped to their artwork, scaled into a per-slot box and
centred on the 1024×1536 master canvas — so placement cannot live in the pixels. It lives
here, which is what makes correcting a piece a one-time cost rather than a PNG re-touch
every time the art changes.

```json
{
  "slotDefaults": {
    "head": {"offsetX": 0, "offsetY": -498.25, "scaleX": 0.97059, "scaleY": 0.96963, "rotation": 0}
  },
  "layers": {
    "chest/breastplate": {"offsetX": -10, "offsetY": -28.5, "scaleX": 0.98113,
                          "scaleY": 0.98226, "rotation": 0, "sourceHash": "fbcedbd161f6ffa9"}
  }
}
```

| Field | Meaning |
|---|---|
| `offsetX` / `offsetY` | Nudge in master-canvas pixels. **`+y` is DOWN** (image-space, not LibGDX y-up). |
| `scaleX` / `scaleY` | Independent multipliers about the layer's centre. Non-uniform scale is the tool for "right height, too narrow". |
| `rotation` | Degrees counter-clockwise about the layer's centre. |
| `hidesHair` / `hidesBeard` | Suppress the base character's hair/beard under an enclosing helm. Carried in the schema; not yet consumed by the renderer. |
| `needsArtRedo` | No calibration makes this piece fit — the art needs regenerating at the correct perspective. Feeds `docs/paperdoll_triage.md`. |
| `sourceHash` | Digest of the layer PNG as written. The baker skips layers whose hash still matches, and re-normalises changed ones **while keeping their calibration**. |

Keys are `"<slot>/<layerName>"`. A layer with no entry falls back to its `slotDefaults`
entry, then to identity.

> **Do not delete this file.** Placement lives here, not in the PNGs. Deleting it and
> re-baking would re-measure already-normalised (centred) layers and seed identity for all
> of them. The baker detects that case and refuses to write, but the file is still the only
> copy of the placement — it is git-tracked for exactly this reason.

---

## `paperdoll_layers.json` — Item to Layer Mapping

```json
{"items": {"BANDED_MAIL": {"slot": "chest", "layer": "banded"}}}
```

Read by `PaperdollLayerMap`. Replaces the three-way filename guessing the widget used to
do (texture basename → enum name → slugified display name). That guessing is why the baker
had to emit a duplicate PNG under every name a lookup might try; several items now share
one layer file and one calibration instead of byte-identical copies that can drift apart.

Items with no visual layer — ammunition, projectiles — are simply absent, which is a normal
state. They are listed in `docs/paperdoll_triage.md`.
