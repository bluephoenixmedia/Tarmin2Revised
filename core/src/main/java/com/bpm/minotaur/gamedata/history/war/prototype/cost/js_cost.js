// Times the army-sim prototype per war turn and per 250-turn sleep jump.
// usage: node js_cost.js <prototype dir> '<params json>' [seasons]
const fs = require('fs'), path = require('path');
const dir = process.argv[2];
const params = JSON.parse(process.argv[3] || '{}');
const seasons = +(process.argv[4] || 40);
eval(fs.readFileSync(path.join(dir, 'worlds_PROTOTYPE.js'), 'utf8').replace('const WORLDS', 'global.WORLDS'));
// instrument the pathfinder by source patch: count calls and time spent
let src = fs.readFileSync(path.join(dir, 'army_sim_PROTOTYPE.js'), 'utf8');
src = src.replace('function path(S, sx, sy, tx, ty, walker) {', 'function path(S, sx, sy, tx, ty, walker) { const _t = process.hrtime.bigint(); const _r = path0(S, sx, sy, tx, ty, walker); global.PATH_NS += Number(process.hrtime.bigint() - _t); global.PATH_N++; return _r; }\n  function path0(S, sx, sy, tx, ty, walker) {');
global.PATH_NS = 0; global.PATH_N = 0;
const m = { exports: {} }; new Function('module', 'require', src)(m, require); const Sim = m.exports;
const q = (a, p) => a[Math.min(a.length - 1, Math.floor(a.length * p))];
const all = []; let jumps = [], armiesSum = 0, armiesMax = 0, battlesSum = 0, warsSum = 0, n = 0;
for (const w of WORLDS) {
  const S = Sim.create(w, params, w.seed); S.autoSleep = true;
  const t = [];
  while (S.season < seasons) {
    const a = process.hrtime.bigint(); Sim.tick(S); t.push(Number(process.hrtime.bigint() - a) / 1000);
    armiesSum += S.armies.length; armiesMax = Math.max(armiesMax, S.armies.length); battlesSum += S.battles.length; warsSum += S.wars.filter(x => x.active).length; n++;
  }
  // skip the first Season (JIT warm-up) for per-turn numbers; every 250-turn window for jumps
  const warm = t.slice(500); for (const x of warm) all.push(x);
  let sum = 0; for (let i = 0; i < warm.length; i++) { sum += warm[i]; if (i >= 250) sum -= warm[i - 250]; if (i >= 249 && i % 25 === 0) jumps.push(sum / 1000); }
}
all.sort((a, b) => a - b); jumps.sort((a, b) => a - b);
const mean = all.reduce((a, b) => a + b, 0) / all.length;
console.log(JSON.stringify(params), `turns ${all.length}`);
console.log(`  units mean ${(armiesSum / n).toFixed(1)} max ${armiesMax}; wars mean ${(warsSum / n).toFixed(2)}; battles mean ${(battlesSum / n).toFixed(2)}`);
console.log(`  per turn us: mean ${mean.toFixed(1)} p50 ${q(all, .5).toFixed(1)} p99 ${q(all, .99).toFixed(1)} p99.9 ${q(all, .999).toFixed(0)} max ${all[all.length - 1].toFixed(0)}`);
console.log(`  250-turn jump ms: p50 ${q(jumps, .5).toFixed(2)} p99 ${q(jumps, .99).toFixed(2)} max ${jumps[jumps.length - 1].toFixed(2)}`);
console.log(`  pathfinder: ${PATH_N} calls (${(PATH_N / n).toFixed(3)} per turn), mean ${(PATH_NS / PATH_N / 1000).toFixed(1)} us, ${(100 * PATH_NS / 1000 / (mean * all.length)).toFixed(0)}% of time`);
