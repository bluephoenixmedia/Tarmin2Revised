package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Represents a localized wound decal stamped onto a creature's texture.
 */
public class WoundDecal {

    public enum WoundType {
        SLASH,      // Broad directional laceration (swords, 2H blades, axes)
        SLICE,      // Narrow razor incision (finesse weapons, daggers, claws)
        STAB,       // Elongated thrust entry wound (spears, daggers, rapiers)
        PUNCTURE,   // Point / ballistic cavity (arrows, bolts, bullets, darts)
        CRUSH,      // Radial fracture / blunt contusion (maces, hammers, brawling)
        SCORCH      // Burned charred mark (fire, lightning, acid)
    }

    public WoundType type = WoundType.SLASH;
    public float u = 0.5f;             // Normalized UV X on monster [0..1]
    public float v = 0.5f;             // Normalized UV Y on monster [0..1]
    public float angle = 0.0f;         // Trajectory angle in radians
    public float length = 0.25f;       // Length relative to sprite size
    public float width = 0.06f;        // Thickness / width relative to sprite size
    public final Color color = new Color(Color.WHITE);
    public TextureRegion customRegion = null;

    public WoundDecal() {
    }

    public WoundDecal(WoundType type, float u, float v, float angle, float length, float width, Color color) {
        this.type = type;
        this.u = u;
        this.v = v;
        this.angle = angle;
        this.length = length;
        this.width = width;
        if (color != null) {
            this.color.set(color);
        }
    }

    public WoundDecal cpy() {
        WoundDecal d = new WoundDecal(type, u, v, angle, length, width, color);
        d.customRegion = this.customRegion;
        return d;
    }
}
