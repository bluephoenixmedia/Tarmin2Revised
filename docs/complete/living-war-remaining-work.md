# The Living War: Remaining Work

State as of 2026-10-11. The plan is `Implementation Plan_ The Living War.md`.

## Status: COMPLETE FOR 0.0.2 MVP

All remaining tasks and gaps identified at the close of The Living War phases have been resolved, verified with unit and integration suites, and pushed to `develop`.

---

## Completed Work

1. **Breach Layout Fix**:
   - The siege breach searches outward from the gate's doorstep for the nearest clear tile it can reach (`nearestClear`).
   - Verified and committed as `fix(war): the breach finds clear ground on any castle layout`.
   - Full test suite passed (1,953 tests).

2. **Repository Sync**:
   - `develop` committed and pushed to `origin/develop`.
   - Untracked `docs/DEsign/defects_10_10_26.md` committed as chore/docs.

3. **Natural Siege Full Lifecycle Test**:
   - Added `SiegeTest.naturalSiegeLifecycleAndWorldIntegration`:
     - Verifies organic history simulation reaches `SIEGE_STRENGTH` and triggers `EventType.SIEGE_LAID` naturally.
     - Confirms Tarmin's Knell tolls at siege start.
     - Confirms besieger camps ring the 4 cardinal directions of Castle Tarmin chunk.
     - Confirms breach seal requirements (1 for friend/sworn, 2 for stranger).
     - Confirms attacker collapse triggers `SIEGE_BROKEN` / `SIEGE_LIFTED`, cleans up camps, silences soundscape, and tolls the Knell.

4. **Camp Fire Resting & Sleep System**:
   - Rest decoupled from the `war_banner` prop (which now handles oath swearing, contract turn-ins, and seal awards only).
   - Interacting with the camp's `campfire` prop restores HP/MP, burns taint, clears status ailments, resets expedition turns, advances world history by a season via `history.onSleep()`, outputs chronicles and rumours, advances day/night cycle, and saves the game via `SaveManager`.
   - Rest is gated to Friends and Sworn seekers.

5. **House Quartermaster Commissary**:
   - Upgraded camp quartermaster in `ShopInventory` and `GameScreen` from generic merchant to a military commissary.
   - Stocks war provisions (rations, potions, ammunition) and doctrine-specific military gear:
     - House signature weapon (per `Relics.weaponOf(doctrineId)`).
     - House tabard with heraldry and house affiliation.
     - Military armor and ammunition.
   - Gives Sworn members a **20% military discount** on all commissary goods.
   - Branded in the shop window as `QUARTERMASTER OF <HOUSE>`.

6. **Sound & Art Asset Integration**:
   - Added `crow.png`, `scorched_ground.png`, and `corpse_pile.png` to `props.json` and wired them into `EncounterDirector.FIELD` aftermath dressing.
   - Wired `camp_loop.mp3`, `crows_loop.mp3`, and `march_loop.wav` into `WarSoundscape.Bed` and `WarAudio` (with multi-format support for `.ogg`, `.mp3`, and `.wav`).
   - Verified all 8 doctrine tabard images (`1024x1536`) decode cleanly on `PaperDoll2DWidget`.
   - Deleted orphaned `__MACOSX` metadata stubs from `docs/game_assets/`.

---

## Standing Backlog (Deferred by Plan to Post-0.0.2)

- **Rising through the ranks** to duel a house lord (W39): requires its own design grill and implementation plan.
- **Castle Tarmin interior beyond the sealed throne door** (W36 stub): endgame dungeon requires its own plan.
- **Tabard disguise mechanics** (W17).
- **Dreamsmoke-style polymorph expansion** (W40).
