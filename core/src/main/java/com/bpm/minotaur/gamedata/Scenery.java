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
        LOW_COVER_RUBBLE,
        LOW_COVER_ALTAR,
        LOW_COVER_BARRICADE
    }

    private final SceneryType type;
    private final Vector2 position;
    private double distanceToPlayer;
    private final boolean impassable;
    public Vector2 scale; // <-- ADDED THIS (like Monster.java)
    private Texture texture; // Optional texture for Modern rendering
    private String texturePath;

    // --- NEW: Retro Colors ---
    private static final Color treeTrunk = new Color(0x5d4a41ff); // Brown
    private static final Color treeLeaves = new Color(0x3e7b4cff); // Dark Green
    private static final Color rockColor = new Color(0x7b7b7bff); // Gray
    private static final Color bushColor = new Color(0x2b5736ff); // Darker Green
    private static final Color statueColor = new Color(0xd4af37ff); // Gold/Stone

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
            case LOW_COVER_RUBBLE:
            case LOW_COVER_ALTAR:
            case LOW_COVER_BARRICADE:
                this.impassable = true;
                this.scale.set(1.0f, 0.7f); // Half-height obstacle billboard
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

    public SceneryType getType() {
        return type;
    }

    public boolean isImpassable() {
        return impassable;
    }

    public boolean isLowCover() {
        return type == SceneryType.LOW_COVER_RUBBLE
                || type == SceneryType.LOW_COVER_ALTAR
                || type == SceneryType.LOW_COVER_BARRICADE;
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
