# Claude Code prompts — UI overhaul

Run these one at a time, in order. Start a fresh Claude Code conversation (`/clear`) for each phase so context stays small; each prompt re-points at SPEC.md. Commit after every phase that passes.

---

## Prompt 0 — Recon (no code changes)

```
Read CLAUDE.md, docs/ui-overhaul/SPEC.md, and look at the PNGs in docs/ui-overhaul/mockups/.

This is a Java/libGDX first-person dungeon crawler using Scene2D UI. We are doing a pre-launch UI polish pass driven by SPEC.md. Approach: minimal diffs that extend existing classes. No rewrites, no new architecture beyond what SPEC §5 names.

PROTECTED: the inventory paper doll and its armor overlay coordinates (SPEC §0). Do not touch that code in this or any phase unless a prompt explicitly says so.

Do NOT change any code yet. Inspect the codebase and report:
1. Every screen class that maps to a SPEC screen ID (TITLE, SLOTS, HUD, LEVELUP, INV, SPELL, SKILL, ALTAR, CHRON, STASH), with file paths.
2. How the UI stage/viewport, fonts and skin are created today.
3. Where text is positioned by hand (setPosition / manual y offsets / font.draw at coordinates).
4. Where fonts are scaled, and by what factors.
5. Where enum names or toString() reach Labels or font.draw.
6. How each tab bar is built (altar, chronicle, inventory).
7. How key input is routed (InputProcessor/Multiplexer), and where 1–6 and E are handled.
8. Where the CRT/scanline shader is applied.
9. The paper doll + armor overlay code: file, class, the constants/data that hold overlay coordinates, and how they are drawn. Describe only — do not modify.
10. Any existing tests and how to run the game/tests from the command line.

Output a short table per item, then a proposed file-by-file plan for Prompt 1. Stop and wait.
```

## Prompt 0b — Paper doll safety net

```
Using your recon, add a way to capture golden screenshots of the paper doll with each armor piece equipped (a debug key, test harness or headless render — whichever is least invasive in this codebase). Capture them now into docs/ui-overhaul/golden/. Do not modify any doll or overlay code. Explain how to re-run the comparison after each later phase.
```

## Prompt 1 — Lowest risk: data hygiene (RC4)

```
Implement SPEC §5.8 only:
- Route every enum-derived UI string through a display-name lookup (string table). Fix LEVELUP-8, ALTAR-9, STASH-2, CHRON-2/5 name rendering.
- Add the glyph-coverage unit test for every font and every UI string; fix or report missing glyphs (CHRON-4, STASH-7, LEVELUP-2).
Inspect each call site before editing. Minimal diffs. List every file changed and anything you could not resolve.
```

## Prompt 2 — P0 overflow/overprint fixes, no restyle

```
Fix these P0s with minimal diffs inside the existing screen classes — no palette/font/viewport changes yet:
SLOTS-1, SLOTS-2, HUD-1, HUD-2, LEVELUP-1..4, INV-1, INV-2, SPELL-1, SPELL-2, SKILL-1..3, ALTAR-1, ALTAR-3, ALTAR-4, CHRON-2, CHRON-3, STASH-1, STASH-3, TITLE-2.
Use SPEC §5.4 layout rules (Table cells, wrap/ellipsis, font line height). For each finding: inspect the code first, state the cause in one line, then fix.
Do NOT touch the paper doll or overlay code (INV-1/INV-2 are the right-page stats, spellbook and alchemy boxes only). Re-run the golden doll comparison at the end.
```

## Prompt 3 — Shared tabs (RC5)

```
Implement UiTabs per SPEC §4/§5.4e and replace the tab bars in ALTAR, CHRON and INV (fixes ALTAR-2, CHRON-1, CHRON-10). Keep existing tab-switch logic and hotkeys; only replace the presentation. Inspect all three implementations first and report differences before unifying.
```

## Prompt 4 — Input contexts

```
Implement SPEC §5.6 (input context stack). Fixes LEVELUP-5 and hides world prompts while panels are open (LEVELUP-1, HUD-1). Extend the existing InputProcessor setup rather than replacing it. Gameplay stays the bottom context. Show me the before/after routing for keys 1–6, E and Esc.
```

## Prompt 5 — CRT on world only

```
Implement SPEC §5.5 so the CRT/scanline effect applies to the 3D world only and the UI stage draws after it, unfiltered (HUD-3). Inspect the current render loop first. Do not change the shader itself.
```

## Prompt 6 — Foundation: viewport, tokens, fonts, skin

```
Implement SPEC §5.1–5.3 and §3:
- PixelUiViewport for the UI stage.
- UiTheme constants from SPEC §3.
- FreeType font generation per §5.2 (fonts in assets/fonts — ask me which files if not present).
- skin.json styles + 9-patch drawables per §4 (create simple placeholder 9-patches if art isn't in the repo, and list them for me to replace).
Screens should keep working unchanged at this point. Verify at 1280×720, 1920×1080 and 1280×800. Re-run the golden doll comparison — the doll must still render pixel-identical; if the new viewport changes its scale, stop and tell me instead of adjusting the doll.
```

## Prompt 7..N — Screen passes (one per prompt)

Use this template, once per screen, in this order: TITLE → SLOTS → SPELL → SKILL → ALTAR → CHRON → STASH → HUD → LEVELUP → INV.

```
Screen pass: <SCREEN ID>. Reference: SPEC.md §2 <SCREEN ID> findings and mockups/<file>.png.
Apply every remaining unchecked finding for this screen using the components in SPEC §4 and tokens in §3. Extend the existing screen class; keep game logic and existing hotkeys. Inspect first, then list your plan, then implement.
When done: tick the checkboxes you completed in SPEC.md, list anything deferred and why, and screenshot the screen at 1280×720 and 1920×1080.
```

Extra line for the INV pass:
```
The paper doll and armor overlays are frozen (SPEC §0, §5.9). Wrap existing doll drawing in PaperDollWidget with its current size — do not edit coordinates or scale. Move slots/tabs around it. Golden comparison must pass at 0 px.
```
