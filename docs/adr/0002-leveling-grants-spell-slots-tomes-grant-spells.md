# Leveling grants spell slots; Tomes grant permanent spells; the Altar shapes Tome choices

**Supersedes**: [ADR 0001: Tomes grant spell slots and a spell choice](file:///c:/workspace/Tarmin2/docs/adr/0001-tomes-grant-slots-and-a-spell-choice.md)

ADR 0001 tied spell slots 2–5 exclusively to milestone Tomes found at depths 2, 4, 6, and 8. In practice, depth-gating and scarcity made spell slots inaccessible during normal leveling and exploration.

We decided that **character leveling** unlocks spell slots: characters start with 1 slot and gain +1 slot at levels 2, 5, 8, and 11 (capped at 5). **Tomes** cease unlocking slots and instead grant a **permanent, curated spell** that survives death (`permanentSpellIds`). The Shelter Altar's Arcane Attunement tree stops hard-sealing spells and instead governs Tome Choice richness (options 3→4→5, rerolls, and Level 9 reach for the Tome of Tarmin).

## Consequences

- Spell slots are unlocked predictably through character level progression (`Player.slotsForLevel(level)`: 1, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5...).
- Loaded saves maintain backwards compatibility via `Math.max(slotsForLevel(level), savedSlots)`, ensuring existing characters never lose previously earned slots.
- Tomes offer a curated pool of permanent spells via Tome Choice. If the curated pool has fewer spells than offered card slots, general unlearned spells of that tier fill the remaining slots (mixed hand). If the entire tier is known, a permanent +5 Max MP bonus is granted.
- `Tome.TARMIN.maxSpellLevel` is 8 by default (making all 4 curated Tarmin spells reachable). Arcane Attunement Tier 3 unlocks Level 9 spells.
- Shelter Altar retired its 9-spell hard gate (`isSpellSealed`), making all spells eligible for loot spawning naturally.
- In-combat spell preparation allows filling *empty* slots at the cost of one combat action turn, while locking already prepared slots against swapping or clearing during battle.
