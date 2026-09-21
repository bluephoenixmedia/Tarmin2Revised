# UX standard for menu screens

**Scope:** every menu and modal screen — 24 of the 27 the capture harness records. The in-world
HUD (`09_exploration_hud`) and the combat dice overlay (`13_combat_dice_overlay`) are **out of
scope**: they draw over live 3D against unpredictable backgrounds, which is a different problem with
different rules.

This exists because "consistent" without a written rule means "whatever the last screen happened to
do". The Save Slots screen had three button styles with no stated meaning; the same slot header was
drawn in two different fonts on the same row. Both are what an unwritten standard produces.

---

## 1. Verifying your work

**You can run the game and look at the result.** From the repo root:

```
./gradlew :lwjgl3:run --args="--capture-polished"
```

This boots straight into `UXScreenCaptureScreen`, captures all 27 screens to
`docs/ux/screenshots/polished/`, and exits on its own. Takes about 40 seconds.
`--capture-baseline` writes to `baseline/` instead.

**Look at the PNG before claiming a screen is fixed.** Every defect listed in §6 was found by
looking at a capture, not by reading code. Several were invisible in source — a button whose label
overflowed its own background reads as perfectly ordinary code.

---

## 2. Two layout shapes

Every screen is one of these. Pick by content, not by preference.

### Full-bleed

For screens with multiple panels or a scrolling list. Fills the frame.

```
┌──────────────────────────────────────────────┐
│  TITLE IN GOLD CAPS                          │  header bar
│  One-line subtitle explaining the screen     │
├──────────────────────────────────────────────┤
│  (optional status strip — currency, capacity)│
│                                              │
│   ┌────────────┐   ┌──────────────────────┐  │  body panels
│   │ list       │   │ detail               │  │
│   └────────────┘   └──────────────────────┘  │
├──────────────────────────────────────────────┤
│  [KEY] hint   [KEY] hint        [ESC] Return │  footer hint bar
└──────────────────────────────────────────────┘
```

`21_alchemy` is the reference implementation.

**Assigned:** save_slots · inventory_modern · shopkeeper_window · shelter_chest · shelter_altar ·
crafting_forge · crafting_salvage · crafting_ossuary · cooking_whip_up · cooking_cauldron · alchemy ·
skill_tree · spellbook · castle_map · torment_pacts · player_death · game_over

### Centred modal

For a short list of choices or a single message. Sized to content on a dimmed backdrop.

```
        ┌───────────────────────────┐
        │      TITLE IN GOLD        │
        │                           │
        │   Label        [value]    │
        │   Label        [value]    │
        │                           │
        │  [ PRIMARY ACTION   [K] ] │
        │  [ Secondary        [ESC]]│
        └───────────────────────────┘
```

**Assigned:** main_menu · controls · settings · pause · loading · first_aid_modal ·
encounter_shrine · bones_awaken

---

## 3. Buttons: three roles

| Role | Style | Use for |
|---|---|---|
| **Primary** | Gold fill, dark ink (`COL_TEXT_ON_GOLD`) | The main action. One per screen. |
| **Secondary** | Dark fill, gold text | Navigation, dismissal, "back". |
| **Destructive** | Dark fill, red text | Delete, abandon, anything irreversible. |

A destructive action must never share the primary style. "DELETE" rendered in the same gold as
"RESUME DELVE" is a hazard, not an inconsistency.

**Never pin a button cell with `.width(n)`.** That fixes min, pref *and* max, so a label wider than
`n` overflows its own background — which is exactly what happened to
`"KEY BINDINGS & CONTROLS  [C]"` on Settings. Use **`.minWidth(n)`**: identical when the label fits,
and it grows when it doesn't.

Horizontal padding between a label and its button edge comes from the style
(`HudSkin.BUTTON_LABEL_INSET`), not from per-button padding. Don't add your own.

---

## 4. Colour

Always use a `HudSkin` token. Never `Color.GRAY`, `Color.LIGHT_GRAY` or a literal.

### On a dark panel (most menus)

| Purpose | Token |
|---|---|
| Titles, headings | `COL_GOLD_BRIGHT` |
| Body text | `COL_TEXT_ON_DARK` |
| De-emphasised (timestamps, empty states) | `COL_TEXT_MUTED` |
| Health | `COL_HP_ON_DARK` |
| Mana | `COL_MP_ON_DARK` |
| Success, sufficiency | `COL_FOOD_ON_DARK` |
| Warning | `COL_TEMP_ON_DARK` |

> The `COL_HP_RED` / `COL_MP_BLUE` family is tuned to read as **bars over a lit dungeon**. As small
> text on a near-black panel the same values are too dark. The `_ON_DARK` variants exist for menus;
> the originals belong to the HUD and should not be changed.

### On a gold fill (selected rows, primary buttons)

Use `COL_TEXT_ON_GOLD`. **Anything that changes background on selection must change its text colour
too.** Alchemy's `[ MISSING REAGENTS ]` stayed muted gold when its row filled gold, which made it
invisible on the one row the player was looking at.

---

## 5. Type

Six sizes come from `HudSkin`: `fontHeader` (19), `fontMain` (18), `fontLog` (17), `fontSmall` (14),
`fontMicro` (12), `fontCompass` (22).

**The same element must use the same font in every branch of the same screen.** The Save Slots
header used `fontHeader` when the slot was empty and `fontSmall` when occupied, so headers sat at
different heights across one row of cards.

Wrapped text is safe: line spacing is corrected once at the font (`HudSkin.LINE_SPACING`) rather
than per label. Don't set line height on individual labels.

Long text that can overflow gets `setEllipsis(true)` and a cell allowed to shrink
(`.expandX().fillX().minWidth(0)`). Without the `minWidth(0)` the ellipsis never engages and the row
overflows instead.

---

## 6. Known defects, for reference

All of these were found by looking at captures. They are the shapes to watch for.

| Defect | Screen | Cause |
|---|---|---|
| Button label overflowed its own background | Settings | `.width(400)` smaller than the label |
| Wrapped lines collided | Alchemy, and everywhere else text wraps | Font line height too tight; 39 sites shared it |
| Status text invisible when selected | Alchemy | Text colour didn't flip with the gold background |
| `HP:` / `MP:` too dark to read | Save Slots | HUD bar colours used as menu text |
| Header at two different heights in one row | Save Slots | Two fonts, two paddings, one element |

---

## 7. Still to do

Not every screen has been conformed to this document. It was written from the screens that have
been, and the remainder should be brought to it one at a time, capturing after each.

The natural follow-on is **shared widgets** — a `ScreenFrame` helper that builds the header bar,
body and footer hint bar for each of the two shapes, so layout can't drift back. That was
deliberately deferred: it is much easier to design once enough screens have been conformed by hand
to know what they genuinely share.
