# Implementation plan: throwing from Quick Slots

**Status:** designed, not implemented
**Origin:** `docs/polish/polish_9_17_26.md` — *"If a weapon is placed in the Quick Slots, the intent of
doing that should be that the player will throw that weapon by pressing the quick slot key."*

Putting a dagger in a quick slot should mean *"I intend to throw this"*. Today it means *"swap this
into my right hand"*, and throwing is reachable only by equipping a weapon first and pressing `A`.

---

## 1. What exists today

**Quick slots** are `Item[6]` on `Inventory`. Pressing `1`–`6` routes through one of two handlers
depending on combat state, and **both end up in the same method**:

| Context | Handler | Ends at |
|---|---|---|
| Exploring (`INACTIVE`) | `GameScreen` key block (`NUM_1`–`NUM_6`) | `Player.useQuickSlot` |
| In combat | `GameScreen` combat block → `CombatManager.playerUseItem` (`CombatManager.java:1391`) | `Player.useQuickSlot` |

That convergence is the single most useful fact here: **changing `Player.useQuickSlot`
(`Player.java:766`) changes behaviour in both exploration and combat.**

`useQuickSlot` currently branches: `isWeapon()` → swap into right hand (old weapon goes back into the
slot); `isShield()` → swap into left hand; otherwise → `useItem`.

**Throwing** is `CombatManager.throwWeapon(Item)` (`CombatManager.java:1106`). It raycasts
`max(3, weapon.getRange())`, spawns a `PROJECTILE_PLAYER` animation, rolls to-hit, drops the weapon on
the floor at the impact point, and calls `Inventory.removeItem(weapon)` — which already handles quick
slots correctly, so no removal work is needed.

`Item.isThrown()` (`Item.java:880`) is read in **exactly one place in the codebase**: the `A` key, which
throws the right-hand weapon. 24 weapons carry the flag.

---

## 2. Decisions

| # | Decision |
|---|---|
| **Q1** | A quick slot throws only weapons flagged `isThrown`. Everything else keeps the existing swap behaviour. |
| **Q2** | After a throw, the emptied slot auto-refills from the backpack with the next weapon of the same type. |
| **Q3** | The thrown-attack maths is left alone and reported (§5). |
| **Q4** | Both throw paths stay: `A` throws what is equipped, `1`–`6` throws from a slot. |
| **Q5** | A refill matches type **and skips cursed items**. |
| **Q6** | The three ammunition-shadowed darts are left alone and reported (§5). |
| **Q7** | The refill searches the backpack only, and posts a message when it finds nothing. |
| **Q9** | Throwable slots are marked in both the HUD and the inventory panel. |

> **Q8 was dropped.** It proposed fixing slot 6 in combat on the belief that only `NUM_1`–`NUM_5` were
> bound there. That was wrong: `GameScreen.java:2201` binds `NUM_6` to `playerUseItem(5, ...)`. Slot 6
> already works in combat. It has no Shift/spell branch, which is correct — there are only five spell
> slots.

### Why Q1 is not the literal reading

The literal request is "a weapon in a quick slot is thrown". Taken at face value that would let you
hurl a two-handed claymore, and it would delete hotswapping entirely — quick slots are currently the
only way to switch between a sword and a bow mid-delve. Restricting throwing to the 24 weapons already
flagged `isThrown` delivers the stated intent (a dagger in a slot throws) without either consequence.

---

## 3. The work

### 3.1 `Player.useQuickSlot` — the throw branch

`Player.java:766`. Insert **before** the existing `isWeapon()` swap branch:

```
if (item.isWeapon() && item.isThrown() && combatManager != null) {
    throw it, then refill the slot
}
```

**The `combatManager != null` guard is load-bearing.** There is a 4-argument overload at
`Player.java:762` that passes `null`. Throwing needs the manager, so a null one must fall through to
the existing swap rather than throwing a `NullPointerException` or silently doing nothing.

### 3.2 The refill

New private helper on `Player`. Rules, all from the decisions:

- Search **`inventory.getMainInventory()` only** — never other quick slots. Pulling from another slot
  would rearrange bindings the player set deliberately, which is the one thing a hotbar must not do.
- Match on **`getType()`**, so the key you learned as "throw a dagger" keeps meaning that.
- **Skip cursed items** (`Beatitude.CURSED`). The refill is a convenience the player did not ask for
  each time; it must not be able to arm them with something harmful behind their back.
- On finding nothing, **post an event** naming the empty slot. The difference between "the slot is
  empty" and "the key did nothing" is exactly the confusion that made a silent failure a bug in the
  firearm work.

### 3.3 UI (Q9)

Two surfaces, because they answer different questions:

- **`Hud.java`** (quick-slot rendering at ~`:1600` and ~`:1644`) — where the player presses the key.
- **`screens/inventory/QuickSlotsPanel.java`** — where the player assigns the binding.

A slot holding an `isThrown` weapon should be visibly distinct. Three behaviours now sit behind one
key (throw / swap / use), and the player needs to know which *before* committing a turn and a weapon.

---

## 4. Edge cases to get right

- **Null `CombatManager`** — see §3.1. The most likely way to break this.
- **Throwing at nothing.** `throwWeapon` handles a miss ("clatters to the stone") and drops the weapon
  at the collision point, so it stays recoverable. No guard needed, but confirm the weapon is reachable
  and not left inside a wall tile.
- **Two-handed throwables.** `JAVELIN_TWO_HANDED` and `JAVELIN_STONE_TWO_HANDED` are throwable. Nothing
  special is required, but they should be sanity-checked in play.
- **Turn cost.** Both handlers already call through to a turn advance (`playerTurnTakesAction` when
  exploring, `closeMenuOrPassTurn` in combat). A throw must not double-advance.
- **Shields** are untouched — they are not `isThrown`, so they keep swapping to the left hand.

## 4.1 Suggested tests

The firearm work showed that unit tests over the pieces can all pass while the seam between them is
broken, so cover the flow:

- A thrown weapon in a slot leaves the inventory and lands in the maze.
- A non-throwable weapon in a slot still swaps into the right hand.
- The slot refills from the backpack with the same type.
- The refill skips a cursed item and takes the next valid one.
- With nothing to refill from, the slot ends empty and a message is posted.
- Throwing works from a slot **in combat**, not just while exploring — that is the only situation the
  feature exists for, and it is the exact failure mode the firearm work hit in reverse.

---

## 5. Known problems, deliberately not fixed here

**Thrown attacks use a weaker, parallel combat model.** `throwWeapon` resolves on a flat
`d20 + max(STR modifier, DEX modifier)` against AC — no critical hits, no glancing blows, and it ignores
both the weapon's `accuracyModifier` and the player's injury penalties, unlike `resolveAttack`. This
change will take throwing from nearly unused to a primary tactic, so that model is about to face real
scrutiny for the first time. Rewriting it here would mean shipping an unasked-for balance shift that
could not be isolated if it felt wrong.

**Three throwable weapons are unobtainable as items.** `DART`, `DART_BARBED` and `BLOWGUN_BARBED_DART`
carry `isThrown: true` but also match `Item.isAmmunition()`, so picking one up converts it into 8–14
generic arrows and destroys the item — it can never reach a quick slot. Either fix (exclude them from
ammunition, or strip their `isThrown` flag) changes what drops and what feeds the arrow economy, which
is a loot-balance decision rather than a quick-slot one. 21 holdable throwables remain, which is ample.
