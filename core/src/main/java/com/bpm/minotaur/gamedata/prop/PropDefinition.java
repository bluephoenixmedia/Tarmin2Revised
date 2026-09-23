package com.bpm.minotaur.gamedata.prop;

import com.badlogic.gdx.graphics.Color;

/**
 * A single entry in the prop catalogue (assets/data/props.json).
 *
 * <p>Props are catalogued once and referenced by many themes: a brazier belongs
 * to both Wandering Battalion and Ruined Castle, but its asset path, scale and
 * passability are defined in exactly one place.
 *
 * <p>See {@code docs/DEsign/Themed Chunk Contract.md} section 5.
 */
public class PropDefinition {

    /** How the renderer should draw this prop. */
    public enum RenderMode {
        /** A billboard PNG, batched like every other sprite. */
        SPRITE,
        /** An .obj/.g3dj mesh. Reserved for a handful of hero props. */
        OBJ
    }

    private final String id;
    private final String asset;
    private final RenderMode renderMode;
    private final float scaleX;
    private final float scaleY;
    private final boolean passable;
    private final Color emissiveTint;
    private final float pixelOffsetY;
    private final int burnDamage;

    public PropDefinition(String id, String asset, RenderMode renderMode,
                          float scaleX, float scaleY, boolean passable,
                          Color emissiveTint, float pixelOffsetY, int burnDamage) {
        this.id = id;
        this.asset = asset;
        this.renderMode = renderMode;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.passable = passable;
        this.emissiveTint = emissiveTint;
        this.pixelOffsetY = pixelOffsetY;
        this.burnDamage = burnDamage;
    }

    /**
     * Damage dealt when an entity steps onto this prop's tile; 0 for props that
     * do not burn.
     *
     * <p>Braziers and campfires are walkable precisely so they can hurt. An
     * impassable fire is just a wall wearing a flame, and the Wandering
     * Battalion's declared BURNING_TILES hazard would burn nothing.
     */
    public int getBurnDamage() {
        return burnDamage;
    }

    public boolean burns() {
        return burnDamage > 0;
    }

    public String getId() {
        return id;
    }

    public String getAsset() {
        return asset;
    }

    public RenderMode getRenderMode() {
        return renderMode;
    }

    public float getScaleX() {
        return scaleX;
    }

    public float getScaleY() {
        return scaleY;
    }

    /** False means the prop blocks movement for player and monsters alike. */
    public boolean isPassable() {
        return passable;
    }

    /** Null when the prop does not glow. Drives the dynamic light injection. */
    public Color getEmissiveTint() {
        return emissiveTint;
    }

    public boolean isEmissive() {
        return emissiveTint != null;
    }

    /** Vertical billboard nudge, in pixels. Negative sinks the prop floorward. */
    public float getPixelOffsetY() {
        return pixelOffsetY;
    }

    @Override
    public String toString() {
        return "PropDefinition[" + id + " -> " + asset + "]";
    }
}
