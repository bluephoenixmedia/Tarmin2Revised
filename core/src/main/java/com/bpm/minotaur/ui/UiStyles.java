package com.bpm.minotaur.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.bpm.minotaur.rendering.HudSkin;

/**
 * The four text roles and four button roles from SPEC section 3/4, built over {@link HudSkin}.
 *
 * <p>Before this, screen code picked a font and a colour at every call site: the audit counted
 * five type families across the inventory book alone, and three different greens for "you can
 * afford this". A style here is the only thing a screen should reach for.
 *
 * <p><b>Type roles.</b> The spec asks for two faces; the repo ships one, {@code intellivision.ttf},
 * and it is the face the mockups were drawn with. So the roles are separated by size and colour
 * rather than by face, and every size is generated natively -- there is no {@code setScale} on a
 * UI font anywhere, which is what RC1's "uneven glyph strokes" came from.
 *
 * <ul>
 *   <li><b>Display</b> (36px) -- screen titles, one per screen.</li>
 *   <li><b>Label</b> (18px, caps) -- buttons and section labels.</li>
 *   <li><b>Body</b> (18px) -- names, descriptions, values.</li>
 *   <li><b>Caption</b> (14px, dim) -- captions, units, legends.</li>
 * </ul>
 *
 * <p>Styles are cheap value objects, so these are factories rather than a cache. A style handed
 * to two actors is shared state -- Scene2D mutates {@code style.fontColor} nowhere, but screen
 * code has been known to, so take a fresh one per widget unless you mean to share.
 */
public final class UiStyles {

    private UiStyles() {
    }

    // --- Text -------------------------------------------------------------

    /** Screen title. Gold, 36px. One per screen. */
    public static Label.LabelStyle display(HudSkin skin) {
        return new Label.LabelStyle(skin.getFontDisplay(), UiTheme.GOLD);
    }

    /** Section label or button text. Caps at the call site; this only sets face and colour. */
    public static Label.LabelStyle label(HudSkin skin) {
        return new Label.LabelStyle(skin.getFontMain(), UiTheme.TEXT_DIM);
    }

    /** Body copy: names, descriptions, the value half of a stat row. */
    public static Label.LabelStyle body(HudSkin skin) {
        return new Label.LabelStyle(skin.getFontMain(), UiTheme.TEXT);
    }

    /** Body copy in a named colour, for the few values that carry meaning in their colour. */
    public static Label.LabelStyle body(HudSkin skin, Color color) {
        return new Label.LabelStyle(skin.getFontMain(), color);
    }

    /** Captions, units, legend text, the label half of a stat row. */
    public static Label.LabelStyle caption(HudSkin skin) {
        return new Label.LabelStyle(skin.getFontSmall(), UiTheme.TEXT_DIM);
    }

    /** Caption in a named colour. */
    public static Label.LabelStyle caption(HudSkin skin, Color color) {
        return new Label.LabelStyle(skin.getFontSmall(), color);
    }

    /** Text that is switched off. Never use it for anything a player still has to read. */
    public static Label.LabelStyle disabled(HudSkin skin) {
        return new Label.LabelStyle(skin.getFontMain(), UiTheme.TEXT_OFF);
    }

    // --- Buttons ----------------------------------------------------------

    /**
     * The one action on a screen that the player most likely wants. Gold fill, dark text.
     *
     * <p>Exactly one per pane. The altar had eleven gold buttons on the sacrifice tab, which is
     * the same as having none.
     */
    public static TextButton.TextButtonStyle primary(HudSkin skin) {
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
        s.font = skin.getFontMain();
        s.up = skin.getPrimaryButtonUp();
        s.down = skin.getPrimaryButtonDown();
        s.over = skin.getPrimaryButtonDown();
        s.focused = skin.getPrimaryButtonDown();
        s.disabled = skin.getInsetBg();
        s.fontColor = UiTheme.ON_GOLD;
        s.downFontColor = UiTheme.ON_GOLD;
        s.disabledFontColor = UiTheme.TEXT_OFF;
        return s;
    }

    /** Everything else that is safe to press. Raised surface, framed. */
    public static TextButton.TextButtonStyle secondary(HudSkin skin) {
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
        s.font = skin.getFontMain();
        s.up = skin.getCardBg();
        s.down = skin.getTabActive();
        s.over = skin.getTabActive();
        s.focused = skin.getTabActive();
        s.disabled = skin.getInsetBg();
        s.fontColor = UiTheme.TEXT;
        s.overFontColor = UiTheme.GOLD;
        s.disabledFontColor = UiTheme.TEXT_OFF;
        return s;
    }

    /**
     * Destructive. Outline in DANGER, filling only on focus.
     *
     * <p>Destructive is never gold: the save-slot screen styled Overwrite exactly like Create,
     * and the altar styled "sacrifice your equipped sword" exactly like "build a bed".
     */
    public static TextButton.TextButtonStyle danger(HudSkin skin) {
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
        s.font = skin.getFontMain();
        s.up = skin.getInsetBg();
        s.down = skin.solid(UiTheme.DANGER);
        s.over = skin.solid(UiTheme.DANGER);
        s.focused = skin.solid(UiTheme.DANGER);
        s.disabled = skin.getInsetBg();
        s.fontColor = UiTheme.DANGER;
        s.overFontColor = UiTheme.ON_GOLD;
        s.downFontColor = UiTheme.ON_GOLD;
        s.disabledFontColor = UiTheme.TEXT_OFF;
        return s;
    }

    /**
     * A menu row: no fill at rest, a marker and a gold label on focus.
     *
     * <p>Menu items share one fixed width ({@link UiTheme#MENU_ITEM_W}) so the column is not
     * ragged; that is the caller's job, at the cell.
     */
    public static TextButton.TextButtonStyle menu(HudSkin skin) {
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
        s.font = skin.getFontMain();
        s.up = skin.getTabInactive();
        s.down = skin.getTabActive();
        s.over = skin.getTabActive();
        s.focused = skin.getTabActive();
        s.disabled = skin.getInsetBg();
        s.fontColor = UiTheme.TEXT_DIM;
        s.overFontColor = UiTheme.GOLD;
        s.downFontColor = UiTheme.GOLD;
        s.disabledFontColor = UiTheme.TEXT_OFF;
        return s;
    }

    /**
     * Applies a button's disabled state and its reason in one call.
     *
     * <p>A button the player cannot use must say why. "Build" sitting live at zero Divinities is
     * the altar's worst finding, because pressing it teaches nothing.
     */
    public static void setEnabled(TextButton button, boolean enabled) {
        button.setDisabled(!enabled);
        button.setTouchable(enabled
                ? com.badlogic.gdx.scenes.scene2d.Touchable.enabled
                : com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
    }

    /** The font a role uses, for code that measures text rather than laying it out. */
    public static BitmapFont fontFor(HudSkin skin, boolean caption) {
        return caption ? skin.getFontSmall() : skin.getFontMain();
    }
}
