package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.badlogic.gdx.graphics.g2d.PolygonRegion;
import java.util.HashMap;
import java.util.Map;
import com.bpm.minotaur.utils.ShatterUtils;

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

    public GoreManager() {
    }

    // --- Modern Textures ---
    private final Array<com.badlogic.gdx.graphics.g2d.TextureRegion> dropTextures = new Array<>();
    private final Array<com.badlogic.gdx.graphics.g2d.TextureRegion> smearTextures = new Array<>();
    private final Array<com.badlogic.gdx.graphics.g2d.TextureRegion> gibTextures = new Array<>();
    private com.badlogic.gdx.graphics.g2d.TextureRegion spatterTexture;

    public void setTextures(Array<com.badlogic.gdx.graphics.g2d.TextureRegion> drops,
            Array<com.badlogic.gdx.graphics.g2d.TextureRegion> smears,
            com.badlogic.gdx.graphics.g2d.TextureRegion spatter,
            Array<com.badlogic.gdx.graphics.g2d.TextureRegion> gibs) {
        Gdx.app.log("GoreManager", "setTextures called. Drops: " + drops.size + ", Smears: " + smears.size);
        this.dropTextures.clear();
        this.dropTextures.addAll(drops);
        this.smearTextures.clear();
        this.smearTextures.addAll(smears);
        this.spatterTexture = spatter;
        this.gibTextures.clear();
        this.gibTextures.addAll(gibs);
    }

    /**
     * Spawns blood particles using the global UNIFIED_BLOOD_COLOR.
     * Includes variation logic for clots vs arterial spray.
     */
    public void spawnBloodSpray(Vector3 origin, Vector3 direction, int intensity) {
        Gdx.app.log("GoreManager", "spawnBloodSpray called at " + origin + " with intensity " + intensity);
        int count = intensity * 10;
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

            com.badlogic.gdx.graphics.g2d.TextureRegion tex = null;
            if (dropTextures.size > 0) {
                tex = dropTextures.random();
            } else {
                Gdx.app.log("GoreManager", "No drop textures available!");
            }

            p.init(origin, vel, finalColor, life, size, tex);
            activeParticles.add(p);
        }
        Gdx.app.log("GoreManager", "Active particles count: " + activeParticles.size);
    }

    /**
     * Spawns gibs using the global UNIFIED_BLOOD_COLOR.
     */
    public void spawnGibExplosion(Vector3 origin) {
        // Modern Mode: Texture Gibs
        if (gibTextures.size > 0) {
            int count = MathUtils.random(6, 10);
            for (int i = 0; i < count; i++) {
                Gib g = gibPool.obtain();
                com.badlogic.gdx.graphics.g2d.TextureRegion tex = gibTextures.random();

                // Explosive velocity: Up and out
                float angle = MathUtils.random(0, 360) * MathUtils.degreesToRadians;
                float speed = MathUtils.random(2.0f, 6.0f);
                float up = MathUtils.random(3.0f, 8.0f);

                Vector3 vel = new Vector3(MathUtils.cos(angle) * speed, up, MathUtils.sin(angle) * speed);

                g.init(origin, vel, tex);
                activeGibs.add(g);
            }
        }
    }

    private void spawnGib(Vector3 origin, GibType type, Color color) {
        Gib g = gibPool.obtain();
        Vector3 vel = new Vector3(MathUtils.random(-1f, 1f), MathUtils.random(2f, 6f), MathUtils.random(-1f, 1f)).nor()
                .scl(MathUtils.random(3f, 8f));
        g.init(origin, vel, type, color);
        activeGibs.add(g);
    }

    public void spawnTextureGibs(Vector3 origin, com.badlogic.gdx.graphics.Texture texture) {
        // Delegate to authentic gibs textures from gore atlas
        spawnGibExplosion(origin);
    }

    public void spawnRetroGibs(Vector3 origin, String[] spriteData, Color color) {
        if (spriteData == null || spriteData.length == 0)
            return;

        int rows = spriteData.length;
        int cols = spriteData[0].length();
        int halfRows = rows / 2;
        int halfCols = cols / 2;

        // Top-Left
        spawnQuadrantGib(origin, spriteData, 0, 0, halfCols, halfRows, color);
        // Top-Right
        spawnQuadrantGib(origin, spriteData, halfCols, 0, cols, halfRows, color);
        // Bottom-Left
        spawnQuadrantGib(origin, spriteData, 0, halfRows, halfCols, rows, color);
        // Bottom-Right
        spawnQuadrantGib(origin, spriteData, halfCols, halfRows, cols, rows, color);
    }

    private void spawnQuadrantGib(Vector3 origin, String[] fullSprite, int startCol, int startRow, int endCol,
            int endRow, Color color) {
        int height = endRow - startRow;
        String[] chunk = new String[height];
        boolean isEmpty = true;

        for (int i = 0; i < height; i++) {
            if (startRow + i < fullSprite.length) {
                String row = fullSprite[startRow + i];
                // Ensure we don't go out of bounds if row is short
                int actualStart = Math.max(0, Math.min(row.length(), startCol));
                int actualEnd = Math.max(0, Math.min(row.length(), endCol));
                chunk[i] = row.substring(actualStart, actualEnd);

                // Check if this chunk actually has pixels
                for (char c : chunk[i].toCharArray()) {
                    if (c != '.') {
                        isEmpty = false;
                    }
                }
            } else {
                chunk[i] = "";
            }
        }

        if (isEmpty)
            return;

        Gib g = gibPool.obtain();
        // Burst outwards
        float spreadX = (startCol < endCol / 2) ? -1f : 1f; // Rough heuristic
        float spreadZ = MathUtils.random(-0.5f, 0.5f);

        Vector3 vel = new Vector3(
                MathUtils.random(-1f, 1f),
                MathUtils.random(3f, 6f),
                MathUtils.random(-1f, 1f)).nor().scl(MathUtils.random(3f, 7f));

        g.init(origin, vel, chunk, color);
        activeGibs.add(g);
    }

    private void spawnWallDecal(int x, int y, Direction dir, float wallX, float height, float radius, Color color) {
        int side = (dir == Direction.EAST || dir == Direction.WEST) ? 0 : 1;
        int key = (x * 1000 + y) * 2 + side;
        if (!wallDecals.containsKey(key)) {
            wallDecals.put(key, new Array<>());
        }
        float splatRadius = radius * MathUtils.random(1.5f, 3.0f);
        WallDecal wd = wallDecalPool.obtain();

        com.badlogic.gdx.graphics.g2d.TextureRegion tex = null;
        if (spatterTexture != null && MathUtils.randomBoolean(0.3f)) {
            tex = spatterTexture;
        } else if (smearTextures.size > 0) {
            tex = smearTextures.random();
        } else if (dropTextures.size > 0) {
            tex = dropTextures.random();
        }

        wd.init(x, y, dir, wallX, height, splatRadius, color, tex);
        wallDecals.get(key).add(wd);
    }

    private void spawnWallDecal(int x, int y, int side, float wallX, float height, float radius, Color color) {
        Direction fallbackDir = (side == 0) ? Direction.WEST : Direction.NORTH;
        spawnWallDecal(x, y, fallbackDir, wallX, height, radius, color);
    }

    public Array<WallDecal> getWallDecals(int x, int y, int side) {
        int key = (x * 1000 + y) * 2 + side;
        return wallDecals.get(key);
    }

    public Map<Integer, Array<WallDecal>> getAllWallDecals() {
        return wallDecals;
    }

    public void update(float delta, Maze maze) {
        for (int i = activeParticles.size - 1; i >= 0; i--) {
            BloodParticle p = activeParticles.get(i);
            float prevX = p.position.x;
            float prevZ = p.position.z;
            p.update(delta);

            // --- THIN WALL COLLISION LOGIC ---
            int currGridX = (int) p.position.x;
            int currGridY = (int) p.position.z;
            int prevGridX = (int) prevX;
            int prevGridY = (int) prevZ;

            boolean hitWall = false;
            int hitX = prevGridX;
            int hitY = prevGridY;
            Direction hitDir = null;
            float wallX = 0;

            // Walls only exist from Y = 0.0 to Y = 1.0; particles above 1.0 fly freely over walls
            if (p.position.y >= 0.0f && p.position.y <= 1.0f) {
                // 1. Check X-Axis Crossing (East/West Walls)
                if (currGridX != prevGridX) {
                    Direction dir = (currGridX > prevGridX) ? Direction.EAST : Direction.WEST;
                    if (maze.isWallBlocking(prevGridX, prevGridY, dir)) {
                        hitWall = true;
                        hitDir = dir;
                        hitX = prevGridX;
                        hitY = prevGridY;
                        wallX = p.position.z - (int) p.position.z;
                    }
                }

                // 2. Check Z-Axis Crossing (North/South Walls)
                if (!hitWall && currGridY != prevGridY) {
                    Direction dir = (currGridY > prevGridY) ? Direction.NORTH : Direction.SOUTH;
                    if (maze.isWallBlocking(prevGridX, prevGridY, dir)) {
                        hitWall = true;
                        hitDir = dir;
                        wallX = p.position.x - (int) p.position.x;
                        hitX = prevGridX;
                        hitY = prevGridY;
                    }
                }
            }

            if (hitWall && hitDir != null) {
                spawnWallDecal(hitX, hitY, hitDir, wallX, p.position.y, p.size, p.color);
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

        com.badlogic.gdx.graphics.g2d.TextureRegion tex = null;
        if (smearTextures.size > 0) {
            tex = smearTextures.random();
        } else if (spatterTexture != null) {
            tex = spatterTexture;
        }

        d.init(pos, color, originalSize * 2.5f, tex);
        activeDecals.add(d);
    }

    public Array<BloodParticle> getActiveParticles() {
        return activeParticles;
    }

    public Array<SurfaceDecal> getActiveDecals() {
        return activeDecals;
    }

    public Array<Gib> getActiveGibs() {
        return activeGibs;
    }
}
