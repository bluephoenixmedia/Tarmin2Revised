# Spec: Combat Arts & Weapon Mastery

The spec the map **Wayfinder: Combat Arts & Weapon Mastery** ([#221](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/221)) was charting toward. Each section names the ticket that decided it; that ticket wins if this page disagrees. Vocabulary is in `CONTEXT.md`: Weapon Family, Mastery, Combat Art, Active Art, Passive Art, capstone, Art bar.

## Already built

- **Weapon Families** ([#224](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/224), built in [#231](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/231)):
  - Every weapon in `weapons.json` carries a required `weaponFamily`.
  - A throwable weapon trains its home Family when swung and Thrown when thrown.
  - Polymorphed blows train Unarmed, and a shield bash trains the shield's Family.
- **Mastery** ([#226](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/226), built in #231): `gamedata/player/Mastery`.
  - Full hits on non-Trivial monsters train it. Ranks fall at 10 / 50 / 150 / 400 hits, and each rank gives +1 to-hit.
  - Mastery belongs to the Incarnation and is saved.
  - `Mastery.procMultiplier` and `Mastery.cooldownMultiplier` hold the rank scaling the Arts use.
- **Telemetry** ([#234](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/234)): schema 3, with an empty `artUses` map for the Arts to fill.
- **Duel simulator** ([#232](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/232), [#239](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/239)): `--mastery`, `--approach`, `--passive` and `--weapon=UNARMED`.

## 1. Combat Arts and the soul

- **Three Arts per Family** ([#227](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/227), [#228](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/228)), each learned on **reaching** that rank in that Family:
  - an **Adept Passive**;
  - an **Expert Active**;
  - a **Master capstone**.
- **The soul keeps learned Arts** across deaths, beside Known Spells. Mastery does not carry over: a new body starts Untrained ([charting decisions](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/221)).
- **A kept Art below its rank still works, but weaker:**
  - a Passive procs at **half** chance;
  - an Active or capstone takes **double** cooldown.

  Both return to normal once the body retrains the rank (#226).
- **Passive chance scales with rank:** base chance at the rank that taught the Art, **+25% of base per rank above it**, capped at 1.5x (#226).
- **What triggers a Passive:** only a **full hit** (a glancing blow never does), with no separate armour roll. A crit is a full hit like any other ([#237](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/237)).
- **The Father's Sword Perk** (HP and crit) stacks with Blade Mastery and never upgrades an Art (#237).
- **Starting numbers** (tuned later in the simulator):
  - Passives: 25–30% base chance.
  - Expert Actives: 15-turn cooldown.
  - Capstones: last 5 turns, 50-turn cooldown.

## 2. The catalogue

Every name is original: nothing is taken from Caves of Qud or XCOM. Each capstone makes that Family's Adept Passive **certain** for 5 turns, then puts the Expert Active on cooldown.

| Family | Adept Passive | Expert Active | Master capstone |
|---|---|---|---|
| Blade | **Answering Steel**: a monster's *melee* attack misses you → 30% instant free counter-cut | **Closing Thrust**: step in and strike a monster 2 tiles ahead at +2 to-hit | **Whirling Edge** |
| Dagger | **Cut the Sinew**: 25% a full hit makes the target Bleed and be Slowed for 2 turns | **Gutting Stab**: +1d4 for each status on the target (Bleeding, Slowed, Dazed, Pinned, Stunned) | **Red Harvest** |
| Axe | **Wide Chop**: 30% a full hit also strikes every other adjacent hostile | **Hew Through**: strips 3 AC for 5 turns and makes the target Bleed | **Butcher's Frenzy** |
| Blunt | **Ringing Blow**: 25% a full hit Dazes; a Dazed target is Stunned instead | **Battering Ram**: knocks the target back 1 tile and Dazes it; into a wall, +1d6 and Stun | **Toll the Bell** |
| Polearm | **Hold the Line**: the first time each monster steps next to you, you strike first, free | **Reel In**: pulls a monster from 2 tiles into reach and Dazes it | **Thicket of Points** |
| Unarmed | **Quick Knuckles**: 30% a full hit adds a free half-damage strike | **Iron Grip**: an adjacent monster can't move and is at -2 to-hit for 3 turns | **Storm of Fists** |
| Bow | **Swift Nock**: 25% a full hit lets you loose a second arrow at once | **Held Arrow**: ready one reaction shot (§4) | **Rain of Shafts** |
| Crossbow | **Staggering Bolt**: 25% a full hit Dazes | **Nail in Place**: a normal shot; on a hit, the target can't move for 3 turns but can still attack | **Siege Rhythm** |
| Firearm | **Practised Hands**: 25% a full hit cuts the next reload by one turn | **Marked Skull**: aim for one turn, then a guaranteed headshot (crit plus Daze). A monster without a head takes a normal shot | **Powder Fury** |
| Thrown | **Tangle**: 25% a thrown full hit Slows the target for 2 turns | **Shattering Throw**: hits the target and every adjacent creature for half damage, **the player included** | **Hail of Iron** |

- **Thrown Arts** apply to any thrown weapon, including a hand axe or a javelin (#228).
- **Firearms** stay hitscan. Their reload and noise are unchanged; Marked Skull only adds a turn of aim.

## 3. New effects (#227, #228, [#225](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/225))

- **Dazed:**
  - -2 to-hit, and the creature acts slightly slower.
  - Dazing a Dazed creature **Stuns** it, but not while its post-stun immunity runs. During immunity it can still be Dazed.
- **Pinned:** cannot move for 3 turns, but can still attack.
- **Knockback** (Battering Ram) and **pull** (Reel In): Heavies and bosses cannot be moved, but still take the Daze.
- **Splash** (Shattering Throw): half damage to every creature adjacent to the target, the player included.

## 4. Readied actions: Held Arrow (#225)

- **The hook:** a monster step goes through `MonsterAiManager.moveMonsterTo`. Raise a step event there, plus an "attacked from the line" event from the monster attack paths.
- **The shot:** the reaction shot is the same instant `CombatManager.raycastProjectile` the bow already uses.
- **Trigger:** the first hostile that moves into or along the line of fire, or attacks from inside it, within range.
- **One shot**, at x0.7 accuracy and no crit.
- **The stance:**
  - It lasts while the player keeps waiting; any other action ends it.
  - Being hit does not break it.
  - Pressing the key again lowers the bow without spending the shot.
  - The 15-turn cooldown starts when the shot fires or the stance lapses.
- **The energy loop** in `TurnManager.processTurn` must stop spending a monster's remaining actions once it is dead.
- **Firearms** may also ready a shot, if a firearm Art ever uses one. The gun is then empty, and the shot is refused until the reload is done.

## 5. The Art bar ([#229](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/229))

- **Contents are automatic:** the Expert and Master Arts of the Families in hand, right hand first, then left. There is no assignment screen.
- **Four slots**, all open from the start, on **Ctrl+1 to Ctrl+4**. They are rebindable actions `ART_1`–`ART_4` in `SettingsManager`, and a key always means the same slot.
- **States shown:**
  - ready;
  - cooling (dimmed, with turns left);
  - kept but not retrained (a hollow rank mark, doubled cooldown);
  - Held (Held Arrow while readied).
- **Placement:** a second strip of the same shape beside the spell slots. The layout is coordinated with "Wayfinder: Complete UI/UX Overhaul" (#209).
  - *As built (#245):* the strip sits directly above the spell strip, because five spell slots and four full Art names do not fit side by side on the 1920 canvas. The hollow rank mark is the rank letter standing bare and dim, where a trained Art's is framed ("[E]"): `intellivision.ttf` draws parentheses as brackets.
- **UI rules:** `UiTheme`/`HudSkin` tokens, `UiNames`, Scene2D tables, and `UiGlyphs.sanitize` on every string.

## 6. Showing Mastery and Arts ([#238](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/238))

- **Skill Tree screen (K):** a Mastery section listing all ten Families with exact counts, for example "Axe: Adept, 62 / 150 full hits". It also lists the soul's kept Arts, with "retrain at Adept" where the body is below the rank.
- **Item inspector:** the weapon's Family and your rank in it, by rank name only.
- **Learning an Art:** the first time the soul learns one, a card in the style of the Discovery card shows its name, Family, rank and a one-line effect. When a body retrains a kept Art: *"Your arm remembers Ringing Blow."*
- **When a Passive fires:** a short prefix on the floating damage number, and **no** log line. The melee words (#243) are:

  | Passive | Word |
  |---|---|
  | Ringing Blow | DAZE, or STUN |
  | Cut the Sinew | BLEED SLOW |
  | Wide Chop | +1 CUT, on each free cut |
  | Quick Knuckles | +n BLOW, carrying the second blow's damage |
  | Answering Steel | COUNTER |
  | Hold the Line | FIRST STRIKE |

  A free strike that misses shows "<word> MISS". Ranged Passives add PIN and their own words.
- **Free strikes** (Answering Steel, Wide Chop, Hold the Line) are full swings of the weapon that fired the Passive. A hit trains Mastery like any full hit. A free strike never draws a return blow and never fires another Passive.
- **Hold the Line** fires when a monster *steps* next to you by its own move, once per monster. Spawning beside you, or being pulled in by Reel In, does not count.
- **Actives and capstones:** a combat log line.
- **Pause ledger:** adds "Arts remembered: n".

## 7. Balance (#226, [#233](https://github.com/bluephoenixmedia/Tarmin2Revised/issues/233), #239)

- **The power budget sits inside the 0.0.2 targets.** A fresh body fights at today's numbers; Mastery and Arts pay for the deeper bands.
- **The depth-3 check passes** in the realistic simulator model (`--approach`): Adept Blade wins 88.8%. The plain duel's 79.1% ignored the stuns the game already deals.
- **The open problem is Family spread.** At Adept, depth 3:

  | Weapon | Wins |
  |---|---|
  | Sword, mace | 87–89% |
  | Spear | 76–81% |
  | Axe | 79–82% |
  | Dagger | 57–70% |
  | Unarmed | 19–27% |

  Answering Steel (+0.1) and Ringing Blow (+2.1) barely register at their catalogue chances. Deciding how to treat the weak Families is a separate issue for the user.
- **Telemetry:** every Art use and proc goes into `artUses`.

## Out of scope

- Monsters using Combat Arts: a future effort.
- Morrowind-style attribute growth from use.
