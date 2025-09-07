package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

public class Item implements Renderable {

    public enum ItemType {
        POTION_STRENGTH,
        POTION_HEALING,
        KEY
    }

    private final ItemType type;
    private final Vector2 position;
    private final Color color;
    private final Color liquidColor;

    public Item(ItemType type, int x, int y) {
        this.type = type;
        this.position = new Vector2(x + 0.5f, y + 0.5f); // Center of the tile

        switch (type) {
            case POTION_STRENGTH:
                this.color = new Color(0.6f, 0.2f, 0.2f, 1);
                this.liquidColor = Color.RED;
                break;
            case POTION_HEALING:
                this.color = new Color(0.2f, 0.6f, 0.2f, 1);
                this.liquidColor = Color.GREEN;
                break;
            case KEY:
                this.color = Color.GOLD;
                this.liquidColor = Color.YELLOW;
                break;
            default:
                this.color = Color.GRAY;
                this.liquidColor = Color.WHITE;
                break;
        }
    }

    public ItemType getType() {
        return type;
    }

    @Override
    public Vector2 getPosition() {
        return position;
    }

    @Override
    public Color getColor() {
        return color;
    }

    public Color getLiquidColor() {
        return liquidColor;
    }
}
