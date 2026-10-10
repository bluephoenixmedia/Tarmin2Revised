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

    public static final float TORCH_INTENSITY = 1.40f;           // 40% boost for personal light (was 1.0f)
    public static final float LANTERN_INTENSITY = 1.45f;         // carried brass lantern (1.61 read a little hot; was 1.15 originally)
    public static final float MOUNTED_LANTERN_INTENSITY = 1.40f; // 40% boost for mounted lanterns (was 1.0f)

    public static final Color COLOR_TORCH = new Color(1.0f, 0.62f, 0.26f, 1.0f);     // Pine torch flame
    public static final Color COLOR_LANTERN = new Color(1.0f, 0.80f, 0.44f, 1.0f);   // Warm vintage golden incandescence
    public static final Color COLOR_CAMPFIRE = new Color(1.0f, 0.45f, 0.16f, 1.0f);  // Deep ember orange-red
    public static final Color COLOR_COLD_VOID = new Color(0.04f, 0.045f, 0.08f, 1f); // Chilling unlit dungeon darkness
    public static final Color COLOR_SHELTER_AMBIENT = new Color(0.35f, 0.28f, 0.22f, 1.0f); // Warm safe haven shelter glow
    public static final Color COLOR_MOTE = new Color(0.85f, 0.92f, 1.0f, 1.0f); // Warm mystical wisp radiance

    private final LightSource playerLight;
    private final Array<LightSource> worldLights = new Array<>(false, 32);

    // Scratch buffers to eliminate per-frame allocations
    private final Vector2 scratchVec = new Vector2();
    private final Array<LightSource> scratchCandidates = new Array<>(false, 16);
    private final Comparator<LightSource> distanceComparator;

    private Vector2 currentQueryOrigin = new Vector2();
    private final LightSource shopkeeperLight;

    public LightingManager() {
        this.playerLight = new LightSource(
                "player_light",
                0f, 0f,
                COLOR_TORCH,
                TORCH_RADIUS,
                TORCH_INTENSITY,
                LightSource.FlickerProfile.TORCH_FLUTTER
        );

        this.shopkeeperLight = new LightSource(
                "shopkeeper_light",
                0f, 0f,
                COLOR_LANTERN,
                LANTERN_RADIUS * 0.9f,
                LANTERN_INTENSITY * 0.95f,
                LightSource.FlickerProfile.LANTERN_BREATH
        );
        this.shopkeeperLight.setActive(false);

        this.distanceComparator = (l1, l2) -> {
            float d1 = l1.getPosition().dst2(currentQueryOrigin);
            float d2 = l2.getPosition().dst2(currentQueryOrigin);
            return Float.compare(d1, d2);
        };
    }

    /**
     * Updates all active lights, animation wave accumulators, and player light positioning.
     */
    /** Seconds the lights still tremble for: a battle overhead shaking the stone (Living War W9). */
    private float tremor;
    private float tremorTime;
    static final float TREMOR_DIP = 0.35f;

    /** The stone shakes: every light gutters for {@code seconds}. */
    public void tremble(float seconds) {
        tremor = Math.max(tremor, seconds);
    }

    /** How much of its light a source gives now: one, or less while the stone shakes. */
    float tremorFactor() {
        if (tremor <= 0f) return 1f;
        float fade = Math.min(1f, tremor / 0.4f);
        return 1f - TREMOR_DIP * fade * (0.5f + 0.5f * (float) Math.abs(Math.sin(tremorTime * 37f)));
    }

    public void update(float delta, Player player, Maze maze) {
        if (tremor > 0f) {
            tremor = Math.max(0f, tremor - delta);
            tremorTime += delta;
        }
        if (player != null) {
            updatePlayerLight(player, maze);
        }

        playerLight.update(delta);

        if (maze != null && maze.getShopkeeper() != null && maze.getShopkeeper().isAlive()) {
            shopkeeperLight.setActive(true);
            shopkeeperLight.setPosition(maze.getShopkeeper().getPosition().x, maze.getShopkeeper().getPosition().y);
            shopkeeperLight.update(delta);
        } else {
            shopkeeperLight.setActive(false);
        }

        for (int i = 0; i < worldLights.size; i++) {
            worldLights.get(i).update(delta);
        }
    }

    private boolean lanternLit = true;

    public boolean isLanternLit() {
        return lanternLit;
    }

    public void setLanternLit(boolean lit) {
        this.lanternLit = lit;
    }

    public boolean toggleLantern() {
        this.lanternLit = !this.lanternLit;
        return this.lanternLit;
    }

    // Underground (any level below the overworld) there is no daylight at all, so
    // the carried lantern reaches further. Reach, mostly -- not glare: a x1.5 on
    // intensity as well put a wall one tile away near the shader's cap (1.87 of
    // 2.2) and washed the corridor out. Now it is ~1.25, still above the surface.
    private static final float UNDERGROUND_LANTERN_RADIUS_BOOST = 1.35f;
    private static final float UNDERGROUND_LANTERN_INTENSITY_BOOST = 1.15f;

    /** What owning the Shelter Lantern station does for the lantern you carry: it burns wider and brighter. */
    public static final float STATION_LANTERN_RADIUS_BONUS = 1.0f;
    public static final float STATION_LANTERN_INTENSITY_FACTOR = 1.10f;

    public static float carriedLanternRadius(boolean underground, boolean stationOwned) {
        float radius = LANTERN_RADIUS + (stationOwned ? STATION_LANTERN_RADIUS_BONUS : 0f);
        return radius * (underground ? UNDERGROUND_LANTERN_RADIUS_BOOST : 1.0f);
    }

    public static float carriedLanternIntensity(boolean underground, boolean stationOwned) {
        float intensity = LANTERN_INTENSITY * (stationOwned ? STATION_LANTERN_INTENSITY_FACTOR : 1.0f);
        return intensity * (underground ? UNDERGROUND_LANTERN_INTENSITY_BOOST : 1.0f);
    }

    /**
     * Syncs player light coordinates and upgrades to Brass Lantern if equipped in either hand.
     */
    private void updatePlayerLight(Player player, Maze maze) {
        playerLight.setPosition(player.getPosition().x, player.getPosition().y);

        boolean hasLantern = player.hasLantern();

        boolean isUnderground = maze != null && maze.getLevel() > 1;

        boolean hasMote = player != null && player.getStatusManager() != null
                && player.getStatusManager().hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.MOTE_OF_LIGHT);

        if (hasMote) {
            playerLight.setActive(true);
            playerLight.setBaseRadius(8.5f);
            playerLight.setBaseIntensity(1.35f);
            playerLight.setBaseColor(COLOR_MOTE);
            playerLight.setProfile(LightSource.FlickerProfile.STEADY);
        } else if (hasLantern) {
            playerLight.setActive(lanternLit);
            boolean stationOwned = com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance()
                    .hasStation(com.bpm.minotaur.gamedata.progression.ShelterAltar.Station.LANTERN);
            playerLight.setBaseRadius(carriedLanternRadius(isUnderground, stationOwned));
            playerLight.setBaseIntensity(carriedLanternIntensity(isUnderground, stationOwned));
            playerLight.setBaseColor(COLOR_LANTERN);
            // A flame you carry wavers; LANTERN_BREATH's +/-2% swell was too calm
            // to see, which is why the flicker vanished once everyone started
            // with a lantern instead of a torch.
            playerLight.setProfile(LightSource.FlickerProfile.LANTERN_FLAME);
        } else {
            playerLight.setActive(true);
            playerLight.setBaseRadius(TORCH_RADIUS);
            playerLight.setBaseIntensity(TORCH_INTENSITY);
            playerLight.setBaseColor(COLOR_TORCH);
            playerLight.setProfile(LightSource.FlickerProfile.TORCH_FLUTTER);
        }
        float traitLight = com.bpm.minotaur.gamedata.trait.TraitEffects.add("lightAdd") + com.bpm.minotaur.gamedata.trait.TraitEffects.add(isUnderground ? "lightUnderground" : "lightSurface");
        if (traitLight != 0f && !hasMote) {
            playerLight.setBaseRadius(playerLight.getBaseRadius() + traitLight);
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
        calculateLightAt(x, y, maze, outColor, baseAmbient, COLOR_COLD_VOID);
    }

    /**
     * Calculates cumulative RGB lighting at a world coordinate (X, Y), allowing a tailored
     * ambient color (e.g. warm shelter glow vs. cold subterranean void).
     */
    public void calculateLightAt(float x, float y, Maze maze, Color outColor, float baseAmbient, Color ambientColor) {
        Color baseCol = (ambientColor != null) ? ambientColor : COLOR_COLD_VOID;
        outColor.set(
                baseCol.r * baseAmbient,
                baseCol.g * baseAmbient,
                baseCol.b * baseAmbient,
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

        // 3. Evaluate traveling merchant light
        if (shopkeeperLight.isActive()) {
            applyLightSource(shopkeeperLight, x, y, maze, outColor);
        }

        // 4. The stone shaking under a battle overhead makes every flame gutter.
        float shake = tremorFactor();
        if (shake < 1f) {
            float floorR = baseCol.r * baseAmbient, floorG = baseCol.g * baseAmbient, floorB = baseCol.b * baseAmbient;
            outColor.r = floorR + (outColor.r - floorR) * shake;
            outColor.g = floorG + (outColor.g - floorG) * shake;
            outColor.b = floorB + (outColor.b - floorB) * shake;
        }

        // Clamp values to valid visual HDR range
        outColor.r = MathUtils.clamp(outColor.r, 0.0f, 1.8f);
        outColor.g = MathUtils.clamp(outColor.g, 0.0f, 1.8f);
        outColor.b = MathUtils.clamp(outColor.b, 0.0f, 1.8f);
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
