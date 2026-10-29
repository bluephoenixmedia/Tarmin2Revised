const fs=require('fs');eval(fs.readFileSync(__dirname+'/worlds_PROTOTYPE.js','utf8').replace('const WORLDS','global.WORLDS'));
const Sim=require('./army_sim_PROTOTYPE.js');const params=JSON.parse(process.argv[2]||'{}');
let ov=0,enc=0,pit=0,exp=0,lens=[],how={},armies=0,n=0,bt=[];
for(const w of WORLDS){const S=Sim.create(w,params,w.seed);S.autoSleep=true;while(S.season<40){Sim.tick(S);armies+=S.armies.length;n++;}
 const r=Sim.summary(S);ov+=r.overlandTurns;enc+=r.encounters;pit+=S.stats.pitched;exp+=S.stats.expeditions;bt=bt.concat(S.stats.battles.map(b=>b.turns));
 for(const x of r.warsEnded){lens.push(x.seasons);const k=x.how.replace(/^House \S+ |^the Antlered Host /,'');how[k]=(how[k]||0)+1;}}
lens.sort((a,b)=>a-b);bt.sort((a,b)=>a-b);
console.log(`turns/encounter ${(ov/Math.max(1,enc)).toFixed(0)} | pitched/expedition ${(pit/exp).toFixed(2)} | armies in field ${(armies/n).toFixed(1)} | battle turns median ${bt[bt.length>>1]} | wars ended ${lens.length}, length median ${lens[lens.length>>1]} p25 ${lens[lens.length>>2]} p75 ${lens[(lens.length*3)>>2]} | ${JSON.stringify(how)}`);
