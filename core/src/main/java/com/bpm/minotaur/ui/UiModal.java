package com.bpm.minotaur.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.bpm.minotaur.rendering.HudSkin;

/**
 * Builds the one kind of modal this game has (SPEC section 4, 5.7).
 *
 * <p>Two things were wrong with every {@code Dialog} in the codebase and both come from using
 * {@link Window}'s own title bar.
 *
 * <p>First, a {@code Window} draws its title <i>inside its background drawable's top edge</i>. The
 * backgrounds here are carved 9-patch frames with no title area, so "OVERWRITE WARNING" was
 * painted across the frame's own border and down onto the first line of the card beneath it
 * (SLOTS-1). Passing an empty title and putting a Label at the top of the content table puts the
 * title where a reader expects it and where the panel has room for it.
 *
 * <p>Second, a {@code WindowStyle} with no {@code stageBackground} leaves whatever is behind the
 * dialog fully visible and fully lit, and the panel drawables are themselves slightly
 * translucent, so the save-slot cards showed straight through the warning that was meant to stop
 * the player (SLOTS-2). The scrim is part of the style here, so a modal cannot be built without
 * one.
 *
 * <p>The returned {@code Dialog} is otherwise ordinary: callers add their own text and buttons and
 * call {@code show(stage)}. Esc and Back cancel by libGDX's own convention once a cancel button is
 * registered with {@link Dialog#key}.
 */
public final class UiModal {

    private UiModal() {
    }

    /**
     * A modal with its title inside the panel and a scrim behind it.
     *
     * <p>{@code title} is displayed; it is not a {@code Window} title. Pass the {@code Dialog}
     * subclass you need through {@code dialog} so {@code result(Object)} stays yours.
     */
    public static Dialog style(Dialog dialog, HudSkin skin, String title, boolean destructive) {
        Window.WindowStyle style = dialog.getStyle();
        style.background = skin.getDoubleBorderPanel();
        // The scrim is the style's job, so no caller can forget it.
        style.stageBackground = skin.getScrim();
        style.titleFont = skin.getFontHeader();
        dialog.setStyle(style);

        // An empty Window title leaves the title bar unused; the real title is the first row of
        // the content table.
        dialog.getTitleLabel().setText("");

        dialog.getContentTable().pad(UiTheme.PAD_XXL);
        dialog.getContentTable().defaults().left();
        dialog.getButtonTable().pad(UiTheme.PAD_XL);
        dialog.getButtonTable().defaults().pad(UiTheme.PAD_SM).height(UiTheme.BUTTON_H)
                .minWidth(UiTheme.BUTTON_MIN_W);

        Label titleLabel = UiLabels.wrapping(title,
                UiStyles.body(skin, destructive ? UiTheme.DANGER : UiTheme.GOLD));
        dialog.getContentTable().add(titleLabel).width(560f).padBottom(UiTheme.PAD_MD).row();

        dialog.setModal(true);
        dialog.setMovable(false);
        dialog.setResizable(false);
        return dialog;
    }

    /** Adds a body paragraph that wraps rather than forcing the dialog wider than the screen. */
    public static void text(Dialog dialog, HudSkin skin, String body) {
        dialog.getContentTable().add(UiLabels.wrapping(body, UiStyles.body(skin)))
                .width(560f).padBottom(UiTheme.PAD_SM).row();
    }
}
