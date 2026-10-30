// Writes each world's grid and road corridor for Sketch.java: "seed R grid corridor" per line.
const fs = require('fs'), path = require('path');
const dir = process.argv[2], out = process.argv[3];
eval(fs.readFileSync(path.join(dir, 'worlds_PROTOTYPE.js'), 'utf8').replace('const WORLDS', 'global.WORLDS'));
const Sim = require(path.join(dir, 'army_sim_PROTOTYPE.js'));
const lines = WORLDS.map(w => { const S = Sim.create(w, {}, w.seed); return [w.seed, w.R, w.grid, Array.from(S.corridor).join('')].join(' '); });
fs.writeFileSync(out, lines.join('\n') + '\n');
const b = {}; for (const c of WORLDS[0].grid) b[c] = (b[c] || 0) + 1; console.log(WORLDS.length, 'worlds, R', WORLDS[0].R, JSON.stringify(b));
