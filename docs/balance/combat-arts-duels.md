# Combat Arts in the duel simulator (#250, #313)

`./gradlew :lwjgl3:runDuelSimulation -PduelArgs="--trials=100 --chain=0 --report"` (also writes `assets/logs/duel-arts-report.md`).

- **The Arts are the game's own.** `--passive` and `--arts` have the soul learn them, and `CombatManager` fires them.
- **The policy** is simple: the capstone when it is ready and the foe is in reach, then the Expert Active. Held Arrow is drawn while the foe walks in; Marked Skull aims, then fires.
- **Depth d** is a level-d body against the monsters of base level 1 to d, scaled to EDL d, with `--approach`.
- **Each Arts column sits beside the same rank without them,** so the gap between the two is the Arts alone. Untrained is the fresh body the 0.0.2 targets are written for.
- **The weak Families were tuned from these numbers** in [#313](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/313), to the bound the user chose in #303. Their Arts were not touched.

## Results

Measured after [#313](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/313) brought the weak Families within the bound. The previous numbers are in the git history of this file.

### Depth 1 (level 1, EDL 1, monsters of base level 1-1: 7 species)

| Family | Weapon | Untrained | Adept | Adept + Passive | Master | Master + all Arts |
|---|---|---|---|---|---|---|
| Blade | Father's Sword | 98.3% | 99.1% | 99.4% | 99.3% | 99.3% |
| Dagger | DAGGER | 94.7% | 96.9% | 98.1% | 98.6% | 99.6% |
| Axe | AXE | 94.7% | 97.1% | 98.4% | 98.6% | 98.4% |
| Blunt | MACE_FOOTMANS | 97.9% | 98.6% | 98.7% | 99.6% | 100.0% |
| Polearm | SPEAR | 97.6% | 98.9% | 99.7% | 99.0% | 99.7% |
| Unarmed | bare hands | 93.9% | 97.9% | 98.9% | 99.7% | 100.0% |
| Bow | BOW | 98.7% | 99.7% | 99.9% | 100.0% | 100.0% |
| Crossbow | CROSSBOW | 96.7% | 98.4% | 98.6% | 99.1% | 100.0% |
| Firearm | MUSKET | 96.6% | 97.4% | 98.6% | 99.6% | 99.4% |
| Thrown | DART | 97.4% | 99.6% | 99.6% | 99.9% | 99.9% |

### Depth 3 (level 3, EDL 3, monsters of base level 1-3: 23 species)

| Family | Weapon | Untrained | Adept | Adept + Passive | Master | Master + all Arts |
|---|---|---|---|---|---|---|
| Blade | Father's Sword | 93.5% | 94.9% | 96.7% | 96.2% | 97.4% |
| Dagger | DAGGER | 90.6% | 92.6% | 94.6% | 94.3% | 97.8% |
| Axe | AXE | 89.7% | 90.4% | 90.1% | 93.1% | 91.6% |
| Blunt | MACE_FOOTMANS | 92.7% | 94.7% | 96.0% | 96.2% | 98.8% |
| Polearm | SPEAR | 93.0% | 94.7% | 96.8% | 95.9% | 96.6% |
| Unarmed | bare hands | 86.0% | 94.3% | 95.7% | 98.6% | 99.0% |
| Bow | BOW | 93.2% | 95.6% | 98.2% | 96.2% | 99.1% |
| Crossbow | CROSSBOW | 87.1% | 90.6% | 93.9% | 92.2% | 99.0% |
| Firearm | MUSKET | 90.7% | 95.0% | 94.9% | 96.5% | 95.0% |
| Thrown | DART | 93.1% | 97.2% | 98.2% | 99.3% | 99.7% |

The Thrown row was measured on its own, after the dart moved from 1d6+2 to 1d4+3 (the same mean) so that its loot tier stays where it was.

## The bound ([#303](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/303))

No Family may fall more than **10 points** behind the Father's Sword untrained, or more than **5 points** at Adept, at depths 1 and 3. The sword is the one that must meet the 0.0.2 targets (about 95% at depths 1-2 and 90% at depths 3-5), and it is unchanged. In this run the floors are:

| | Depth 1 | Depth 3 | Nearest Family |
|---|---|---|---|
| Untrained (sword - 10) | 88.3% | 83.5% | Unarmed 86.0% and Crossbow 87.1% at depth 3 |
| Adept (sword - 5) | 94.1% | 89.9% | Axe 90.4% at depth 3, 0.5 above the floor and inside the noise |

Every Family is inside the bound at both depths, untrained and at Adept.

## What #313 changed

| Family | Before | After |
|---|---|---|
| Dagger | 1d4+1 | 1d6+2 |
| Polearm (Spear) | 1d6 | 1d8+1 |
| Firearm (Musket) | 1d12 | 1d12+2, still under the 2d8 guns |
| Thrown (Dart) | 1d4+1 | 1d4+3 |
| Unarmed (the fist) | 1d2 | by Unarmed rank: 1d6, 1d7, 1d8, 1d10, 1d12 from Untrained to Master. A polymorphed body keeps its form's dice. |

No Art and no to-hit changed.

**Why damage and not to-hit.** A slashing blow can bleed and a blunt one can stun; a piercing one only skips armour soak, which few early monsters have. So the piercing Dagger and Spear need a higher mean than the slashing sword to fight as well. Holding them to the one-handed Longsword's mean (4.5) and adding to-hit instead was measured, and it fails the Adept floor at depth 3: Dagger 1d4+2 at +3 to-hit 87.3%, Spear 1d8 at +2 88.9%, against a floor of 89.9%. As a result the Dagger and Spear now roll a higher mean (5.5) than the Longsword's one-handed 1d8, and match its two-handed 1d10.

## Still open

- **Firearm Arts still add little in a duel** (Master + all Arts 95.0% vs Master 96.5% at depth 3). Marked Skull spends a turn aiming that the musket's slow reload makes costly; [#314](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/314) lets it aim while reloading.
- **The Axe** sits closest to the Adept floor at depth 3, and its Arts add little there. It was not one of the weak Families and was not tuned.
- **Polearm Arts** (Reel In, Thicket of Points) want a foe two tiles off or several foes, which a single duel offers neither of.

## How to read it

- **Noise.** Each cell is 700 duels (depth 1) or 2,300 (depth 3). Differences under about 1.5 points are noise; that covers the small negative Arts deltas for Axe and Firearm.
- **Shooters get free shots.** A bow, crossbow, gun or dart meets every monster four tiles off and shoots while it walks in, which a melee fighter never gets. Compare ranged Families with each other, not with melee.
- **Darts never run out.** The thrown dart is restocked into the hand every turn, which flatters Thrown.
- **Polearm uses the Spear,** as #239 did. Until #301 the Spear was marked ranged in `weapons.json` and could not bump-attack at all; it is a melee polearm now.
- **This depth 3 is not #239's.** #239's level-1 band of 10 species cannot be rebuilt from today's data: the level-1 band is 7 species, and was then too. Depth 3 here meets the 23 species of base level 1-3, the floor a depth-3 level holds. On the old level-1-only band, depth 3 reads about 99% for every Family, which hides the spread.
