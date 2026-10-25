# Combat Arts in the duel simulator (#250)

`./gradlew :lwjgl3:runDuelSimulation -PduelArgs="--trials=200 --chain=0 --report"` (writes `assets/logs/duel-arts-report.md`).
The Arts are the game's own: `--passive` and `--arts` have the soul learn them, and `CombatManager` fires them.
Actives and capstones are used by a simple policy: the capstone when ready and the foe is in reach, then the Active;
Held Arrow is drawn while the foe walks in, Marked Skull aims then fires. Bows, crossbows, guns and darts fight from
four tiles off while the monster walks in. **Nothing in the game was tuned from these numbers.**

Notes for reading it:
- Each Arts column sits beside the same Mastery rank without them; the difference is the Arts alone.
- Polearm is measured with ASSEGAI: `weapons.json` now marks SPEAR `isRanged: true` (range 1), so a spear cannot
  bump-attack at all (0% wins). That data change came after #239 and is flagged for the user, not fixed here.
- The depth-1 band is base level 1 and now has 7 species (it had 10 in #239), and depth 3 reads far higher than #239's
  83.9% Untrained baseline: develop without this change gives the same (99.2%), so the band and the game moved, not this.

Combat Arts in the duel simulator (#250): 200 duels x 7 species per cell, band base level 1-1, --approach, seed 42. Win rates.

### Depth 1 (level 1, EDL 1)

| Family | Weapon | Untrained | Adept | Adept + Passive | Master | Master + all Arts |
|---|---|---|---|---|---|---|
| Blade | Father's Sword | 97.1% | 99.1% | 98.6% | 99.4% | 99.6% |
| Dagger | DAGGER | 85.9% | 90.1% | 93.6% | 93.4% | 98.0% |
| Axe | AXE | 94.8% | 96.6% | 96.7% | 98.7% | 98.9% |
| Blunt | MACE_FOOTMANS | 97.1% | 98.9% | 98.7% | 99.1% | 99.7% |
| Polearm | ASSEGAI | 95.8% | 96.8% | 98.5% | 98.7% | 98.8% |
| Unarmed | bare hands | 58.9% | 67.2% | 76.6% | 69.8% | 86.2% |
| Bow | BOW | 98.3% | 99.9% | 99.9% | 99.8% | 100.0% |
| Crossbow | CROSSBOW | 97.1% | 98.2% | 99.0% | 99.5% | 99.9% |
| Firearm | MUSKET | 90.3% | 95.0% | 94.9% | 97.8% | 97.1% |
| Thrown | DART | 86.6% | 95.4% | 96.9% | 99.0% | 99.6% |

### Depth 3 (level 3, EDL 3)

| Family | Weapon | Untrained | Adept | Adept + Passive | Master | Master + all Arts |
|---|---|---|---|---|---|---|
| Blade | Father's Sword | 99.4% | 99.4% | 100.0% | 99.9% | 99.9% |
| Dagger | DAGGER | 92.1% | 95.5% | 97.9% | 98.3% | 99.9% |
| Axe | AXE | 98.1% | 99.7% | 99.6% | 99.9% | 99.6% |
| Blunt | MACE_FOOTMANS | 99.1% | 99.8% | 99.8% | 99.9% | 100.0% |
| Polearm | ASSEGAI | 99.3% | 99.8% | 99.9% | 99.7% | 99.6% |
| Unarmed | bare hands | 65.3% | 72.1% | 85.2% | 79.4% | 91.5% |
| Bow | BOW | 99.7% | 99.9% | 100.0% | 100.0% | 100.0% |
| Crossbow | CROSSBOW | 98.2% | 98.6% | 99.5% | 99.1% | 100.0% |
| Firearm | MUSKET | 94.8% | 96.9% | 97.7% | 98.6% | 98.0% |
| Thrown | DART | 90.1% | 96.7% | 97.4% | 99.3% | 99.9% |
