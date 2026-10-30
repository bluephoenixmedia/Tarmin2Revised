# Implementation Plan: The Living War

> **Status:** Shipped as a scheduled war; being rebuilt as a true simulation. **Section 1b supersedes parts of Section 1.** Builds on the shipped Houses of the Maze
> (`Houses of the Maze - How It Works.md`, `Implementation Plan_ The Houses of the Maze.md`).
> **Source:** design grilling session, 2026-10-10 (decisions Q1-Q25, recorded below as W1-W40).
> **Branching:** one branch per phase, `feat/living-war-<n>-<slug>` from `develop`, merged back to
> `develop` after the full suite passes and a play-test. Never `master`.

The war of the houses is no longer a story the player hears about after the fact. It is happening
**all around them, all the time**. Horns carry across the overland, smoke stands on the horizon,
columns of soldiers march past with banners and torches, and the stone underfoot shakes when a gash's
house is fighting. The player is a small cog in a gigantic war. They can **join it**, earning a
house's favour, swearing to it, and fighting its contracts, or **use it**, slipping through its battles
as a rogue and sneaking into Castle Tarmin while a siege has the defenders busy.

---

## 1. Decisions (single source of truth)

Every task traces to these. A task that contradicts one is a defect in the task.

### The two layers

| # | Decision |
| :-- | :-- |
| W1 | *Amended by A1.* **Two layers of war.** The history (`HistorySimulator`) decides who is at war, who wins each season, and who holds what. On the surface, a second layer of **encounters** (skirmishes, columns, raids, camps, aftermath, sieges) makes the war visible and audible. Encounters write no history unless the player takes part (W12). |
| W2 | *Superseded by A1.* **Encounter kinds:** skirmish (two war-bands of 6-10 clash until one routs, ~30-60 turns); marching column (8-15 soldiers in file with banners and torches, crossing chunks toward a front, ~20 turns to pass); raiding party (4-6 soldiers burning a holdfast, shelter road or town entrance); war camp (tents, fires, sentries and a captain near a front, for as long as the war lasts); pitched battle (the existing `BattleDirector`); aftermath (bodies, crows, burnt ground, broken banners and loot, lasting 3 sleeps); siege (W33). |
| W3 | *Superseded by A1.* **Encounters are scheduled, not simulated.** Like fronts, they are a pure function of the world seed, the war clock and the chunk. They are never saved; a reload sees the same war. Only what the player changes is saved: aftermath fields, favour, oath, contracts, destroyed columns and camps, and siege state. |
| W4 | *Superseded by A8.* **Frequency targets (surface, tuning constants):** while any war is active, some war is always within earshot (3-6 chunks); a **visible encounter** (skirmish, column or raid) about every **80-120 overland turns**; about **one pitched battle per expedition**. |
| W5 | *Superseded by A1, A2.* **Bias toward the player.** The scheduler picks *which chunk* the next encounter crosses so it falls near the player. That is how frequency targets are met without moving the history. |
| W6 | *Extended by A9.* **A war at the start.** If prehistory ends with no active war, the most aggrieved house declares one when live play begins. This is a deterministic deed, so it replays. |
| W7 | *Amended by A9.* **Border raids in peacetime.** Houses that bear a grudge but are not at war still send occasional raiding parties, so peace is never total silence. |
| W8 | *Dropped by A2.* **The first skirmish is guaranteed.** On the first expedition, within about **150 turns** of leaving the shelter, a skirmish crosses a chunk next to the player: horns first, then it comes into view. It teaches the player that they can fight or slip past. |

### Hearing and seeing the war

| # | Decision |
| :-- | :-- |
| W9 | *Sources replaced by A53-A55.* **Distant battle sound** by chunk distance, panned toward the fight (left/right): **adjacent**: clash of steel, screams, arrow volleys, beast roars, shouted orders (surface); dull thuds and dust falling from the ceiling (underground). **2-3 chunks**: horns and drums, a low roar of many voices (surface); a rumble through the stone and torches flickering (underground). **4-6 chunks**: a lone distant horn now and then, drums at the edge of hearing (surface); underground, silence, *unless the gash's own house is at war*, in which case its interior shakes. |
| W10 | *Sources replaced by A54, A57.* **Seeing the war (surface), all seven, built in this order:** (1) smoke columns on the skybox horizon toward each active front, black and lit orange from below at night; (6) aftermath fields that stay scarred for 3 sleeps; (5) marching columns; (7) war camps from the Goblin War Camp and camp-scatter packs; (4) arrow volleys arcing in the sky over an adjacent chunk; (3) signal beacons lit in a chain toward the player when a house musters; (2) fire glow on the horizon at night. |
| W11 | **Sound sourcing.** The battle bed is built from existing pieces (`war_horn`, `war_drums`, `arrow_1-4`, metal hits, roars, thunder, the ABYSS loops for underground rumble), overlapped and randomised, then filtered and attenuated by distance. Gaps (a many-voices battle roar loop, a distant clash loop, crows, marching feet and armour jingle, camp ambience) get stand-ins and an AI-generation prompt each. The user is also sourcing new audio. |

### The player in the war

| # | Decision |
| :-- | :-- |
| W12 | *Superseded by A17, A26, A40.* **A skirmish the player joins** nudges the history: winner +2 strength, loser -2, plus favour for the player. It enters the chronicle **only when a named captain dies** in it. A pitched battle the player stays in is still a full `BATTLE_WITNESSED` battle. |
| W13 | **Noise masking.** In a battle's chunk and its neighbours, monsters' hearing range is cut sharply: the din covers the player. Sight is unchanged. |
| W14 | **Busy soldiers.** Soldiers in combat target enemy soldiers first, and turn on the player only if struck or if the player comes within 1 tile. |
| W15 | *Now a battle's chunk, not a front's.* **Slipping the lines.** A player who crosses a front's chunk during a battle and never attacks does not trigger the "lines close" lock. |
| W16 | **The polymorph disguise.** If the player's random polymorph form is a monster type on a house's doctrine roster, soldiers of **every house fielding that type** take them for one of their own. They ignore the player unless struck, or until the player has been adjacent for 3 turns. **Those houses' enemies see an enemy soldier** and may attack. The disguise ends with the form. The Antlered Host's roster (Minotaur, Orc, Hobgoblin, Gargoyle, Ogre) makes castle defenders take the player for their own. |
| W17 | **Tabard disguise is deferred** until the tabard exists (W24). The polymorph disguise (W16) is the disguise for now. |

### Favour and the oath

| # | Decision |
| :-- | :-- |
| W18 | *Hunters are Hunting Parties (A12).* **Favour per house, -100 to +100.** Enemy ≤ -50: its soldiers hunt the player, and bounty hunters come. Distrusted, -49 to -10: hostile. Unknown, -9 to +24: hostile on sight, as today. Tolerated, +25 to +59: its soldiers ignore the player unless struck. Friend, ≥ +60: soldiers salute, its camps let the player rest and trade, and an oath is offered. |
| W19 | *Battle Favour replaced by A40.* **Favour is earned** by killing a house's enemies near its soldiers, winning skirmishes on its side, and returning its signets. **It is lost** by killing its soldiers, aiding its enemies, and killing its heralds. Favour with one house costs a little with that house's enemies (half, reversed, the same spillover rule as town standing). |
| W20 | *Offered by any halted host's captain (A39).* **The oath** is offered by a war-camp captain at Friend. Taking it gives: the house's tabard (W24); contracts (W25); its camps as safe rest points; its soldiers fighting beside the player. Its enemies treat the player as Enemy on sight. |
| W21 | *Extended by A41.* **Breaking the oath** (attacking the sworn house, or swearing to another) is a chronicled **BETRAYAL** naming the player. Every house's favour drops, and the former house sends hunters. |
| W22 | **The sworn house's gash court still fights for its seal**, unless the player completes the **"the lord grants you the seal"** contract chain (W26). |
| W23 | **This plan supersedes Houses D10 and invariant 4** ("violence only; the Maze is hostile"). Favour and the oath are a path to an allied house. Every house still starts hostile (Unknown). |
| W24 | **The tabard** is a new worn chest item: one tabard sprite tinted with the doctrine's primary and secondary colours, plus a small sigil charge. It is drawn through the existing paper-doll overlay. **The doll and its overlay offsets are never edited** (UI overhaul rule). Check `docs/game_assets` first; if nothing fits, ship a stand-in and give the user a generation prompt. |
| W25 | *Kinds replaced by A37.* **Contracts:** hold a chunk; kill an enemy captain; escort a column; burn an enemy camp. Each one gives +10 to +20 favour and gold. **Every third contract** gives a **doctrine relic**: a weapon or armour in the house's style, rolled from the existing rare-variant/modifier pool as a fixed rare. |
| W26 | **The seal-grant chain:** three hard contracts at Friend or better while sworn, ending with the house lord handing the player its gash's seal. |

### Keeping up with the war

| # | Decision |
| :-- | :-- |
| W27 | *Layout replaced by A50.* **The War Table**: a new shelter prop beside the Archive Lectern, opening its own full-screen war map. It shows wars, fronts, each season's score, sieges, seats at risk, the player's favour with each house, and active contracts. |
| W28 | *Replaced by A46-A47.* **Battle reports on the expedition map:** fronts drawn as clashing banners, fresh battlefields marked, columns as arrows. |
| W29 | *Heralds are real units (A15).* **Heralds and couriers** walk the shelter roads between seats. Reading a herald's proclamation (or taking it from the body) gives the news early: it unlocks a fresh event. Killing a herald costs that house favour. |
| W30 | **The HUD war tally**: while the player is in a skirmish or battle chunk, two house banners with a morale tug-of-war bar. |
| W31 | **The Knell is not widened.** It stays rare. A siege beginning and ending tolls it (W33); nothing else new does. |

### Underground

| # | Decision |
| :-- | :-- |
| W32 | *Amended by A56.* **This plan supersedes Houses D13** for two cases only. (1) **Gash interiors** (strata 2-3 under a seal site): while the holding house is at war, enemy **raiding parties** come down into the gash, and its court is **thinner** because sworn swords leave to fight. (2) **Towns** (strata 2-4): occasional **raids on the town's gate** by a house, which the guards and the player can drive off for standing and favour. **No columns or skirmishes underground**: the strata stay quieter and deeper, where the war is heard as rumble (W9). |

### The siege of Castle Tarmin

| # | Decision |
| :-- | :-- |
| W33 | *Roll replaced by A35.* **SIEGE_LAID** is a new history event. A house at war with the Antlered Host, with strength ≥ 70, may besiege the castle instead of fighting at a front. The siege lasts **3-6 seasons**, or until the attacker's strength breaks (**SIEGE_BROKEN** / **SIEGE_LIFTED**). The Knell tolls when it begins and ends. |
| W34 | *Sources amended by A35, A54, A55.* **The look and sound of a siege:** siege camps ring the castle chunk; fires and smoke columns rise around the castle silhouette; siege fire (flaming projectiles) arcs onto the walls; red lightning when Tarmin answers; horns and drums without end. |
| W35 | **The breach.** While a siege lasts, a breach opens in the castle wall. Entering by the breach needs **1 seal** if the player is Friend or sworn with the besieging house, and **2 seals** otherwise. This plan supersedes Houses invariant 8 for the breach only; the gate still needs 3. |
| W36 | **Beyond the breach** (castle interior not yet built): a **stub**, "the castle's halls": one themed chunk where both sides fight in the halls and the defenders are thinned, ending at a sealed inner door ("the throne lies beyond"). The gate leads to the same place once it opens. The real interior gets its own plan. |
| W37 | *Amended by A37, A42.* **Sworn players can join the siege** as an assault contract, which also needs 1 seal. |

### Process

| # | Decision |
| :-- | :-- |
| W38 | **Build it all, in five phases** (Section 4), each playable on its own. Phase 1 alone delivers "the war is all around me". |
| W39 | **Rising through the ranks** of a house is a separate plan, written after its own grill: the player may abandon the main story and climb a house's ranks, ending in a duel with the current lord to take the house. It builds on W18-W26. |
| W40 | **Polymorph remains numbers-only** apart from W16. The richer dreamsmoke-style system stays deferred. |

---

## 1b. Decisions: Armies, Arms and the Bivouac (revision of 2026-10-30)

Gathered from the map [The Living War as a simulation](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/317). Each decision's reasoning is on the ticket named at its group's heading. **Where this block and Section 1 disagree, this block wins.** Where tickets disagreed, the later and measured one is recorded here. Numbers marked *tune* are starting values, to re-measure at build time. Vocabulary is in `CONTEXT.md`.

**The rule behind all of it:** the war is a true simulation, never staged around the player. The player changes the outcome of the fights they are in, never a lord's or captain's decisions. Regular chunk spawning is untouched; armies are a separate layer that interacts with it, and monsters fighting monsters is common.

### Armies ([#318](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/318), [#320](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/320), [#321](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/321), [#322](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/322))

| # | Decision |
| :-- | :-- |
| A1 | **Armies are the only source of war outcomes in live play.** Each is a persistent unit on the overland chunk grid: house, war, kind (host, levy, raid, supply, foragers, hunting party, herald), captain, soldiers, morale, objective. Surface only: Courts stay in the strata, and the central maze is closed to armies. Prehistory keeps the abstract Season roll. **Supersedes W1's second layer, W2, W3 and W5.** |
| A2 | **Nothing is placed for the player.** W8 is dropped; an early skirmish happens only if the simulation brings one. No army reads the player's position, with one exception (A12). |
| A3 | **Strength is soldiers,** 1 point to 1 soldier: armies in the field plus the garrison at home. Only the dead lower it; mustering and disbanding move soldiers. A house keeps at least 1/4 as garrison. Armies muster at **15** soldiers, **one per 20 strength** (*tune*). A house that can't field 15 fields none. Recovery is **5 strength per Season** (*tune*). |
| A4 | **Six objectives:** march on a seat, hunt, raid, guard, recover, follow. Re-scored only on events (arriving, a battle ending, morale or strength crossing a threshold, a seat falling, an enemy coming into sight, a Season tick), weighing distance, relative strength and the captain's or lord's traits: AMBITIOUS marches on seats; WRATHFUL hunts and recovers late; CRAVEN guards and recovers early; PATIENT waits for a margin; CUNNING raids and avoids even fights; CRUEL raids and leaves more aftermath; ZEALOT goes for its deepest grudge; HONOURABLE never raids. Each army belongs to one war; when it ends, the army is reassigned or disbanded. |
| A5 | **Knowing and marching.** An army knows every seat, and enemy armies within **6 chunks** of itself or its allies. Marching takes **15 turns a chunk on a road corridor, 20 off it, 26 in Tundra or Blight** (*tune*). |
| A6 | **Captains.** Every army is led by a named figure: the lord (only if their traits fit), the heir or a sworn sword. On a rout the captain is slain (real death, SLAIN_KIN grudge), taken (HOSTAGE_TAKEN, HOSTAGE_HELD grudge) or escapes. This replaces the Season's 5% / 12% / 15% rolls. |
| A7 | **Vassals and allies.** At a liege's war each vassal musters one levy of about 1/3 of its strength, following the liege's nearest army under its own Arms. The 30% power bonus goes. Allies don't march to help, but an allied army in or next to a battle's chunk joins it. |
| A8 | **Pacing, replacing W4:** an encounter about every **400-700 overland turns** on the roads; about **one pitched battle per Season somewhere**; one at the player's side by chance about 1 in 20 expeditions, so a player sees battle by following a host. Wars last **4-16 Seasons** and end with both houses usually standing. |
| A9 | **War pressure, extending W6:** with fewer than **2** wars running (*tune*), the most aggrieved free house declares; failing a grudge, the strongest free house declares on the weakest it may attack. **Peacetime raids (W7) stay,** now as real small parties that go home. |
| A10 | **Logistics are real units** that can be met, fought and cut off: **supply columns** from the seat to a host (checked about every 60 turns, at least 3 soldiers) and **foragers** (4 soldiers, out 1-2 chunks and back, about every 80 turns). |
| A11 | **Raids** are parties of **8** (*tune*) that do damage and take nothing. A raid on a road may camp outside a road shelter's walls, never inside, only on roads leading to a seat the raiders are at war with, until the next Season or until beaten. Beating them earns Favour with the house raided. The Shelter at (0,0) is untouched. |
| A12 | **Hunting Parties are the only part of the war that aims at the player:** a raid party sent by a house that counts them an Enemy, toward where its armies last saw them. |
| A13 | **Holdfasts stand on open land** ([#330](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/330)). |
| A14 | **Shifting.** Battles under way resolve instantly by the numbers; each army is placed anew at the same fraction of its path on the reborn map, keeping its strength, captain and objective. |
| A15 | **Heralds are real units in the roster.** A house sends a lone herald along the roads between seats when it has news: a declaration, a seat's fall, a peace. It can be met, read or killed like anything else on the road (W29's reading and Favour stay). |

### Battles and the history ([#321](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/321), [#323](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/323), [#324](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/324), [ADR 0006](../adr/0006-armies-are-a-snapshot-beside-the-replayed-history.md))

| # | Decision |
| :-- | :-- |
| A16 | **The history never reads the armies.** Every effect goes through a deed: `ARMY_BATTLE`, `SUED_FOR_PEACE`, `ARMY_ATTRITION`, `ARMY_BEAST`, `SEAT_INVESTED`, `SIEGE_RAISED`; Castle Tarmin keeps `SIEGE_LAID`, `SIEGE_BROKEN`, `SIEGE_LIFTED`. `SKIRMISH` and `BATTLE_WITNESSED` stay readable and are no longer written. |
| A17 | **`ARMY_BATTLE`** carries winner, loser, the dead per side (never below a house's floor of 5), each captain's fate and who killed them (player, enemy, monster), whether at a seat, the chunk, whom the player fought for, and whether the player **turned** it (A40). Prestige +2, or +1 for a skirmish (under about 20 soldiers in all). Chronicled only when a captain fell or was taken, a seat changed hands, or the player turned it. |
| A18 | **Armies are a snapshot.** `WarRoster` in `HistorySaveData` holds armies, battles under way and each army's trail; it replaces `EncounterLedger`'s anchors and first-skirmish flags, and aftermath dressings stay. Paths are recomputed on load. The history wins on load: extinct houses' armies drop, ended wars' armies go home, dead or hostage captains are replaced, and armies are scaled to fit strength. A save from before armies musters fresh. |
| A19 | **A reload replays the same future.** Army randomness comes from the history seed, the war clock and the army id, never a saved generator. Round-trip tests replay a run with off-screen battles. |
| A20 | **One battle state, watched or not.** Starting values: lethality **0.012** per soldier in the line per turn (battles of 30-150 turns, *tune*). Declining a battle no longer exists. |
| A21 | **Materialising.** Only the lines appear, up to **16 a side**; the rest is a reserve marching in from that side's edge, 3 a turn. A battle that starts with the player present gives the horns warning; walking into one under way gives none. Gate guards hold as now. With several houses on a side, the line is in proportion, each soldier with its own house's roster and Arms, one captain per army, morale shared. |
| A22 | **The map is the truth while watched.** Every soldier on the field counts, whoever kills it, and native monsters join through the faction matrix. **Acceptance test:** averaged over many fights, on-map and off-screen kill rates agree within about 25%. |
| A23 | **Collapsing.** On leaving, survivors plus reserve become the number again; morale and captains' fates carry; wounds are forgotten. Soldiers never cross a chunk edge. Corpses and dropped arms become the aftermath, left by every battle watched or not, for 3 sleeps. |
| A24 | **Spoils.** A player present at the rout gets the full Spoils. One who arrives at the aftermath finds only picked-over arms. |
| A25 | **Peace.** A house sues (`SUED_FOR_PEACE`, written by the army layer) once it has lost half the strength it had when the war began and can't muster another host. The Season's old rules stay: wars over 16 Seasons at 5%, and mutual exhaustion below 30 at 15%. |
| A26 | **Choosing a side on arrival.** Entering the chunk of a battle or siege under way offers: help the attackers, help the defenders, or keep away. A sworn player is offered only their liege's side; fighting the other side breaks the oath. An unsworn player counts as the chosen side's ally until the fight ends. **Amends W12.** |

### Armies against the world ([#325](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/325))

| # | Decision |
| :-- | :-- |
| A27 | **Attrition on the march:** about 1 soldier per 10 chunks in low EDL, rising to about 1 per 3 in the far bands; the Blight triples it, except for the Antlered Host, which loses nothing there. One `ARMY_ATTRITION` deed per house per Season, never chronicled. |
| A28 | **Megabeasts.** The Season's beast raid becomes physical: the beast goes for the house's nearest army or its seat, as one battle state. A beast that followed the player to the surface fights any army it meets. Wounds go to its saved hit points; a slain beast is credited to the house. `ARMY_BEAST`, chronicled only if the beast or a captain dies. |
| A29 | **Trails thin the natives.** The roster keeps the chunks each army crossed in the last Season. Unvisited trail chunks generate with about half their natives, plus a few corpses; saved ones lose about half on next load, chosen deterministically, with corpses in their place. |

### Sieges ([#331](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/331), [#332](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/332))

| # | Decision |
| :-- | :-- |
| A30 | **Seats are invested first.** An army reaching an enemy seat camps around it (`SEAT_INVESTED`, chronicled). The garrison starves about 1 soldier per 100 turns, into `ARMY_ATTRITION`. The walls edge starts at **4** (*tune*) and falls about 0.5 a Season to 1.5. The defender's hosts are strongly drawn to relieve. A siege ending without a fall writes `SIEGE_RAISED`. |
| A31 | **Siege works.** While invested, the besiegers build **ladders** for any wall and a **ram** for a stone gate at a seal site; a palisade at a holdfast can be fired instead. Progress shows in the camp. |
| A32 | **The ring.** A walled ring around the chunk's centre: stone at a seal site, timber palisade at a holdfast, two gates. The garrison holds the ring; the camp is outside. Assets: Goblin War Camp and POLYGON pieces, checked against the pack inventory at build time. |
| A33 | **The assault.** Only the captain's traits start it, on numbers against worn walls (PATIENT later, WRATHFUL earlier). Two phases: **the walls** (ladders or the gate), then **the yard**, a last stand at the keep or court. Waves as A21. The seat falls when the yard is cleared, as `ARMY_BATTLE` at a seat, and today's seizure rules apply. |
| A34 | **Sallies.** A garrison whose traits favour it strikes the camp: a battle there, fought the same way. |
| A35 | **Castle Tarmin, replacing W33's roll.** A house at war with the Antlered Host, at strength 70 or more, may give a host the objective "besiege the castle". It camps in the existing ring 2 chunks out (`SIEGE_LAID`). **Broken** when that host routs, **lifted** when the war ends or after 3-6 Seasons. The breach (W35) exists while the host holds. The castle never falls to a house. |

### The player and their oath ([#332](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/332))

| # | Decision |
| :-- | :-- |
| A36 | **Following.** At a halted host of their liege the player can ask to Follow it, at its pace, never steering it, leaving at will. **"March on"** skips time like a sleep, keeping to the host chunk by chunk, stopping the moment the host meets anything. Sleep in its camp counts as a Bivouac with Alarm and Shield wards. Native Fodder leave the column alone; Soldiers and Heavies may not. In a battle the player stands in the host's line from the start. |
| A37 | **Contracts name a real thing in the roster, replacing W25's kinds:** break a named host; kill or take its captain; escort a liege's supply column or cut off an enemy's; break a war camp; relieve the liege's besieged seat; join the assault on an enemy seat; stand with a named host through its next battle; on Castle Tarmin, unchanged, enter the halls by the breach. The War Table shows the target's last known chunk, as far as the liege knows. Hard contracts and the seal chain (W26) stay. |
| A38 | **A contract's target disappearing** voids it: no penalty, no pay. The exception: a target falling in a battle the player fought in for their liege counts as done, whoever struck the blow. |
| A39 | **Contracts are given and paid** by the captain of any halted host of the liege (camped, besieging or idle) or at its seat. **The oath** is sworn the same way, at Friend, to any halted host's captain or at the house's seat. |
| A40 | **Favour from battles, replacing W19's flat +10.** Winning side: +5, plus up to +15 by the player's share of the enemy dead (full at a third), plus +10 for a captain slain or taken. Losing side, fought to the end: +3. Fled before the rout: nothing. Half, reversed, to the enemy as today. At a fifth or more of the enemy dead on a winning side, the deed records the player **turned** the battle. |
| A41 | **Breaking the oath mid-campaign.** Today's cost stays (W21). If Following, the host turns on the player at once. The betrayed house's nearest host sends a Hunting Party at once. An open contract and unpaid reward are forfeit. Swearing to the enemy mid-war is allowed, but the new liege holds the turncoat's Favour at Tolerated or below for a Season. |
| A42 | **Seats and the sworn.** A sworn player can stand with a besieging host ("wait in the siege" skips time until the assault, a relief or a sally, and wakes them). They can't start the assault; at Friend, with the works ready, they may be asked to take the first ladder, as a hard contract. Join-the-assault is done by fighting in it, paid at the hard rate if the seat falls. A sworn player can pass the ring into their liege's besieged seat; relief is done if the siege is raised, or the besiegers rout, while they are inside or with the relief. |

### Arms ([#326](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/326), [#327](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/327))

| # | Decision |
| :-- | :-- |
| A43 | **Arms draw the house's sigil,** plus a second field colour (from the same seven, contrasting) and a division (per pale, per bend, per chevron, quarterly, per fess), both chosen deterministically from the house. The written sigil grows to match, the old phrase staying a prefix: "an open mouth singing on bone white and tarnished gold, per pale". The doctrine is only the **border**. The charge is dark or light, whichever contrasts with both fields, with a thin halo. |
| A44 | **Art.** Shields, banners, divisions and colours are drawn in code. Only the **23 charges** are art; the prompts are on [#326](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/326). Ship stand-in silhouettes until they land. |
| A45 | **In the field.** An E shield over every soldier with a house within 3 tiles (about 32 / 24 / 16-18 px at 1 / 2 / 3 tiles), fading beyond. A captain carries their army's C banner at every distance. Revisit a simplified badge if 12 px proves unreadable. |
| A46 | **HUD and map.** Minimap dots take the house's two colours for every monster with a house, natives staying red. The battle bar shows both Arms, with shortened names never cut mid-word. A map marker is a 14 px shield with a heading arrow; selecting it shows the card and trail. |

### What the player knows, and how they learn it ([#328](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/328), [#333](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/333), [#334](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/334))

| # | Decision |
| :-- | :-- |
| A47 | **Seeing.** A host shows on the map only while in the player's chunk or next to it, then stays as **last known** with its heading, "last seen N sleeps ago", dimming, dropped after a Season. Nothing on the map is ever wrong; it only ages. A battle within earshot places a "sounds of battle" mark. |
| A48 | **A sighting tells** the house, the captain if their banner was seen, the heading and an estimated size: band (under 10), company (10-20), host (20-40), great host (40+). Never an exact count or the objective. |
| A49 | **Other sources.** The War Table each sleep gives each host's rough region and heading, as rumour-quality markers. Town rumours give a position up to a Season old, **at least one per visit** while a war runs. **Sieges are public and exact.** A sworn player sees their own house's hosts exactly, with objectives. Stepping on a trail says "tracks lead north-east, a few days old" and refreshes that host's marker. Knowledge is saved with map knowledge, not history. |
| A50 | **The War Table is Dispatches, replacing W27's layout.** Main column: AIMED AT YOU (hunter warnings, the contract), YOUR HOUSE (sworn only), SIEGES, NEAR THE SHELTER (about 3 days' march, nearest first), FARTHER OFF. Rail: THE WARS (two lines each), SEATS AT RISK, YOUR STANDING. Selecting a war swaps the news column for its page: cause, since when, score, front, hosts. **W** on a row sets the Expedition Map's waypoint. No map of its own. Asset: [prototype/war-table](https://github.com/bluephoenixmedia/Tarmin2Revised/tree/prototype/war-table/core/src/main/java/com/bpm/minotaur/screens/prototype_wartable). |
| A51 | **Teaching.** The first return to the Shelter points out the Table. First opening shows: "The houses are at war, and their armies march whether you watch or not." / "These marks show where they were reported. Only a siege is certain." / "When you reach a fight, you choose a side." Once-only hints on the first sighting, earshot, trail, arrival at a fight and Tolerated, worded on [#333](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/333). A "Living War" Codex page unlocks on the first Table visit. |
| A52 | **The soft nudge never reads the player.** Crossroads near the Shelter count as strategic ground when lords score objectives, in every world (weight *tune*, against A8; it must not make the Shelter's region a permanent battlefield). The Table lists the hosts nearest the Shelter first. |

### Sound and sight ([#336](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/336)), replacing W9 and W10's sources

| # | Decision |
| :-- | :-- |
| A53 | **Only real things make sound or smoke.** `Front`s make neither; a Front stays as a description of where a war's armies are meeting. There is no ambient war. |
| A54 | **Smoke,** range 24 chunks, cap 12: battle, a tall black plume lit orange at night; siege, a tall plume while it lasts; burnt ground and raided lands, medium, thinning over 3 sleeps; camp or halted host, a thin wisp within 8 chunks. A marching host raises none. Smoke places no map mark. |
| A55 | **Sound by source and distance** (here or next door / 2-3 chunks / 4-6 chunks): battle, assault, sally: steel, screams, volleys / roar, drums, horn / faint roar, lone horn. Host marching (10+): drums, feet / faint drums / nothing. Small party: feet / nothing / nothing. Camp or halted host: camp noise / nothing / nothing. Siege invested: camp noise, works / occasional thuds and a horn / nothing. Castle siege: engines, barrage, great horn, fainter with distance. One-shots to 6 chunks: `horn_battle` at a battle's start, `war_horn` at a rout. `crows_loop` over an aftermath, from next door. No new audio is needed. |
| A56 | **Underground, only real sources:** a rumble from a battle overhead within the existing reach; a gash shakes only while its house's seat is invested or assaulted, or a battle is fought over it. **Amends W32.** |
| A57 | **Beacons** light when a seat musters a host or is invested, toward the host's objective, or toward its lord's other hosts when calling for relief. |

### Cost ([#335](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/335))

| # | Decision |
| :-- | :-- |
| A58 | **The sleep jump runs every turn at full detail,** about 250 turns, with no coarser step. Budget at 26 units and 2 battles, headless on the development machine: **0.25 ms mean and 2 ms p99 a turn, 150 ms worst a jump, fresh JVM included, 1 KB a turn allocated**. |
| A59 | **The pathfinder is pooled.** Long skips ("wait in the siege") run at most 250 turns a frame and check for wake-ups between. Tests assert allocation and path calls per turn, never time. A headless task beside `runDuelSimulation` prints the timing and the A8 pacing numbers. |

### The Bivouac ([#318](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/318), [#329](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/329))

| # | Decision |
| :-- | :-- |
| A60 | **The kit** is crafted at the Crafting Bench: 3 Leather Scrap + 2 Sticks + 1 Bone. Lasts 10 nights; repair is 1 Leather Scrap for 3 nights; worn out it becomes scraps. Icon: the existing unused `assets/images/items/bedroll.png`. The pitched camp uses the tent and bedroll from `docs/game_assets/fantasy-camp-scatter-...`. |
| A61 | **Pitching and sleeping.** Any surface or strata chunk except sanctuary or town ground. The same war-clock jump and Season tick as a Shelter sleep; rest is **50% of max HP and MP**, prorated if cut short; no ailments cleared, no special save. The fire costs a Tinder Bundle (`FieldFire`). Exposure keeps working. A night counts a quarter of the sleep's turns (about 60) toward lingering. |
| A62 | **Ambush odds per night:** 15% + 1% per EDL + 3% per stage of Tarmin's Hunger; x1.5 underground; x1.5 with a lit fire; capped at 75%. 1-3 monsters from the chunk's own spawn table, at a random turn of the sleep. Armies aren't in this roll: they are simulated during the jump, and the jump stops at the turn a threat arrives. |
| A63 | **Waking.** Each hostile already adjacent gets one free attack round; a fire-drawn ambush gets two. |
| A64 | **Wards stack, each paid separately, one night each,** crafted or cast on pitching. **Shield:** Staked Hide Screen (2 Leather Scrap + 2 Sticks) or Sleeper's Ward (8 MP): absorbs the first 20 damage of the free rounds. **Alarm:** Tripwire Bells (1 Metal Scrap + 1 Stick) or Watchful Sleep (5 MP): wakes the sleeper when a hostile comes within 6 tiles, so no free rounds; no help against an army arriving. **Concealment:** Ash-and-Bone Circle (1 Firewood + 1 Bone) or Veil of the Sleeper (10 MP): unseen by monsters below intelligence 12 that aren't spellcasters; no fire allowed. The spells come from one new early-loot **Wayfarer's Tome**. Three icon prompts are on [#329](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/329); ship tinted stand-ins until they land. |

### Section 3's invariants, amended

- **Invariant 1:** the army layer draws from the history seed, the war clock and the army id (A19). The scheduler is gone.
- **Invariant 2:** the army simulation, `WarSoundscape`, `Favour`, `Oath` and `Contract` make no `Gdx.*` calls and are covered by JUnit.
- **Invariant 3:** every army effect on the history is also a deed (A16).
- **Invariant 8:** monsters exist only for the lines in the player's chunk (A21). Everything else is numbers.

---

## 2. Glossary

Add to `CONTEXT.md` in L1.1.

- **Encounter**: a scheduled piece of the surface war (skirmish, column, raid, camp, aftermath, siege).
  Not history, unless the player takes part.
- **War-band**: one side of a skirmish.
- **Column**: soldiers marching between a seat and a front.
- **Earshot**: the chunk radius within which a battle is heard (W9).
- **Favour**: a house's regard for the player (W18).
- **Sworn**: bound to a house by the oath (W20).
- **Contract**: a task a sworn house gives (W25).
- **Herald**: a house courier on the roads, carrying a proclamation (W29).
- **Siege, breach**: W33-W36.

---

## 3. Architecture

```
gamedata/history/war/
  EncounterScheduler   pure: (seed, warClock, history, playerChunk, saved overrides) -> encounters
                       in and around a chunk. Bias toward the player (W5), first skirmish (W8)
  Encounter            kind, houses, chunk(s), start/end clock, route (columns)
  WarSoundscape        pure: encounters + player chunk/level -> layered cues with volume and pan
  SiegePlanner         pure: siege state from the history
gamedata/history/favour/
  Favour               per-house favour, spillover, tiers
  Oath, Contract       sworn house, contracts, relic counter, seal-grant chain
managers/
  EncounterDirector    puts an encounter's monsters, props and aftermath into the live chunk
  WarAudio             plays WarSoundscape beds and cues through SoundManager, ducks under the Knell
rendering/
  Skybox3DRenderer     smoke columns, fire glow, arrow arcs, beacons, siege fire
screens/WarTableScreen the War Table
```

### Invariants

1. **Determinism** (Houses invariant 1). The scheduler draws only from streams keyed on the seed,
   the war clock and the chunk. A reload sees the same encounters.
2. **Headless.** `EncounterScheduler` (which records each slot's anchor in the ledger as it begins, so it is called once a turn), `WarSoundscape`, `SiegePlanner`, `Favour`, `Oath` and
   `Contract` make no `Gdx.*` calls and are covered by JUnit.
3. **History changes only through deeds.** Every effect the player has on the history (W6, W12,
   W21, W26, W33) is a `PlayerDeed`, so the save replays it.
4. **Save compatibility.** A save without favour, oath or siege data loads as Unknown with every
   house, unsworn, no siege.
5. **The Knell stays rare** (W31).
6. **UI text** passes `UiGlyphs.sanitize`; colours and sizes come from `UiTheme` and `HudSkin`.
7. **The paper doll is protected** (W24).
8. **Performance.** Distant encounters are sound and sky only. Monsters exist only for encounters
   in the player's chunk.

---

## 4. Phases and tasks

Each task: AC, test first where a test is named. Play-test scenarios use the fresh-snapshot save
procedure.

### Phase 1: The war you hear and see

Exit criterion: on a new game, the player hears war within the first minutes, sees smoke on the
horizon, meets a skirmish within ~150 turns, and comes across columns, raids, camps and aftermath
regularly on the surface.

**L1.1: Glossary** · deps: none. Add the Section 2 terms to `CONTEXT.md`.

**L1.2: A war at the start (W6)** · deps: none
- If no war is active when live play begins, a deterministic deed makes the most aggrieved house
  declare war.
- AC: 100 seeds, each starts with ≥ 1 active war (test); a save round-trip keeps the same history.

**L1.3: EncounterScheduler (W2-W5, W7, W8)** · deps: L1.2
- AC: same seed and clock give the same encounters (test). Over a simulated 2,000-turn overland
  walk, a visible encounter every 80-120 turns, and some war always within 6 chunks while a war is
  active (test). The first skirmish is adjacent within 150 turns on the first expedition (test).
  Border raids happen in peacetime between grudge-holding houses (test).

**L1.4: EncounterDirector: skirmish, column, raid** · deps: L1.3
- Spawns war-bands and columns from doctrine rosters; columns walk their route across chunks;
  skirmishes fight to a rout (reuse `BattleModel`); raids burn props.
- Joining a skirmish applies W12 as a deed.
- AC: a skirmish routs within 30-60 turns with no player; joining it nudges strength (test);
  a captain killed in it is chronicled (test).

**L1.5: War camps and aftermath (W2, W10.6-7)** · deps: L1.4
- Camps from the Goblin War Camp and camp-scatter packs; aftermath fields saved for 3 sleeps.
- AC: an aftermath field persists across save/load and is gone after 3 sleeps (test); screenshot review.

**L1.6: WarSoundscape and WarAudio (W9, W11)** · deps: L1.3
- Re-extract the SFX bundle's Medieval Fighting, Monsters, Foley, Ambience & SFX and Horror
  Screamers folders (currently only `__MACOSX` stubs); use what fits; stand-ins plus prompts for gaps.
- AC: the cue table from W9 per distance and level (test); panning follows the bearing (test);
  ducks under the Knell; play-test by ear.

**L1.7: Sky: smoke columns (W10.1)** · deps: L1.3. Smoke on the horizon toward each active front, lit from below at night. AC: screenshots by day and night.

**L1.8: Play-test scenario `living-war`**: new game, walk out, assert the first skirmish, screenshots
of smoke, a column, a camp and an aftermath field.

### Phase 2: The rogue

**L2.1: Noise masking (W13)**. AC: a monster's hearing range drops in a battle chunk and its neighbours (test).
**L2.2: Busy soldiers (W14)**. AC: soldiers in combat prefer enemy soldiers; they turn on the player only when struck or adjacent (test).
**L2.3: Slipping the lines (W15)**. AC: crossing a front during a battle without attacking does not lock the lines (test).
**L2.4: Polymorph disguise (W16)**. AC: a roster form is ignored by every house fielding it, attacked by their enemies, and noticed after 3 adjacent turns (tests); a Minotaur form passes Antlered Host defenders.
**L2.5: Underground raids (W32)**. Gash raiders and a thinner court while the holder is at war; raids on town gates. AC: tests for both; town standing and favour from driving a raid off.

### Phase 3: Keeping up

**L3.1: Heralds (W29)**. AC: heralds walk roads between seats; a proclamation unlocks a fresh event; killing one costs favour (tests).
**L3.2: The War Table (W27)**. A prop beside the Lectern, plus a `WarTableScreen`. AC: shows wars, fronts, season scores, sieges, seats at risk, favour, contracts; screenshot review.
**L3.3: Map battle reports (W28)**. AC: fronts as clashing banners, battlefields marked, columns as arrows; screenshot review.
**L3.4: HUD war tally (W30)**. AC: banners and a morale bar in a battle chunk, hidden elsewhere.
**L3.5: Sky: arrow volleys, beacons, fire glow (W10.2-4)**. AC: screenshots.

### Phase 4: The sworn

**L4.1: Favour (W18, W19)**. AC: the tiers drive soldier hostility (test); spillover to enemies (test); saved.
**L4.2: The oath (W20, W21, W23)**. AC: offered at Friend by a camp captain; sworn effects (tests); breaking it is a chronicled BETRAYAL naming the player, drops every house's favour, and sends hunters (test).
**L4.3: The tabard (W24)**. AC: a tinted tabard per doctrine on the doll overlay; the doll's coordinates unchanged (existing doll tests stay green).
**L4.4: Contracts and relics (W25)**. AC: each contract kind completes (tests); every third contract gives a doctrine relic.
**L4.5: The seal-grant chain (W22, W26)**. AC: three contracts, then the lord grants the seal; `ShelterNetwork` counts it (test).

### Phase 5: The siege

**L5.1: SIEGE_LAID / SIEGE_LIFTED / SIEGE_BROKEN (W33)**. History events, grammar for every bias and for the Knell. AC: sieges occur under the trigger (test); deterministic; tolled.
**L5.2: The siege's look and sound (W34)**. AC: screenshots; play-test by ear.
**L5.3: The breach (W35)**. AC: 1 seal when Friend or sworn with the besieger, 2 otherwise (tests); the gate still needs 3.
**L5.4: The castle's halls stub (W36)**. AC: entering the breach leads to the halls chunk; both sides fight; the inner door is sealed.
**L5.5: Siege assault contract (W37)**. AC: sworn players get it; it needs 1 seal.

---

## 5. Assets

| Need | On hand | Gap |
| :-- | :-- | :-- |
| Battle sounds (earlier) | `sounds/war/war_horn.ogg`, `war_drums.ogg`; `sfx/arrow_1-4`; `metal_hit*`, `meat_hit`, `monster_roar*`; `thunder_1-5`; ABYSS dark-cinematic loops | Many-voices battle roar loop, distant clash loop, crows, marching feet and armour jingle, camp ambience. **Re-extract** the SFX bundle's Medieval Fighting, Monsters, Foley, Ambience & SFX and Horror Screamers folders (only `__MACOSX` stubs today). The user is sourcing more. |
| Battle sounds, sourced 2026-10-10 (`assets/sounds/war/`, Freesound) | **Adjacent:** `376646` vikings in battle (208 s bed), `349382` medieval battle 1500 AD (141 s bed), `782984` wading into zombies, `547600` sword attack, `614068` demon pain (soldier screams), `222608` archers shooting + `866990` dart/arrow (volleys). **2-3 chunks:** `188815` battle horn, `274223` war drum loop, `342465` tribe drum loop, plus the adjacent beds low-passed. **4-6 chunks and underground rumble:** `823852` distant rumbling battle (143 s bed), `149966` muffled distant explosion, `372086` distant explosion. **Siege (W34):** `486086` artillery barrage (siege fire), `682401` explosion echo, `783023` fireworks (trim to bursts), `244796` war-of-the-worlds horn (the siege's horn, or Tarmin answering). **Bells:** `703236`, `867756` (spare knell/siege tolls) | Crows, marching feet and armour jingle, camp ambience. **Before shipping:** convert every file to 16-bit OGG (libGDX `Sound` can't load 24-bit/float WAV or FLAC); trim and loop the beds and stream them as `Music`; keep the 230 MB and 55 MB originals out of `assets/`; record each file's Freesound licence and attribution in `docs/asset-licenses.md`, and drop any CC BY-NC file. |
| Camps | `POLYGON_Goblin_War_Camp` (tents, palisades, gibbets, banners); `fantasy-camp-scatter-terrain` (tents, campfires, supplies); `assets/models/camp` | none |
| Banners | Viking Realm banners; Kenney flags and banners | none |
| Smoke, fire, siege fire | `POLYGON_Particle_FX`, `BearFX Explosions` | a sky smoke-column texture if particles don't read at distance |
| Tabard | none found yet | a tintable tabard overlay sprite: stand-in plus prompt |
| Castle siege | `assets/models/castle_citadel.glb` | a breach in the wall; siege engines |

---

## 6. Deferred

- **Rising through the ranks** of a house to duel its lord (W39): its own plan after its own grill.
- **The Castle Tarmin interior** beyond the halls stub (W36): its own plan.
- **The tabard disguise** (W17).
- **Dreamsmoke-style polymorph** (W40).
