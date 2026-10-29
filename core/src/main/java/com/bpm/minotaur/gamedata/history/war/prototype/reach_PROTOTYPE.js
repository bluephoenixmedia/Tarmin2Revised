const fs=require('fs');eval(fs.readFileSync(__dirname+'/worlds_PROTOTYPE.js','utf8').replace('const WORLDS','global.WORLDS'));
const Sim=require('./army_sim_PROTOTYPE.js');const params=JSON.parse(process.argv[2]||'{}');
let turns=0,any=0,w6=0,w15=0,pitchedPerSeason=0,battles=0,seasons=0;
for(const w of WORLDS){const S=Sim.create(w,params,w.seed);S.autoSleep=true;while(S.season<40){Sim.tick(S);turns++;
 if(S.battles.length)any++;const d=Math.min(99,...S.battles.map(B=>Math.max(Math.abs(B.x-S.player.x),Math.abs(B.y-S.player.y))));if(d<=6)w6++;if(d<=15)w15++;}
 battles+=S.stats.battles.length;pitchedPerSeason+=S.stats.battles.filter(b=>b.size>=30).length;seasons+=S.season;}
console.log(`a battle under way somewhere ${(100*any/turns).toFixed(0)}% of turns | within earshot (6) ${(100*w6/turns).toFixed(1)}% | within 15 chunks ${(100*w15/turns).toFixed(1)}% | battles per Season ${(battles/seasons).toFixed(2)}, pitched per Season ${(pitchedPerSeason/seasons).toFixed(2)}`);
