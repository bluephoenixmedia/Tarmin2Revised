# Maze houses are dynamic factions layered on the Faction enum; history is saved as a replay

The Houses of the Maze plan (decision D26) needs every Maze house to act as its own faction, with
relations between houses driven by the generated history rather than seeded at random. `Faction`
is a fixed enum stored by name on every saved monster and read all over combat and AI code.

We decided to **keep the `Faction` enum** and add one value, `MAZE_HOUSE`, plus a **house id** on
`Monster`. A monster whose faction is `MAZE_HOUSE` belongs to the house its id names.
`TARMIN_LEGION` monsters belong to Tarmin-Zul's house implicitly, so the Legion joins the politics
without retagging any existing monster data.

`FactionMatrix` gains a `HouseRelations` source, which the history supplies:

- **House against house:** the history decides. Houses at war are hostile. Allied houses, and a
  house and its sworn vassal, are allied. Everything else is neutral, so houses that are not at
  war walk past each other.
- **House against a mortal faction:** hostile. The Maze is an invader. `NEUTRAL` stays neutral,
  and `CHAOS_BERSERK` stays hostile to everything.
- **Mortal against mortal:** unchanged, seeded as before.

The player is outside the matrix, as today, so every house stays hostile to the player.

We also decided the **history is saved as a replay**, not as a snapshot. The world save stores
the seasons elapsed, the ordered log of player deeds and the unlocked fragment ids. Loading
regenerates prehistory from the world seed and replays the seasons. The save stays tiny, and
determinism is enforced by the format itself: if the replay ever diverges, the bug shows up as a
failing round-trip test instead of silently corrupting a save.

## Considered options

- **One enum value per house.** Rejected: houses are generated per world, so they can't be enum
  constants.
- **Replace the enum with a string or int faction id everywhere.** Rejected: it touches every
  call site and every saved chunk, for no behaviour the house id doesn't already give.
- **Snapshot the whole `HistoryWorld` into the save.** Rejected: it's larger, it needs a schema
  version for every model change, and it hides determinism bugs.

## Consequences

- `ChunkData.MonsterData` gains `houseId`, which defaults to -1 (no house). Saves written before
  this change load with every monster outside any house, exactly as before.
- `WorldSaveData.factionMatrix` keeps serialising only the mortal rows. House relations are never
  serialised, because they are rebuilt from the history.
- Any change to prehistory generation changes the history of existing worlds. That's acceptable
  while the feature is pre-release. After release, it needs a history-generation version field
  next to `worldGenVersion`.
