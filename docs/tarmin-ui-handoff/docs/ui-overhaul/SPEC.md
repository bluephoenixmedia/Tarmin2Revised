# Pre-launch UI/UX spec — implementation reference

Source of truth for the UI pass. Visual reference: `mockups/` (PNG exports of the design canvas). Where this file and a mockup disagree, this file wins.

Units: **vu** = one virtual UI pixel on the 640×360 UI stage. Mockups are drawn at 1280×720 = 2 px per vu.

---

## 0. Protected — do not change

**Paper doll + armor overlays (Inventory).** Equipped armor is drawn over the paper doll at pre-registered coordinates. During this pass:

- Do not move, resize or re-scale the doll. Its origin, size and draw scale stay exactly as shipped.
- Do not edit any overlay offset / coordinate data.
- Wrap the existing doll + overlay drawing in a fixed-size widget (see §5.9) and place it in a Table cell with `.size(w, h)` — never `.fill()` / `.expand()`.
- Overlays draw relative to the doll's own origin, so moving the cell moves everything together.
- Before any UI change, capture golden screenshots of every armor piece on the doll. After every step, diff at 0-pixel tolerance.

---

## 1. Root causes (fix these, most symptoms disappear)

| ID | Cause | Fix |
|---|---|---|
| RC1 | Absolute pixel placement, non-integer font scaling, hand-tuned y offsets | Integer-scaled 640×360 UI viewport; Table-only layout; line heights from font metrics; 12vu safe area |
| RC2 | Two palettes (navy/cyan vs ember/gold), 5+ typefaces, 3 panel styles | One Ember token set (§3), two pixel faces, one dark 9-patch family + one parchment family |
| RC3 | No focus indicator, hotkeys baked into labels, locked state printed as text, destructive actions styled as primary | Focus ring, KeyHint legend, state via icon + dimming, hold-to-confirm |
| RC4 | Enum names reach the UI (`GIANT←ANT`, `BAT←GUANO`, `RING←OF←RES…`); font draws `_` as an arrow; missing glyphs render as boxes | Display names from a string table; glyph-coverage unit test |
| RC5 | Tab bar code duplicated per screen and broken (`EXPANSI|2.`, `RELI2C:`, `MEAL [2`, `]D[`) | One shared `UiTabs` widget |

---

## 2. Findings by screen

Severity: **P0** blocks launch · **P1** fix before launch · **P2** launch-window patch. Numbers match the pins on the audit boards.

### TITLE — Title screen (mockup: `11-title.png`)
- [ ] TITLE-1 **P0** No game logo on the title screen. Add a logo lockup top-left.
- [ ] TITLE-2 **P0** Footer hint ticker clipped on both edges. Replace with a static KeyHint legend inside the safe area.
- [ ] TITLE-3 P1 Menu covers the key art's focal point. Move menu to a left rail (opaque panel, ~208vu wide).
- [ ] TITLE-4 P1 Buttons hug their labels (ragged widths); navy fills off-palette. Fixed 160vu items, Ember styles.
- [ ] TITLE-5 P1 Hotkeys baked into labels (`[L]`, `[N]`, `[S]`, `[ESC]`). Remove from labels; keep shortcuts working; show in legend.
- [ ] TITLE-6 P1 Continue label overflows, mixed case. Two lines: `CONTINUE` + caption `Hero · Lv 2 Warrior · Shelter F1 · 2h ago`.
- [ ] TITLE-7 P2 Letterbox strip at top; uneven glyph strokes (fractional font scale). Fixed by §5.1–5.2.
- [ ] TITLE-8 P2 `M: MODE [ADVANCED]` unexplained. Move to Settings with a description. Add version/build tag bottom-left.

### SLOTS — Save slots + overwrite modal (mockups: `21-slots.png`, `22-slots-modal.png`)
- [ ] SLOTS-1 **P0** Modal title drawn across its border, overprints card text. Title Label goes inside the content Table (don't use Window title bar).
- [ ] SLOTS-2 **P0** No scrim; modal translucent. Opaque panel + `WindowStyle.stageBackground` scrim (black 78%).
- [ ] SLOTS-3 P1 Overwrite styled gold like Create. Occupied slot: primary = Continue, secondary = Overwrite, Delete = icon button in card corner.
- [ ] SLOTS-4 P1 Card content top-heavy, ~45% dead space, no portrait. Portrait + labelled grid anchored to card.
- [ ] SLOTS-5 P1 Unlabelled data (`MODERN`, `Doom Deaths 4/50`), `SLOT 1 [1]` redundant. Label everything (`Ruleset: Modern`, Doom meter).
- [ ] SLOTS-6 P1 No visible focus. Focus ring on card; ←/→ between cards.
- [ ] SLOTS-7 P2 Back button label overflows frame; low-contrast header instructions → legend.
- [ ] SLOTS-8 P2 New and Load are duplicate screens. Merge into one "Expeditions" screen; each card offers the action for its state.
- [ ] SLOTS-9 P2 Copy `Level 2 Hero` uses name as class. Use `{Name} — Lv {n} {Class}`; plain-language consequence text.

### HUD — In-game HUD (mockup: `31-hud.png`)
- [ ] HUD-1 **P0** World prompt covers the spell bar; spell labels truncated (`Mote o…`). Prompt anchored bottom-center of world view, 8vu above HUD; spells move into action row.
- [ ] HUD-2 **P0** Portrait block lines overprint (DIV/DOOM/ARR/SHOT). Table with icon + value cells.
- [ ] HUD-3 P1 CRT scanlines over HUD text. CRT on world FBO only (§5.5).
- [ ] HUD-4 P1 `HUNGRY` far from the food bar. Status word inside the bar that triggered it; bar frame turns DANGER below 25%.
- [ ] HUD-5 P1 Four locked spell slots take prime space. One full-size active slot + compact lock chips (`next slot Lv 2`).
- [ ] HUD-6 P1 Chronicle log shouts one line. Body size, newest at bottom, last 4 lines, older lines dimmer.
- [ ] HUD-7 P1 Minimap is an empty box; compass lives in portrait block. Top-right cluster: minimap + compass + clock/time-of-day.
- [ ] HUD-8 P2 Quickbar as 3×2 grid; inventory shows 1–5 (6th clipped). One horizontal row 1–6 everywhere.
- [ ] HUD-9 P2 Parchment prompt in dark HUD; `[ O ]Open` spacing. Use dark World Prompt component.

### LEVELUP — Level-up overlay (mockup: `71-levelup.png`)
- [ ] LEVELUP-1 **P0** World prompt (corpse) drawn over the list; list runs behind HUD. Hide world prompts while any panel is open.
- [ ] LEVELUP-2 **P0** Header lines wider than the panel; starts with missing-glyph box. Wrap to panel width; rewards become chips.
- [ ] LEVELUP-3 **P0** HUD vitals overflow (`LVL 2 [K: +3]` clipped at screen edge, `9:38 AM [MORNING]` into weapon panel). Same fix as HUD-2.
- [ ] LEVELUP-4 **P0** Chronicle lines overprint and clip. Wrap + fixed line height.
- [ ] LEVELUP-5 P1 Hotkey collision: attributes use 1–6 (quick-slot keys); E still harvests underneath. Input contexts (§5.6).
- [ ] LEVELUP-6 P1 Unclear if world is paused. Pause + scrim, or make level-up non-blocking (toast + badge).
- [ ] LEVELUP-7 P1 Blind allocation: `[+0]` unexplained, no preview, no undo. Stage points (−/+ steppers), show deltas, Reset / Decide later / Confirm.
- [ ] LEVELUP-8 P1 Enum ID on screen (`GIANT←SNAKE`). RC4.
- [ ] LEVELUP-9 P2 Green `[ + ]` buttons off-palette. Stepper component.
- [ ] LEVELUP-10 P2 Unspent points hidden in clipped corner. Pulsing `+n` badge on HUD portrait until spent.

### INV — Inventory book (mockup: `41-inventory.png`) — see §0 first
- [ ] INV-1 **P0** Attributes panel clipped by page edge; columns overprint. Stats move to their own tab (two-column Table).
- [ ] INV-2 **P0** Spellbook & alchemy boxes overprint (stacked `LOCKED`, grid over `Crafting` title, footer below frame).
- [ ] INV-3 P1 Five type families. Dynamic text uses the two UI faces in INK colors; calligraphy only if baked into art.
- [ ] INV-4 P1 Capacity contradicts itself (`[0/42]` vs `[42/42]`; 48 cells, 6 greyed). One counter `used / cap`; draw only usable cells (or locked cells with lock icon). **Also reconcile with stash screen, which says 48.**
- [ ] INV-5 P1 Equipment slots look like debug output (tiny blue labels, two `Feet`, unlabelled slots, `Back` overlaps doll frame, `L.Hand` clipped). Silhouette icons + tooltip names. **Slots move; doll does not.**
- [ ] INV-6 P1 Quick slot 6 clipped. Must match HUD's six slots.
- [ ] INV-7 P2 Instructions in headings (`(Right-Click to Drop)`) → page-foot legend.
- [ ] INV-8 P2 Five systems on one spread. Left page = doll; right page = bookmark tabs (Backpack / Alchemy / Stats). Spells stay on their own screen (Q).

### SPELL — Spellbook (mockup: `51-spellbook.png`)
- [ ] SPELL-1 **P0** Slot cards print two lines at the same Y. One Label per row in a Table.
- [ ] SPELL-2 **P0** Detail stat rows overlap (spacing < cap height). Font line height.
- [ ] SPELL-3 P1 Zero-value stats shown (`Range 0 tiles`, `Damage 0 Radiant`). Hide zero/N/A rows; add the one that matters (light radius).
- [ ] SPELL-4 P1 Palette drift (blue header, cyan name). TEXT/GOLD; MP as MP bar.
- [ ] SPELL-5 P2 Filters don't read as tabs; no spell icons. Chip filters, 32vu icons, flavor line.
- [ ] SPELL-6 P2 Footer is a sentence → KeyHint legend.

### SKILL — Skill tree (mockup: `61-skills.png`)
- [ ] SKILL-1 **P0** Layout wider than the screen (attributes clipped left, Arcana clipped right, footer cut). Root Table `setFillParent`, flex columns.
- [ ] SKILL-2 **P0** Text spills across columns; school titles overprint subtitles. Wrap Labels at cell width.
- [ ] SKILL-3 **P0** `TRAINING GROUNDS LOCKED` pill overlaps detail header. Real disabled button in pane footer with its reason.
- [ ] SKILL-4 P1 `[ LOCKED ]` on every node. Dim + lock icon + single blocking requirement.
- [ ] SKILL-5 P1 A list, not a tree. Tier rows with connector lines.
- [ ] SKILL-6 P1 School colors fail color-blind (red/green; two greens). Warfare #E0643D / Finesse #4FB7A6 / Arcana #A583F0 + glyph per school.
- [ ] SKILL-7 P2 Crowded header, nested brackets. Point counters as chips; Training Grounds hint as info strip.

### ALTAR — Ancient stone altar, 4 tabs (mockup: `81-altar.png`)
- [ ] ALTAR-1 **P0** Commune button drawn over the Divinity balance; label clipped both sides. Separate cells.
- [ ] ALTAR-2 **P0** Tab labels overprint (`SHELTER EXPANSI|2.`). RC5.
- [ ] ALTAR-3 **P0** Blessings: text and Upgrade buttons escape cards; last card off-screen.
- [ ] ALTAR-4 **P0** Ascension: ASCEND buttons start outside cards; text crosses button borders.
- [ ] ALTAR-5 P1 Affordability invisible (all Build buttons live at 0 Divinity). Disable with shortfall (`Need 15 · have 0`); sort affordable first.
- [ ] ALTAR-6 P1 Ragged card grid; Blessing cards 75% empty. Equal rows; list-detail.
- [ ] ALTAR-7 P1 Sacrifice: rarity as words (`[TAN]`), no stacking, 11 gold primaries, equipped gear sacrificed in one click. Icons, stacks with qty, one action, hold-to-confirm for equipment.
- [ ] ALTAR-8 P1 Crests of Valor hidden in a subheading; `Div`/`Divinities`/`+1 Divinities` vary. Both balances in header with icons; plural-aware.
- [ ] ALTAR-9 P1 Enum IDs (`Raw GIANT←ANT Meat`). RC4.
- [ ] ALTAR-10 P2 Stale status line across all tabs → timed toast.
- [ ] ALTAR-11 P2 `[ CONSTRUCTED ]` looks like a button → check badge.

### CHRON — Chronicle of Tarmin, 3 tabs (mockup: `91-chronicle.png`)
- [ ] CHRON-1 **P0** Same tab bug; Return button overflows frame/screen. RC5.
- [ ] CHRON-2 **P0** Relic card headers collide (name / `[RELIC] Score` / `UNLOCKED`); badge crosses border; truncation makes rings identical.
- [ ] CHRON-3 **P0** Cost tags overprint titles (`BED / SLEEPING BAG[ 15 DIVINITIES ]`); Blessings row off-screen; `3][ SEALED ]`.
- [ ] CHRON-4 P1 Missing-glyph boxes in Circle headers. RC4 glyph test.
- [ ] CHRON-5 P1 Placeholder content (`An enigmatic artifact…`, `Score: 0`, item named `Unknown`, one chest icon for all rings).
- [ ] CHRON-6 P1 Circle `SEALED` vs spell `AVAILABLE` contradiction. Spells inherit circle state.
- [ ] CHRON-7 P1 626 items in tall cards, no scroll cue. Dense icon grid + detail pane; ScrollPane with visible bar.
- [ ] CHRON-8 P1 Camp tab duplicates altar at ~2:1 contrast. Read-only summary linking to altar.
- [ ] CHRON-9 P2 Filters look like tabs → chips; completion as progress bar.
- [ ] CHRON-10 P2 Tab number `1.` outside tab → keycap inside tab.

### STASH — Shelter stash chest (mockup: `101-stash.png`)
- [ ] STASH-1 **P0** Upgrade button overprints its price and runs off-screen.
- [ ] STASH-2 **P0** Debug IDs printed under every item name (`BAT←GUANO`). Replace with category line. RC4.
- [ ] STASH-3 **P0** `STORE ALL GIBS` label wider than button.
- [ ] STASH-4 P1 Select-then-press transfer column; buttons live with nothing selected. Enter / double-click / drag to transfer; bulk action in backpack header.
- [ ] STASH-5 P1 No stacking, list cut with no scrollbar, capacity 48 vs inventory's 42.
- [ ] STASH-6 P1 Empty stash is a black void; `00 / 30` zero-padded. Draw 30 slots; consistent counters.
- [ ] STASH-7 P1 Missing arrow glyphs (`[□□] SELECT`). Keycaps with icon arrows.
- [ ] STASH-8 P2 Same instruction shown three times → one legend.
- [ ] STASH-9 P2 Upgrade doesn't say what you get → now vs next, with shortfall.

### HEARTH — Shelter cooking hearth, 4 tabs (mockup: `111-hearth.png`)
- [ ] HEARTH-1 **P0** Tab labels overprint brackets (`MEAL [2`, `[CUSTOM][3`). RC5.
- [ ] HEARTH-2 **P0** Back button `[ ESC / O : Back to Shelt…` clipped both sides. Replace with legend entry.
- [ ] HEARTH-3 **P0** Action labels wider than buttons (`FEAST AT HEARTH [EAT NOW]`, `…EST AND STOKE FIRE [1 Kindling…`). Two-line buttons: verb + cost caption.
- [ ] HEARTH-4 **P0** Pantry/codex subtitles clipped (`Click to ad…`, `Click [F…`); codex cards run off the right edge. Wrap; ScrollPane.
- [ ] HEARTH-5 P1 Pantry unstacked (Flesh ×4, Bone ×3 as rows); source as `[CHEST]` text. Stack + group by type; source as small icon.
- [ ] HEARTH-6 P1 Enum IDs (`GIB←FLESH`, `MONSTER←EYE`, `Raw GIANT←ANT Meat`, `MEAT, MEAT, MEAT`). RC4; ingredient chips.
- [ ] HEARTH-7 P1 Missing glyphs: ☒ bullets, `37.0☒C` degree sign, stray apostrophe in `Warrior's`. RC4 glyph test.
- [ ] HEARTH-8 P1 Cream parchment result card in dark UI; green-on-cream < 3:1; rest effects in red/yellow/green. Panel tokens; icon + TEXT; gains in SUCCESS.
- [ ] HEARTH-9 P1 Enabled vs disabled unreadable (empty cauldron); hint bar looks like a button. Real disabled state with reason.
- [ ] HEARTH-10 P1 Quick meal doesn't show which pantry items it will consume. "Will use" tag on those rows.
- [ ] HEARTH-11 P2 Resource strip is a bracketed sentence; `Cooking skill 0` unexplained. Icon chips + tooltip.
- [ ] HEARTH-12 P2 "Unknown" recipes list full ingredients (spoils discovery). Show one, hide rest — design call.
- [ ] HEARTH-13 P2 Empty pot slots near-invisible; static status line. Dashed slots with +; timed toasts.

### WORK — Artisan's workshop, 4 tabs (mockup: `121-workshop.png`)
- [ ] WORK-1 **P0** Tab brackets collide (`]][`, `]D[`). RC5.
- [ ] WORK-2 **P0** Buttons clipped/overlapping: `…ap All Junk Debris in Stor…`; `Carve Die into Poo|lear Bone Slots` drawn on top of each other; `Forge Bone Talisman` wider than frame.
- [ ] WORK-3 **P0** Die diagram overprints (face labels cross borders, net off-grid); talisman line cut (`+ Tr…`). Rebuild as a 4×3 cross-net Table (poles 1/6, edge 2/5, core 3/4).
- [ ] WORK-4 **P0** Panel headers render with top rows of glyphs cropped (`Iropnies`, `Hecipe Codex`, `UBBLING CHULDKUN`). Check font ascent/line height and any scissor clip.
- [ ] WORK-5 P1 `Spetum3 Polearm` — ID suffix in display name. RC4.
- [ ] WORK-6 P1 No stacking / icons (Crossbow Disk ×3, Rusty Sword ×2); stats in brackets. Icon, name, stat caption, qty.
- [ ] WORK-7 P1 Right pane opens empty ("Select an item to begin"). Pre-select first item.
- [ ] WORK-8 P1 Materials strip all zeros, `Unified Storage ::` jargon, no source hint. Icon chips; tooltip names source.
- [ ] WORK-9 P1 Brewable recipes not marked (one row brighter, unexplained). Craftable = TEXT + check; else dim + "Needs 2 Bile".
- [ ] WORK-10 P1 Two cauldrons and two waters (workshop Alchemy Cauldron / Water 0 vs hearth Cauldron / Cooking Water 5). Rename one; unify or clearly name water.
- [ ] WORK-11 P2 `Tab switched.` debug status. Remove.
- [ ] WORK-12 P2 `ARTISAN´S` (acute accent for apostrophe); no Esc hint on screen. Legend bar.

**Shared station template (ALTAR, STASH, CHRON, HEARTH, WORK):** header (title + resource chips) → UiTabs → list pane (stacked, icons, grouped) + detail pane (pre-selected) → action row (verb + cost caption, real disabled state with reason) → KeyHint legend. Build once as `StationScreen` layout helper; each screen fills it.

---

## 3. Tokens (`UiTheme.java` + `skin.json`)

| Name | Hex | Use |
|---|---|---|
| BG_VOID | #0B0907 | screen |
| BG_PANEL | #15100B | panels |
| BG_RAISED | #1F170F | cards, rows |
| BG_INSET | #0E0B08 | slots, wells, bar troughs |
| LINE_DIM | #3A2A18 | dividers |
| LINE | #6E4D25 | frames |
| LINE_BRIGHT | #B8862F | hover edge |
| FOCUS | #FFD77A | focus ring |
| TEXT | #EFE3CC | body (14.9:1) |
| TEXT_DIM | #B39C78 | labels, captions (7.1:1) |
| TEXT_OFF | #6F5E46 | disabled only |
| GOLD | #E9B44C | primary, titles |
| DANGER | #E0533D | destructive, low |
| SUCCESS | #79C267 | met, gained |
| INFO | #6FA8DC | hints |
| SCRIM | #000 @ 78% | modals |
| RES_HP / MP / FOOD / WATER / TOX | #C8372D / #3F74D6 / #D08A3A / #4A9BD8 / #8FBF3A | bar fills + icons only |
| SCHOOL_WARFARE / FINESSE / ARCANA | #E0643D / #4FB7A6 / #A583F0 | + glyph: blade / arrow / rune |
| Parchment: PAPER / PAPER_SHADE / INK / INK_DIM / INK_ACCENT | #E9D8B4 / #CDB68A / #3B2614 / #6B4E2E / #8A2E1C | inventory book only |

Retired: navy menu fills, cyan spell names, blue header stats, olive MP text, red/green school titles. Values on bars are TEXT with a 1vu BG_VOID drop shadow.

**Type** — two faces, four roles. Integer scale only (1× or 2× native), never 1.5×.
- Display (display face ×2) — screen titles
- Label (display face ×1, caps, +1vu tracking) — buttons, section labels
- Body (body face, native; line = native + 2vu) — names, descriptions, values
- Caption (body face, TEXT_DIM)

Rules: max two sizes per panel; ALL CAPS only for Display/Label; square brackets reserved for nothing (keys = keycaps, state = icons); stat columns label-left TEXT_DIM, value-right TEXT; every Label has a max width and wraps or ellipsizes.

Font candidates (commercial-safe): Silkscreen (OFL) or current chunky face for Display/Label; Pixelify Sans (OFL), Pixel Operator (CC0) or m6x11 (check author terms) for Body. Confirm native pixel grid before generating.

**Spacing (vu):** XS 2 · SM 4 · MD 8 (panel padding) · LG 12 (safe area) · XL 16 · XXL 24.
**Sizes (vu):** button h 22, min w 96; menu item w 160; item slot 20; icons 8/16/24/32; resource bar h 10; frame line 1 (+1 inner); focus ring 1, offset 1.
**Layers:** World (+CRT) → HUD → screen panels → tooltip/world prompt → scrim → modal → toasts → cursor.
**Motion:** focus 80ms snap · panel in/out 160/120ms · scrim 150ms · hold-to-confirm 900ms · toast 4s + 300ms fade.

---

## 4. Components

- **Button** (TextButtonStyle): primary (GOLD fill), secondary (raised + LINE), danger (outline DANGER → fill on focus), ghost/menu (▸ marker on focus). Focus and hover share one look. Destructive is never gold.
- **HoldButton**: danger + progress fill, 900ms, release cancels.
- **KeyHint legend**: one per screen, bottom-right in safe area; keycap 9-patch with 2vu bottom lip; glyph set swaps to last input device (keyboard / gamepad).
- **Panels** (NinePatch, 24×24 source, split 8/8/8/8, padding 8vu): panel, card, inset, parchment. Opaque — translucency only for scrim.
- **Item slot** (20vu): empty / filled (+qty) / focus / locked (dashed + lock) / invalid drop (DANGER).
- **Resource bar** (10vu): label left, value right inside; <25% → DANGER frame + status word inside.
- **World prompt**: type line (Label caps), name (Body), keycap + verb. Max width 200vu; three lines max.
- **Skill node**: locked (dim + lock + one requirement) / available (school-colored frame) / learned (double frame + check).
- **Modal** (Dialog, WindowStyle.stageBackground = scrim): title inside panel; default focus = safe action; Esc/B cancels; blocks input behind.
- **UiTabs**: Button + keycap number + Label, sized to pref width, ButtonGroup single-select. Used by altar, chronicle, inventory.
- **Stepper**: − value + (level-up allocation).
- **Toast**: timed, top-right, 4s.

---

## 5. libGDX implementation notes

### 5.1 Integer-scaled UI viewport
```java
public final class PixelUiViewport extends ScreenViewport {
    public static final int VW = 640, VH = 360;
    private int scale = 1;
    @Override public void update(int w, int h, boolean center) {
        scale = Math.max(1, Math.min(w / VW, h / VH));
        setUnitsPerPixel(1f / scale);
        super.update(w, h, center);
    }
    public int scale() { return scale; }
}
// 1280×720 ×2 · 1280×800 ×2 (640×400) · 1920×1080 ×3 · 2560×1440 ×4 · 3440×1440 ×4 (860×360)
```

### 5.2 Fonts
```java
FreeTypeFontParameter p = new FreeTypeFontParameter();
p.size = BODY_NATIVE_PX;
p.mono = true;
p.hinting = FreeTypeFontGenerator.Hinting.None;
p.minFilter = p.magFilter = Texture.TextureFilter.Nearest;
p.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "←→↑↓✦◆·—…×";
BitmapFont body = gen.generateFont(p);
body.getData().setLineHeight(BODY_NATIVE_PX + 2);
display.getData().setScale(2f); // integer only
```

### 5.3 Skin
`UiTheme` constants mirror §3. `skin.json` defines `primary`, `secondary`, `danger`, `menu` TextButtonStyles (with `focused` drawables), `modal` WindowStyle with `stageBackground: scrim`, and `Skin$TintedDrawable` `scrim` (black, a 0.78). Nine-patches authored as `*.9.png`, packed by TexturePacker.

### 5.4 Layout rules (review checklist)
a. Screens are a root Table, `setFillParent(true)`, `pad(SAFE)`. No `setPosition` on content — only on world-anchored prompts.
b. One Label per line. Multi-line = `setWrap(true)` in a sized cell. Single line in fixed cell = `setEllipsis(true)` + tooltip.
c. Buttons size to pref width with `minWidth(96)`; menu items share one fixed width.
d. Long lists in a ScrollPane with visible bar; focus scrolls the pane.
e. Shared `UiTabs` everywhere tabs appear.
f. `stage.setDebugAll(true)` on F9 during the pass.

### 5.5 CRT on world only
Render world into a FrameBuffer → draw it with the CRT shader → `batch.setShader(null)` → `uiStage.act(); uiStage.draw();`.

### 5.6 Input contexts
Stack of `UiContext`s; top gets keys first; a modal context blocks everything below. Gameplay is the bottom context. Opening any panel pushes it and hides world prompts.

### 5.7 Modal + hold-to-confirm
Title Label inside content Table. `stage.setKeyboardFocus(cancelButton)` on show. `HoldButton extends TextButton` accumulates `dt` while pressed/key held; fires at 0.9s; exposes `progress()` for the fill.

### 5.8 Display names + glyph coverage
- UI text only from the string table: `strings.get("item." + id.name().toLowerCase())`.
- Unit test: every UI string, every char, `font.getData().getGlyph(ch) != null` for each font that renders it.
- CI grep: no `.name()` / `.toString()` of enums under the UI package.

### 5.9 Paper doll (protected, see §0)
```java
/** FROZEN GEOMETRY — do not change in the UI pass. */
public final class PaperDollWidget extends Widget {
    static final float DOLL_W = /* existing value */;
    static final float DOLL_H = /* existing value */;
    @Override public float getPrefWidth()  { return DOLL_W; }
    @Override public float getPrefHeight() { return DOLL_H; }
    @Override public void draw(Batch b, float a) {
        float ox = getX(), oy = getY();
        // existing doll + overlay drawing, offsets relative to (ox, oy), unchanged
    }
}
left.add(paperDoll).size(PaperDollWidget.DOLL_W, PaperDollWidget.DOLL_H);
```

### 5.10 QA matrix
1280×720, 1280×800 (Steam Deck), 1366×768, 1600×900, 1920×1080, 2560×1440, 3440×1440, 3840×2160. Plus: +30% pseudo-locale; mouse-only, keyboard-only and gamepad-only passes; 0 and max currency; empty and full inventories.

---

## 6. Open decisions (need DG)
- Backpack capacity: 42 (inventory book) vs 48 (stash). Pick one.
- Final body/display fonts (license check).
- Logo lockup art for the title screen.
- Level-up: pause + modal (as mocked) or non-blocking toast?
- Rename one of the two cauldrons; one water resource or two clearly named ones.
- Should undiscovered cookbook recipes reveal their ingredients?
