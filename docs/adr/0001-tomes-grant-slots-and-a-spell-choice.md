# Tomes grant spell slots and a spell choice; the Altar shapes the choice

The Progression Reboot plan said Tarmin Tomes raise War/Spiritual power thresholds, while the code had Tomes *and* the Shelter Altar's Arcane Attunement both unlocking the same five spell slots (eight unlocks for five slots). We decided a Tome unlocks its spell slot (Initiate 2, Elements 3, Arcane 4, Tarmin 5) and offers a **Tome Choice** — pick one unknown spell from a curated, level-banded pool — while Arcane Attunement stops granting slots and instead improves the Tome Choice (more options, a reroll, a higher Tarmin band) on top of unsealing spell circles for scroll loot. Slots stay a delve reward found in the strata; the Altar is where the player shapes which spells those rewards offer.

## Consequences

- Known spells, unlocked slots and prepared slots are persistent progression: they survive death and are saved. Spell ids (5e `spells.json` ids) are the single source of truth; the legacy `SpellType` known-spells list is retired.
- A repeat Tome (slot already unlocked) still offers a Tome Choice, so milestone depths stay worth revisiting; the Tome of Tarmin spawns only at depth 8 to keep top-band picks scarce.
- Studying a Tome in the field is a channelled action that monsters can interrupt; in the Shelter it is instant.
