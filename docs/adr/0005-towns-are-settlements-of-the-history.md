# Towns are settlements of the history; a map town binds to one on first sight

The Houses of the Maze plan (D37, T4.2) asks that a town's 4-8 folk be "named characters in the
`HistoryWorld`", and that NPC names match history characters. The history knew only the houses
of the Maze and their figures. Towns were a pure function of the history seed and the town's map
key, so their folk had names but no history.

Two things make towns awkward to put in the history:

- **The map is per run, the history is not.** Death re-rolls the world seed, so towns move and
  their keys (`level:x:y`) change, while the history persists (ADR 0004).
- **The house simulation is pinned.** Golden files and save round-trips depend on the order of
  every random draw the house simulator makes, and on figure ids.

We decided:

- **The history keeps its own settlements.** Prehistory founds a fixed number of `Settlement`s,
  each owing allegiance to a mortal power, with a seat for each role: merchant, smith, innkeeper,
  reeve, elder and some townsfolk. Each seat holds a `Mortal`: a named character with a birth,
  a death and a predecessor. When a holder dies, a successor of the same family takes the seat.
  Seasons tick the mortals in prehistory and in live play alike.
- **Mortals live apart from the houses.** They are a separate list in `HistoryWorld` with their
  own ids, simulated with a random stream keyed on the history seed and the season, never on the
  house simulator's generator. The houses' history, figure ids and golden files are unchanged.
- **A map town binds to a settlement the first time the game asks for it**, and the binding is
  saved. The town's name, allegiance and folk then come from the settlement, so a quest that names
  a distant town names the same town the player later walks into. Once every settlement is bound,
  further towns fall back to the old generated town, without history folk.
- **Exiles stay.** A Maze exile taken in by a town (sworn swords of fallen houses, losers of a
  succession) is added to the town's folk as before.

## Considered options

- **Mortals as `Figure`s with no house.** Rejected: house code reads every figure's house, and
  inserting mortals into the figure list would shift house figure ids and golden output.
- **Settlement chosen by hashing the map key.** Rejected: with a few dozen settlements, two towns
  of one run would often share one.
- **Generate a settlement for each town when found, as a live event.** Rejected for now: it needs
  a deed per town in the replay log, and a settlement born the day it is found has no history.
