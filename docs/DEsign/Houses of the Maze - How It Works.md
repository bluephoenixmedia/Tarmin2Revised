# The Houses of the Maze: How It Works

How the living history of the Maze runs in the game as built on `develop` (October 2026): the
houses and their wars, the lore it writes, and how news of it reaches the player. The design
reasoning lives in `Implementation Plan_ The Houses of the Maze.md`; this document describes what
the code actually does, with the numbers it uses.

---

## 1. The big picture

The Maze is ruled by a handful of demonic **houses**. Before the player ever sets foot in it, the
game simulates **300 years** of their politics: lords are born, marry, scheme, murder, go to war,
lose their seats and die out. That simulated past is the lore. Once play begins, the simulation
keeps running, one **season** at a time, and the player's deeds feed into it.

```
            world seed
                |
        HistorySimulator.prehistory()      300 years x 4 seasons, before the game starts
                |
          HistoryWorld  <-----------------  PlayerDeed (kills, deaths, battles, quests...)
                |
     +----------+-----------+--------------+---------------+
     |          |           |              |               |
  seal lords  surface    megabeasts    towns of the     the chronicle
  in gashes   war fronts  in the deep   strata          (fragments, Lectern,
  (bosses)    (battles)                                  rumours, the Knell)
```

Everything is **deterministic**: the same seed plus the same deeds always gives the same history.
The save file does not store the history itself, only the seed, the number of live seasons and
the list of deeds. On load the game re-runs prehistory and replays the seasons and deeds
(`HistoryManager.fromSave`). That is why every random draw in the system is keyed to the seed and
the season, and why "things that happened" are always recorded as deeds.

Key code:

| What | Where |
|---|---|
| Data model | `gamedata/history/` (`House`, `Figure`, `War`, `Grudge`, `HistoryEvent`, `Megabeast`) |
| The simulation | `gamedata/history/HistorySimulator.java`, `MortalSimulator.java` |
| Runtime owner, save/replay | `managers/HistoryManager.java` |
| Prose | `gamedata/history/text/` + `assets/data/chronicle_grammar.json` |
| House creeds | `assets/data/doctrines.json` |
| Megabeasts | `assets/data/megabeasts.json` |
| Seal lords | `gamedata/boss/SealLord.java`, `managers/SealCourt.java` |
| Surface wars | `gamedata/history/war/`, `managers/WarManager.java`, `managers/BattleDirector.java` |
| The Knell | `gamedata/history/text/Knell.java`, `managers/KnellCrier.java`, `rendering/KnellOverlay.java` |

---

## 2. Time

- **4 seasons a year.** Year numbers in the prose are `season / 4 + 1`.
- **Prehistory**: 300 years (1,200 seasons). The game starts in about year 301, "the Present Age".
- **Live play**: a season passes when:
  1. **the player sleeps in a shelter bed**, or
  2. **500 player turns pass** on an expedition without sleep (`HistoryManager.SEASON_TURNS`).
     The Maze does not wait for the seeker: a house can fall while they are underground.
- The **war clock** counts every player turn. It drives where war fronts are and where megabeasts
  roam, and it triggers the 500-turn seasons.
- Death does **not** reset the history. The world map re-rolls, so the houses' seats move on it, but
  the houses, their feuds and everything the player has done carry on.

---

## 3. The houses

### 3.1 Kinds of house

| Kind | Count | Holds | Strength cap |
|---|---|---|---|
| **Great house** | always exactly 3 | one of the three **gashes** (wounds in the world, each a seal site) | 100 |
| **Lesser house** | 4-8 at founding, never fewer than 4 | a holdfast out in the wilds | 60 |
| **The Antlered Host** (Tarmin-Zul's) | 1, appears in years 180-260 | Castle Tarmin | 120 |

Every gash always has exactly one holder. If a great house dies out, the strongest claimant takes
its gash (one of its own vassals first, else the strongest lesser house). If the lesser houses
drop below 4, a new one is founded.

Each house has a name, a **sigil**, **words** (a motto), a holdfast name, a lord, kin, sworn
swords, a **strength** (its military might, 5 up to its cap) and **prestige** (renown from victories,
seizures and killing seekers).

### 3.2 Doctrines

Each house follows a **doctrine**, a creed from `doctrines.json`. The doctrine decides the soldiers
it fields, its colours, what its gash's interior looks like, what body its lord fights in, the
voice its chroniclers write in, and which traits its lords tend to be born with.

| Doctrine | Creed | Lord's body | Gash interior | Typical lords |
|---|---|---|---|---|
| The Flensed Choir | Pain is a hymn, and the skin is only the lid of the hymnal. | Mind Flayer | flayed cathedral | Zealous, Cruel |
| The Chainwrights | What is bound cannot be lost. Bind everything. | Iron Golem | hook foundry | Patient |
| The Hollow Court | The perfect body hears nothing, sees nothing, wants nothing. | Lich | silent galleries | Patient, Cunning |
| The Gilded Rot | Decay is the last and richest ornament. | Demon Slime | gilded charnel | Ambitious, Cunning |
| The Ashen Penitents | Burn the sin out, then burn the burning out. | Fallen Angel | pyre cloisters | Zealous, Wrathful |
| The Ossuary Lords | The dead are the only loyal subjects. | Bringer of Death | bone architecture | Patient, Ambitious |
| The Glass Hunger | Appetite is the only honest prayer. | Purple Worm | glass gullet | Wrathful, Ambitious, Cruel |
| The Antlered Host | All Mazes kneel to the Antlered Lord. | Minotaur | obsidian rift | Tarmin-Zul only |

### 3.3 Figures and traits

Every named character is a **figure**: a lord, kin (children, spouses) or a sworn sword. Figures
are born, age, marry, are taken hostage and die. A figure dies of:
natural causes, battle, assassination, execution (as a hostage), a succession dispute, or the
player.

Each figure has one or two **traits**, drawn mostly from their doctrine's weights. Traits are the
engine of the politics: they decide what a lord does with their season.

| Trait | Effect on what a lord does |
|---|---|
| Ambitious | wants war (+), may betray allies, may rebel against a liege, may usurp a child heir |
| Wrathful | wants war against anyone it holds a grudge against, rebels, usurps, executes hostages |
| Craven | avoids war (--), swears fealty to a great house |
| Zealous | wants war (slightly) |
| Cunning | betrays allies, assassinates rivals, sets megabeasts on enemies, usurps |
| Honourable | no special scheming |
| Cruel | assassinates, executes hostages, sets megabeasts on enemies, never marries for alliance |
| Patient | slower to war, swears fealty |

**Tarmin-Zul** is ageless, Ambitious and Cruel, and is never assassinated or killed by age.

### 3.4 Relationships between houses

Any two houses stand in one of four **stances** (`HistoryWorld.stance`):

- **War**: an active war between them.
- **Sworn**: one is the other's vassal.
- **Allied**: joined by a marriage pact.
- **Neutral**: none of the above.

Houses also hold **grudges**: remembered wrongs, each with a cause and a weight. Grudges fade by
about 0.8% a season and vanish below 0.25. Grudge weight is the main thing that pushes a house
toward war, and the strongest grudge supplies the war's **casus belli** (its stated reason).

| Wrong done to a house | Grudge it holds | Weight |
|---|---|---|
| Its hostage executed | Slain kin | 5 |
| Ally betrayed it | Broken pact | 5 |
| Its lord assassinated | Slain kin | 4 |
| Its lord killed in battle | Slain kin | 4 |
| Its gash seized | Usurped seat | 4 |
| Forced to kneel after a defeat | Usurped seat | 3 |
| A megabeast was set on it | Slain kin | 3 |
| Its kin taken hostage | Hostage held | 2 |

---

## 4. One season, step by step

Every season (`HistorySimulator.step`) runs in this order:

1. **Tarmin-Zul rises**, if his year has come (once, in prehistory). His house, the Antlered Host,
   appears holding Castle Tarmin.
2. **Life and death.** Everyone ages. After 40, the chance of dying grows each season. Lords
   aged 18-50 have children (up to 4). A house with no sworn sword may gain one.
3. **Successions.** Any house whose lord died gets a new one (see 4.1).
4. **Hostages.** A hostage is released if the war ends or the captor falls. A Cruel or Wrathful
   captor may execute one (6% a season).
5. **Megabeasts** wake on their appointed season and, once awake, raid a random house (1.2% a
   season per beast): the house loses 8 strength and sometimes a family member.
6. **Each lord decides** (see 4.2).
7. **Wars are fought** (see section 5).
8. **Successions again**, so no house ends a season without a head.
9. **Recovery.** Every house regains 1.2 strength; grudges fade.
10. **Refill.** If the lesser houses are below 4, a new house is founded.

Live seasons also run the **mortal towns** and the **houses' plots against them** (section 9).

### 4.1 Succession

When a lord dies, the heir is their eldest living child, else the eldest of the house. A
scheming adult kinsman (Ambitious, Cunning or Wrathful) may contest it:

- **Usurpation**: if the heir is a child, the rival takes the seat (60%). The child may be killed.
- **Disputed succession**: otherwise the rival may contest the seat (35%, 50% against a child heir).
  The rival wins 40% of the time, the loser may die, and the house loses 20% of its strength.
- **Succession**: an orderly handover.

Losers who survive become **exiles**, and may turn up living in an underground town.
A house with no living members (hostages don't count) **dies out**: its wars end, its vassals go
free, its alliances break, and its gash (if it had one) passes to a claimant.

### 4.2 What a lord does with a season

Only an adult lord acts. A **vassal** lord can only **rebel**: if Ambitious or Wrathful and their
house is above 60% of the liege's strength, a 3% chance to throw off the oath and declare war
("Independence").

A free lord tries these in order and does the first that happens:

1. **Betrayal** (Ambitious/Cunning): break an alliance with a weak or embattled ally and attack it.
2. **Megabeast bargain** (Cunning/Cruel): set an awake beast on a house it holds a grudge against.
3. **Assassination** (Cunning/Cruel): murder the lord of a house it holds a grudge against (1.5%).
4. **War** (see 5.1).
5. **Fealty oath** (Craven/Patient, or a weak lesser house): swear to the strongest great house.
6. **Marriage pact** (never Cruel): marry into a neutral house it bears no grudge, and become allies.

---

## 5. War

### 5.1 Why houses go to war

A free house may hold at most **2 wars** at once. Each season it scores every neutral house as a
target (`warUtility`):

- grudge weight against it (capped at 6), minus 1.6
- Ambitious +0.8, Wrathful +0.8 (if any grudge), Zealous +0.3, Craven -1.0, Patient -0.4
- target already at war +0.6
- we are 30% stronger +0.6, or 30% weaker -0.8
- a lesser house eyeing a great house's gash +0.7
- the target is Tarmin-Zul's castle +0.3

It picks the best target, and declares war with probability `min(25%, utility x 8%)`. The
**casus belli** is the strongest grudge's cause if any; otherwise *hostage held*,
*claim to the castle*, *claim to a gash* or plain *ambition*.

### 5.2 How a war plays out (in the simulation)

Each season of an active war:

- **20% chance of a battle.** Each side's power is its strength plus 30% of its vassals'
  strength, times a random 0.6-1.4 roll; the defender gets +10%. The battle is named after a place.
  - Loser loses 6-14 strength; winner loses 3-8 and gains 2 prestige.
  - 5% the losing lord falls in battle; 12% one of its sworn swords does.
  - 15% the winner takes one of the loser's family **hostage**.
  - If the loser is below 15 strength: 35% chance the war ends in **seizure**:
    - a lesser house beating a great house **takes its gash** ("Seat seized");
    - a beaten lesser house is **forced to kneel** as the winner's vassal;
    - a great house beaten by a great house sues for **peace**;
    - Tarmin-Zul's castle is never seized.
- **Peace**: after 16 seasons, 5% a season; or 15% a season if both sides are below 30 strength.
- A war also ends if either house dies out.

### 5.3 Wars on the surface (fronts and battles the player can join)

Every active war has a **front**: a 3x3 block of overland chunks. Each house has a **seat** on the
map (great houses at their gash's seal site at the end of a shelter road, Tarmin-Zul at the castle,
lesser houses at a holdfast 14-32 chunks out). The front sways back and forth along the line
between the two seats with the war clock, completing one swing every 3,000 turns. The expedition
map shows the fronts.

When the player walks into a front on the surface:

1. **The horns sound** (`BattleDirector`): "War-horns! House X marches on House Y. The lines close
   here in 12 turns: leave by any edge, or go below." Leaving now avoids the battle.
2. **The lines close.** Each gate is held by 2 soldiers, and each house marches a line of 16 in
   from its own edge, drawn from its doctrine's roster. Each side's reserve depends on its
   strength (16 plus strength/5, between 2 and 20 extra).
3. **The battle.** Every turn each side marches in up to 3 reinforcements. **Volleys** are marked
   on the ground a turn before they land (every 7 turns); **charges** sweep across the field
   (every 11). Each loss costs a side 4 morale; its **war-captain** (a sworn sword, or 15% of the
   time the lord in person) falling costs 70. A side routs when its morale breaks or its men run out.
   A battle that will not break is called after 200 turns for the stronger side.
4. **Aftermath.** The victors linger 3 turns and march on. The routed house leaves its **signet ring**
   and **torn banner** on the field as trophies.
5. **It enters the history.** A battle the player stood in until it broke is recorded as a deed
   (`BATTLE_WITNESSED`) and resolves exactly like a simulated battle: strength, deaths, hostages,
   possibly a seized gash. **The player can change the course of a war by fighting in it.**

House soldiers in the Maze fight by the history's stances: houses at war attack each other on
sight, allied and sworn houses don't, and every house is hostile to the mortal factions
(`FactionMatrix.getRelation`).

---

## 6. The seal lords (the gash bosses)

The three gashes are the three seals the player needs for Castle Tarmin. **The boss of a gash is
whoever holds it right now**, not a fixed monster.

- **Where**: two strata under the gash's seal site (level 3). The strata of the gash from level 2
  down to the court wear the holding house's doctrine as their interior (`GashInterior`). If a war
  passes the gash to a new house, the interior changes next time the player enters.
- **The sealed gate** at the seal site names the current holder: "A sealed way down. Far beneath
  it, Vora the Restorer of House Gullet holds the Ashen Gash, and one of the ancient seals with it."
- **The map** says who holds each seal site and, if it changed hands this run, from whom and when.

### 6.1 How a lord is built (`SealLord.compose`)

Every lord starts from the same frame: **180 HP, 2d8+3 damage, armour 15**. HP grows 7.5% per
level of the court's difficulty. The doctrine supplies the body (look) only. Then:

| Lord's trait | Fight effect |
|---|---|
| Ambitious | +15% HP, +2 damage |
| Wrathful | +3 damage; **berserks** below half health (+4 damage, +2 speed) |
| Craven | -10% HP; below a third it **cowers** (+3 armour) and calls its retinue |
| Zealous | **regenerates** a little every turn |
| Cunning | +2 armour |
| Honourable | +1 armour; **duellist**: its retinue holds back until it is wounded |
| Cruel | +2 damage |
| Patient | +25% HP, slower |

The lord is also older (over 60: -15% HP) or younger (under 20: -10% HP), and carries its house's
prestige (up to +25% HP). Up to 3 of the house's **sworn swords** stand beside it, named
("Kel, sworn to House Gullet").

The lord's name includes an **epithet** from its history (section 8.3).

### 6.2 Winning a seal

- **Kill the lord**: the player takes the seal from the body.
- **A seal lord's court admits no strays**: the Doom Clock's periodic spawns skip it, so the duel
  belongs to the lord and its sworn swords.
- **If something else kills the lord after the player drew its blood**, the seal still falls to
  the player.
- **If something else kills a lord the player never touched**, a rival house's soldier or a beast,
  **the killer takes the seal**. It becomes a **seal bearer** ("Grul, bearing the seal of the
  Ashen Gash") and must be hunted down for it. The lord dies in the history
  (`LORD_SLAIN_IN_COURT`), the killing house gains prestige, the house crowns an heir, and the
  Knell announces it: "The court of the Ashen Gash is empty... Whoever holds the seal now, it is not
  you."
- If the history replaces the lord (a death, a seizure) before the player arrives, the old court
  is removed and the new lord takes the seat.

---

## 7. Megabeasts

Three to six unique, named beasts per world ("Ulgrath the Obsidian Wyrm"), built from
`megabeasts.json`: an archetype (Wyrm, Titan, Colossal Hive, Drowned Thing, Bone-Choir, Fungal
Mother), a material (Obsidian, Brass, Bone, Glass, Rotting, Salt), a breath (cinders, frost,
plague, madness, acid) and a damage-type weakness.

- Each wakes in some season of prehistory (announced as "stirs") and lairs at a depth between the
  first stratum and level 8. The first always lairs within reach of strata 1-2.
- In the history, awake beasts **raid** houses and are **bargained with** by Cunning or Cruel lords
  who set them on their enemies.
- In play, a beast **roams** the chunks within 2 of its lair on its level (one circuit per 1,200
  turns). It is never saved in a chunk; it is wherever the rules put it. Its wounds persist.
- **Hunting**: a beast that loses the player follows them through a gate or down a ladder, arriving
  3 turns behind, for up to 300 turns.
- **Peace**: lay a trophy of the houses (a signet or banner from a battlefield) within 3 tiles of a
  beast and it takes the offering and stops hunting the player. Striking it again breaks the peace.
- **Killing one** is chronicled and permanent.

---

## 8. Lore: the chronicle

### 8.1 Events

The history records everything as **events** (`EventType`): founding, Tarmin-Zul's rising,
successions, usurpations, disputed successions, natural deaths, assassinations, marriages,
betrayals, oaths, rebellions, wars, battles, seized seats, hostages taken and executed, peace,
extinction, megabeast stirrings, raids, bargains, slayings and pacifications, towns bought and
betrayed, quests done, lords slain in court, seekers fallen, figures slain by the player, and
Tarmin-Zul's ascendancy. Each event remembers who acted, who was acted upon, why (casus belli),
where, and **the earlier event that caused it**.

### 8.2 Two sides to every story

The prose comes from `chronicle_grammar.json`. Each event type has three sets of templates:

- **NEUTRAL**: a rumour ("In the year 304 House Pyreholt took the Ashen Gash from House Glassjaw.")
- **FOR**: as the acting house's own chronicler tells it, triumphant.
- **AGAINST**: as the victim or its bitterest rival tells it, bitter.

Every account has a **byline**, a **chronicler** ("Cantor Sibine of House Sallow"), with a title
and turn of phrase from their doctrine's voice. A neutral account is signed "A tavern rumour", "A
refugee's account", "Scrawled on a shelter wall" and the like.

### 8.3 Epithets

What a figure is called depends on who is speaking:

| Deed or trait | Own house says | Everyone else says |
|---|---|---|
| Usurped a seat | the Steadfast | the Usurper |
| Won a disputed succession | the Restorer | Half-Crowned |
| Assassinated someone | the Quiet | the Poisoner |
| Executed a hostage | the Just | the Hangman |
| Betrayed an ally | the Shrewd | the Faithless |
| (a lord, by first trait) Cruel / Craven / Wrathful / Ambitious ... | the Stern / the Prudent / the Fierce / the Great ... | the Cruel / the Craven / the Mad / the Grasping ... |

The Maze names the player too, from their deeds: **the Seeker**, then **the Bloodied** (one named
kill), **Lordsbane** (three or more), and **Gashbreaker** (killed the lord of a great house).

### 8.4 How the player learns the lore

The player starts knowing nothing. An event becomes **known** when it is:

- **read in a fragment** found in the world (about 3 chunks in 10 hold one):
  - **Chronicle pages** (mostly in the strata) tell personal history: successions, murders,
    marriages, hostages, extinctions, megabeasts, and plots against towns.
  - **Herald proclamations** tell matters of state: wars, peace, seized seats, oaths, rebellions,
    the player's kills, Tarmin's ascendancy, quests done, betrayed towns.
  - **Torn banners** (on the surface, and dropped by routed armies) tell battles and seizures.
  - **Void glyphs** tell the oldest story: how Tarmin-Zul came through the Obsidian Rift.

  Each read reveals one not-yet-known event of its kind, plus **the event that caused it**. A plot
  still in motion, a town bought but not yet turned, surfaces first. A fragment is read on pickup
  and its account appears in the log, told for or against depending on the event.
- **heard as a shelter rumour** after sleeping (section 10.1),
- **tolled by Tarmin's Knell** (section 10.2).

Everything known is collected at the **Archive Lectern** in the shelter (the Codex), grouped into
eras (the First, Second and Third Century, the Present Age), filterable by house, with every
entry shown **twice: as the acting house tells it and as its enemy does**.

---

## 9. The towns of the strata (mortals)

Separate from the houses, the history keeps **24 mortal settlements**, founded in the first
century, each answering to one of three mortal powers:

| Power | Guard | Folk |
|---|---|---|
| The Refugee Council | Dwarf | dwarves, sages, jesters |
| The Goblin Clans | Hobgoblin | goblins, hobgoblins, kobolds |
| The Outcast Covenant | Cloaked Skeleton | sages, jesters, troglodytes |

- Each settlement has **seats** (reeve, merchant and so on) held by named **mortals** who age, die and
  are succeeded within their family. The mortal simulation uses its own random stream, so it never
  changes the houses' history.
- Towns stand on **strata 2-4** (levels 3-5), about one candidate chunk in nine, where the stratum
  allows. The first town the player finds is bound to the first unbound settlement, and so on.
- A town may shelter one of the history's **exiles**: a sworn sword of a fallen house, or the
  loser of a succession.
- **Standing**: each town starts with its own welcome. Favours (+20) and crimes (-40, such as striking a
  guard) are heard at half weight by its sister towns and, reversed, at a quarter by the powers it
  feuds with (the Council and the Covenant don't feud). At -25 or below the town turns on the player.
- **Quests** from the reeve are drawn from the history: recover a signet of a broken house, unmask
  a house's agent among the townsfolk, slay a megabeast, or carry a message to another town (in
  wartime, through a war's front on the surface). Done quests enter the chronicle.
- **The houses' plots**: each live season, any unbought town has a 0.4% chance of being secretly
  **bought** by a house. 6-15 seasons later it **betrays** the mortals: it becomes the house's,
  and shuts its gate on strangers. This is never a rumour; only fragments reveal it, so a sharp
  reader can find the plot before it ripens.

Townsfolk also pass on recent rumours when spoken to (`TownTalk`).

---

## 10. How news reaches the player

There are three channels, from quietest to loudest. All of them also go into the **running log**
in the lower right of the HUD, which can be scrolled back (mouse wheel) to reread earlier
messages.

### 10.1 Shelter rumours (on sleep)

After the player sleeps, they wake to the season's news. Of the events that **did not toll the
knell**, up to the **3 loudest** are told as neutral rumours and become known history. Loudness
(`Headlines.weight`):

| Weight | Events |
|---|---|
| 10 | seat seized, Tarmin-Zul rises, Tarmin ascendant |
| 9 | megabeast slain, town betrayed, lord slain in court |
| 8 | war declared, house extinguished |
| 7 | usurpation, assassination, betrayal, hostage executed, rebellion, battle where a lord fell, megabeast raid / bargain / pacified |
| 6 | megabeast stirs, quest done |
| 5 | disputed succession, peace |
| 4 | battle |
| 3 | marriage, oath, hostage taken, succession, founding |
| 0 | everything else (natural deaths, the player's own death), never a rumour |

### 10.2 Tarmin's Knell (the great events)

The great events are announced by Tarmin-Zul himself, over the whole Maze, wherever the player is.

**What tolls** (`Knell.tolls`):

- **Always**: a seat seized, a house extinguished, a lord slain in court, a figure slain by the
  player, peace, a megabeast stirring, slain or pacified, a town betrayed, Tarmin's ascendancy.
- **When it is a great house or the castle**: succession, usurpation, disputed succession (the head
  of a great house has died).
- **A battle**, only when a lord or captain fell in it.

**When**: after a shelter sleep, and on any turn a 500-turn season passes mid-expedition. The
crier only listens to events since it last listened, so loading a save never tolls old news.

**What the player experiences**:

1. **A gong**: a deep toll (`assets/sounds/sfx/toll.ogg`) with its own volume setting. The music
   ducks under it.
2. **Red telepathic text** in the intellivision font, one line at a time, fading in and out over
   the middle of the view, with a faint **red vignette** bleeding in from the screen edges
   (switchable in Settings). It never pauses play or takes input.
3. **Tarmin speaks in character**, gloating, from the `knell` section of the grammar:
   - "{A} has taken {gash} from {B}. I did so enjoy watching them try to keep it."
   - "{A} and {B} have made peace. How dull. It will not last."
   - About the seeker's own deeds: "Ah, my little seeker. You killed {b} of {B}. They were mine,
     and you broke them. I am almost proud."
4. **Above ground, the sky answers**: cloud-to-cloud lightning ripples out from the castle and sweeps
   across the sky in time with the gong, lighting the maze as it passes overhead.
5. At most **3 lines** per toll; on a crowded day Tarmin waves at the rest ("There is more. There is
   always more. Go and find it.").

Every tolled event becomes known history at the Lectern, and its line is added to the log.

### 10.3 In-the-moment messages

Events the player is there to see are told on the spot: the horns of an approaching battle and
the battle's turns; "You take the seal of the Ashen Gash from the body. (2/3 seals)"; "X falls,
and Y takes the seal."; a slain megabeast; a message carried through the lines; a fragment's
account as it is read. Reading a fragment about a seized seat, an extinct house, a war or a
megabeast also plays a warning sound.

---

## 11. The player's place in the history

The player's actions enter the history as **deeds** (`PlayerDeed`), the only input besides the seed:

| Deed | What it does in the history |
|---|---|
| Killing a named figure (lord, sworn sword, bearer) | the figure dies; its house must find an heir; the Knell gloats |
| Dying | "a seeker fell", numbered; the house whose soldier did it gains 3 prestige |
| The Doom Clock reaching a new stage | **Tarmin ascendant**: every other house loses 3 x stage strength; from stage 3 the weakest free lesser house is forced to kneel to him |
| Standing in a surface battle until it breaks | a real battle of that war, with all its consequences |
| Killing / pacifying a megabeast | chronicled; a dead beast never returns |
| A seal lord killed in court by another hand | the lord dies; the killer's house gains prestige |
| Finishing a town's quest | chronicled as a deed of that town |

So the player shifts the balance of power indirectly. Killing a lord can set off a usurpation.
Joining a battle can break a house and hand its gash to a lesser one, which also changes the seal
lord and the gash's interior. Dying feeds Tarmin's Hunger, which bleeds every house and drives
the weak to kneel to him.

---

## 12. Tuning knobs

| Constant | Value | Where |
|---|---|---|
| Prehistory length | 300 years | `HistorySimulator.PREHISTORY_YEARS` |
| Lesser houses | 4-8 | `MIN_LESSER`, `MAX_LESSER` |
| Wars per house | 2 | `MAX_WARS_PER_HOUSE` |
| Battle chance per war per season | 20% | `HistorySimulator.wars` |
| Megabeasts | 3-6, raid 1.2%/season | `MIN_BEASTS`, `MAX_BEASTS`, `BEAST_RAID` |
| Turns per mid-expedition season | 500 | `HistoryManager.SEASON_TURNS` |
| Front sway period | 3,000 turns | `FrontPlanner.PERIOD` |
| Battle warning / line / cap | 12 turns / 16 soldiers / 200 turns | `BattleDirector`, `BattleModel` |
| Seal lord frame | 180 HP, 2d8+3, AC 15, +7.5% HP/level | `SealLord` |
| Seal court level | 3 | `SealCourt.COURT_LEVEL` |
| Knell lines per toll | 3 | `KnellCrier.MAX_LINES` |
| Rumours per sleep | 3 | `GameScreen` (sleep) |
| Settlements | 24 | `Settlement.COUNT` |
| Town buy chance / betrayal delay | 0.4% / 6-15 seasons | `MortalSimulator` |

To see a full generated history for a seed, `ChronicleDump` prints one, and the play-test
scenarios (`--playtest=seal-lords`, `surface-wars-megabeasts`, `towns-and-trade`,
`tarmins-knell`, ...) exercise each system in the running game.
