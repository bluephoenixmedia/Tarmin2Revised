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
| Blade | Father's Sword | 97.3% | 98.3% | 98.6% | 99.0% | 100.0% |
| Dagger | DAGGER | 95.9% | 97.0% | 98.3% | 99.0% | 99.9% |
| Axe | AXE | 94.4% | 97.1% | 97.1% | 98.6% | 98.4% |
| Blunt | MACE_FOOTMANS | 95.9% | 98.0% | 99.0% | 99.6% | 99.9% |
| Polearm | SPEAR | 96.6% | 98.6% | 99.6% | 99.3% | 99.7% |
| Unarmed | bare hands | 93.7% | 98.1% | 98.4% | 99.9% | 100.0% |
| Bow | BOW | 98.9% | 99.9% | 99.9% | 99.9% | 100.0% |
| Crossbow | CROSSBOW | 98.1% | 98.1% | 99.3% | 99.4% | 99.9% |
| Firearm | MUSKET | 95.6% | 98.4% | 97.6% | 99.4% | 99.9% |
| Thrown | DART | 97.9% | 99.4% | 99.0% | 100.0% | 100.0% |

### Depth 3 (level 3, EDL 3, monsters of base level 1-3: 23 species)

| Family | Weapon | Untrained | Adept | Adept + Passive | Master | Master + all Arts |
|---|---|---|---|---|---|---|
| Blade | Father's Sword | 93.4% | 94.7% | 95.7% | 96.1% | 97.4% |
| Dagger | DAGGER | 88.9% | 93.0% | 95.7% | 94.7% | 98.0% |
| Axe | AXE | 89.1% | 91.4% | 91.3% | 93.0% | 93.3% |
| Blunt | MACE_FOOTMANS | 93.6% | 94.0% | 96.2% | 95.2% | 98.6% |
| Polearm | SPEAR | 92.6% | 95.0% | 96.8% | 95.3% | 96.6% |
| Unarmed | bare hands | 85.9% | 94.0% | 96.0% | 98.7% | 99.4% |
| Bow | BOW | 92.6% | 95.6% | 97.5% | 96.1% | 98.7% |
| Crossbow | CROSSBOW | 88.7% | 90.8% | 94.3% | 91.3% | 99.0% |
| Firearm | MUSKET | 92.3% | 95.0% | 95.5% | 96.3% | 95.4% |
| Thrown | DART | 92.0% | 97.4% | 98.3% | 99.0% | 99.7% |

The dart takes its extra damage as a flat bonus (1d4+3), because a larger die would raise its loot tier.

## The bound ([#303](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/303))

No Family may fall more than **10 points** behind the Father's Sword untrained, or more than **5 points** at Adept, at depths 1 and 3. The sword is the one that must meet the 0.0.2 targets (about 95% at depths 1-2 and 90% at depths 3-5), and it is unchanged. In this run the floors are:

| | Depth 1 | Depth 3 | Nearest Family |
|---|---|---|---|
| Untrained (sword - 10) | 87.3% | 83.4% | Unarmed 93.7% at depth 1 and 85.9% at depth 3 |
| Adept (sword - 5) | 93.3% | 89.7% | Crossbow 90.8% at depth 3 |

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

- **Firearm Arts still add little in a duel** (Master + all Arts 95.4% vs Master 96.3% at depth 3). Marked Skull spends a turn aiming that the musket's slow reload makes costly; [#314](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/314) lets it aim while reloading.
- **The Axe and the Crossbow** sit nearest the Adept floor at depth 3 (91.4% and 90.8%). Neither was one of the weak Families, and neither was tuned.
- **Polearm Arts** (Reel In, Thicket of Points) want a foe two tiles off or several foes, which a single duel offers neither of.

## How to read it

- **Noise.** Each cell is 700 duels (depth 1) or 2,300 (depth 3). Differences under about 1.5 points are noise; that covers the small negative Arts deltas for Axe and Firearm.
- **Shooters get free shots.** A bow, crossbow, gun or dart meets every monster four tiles off and shoots while it walks in, which a melee fighter never gets. Compare ranged Families with each other, not with melee.
- **Darts never run out.** The thrown dart is restocked into the hand every turn, which flatters Thrown.
- **Polearm uses the Spear,** as #239 did. Until #301 the Spear was marked ranged in `weapons.json` and could not bump-attack at all; it is a melee polearm now.
- **This depth 3 is not #239's.** #239's level-1 band of 10 species cannot be rebuilt from today's data: the level-1 band is 7 species, and was then too. Depth 3 here meets the 23 species of base level 1-3, the floor a depth-3 level holds. On the old level-1-only band, depth 3 reads about 99% for every Family, which hides the spread.
