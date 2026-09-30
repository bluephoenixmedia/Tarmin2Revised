package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import org.junit.Test;

import static org.junit.Assert.*;

public class ItemSpriteTintTest {

    @Test
    public void modernNeverTintsArtByQualityTier() {
        for (ItemColor tier : ItemColor.values()) {
            Item item = new Item();
            item.setItemColor(tier);
            Color tint = ItemSpriteTint.forItem(item, false);
            assertEquals(tier + " must not recolour a modern sprite", Color.WHITE, tint);
        }
    }

    @Test
    public void retroKeepsTheTierColour() {
        Item item = new Item();
        item.setItemColor(ItemColor.BLUE);
        assertEquals(ItemColor.BLUE.getColor(), ItemSpriteTint.forItem(item, true));
    }
}
