# Requirements: The Bridge Integrity Boss

**Status**: Not started. Deferred from the `bugs_9_24_26.md` pass.
**Date**: September 2026
**Related Documents**:
- `docs/DEsign/Themed Chunk Contract.md`
- `docs/Implementation Plan_ The Expedition Loop & Progression Reboot.md`

---

## 1. The ask

> When bridge integrity reaches 100%, a boss should spawn on the first chunk
> that the player must kill in order to reset the integrity level. This will
> require the player to prepare carefully for this scenario by storing items in
> their shelter chest (meaning they also need to unlock the chest and as many
> other shelter amenities as possible).

The intent is a forced confrontation that converts the death counter from a
slow slide into an event you can prepare for and survive.

---

## 2. How bridge integrity actually works today

`DoomManager` owns it, and it is **derived, not stored**:

```java
private static final int MAX_DEATHS_ALLOWED = 50;   // the hard cap
private int deathCount = 0;
// getBridgeIntegrity() == deathCount / 50 * 100
```

| Fact | Detail |
| :--- | :--- |
| Source | `deathCount`, incremented once per death in `incrementDeaths()` |
| Scale | 50 deaths = 100%. One death is **2%** |
| Persistence | Saved per slot in `doom_state.json` |
| At 100% | `SaveManager.wipeActiveSlotOnApocalypse()` — the save is destroyed |
| Reset | `resetDeaths()` exists and nothing in the game calls it |
| Read by | `Hud` (the integrity bar), `GameOverScreen`, and the sky shader's `u_doom` tint |
| Also drives | `getDoomStage()` 1–4, from `deathCount` **or** `expeditionTurns` |

> [!IMPORTANT]
> 100% integrity currently means **the save is wiped**. The feature as written
> replaces an apocalypse with a boss fight. That is a major change to what the
> death counter means and must be a deliberate decision, not a side effect: a
> player who has survived 49 deaths is currently one death from losing
> everything, and under this feature they would instead get a boss.

---

## 3. The decision this document exists to force

**What does "reset the integrity level" mean numerically?**

| Option | Effect | Consequence |
| :--- | :--- | :--- |
| **(a) Full reset** | `deathCount = 0` | The run becomes indefinitely survivable. A strong player never faces the apocalypse, and the 50-death cap stops meaning anything |
| **(b) Partial cut** | e.g. `deathCount -= 15` (30%) | The counter still climbs over a long enough campaign; the boss buys time rather than absolution |
| **(c) Diminishing** | First win −25, second −15, third −10… | Each victory is worth less, so the end still arrives but the player chooses when to spend the effort |

**Recommendation: (b), a flat 30% cut.** (a) removes the pressure the whole
Doom system exists to create. (c) is the most interesting but needs a second
persisted counter (wins so far) and a curve to tune, which is a lot of
machinery for a mechanic that has not been played yet. (b) is one line,
preserves the eventual apocalypse, and can become (c) later without changing
anything the player sees.

**If the boss is not killed, what happens?** The apocalypse must still exist or
integrity has no teeth. Proposal: reaching 100% spawns the boss *instead of*
wiping the save, but the next death while the boss is alive wipes it. The
player gets exactly one chance.

---

## 4. Where the boss lives

"The first chunk" is chunk (0, 0) on level 1 — the shelter chunk. That is the
player's safe room, which creates three problems:

1. The shelter is where the player stores and prepares. A boss standing in it
   denies them the preparation the feature is meant to reward.
2. `getChunkTheme` explicitly never themes the shelter chunk, and the shelter
   is generated from a fixed `homeTile` layout with no room for a boss arena.
3. The player respawns there. A boss on the respawn tile is a death loop.

**Recommendation:** spawn it in the chunk **adjacent to** the shelter, on the
side the player first exits, and seal that chunk the way a themed chunk seals —
reusing `ThemeObjectiveState`, the Rune of Surrender and the existing
`SLAY_CHAMPION` objective rather than inventing a parallel system. The shelter
stays a sanctuary; the boss stands between the player and everything else.

---

## 5. Which boss

`MonsterType` already has suitable entries that no generator currently spawns
as a champion: `BRINGER_OF_DEATH`, `FALL_ANGEL`, `AGIS`, `LICH`, `IRON_GOLEM`.
`BRINGER_OF_DEATH` has licensed animated art (Clembod, credit required — see
`docs/asset-licenses.md`) and fits a bridge-of-souls fiction.

The boss must be meaningfully harder than a themed-chunk champion, which
currently tops out around `championHpBonus: 80`. It should also scale with
`deathCount`, so a player who reached 100% slowly meets a different fight from
one who got there in a hurry.

---

## 6. How it satisfies the themed chunk contract

If the boss chunk seals, it is a themed chunk and must fill all seven slots of
`docs/DEsign/Themed Chunk Contract.md`, enforced by `ThemeContractTest`:

| Slot | Content |
| :--- | :--- |
| (a) Layout | An arena carve, as Blood Colosseum does |
| (b) Props | Bone and ruin props from the existing catalogue |
| (c) Hazard | Proposal: the bridge itself — tiles that collapse as integrity burns |
| (d) Encounters | The boss alone, or the boss plus a small honour guard |
| (e) Objective | `SLAY_CHAMPION`, already implemented |
| (f) Reward | Crests, **plus** the integrity cut, which is the real prize |
| (g) Signature | Red-black fog, the existing `amb_doom_subbass` stinger |

---

## 7. The preparation loop the feature is really about

The stated purpose is to make the shelter chest and its amenities matter. That
only works if the player can **see it coming**. Requirements:

- The HUD integrity bar must warn well before 100% — a distinct state at, say,
  90%, not just a number creeping up.
- The player must be able to return to the shelter and prepare after the warning
  and before the fight. If the boss spawns the instant integrity hits 100%, the
  preparation the feature exists to reward is impossible.
- Recommendation: the boss spawns on the **next expedition start** after
  integrity reaches 100%, not mid-run. The shelter visit between runs is the
  preparation window.

---

## 8. Definition of done

- [ ] 100% integrity spawns the boss instead of wiping the slot.
- [ ] A death while the boss lives still wipes the slot.
- [ ] Killing the boss cuts integrity by the agreed amount and the HUD reflects it.
- [ ] The boss spawns adjacent to the shelter, never inside it, never on the respawn tile.
- [ ] The boss chunk fills all seven contract slots and passes `ThemeContractTest`.
- [ ] The HUD warns at a threshold below 100%, with time to prepare.
- [ ] `resetDeaths()` — currently dead code — is either used here or deleted.
- [ ] A test that integrity at 100% produces a boss and not a wipe.

---

## 9. Risks

**This changes what death means.** Every existing save carries a `deathCount`;
one at 49 deaths becomes a boss encounter rather than a wipe. That is arguably
a gift, but it is a silent change to a save's fate and should be stated in the
release notes rather than discovered.

**The apocalypse may become unreachable.** With option (a) it certainly does.
Even with (b), a player who beats the boss reliably has effectively removed the
hard cap. If the 50-death apocalypse is meant to be a real ending, the boss
must get harder each time it is summoned.
