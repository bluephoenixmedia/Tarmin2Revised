package com.bpm.minotaur.paperdoll.data;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

/**
 * Represents a single visual element of the paper doll.
 * One item (e.g., Plate Armor) can consist of multiple fragments (Chainmail
 * underlayer + Plate overlayer).
 */
public class DollFragment {
    public TextureRegion region;
    public Vector2 localOffset;
    public int zIndex;
    public String socketName; // The socket this fragment attaches to
    public String shaderId; // For recoloring, null if none
    public Color tint; // Tint color, null if none

    public float scaleX = 1f;
    public float scaleY = 1f;

    public DollFragment(TextureRegion region, int zIndex, String socketName) {
        this.region = region;
        this.zIndex = zIndex;
        this.socketName = socketName;
        this.localOffset = new Vector2(0, 0);
        this.tint = Color.WHITE;
    }

    public DollFragment(TextureRegion region, int zIndex, String socketName, float scaleX, float scaleY) {
        this(region, zIndex, socketName);
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    public DollFragment(TextureRegion region, int zIndex, float offsetX, float offsetY, String socketName) {
        this.region = region;
        this.zIndex = zIndex;
        this.socketName = socketName;
        this.localOffset = new Vector2(offsetX, offsetY);
        this.tint = Color.WHITE;
    }
}
