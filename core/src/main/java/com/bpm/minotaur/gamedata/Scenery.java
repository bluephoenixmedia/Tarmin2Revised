package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.graphics.Texture; // <-- IMPORT ADDED FOR COMPATIBILITY

/**
 * A non-monster, non-item renderable object in the world, like a tree or rock.
 */
public class Scenery implements Renderable {

    public enum SceneryType {
        TREE,
        ROCK,
        BUSH
    }

    private final SceneryType type;
    private final Vector2 position;
    private double distanceToPlayer;
    private final boolean impassable;
    public Vector2 scale; // <-- ADDED THIS (like Monster.java)

    // --- NEW: Retro Colors ---
    private static final Color treeTrunk = new Color(0x5d4a41ff); // Brown
    private static final Color treeLeaves = new Color(0x3e7b4cff); // Dark Green
    private static final Color rockColor = new Color(0x7b7b7bff);   // Gray
    private static final Color bushColor = new Color(0x2b5736ff); // Darker Green

    public Scenery(SceneryType type, int x, int y) {
        this.type = type;
        this.position = new Vector2(x + 0.5f, y + 0.5f);
        this.scale = new Vector2(1.0f, 1.0f); // <<<--- THIS IS THE FIX. It must be initialized here.

        // Set impassable flag
        switch (type) {
            case TREE:
                this.impassable = true;
                this.scale.set(1.0f, 2.0f); // <-- ADDED THIS (2x height)
                break;
            case ROCK:
                this.impassable = true;
                this.scale.set(1.0f, 0.8f); // <-- ADDED THIS (Slightly shorter)
                break;
            case BUSH:
                this.impassable = false;
                this.scale.set(1.0f, 0.75f); // <-- ADDED THIS (Shorter)
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
     * @return The LibGDX Color.
     */
    @Override
    public Color getColor() {
        switch (type) {
            case TREE:
                // We'll use leaves color as the primary. The ASCII art handles the trunk.
                return treeLeaves;
            case ROCK:
                return rockColor;
            case BUSH:
                return bushColor;
            default:
                return rockColor;
        }
    }

    // --- Implemented from Renderable interface for compatibility ---
    public Color getMonsterColor() { return null; }
    public Color getItemColor() { return null; }
    public Texture getTexture() { return null; }
}

