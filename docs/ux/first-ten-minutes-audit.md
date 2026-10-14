# What the game teaches in the first 10 minutes

Research for issue #156 (child of map #149). The question: from new game through the
first delve, what is a new player told, prompted or shown, and what is never taught?

- **Source:** this repo's code and data on `develop` at `dae38d64`. Every claim cites
  `file:line`. Java paths are relative to `core/src/main/java/com/bpm/minotaur/`.
- **Scope:** facts only. This file makes no fix recommendations.
- **Method:** the new-game path traced from the main menu through to the first fight.
  The game was not run. A string that exists in code is reported as "shown" only when the
  code path that displays it is reachable in a normal first session.

---

## 1. The path from "New Expedition" to the first step

| Step | What the player sees | Source |
|---|---|---|
| Boot | Intro video, then rotating flavour lines ("Building the maze...", "Planning your demise..."). No gameplay tips. | `screens/LoadingScreen.java:30-37` |
| Main menu | Buttons CONTINUE / LOAD / NEW EXPEDITION / SETTINGS / QUIT. Footer legend: `Up/Down Navigate`, `ENTER Choose`, `F2 Retro view`, `ESC Quit`. | `screens/MainMenuScreen.java:245-266, 306-310` |
| Slot select | Legend `1-3 Choose slot`, `ENTER Confirm`, `ESC Back`. | `screens/SaveSlotSelectScreen.java:127-129` |
| Mode select | Shown only once Classic is unlocked: "Classic Mode has been unlocked. Choose your expedition style." Otherwise the game goes straight to Modern. | `screens/SaveSlotSelectScreen.java:265-270, 300-303` |
| Character | **No choice is shown.** The name, class and difficulty are hard-coded: `"Hero"`, `"Warrior"`, `Difficulty.EASY`. | `screens/SaveSlotSelectScreen.java:383-389` |
| Spawn | The player spawns in the starting shelter room inside chunk (0,0), facing north. No welcome or arrival message fires. `show()` and `generateLevel()` only pick shelter music and award "+N Divinity (new area)". | `managers/WorldManager.java:1075-1088`, `gamedata/player/Player.java:1087-1088`, `screens/GameScreen.java:411-418, 548-555` |

**Starting kit** (`gamedata/player/Player.java:1103-1135`):

- A Rusty Sword (1d3) in the right hand. Its category is `WAR_WEAPON`
  (`gamedata/item/Item.java:1411-1416`; `assets/data/items.json:4112-4144`).
- A Brass Lantern clipped to the **belt**, not held in a hand
  (`Player.java:3802-3808`). It starts lit (`lighting/LightingManager.java:124`).
- 30 rations, a waterskin and 2 Tinder Bundles (`Player.java:3789-3791`).
- No armour and no throwable item. All quick slots are empty.
- One known spell, Mote of Light, prepared in slot 1 (`Player.java:1054, 1061-1065`).
  It is a self-targeted light (`assets/data/spells.json`, `MOTE_OF_LIGHT`).
- Only 1 spell slot is unlocked (`Player.java:297`).
- EASY gives 8 arrows (`gamedata/Difficulty.java:5`), but the kit has no bow.

---

## 2. What the player is told, prompted or shown

### 2.1 Always-on HUD

None of these elements explains itself. Each is just a label or badge.

- **Quick-slot badges `1`-`6`** on the backpack strip (`rendering/Hud.java:523`).
- **Hand-slot badges `L` and `R`** (`Hud.java:577, 605`).
- **`SPELLS [Q]`** header over the 5-slot spell hotbar, with each slot badged
  `[Z] [X] [V] [B] [N]` (`Hud.java:778, 799-804`; keys from
  `screens/SpellbookScreen.java:52`).
- **`DIV: n`** and **`DOOM: n [n%]`** (`Hud.java:415-416, 1088`). Neither has a tooltip
  (`Hud.java` registers none on these labels).
- **`DUNGEON LVL n [BIOME]`** (`Hud.java:1261`).

### 2.2 Hover tooltips (mouse only)

- **Quick slot:** "Hotkey N | Right-click: Use / Throw / Drop". For a weapon it reads
  "Throw / [N] | Right-click: Use / Throw / Drop" (`Hud.java:549-556`).
  - Right-click opens a USE / THROW / DROP menu (`Hud.java:170-205, 528-531`).
  - The tooltip only fires when the slot holds an item, and every slot starts empty.
- **Spell slot:** name, school, MP cost, description and hotkey "Z or Shift+1" ... "N or
  Shift+5" (`Hud.java:821-840`). It shows only for a slot that holds a prepared spell, so
  at start only slot 1 (Mote of Light) shows it.
- **Item tooltip:** shows the raw category enum name (`rendering/HudTooltip.java:63`),
  for example `WAR_WEAPON`.

### 2.3 Interaction cards (the facing-tile prompt)

`Hud.java` shows a card for whatever the player faces. Each card has a key badge and an
action line.

**In the starting shelter:**

| Station | Key | Action line | Source |
|---|---|---|---|
| Storage Chest | `[ O ]` | Open Storage Chest | `Hud.java:2280-2290` |
| Bedroll | `[ O ]` | Rest & Save Game | `Hud.java:2306-2316` |
| Workbench | `[ O ]` | Open Crafting Bench | `Hud.java:2318-2328` |
| Cooking pot | `[ O ]` | Cook Meals | `Hud.java:2330-2340` |
| Altar | `[ O ]` | Open Shelter Altar | `Hud.java:2342-2352` |
| Lectern | `[ O ]` | Examine Archive Lectern | `Hud.java:2354-2364` |
| War Table | `[ O ]` | Study the War Table | `Hud.java:2366-2376` |
| Cartographer's Desk | `[ O ]` | Examine Cartographer's Desk | `Hud.java:2378-2388` |
| Training dummy | `[ O ]` | Open Skill Tree | `Hud.java:2402-2412` |
| Barred window | `[ O / E ]` | Look Outside | `Hud.java:2418-2428` |

- **Mounted Brass Lantern:** `[ P ] Take Lantern` and `[ O ] Toggle Flame`. The body text
  says "Equip in off-hand to pierce dungeon darkness" (`Hud.java:2390-2400`). The starter
  lantern is on the belt, not in a hand (`Player.java:3802-3808`).

**In the field:**

- **Ground item:** `[ P / E ]` "Pick Up [P]". It adds "| Quick Equip [E]" for gear and
  "| Consume [E/U]" for consumables. The category tag reads e.g. `[WAR WEAPON]` or
  `[SPIRITUAL WEAPON]` (`Hud.java:2591-2617`).
- **Corpse:** `[ E ]` "Butcher / Harvest [E]", or "Examine Remains [E]" plus "Requires a
  bladed tool to butcher" (`Hud.java:2262-2275`).
- **Doors, gates, containers:** `[ O ]` (`Hud.java:2432-2470, 2490-2500`).
- **Ladders, stairs, sinkholes:** `[ D ]` with "Descend Ladder [D]" and similar
  (`Hud.java:2530-2580`).
- **Cold hearth:** `[ O ]` "Light the Hearth". Its body text explains the Tinder Bundle
  (`Hud.java:2294-2304`).

### 2.4 Log and event messages a new player is likely to hit

| Trigger | Message | Source |
|---|---|---|
| Walk into a wall | "Thud! You strike a solid wall." | `screens/GameScreen.java:4064` |
| Enter a new chunk | "+N Divinity (new area)" | `GameScreen.java:554` |
| Pick up an item | "Acquired: <name> [CATEGORY] ..." toast | `Hud.java:2702` |
| Press `L` | "Lantern flame kindled./snuffed out." | `GameScreen.java:4268-4280` |
| Press `L` with no lantern | "You must equip a Brass Lantern to use it." | `GameScreen.java:4268-4280` |
| Press `F` with nothing to fire | "You have nothing in hand to fire or throw." | `GameScreen.java:4012` |
| Press `.` | "You wait..." | `GameScreen.java:4019` |
| Press `R` with no wounds | "Nothing here needs tending. Press H to rest and recover strength." | `GameScreen.java:4230-4231` |
| Bleeding | Toast "BLEEDING INFLICTED - Tend wounds immediately! [R]" | `Hud.java:2744` |
| Bleeding (body silhouette tooltip) | "[R] or Click to Tend" | `rendering/AnatomicalSilhouetteWidget.java:62` |
| Melee with a ranged weapon | "Cannot melee with a ranged weapon. Press [F] to shoot!" | `managers/CombatManager.java:628` |
| Cast from a locked spell slot | "Spell slot N is locked! Study a Tome to unlock it." | `gamedata/player/Player.java:520` |
| Cast from an empty spell slot | "Spell slot N is empty! Assign a spell in the Spellbook [Q]" | `Player.java:525` |
| Use an empty quick slot | "Quick slot N is empty." | `Player.java:1250` |
| Level up | "2 Attribute Points & 1 Skill Point gained! Press [K] to view Skill Tree." | `Player.java:3529, 3564` |
| Enter a themed (sealed) chunk | A headline plus a goal line. Rune of Surrender lines follow if the player channels it. | `GameScreen.java:222-229`; `generation/theme/ThemeObjectiveManager.java:248-350` |
| Low HP, hunger, thirst, noticed, unspent points | A red screen flash and the `alert` sound only. **No text.** | `managers/AlertMonitor.java:13-15`; `GameScreen.java:1576-1579` |
| Death | Death screen with "TARMIN'S HUNGER", DEMISE n/max, BRIDGE %, then "AWAKEN IN THE SHELTER [ENTER / SPACE]" | `screens/PlayerDeathScreen.java:129-316` |

### 2.5 The controls legend (TAB) and the Controls screen

- **TAB** toggles a full legend overlay in game (`GameScreen.java:4290-4295`;
  `rendering/ControlsLegendOverlay.java`). When the pickup toast holds several items,
  TAB cycles the toast instead.
  - Nothing on the HUD says TAB opens it. The overlay's only TAB hint is "[TAB] Close",
    inside the overlay itself (`ControlsLegendOverlay.java:49`).
- The legend lists exactly the 25 actions in `SettingsManager.keyBindingDescriptions`
  (`managers/SettingsManager.java:35-59`). Defaults are at `SettingsManager.java:225-255`.
- The same list is the Controls screen, reached via Main Menu > Settings > Controls
  (`screens/SettingsScreen.java:221`).
- F1 shows a debug overlay with a KEYBINDS column (`Hud.java:1693-1740`). This is
  debug-only.

---

## 3. The specific keys asked about

| Key | What it actually does | Taught? |
|---|---|---|
| **E** | Hard-coded multipurpose. It loots a facing corpse or one at your feet, looks out of a window, quick-equips or consumes a ground item, and otherwise **swaps the hand with the pack** (`GameScreen.java:3807-3835`). It is **not** bound to INTERACT. The default INTERACT key is **O** (`SettingsManager.java:231`), and the legend lists E as "Swap with Pack" (`SettingsManager.java:46, 238`). | Partly. The `[E]` badges on corpse, window and ground-item cards teach its context uses. The legend calls it "Swap with Pack". Nothing ties the two together. |
| **O** | Open/Interact (`GameScreen.java:4118-4120`). | Yes. It is the `[ O ]` badge on every shelter station, door and container card, and it is in the legend. |
| **L** | Toggles the lantern (`GameScreen.java:4268-4280`). The lantern starts lit on the belt. | Only in the TAB legend ("Toggle Lantern"). No card or message mentions `L`. The shelter's mounted-lantern card teaches `[ O ] Toggle Flame` for the wall lantern, and its text says "equip in off-hand", which does not match the starter lantern's belt slot. |
| **Z X V B N** | Cast spell slots 1-5, in and out of combat (`GameScreen.java:3709-3723, 3855-3884, 4131-4155`). | Shown as `[Z]`...`[N]` badges on the hotbar (`Hud.java:804`) and in the spell tooltip. **Not in the TAB legend.** Only slot 1 is unlocked at start. |
| **Shift+1..5** | Also cast spell slots 1-5 (`GameScreen.java:3724-3765, 3886-3940, 4156-4205`). | Only in the spell-slot hover tooltip ("Z or Shift+1"), which needs a mouse hover on a filled slot. **Not in the legend.** |
| **1-6** | Use a quick slot. A **weapon** in a quick slot is **thrown** (`Player.java:1254-1276`). In menu combat it routes through `playerUseItem` (`GameScreen.java:3724-3769`). | Badges `1`-`6` on the slots, plus the quick-slot hover tooltip "Throw / [N]" for weapons (`Hud.java:552-555`). **Not in the legend.** All slots start empty, so the tooltip never fires until the player fills one. |
| **Right-click a quick slot** | USE / THROW / DROP menu (`Hud.java:170-205`). Out of combat only (`GameScreen.java:2879-2882`). | Only in the quick-slot hover tooltip. |
| **F / A** | Fire a held ranged weapon (`GameScreen.java:4006-4015`). | "Fire Ranged Weapon" is in the legend. The "Press [F] to shoot!" message only appears if you try to melee with a ranged weapon. |
| **SPACE** | Out of combat, opens the combat menu: ATTACK / CAST / ROLL / USE / BLOCK / COOK (`GameScreen.java:3959-3962`; `Hud.java:3477-3482`). | **Nowhere.** It is not in the legend, and no card or message mentions it. |
| **I** | Inventory (`GameScreen.java:4212-4216, 4311`). | **Not in the legend** and not on the HUD. It is hard-coded, so it is absent from `keyBindingDescriptions`. |
| **G** | Shield guard or shield bash, with a shield in the left hand (`GameScreen.java:3986-4000`). | **Not in the legend.** |
| **.** (period) | Wait a turn (`GameScreen.java:4018-4020`). | **Not in the legend.** |
| **Up into a monster** | Bump-to-attack (`GameScreen.java:4022-4035`; `CombatManager.java:612`). | **Never stated.** "Attack (Combat)" = A appears in the legend. |

**Rebinding.** The legend shows the *bound* key for each action. Only MAP, SPELLBOOK,
SKILL_TREE, TOGGLE_LANTERN, FIRE_RANGED and the map keys read the binding at runtime
(`GameScreen.java:4006, 4250-4268`; `screens/map/ExpeditionMapScreen.java:396-404`).

- O, P, U, D, R, H, C, J, K, S, E, T, A and the arrows are matched as literal keycodes
  (`GameScreen.java:3807-3840, 4022-4124, 4212-4245`).
- So after a rebind of those actions, the legend names a key the game does not read.

---

## 4. War vs Spiritual (Bad / Nasty / Horrible)

### 4.1 What the code does

- Categories are fixed per monster type (`gamedata/monster/Monster.java:15-66`):
  - **BAD** resists War (physical) and is weak to Spiritual.
  - **NASTY** resists Spiritual and is weak to War.
  - **HORRIBLE** is neutral.
- **There is no immunity.** `isImmuneToType` always returns `false`, with the comment
  "Soft resistance replaces hard immunity" (`Monster.java:622-625`). Instead:
  - **Resistant:** damage is halved (min 1).
  - **Weak:** damage is ×1.5.
  - A crit ignores resistance (`Monster.java:630-647`).
- So **CONTEXT.md:16-17 ("immune to physical" / "immune to spiritual") does not match the
  code.**
- The "X is immune to War/Spiritual attacks!" message (`CombatManager.java:2229-2236`) is
  unreachable, because its guard is always false.
- The same holds for "is immune to spiritual magic!" in
  `gamedata/spells/SpellExecutionEngine.java:679-683` and the matching checks in
  `WandExecutionEngine.java:335, 356`.

### 4.2 What the player sees

**On a weapon hit, the log names the affinity**
(`CombatManager.java:2368-2414`):

- "GOBLIN resists War attacks!" with floating text "RESISTED!" (cyan), or
- "SKELETON is weak to War attacks!" with "WEAKNESS!" (gold).

Three limits apply:

- These lines only fire on a **plain** hit. A crit, glance, dual strike, finisher or combo
  step takes an earlier branch, which prints no affinity line
  (`CombatManager.java:2385-2404`).
- The monster is named by its enum (`monster.getType()`), for example `GOBLIN`.
- Spell and wand damage applies the same multiplier through `takeSpellDamage`
  (`Monster.java:680-683`), but prints no resist or weak line. A grep of both execution
  engines finds no such message.

**The HUD category badge almost never shows.** It reads "[BAD: Holy/Spiritual]",
"[NASTY: Weak to War]" or "[HORRIBLE: Balanced]" next to the monster's name
(`Hud.java:1270-1290`).

- It renders only when `CombatManager` state is not `INACTIVE`.
- Bump melee and ranged fire use the stateless path, which "stays INACTIVE, so no menu
  opens" (`CombatManager.java:662-667`, `engageInstant`).
- So in normal bump-to-attack play the badge is never on screen. It appears only after
  SPACE opens the combat menu (`GameScreen.java:3959`) or via `startCombat`
  (`CombatManager.java:517, 583`), which wands, projectile hits and mimics trigger.
- The badge wording is also uneven. "Holy/Spiritual" names what hurts BAD, "Weak to War"
  names what hurts NASTY, and "Balanced" says neither.

**The weapon category is visible**, as `[WAR WEAPON]` or `[SPIRITUAL WEAPON]`:

- on ground-item cards (`Hud.java:2594`),
- in the item tooltip, as the raw enum name `WAR_WEAPON` (`HudTooltip.java:63`),
- in the inventory inspector badge (`screens/inventory/ItemInspectorPanel.java:91-95`).

Nothing links that tag to which monsters it works on.

### 4.3 The first-delve exposure

- The starting weapon is War (Rusty Sword). Mote of Light does no damage. **The player has
  no Spiritual damage source at start.**
- Spiritual weapons are the cross, wand, the three combat books and any `*CROSS*`,
  `*HOLY*` or `*SPIRIT*` weapon (`Item.java:1402-1414`).
- The level-1 rows of `assets/data/spawntables.json` (`monsterSpawnTable`) carry these
  spawn weights:

| Category | Monsters | Weight | Share |
|---|---|---|---|
| BAD | Kobold 12, Goblin 10, Dwarf 8 | 30 | 46% |
| HORRIBLE | Giant Ant 10, Orc 10, Giant Scorpion 8 | 28 | 43% |
| NASTY | Skeleton 7 | 7 | 11% |

- So roughly half the first monsters by weight halve the starting sword's damage.
- (This is the shared spawn table. Biome or themed-chunk spawns can override it. That was
  not traced.)

### 4.4 Never stated anywhere

- No codex, lectern, tooltip, loading line or message explains:
  - that monsters have a War/Spiritual category,
  - which monsters are which,
  - what the categories mean (half / ×1.5 damage).
- A search of `screens/CodexScreen.java`, `screens/TalkScreen.java` and the inventory
  screens for "nasty", "horrible", "spiritual" or "weak to" finds no player-facing text.
- In `assets/data/`, the only hits are the spell `domain` fields in `spells.json`.

---

## 5. What is never taught

These have no prompt, card, message or legend entry on the first-10-minutes path.

1. **SPACE opens the combat menu** (ATTACK / CAST / ROLL / USE / BLOCK / COOK).
2. **I opens the inventory.** It is in neither the legend nor the HUD.
3. **TAB opens the controls legend.** Nothing on screen points to it.
4. **Bump-to-attack**: walking into a monster attacks it.
5. **Shift+1..5 casts**: only in a hover tooltip, never in the legend.
6. **Z/X/V/B/N casts**: badges only, never in the legend.
7. **Number keys 1-6 throw a weapon** from a quick slot: tooltip only, and there is no
   throwable at start.
8. **How to put an item in a quick slot.** No card, message or tooltip on the HUD covers
   assigning one.
9. **G for shield guard or bash. `.` to wait.** Neither is in the legend.
10. **E's dual role.** The legend says "Swap with Pack". The cards say loot, equip, consume
    and look.
11. **The lantern starts lit, and L toggles it.** Only the legend says this. The shelter
    lantern card says "equip in off-hand".
12. **War vs Spiritual categories and their damage effect.** The badge is effectively
    hidden in bump combat (§4.2). The affinity log line is the only reliable cue, and it
    appears only after the player has already hit the monster.
13. **What DOOM and DIV mean.** These are HUD numbers with no tooltip. DOOM is first
    explained on the death screen.
14. **Why the alert flash fired** (low HP, hunger, thirst, noticed, unspent points). It is
    a flash and a sound with no text (`AlertMonitor.java:13-15`).
15. **Spell slots 2-5 are locked.** Only the error on use says "Study a Tome to unlock it".
16. **Difficulty and character choice.** The player gets neither, so there is nothing to
    explain (`SaveSlotSelectScreen.java:385-389`).
17. **The Delve & Return loop.** No message says to leave the shelter, delve, return to
    rest or save, or what Castle Tarmin is. Shelter cards describe each station in
    isolation.

## 6. Related discrepancies noted in passing (not acted on)

- CONTEXT.md:16-17 says Bad and Nasty monsters are *immune*. The code uses soft resistance
  (`Monster.java:622-647`). The "immune" log lines are dead code
  (`CombatManager.java:2229-2236`; `SpellExecutionEngine.java:679-683, 749, 780`;
  `WandExecutionEngine.java:335, 356`).
- The mounted shelter lantern card says "Equip in off-hand" (`Hud.java:2395`). The starter
  lantern goes on the belt (`Player.java:3802-3808`).
- The legend reflects rebinds that the hard-coded keys ignore (§3).
