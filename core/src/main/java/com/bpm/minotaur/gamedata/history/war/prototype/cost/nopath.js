// How often does the prototype ask for a path that does not exist, and what does that cost?
const fs = require('fs'), path = require('path');
const dir = process.argv[2];
eval(fs.readFileSync(path.join(dir, 'worlds_PROTOTYPE.js'), 'utf8').replace('const WORLDS', 'global.WORLDS'));
let src = fs.readFileSync(path.join(dir, 'army_sim_PROTOTYPE.js'), 'utf8');
src = src.replace('function path(S, sx, sy, tx, ty, walker) {', 'function path(S, sx, sy, tx, ty, walker) { const _t = process.hrtime.bigint(); const _r = path0(S, sx, sy, tx, ty, walker); const d = Number(process.hrtime.bigint() - _t) / 1000; if (_r === null) { NP.n++; NP.us += d; NP.max = Math.max(NP.max, d); } else { OK.n++; OK.us += d; OK.max = Math.max(OK.max, d); } return _r; }\n  function path0(S, sx, sy, tx, ty, walker) {');
global.NP = { n: 0, us: 0, max: 0 }; global.OK = { n: 0, us: 0, max: 0 };
const m = { exports: {} }; new Function('module', 'require', src)(m, require); const Sim = m.exports;
for (const w of WORLDS) {
  const a = { ...NP }, b = { ...OK };
  const S = Sim.create(w, { warsTarget: 3 }, w.seed); S.autoSleep = true;
  while (S.season < 40) Sim.tick(S);
  // connected components of the passable overland
  const N = 2 * w.R + 1, seen = new Uint8Array(N * N), open = c => c !== 'O' && c !== 'M' && c !== 'Z'; let comps = [];
  for (let i = 0; i < N * N; i++) { if (seen[i] || !open(w.grid[i])) continue; let size = 0; const st = [i]; seen[i] = 1; while (st.length) { const c = st.pop(); size++; const x = c % N, y = Math.floor(c / N); for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) { const nx = x + dx, ny = y + dy; if (nx < 0 || ny < 0 || nx >= N || ny >= N) continue; const j = ny * N + nx; if (!seen[j] && open(w.grid[j])) { seen[j] = 1; st.push(j); } } } comps.push(size); }
  comps.sort((p, q) => q - p);
  console.log(`seed ${w.seed}: paths ok ${OK.n - b.n}, none ${NP.n - a.n}; land components ${comps.length} (largest ${comps.slice(0, 4).join(', ')})`);
}
console.log(`found: mean ${(OK.us / OK.n).toFixed(1)} us, max ${OK.max.toFixed(0)} us | not found: ${NP.n} calls, mean ${(NP.us / Math.max(1, NP.n)).toFixed(1)} us, max ${NP.max.toFixed(0)} us, ${(100 * NP.us / (NP.us + OK.us)).toFixed(0)}% of pathfinding time`);
