package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import java.util.HashMap;
import java.util.Map;

public class GoreManager {

    // --- Configuration ---
    // Global Red Color for all blood effects
    public static final Color UNIFIED_BLOOD_COLOR = new Color(0.7f, 0.05f, 0.05f, 1.0f);

    // --- Particles ---
    private final Array<BloodParticle> activeParticles = new Array<>(false, 500);
    private final Pool<BloodParticle> particlePool = new Pool<>() {
        @Override
        protected BloodParticle newObject() {
            return new BloodParticle();
        }
    };

    // --- Floor Decals ---
    private final Array<SurfaceDecal> activeDecals = new Array<>(false, 1000);
    private final Pool<SurfaceDecal> decalPool = new Pool<>() {
        @Override
        protected SurfaceDecal newObject() {
            return new SurfaceDecal();
        }
    };

    // --- Gibs ---
    private final Array<Gib> activeGibs = new Array<>(false, 100);
    private final Pool<Gib> gibPool = new Pool<>() {
        @Override
        protected Gib newObject() {
            return new Gib();
        }
    };

    // --- Wall Decals ---
    // Key: (x * 1000 + y) * 2 + side. Allows fast lookup per wall face.
    private final Map<Integer, Array<WallDecal>> wallDecals = new HashMap<>();
    private final Pool<WallDecal> wallDecalPool = new Pool<>() {
        @Override
        protected WallDecal newObject() {
            return new WallDecal();
        }
    };

    public GoreManager() {}

    /**
     * Spawns blood particles using the global UNIFIED_BLOOD_COLOR.
     * Includes variation logic for clots vs arterial spray.
     */
    public void spawnBloodSpray(Vector3 origin, Vector3 direction, int intensity) {
        int count = intensity * 12;
        for (int i = 0; i < count; i++) {
            BloodParticle p = particlePool.obtain();
            float spreadX = MathUtils.random(-0.4f, 0.4f);
            float spreadY = MathUtils.random(-0.1f, 0.7f);
            float spreadZ = MathUtils.random(-0.4f, 0.4f);
            float speed = MathUtils.random(3.0f, 9.0f);
            Vector3 vel = new Vector3(direction).scl(0.6f).add(spreadX, spreadY, spreadZ).nor().scl(speed);

            // --- Color Variation Logic ---
            Color finalColor = new Color(UNIFIED_BLOOD_COLOR);
            float typeRoll = MathUtils.random();

            if (typeRoll < 0.3f) {
                // Darker "Clot" (30% chance)
                finalColor.r *= 0.6f;
                finalColor.g *= 0.6f;
                finalColor.b *= 0.6f;
            } else if (typeRoll < 0.6f) {
                // Brighter "Arterial" Spray (30% chance)
                finalColor.r = Math.min(1.0f, finalColor.r * 1.3f);
                finalColor.g *= 0.9f; // Keep it red
                finalColor.b *= 0.9f;
            }
            // Remaining 40% is the base UNIFIED_BLOOD_COLOR

            float size = MathUtils.random(0.03f, 0.07f);
            float life = MathUtils.random(1.0f, 3.0f);
            p.init(origin, vel, finalColor, life, size);
            activeParticles.add(p);
        }
    }

    /**
     * Spawns gibs using the global UNIFIED_BLOOD_COLOR.
     */
    public void spawnGibExplosion(Vector3 origin) {
        int meatCount = MathUtils.random(3, 6);
        int boneCount = MathUtils.random(2, 4);

        // Use unified color for meat chunks
        for(int i=0; i<meatCount; i++) spawnGib(origin, GibType.MEAT_CHUNK, UNIFIED_BLOOD_COLOR);

        // Bones, ribs, intestines, etc usually have their own default tint or use the unified color if we choose
        for(int i=0; i<boneCount; i++) spawnGib(origin, GibType.BONE_SHARD, UNIFIED_BLOOD_COLOR);
        if(MathUtils.randomBoolean()) spawnGib(origin, GibType.RIB_CAGE, UNIFIED_BLOOD_COLOR);
        if(MathUtils.randomBoolean()) spawnGib(origin, GibType.INTESTINE, UNIFIED_BLOOD_COLOR);
        if(MathUtils.randomBoolean(0.3f)) spawnGib(origin, GibType.EYEBALL, UNIFIED_BLOOD_COLOR);
    }

    private void spawnGib(Vector3 origin, GibType type, Color color) {
        Gib g = gibPool.obtain();
        Vector3 vel = new Vector3(MathUtils.random(-1f, 1f), MathUtils.random(2f, 6f), MathUtils.random(-1f, 1f)).nor().scl(MathUtils.random(3f, 8f));
        g.init(origin, vel, type, color);
        activeGibs.add(g);
    }

    private void spawnWallDecal(int x, int y, int side, float wallX, float height, float radius, Color color) {
        int key = (x * 1000 + y) * 2 + side;
        if (!wallDecals.containsKey(key)) {
            wallDecals.put(key, new Array<>());
        }
        float splatRadius = radius * MathUtils.random(1.5f, 3.0f);
        WallDecal wd = wallDecalPool.obtain();
        wd.init(x, y, side, wallX, height, splatRadius, color);
        wallDecals.get(key).add(wd);
    }

    public Array<WallDecal> getWallDecals(int x, int y, int side) {
        int key = (x * 1000 + y) * 2 + side;
        return wallDecals.get(key);
    }

    public void update(float delta, Maze maze) {
        for (int i = activeParticles.size - 1; i >= 0; i--) {
            BloodParticle p = activeParticles.get(i);
            float prevX = p.position.x;
            float prevZ = p.position.z;
            p.update(delta);

            // --- THIN WALL COLLISION LOGIC ---
            int currGridX = (int)p.position.x;
            int currGridY = (int)p.position.z;
            int prevGridX = (int)prevX;
            int prevGridY = (int)prevZ;

            boolean hitWall = false;
            int hitX = currGridX;
            int hitY = currGridY;
            int side = -1; // 0 = East/West, 1 = North/South
            float wallX = 0;

            // 1. Check X-Axis Crossing (East/West Walls)
            if (currGridX != prevGridX) {
                Direction dir = (currGridX > prevGridX) ? Direction.EAST : Direction.WEST;
                if (maze.isWallBlocking(prevGridX, prevGridY, dir)) {
                    hitWall = true;
                    side = 0;
                    hitX = prevGridX;
                    hitY = prevGridY;
                    wallX = p.position.z - (int)p.position.z;
                }
            }

            // 2. Check Z-Axis Crossing (North/South Walls)
            if (!hitWall && currGridY != prevGridY) {
                Direction dir = (currGridY > prevGridY) ? Direction.NORTH : Direction.SOUTH;
                if (maze.isWallBlocking(prevGridX, prevGridY, dir)) {
                    hitWall = true;
                    side = 1;
                    wallX = p.position.x - (int)p.position.x;
                    hitX = prevGridX;
                    hitY = prevGridY;
                }
            }

            if (hitWall && side != -1) {
                spawnWallDecal(hitX, hitY, side, wallX, p.position.y, p.size, p.color);
                activeParticles.removeIndex(i);
                particlePool.free(p);
                continue;
            }

            // 3. Floor Collision
            if (p.onGround) {
                spawnDecal(p.position, p.color, p.size);
                activeParticles.removeIndex(i);
                particlePool.free(p);
                continue;
            }

            if (p.lifeTimer <= 0) {
                activeParticles.removeIndex(i);
                particlePool.free(p);
            }
        }

        for (int i = activeDecals.size - 1; i >= 0; i--) {
            SurfaceDecal d = activeDecals.get(i);
            d.update(delta);
            if (d.lifeTimer <= 0) {
                activeDecals.removeIndex(i);
                decalPool.free(d);
            }
        }
        for (int i = activeGibs.size - 1; i >= 0; i--) {
            Gib g = activeGibs.get(i);
            g.update(delta);
            if (g.lifeTimer <= 0) {
                activeGibs.removeIndex(i);
                gibPool.free(g);
            }
        }
    }

    private void spawnDecal(Vector3 pos, Color color, float originalSize) {
        if (activeDecals.size >= 1000) {
            SurfaceDecal old = activeDecals.first();
            activeDecals.removeIndex(0);
            decalPool.free(old);
        }
        SurfaceDecal d = decalPool.obtain();
        d.init(pos, color, originalSize * 2.5f);
        activeDecals.add(d);
    }

    public Array<BloodParticle> getActiveParticles() { return activeParticles; }
    public Array<SurfaceDecal> getActiveDecals() { return activeDecals; }
    public Array<Gib> getActiveGibs() { return activeGibs; }
}
