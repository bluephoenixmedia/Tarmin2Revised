# 0.0.2 verified polish backlog

Resolution of [Reconcile the polish backlog into one verified list](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/150), part of the 0.0.2 polish map (#149). Checked against `develop` at `dae38d64` on 2026-10-14.

Sources: `docs/test_notes_9_12_26.md` (open items), `docs/testing_1.md`, issues #102 #105 #106 #107 #108, the data slips in `docs/spawn-chart-by-depth.md` (branch `research/spawn-chart-by-depth`, #157), and `docs/ux/first-ten-minutes-audit.md` (branch `research/first-ten-minutes`, #156).

Paths: `J/` = `core/src/main/java/com/bpm/minotaur/`.

## Still broken (verified)

| # | Item | Evidence |
|---|---|---|
| B1 | Apocalypse death is counted twice (#106) | `J/screens/GameOverScreen.java:144` still calls `incrementDeaths()` after `recordDeath` |
| B2 | Field kits stored in the Shelter Chest are granted again (#102) | `J/gamedata/progression/FieldKitGrant.java:80` uses `inventory.hasItemOfType`, which ignores the chest |
| B3 | HUD conformance: the bleed badge covers the minimap, the minimap has no backing panel, vitals overflow, gauges are inconsistent, the hotbar is two widgets, the chronicle is mis-sized (#108) | issue #108; check for overlap with `docs/ui-overhaul/SPEC.md` before ticketing |
| B4 | 17 spawn data slips and code quirks: Dragon and other XP outliers, dice like `1d6+1` breaking the unlock score, "Disk, Crossbow" counted as holy, TOOL/AMULET/GOLD rolls wasted, dead container weights, items without variants never placed, COOKED_MEAT flagged as a potion, `weapons.json` overriding SCROLL, 16 `_S` keys never loading, `defaultBudget` 5, `difficultyOffset` always 0, firearm/mimic gates on EDL, no `generationFlags`, the GIANT substring arming insects | `docs/spawn-chart-by-depth.md` section 7, each with file:line |
| B5 | `docs/tuning_knobs.md` is wrong: `probability: 0` is rewritten to 10, `monsterSpawnTable` is live for Desert/Lakelands, the firearm gate is EDL; the `ItemTemplate.java:41` comment says the gate is 310, but it is 50 | spawn chart, slips 2, 3, 14, 15 |
| B6 | Hard-coded keys O/P/U/D/R/H/C/J/K/S/E/T/A ignore rebinding, though the legend shows rebinds | first-ten-minutes audit |
| B7 | The shelter lantern prompt says "equip in off-hand", which contradicts the belt slot | first-ten-minutes audit |
| B8 | The "is immune" log lines can never fire; the code resists (0.5x) rather than granting immunity | first-ten-minutes audit; decided by #164 |

## Fixed (close or untick)

| Item | Evidence |
|---|---|
| Sort-first item names shown to players (#107) | `J/gamedata/item/Item.java` `getDisplayName` routes through `ItemName.natural`. Close #107 |
| Death sequence and death screen (#105) | `J/rendering/DeathSequence.java`, commits `5ec2aa83` and `916fb46e`, `SoundManager.java:124` death cues. Confirm the bleed-out kill path in the reference run, then close #105 |
| Progression and unlock model | `UnlockManager`, `rollRunUnlocks`, death-screen display (`docs/polish/polish_9_17_26.md`) |
| Teleport into an enclosed area | `J/gamedata/spells/effects/TeleportEffect.java:26-28` only picks tiles that have a path. The wand teleport path is unverified |
| Right-click doesn't drop items | `J/screens/inventory/InventoryDragDropHandler.java:62`, `J/screens/InventoryScreen.java:1355`; quick-slot menu `89261857` |
| Scroll name revealed by the pickup notification | `J/gamedata/player/Player.java:1467,1543` route through `DiscoveryManager.getGroundItemDisplayName` |
| Monster flesh uses the food texture | `assets/data/items.json:183` `images/gore/gib1.png` |
| Book weapons strike instead of casting | `docs/polish/polish_9_17_26.md` (page particles, spell overlays) |
| "Do tomes affect spell slots?" | Not a bug: slots unlock by level (2/5/8/11) and tomes grant Known Spells (`CONTEXT.md`) |
| Telemetry system | Ticketed: #153 and #154 |

## Unclear: confirm during the reference run (#155)

| Item | Note |
|---|---|
| U1 Drag and drop between quick slots and inventory | `4ace108f` fixed spellbook drag and drop only |
| U2 "Inventory full" while slots are free | `J/gamedata/Inventory.java:55-63` has a quick-slot fallback; possibly a counting mismatch |
| U3 A special book vanishes after "can't use that" | `J/gamedata/player/Player.java:1779` |
| U4 A scroll's effect doesn't match its displayed name | may be the old pickup-name leak (now fixed) or a real mismatch |
| U5 Thick white fog in subterranean forest levels | `d30fce84` gave the overland forest green-black fog; Fungal Forest strata unverified |
| U6 Tornado VFX inside the shelter | `WeatherRenderer` has roof checks; tornado shake is not shelter-gated |
| U7 "Return to shelter" registers several deaths | `DeathIdempotencyTest` covers `recordDeath`; related to B1 |
| U8 Long weapons hit more than one tile away | no reach constant found in `CombatManager`; needs a live check |
| U9 Monster health bars not shown in every mode | `J/rendering/EntityRenderer.java:1532-1550` filters to `activeMonsterOnly` when one is set |
| U10 Rare non-deterministic monster spawner error (about 2 in 4M calls) | spawn chart probe caveat |

## Balance: routed to the balance tickets, not fixed piecemeal

| Item | Ticket |
|---|---|
| Late fights: about 80% of strikes miss, drawn out | combat feel targets (#158) |
| MP and HP regeneration | survival pacing targets (#159) |
| Flesh, trophy and bone drops too frequent: still 20% base, 30%/45% on overkill (`J/managers/CombatManager.java:3035-3039`) | loot economy (#160) |
| XP outliers (Dragon 50 and others) | progression pacing (#162) |

## Polish to triage (in scope under the freeze rule)

| Item | Note |
|---|---|
| P1 The Ossuary is not understood | onboarding (#165) or a P1/P2 clarity fix |
| P2 Buff glow on the idle first-person weapon | feedback on existing buffs, not a new system |
| P3 2 to 3 ladders per chunk | tuning of existing generation |

## Features found

None. Everything above fixes, tunes or clarifies something that already exists.
