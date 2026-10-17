package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.item.Item;

/**
 * The colour an item's billboard is multiplied by in the 3D world.
 *
 * <p>An item's {@link Item#getColor()} is its quality tier, the colour language of the classic
 * renderer. The modern renderer draws authored sprite art, and multiplying art by a tier colour
 * strips the channels the tier lacks: {@code ItemColor.BLUE} is pure blue, so a blue-tier item
 * lost its red and green and came out solid blue (likewise red, green and purple tiers). Modern
 * draws the art as it is; retro keeps the tier colour.
 */
public final class ItemSpriteTint {

    private ItemSpriteTint() {
    }

    public static Color forItem(Item item, boolean retro) {
        if (retro && item != null) {
            return item.getColor();
        }
        return Color.WHITE;
    }
}
