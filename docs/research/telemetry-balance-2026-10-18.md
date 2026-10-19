# Balance from telemetry, 2026-10-18

Twelve lives on MEDIUM with no cheats: nine from 0.0.2-SNAPSHOT (10-16 to 10-17) and three from 0.0.3-SNAPSHOT (10-18, 22:56 to 23:01). Read with `tools/telemetry/aggregate.py` and a per-fight dump. Targets are from `docs/balance/0.0.2-targets.md`.

## Headline

| | Target | All 12 lives | Latest 3 |
|---|---|---|---|
| Depth-1 fights won | about 95% | **83%** (25 of 30) | 100% (6 of 6) |
| Depth-1 HP lost per fight | 20-35% of about 22 | 8.5 median (39%) | 3.5 median (16%) |
| Monster hit rate | 55-65% unarmoured; 10-20 points less armoured | 67% | 65% at depth 1, 83% at depth 2 |
| Player full-hit rate (melee) | 65-75% | 69% | 71% |
| Outcomes | | 10 died, 2 abandoned, none returned | 3 died |

The latest three lives wore armour: damage absorbed by armour was 19 and 69 HP, where nearly every earlier life had 0. They won every depth-1 fight. They then died at depth 2 to a Goblin, at depth 1 to a Spider fight followed by bleed-out, and at depth 3 to an Orc while parched. This is not enough lives to say that depth 1 is fixed.

## Findings

1. **Monsters still out-act the player at depth 1.** Across all fights the monster made 348 attacks to the player's 252, a ratio of 1.38. By depth:

   | Depth | Monster attacks per player attack |
   |---|---|
   | 1 | **1.83** (192 / 105) |
   | 2 | 1.25 (81 / 65) |
   | 3 | 0.91 (75 / 82) |

   The gap is the ranged Soldiers (Skeleton, Goblin, Giant Ant, Spider) shooting during the approach, and fast fodder taking several actions per player action. One Giant Bee fight went 15 attacks to 2, and one Bat fight 15 to 4. The duel simulator models neither, which is why it reports depth 1 at 95%. Mastery's +1 to-hit per rank does not touch this; hit rate is already on target.

2. **The heal is almost never used.** MP moved in one life of the latest three (3 MP, one Cure Wounds), and in none of the nine before. Telemetry has no count of spells cast, so a heal can only be inferred from MP spent.

3. **Thirst kills lives that otherwise go well.** Two of the twelve lives were thirsty: both were the deepest runs, and both died "while parched".
   - The depth-3 life on 10-18 spent 159 turns thirsty and drank **0** times, though every body starts with a full Waterskin (five drinks).
   - All three of the latest lives drank 0 times. The THIRST hint ("drink from your waterskin, or face water to refill it") is shown once per profile and may have been dismissed long ago.

4. **Bleed-out can follow a fight at once.** The 10-18 23:00 life took 18 HP in an 8-turn Spider fight and died the turn it ended, with the epitaph "Bled out from untended wounds". That is accurate, because the last tick was the bleed, but in play it reads as a Spider death.

5. **Levelling stays rare.** One level-up in twelve lives (turn 628 of the depth-3 run). Most lives end at level 1, so the Trivial rule for Mastery has still never applied.

6. **Telemetry is blind to the new systems.** There is no Weapon Family, Mastery or spell-cast data in the expedition file, so the Mastery slice merged on 10-18 cannot be read from these lives. It is not certain whether the latest three ran with it.

## Implications for the Combat Arts map

- Mastery's budget is to-hit, and to-hit is on target. The depth-1 shortfall is **tempo**: who acts first and how often. That is what the Combat Art catalogues can answer: Overwatch against an approaching archer, Pin Down, a Blunt stagger, Polearm reach, a Dagger first strike. The catalogue tickets should weigh Arts against this gap explicitly.
- The acceptance check (an Adept character at depth 3 winning at least 85%) is measured in a simulator that ignores ranged approach and speed. It will overstate how much Mastery helps. Teaching the simulator ranged approach and multi-action monsters belongs with the "Duel simulator models Mastery" issue.
- Telemetry for Mastery, Arts and spells cast should land before the Arts, or their effect cannot be tuned.
