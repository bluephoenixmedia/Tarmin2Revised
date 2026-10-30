## Resolution: the army simulation's cost and budget (measured 2026-10-30)

**Asset:** [`cost/` on prototype/army-sim](https://github.com/bluephoenixmedia/Tarmin2Revised/tree/prototype/army-sim/core/src/main/java/com/bpm/minotaur/gamedata/history/war/prototype/cost) (throwaway, never merge): `js_cost.js`, `nopath.js`, `export.js`, `Sketch.java.txt` (renamed so the Java build ignores it) and these results.

**Verdict: the simulation is cheap. A sleep jump runs every turn at full detail, with no coarser step.**

### What was measured
- **The JS prototype itself:** six real worlds, 40 Seasons each, about 100,000 turns per setting, with its decisions, logistics and battles all live.
- **A Java load sketch** on the same terrain, compiled with `--release 17`, run on Java 24, single-threaded. It is **not a port** of the decisions: it reproduces the prototype's measured load (0.05 replans per unit per turn, sight checks every 10 turns, contacts every turn, lines of 16). It is deliberately pessimistic: half its replans cross the map, and its battles never stop.
- Two Java styles: **naive** (fresh search arrays per path, boxed queues, per-turn maps) and **pooled** (reused search arrays, primitive heap, no per-turn maps). Java figures are the two runs after a terrain-cost fix; a run before the fix agreed.

### JS prototype

| Setting | Units, mean (max) | Turn, mean | Turn, p99 | 250-turn jump, p50 | Jump, max |
| :-- | :-- | :-- | :-- | :-- | :-- |
| 2 wars | 10.6 (24) | 13 us | 118 us | 2.8 ms | 15 ms |
| 3 wars | 14.1 (26) | 18 us | 140 us | 3.8 ms | 21 ms |
| 6 wars, double armies | 13.3 (30) | 19 us | 167 us | 3.8 ms | 26 ms |

Asking for more armies barely raised the count: the houses run out of strength to field. Pathfinding is 40-50% of the time, at about 10 us a call.

### Java sketch (ranges over two runs)

| Load | Style | Turn, mean | Turn, p99 | Jump, p50 | Jump, max | First jump in a fresh JVM | Allocated per turn |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| 14 units, 1 battle | naive | 113-118 us | 1.3 ms | 28-30 ms | 83-89 ms | 53-55 ms | 143 KB |
| 14 units, 1 battle | pooled | 65-68 us | 0.8-0.9 ms | 12-15 ms | 48-52 ms | 31-35 ms | 0.2 KB |
| 26 units, 2 battles | naive | 238-239 us | 1.8 ms | 59-61 ms | 120-137 ms | 77-83 ms | 265 KB |
| 26 units, 2 battles | pooled | 134-138 us | 1.2 ms | 33-35 ms | 81-102 ms | 48-55 ms | 0.3 KB |
| 50 units, 4 battles | naive | 496-508 us | 2.7 ms | 122-125 ms | 217-225 ms | 121-132 ms | 511 KB |
| 50 units, 4 battles | pooled | 239-300 us | 1.5-1.9 ms | 57-73 ms | 173-180 ms | 81-84 ms | 0.6 KB |

The highest single turn seen, pooled, was 4.6-14 ms. The timing can't say whether those spikes are the simulation, the collector or the operating system.

### The budget
Measured headless on the development machine, at the densest decided load (26 units, 2 battles):

| Measure | Budget | Pooled sketch today |
| :-- | :-- | :-- |
| War turn, mean | 0.25 ms | 0.14 ms |
| War turn, p99 | 2 ms (an eighth of a 60 fps frame) | 1.2 ms |
| 250-turn jump, worst | 150 ms, fresh JVM included | 102 ms |
| Allocation | 1 KB per turn | 0.3 KB |

Double the load (50 units, 4 battles) should stay under 300 ms a jump. That is a margin, not a target.

### What the build must do
1. **No coarser step for the jump.** It isn't needed, and it would break two decisions: battles are one state watched or not, and a reload replays the same future.
2. **Pool the pathfinder.** Reuse its search arrays. Naive code costs about 66 MB of garbage per sleep at the densest load and about 1.7 times the time. Time alone would pass the mean budget, so the allocation budget is what enforces this.
3. **Long skips run in slices.** "Wait in the siege" can run for Seasons: 1,500 turns is about 200 ms. It has to stop for an assault, relief or sally anyway, so it runs up to 250 turns a frame and checks between slices. "March on" stops the same way.
4. **Test what is stable.** The suite asserts allocation per turn and path calls per turn. It does not assert time, because this machine's timing flakes under hardware errors. Timing goes in a headless task beside `runDuelSimulation` that prints the table above.

### Checked and not needed
The worst searches (up to about 12 ms) looked like floods toward unreachable targets, since land has islets of up to 516 chunks. In the prototype's 40 Seasons on six worlds, **no path request failed** in about 72,000 calls. No reachability precomputation is needed. Revisit if a seat lands on an islet ([bug #330](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/330) moves stranded holdfasts to open land).

### Not measured
- **Materialised soldiers:** up to 32 monsters in the player's chunk during a battle cost what the existing monster AI costs, and this budget does not cover them.
- **The real Season tick and chronicle:** the prototype's stand-in is included, not `HistorySimulator`. Replay on load also grows with the deed log, by about one `ARMY_BATTLE` per Season per war.
- **Rendering:** shields over soldiers, map markers.
- **A lower-end machine:** all figures are from one desktop. Three times slower would make the worst jump about 300 ms, which a sleep fade can hide but a stutter during play could not.
- **The real Java code:** the sketch models the load. The build should confirm the budget with the headless task once the army layer exists.

Environment notes: one Node run hit the known segfault (the CPU's corrected hardware errors) and was rerun cleanly.

**Glossary:** no new terms.
