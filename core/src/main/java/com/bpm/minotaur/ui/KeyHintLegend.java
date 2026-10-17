package com.bpm.minotaur.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.bpm.minotaur.rendering.HudSkin;

/**
 * One legend per screen, in the safe area, listing what the keys do.
 *
 * <p>This replaces three separate habits the audit found. Hotkeys baked into button labels
 * ({@code [L]}, {@code [N]}, {@code [ESC]}) -- which makes every button's width depend on its
 * shortcut and puts brackets in the middle of the reading order. Instructions in headings
 * ({@code (Right-Click to Drop)}). And a footer written as a sentence, repeated three times on
 * one screen in the stash's case.
 *
 * <p>Square brackets are reserved for nothing (SPEC section 3): a key is drawn as a keycap, a
 * state is drawn as an icon.
 *
 * <p>Arrow keys are a special case. The font has no arrow glyphs, so the stash's
 * {@code [<-->] SELECT} rendered as two missing-glyph boxes. {@link #ARROWS_LR} and friends are
 * the spellings that do render.
 */
public class KeyHintLegend extends Table {

    /** Left/right arrows, spelled so the font can draw them. */
    public static final String ARROWS_LR = "< >";
    /** Up/down arrows. */
    public static final String ARROWS_UD = "^ v";
    /** All four. */
    public static final String ARROWS_ALL = "^v<>";

    private final HudSkin skin;

    public KeyHintLegend(HudSkin skin) {
        this.skin = skin;
        right().bottom();
    }

    /**
     * Adds one hint: a keycap and the verb it performs.
     *
     * <p>The verb is what the key does, in one or two words -- "Leave", "Sacrifice", "Next tab".
     * Not a sentence, and not a restatement of the button it duplicates.
     */
    public KeyHintLegend hint(String key, String verb) {
        Table cap = new Table();
        cap.setBackground(skin.getKeycap());
        Label keyLabel = UiLabels.of(key, UiStyles.caption(skin, UiTheme.TEXT));
        cap.add(keyLabel).pad(UiTheme.PAD_XS, UiTheme.PAD_SM, UiTheme.PAD_XS, UiTheme.PAD_SM);

        add(cap).padLeft(UiTheme.PAD_MD);
        add(UiLabels.of(verb, UiStyles.caption(skin))).padLeft(UiTheme.PAD_XS);
        return this;
    }

    /** The hint every panel screen has. Always last, so Esc sits at the end of the row. */
    public KeyHintLegend escapeHint(String verb) {
        return hint("ESC", verb);
    }
}
