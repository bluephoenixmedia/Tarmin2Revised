---
status: accepted (amends ADR 0004)
---

# Armies are saved as a snapshot beside the replayed history

The Living War became a true simulation (wayfinder map #317): houses field **armies** that march, camp, raid and fight across the overland whether or not the player is near, and what they win or lose is history. ADR 0004 saves the history as a **replay**: a seed, the Seasons elapsed and the ordered deed log, so determinism bugs surface as failing round-trip tests. A battle fought off-screen, though, is not a player deed. Army positions are not a function of anything a seed can reproduce, and replaying the army simulation from war clock 0 would make load time grow with play time.

We decided on a **hybrid**:

- **The history never reads the armies.** Every change the armies make to the history is written to the deed log when it happens:
  - `ARMY_BATTLE`, for every battle, watched or not: winner, loser, the dead on each side, each captain's fate (slain or taken), whether it was fought at a seat, the chunk, and whom the player fought for.
  - `SUED_FOR_PEACE`: the army layer decides a house has lost half its strength since the war began and can't muster another host.
  - `ARMY_ATTRITION`: one per house per Season, the soldiers lost to the land on the march.
  - `ARMY_BEAST`: a fight between an army and a megabeast: the beast, the house, the dead, the wound dealt, and whether the beast was slain.
  - `SEAT_INVESTED` and `SIEGE_RAISED`: an army settles around an enemy seat, and leaves it without a fall. The siege of Castle Tarmin keeps `SIEGE_LAID`, `SIEGE_BROKEN` and `SIEGE_LIFTED`, now written by the besieging host.

  The Season reads only deeds. So the replay stays exact, and the chronicle is the same however the armies are saved.
- **The armies themselves are a snapshot.** A `WarRoster` in `HistorySaveData` holds:
  - **every army:** house, levy-of, war, kind, captain, soldiers, starting soldiers, morale, chunk, march progress, objective, and a raid's "home by" Season;
  - **every battle under way:** chunk, the armies on each side, the garrison side, and each side's morale, dead, starting count and turn.

  It also keeps each army's **trail**: the chunks it crossed in the last Season, so chunks generated or loaded there come thinned of natives. Paths are recomputed on load. It replaces `EncounterLedger`'s slot anchors and first-skirmish flags; the aftermath dressings stay.
- **The history wins any disagreement on load.**
  - Armies of an extinct house are dropped.
  - Armies whose war has ended march home and disband.
  - A captain the history has dead or hostage is replaced (or the army goes nameless).
  - A house's armies are scaled down to fit inside its strength.
- **A reload replays the same future.** The army layer draws its randomness from the history seed, the war clock and the army id, never from a saved generator.
- **Old deeds still replay.** `SKIRMISH` and `BATTLE_WITNESSED` stay readable so old logs replay, but nothing writes them any more.
- **A save from before armies** loads with no roster: every house at war musters fresh at its seat, as if its war had just begun.

## Considered options

- **Pure replay: re-run the army simulation from war clock 0 on load.** Rejected: load time grows with every turn ever played, and any change to army tuning would rewrite every saved world's history.
- **Snapshot the whole history.** Rejected, as in ADR 0004: larger saves, a schema version for every model change, and determinism bugs hidden instead of failing.
- **Let the Season read the saved roster** (for example, to see that a house has no army left). Rejected: a stale or missing roster could then change the chronicle on replay.

## Consequences

- An army's effects on the history must always go through a deed. A new interaction that skips the log (for example, an army burning a holdfast that lowers its house's strength) breaks replay. Round-trip tests should replay a run that includes off-screen battles.
- The deed log now grows with the war, not only with the player's deeds: about one `ARMY_BATTLE` per Season per active war.
