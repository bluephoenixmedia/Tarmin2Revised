# PROTOTYPE: the Living War as a true simulation (throwaway)

Answers wayfinder ticket #322, "Prototype a headless army simulation against the pacing targets". Not production code; never merge this branch.

- `PROTOTYPE_army_sim.html`: double-click it. Six real worlds, a map, tuning sliders, guided walkthroughs, and a batch that measures the W4 pacing targets.
- `army_sim_PROTOTYPE.js`: the pure simulation module (also embedded in the page).
- `batch_PROTOTYPE.js`: headless run, e.g. `node batch_PROTOTYPE.js '{"warsTarget":3}'`.
- `worlds_PROTOTYPE.js`: the world data, exported by `ZzArmyProtoExportTmp` (core tests) from seeds 1000, 8919, 24757, 40595, 135623 and 151461.
