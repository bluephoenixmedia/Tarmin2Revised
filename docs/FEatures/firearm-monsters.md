# Design note: gun-armed monsters

**Status:** not implemented, deliberately. Written alongside the player firearms work so the
reasoning survives.

Player firearms landed with `MUSKET`, `ARQUEBUS`, `BLUNDERBUS`, `PISTOL_STARWHEEL` and `TUFENK`
made into real ranged weapons. Arming *monsters* with them was explicitly left out. This note
records why, and what it would take.

---

## Why it was left out

A monster with a musket is not a monster with a bow and bigger dice. Everything that makes the
player's firearm interesting is state the AI does not have:

- **The reload.** `ReloadChannel` is a per-player object held by `CombatManager`. A gun-armed
  monster needs its own instance, ticked on its own turn, and an AI that understands "I cannot
  shoot for three turns" — which means deciding whether to close, retreat, or hold. That is a
  second implementation of the whole mechanic on the AI side, sharing nothing with the player's.
- **The noise.** `GunshotNoise.wake` currently wakes monsters. A monster firing would need it to
  wake *other* monsters but not itself, and to mean something to the player — who has no
  equivalent of being woken.
- **The misfire.** `PowderDampness` is carried on `PlayerStats`. Monsters have no wetness, and
  giving them one means either a parallel field or a world-level model.

Shipping a gun-armed monster without those is just a bow that hits harder, which is the
outcome the player-side design spent its whole budget avoiding.

## Who should get them

Intelligence is the natural gate. `MonsterAiManager.checkAwareness` already scales behaviour by
`monster.getIntelligence()`, so the roster divides cleanly:

- **Plausible:** `HOBGOBLIN`, `MIND_FLAYER`, `LICH`, `MEDUSA` — the ones already carrying
  `ARROW` projectiles and using tactics.
- **Implausible:** beasts and vermin. A `GELATINOUS_CUBE` with a blunderbuss is a joke, not a
  threat.

A firearm on a monster should read as *evidence of a mind*: the thing pointing a gun at you is
reasoning, and that is worth more as characterisation than as damage.

## What it would take

1. **A projectile archetype.** `MonsterProjectileRegistry` has ten entries, all ballistic
   travelling sprites. Firearms are hitscan on the player side, so this needs either a `BULLET`
   entry that travels very fast, or hitscan support in `performMonsterRangedAttack`. The
   registry's `(id, name, sprite, color, damageType, explosionType, soundKey, speed)` shape has
   no slot for "instant".
2. **Reload state on `Monster`.** Note `ChunkData.MonsterData` persists six fields and **not**
   monster inventory — so a monster's reload state, like its loot, would evaporate on a chunk
   unload. Either accept that a gun-armed monster reloads afresh when you re-enter, or extend
   the DTO.
3. **AI that respects it.** `MonsterAiManager` dispatches on `IDLE`/`WANDERING`/`HUNTING`. A
   reloading state is a fourth mode with its own movement preference, and the existing archer
   behaviour ("close in to fight rather than back away", per `aa371e64`) would need revisiting
   for a weapon whose whole point is that closing is bad.
4. **Telegraphing.** The player needs to see a monster's reload, or being shot at will feel
   arbitrary. `triggerAttackIndicator` already exists for the melee wind-up and is the obvious
   hook.

## Related

- `core/src/main/java/com/bpm/minotaur/gamedata/firearm/` — the player-side implementation.
- `FirearmProfile` holds reload turns and noise radius per weapon; a monster implementation
  should read from it rather than duplicating the numbers.
- `GhostPlayerMonster` is the one existing entity that derives ranged capability from carried
  equipment, and now understands firearms. It is the closest thing to a precedent.
