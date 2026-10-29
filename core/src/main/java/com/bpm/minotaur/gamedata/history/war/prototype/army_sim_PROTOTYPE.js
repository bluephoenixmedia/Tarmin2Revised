// ===================== PROTOTYPE: army simulation module (pure; no DOM) =====================
// Wayfinder "Prototype a headless army simulation against the pacing targets" (#322).
// Encodes the decisions of #318, #320 and #321. Throwaway.
const ArmySim = (() => {
  const DEFAULTS = {
    roadTurns: 15, offTurns: 20, roughTurns: 26,   // war turns to cross a chunk (#320)
    armySize: 15, minArmy: 15, garrisonShare: 0.25, // (#321)
    levyShare: 1 / 3, raidSize: 8,
    recovery: 5,                                    // strength per Season (tuned up from 1.2)
    killRate: 0.012, moraleLoss: 4, captainFall: 0.004, garrisonEdge: 1.1,
    sight: 6, seasonTurns: 500, sleepTurns: 250,
    playerTurns: 45, expeditionTurns: 1500,         // player: war turns per chunk walked; overland turns per expedition
    declareChance: 0.04, peaceRaidChance: 0.25, strengthPerArmy: 20, wallEdge: 4, warsTarget: 0, minWars: 2, seenRadius: 1, supplyEvery: 60, supplyMin: 3, forageEvery: 80, forageSize: 4, forageTurns: 40, logistics: 1,
  };
  const TRAITS = ['AMBITIOUS', 'WRATHFUL', 'CRAVEN', 'ZEALOT', 'CUNNING', 'HONOURABLE', 'CRUEL', 'PATIENT'];

  function rngOf(seed) {
    let a = (seed >>> 0) || 1;
    return () => { a |= 0; a = a + 0x6D2B79F5 | 0; let t = Math.imul(a ^ a >>> 15, 1 | a); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; };
  }
  const cheb = (ax, ay, bx, by) => Math.max(Math.abs(ax - bx), Math.abs(ay - by));

  // ---------- world ----------
  function create(worldData, params, seed) {
    const P = Object.assign({}, DEFAULTS, params || {});
    const W = JSON.parse(JSON.stringify(worldData));
    const S = {
      P, W, R: W.R, rng: rngOf(seed || W.seed), clock: 0, season: 0, nextId: 1,
      houses: {}, wars: [], armies: [], battles: [], deeds: [], log: [],
      allies: new Set(W.allies.map(([a, b]) => key(a, b))),
      grudges: W.grudges.slice(),
      player: { x: 0, y: 0, route: [], prog: 0, underground: false, overland: 0, expTurns: 0, asleep: 0, roadIx: 0, travelling: true },
      stats: newStats(),
    };
    for (const h of W.houses) {
      S.houses[h.id] = Object.assign(h, { alive: true, prestige: 0, deadFigures: new Set(), hostages: new Set(), gx: h.seat[0], gy: h.seat[1] });
    }
    for (const w of W.wars) S.wars.push({ id: S.nextId++, a: w.a, d: w.d, start: -w.age, active: true, s0: { [w.a]: S_str(W, w.a), [w.d]: S_str(W, w.d) } });
    S.corridor = new Uint8Array((2 * S.R + 1) ** 2);
    for (let y = -S.R; y <= S.R; y++) for (let x = -S.R; x <= S.R; x++)
      for (const r of W.roads) if (segDist(x, y, r.end[0], r.end[1]) <= 1) S.corridor[ix(S, x, y)] = 1;
    S.shelters = []; W.roads.forEach((r, i) => r.shelters.forEach(s => S.shelters.push({ x: s[0], y: s[1], road: i })));
    for (const h of Object.values(S.houses)) muster(S, h);
    note(S, 'The live war begins. ' + activeWars(S).map(w => warName(S, w)).join('; '));
    return S;
  }
  const S_str = (W, id) => (W.houses.find(h => h.id === id) || { strength: 0 }).strength;
  const key = (a, b) => a < b ? a + ':' + b : b + ':' + a;
  function segDist(x, y, ex, ey) {
    const L = ex * ex + ey * ey; if (!L) return Math.hypot(x, y);
    const t = Math.max(0, Math.min(1, (x * ex + y * ey) / L)); return Math.hypot(x - t * ex, y - t * ey);
  }
  const ix = (S, x, y) => (y + S.R) * (2 * S.R + 1) + (x + S.R);
  function biome(S, x, y) { return Math.abs(x) > S.R || Math.abs(y) > S.R ? 'O' : S.W.grid[ix(S, x, y)]; }
  function passable(S, x, y) { const b = biome(S, x, y); return b !== 'O' && b !== 'M' && b !== 'Z'; }
  function cost(S, x, y) { if (S.corridor[ix(S, x, y)]) return S.P.roadTurns; const b = biome(S, x, y); return b === 'T' || b === 'B' ? S.P.roughTurns : S.P.offTurns; }

  function path(S, sx, sy, tx, ty, walker) {
    if (sx === tx && sy === ty) return [];
    const N = 2 * S.R + 1, g = new Float64Array(N * N).fill(Infinity), from = new Int32Array(N * N).fill(-1);
    const heap = []; const push = (f, i) => { heap.push([f, i]); let c = heap.length - 1; while (c > 0) { const p = (c - 1) >> 1; if (heap[p][0] <= heap[c][0]) break; [heap[p], heap[c]] = [heap[c], heap[p]]; c = p; } };
    const pop = () => { const top = heap[0], last = heap.pop(); if (heap.length) { heap[0] = last; let c = 0; for (;;) { const l = 2 * c + 1, r = l + 1; let m = c; if (l < heap.length && heap[l][0] < heap[m][0]) m = l; if (r < heap.length && heap[r][0] < heap[m][0]) m = r; if (m === c) break; [heap[m], heap[c]] = [heap[c], heap[m]]; c = m; } } return top; };
    const s = ix(S, sx, sy), t = ix(S, tx, ty); g[s] = 0; push(0, s);
    while (heap.length) {
      const [, i] = pop(); if (i === t) break;
      const x = i % N - S.R, y = Math.floor(i / N) - S.R;
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        if (!dx && !dy) continue; const nx = x + dx, ny = y + dy;
        if (Math.abs(nx) > S.R || Math.abs(ny) > S.R) continue;
        const j = ix(S, nx, ny); if (j !== t && !passable(S, nx, ny) && !(walker && biome(S, nx, ny) === 'Z')) continue;
        const ng = g[i] + cost(S, nx, ny); if (ng < g[j]) { g[j] = ng; from[j] = i; push(ng + cheb(nx, ny, tx, ty) * S.P.roadTurns, j); }
      }
    }
    if (from[t] < 0) return null;
    const out = []; for (let i = t; i !== s; i = from[i]) out.push([i % N - S.R, Math.floor(i / N) - S.R]); return out.reverse();
  }

  // ---------- history helpers ----------
  const activeWars = S => S.wars.filter(w => w.active);
  const warsOf = (S, h) => activeWars(S).filter(w => w.a === h || w.d === h);
  const atWar = (S, a, b) => activeWars(S).some(w => (w.a === a && w.d === b) || (w.a === b && w.d === a));
  const side = A => A.levyOf != null ? A.levyOf : A.house;
  const hostile = (S, A, B) => A.peaceRaid || B.peaceRaid ? false : atWar(S, side(A), side(B));
  const friendly = (S, A, B) => side(A) === side(B) || S.allies.has(key(side(A), side(B)));
  const H = (S, id) => S.houses[id];
  const hname = (S, id) => H(S, id) ? H(S, id).name : '?';
  const warName = (S, w) => hname(S, w.a) + ' against ' + hname(S, w.d);
  const fielded = (S, h) => S.armies.filter(a => a.house === h).reduce((n, a) => n + a.soldiers, 0);
  const garrison = (S, h) => Math.max(0, Math.round(H(S, h).strength - fielded(S, h)));
  const has = (A, t) => A.captain && A.captain.traits.includes(t);
  function note(S, text, chronicled) { S.log.push({ t: S.clock, season: S.season, text, chronicled: !!chronicled }); if (S.log.length > 400) S.log.shift(); }
  function deed(S, d) { d.t = S.clock; d.season = S.season; S.deeds.push(d); }

  function pickCaptain(S, h) {
    const used = new Set(S.armies.filter(a => a.captain).map(a => a.captain.id));
    const ok = f => !used.has(f.id) && !h.deadFigures.has(f.id) && !h.hostages.has(f.id);
    const lord = h.figures.find(f => f.role === 'lord' && ok(f));
    if (lord && (lord.traits.includes('WRATHFUL') || lord.traits.includes('AMBITIOUS')) && !lord.traits.includes('CRAVEN')) return lord;
    return h.figures.find(f => f.role === 'kin' && ok(f)) || h.figures.find(f => f.role === 'sworn' && ok(f)) || null;
  }
  function newArmy(S, h, soldiers, extra) {
    const A = Object.assign({ id: S.nextId++, house: h.id, soldiers, start: soldiers, morale: 100, x: h.gx, y: h.gy, path: [], prog: 0, obj: { kind: 'guard' }, battle: null, captain: pickCaptain(S, h), raidSent: false, reseen: 0, seenIds: new Set() }, extra || {});
    S.armies.push(A); return A;
  }
  function muster(S, h) {
    if (!h.alive) return;
    const wars = warsOf(S, h.id);
    const minG = Math.ceil(h.strength * S.P.garrisonShare);
    if (wars.length) {
      for (;;) {
        const avail = garrison(S, h.id) - minG; if (avail < S.P.minArmy) break;
        const own = S.armies.filter(a => a.house === h.id && !a.raid && !a.supply && !a.forage && a.levyOf == null);
        if (own.length >= Math.max(1, Math.round(h.strength / S.P.strengthPerArmy))) break;
        const w = wars.slice().sort((x, y) => own.filter(a => a.war === x.id).length - own.filter(a => a.war === y.id).length || H(S, enemyIn(y, h.id)).strength - H(S, enemyIn(x, h.id)).strength)[0];
        const A = newArmy(S, h, Math.min(S.P.armySize, avail), { war: w.id });
        note(S, `${h.name} musters a host of ${A.soldiers} under ${capName(A)}, against ${hname(S, enemyIn(w, h.id))}.`);
        decide(S, A);
      }
    } else if (h.liege >= 0 && H(S, h.liege) && H(S, h.liege).alive && warsOf(S, h.liege).length && !S.armies.some(a => a.house === h.id && a.levyOf != null)) {
      const n = Math.round(h.strength * S.P.levyShare);
      if (n >= S.P.minArmy && garrison(S, h.id) - minG >= n) {
        const A = newArmy(S, h, n, { levyOf: h.liege, war: warsOf(S, h.liege)[0].id });
        note(S, `${h.name} sends a levy of ${n} to its liege, ${hname(S, h.liege)}.`); decide(S, A);
      }
    }
  }
  const enemyIn = (w, h) => w.a === h ? w.d : w.a;
  const capName = A => A.captain ? A.captain.name : 'a nameless captain';

  // ---------- objectives (#320) ----------
  function knownEnemies(S, A) {
    const out = [];
    const eyes = S.armies.filter(o => friendly(S, o, A));
    for (const E of S.armies) {
      if (!hostile(S, A, E)) continue;
      const home = H(S, side(A));
      if (eyes.some(o => cheb(o.x, o.y, E.x, E.y) <= S.P.sight) || (home && cheb(home.gx, home.gy, E.x, E.y) <= S.P.sight)) out.push(E);
    }
    return out;
  }
  function goTo(S, A, tx, ty) { const p = path(S, A.x, A.y, tx, ty); A.path = p || []; }
  function decide(S, A) {
    const h = H(S, A.house);
    if (A.raid || A.supply || A.forage) return; // detachments keep their errand
    const war = S.wars.find(w => w.id === A.war);
    if (A.levyOf != null) {
      if (!war || !war.active) return goHome(S, A, 'disband');
      const liegeArmies = S.armies.filter(o => o.house === A.levyOf && !o.raid);
      if (!liegeArmies.length) return goHome(S, A, 'guard');
      const L = liegeArmies.sort((p, q) => cheb(A.x, A.y, p.x, p.y) - cheb(A.x, A.y, q.x, q.y))[0];
      A.obj = { kind: 'follow', target: L.id, label: 'follows ' + capName(L) }; goTo(S, A, L.x, L.y); return;
    }
    if (!war || !war.active) return goHome(S, A, 'disband');
    const thr = has(A, 'CRAVEN') ? 0.65 : has(A, 'WRATHFUL') ? 0.35 : 0.5;
    if (A.soldiers < A.start * thr || A.morale < 40) return goHome(S, A, 'recover');
    const enemy = H(S, enemyIn(war, h.id));
    const cands = [];
    if (enemy && enemy.alive && !enemy.kind.startsWith('castle')) {
      const ratio = A.soldiers / (garrison(S, enemy.id) * S.P.wallEdge + 1), d = cheb(A.x, A.y, enemy.gx, enemy.gy);
      cands.push({ kind: 'seat', score: 0.6 + (has(A, 'AMBITIOUS') ? 0.5 : 0) + 0.4 * Math.min(ratio, 2) - d / 80 - (has(A, 'PATIENT') && ratio < 1.5 ? 0.8 : 0) + (has(A, 'ZEALOT') ? 0.2 : 0), tx: enemy.gx, ty: enemy.gy, label: 'marches on the seat of ' + enemy.name });
    }
    const seen = knownEnemies(S, A);
    if (seen.length) {
      const E = seen.sort((p, q) => cheb(A.x, A.y, p.x, p.y) - cheb(A.x, A.y, q.x, q.y))[0];
      const ratio = A.soldiers / Math.max(1, E.soldiers), d = cheb(A.x, A.y, E.x, E.y);
      cands.push({ kind: 'hunt', target: E.id, score: 0.5 + (has(A, 'WRATHFUL') ? 0.5 : 0) + 0.4 * Math.min(ratio, 2) - d / 25 - (has(A, 'CUNNING') && ratio > 0.7 && ratio < 1.4 ? 0.5 : 0), tx: E.x, ty: E.y, label: 'hunts the host of ' + capName(E) });
      const nearHome = seen.some(E2 => cheb(E2.x, E2.y, h.gx, h.gy) <= S.P.sight);
      if (nearHome) cands.push({ kind: 'guard', score: 0.7 + (has(A, 'CRAVEN') ? 0.6 : 0), tx: h.gx, ty: h.gy, label: 'guards its seat' });
    }
    if (!has(A, 'HONOURABLE') && !A.raidSent && A.soldiers >= S.P.minArmy + S.P.raidSize && enemy && enemy.alive)
      cands.push({ kind: 'raid', score: 0.3 + (has(A, 'CUNNING') ? 0.5 : 0) + (has(A, 'CRUEL') ? 0.4 : 0) + S.rng() * 0.2 });
    cands.push({ kind: 'guard', score: 0.15 + (has(A, 'CRAVEN') ? 0.3 : 0), tx: h.gx, ty: h.gy, label: 'holds near its seat' });
    for (const c of cands) c.score += S.rng() * 0.15;
    cands.sort((p, q) => q.score - p.score);
    let best = cands[0];
    if (best.kind === 'raid') {
      A.raidSent = true; sendRaid(S, A, enemy, war);
      best = cands.find(c => c.kind !== 'raid');
    }
    A.obj = { kind: best.kind, target: best.target, label: best.label }; goTo(S, A, best.tx, best.ty);
  }
  function goHome(S, A, why) { const h = H(S, A.house); A.obj = { kind: why, label: why === 'recover' ? 'falls back home to recover' : why === 'disband' ? 'marches home to disband' : 'holds near its seat' }; goTo(S, A, h.gx, h.gy); }
  function raidTarget(S, from, enemy) {
    if (enemy.kind === 'great') {
      const road = S.W.roads.findIndex(r => r.end[0] === enemy.gx && r.end[1] === enemy.gy);
      const sh = S.shelters.filter(s => s.road === road);
      if (sh.length) { const s = sh.sort((p, q) => cheb(from.x, from.y, p.x, p.y) - cheb(from.x, from.y, q.x, q.y))[0]; return { x: s.x, y: s.y, label: 'camps outside a road shelter on the road to ' + enemy.name }; }
    }
    for (let r = 1; r < 4; r++) for (let dy = -r; dy <= r; dy++) for (let dx = -r; dx <= r; dx++)
      if (passable(S, enemy.gx + dx, enemy.gy + dy) && (dx || dy)) return { x: enemy.gx + dx, y: enemy.gy + dy, label: 'burns the lands of ' + enemy.name };
    return { x: enemy.gx, y: enemy.gy, label: 'burns the lands of ' + enemy.name };
  }
  function sendRaid(S, A, enemy, war, peace) {
    const h = H(S, A ? A.house : peace.house);
    const from = A || { x: h.gx, y: h.gy };
    const t = raidTarget(S, from, enemy);
    if (A) A.soldiers -= S.P.raidSize; // the party is detached from the host
    const R = newArmy(S, h, S.P.raidSize, { raid: true, peaceRaid: !!peace, war: war ? war.id : null, x: from.x, y: from.y, untilSeason: S.season + 1 });
    R.captain = null; R.obj = { kind: 'raid', label: t.label }; goTo(S, R, t.x, t.y);
    note(S, `${h.name} sends raiders: ${t.label}.`);
  }

  // ---------- one war turn ----------
  function tick(S) {
    S.clock++;
    // marching
    for (const A of S.armies) {
      if (A.battle) continue;
      if (A.path.length) {
        A.prog++;
        const [nx, ny] = A.path[0];
        if (A.prog >= cost(S, nx, ny)) { A.prog = 0; A.x = nx; A.y = ny; A.path.shift(); if (!A.path.length) arrive(S, A); }
      }
      else if ((S.clock + A.id) % 25 === 0) arrive(S, A); // idle (there already, or no way): look again
      if (A.obj.kind === 'hunt' || A.obj.kind === 'follow') {
        if ((S.clock + A.id) % 20 === 0) { const T = S.armies.find(o => o.id === A.obj.target); if (!T) decide(S, A); else goTo(S, A, T.x, T.y); }
      }
      if ((S.clock + A.id) % 10 === 0 && A.levyOf == null && !A.raid) {
        const seen = knownEnemies(S, A).filter(E => !A.seenIds.has(E.id));
        if (seen.length) { seen.forEach(E => A.seenIds.add(E.id)); decide(S, A); }
      }
    }
    if (S.P.logistics) logistics(S);
    contacts(S);
    for (const B of S.battles.slice()) stepBattle(S, B);
    S.armies = S.armies.filter(A => A.soldiers > 0 && !A.gone);
    if (S.clock % S.P.seasonTurns === 0) seasonTick(S);
    playerTick(S);
  }
  // Logistics (#322, Q1): supply columns from the seat to its hosts, and foragers out and back.
  function logistics(S) {
    for (const A of S.armies.slice()) {
      if (A.raid || A.supply || A.forage || A.battle) continue;
      const h = H(S, A.house);
      if ((S.clock + A.id) % S.P.supplyEvery === 0 && A.soldiers < S.P.armySize && !S.armies.some(c => c.supply && c.obj.target === A.id)) {
        const minG = Math.ceil(h.strength * S.P.garrisonShare), n = Math.min(S.P.armySize - A.soldiers, garrison(S, h.id) - minG);
        if (n >= S.P.supplyMin && cheb(A.x, A.y, h.gx, h.gy) > 0) {
          const C = newArmy(S, h, n, { supply: true, war: A.war, levyOf: A.levyOf }); C.captain = null;
          C.obj = { kind: 'supply', target: A.id, label: 'a supply column for ' + capName(A) }; goTo(S, C, A.x, A.y);
        }
      }
      if ((S.clock + A.id * 7) % S.P.forageEvery === 0 && A.soldiers > S.P.forageSize + S.P.minArmy) {
        const dx = Math.floor(S.rng() * 5) - 2, dy = Math.floor(S.rng() * 5) - 2;
        if ((dx || dy) && passable(S, A.x + dx, A.y + dy)) {
          A.soldiers -= S.P.forageSize;
          const F = newArmy(S, h, S.P.forageSize, { forage: true, war: A.war, levyOf: A.levyOf, x: A.x, y: A.y, back: A.id, until: S.clock + S.P.forageTurns }); F.captain = null;
          F.obj = { kind: 'forage', label: 'foragers of ' + capName(A) }; goTo(S, F, A.x + dx, A.y + dy);
        }
      }
    }
    for (const C of S.armies) {
      if (C.battle) continue;
      if (C.supply) {
        const T = S.armies.find(o => o.id === C.obj.target);
        if (!T) { C.gone = true; continue; }
        if (T.x === C.x && T.y === C.y) { T.soldiers += C.soldiers; T.start = Math.max(T.start, T.soldiers); C.soldiers = 0; C.gone = true; }
        else if ((S.clock + C.id) % 20 === 0) goTo(S, C, T.x, T.y);
      }
      if (C.forage && S.clock >= C.until) {
        const T = S.armies.find(o => o.id === C.back);
        if (!T) { C.gone = true; continue; }
        if (T.x === C.x && T.y === C.y) { T.soldiers += C.soldiers; C.soldiers = 0; C.gone = true; }
        else if ((S.clock + C.id) % 20 === 0) goTo(S, C, T.x, T.y);
      }
    }
  }
  function arrive(S, A) {
    const h = H(S, A.house);
    if (A.supply || A.forage) return;
    if (A.raid) return; // camp until the season turns
    if (A.obj.kind === 'disband' && A.x === h.gx && A.y === h.gy) { A.gone = true; note(S, `The host of ${capName(A)} returns to ${h.name}'s seat and disbands.`); return; }
    if (A.obj.kind === 'recover' && A.x === h.gx && A.y === h.gy) {
      const minG = Math.ceil(h.strength * S.P.garrisonShare), top = Math.max(0, Math.min(S.P.armySize - A.soldiers, garrison(S, h.id) - minG));
      A.soldiers += top; A.start = Math.max(A.soldiers, S.P.minArmy); A.morale = 100;
      if (A.soldiers < S.P.minArmy) { A.gone = true; note(S, `The host of ${capName(A)} is too few to march again and joins the garrison.`); return; }
    }
    if (A.obj.kind === 'seat') {
      const war = S.wars.find(w => w.id === A.war), enemy = war && H(S, enemyIn(war, A.house));
      if (enemy && enemy.alive && A.x === enemy.gx && A.y === enemy.gy) return startBattle(S, A.x, A.y, [A], [], enemy.id);
    }
    decide(S, A);
  }

  // ---------- battles (one state, watched or not: #318) ----------
  function contacts(S) {
    const byChunk = new Map();
    for (const A of S.armies) { const k = A.x + ',' + A.y; (byChunk.get(k) || byChunk.set(k, []).get(k)).push(A); }
    for (const [, list] of byChunk) {
      const free = list.filter(A => !A.battle);
      if (!free.length) continue;
      const B = S.battles.find(b => b.x === list[0].x && b.y === list[0].y);
      if (B) { for (const A of free) join(S, B, A); continue; }
      for (let i = 0; i < free.length; i++) for (let j = i + 1; j < free.length; j++) {
        if (!free[i].battle && !free[j].battle && hostile(S, free[i], free[j])) {
          const nb = startBattle(S, free[i].x, free[i].y, [free[i]], [free[j]], null);
          for (const o of free) if (!o.battle) join(S, nb, o);
        }
      }
    }
  }
  function join(S, B, A) {
    for (const k of ['a', 'b']) {
      const other = k === 'a' ? 'b' : 'a';
      const foe = B[other].armies.map(id => S.armies.find(x => x.id === id)).filter(Boolean)[0];
      const foeHouse = foe ? side(foe) : B[other].garrisonOf;
      if (foeHouse == null) continue;
      const mine = B[k].armies.map(id => S.armies.find(x => x.id === id)).filter(Boolean)[0];
      if (atWar(S, side(A), foeHouse) && !A.peaceRaid && (!mine || friendly(S, A, mine))) {
        B[k].armies.push(A.id); A.battle = B.id; B[k].start += A.soldiers; return;
      }
    }
    // allies of a side join if adjacent/present (#320 Q6)
    for (const k of ['a', 'b']) {
      const mine = B[k].armies.map(id => S.armies.find(x => x.id === id)).filter(Boolean)[0];
      if (mine && S.allies.has(key(side(A), side(mine)))) { B[k].armies.push(A.id); A.battle = B.id; B[k].start += A.soldiers; note(S, `${hname(S, A.house)} joins the fight on its ally's side.`); return; }
    }
  }
  function startBattle(S, x, y, as, bs, garrisonOf) {
    const B = { id: S.nextId++, x, y, turn: 0, seat: garrisonOf != null,
      a: { armies: as.map(A => A.id), morale: 100, dead: 0, start: as.reduce((n, A) => n + A.soldiers, 0), captainFell: new Set() },
      b: { armies: bs.map(A => A.id), garrisonOf, morale: 100, dead: 0, start: bs.reduce((n, A) => n + A.soldiers, 0) + (garrisonOf != null ? garrison(S, garrisonOf) : 0), captainFell: new Set() } };
    as.concat(bs).forEach(A => A.battle = B.id);
    S.battles.push(B);
    // allied armies within one chunk join
    for (const o of S.armies) if (!o.battle && cheb(o.x, o.y, x, y) <= 1) {
      for (const k of ['a', 'b']) { const m = B[k].armies.map(id => S.armies.find(q => q.id === id))[0]; if (m && S.allies.has(key(side(o), side(m)))) { B[k].armies.push(o.id); o.battle = B.id; B[k].start += o.soldiers; note(S, `${hname(S, o.house)} marches in on its ally's side.`); break; } }
    }
    const names = k => B[k].armies.map(id => hname(S, S.armies.find(q => q.id === id).house)).concat(B[k].garrisonOf != null ? [hname(S, B[k].garrisonOf) + ' (garrison)'] : []);
    note(S, `${B.seat ? 'Assault on a seat' : 'Battle'} at (${x},${y}): ${names('a').join(', ')} against ${names('b').join(', ')}.`);
    return B;
  }
  function sideAlive(S, B, k) {
    let n = B[k].armies.map(id => S.armies.find(q => q.id === id)).filter(Boolean).reduce((s, A) => s + A.soldiers, 0);
    if (B[k].garrisonOf != null) n += garrison(S, B[k].garrisonOf);
    return n;
  }
  function kill(S, B, k, n) {
    let left = n;
    for (const id of B[k].armies) { const A = S.armies.find(q => q.id === id); if (!A || left <= 0) continue; const d = Math.min(A.soldiers, left); A.soldiers -= d; left -= d; H(S, A.house).strength -= d; }
    if (left > 0 && B[k].garrisonOf != null) { const g = H(S, B[k].garrisonOf); const d = Math.min(garrison(S, g.id), left); g.strength -= d; left -= d; }
    B[k].dead += n - left;
  }
  function stepBattle(S, B) {
    B.turn++;
    const na = sideAlive(S, B, 'a'), nb = sideAlive(S, B, 'b');
    const edge = k => B[k].garrisonOf != null ? S.P.wallEdge : 1;
    const hits = (n, k) => { const line = Math.min(n, 16); let h = 0; for (let i = 0; i < line; i++) if (S.rng() < S.P.killRate * edge(k)) h++; return h; };
    const ka = Math.min(nb, hits(na, 'a')), kb = Math.min(na, hits(nb, 'b'));
    kill(S, B, 'b', ka); kill(S, B, 'a', kb);
    for (const [k, lost] of [['a', kb], ['b', ka]]) {
      B[k].morale -= lost * S.P.moraleLoss * 30 / Math.max(30, B[k].start);
      if (lost === 0 && B[k].morale < 100) B[k].morale += 1;
      for (const id of B[k].armies) { const A = S.armies.find(q => q.id === id); if (A && A.captain && !B[k].captainFell.has(id) && S.rng() < S.P.captainFall) { B[k].captainFell.add(id); B[k].morale -= 70; } }
    }
    const aB = B.a.morale <= 0 || sideAlive(S, B, 'a') <= 0, bB = B.b.morale <= 0 || sideAlive(S, B, 'b') <= 0;
    if (aB || bB) return endBattle(S, B, aB && (!bB || B.a.morale <= B.b.morale) ? 'a' : 'b');
    if (B.turn >= 200) return endBattle(S, B, sideAlive(S, B, 'a') * Math.max(1, B.a.morale) >= sideAlive(S, B, 'b') * Math.max(1, B.b.morale) ? 'b' : 'a');
  }
  function endBattle(S, B, loserK) {
    const winK = loserK === 'a' ? 'b' : 'a';
    S.battles = S.battles.filter(b => b !== B);
    const armies = k => B[k].armies.map(id => S.armies.find(q => q.id === id)).filter(Boolean);
    const houseOf = k => { const a = armies(k)[0]; return a ? side(a) : B[k].garrisonOf; };
    const winner = houseOf(winK), loser = houseOf(loserK);
    const total = B.a.start + B.b.start, skirmish = total < 20;
    const fates = [];
    for (const k of ['a', 'b']) for (const A of armies(k)) {
      if (!A.captain) continue;
      let fate = null;
      if (B[k].captainFell.has(A.id)) fate = 'slain';
      else if (k === loserK) { const r = S.rng(); fate = r < 0.3 ? 'slain' : r < 0.55 ? 'taken' : null; }
      if (fate) { const h = H(S, A.house); (fate === 'slain' ? h.deadFigures : h.hostages).add(A.captain.id); fates.push({ captain: A.captain.name, house: h.name, fate }); A.captain = null; }
    }
    for (const id of [winner, loser]) { const h = H(S, id); if (h) h.strength = Math.max(5, h.strength); }
    if (H(S, winner)) H(S, winner).prestige += skirmish ? 1 : 2;
    const chron = fates.length > 0;
    deed(S, { kind: 'BATTLE', winner, loser, deadW: B[winK].dead, deadL: B[loserK].dead, fates, x: B.x, y: B.y, seat: B.seat, skirmish, chronicled: chron });
    S.stats.battles.push({ t: S.clock, x: B.x, y: B.y, size: total, turns: B.turn });
    note(S, `${skirmish ? 'Skirmish' : 'Battle'} at (${B.x},${B.y}) ends after ${B.turn} turns: ${hname(S, winner)} holds the field (${B[winK].dead} dead), ${hname(S, loser)} routs (${B[loserK].dead} dead).` + fates.map(f => ` ${f.captain} of ${f.house} is ${f.fate === 'slain' ? 'slain' : 'taken hostage'}.`).join(''), chron);
    for (const A of armies(winK)) { A.battle = null; A.morale = Math.min(100, A.morale + 30); }
    for (const A of armies(loserK)) {
      A.battle = null;
      if (A.supply || A.forage) { A.gone = true; }
      else if (A.soldiers < S.P.minArmy && !A.raid) { A.gone = true; const wr = S.wars.find(w => w.id === A.war); if (wr) wr.broken = (wr.broken || new Set()).add(A.house); note(S, `The host of ${capName(A)} is broken; its ${A.soldiers} survivors straggle home.`); }
      else { A.morale = 30; if (A.raid) { A.gone = true; } else goHome(S, A, 'recover'); }
    }
    if (B.seat && loserK === 'b') seize(S, winner, loser);
    for (const A of armies(winK)) if (!A.gone) decide(S, A);
  }
  function seize(S, wid, lid) {
    const w = H(S, wid), l = H(S, lid), war = activeWars(S).find(x => (x.a === wid && x.d === lid) || (x.a === lid && x.d === wid));
    if (!war || l.kind === 'castle') return;
    war.active = false; war.end = S.season;
    let text;
    if (l.kind === 'great' && w.kind !== 'great' && w.kind !== 'castle') {
      [w.gx, w.gy, l.gx, l.gy] = [l.gx, l.gy, w.gx, w.gy]; w.kind = 'great'; l.kind = 'lesser'; w.liege = -1; l.strength = Math.min(l.strength, 15); w.prestige += 5;
      text = `${w.name} takes the gash of ${l.name}.`; deed(S, { kind: 'SEAT_SEIZED', winner: wid, loser: lid, chronicled: true });
    } else if (l.kind !== 'great') { l.liege = wid; text = `${l.name} bends the knee to ${w.name}.`; deed(S, { kind: 'SWORE', winner: wid, loser: lid, chronicled: true }); }
    else { text = `${w.name} and ${l.name} make peace after the assault.`; deed(S, { kind: 'PEACE', winner: wid, loser: lid, chronicled: true }); }
    S.stats.seizures++; note(S, 'The seat falls. ' + text, true);
    S.stats.wars.push({ name: warName(S, war), seasons: S.season - war.start, how: 'seat fell', bothStand: true });
    for (const A of S.armies) if (A.war === war.id) decide(S, A);
  }

  // ---------- the Season (history keeps declarations, peace; #321) ----------
  function seasonTick(S) {
    S.season++;
    for (const h of Object.values(S.houses)) h.strength = Math.min(h.cap, h.strength + S.P.recovery);
    for (const A of S.armies.filter(a => a.raid && S.season >= a.untilSeason)) { A.gone = true; note(S, `Raiders of ${hname(S, A.house)} go home.`); }
    for (const w of activeWars(S)) {
      const a = H(S, w.a), d = H(S, w.d), age = S.season - w.start;
      const spent = h => !S.armies.some(x => x.house === h.id && !x.raid && !x.supply && !x.forage) && garrison(S, h.id) - Math.ceil(h.strength * S.P.garrisonShare) < S.P.minArmy;
      let why = null;
      if (age > 16 && S.rng() < 0.05) why = 'an old war, ended';
      else if (a.strength < 30 && d.strength < 30 && S.rng() < 0.15) why = 'both houses exhausted';
      else if (w.s0 && a.strength < 0.5 * w.s0[a.id] && spent(a)) why = a.name + ' sues for peace, beaten';
      else if (w.s0 && d.strength < 0.5 * w.s0[d.id] && spent(d)) why = d.name + ' sues for peace, beaten';
      if (why) {
        w.active = false; w.end = S.season;
        deed(S, { kind: 'PEACE', winner: w.a, loser: w.d, why, chronicled: true });
        note(S, `Peace: ${warName(S, w)} (${why}).`, true);
        S.stats.wars.push({ name: warName(S, w), seasons: age, how: why, bothStand: a.strength > 5 && d.strength > 5 });
      }
    }
    // declarations: the free house with the heaviest grudge (a sketch of HistorySimulator.decide)
    for (const h of Object.values(S.houses)) {
      if (!h.alive || h.liege >= 0 || h.kind === 'castle' || warsOf(S, h.id).length || h.strength < 40) continue;
      const g = S.grudges.filter(x => x.h === h.id && H(S, x.a) && H(S, x.a).alive && !S.allies.has(key(h.id, x.a))).sort((p, q) => q.w - p.w)[0];
      if (g && g.w > 1 && S.rng() < S.P.declareChance) {
        S.wars.push({ id: S.nextId++, a: h.id, d: g.a, start: S.season, active: true, s0: { [h.id]: h.strength, [g.a]: H(S, g.a).strength } });
        deed(S, { kind: 'WAR_DECLARED', winner: h.id, loser: g.a, chronicled: true });
        note(S, `${h.name} declares war on ${hname(S, g.a)}.`, true);
      } else if (g && g.w > 1 && S.rng() < S.P.peaceRaidChance && garrison(S, h.id) - Math.ceil(h.strength * S.P.garrisonShare) >= S.P.raidSize) {
        sendRaid(S, null, H(S, g.a), null, { house: h.id });
      }
    }
    while (S.P.minWars && activeWars(S).length < S.P.minWars) {
      const free = Object.values(S.houses).filter(h => h.alive && h.liege < 0 && warsOf(S, h.id).length < 2 && h.kind !== 'castle');
      const ok = (a, d) => d && d.alive && d.id !== a.id && d.liege !== a.id && !atWar(S, a.id, d.id) && !S.allies.has(key(a.id, d.id));
      let best = null;
      for (const a of free) for (const g of S.grudges.filter(g => g.h === a.id)) if (ok(a, H(S, g.a)) && (!best || g.w > best.g.w)) best = { a, d: H(S, g.a), g };
      if (!best) { const a = free.sort((p, q) => q.strength - p.strength)[0]; const d = a && Object.values(S.houses).filter(h => ok(a, h)).sort((p, q) => p.strength - q.strength)[0]; if (!d) break; best = { a, d }; }
      S.wars.push({ id: S.nextId++, a: best.a.id, d: best.d.id, start: S.season, active: true, s0: { [best.a.id]: best.a.strength, [best.d.id]: best.d.strength } });
      note(S, `${best.a.name} declares war on ${best.d.name}${best.g ? ' (an old grudge)' : ''}.`, true);
      for (const h of [best.a, best.d]) muster(S, h);
    }
    while (S.P.warsTarget && activeWars(S).length < S.P.warsTarget) {
      const free = Object.values(S.houses).filter(h => h.alive && h.liege < 0 && warsOf(S, h.id).length < 2 && h.kind !== 'castle');
      if (free.length < 2) break;
      const a = free[Math.floor(S.rng() * free.length)];
      const t = Object.values(S.houses).filter(h => h.alive && h.id !== a.id && h.liege !== a.id && !atWar(S, a.id, h.id) && !S.allies.has(key(a.id, h.id)));
      if (!t.length) break;
      const d = t[Math.floor(S.rng() * t.length)];
      S.wars.push({ id: S.nextId++, a: a.id, d: d.id, start: S.season, active: true, s0: { [a.id]: a.strength, [d.id]: d.strength } });
      note(S, `${a.name} declares war on ${d.name}.`, true);
    }
    for (const h of Object.values(S.houses)) muster(S, h);
    for (const A of S.armies) if (!A.battle && !A.raid) decide(S, A);
  }

  // ---------- the player, for pacing (W4) ----------
  function playerTick(S) {
    const p = S.player;
    if (p.asleep > 0) { p.asleep--; return; }
    if (p.underground) { p.expTurns++; }
    else {
      p.overland++; p.expTurns++;
      if (!p.route.length) {
        const road = S.W.roads[p.roadIx++ % S.W.roads.length];
        const out = path(S, p.x, p.y, road.end[0], road.end[1], true) || [];
        p.route = out.concat(out.slice(0, -1).reverse().concat([[0, 0]]).filter(Boolean)).slice(0, 400);
        if (!p.route.length) p.route = [[0, 0]];
      }
      if (++p.prog >= S.P.playerTurns) { p.prog = 0; [p.x, p.y] = p.route.shift(); }
      observe(S);
    }
    if (p.expTurns >= S.P.expeditionTurns) { p.expTurns = 0; S.stats.expeditions++; S.stats.battlesThisExp.push(p.battlesExp || 0); p.battlesExp = 0; if (S.autoSleep) sleep(S); }
  }
  function observe(S) {
    const p = S.player, st = S.stats;
    st.overland++;
    const near = S.armies.filter(A => cheb(A.x, A.y, p.x, p.y) <= S.P.seenRadius);
    const now = new Set(near.map(A => A.id));
    for (const A of near) if (!st.inView.has(A.id)) { st.encounters.push({ t: S.clock, what: describe(S, A), house: hname(S, A.house) }); }
    st.inView = now;
    for (const B of S.battles) {
      if (cheb(B.x, B.y, p.x, p.y) <= 1 && !st.battlesSeen.has(B.id)) { st.battlesSeen.add(B.id); if (B.a.start + B.b.start >= 30) { st.pitched++; p.battlesExp = (p.battlesExp || 0) + 1; } st.encounters.push({ t: S.clock, what: (B.a.start + B.b.start >= 30 ? 'a pitched battle' : 'a skirmish') + ' under way', house: '' }); }
    }
    if (S.battles.some(B => cheb(B.x, B.y, p.x, p.y) <= S.P.sight)) st.earshotTurns++;
  }
  function describe(S, A) {
    if (A.battle) return 'a battle';
    if (A.raid) return 'raiders';
    if (A.supply) return 'a supply column';
    if (A.forage) return 'foragers';
    if (A.levyOf != null) return 'a levy on the march';
    return A.path.length ? 'a column on the march' : 'a war camp';
  }
  function sleep(S) { for (let i = 0; i < S.P.sleepTurns; i++) { S.player.asleep = 1; tick(S); } S.player.asleep = 0; S.stats.sleeps++; }
  function newStats() { return { battles: [], wars: [], encounters: [], inView: new Set(), battlesSeen: new Set(), pitched: 0, overland: 0, earshotTurns: 0, expeditions: 0, battlesThisExp: [], sleeps: 0, seizures: 0 }; }

  function summary(S) {
    const st = S.stats, enc = st.encounters.filter(e => e.what !== 'a battle');
    return {
      seasons: S.season, overlandTurns: st.overland,
      encounters: enc.length, turnsPerEncounter: enc.length ? Math.round(st.overland / enc.length) : null,
      pitchedPerExpedition: st.expeditions ? +(st.pitched / st.expeditions).toFixed(2) : null,
      earshotShare: st.overland ? +(st.earshotTurns / st.overland).toFixed(2) : 0,
      battles: st.battles.length, seizures: st.seizures,
      warsEnded: st.wars, warsOngoing: activeWars(S).map(w => ({ name: warName(S, w), seasons: S.season - w.start })),
      armies: S.armies.length,
    };
  }
  function runBatch(worlds, params, seasons) {
    return worlds.map(w => {
      const S = create(w, params, w.seed); S.autoSleep = true;
      while (S.season < seasons) tick(S);
      return Object.assign({ seed: w.seed }, summary(S));
    });
  }
  return { DEFAULTS, TRAITS, create, tick, sleep, summary, runBatch, garrison, fielded, activeWars, hname, capName, describe, passable, biome };
})();
if (typeof module !== 'undefined') module.exports = ArmySim;
