# Specification: Fast-Paced 3D Action Combat Prototype

## 1. Vision & Influences
An experimental prototype exploring a high-speed, continuous 3D first-person action combat paradigm for Tarmin:
- **Project Brutality & DOOM 2016**: Fast player speed, continuous arena mobility, high enemy aggression, visceral dismemberment on lethal blows without camera-locking cutscenes, and resource pickups (health/mana shards) bursting from slaughtered enemies.
- **Elderborn & Dark Messiah**: High-impact melee combat with directional trace hitboxes, visceral hitstop micro-pauses (30–50ms freeze frame), active parry windows, and an off-hand kick (`F`) that launches enemies into wall spikes, pits, and environmental hazards.
- **HEXEN**: Blending cold steel and elemental magic, quick-casting spells (`Q`/`E`) alongside swords, battleaxes, and point-blank musket blasts.

---

## 2. Complete Reversibility & Isolation Architecture
To ensure zero risk to the core game and effortless reversibility:
1. **Branch Isolation**: All prototype development resides exclusively on branch `feat/action-combat-prototype`. Deleting or abandoning the branch restores the exact prior state with zero trace.
2. **Package Isolation**: All classes live in a dedicated, self-contained package `com.bpm.minotaur.action`:
   - `ActionPrototypeScreen.java`: Standalone LibGDX screen managing camera, arena, HUD, and input.
   - `ActionPlayerController.java`: Continuous 3D WASD, mouse look, swept capsule collision, dash, jump, slide.
   - `ActionCombatEngine.java`: Melee hit traces, hitstop, camera shake, kick physics, quick-cast spells, weapon hotbar.
   - `ActionArenaBuilder.java`: Builds dedicated 32x32 arena with cover pillars, hazard spikes, weapon racks, and monster spawner.
   - `ActionMonsterEntity.java`: Real-time mobbing AI with continuous steering, telegraphed attacks, and billboard decals.
   - `ActionViewmodelRenderer.java`: First-person weapon viewmodel with camera bobbing, slash arcs, and spell glows.
3. **Single Integration Touchpoint**:
   - Accessible via **Main Menu -> "Action Combat Prototype [Dev]"** and an F6 dev shortcut.
   - If removed, deleting the single menu entry leaves the rest of the codebase untouched.

---

## 3. Locomotion & Physics Toolkit
- **Movement**:
  - `W, A, S, D`: Continuous omnidirectional movement at ~9.0 units/sec base run speed.
  - `Mouse`: Mouselook with configurable pitch/yaw clamp and smooth interpolation.
  - `Shift` (or double-tap strafe): **Lateral Dash** (~22 units/sec burst over 0.22s) with brief invulnerability frames (IFrames).
  - `Space`: **Jump & Vault** (vertical impulse of 7.5 units/sec, standard gravity curve).
  - `Ctrl` (while sprinting): **Crouch-Slide** (lowered eye height, high-momentum slide with directional steering).
- **Collision Physics**:
  - Swept cylinder/capsule ($r = 0.38\text{m}, h = 1.8\text{m}$) against arena geometry and props.
  - Continuous sliding plane resolution with corner chamfering to prevent snagging on 90° doorway corners during high-speed circle-strafing.

---

## 4. Visceral Melee & Kinetic Feedback Loop
- **Primary Attack** (`Left Click`):
  - Directional ray-fan trace across the reticle forward cone.
  - **Hitstop Micro-Pause**: 40ms freeze frame on heavy impacts.
  - **Camera Trauma**: Directional screen-kick scaled to damage.
- **Block & Parry** (`Right Click`):
  - 250ms parry window. Perfectly timing a parry against an enemy strike deflects the attack, staggers the attacker, and guarantees a critical counter-strike.
- **Off-Hand Kick** (`F`):
  - Rapid forward boot strike with heavy impulse.
  - Launches stunned or light enemies flying backward into wall spikes, pits, or other enemies for instant splatters.
- **Dynamic Severing & Gore**:
  - Lethal blows trigger real-time directional dismemberment (heads rolling, limbs severing) using the existing composited monster paperdoll and wound overlays.
  - Enemies burst into health and mana essence shards upon death that vacuum into the player when close.

---

## 5. Magic, Firearms & Hotbar Switching
- **Quick-Cast Magic** (`Q` = Flame Bolt, `E` = Frost Ray):
  - Left hand instantly unleashes magical projectiles or beams without putting away the main weapon.
- **Weapon Hotbar** (`1`–`6`):
  - `1`: Broadsword / Dagger (fast slash combos)
  - `2`: Battleaxe / Warhammer (heavy armor-crushing strikes with huge hitstop)
  - `3`: Crossbow (high-velocity armor-piercing projectile)
  - `4`: Flintlock Pistol / Musket (hitscan meat-shot with smoke puff and massive knockback)
  - `5`: Spellbook / Staff (sustained high-damage casting)

---

## 6. Arena Environment & Mobbing Waves
- **The Crucible Arena**:
  - 32x32 stone arena with 4 elevated corner platforms, central blood fountain, and peripheral spike walls.
  - Wave spawner button: Spawns waves of Skeletons, Ghouls, Hobgoblins, Minotaurs, and Skeletal Wizards.
  - Full debug panel: God mode toggle, enemy clear, weapon spawn, slow-mo toggle (`T`).
