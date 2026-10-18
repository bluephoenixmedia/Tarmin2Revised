# Research: how Caves of Qud's martial skills are built

Ticket: #222, part of map #221 "Wayfinder: Combat Arts & Weapon Mastery".
Researched 2026-10-18. This is a reference for the melee and ranged Combat Art
catalogue tickets. It does not choose the catalogue.

Vocabulary: in Tarmin a **Weapon Family** trains **Mastery**, and Mastery teaches
**Combat Arts** (Active or Passive). Qud calls its trees "skills" and its nodes
"powers". This file keeps Qud's words when describing Qud, and Tarmin's words
when describing Tarmin.

## Sources and how far to trust them

- **Caves of Qud** is closed-source. The primary source available is the official
  community wiki, `wiki.cavesofqud.com`, which takes most numbers from the game's
  data files. Every Qud number below comes from it, one page per tree or power.
  Where a page said nothing (no save, no cooldown), the table says "none listed".
  That means the page is silent, not that the game has no such rule.
- **XCOM 2** numbers come from the game's own UnrealScript, as republished in the
  X2 Community Highlander repository. The 0.7 reaction-fire multiplier is a config
  value; the wiki states it and the code reads it as `REACTION_FINALMOD`.
- **Cataclysm: DDA** headshot rules come from its source on GitHub.
- Qud cooldowns scale with Willpower. Each point above 16 cuts 5% of the base
  cooldown, and each point below 16 adds 5%. The cut is capped at 80% (at WIL 32),
  with a floor of 5 rounds. So a "base 50" Slam runs 68 rounds at WIL 9 and 10 at
  WIL 32. ([Willpower](https://wiki.cavesofqud.com/wiki/Willpower),
  [Slam](https://wiki.cavesofqud.com/wiki/Slam))

## How Qud's trees are shaped

- **A tree is bought, then its powers.** The tree costs 50 to 100 sp and comes
  with a free proficiency power, which is always +to-hit or an action-cost cut.
  Each later power costs 100 to 400 sp and needs a minimum attribute. Attribute
  gates rise down the tree: 17, 19, 21, 23, 25, 27, 29.
  ([Skills](https://wiki.cavesofqud.com/wiki/Skills))
- **Growth is bought with skill points, never trained by use.** This is the one
  place Tarmin deliberately differs.
- **Actives have no resource cost.** You pay with the action and a cooldown.
  Typical base cooldowns are 10 (Conk), 15 (Lunge, Swipe), 30 (Dismember, Hobble),
  50 (Slam, Hook and Drag) and 100 (the capstones).
- **Each tree has one capstone of the same shape.** For 5 to 10 rounds it removes
  the cooldown or makes a proc certain: Berserk!, Demolish, En Garde!, Ultra Fire,
  Empty the Clips. Using it puts the signature Active on cooldown, so the two
  cannot be stacked back to back.
- **Passives are gated by penetration.** In Qud a hit can land and still fail to
  penetrate armour (PV against AV). Many procs fire only "on penetrating hit"
  (Bloodletter, Dismember, Suppressive Fire). Others fire "on hit, no penetration
  needed" (Conk, Slam). The choice decides whether armour counters the Art.
  ([Penetration](https://wiki.cavesofqud.com/wiki/Penetration))
- **Conditions escalate.** Daze on a dazed target becomes stun; Conk on a stunned
  target becomes unconscious; Shank gains +2 penetration rolls for each negative
  status on the target. The tree reads as a combo chain even though every link is
  an ordinary attack.

## Melee trees

### Axe ([Axe](https://wiki.cavesofqud.com/wiki/Axe), [Dismember](https://wiki.cavesofqud.com/wiki/Dismember))

| Power | Kind | Effect | Trigger / cooldown | Counterplay |
|---|---|---|---|---|
| Axe Proficiency | Passive | +2 to hit with axes | Always on | - |
| Cleave | Passive | -1 AV on the target per cleave, stacking to half the Strength modifier (rounded up); +1 more on a crit | 75% on each axe hit | None listed. Shreds armour, so it opens up penetration-gated procs |
| Charging Strike (Axe) | Passive | An axe charge that cleaves adds another -1 AV; with Dismember, doubles the dismember chance on charges | Axe charge hit (needs Cleave and Charge) | - |
| Dismember | Active, plus passive chance | Attacks an adjacent target; a penetrating hit severs a random limb and causes bleeding (1-2 per turn) | Active: base cooldown 30. Passive: 3% (one-handed) or 6% (two-handed) on penetrating axe hits | Toughness save DC 35 against the bleed. Needs penetration, so high AV blocks it |
| Hook and Drag | Active | Grabs a limb and pulls the target with you as you move, with a free axe attack. Lasts 9 rounds or until the limb is severed | Base cooldown 50 | Strength save, DC 20 + your Strength modifier, both to resist and at the start of each of its turns to break free. The dragged target can still attack |
| Decapitate | Passive | Adds heads to Dismember's targets. Removing an organic creature's only head kills it outright; a creature with several heads bleeds 2-3 per turn | Through Dismember | Toughness DC 35 against the bleed (several heads only). Creatures with no head, or with several, survive |
| Berserk! | Active (capstone) | For 5 rounds, dismember chance on axe attacks is 100% | Base cooldown 100; needs Dismember ready, and puts it on cooldown | - |

### Cudgel ([Cudgel](https://wiki.cavesofqud.com/wiki/Cudgel), [Conk](https://wiki.cavesofqud.com/wiki/Conk), [Slam](https://wiki.cavesofqud.com/wiki/Slam), [Dazed](https://wiki.cavesofqud.com/wiki/Dazed))

| Power | Kind | Effect | Trigger / cooldown | Counterplay |
|---|---|---|---|---|
| Cudgel Proficiency | Passive | +2 to hit with cudgels. Improvised (non-weapon) items fall under Cudgel | Always on | - |
| Bludgeon | Passive | Dazes for 3-4 rounds: -4 Agility, -4 Intelligence, -10 move speed. Dazing a dazed target stuns it for 1 round instead | 50% on each cudgel hit | The stun from stacked daze allows a DC 30 save. Stunned creatures save each turn (Toughness, DC 15 by default) |
| Charging Strike (Cudgel) | Passive | A cudgel charge that hits always dazes | Charge hit (needs Bludgeon and Charge) | - |
| Backswing | Passive | Free extra attack with the same hand | 25% after a primary-hand cudgel attack | - |
| Conk | Active | Attacks an adjacent target; a hit always dazes. If the target is already stunned, it is knocked unconscious for 30-40 rounds, and wakes dazed when damaged | Base cooldown 10. Applies on hit, no penetration needed | None listed. Damage wakes the target, so it combos badly with allies |
| Slam | Active | Attack with +1 to the penetration roll. On a hit, knocks the target back up to 3 spaces and stuns it for 1 round plus 1 per space. A target pushed into a wall takes weapon damage again; walls, doors and creatures with AV below 5 x Strength modifier are broken through | Base cooldown 50. On hit, no penetration needed | The AV threshold: anything with AV of 5 x your Strength modifier or more stops the push |
| Demolish | Active (capstone) | For 5 rounds, 100% daze chance and no cooldown on Slam | Base cooldown 100; needs Slam ready, and puts it on cooldown | - |

### Long Blade ([Long Blade](https://wiki.cavesofqud.com/wiki/Long_Blade))

The Long Blade tree is built on **stances**: Aggressive, Defensive and Dueling.
Each Active does something different in each stance, so one Active gives three
moves.

| Power | Kind | Effect | Trigger / cooldown | Counterplay |
|---|---|---|---|---|
| Long Blade (tree) | Passive | Long-blade crits gain +2 PV, even past the penetration cap | On crit | - |
| Long Blade Proficiency | Passive (stances) | Aggressive: +1 penetration, -2 to hit. Defensive: +2 DV | While wielding | - |
| Dueling Stance | Passive (stance) | +2 to hit with long and short blades | While wielding | - |
| Improved Aggressive / Defensive / Dueling | Passive | Aggressive becomes +2 pen / -3 hit; Defensive becomes +3 DV; Dueling becomes +3 hit | While in the stance | - |
| Lunge | Active | Aggressive: dash through an empty space to attack at +2 pen. Defensive: attack, then leap back 2 spaces. Dueling: +1 pen, sure to hit and to penetrate once | Base cooldown 15 | Aggressive needs an empty space to cross |
| Swipe | Active | Aggressive: attack every adjacent enemy. Defensive: push every adjacent creature back 1 and try to trip it. Dueling: one attack plus a disarm attempt, sure to hit and to penetrate once | Base cooldown 15 | Trip: Strength save, DC 30. Disarm: Strength save, DC 25 + your Agility modifier |
| En Garde! | Active (capstone) | For 10 rounds, no cooldown on Lunge or Swipe | Base cooldown 100 | - |

### Short Blade ([Short Blade](https://wiki.cavesofqud.com/wiki/Short_Blade), [Hobble](https://wiki.cavesofqud.com/wiki/Hobble), [Bleeding](https://wiki.cavesofqud.com/wiki/Bleeding))

| Power | Kind | Effect | Trigger / cooldown | Counterplay |
|---|---|---|---|---|
| Short Blade Expertise | Passive | +1 to hit; primary-hand short-blade attacks cost 25% less action | Always on | - |
| Jab | Passive | Attacks again at once with the same offhand weapon | Every offhand short-blade attack (Flurry excluded) | - |
| Bloodletter | Passive | Bleed of 0-1 per turn, stacking up to 1 + Agility modifier | 75% on penetrating short-blade hits | Toughness save, DC 20 + Agility modifier, dropping by 1 each round. Bandages, Staunch Wounds and meditation shorten it |
| Hobble | Active | Primary-hand attack that penetrates exactly once and hobbles the target: -50% move speed for 16-20 rounds | Base cooldown 30 | None listed |
| Pointed Circle | Passive | A free attack | Every Juke with a primary short blade. Its bleed chance is 100% with Bloodletter | - |
| Rejoinder | Passive (reaction) | A free short-blade attack | 40% when an enemy misses you in melee, once per turn | Not attacking the duellist |
| Shank | Active | Attack at +2 penetration rolls for each negative status on the target | Cooldown 10 (primary hand) or 20 (offhand) | Few statuses, little bonus. The Art is a payoff for everything else in the tree |

### Supporting melee trees

These trees work across weapon kinds rather than for one weapon.

| Tree / power | Kind | Effect | Trigger / cooldown | Counterplay |
|---|---|---|---|---|
| Tactics: Charge ([Tactics](https://wiki.cavesofqud.com/wiki/Tactics)) | Active move | Move 2-3 squares and attack at +1 penetration. Feeds both Charging Strikes and a free Shield Slam | None listed | Needs a clear lane |
| Tactics: Juke | Active | Move one square at no action cost, or swap places with an enemy | Cooldown 40 | - |
| Tactics: Kickback | Passive | Fire a missile weapon at an adjacent target and try to push it back | Ranged attack in melee range | - |
| Single Weapon Fighting: Opportune Attacks ([SWF](https://wiki.cavesofqud.com/wiki/Single_Weapon_Fighting)) | Passive | Refreshes the cooldowns of up to two random weapon or shield powers | Melee crit while fighting with one weapon | - |
| SWF: Weapon Expertise / Weapon Mastery | Passive | A free second attack, at 50% chance (Expertise) or every time (Mastery) | Every single-weapon melee attack | - |
| SWF: Penetrating Strikes | Passive | +1 penetration on every attack | Always on | - |
| Shield: Block / Deft / Swift ([Shield](https://wiki.cavesofqud.com/wiki/Shield)) | Passive | 50% (Deft: 75%) chance to block one melee attack per round (Swift: two), adding the shield's AV | Incoming melee | - |
| Shield: Shield Slam | Active | Knockdown attempt dealing (Str mod)d2 + shield AV damage. Free when charging with a shield | Cooldown 40 | Strength save, DC 20 + your Strength modifier |
| Shield: Staggering Block | Passive (reaction) | Stuns the attacker for 1d2 rounds | (Str x 2 - 35)% on each block | - |
| Shield: Shield Wall | Active | Blocks every melee attack for 3 rounds | None listed | Ranged attacks |
| Endurance: Shake It Off ([page](https://wiki.cavesofqud.com/wiki/Shake_It_Off)) | Passive (defence) | (Toughness - 10)% chance each round to recover from daze or stun; poison does 25% less and is 25% shorter | Each round | This is the player's counterplay to stun |

## Counterplay: how Qud resists each condition

| Condition | Resisted by | Recovery |
|---|---|---|
| Stun | Toughness save each turn, DC 15 by default (higher for some sources). Almost nothing is immune; even robots and oozes can be stunned. The stun from stacked daze saves at DC 30 | Shake It Off, padded helmets, high Toughness ([Stun](https://wiki.cavesofqud.com/wiki/Stun)) |
| Daze | No save against the initial daze is listed. Its threat is that a second daze becomes a stun | Times out in 3-4 rounds |
| Bleed | Toughness save each turn against the source's DC, which drops by 1 every turn until a save succeeds. Some bleeds stack; others replace a weaker one | A bandage (one turn, DC drops by about 7), Staunch Wounds doubles that, meditation triples the save rate, Recuperating or Nostrums stop it outright ([Bleeding](https://wiki.cavesofqud.com/wiki/Bleeding)) |
| Knockback (Slam) | A wall of AV at or above 5 x the attacker's Strength modifier stops it; there is no save | - |
| Pull, trip, disarm | Strength saves (Hook and Drag: 20 + Str mod; trip: 30; disarm: 25 + Agi mod) | Hook and Drag allows a new save every turn |
| Penetration-gated procs | Armour. AV that stops penetration stops the proc | Cleave strips AV, so Axe brings its own answer |

Tarmin already has half of this: monsters take `BLEEDING`, and stun carries an
immunity window afterwards (`Monster.stunTurns`, `stunImmunityTurns`). Qud has no
general stun-immunity window. It relies on per-turn saves and on daze-before-stun
escalation instead.

## Ranged

### Bow and Rifle ([Bow and Rifle](https://wiki.cavesofqud.com/wiki/Bow_and_Rifle), [Draw a Bead](https://wiki.cavesofqud.com/wiki/Draw_a_Bead), [Suppressive Fire](https://wiki.cavesofqud.com/wiki/Suppressive_Fire))

The Bow and Rifle tree runs on a **mark**. Draw a Bead costs a turn and marks one
target you can see. The mark falls off if you lose sight of the target or mark
another, and each "Fire" power spends it. A Fire shot therefore always costs two
actions, the mark and the shot, with no cooldown. Crossbows have no tree of
their own in Qud's list.

| Power | Kind | Effect | Trigger / cooldown | Counterplay |
|---|---|---|---|---|
| Steady Hands | Passive | Agility counts as 4 higher for bow and rifle accuracy | Always on | - |
| Draw a Bead | Active (setup) | Marks a target: +2 effective Agility for accuracy against it | One turn. One mark at a time | Breaking line of sight removes the mark |
| Sure Fire | Active (spends the mark) | A shot with no accuracy penalty (the weapon's own accuracy still applies) | Needs a mark | - |
| Suppressive Fire | Active (spends the mark) | On a penetrating hit the target is **suppressed**: cannot move for 3-5 rounds | Needs a mark | Penetration-gated; no save listed |
| Wounding Fire | Active (spends the mark) | Each penetration bleeds 1 per round | Needs a mark | Bleed save DC 20 + the weapon's max damage |
| Disorienting Fire | Upgrade | Wounding Fire also gives -4 DV and -4 MA | Target cannot see or hear any faction ally | Stay grouped |
| Flattening Fire | Upgrade | Suppressive Fire also knocks prone and disarms | Target is surrounded by 6 or more solid obstacles (in a corridor or corner) | Stay in the open |
| Beacon Fire | Upgrade | Sure Fire is always a crit | Target is burning, luminous or phosphorescent | Stay dark |
| Ultra Fire | Active (capstone) | Every Fire effect you own in one shot | Base cooldown 100 | - |

The upgrades are a good shape: each is a free bonus when the **target's
situation** matches (isolated, cornered, lit). They reward the player for reading
the board, and need no new button.

### Pistol ([Pistol](https://wiki.cavesofqud.com/wiki/Pistol), [Disarming Shot](https://wiki.cavesofqud.com/wiki/Disarming_Shot), [Dead Shot](https://wiki.cavesofqud.com/wiki/Dead_Shot))

| Power | Kind | Effect | Trigger / cooldown | Counterplay |
|---|---|---|---|---|
| Steady Hand | Passive | Agility counts as 4 higher for pistol accuracy | Always on | - |
| Akimbo | Passive | With several guns wielded, one ranged attack fires each of them | Every shot | - |
| Weak Spotter | Passive | Pistol crit chance goes from 5% to 10% | Always on | - |
| Sling and Run | Passive | No accuracy penalty when firing while running | - | - |
| Disarming Shot | Passive | Each hit has a chance equal to your Agility modifier, as a percent, to disarm | Rolled per hit, so many-shot guns disarm often | None listed |
| Dead Shot | Passive | Pistol crits get +4 PV instead of +2 | Pistol crit | - |
| Empty the Clips | Active (capstone) | For 20 rounds, firing a pistol takes half a round | Base cooldown 200 | - |
| Fastest Gun in the Rust | Passive | Firing a pistol costs 25% less action | Always on | - |

### Other ranged trees

- **Heavy Weapon** ([page](https://wiki.cavesofqud.com/wiki/Heavy_Weapon)):
  Strapping Shoulders (half weight), Tank (no movement penalty), Sweep (five
  shots in a 90-degree cone). Sweep is Qud's spray or splash shape.
- **Thrown**: Qud has no thrown tree. Tactics' Deft Throwing (+3 range, half the
  throw variance within that range) is all there is
  ([Tactics](https://wiki.cavesofqud.com/wiki/Tactics)).

### Where Overwatch and Pin Down come from: XCOM

Source: XCOM 2's UnrealScript (`X2Ability_DefaultAbilitySet.uc`,
`X2Ability_GrenadierAbilitySet.uc`, `X2AbilityToHitCalc_StandardAim.uc`) in the
[X2 Community Highlander](https://github.com/X2CommunityCore/X2WOTCCommunityHighlander).

- **Overwatch is a reserved action, not a stance.** Activating it costs every
  action point you have left. It converts them into an `OverwatchReserveActionPoint`.
  The reaction shot `OverwatchShot` listens for the `ObjectMoved` event and spends
  that point and 1 ammo to shoot a hostile that moves inside weapon range and in
  plain sight. Peek tiles are not used. One reserved point means one shot.
- **The reaction shot is worse than an aimed one.** `bReactionFire` multiplies
  the hit chance by `REACTION_FINALMOD` (0.7, or 0.6 against a dashing target,
  per the [XCOM wiki](https://xcom.fandom.com/wiki/Reaction_Shot)), ignores the
  flanking bonus, and removes crit chance entirely unless an effect allows it.
  Concealed shooters cannot take it.
- **Its counterplay is built in.** Lightning Reflexes makes the first reaction
  shot against a unit miss, which the code checks for directly (`bLightningReflexes`).
  Flush cancels a unit's Overwatch. A suppressed or disoriented unit cannot go on
  Overwatch.
- **Suppression (Pin Down) costs 2 ammo and all remaining actions.** It reserves a
  `Suppression` action point that fires `SuppressionShot` if the target moves.
  The target takes an aim penalty: -30 in the first game, per the
  [XCOM wiki](https://xcom.fandom.com/wiki/Suppression). The effect lasts until
  the shooter's next turn, and ends early if the target dies **or the suppressor
  takes damage**. The pin is a threat, not a hold: the target can still move if
  it accepts the shot.
- Qud's Suppressive Fire is the hard version: no movement for 3-5 rounds, gated by
  penetration. XCOM's is soft: move and take a shot, plus an aim penalty.

### Headshots in roguelikes: Cataclysm: DDA

Source: [`game_constants.h`](https://github.com/CleverRaven/Cataclysm-DDA/blob/master/src/game_constants.h)
and [`creature.cpp`](https://github.com/CleverRaven/Cataclysm-DDA/blob/master/src/creature.cpp).

- Each shot produces `missed_by`, from 0.0 (perfect) to 1.0 (a miss), out of
  aim, dispersion and distance. Spending turns aiming lowers it.
- Hit tiers are thresholds on that number: `accuracy_headshot = 0.1`,
  `accuracy_critical = 0.2`, `accuracy_goodhit = 0.5`, `accuracy_standard = 0.8`,
  `accuracy_grazing = 1.0`.
- A headshot ("Critical!!") needs the shot under 0.1 **and** the hit to land on
  a head, or to be fatal anyway. Monsters with the `NOHEAD` flag can never be
  headshot, and monsters declare their own weak points in JSON.
- For Tarmin: a headshot works as the top tier of an aimed shot, gated by
  anatomy. Undead, oozes and constructs can be marked headless, which gives
  counterplay for free.
- Qud's head rule is similar. Decapitate kills outright only an organic creature
  with exactly one head.

## Shapes worth borrowing for Tarmin

These are shapes for the catalogue tickets to choose from. None of them is a
decision.

| Tarmin Family | Closest Qud tree | Shapes worth borrowing |
|---|---|---|
| **Blade** | Long Blade | One Active that reads a **stance** or context and does one of three things (Lunge, Swipe). The crit adds PV (penetration). A cleave of every adjacent enemy (Aggressive Swipe). Disarm with a Strength save (Dueling Swipe) |
| **Dagger** | Short Blade | Cheaper attacks (-25% action). A bleed proc whose save DC falls each turn (Bloodletter). A **payoff Active that scales with the number of statuses on the target** (Shank). A riposte when an enemy misses you (Rejoinder). Hobble (-50% move speed) |
| **Axe** | Axe | Armour shred that stacks per hit (Cleave), feeding the penetration-gated Arts. A rare passive sever plus an Active sever on cooldown (Dismember). A capstone that makes the proc certain for N turns and puts the signature Active on cooldown (Berserk!). Decapitation gated by anatomy |
| **Blunt** | Cudgel | **Daze, then stun on a second daze** (Bludgeon): an auto-combo that needs no button and works with Tarmin's stun-immunity window. A knockback Active whose push is blocked by an AV threshold and whose wall impact does extra damage (Slam). A **finisher Active that pays off on a stunned target** (Conk: daze, or unconscious if already stunned) |
| **Polearm** | (none; Qud has no polearm tree) | Borrow Hook and Drag's pull (Strength save each turn), a Defensive-stance push of every adjacent creature, and Charge's run-in with +penetration. Reach weapons in `weapons.json` make "attack from 2 tiles" a natural Passive |
| **Bow** | Bow and Rifle | **Mark, then spend** (Draw a Bead, then Fire): the cost is a setup turn, not a cooldown, and losing sight of the target loses the mark. **Free upgrades when the target's situation matches** (isolated, cornered, lit). Suppressive Fire as a pin gated by penetration |
| **Crossbow** | (shares Bow and Rifle in Qud) | A crossbow already pays a reload, so "Sure Fire" (ignore accuracy penalties) and "Flattening Fire" (prone and disarm when the target is cornered) suit a slow, heavy single shot better than a fast bow volley |
| **Thrown** | Tactics: Deft Throwing | Qud gives Thrown almost nothing, so the room is open. Borrow Deft Throwing's +range and lower variance, and Kickback's push in melee range |
| **Firearm** | Pistol | Disarm chance rolled per hit (Disarming Shot). Crits gain penetration (Dead Shot). A capstone that halves the action cost for N turns (Empty the Clips); for Tarmin this could halve the reload instead. Firearms stay hitscan |
| **Unarmed** | (none; Qud folds improvised items into Cudgel) | Unarmed can take Cudgel's daze, then stun, combo, and Short Blade's extra attacks (Jab, Backswing) |

### Ranged readied actions (Overwatch, Pin Down)

- **Overwatch** follows XCOM: spend the turn to reserve one reaction shot,
  triggered by an enemy moving in line of sight and range. The shot fires at
  reduced accuracy (x0.7) and cannot crit. XCOM's counters translate directly:
  "first reaction shot misses" for fast monsters, and Overwatch breaks when the
  shooter is hit.
- **Pin Down** comes in two shapes. Qud's is hard: the target cannot move for 3-5
  rounds, gated by penetration. XCOM's is soft: an aim penalty, a shot if the
  target moves, and the pin ends when the shooter is damaged.
- **Headshot** follows CDDA: a top accuracy tier, gated by the monster having a
  head. Qud's Beacon Fire suggests the alternative of "auto-crit when the target
  is lit", which ties into Tarmin's lighting.

### Splash

Splash is barely in Qud's weapon trees. Sweep, a five-shot 90-degree cone, is the
only area attack. Splash damage will need a shape from somewhere else, and is
listed under "Not yet specified" on map #221.
