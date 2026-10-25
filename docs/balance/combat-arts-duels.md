# Combat Arts in the duel simulator (#250)

`./gradlew :lwjgl3:runDuelSimulation -PduelArgs="--trials=100 --chain=0 --report"` (also writes `assets/logs/duel-arts-report.md`).

- **The Arts are the game's own.** `--passive` and `--arts` have the soul learn them, and `CombatManager` fires them.
- **The policy** is simple: the capstone when it is ready and the foe is in reach, then the Expert Active. Held Arrow is drawn while the foe walks in; Marked Skull aims, then fires.
- **Depth d** is a level-d body against the monsters of base level 1 to d, scaled to EDL d, with `--approach`.
- **Each Arts column sits beside the same rank without them,** so the gap between the two is the Arts alone. Untrained is the fresh body the 0.0.2 targets are written for.
- **Nothing in the game was tuned from these numbers.** Spec section 7 leaves the weak Families to the user.

## Results

### Depth 1 (level 1, EDL 1, monsters of base level 1-1: 7 species)

| Family | Weapon | Untrained | Adept | Adept + Passive | Master | Master + all Arts |
|---|---|---|---|---|---|---|
| Blade | Father's Sword | 96.3% | 98.1% | 98.7% | 99.7% | 100.0% |
| Dagger | DAGGER | 85.9% | 92.9% | 93.4% | 92.7% | 97.9% |
| Axe | AXE | 95.0% | 98.0% | 98.0% | 99.3% | 97.4% |
| Blunt | MACE_FOOTMANS | 96.7% | 99.6% | 99.7% | 99.6% | 99.7% |
| Polearm | SPEAR | 88.3% | 92.9% | 95.7% | 95.9% | 96.4% |
| Unarmed | bare hands | 56.3% | 66.6% | 77.7% | 69.3% | 86.4% |
| Bow | BOW | 99.3% | 99.6% | 99.9% | 99.9% | 100.0% |
| Crossbow | CROSSBOW | 97.4% | 99.0% | 98.6% | 99.1% | 100.0% |
| Firearm | MUSKET | 91.6% | 95.0% | 94.4% | 97.3% | 97.0% |
| Thrown | DART | 88.7% | 95.0% | 98.6% | 99.4% | 99.4% |

### Depth 3 (level 3, EDL 3, monsters of base level 1-3: 23 species)

| Family | Weapon | Untrained | Adept | Adept + Passive | Master | Master + all Arts |
|---|---|---|---|---|---|---|
| Blade | Father's Sword | 93.6% | 95.5% | 96.0% | 96.0% | 97.7% |
| Dagger | DAGGER | 73.7% | 78.3% | 86.0% | 82.4% | 91.7% |
| Axe | AXE | 89.0% | 90.5% | 91.5% | 92.8% | 93.5% |
| Blunt | MACE_FOOTMANS | 92.0% | 94.9% | 96.8% | 95.8% | 98.8% |
| Polearm | SPEAR | 79.1% | 81.7% | 84.9% | 84.3% | 85.0% |
| Unarmed | bare hands | 43.4% | 47.5% | 59.1% | 50.2% | 63.3% |
| Bow | BOW | 93.7% | 95.0% | 97.6% | 95.9% | 99.0% |
| Crossbow | CROSSBOW | 86.7% | 90.7% | 94.2% | 92.0% | 99.3% |
| Firearm | MUSKET | 83.3% | 87.0% | 88.2% | 88.3% | 87.8% |
| Thrown | DART | 70.5% | 85.3% | 91.0% | 92.5% | 98.2% |

## Against the 0.0.2 targets

The target for one on-pace fight is about **95% at depths 1-2 and 90% at depths 3-5** (`docs/balance/0.0.2-targets.md`). Read against the Untrained column:

| Family | Depth 1 | Depth 3 | What the Arts add (Master + all Arts vs Master) |
|---|---|---|---|
| Unarmed | 56.3%, far under | 43.4%, far under | +17.1 / +13.1, the most of any Family, still far under |
| Dagger | 85.9%, under | 73.7%, under | +5.2 / +9.3 |
| Thrown (dart) | 88.7%, under | 70.5%, under | 0 / +5.7 |
| Firearm (musket) | 91.6%, under | 83.3%, under | -0.3 / -0.5: nothing |
| Polearm (spear) | 88.3%, under | 79.1%, under | +0.5 / +0.7 |
| Crossbow | on target | 86.7%, under | +0.9 / +7.3 |
| Axe | on target | 89.0%: at the line | +1 or less |
| Blade, Blunt, Bow | on target | on target | small |

## Candidates for the user (not applied)

- **Unarmed** misses by 40 points, and its Arts cannot close that. The lever is the fist itself: the bare-hand damage die, or the Unarmed to-hit, not Quick Knuckles' chance.
- **Dagger** and **darts** gain the most from rank and Arts, but start low. A small damage step on the weapons would bring the fresh body to target. Their Arts already pull their weight.
- **Firearm Arts do nothing in a duel.** Marked Skull spends a turn aiming that the musket's slow reload makes costly. Practised Hands and Powder Fury cut a reload that one duel rarely reaches twice. Options: let the aim turn also load the gun, or make Practised Hands a two-turn cut.
- **Polearm** (the Spear) starts under target, and its Arts add nothing at Master. Reel In and Thicket of Points want a foe two tiles off or several foes, and a single duel offers neither; that is likely fine in the maze, but the duel cannot show it. Its Adept Passive, Hold the Line, does help (+2.8 / +3.2). The Assegai, a lighter polearm, measured on target (95% / 89%), so the Spear's own numbers are the lever.

## How to read it

- **Noise.** Each cell is 700 duels (depth 1) or 2,300 (depth 3). Differences under about 1.5 points are noise; that covers the small negative Arts deltas for Axe and Firearm.
- **Shooters get free shots.** A bow, crossbow, gun or dart meets every monster four tiles off and shoots while it walks in, which a melee fighter never gets. Compare ranged Families with each other, not with melee.
- **Darts never run out.** The thrown dart is restocked into the hand every turn, which flatters Thrown.
- **Polearm uses the Spear,** as #239 did. Until #301 the Spear was marked ranged in `weapons.json` and could not bump-attack at all; it is a melee polearm now. Its two rows were measured after that fix, the same way as the rest.
- **This depth 3 is not #239's.** #239's level-1 band of 10 species cannot be rebuilt from today's data: the level-1 band is 7 species, and was then too. Depth 3 here meets the 23 species of base level 1-3, the floor a depth-3 level holds. On the old level-1-only band, depth 3 reads about 99% for every Family, which hides the spread.
