package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.FastNoiseLite;
import com.bpm.minotaur.generation.WorldConstants;

import java.util.Random;

/**
 * Decides which biome lies at any chunk coordinate.
 *
 * <p>Two algorithms live here, chosen by the world-gen version a save records.
 * Legacy worlds keep the original fixed-seed layout -- a forest ring, a
 * northern Tundra band and humidity noise -- so a save never meets a chunk laid
 * out by a different rule. Current worlds are laid out from the world seed:
 * biomes sit in distance bands that follow the portal ladder, and a Castle
 * Tarmin site anchors the Blighted Marches. See
 * docs/DEsign/Requirements_ Procedural World &amp; The Blighted Marches.md.
 */
public class BiomeManager {

    private final int version;
    private final int mazeRadius;

    // Legacy layers
    private FastNoiseLite noise;
    private FastNoiseLite humidityNoise;
    private int forestRadius;

    // Current layers
    private FastNoiseLite elevationNoise;
    private FastNoiseLite warpNoise;
    private FastNoiseLite selectorNoise;
    private FastNoiseLite castleEdgeNoise;
    private GridPoint2 castleSite;

    /** A legacy world: the fixed-seed layout every save had before world-gen versions. */
    public BiomeManager() {
        this(WorldConstants.WORLD_SEED, WorldConstants.WORLD_GEN_LEGACY);
    }

    /** A current-version world laid out from this seed. */
    public BiomeManager(long worldSeed) {
        this(worldSeed, WorldConstants.WORLD_GEN_CURRENT);
    }

    public BiomeManager(long worldSeed, int version) {
        this.version = (version == WorldConstants.WORLD_GEN_LEGACY)
                ? WorldConstants.WORLD_GEN_LEGACY : WorldConstants.WORLD_GEN_CURRENT;
        this.mazeRadius = WorldConstants.CENTRAL_MAZE_RADIUS;

        if (this.version == WorldConstants.WORLD_GEN_LEGACY) {
            initLegacy();
        } else {
            initCurrent(worldSeed);
        }
    }

    private void initLegacy() {
        this.noise = new FastNoiseLite(WorldConstants.WORLD_SEED);
        this.noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.noise.SetFrequency(WorldConstants.BIOME_NOISE_FREQUENCY);

        this.humidityNoise = new FastNoiseLite(WorldConstants.WORLD_SEED + 1013);
        this.humidityNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.humidityNoise.SetFrequency(WorldConstants.HUMIDITY_NOISE_FREQUENCY);

        this.forestRadius = this.mazeRadius + WorldConstants.FOREST_BORDER_SIZE;
    }

    private void initCurrent(long worldSeed) {
        // FastNoiseLite takes an int seed; fold the long so both halves count.
        int base = (int) (worldSeed ^ (worldSeed >>> 32));

        this.elevationNoise = layer(base, WorldConstants.BIOME_NOISE_FREQUENCY);
        this.warpNoise = layer(base + 7919, WorldConstants.BAND_WARP_FREQUENCY);
        this.selectorNoise = layer(base + 15485, WorldConstants.BAND_SELECTOR_FREQUENCY);
        this.castleEdgeNoise = layer(base + 32452, 0.18f);

        // Its own stream, so retuning a noise layer never moves the castle.
        Random rng = new Random(worldSeed ^ 0xCA571E7A2111L);
        double angle = rng.nextDouble() * Math.PI * 2.0;
        double dist = WorldConstants.CASTLE_MIN_DISTANCE
                + rng.nextDouble() * (WorldConstants.CASTLE_MAX_DISTANCE - WorldConstants.CASTLE_MIN_DISTANCE);
        this.castleSite = new GridPoint2(
                (int) Math.round(Math.cos(angle) * dist),
                (int) Math.round(Math.sin(angle) * dist));
    }

    private static FastNoiseLite layer(int seed, float frequency) {
        FastNoiseLite n = new FastNoiseLite(seed);
        n.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        n.SetFrequency(frequency);
        return n;
    }

    public int getVersion() {
        return version;
    }

    public boolean isLegacy() {
        return version == WorldConstants.WORLD_GEN_LEGACY;
    }

    /** The chunk Castle Tarmin stands in, or null in a legacy world, which has no site. */
    public GridPoint2 getCastleSite() {
        return castleSite == null ? null : new GridPoint2(castleSite);
    }

    public boolean isCastleChunk(GridPoint2 chunkId) {
        return castleSite != null && chunkId != null && castleSite.equals(chunkId);
    }

    /**
     * Gets the biome for a chunk coordinate.
     * @param chunkId The (x, y) coordinate of the chunk.
     * @return The Biome enum for that location.
     */
    public Biome getBiome(GridPoint2 chunkId) {
        return isLegacy() ? legacyBiome(chunkId) : currentBiome(chunkId.x, chunkId.y);
    }

    /**
     * The first Blight chunk walking the castle corridor out from the maze.
     *
     * <p>The Crimson Gate lands here rather than at the nearest Blight chunk
     * anywhere: the corridor is the one route guaranteed to reach the castle, so
     * an arrival on it can never be a Blight pocket cut off by sea.
     */
    public GridPoint2 findCorridorBlightEntry() {
        if (castleSite == null) return null;
        double len = Math.hypot(castleSite.x, castleSite.y);
        int steps = (int) Math.ceil(len * 4);
        GridPoint2 last = null;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            GridPoint2 c = new GridPoint2((int) Math.round(castleSite.x * t), (int) Math.round(castleSite.y * t));
            if (c.equals(last)) continue;
            last = c;
            if (currentBiome(c.x, c.y) == Biome.BLIGHT) return c;
        }
        return new GridPoint2(castleSite);
    }

    // ------------------------------------------------------------------
    // Version 2
    // ------------------------------------------------------------------

    private Biome currentBiome(int x, int y) {
        int cheb = Math.max(Math.abs(x), Math.abs(y));
        if (cheb <= mazeRadius) {
            return Biome.MAZE;
        }

        // The one fixed relationship: the land around the castle is the Blight.
        double toCastle = Math.hypot(x - castleSite.x, y - castleSite.y);
        float edge = castleEdgeNoise.GetNoise(x, y) * 1.5f;
        if (toCastle <= WorldConstants.CASTLE_BLIGHT_RADIUS + edge) {
            return Biome.BLIGHT;
        }

        int d = cheb - mazeRadius;

        // Impassable terrain, kept off the way out of the maze and off the castle road.
        if (d > WorldConstants.CLEAR_GROUND_RADIUS && !inCastleCorridor(x, y)) {
            float elevation = elevationNoise.GetNoise(x, y);
            if (elevation < WorldConstants.OCEAN_THRESHOLD) return Biome.OCEAN;
            if (elevation > WorldConstants.MOUNTAIN_THRESHOLD) return Biome.MOUNTAINS;
        }

        float wd = d + warpNoise.GetNoise(x, y) * WorldConstants.BAND_WARP_CHUNKS;
        return pickFromBands(wd, selectorNoise.GetNoise(x, y));
    }

    /** Ordered as the portal ladder; the selector picks among those whose band holds wd. */
    private static final Biome[] BAND_ORDER = {
            Biome.FOREST, Biome.DESERT, Biome.LAKELANDS, Biome.TUNDRA, Biome.BLIGHT
    };

    private static boolean inBand(Biome b, float wd) {
        switch (b) {
            case FOREST:    return wd <= WorldConstants.FOREST_BAND_MAX;
            case DESERT:    return wd >= WorldConstants.DESERT_BAND_MIN && wd <= WorldConstants.DESERT_BAND_MAX;
            case LAKELANDS: return wd >= WorldConstants.LAKELANDS_BAND_MIN && wd <= WorldConstants.LAKELANDS_BAND_MAX;
            case TUNDRA:    return wd >= WorldConstants.TUNDRA_BAND_MIN && wd <= WorldConstants.TUNDRA_BAND_MAX;
            case BLIGHT:    return wd >= WorldConstants.BLIGHT_BAND_MIN;
            default:        return false;
        }
    }

    static Biome pickFromBands(float wd, float selector) {
        Biome[] eligible = new Biome[BAND_ORDER.length];
        int count = 0;
        for (Biome b : BAND_ORDER) {
            if (inBand(b, wd)) eligible[count++] = b;
        }
        if (count == 0) {
            // Bands overlap end to end, so only a misconfigured tuning lands here.
            return wd <= WorldConstants.FOREST_BAND_MAX ? Biome.FOREST : Biome.BLIGHT;
        }
        float s = Math.max(0f, Math.min(0.9999f, (selector + 1f) * 0.5f));
        return eligible[(int) (s * count)];
    }

    /** Within the corridor's half-width of the straight line from the origin to the castle. */
    boolean inCastleCorridor(int x, int y) {
        double cx = castleSite.x;
        double cy = castleSite.y;
        double lenSq = cx * cx + cy * cy;
        double t = lenSq == 0 ? 0 : Math.max(0, Math.min(1, (x * cx + y * cy) / lenSq));
        double px = cx * t - x;
        double py = cy * t - y;
        return Math.sqrt(px * px + py * py) <= WorldConstants.CASTLE_CORRIDOR_HALF_WIDTH;
    }

    // ------------------------------------------------------------------
    // Version 1 -- unchanged from before world-gen versions existed
    // ------------------------------------------------------------------

    private Biome legacyBiome(GridPoint2 chunkId) {

        // Rule 1: Check for the central MAZE area
        // We use 'max' to check distance, creating a square boundary
        if (Math.max(Math.abs(chunkId.x), Math.abs(chunkId.y)) <= mazeRadius) {
            return Biome.MAZE;
        }

        // Rule 2: Check for the FOREST border around the maze
        if (Math.max(Math.abs(chunkId.x), Math.abs(chunkId.y)) <= forestRadius) {
            return Biome.FOREST;
        }

        // Rule 3: We are in the "Wilderness." Use noise to decide.
        // Get a noise value between -1.0 and 1.0 (elevation)
        float noiseValue = noise.GetNoise(chunkId.x, chunkId.y);

        if (noiseValue < WorldConstants.OCEAN_THRESHOLD) {
            return Biome.OCEAN;
        } else if (noiseValue > WorldConstants.MOUNTAIN_THRESHOLD) {
            return Biome.MOUNTAINS;
        } else {
            // Far North wilderness (beyond forest border, y >= 16): Siberian Tundra
            if (chunkId.y >= 16) {
                return Biome.TUNDRA;
            }
            // Wilderness land biomes: determine via humidity noise
            float humidity = humidityNoise.GetNoise(chunkId.x, chunkId.y);
            if (humidity < WorldConstants.DESERT_HUMIDITY_THRESHOLD) {
                return Biome.DESERT;
            } else if (humidity > WorldConstants.LAKELANDS_HUMIDITY_THRESHOLD) {
                return Biome.LAKELANDS;
            } else {
                return Biome.FOREST;
            }
        }
    }
}
