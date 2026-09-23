package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;

/**
 * A non-monster, non-item renderable object in the world, like a tree or rock.
 */
public class Scenery implements Renderable {

    public enum SceneryType {
        TREE,
        ROCK,
        BUSH,
        CACTUS,
        SANDSTONE_ROCK,
        STATUE,
        DECOMPOSING_CORPSE,
        /**
         * A data-driven themed prop. Behaviour comes from a {@code propId}
         * resolved against {@code assets/data/props.json} rather than from a
         * switch arm here. Adding one enum value keeps every existing save
         * loading untouched, which replacing the enum would not.
         */
        PROP
    }

    private final SceneryType type;
    private final Vector2 position;
    private double distanceToPlayer;
    private boolean impassable;

    /** Non-null only for {@link SceneryType#PROP}. */
    private String propId;
    /** Marks this prop as an objective target (a grave, the heart-bloom, a cache). */
    private boolean objectiveMarker = false;
    /** True once an objective marker has been used, so it cannot be farmed twice. */
    private boolean objectiveConsumed = false;
    private Color emissiveTint;
    public Vector2 scale; // <-- ADDED THIS (like Monster.java)
    private float pixelOffsetY = 0f;
    private Texture texture; // Optional texture for Modern rendering
    private String texturePath;
    private com.bpm.minotaur.gamedata.bones.BonesData bonesData;

    // --- NEW: Retro Colors ---
    private static final Color treeTrunk = new Color(0x5d4a41ff); // Brown
    private static final Color treeLeaves = new Color(0x3e7b4cff); // Dark Green
    private static final Color rockColor = new Color(0x7b7b7bff); // Gray
    private static final Color bushColor = new Color(0x2b5736ff); // Darker Green
    private static final Color statueColor = new Color(0xd4af37ff); // Gold/Stone
    private static final Color corpseColor = new Color(0x8a9ea7ff); // Ethereal Bone Gray

    public Scenery(SceneryType type, int x, int y) {
        this(type, x, y, null);
    }

    public Scenery(SceneryType type, int x, int y, String texturePath) {
        this.type = type;
        this.position = new Vector2(x + 0.5f, y + 0.5f);
        this.scale = new Vector2(1.0f, 1.0f); // <<<--- THIS IS THE FIX. It must be initialized here.
        this.texturePath = texturePath;

        // Set impassable flag
        switch (type) {
            case TREE:
            case CACTUS:
                this.impassable = true;
                this.scale.set(2.0f, 4.0f); // Taller and wider for billboard
                break;
            case ROCK:
            case SANDSTONE_ROCK:
                this.impassable = true;
                this.scale.set(1.0f, 0.8f); // <-- ADDED THIS (Slightly shorter)
                break;
            case DECOMPOSING_CORPSE:
                this.impassable = true;
                this.scale.set(1.0f, 0.55f); // Low lying skeletal remains billboard
                this.pixelOffsetY = -50f; // Lowered by ~50 pixels to rest flat on the floor
                break;
            case BUSH:
                this.impassable = false;
                this.scale.set(1.0f, 0.75f); // <-- ADDED THIS (Shorter)
                break;
            case STATUE:
                this.impassable = false;
                this.scale.set(1.0f, 1.25f); // Elegant standing monument height
                break;
            default:
                this.impassable = false;
                break;
        }
    }

    /**
     * Builds a themed prop from its catalogue definition.
     *
     * <p>Returns null when the id is unknown, so a bad reference in a theme
     * table degrades to "no prop placed" rather than crashing chunk generation.
     * {@code ThemeContractTest} catches bad ids loudly instead.
     */
    public static Scenery fromProp(String propId, int x, int y) {
        com.bpm.minotaur.gamedata.prop.PropDefinition def =
                com.bpm.minotaur.gamedata.prop.PropCatalog.getInstance().get(propId);
        if (def == null) return null;

        Scenery s = new Scenery(SceneryType.PROP, x, y, def.getAsset());
        s.propId = propId;
        s.impassable = !def.isPassable();
        s.scale.set(def.getScaleX(), def.getScaleY());
        s.pixelOffsetY = def.getPixelOffsetY();
        s.emissiveTint = def.getEmissiveTint();
        return s;
    }

    public SceneryType getType() {
        return type;
    }

    /** Null unless this is a {@link SceneryType#PROP}. */
    public String getPropId() {
        return propId;
    }

    public void setPropId(String propId) {
        this.propId = propId;
    }

    public boolean isObjectiveMarker() {
        return objectiveMarker && !objectiveConsumed;
    }

    public void setObjectiveMarker(boolean objectiveMarker) {
        this.objectiveMarker = objectiveMarker;
    }

    public boolean isObjectiveConsumed() {
        return objectiveConsumed;
    }

    public void consumeObjective() {
        this.objectiveConsumed = true;
    }

    public void setObjectiveConsumed(boolean consumed) {
        this.objectiveConsumed = consumed;
    }

    /** Null when the prop does not glow. */
    public Color getEmissiveTint() {
        return emissiveTint;
    }

    public void setImpassable(boolean impassable) {
        this.impassable = impassable;
    }

    public boolean isImpassable() {
        return impassable;
    }

    public boolean isDecomposingCorpse() {
        return type == SceneryType.DECOMPOSING_CORPSE;
    }

    public com.bpm.minotaur.gamedata.bones.BonesData getBonesData() {
        return bonesData;
    }

    public void setBonesData(com.bpm.minotaur.gamedata.bones.BonesData bonesData) {
        this.bonesData = bonesData;
    }

    public boolean isAwakened() {
        return bonesData != null && bonesData.awakened;
    }

    public boolean isDefeated() {
        return bonesData != null && bonesData.defeated;
    }
    // --- Renderable Implementation ---

    @Override
    public Vector2 getPosition() {
        return position;
    }

    public void setDistanceToPlayer(double distance) {
        this.distanceToPlayer = distance;
    }

    public double getDistanceToPlayer() {
        return distanceToPlayer;
    }

    /**
     * NEW: Gets the sprite data from the ScenerySpriteData class.
     * 
     * @return A String array representing the sprite.
     */
    public String[] getSpriteData() {
        return ScenerySpriteData.getSpriteByType(this.type);
    }

    // --- NEW: Added getScale() method ---
    public Vector2 getScale() {
        return scale;
    }

    public float getPixelOffsetY() {
        return pixelOffsetY;
    }

    public void setPixelOffsetY(float pixelOffsetY) {
        this.pixelOffsetY = pixelOffsetY;
    }

    /**
     * NEW: Gets the correct retro color for this scenery type.
     * 
     * @return The LibGDX Color.
     */
    @Override
    public Color getColor() {
        switch (type) {
            case TREE:
                // We'll use leaves color as the primary. The ASCII art handles the trunk.
                return treeLeaves;
            case CACTUS:
                return bushColor;
            case ROCK:
            case SANDSTONE_ROCK:
                return rockColor;
            case BUSH:
                return bushColor;
            case STATUE:
                return statueColor;
            case DECOMPOSING_CORPSE:
                return corpseColor;
            case PROP:
                // Themed props carry their own tint when they glow; otherwise
                // they fall back to stone so retro mode still draws something.
                return emissiveTint != null ? emissiveTint : rockColor;
            default:
                return rockColor;
        }
    }

    // --- Implemented from Renderable interface for compatibility ---
    public Color getMonsterColor() {
        return null;
    }

    public Color getItemColor() {
        return null;
    }

    public void setTexture(Texture texture) {
        this.texture = texture;
    }

    public Texture getTexture() {
        return texture;
    }

    public String getTexturePath() {
        return texturePath;
    }

    public void setTexturePath(String texturePath) {
        this.texturePath = texturePath;
    }
}
