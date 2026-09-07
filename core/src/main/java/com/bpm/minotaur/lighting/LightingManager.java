package com.bpm.minotaur.lighting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.player.Player;

import java.util.Comparator;

/**
 * Core lighting subsystem managing dynamic point lights, line-of-sight shadow casting,
 * shelter warmth, and dungeon darkness.
 */
public class LightingManager {

    private static final String TAG = "LightingManager";

    // Standard torch vs. upgraded brass lantern constants
    public static final float TORCH_RADIUS = 3.5f;
    public static final float LANTERN_RADIUS = 5.5f;

    public static final Color COLOR_TORCH = new Color(1.0f, 0.62f, 0.26f, 1.0f);     // Pine torch flame
    public static final Color COLOR_LANTERN = new Color(1.0f, 0.80f, 0.44f, 1.0f);   // Warm vintage golden incandescence
    public static final Color COLOR_CAMPFIRE = new Color(1.0f, 0.45f, 0.16f, 1.0f);  // Deep ember orange-red
    public static final Color COLOR_COLD_VOID = new Color(0.04f, 0.045f, 0.08f, 1f); // Chilling unlit dungeon darkness

    private final LightSource playerLight;
    private final Array<LightSource> worldLights = new Array<>(false, 32);

    // Scratch buffers to eliminate per-frame allocations
    private final Vector2 scratchVec = new Vector2();
    private final Array<LightSource> scratchCandidates = new Array<>(false, 16);
    private final Comparator<LightSource> distanceComparator;

    private Vector2 currentQueryOrigin = new Vector2();

    public LightingManager() {
        this.playerLight = new LightSource(
                "player_light",
                0f, 0f,
                COLOR_TORCH,
                TORCH_RADIUS,
                1.0f,
                LightSource.FlickerProfile.TORCH_FLUTTER
        );

        this.distanceComparator = (l1, l2) -> {
            float d1 = l1.getPosition().dst2(currentQueryOrigin);
            float d2 = l2.getPosition().dst2(currentQueryOrigin);
            return Float.compare(d1, d2);
        };
    }

    /**
     * Updates all active lights, animation wave accumulators, and player light positioning.
     */
    public void update(float delta, Player player, Maze maze) {
        if (player != null) {
            updatePlayerLight(player);
        }

        playerLight.update(delta);

        for (int i = 0; i < worldLights.size; i++) {
            worldLights.get(i).update(delta);
        }
    }

    /**
     * Syncs player light coordinates and upgrades to Brass Lantern if equipped in Left Hand.
     */
    private void updatePlayerLight(Player player) {
        playerLight.setPosition(player.getPosition().x, player.getPosition().y);

        Item offHand = (player.getInventory() != null) ? player.getInventory().getLeftHand() : null;
        boolean hasLantern = offHand != null && offHand.getType() == ItemType.BRASS_LANTERN;

        if (hasLantern) {
            playerLight.setBaseRadius(LANTERN_RADIUS);
            playerLight.setBaseIntensity(1.15f);
            playerLight.setBaseColor(COLOR_LANTERN);
            playerLight.setProfile(LightSource.FlickerProfile.LANTERN_BREATH);
        } else {
            playerLight.setBaseRadius(TORCH_RADIUS);
            playerLight.setBaseIntensity(1.0f);
            playerLight.setBaseColor(COLOR_TORCH);
            playerLight.setProfile(LightSource.FlickerProfile.TORCH_FLUTTER);
        }
    }

    /**
     * Registers a world light source (e.g. Cook Pot, Shelter Lanterns, Glowing Shrine).
     */
    public void addLight(LightSource light) {
        if (light == null) return;
        // Avoid duplicate IDs
        removeLight(light.getId());
        worldLights.add(light);
    }

    public void removeLight(String id) {
        for (int i = worldLights.size - 1; i >= 0; i--) {
            if (worldLights.get(i).getId().equals(id)) {
                worldLights.removeIndex(i);
            }
        }
    }

    public void clearWorldLights() {
        worldLights.clear();
    }

    public void setWorldLights(Array<LightSource> lights) {
        worldLights.clear();
        if (lights != null) {
            for (int i = 0; i < lights.size; i++) {
                worldLights.add(lights.get(i));
            }
        }
    }

    public LightSource getPlayerLight() {
        return playerLight;
    }

    public Array<LightSource> getWorldLights() {
        return worldLights;
    }

    /**
     * Performs line-of-sight ray casting between two points on the maze grid.
     * Closed doors and solid walls block light; open doors and windows let light through.
     */
    public boolean isOccluded(float x1, float y1, float x2, float y2, Maze maze) {
        if (maze == null) return false;

        float dist = Vector2.dst(x1, y1, x2, y2);
        if (dist <= 0.45f) return false; // Immediate proximity is always unoccluded

        int steps = Math.max(3, (int) (dist / 0.25f));
        float stepX = (x2 - x1) / steps;
        float stepY = (y2 - y1) / steps;

        int prevTileX = (int) Math.floor(x1);
        int prevTileY = (int) Math.floor(y1);

        float curX = x1;
        float curY = y1;

        for (int i = 1; i < steps; i++) {
            curX += stepX;
            curY += stepY;

            int tileX = (int) Math.floor(curX);
            int tileY = (int) Math.floor(curY);

            if (tileX != prevTileX || tileY != prevTileY) {
                // Stepped across a tile boundary - check if a wall blocks this transition
                if (tileX > prevTileX && maze.isWallBlocking(prevTileX, prevTileY, com.bpm.minotaur.gamedata.Direction.EAST)) {
                    return true;
                }
                if (tileX < prevTileX && maze.isWallBlocking(prevTileX, prevTileY, com.bpm.minotaur.gamedata.Direction.WEST)) {
                    return true;
                }
                if (tileY > prevTileY && maze.isWallBlocking(prevTileX, prevTileY, com.bpm.minotaur.gamedata.Direction.NORTH)) {
                    return true;
                }
                if (tileY < prevTileY && maze.isWallBlocking(prevTileX, prevTileY, com.bpm.minotaur.gamedata.Direction.SOUTH)) {
                    return true;
                }

                prevTileX = tileX;
                prevTileY = tileY;
            }
        }

        return false;
    }

    /**
     * Calculates cumulative RGB lighting at a world coordinate (X, Y), accounting for
     * ambient darkness, light falloffs, and line-of-sight shadow casting.
     */
    public void calculateLightAt(float x, float y, Maze maze, Color outColor, float baseAmbient) {
        outColor.set(
                COLOR_COLD_VOID.r * baseAmbient,
                COLOR_COLD_VOID.g * baseAmbient,
                COLOR_COLD_VOID.b * baseAmbient,
                1.0f
        );

        // 1. Evaluate player light
        applyLightSource(playerLight, x, y, maze, outColor);

        // 2. Evaluate world lights
        for (int i = 0; i < worldLights.size; i++) {
            LightSource light = worldLights.get(i);
            if (!light.isActive()) continue;
            applyLightSource(light, x, y, maze, outColor);
        }

        // Clamp values to valid visual HDR range
        outColor.r = MathUtils.clamp(outColor.r, 0.0f, 1.3f);
        outColor.g = MathUtils.clamp(outColor.g, 0.0f, 1.3f);
        outColor.b = MathUtils.clamp(outColor.b, 0.0f, 1.3f);
    }

    private void applyLightSource(LightSource light, float x, float y, Maze maze, Color outColor) {
        float lx = light.getPosition().x;
        float ly = light.getPosition().y;
        float dist = Vector2.dst(x, y, lx, ly);

        if (dist >= light.getCurrentRadius()) return;

        // Line-of-sight shadow check
        if (isOccluded(lx, ly, x, y, maze)) return;

        // Smoothstep / inverse falloff
        float normDist = dist / light.getCurrentRadius();
        float atten = (1.0f - normDist) * (1.0f - normDist) * light.getCurrentIntensity();

        Color lColor = light.getCurrentColor();
        outColor.r += lColor.r * atten;
        outColor.g += lColor.g * atten;
        outColor.b += lColor.b * atten;
    }

    /**
     * Retrieves up to maxCount nearest active lights to the given origin for passing to shaders.
     */
    public Array<LightSource> getNearestLights(Vector2 origin, int maxCount, Array<LightSource> outArray) {
        outArray.clear();
        scratchCandidates.clear();

        scratchCandidates.add(playerLight);
        for (int i = 0; i < worldLights.size; i++) {
            LightSource l = worldLights.get(i);
            if (l.isActive()) {
                scratchCandidates.add(l);
            }
        }

        currentQueryOrigin.set(origin);
        scratchCandidates.sort(distanceComparator);

        int count = Math.min(maxCount, scratchCandidates.size);
        for (int i = 0; i < count; i++) {
            outArray.add(scratchCandidates.get(i));
        }

        return outArray;
    }
}
