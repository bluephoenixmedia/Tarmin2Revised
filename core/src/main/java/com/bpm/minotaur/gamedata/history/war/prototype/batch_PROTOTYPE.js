const fs = require('fs'); eval(fs.readFileSync(__dirname + '/worlds_PROTOTYPE.js', 'utf8').replace('const WORLDS', 'global.WORLDS'));
const Sim = require('./army_sim_PROTOTYPE.js'); const params = JSON.parse(process.argv[2] || '{}');
let ov = 0, enc = 0, pit = 0, exp = 0, lens = [], how = {}, armies = 0, n = 0, kinds = {}, wars = 0, both = 0;
for (const w of WORLDS) {
  const S = Sim.create(w, params, w.seed); S.autoSleep = true;
  while (S.season < 40) { Sim.tick(S); armies += S.armies.length; wars += Sim.activeWars(S).length; n++; }
  const r = Sim.summary(S); ov += r.overlandTurns; enc += r.encounters; pit += S.stats.pitched; exp += S.stats.expeditions;
  for (const e of S.stats.encounters) kinds[e.what] = (kinds[e.what] || 0) + 1;
  for (const x of r.warsEnded) { lens.push(x.seasons); if (x.bothStand) both++; const k = x.how.replace(/^.*sues for peace, (\w+).*$/, 'sues for peace, $1'); how[k] = (how[k] || 0) + 1; }
}
lens.sort((a, b) => a - b);
console.log(`turns/encounter ${(ov / Math.max(1, enc)).toFixed(0)} | pitched/expedition ${(pit / exp).toFixed(2)} (one per ${(exp / Math.max(1, pit)).toFixed(0)} expeditions) | units on map ${(armies / n).toFixed(1)} | wars running ${(wars / n).toFixed(1)} | wars ended ${lens.length}, median ${lens[lens.length >> 1]} p25 ${lens[lens.length >> 2]} p75 ${lens[(lens.length * 3) >> 2]}, both standing ${both}/${lens.length}`);
console.log('  ends:', JSON.stringify(how)); console.log('  met:', JSON.stringify(kinds));
