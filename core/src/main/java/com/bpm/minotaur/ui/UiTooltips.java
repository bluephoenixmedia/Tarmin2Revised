package com.bpm.minotaur.ui;

import com.badlogic.gdx.scenes.scene2d.ui.TooltipManager;

/**
 * One {@link TooltipManager} for the whole game, so every tooltip appears and leaves on the same
 * timing wherever it is.
 *
 * <p>libGDX's default manager is already a singleton, but it is shared with anything else on the
 * classpath and its defaults animate. These are the game's: a short delay so a tooltip does not
 * chase the cursor across a grid of item slots, and no animation, because the rest of this UI
 * does not animate either.
 *
 * <p>{@code HudSkin} exposes the same manager for screens that already hold a skin; this is for
 * the ones that hold an {@code InventorySkin} instead.
 */
public final class UiTooltips {

    private UiTooltips() {
    }

    private static TooltipManager manager;

    public static TooltipManager manager() {
        if (manager == null) {
            manager = new TooltipManager();
            manager.initialTime = 0.35f;
            manager.resetTime = 0.1f;
            manager.animations = false;
        }
        return manager;
    }
}
