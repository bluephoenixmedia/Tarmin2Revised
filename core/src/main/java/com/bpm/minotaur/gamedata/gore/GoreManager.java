package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.managers.WorldManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized, high-performance simulation manager for the 3D Viscera gore system.
 * Manages object pools for flying blood particles, tumbling gibs, expanding floor puddles,
 * and coplanar wall splatters. Operates in continuous world coordinates.
 */
public class GoreManager {

    public static final Color UNIFIED_BLOOD_COLOR = new Color(0.77f, 0.12f, 0.12f, 1.0f);

    // --- High-Performance Pool Budgets ---
    public static final int MAX_ACTIVE_PARTICLES = 250;
    public static final int MAX_ACTIVE_GIBS = 40;
    public static final int MAX_ACTIVE_SURFACE_DECALS = 200;
    public static final int MAX_ACTIVE_WALL_DECALS = 100;

    // --- Entity Pools ---
    private final Pool<BloodParticle> particlePool = new Pool<BloodParticle>(64, MAX_ACTIVE_PARTICLES) {
        @Override
        protected BloodParticle newObject() {
            return new BloodParticle();
        }
    };

    private final Pool<SurfaceDecal> surfaceDecalPool = new Pool<SurfaceDecal>(32, MAX_ACTIVE_SURFACE_DECALS) {
        @Override
        protected SurfaceDecal newObject() {
            return new SurfaceDecal();
        }
    };

    private final Pool<WallDecal> wallDecalPool = new Pool<WallDecal>(32, MAX_ACTIVE_WALL_DECALS) {
        @Override
        protected WallDecal newObject() {
            return new WallDecal();
        }
    };

    private final Pool<Gib> gibPool = new Pool<Gib>(16, MAX_ACTIVE_GIBS) {
        @Override
        protected Gib newObject() {
            return new Gib();
        }
    };

    // --- Active Entity Lists ---
    private final Array<BloodParticle> activeParticles = new Array<>(false, MAX_ACTIVE_PARTICLES);
    private final Array<SurfaceDecal> activeSurfaceDecals = new Array<>(false, MAX_ACTIVE_SURFACE_DECALS);
    private final Array<WallDecal> activeWallDecals = new Array<>(false, MAX_ACTIVE_WALL_DECALS);
    private final Array<Gib> activeGibs = new Array<>(false, MAX_ACTIVE_GIBS);
    private final Map<Integer, Array<WallDecal>> wallDecalsByKey = new HashMap<>();

    // --- Textures from gore.atlas ---
    private final Array<TextureRegion> dropTextures = new Array<>();
    private final Array<TextureRegion> smearTextures = new Array<>();
    private final Array<TextureRegion> gibTextures = new Array<>();
    private TextureRegion spatterTexture;

    public GoreManager() {
    }

    public void setTextures(TextureAtlas atlas) {
        if (atlas == null) return;

        dropTextures.clear();
        for (int i = 1; i <= 4; i++) {
            TextureRegion r = atlas.findRegion("blood_drop" + i);
            if (r != null) dropTextures.add(r);
        }

        smearTextures.clear();
        for (int i = 1; i <= 3; i++) {
            TextureRegion r = atlas.findRegion("blood_smear" + i);
            if (r != null) smearTextures.add(r);
        }

        spatterTexture = atlas.findRegion("blood_spatter");

        gibTextures.clear();
        for (int i = 1; i <= 10; i++) {
            TextureRegion r = atlas.findRegion("gib" + i);
            if (r != null) gibTextures.add(r);
        }

        if (Gdx.app != null) {
            Gdx.app.log("GoreManager", "Loaded gore atlas textures: " + dropTextures.size + " drops, "
                    + smearTextures.size + " smears, " + gibTextures.size + " gibs.");
        }
    }

    public void setTextures(
            Array<TextureRegion> drops,
            Array<TextureRegion> smears,
            TextureRegion spatter,
            Array<TextureRegion> gibs
    ) {
        this.dropTextures.clear();
        if (drops != null) this.dropTextures.addAll(drops);

        this.smearTextures.clear();
        if (smears != null) this.smearTextures.addAll(smears);

        this.spatterTexture = spatter;

        this.gibTextures.clear();
        if (gibs != null) this.gibTextures.addAll(gibs);
    }

    // --- Spawn API ---

    public void spawnBloodSpray(Vector3 origin, Vector3 direction, int intensity) {
        spawnBloodSpray(origin, direction, intensity, GoreProfile.FLESH);
    }

    public void spawnBloodSpray(Vector3 origin, Vector3 direction, int intensity, GoreProfile profile) {
        if (profile == null) profile = GoreProfile.FLESH;

        // Incorporeal creatures emit no blood or particles
        if (profile == GoreProfile.INCORPOREAL) return;

        int count = Math.max(3, intensity * 6);

        // Budget check
        int availableSlots = MAX_ACTIVE_PARTICLES - activeParticles.size;
        count = Math.min(count, availableSlots);
        if (count <= 0) return;

        Color baseColor = (profile.primaryColor != null) ? profile.primaryColor : UNIFIED_BLOOD_COLOR;

        for (int i = 0; i < count; i++) {
            BloodParticle p = particlePool.obtain();
            float spreadX = MathUtils.random(-0.4f, 0.4f);
            float spreadY = MathUtils.random(-0.1f, 0.6f);
            float spreadZ = MathUtils.random(-0.4f, 0.4f);
            float speed = MathUtils.random(3.0f, 8.5f);

            Vector3 vel = new Vector3(direction).scl(0.65f).add(spreadX, spreadY, spreadZ).nor().scl(speed);

            Color particleColor = new Color(baseColor);
            if (profile == GoreProfile.FLESH) {
                float roll = MathUtils.random();
                if (roll < 0.3f) {
                    // Dark clot
                    particleColor.r *= 0.6f;
                    particleColor.g *= 0.5f;
                    particleColor.b *= 0.5f;
                } else if (roll < 0.6f) {
                    // Bright arterial spray
                    particleColor.r = Math.min(1.0f, particleColor.r * 1.25f);
                    particleColor.g *= 0.8f;
                    particleColor.b *= 0.8f;
                }
            } else if (profile == GoreProfile.SKELETAL) {
                // Bone dust variation (subtle grey / ivory shifts)
                float shift = MathUtils.random(-0.1f, 0.1f);
                particleColor.add(shift, shift, shift, 0f);
            }

            float size = (profile == GoreProfile.SKELETAL)
                    ? MathUtils.random(0.02f, 0.04f)
                    : MathUtils.random(0.035f, 0.07f);
            float life = MathUtils.random(0.8f, 2.5f);

            TextureRegion tex = (dropTextures.size > 0) ? dropTextures.random() : null;

            p.init(origin, vel, particleColor, life, size, tex);
            activeParticles.add(p);
        }
    }

    public void spawnGibExplosion(Vector3 origin) {
        spawnGibExplosion(origin, Vector3.Y, 1, GoreProfile.FLESH);
    }

    public void spawnGibExplosion(Vector3 origin, Vector3 exitVector, int overkillTier, GoreProfile profile) {
        if (profile == null) profile = GoreProfile.FLESH;
        if (!profile.hasGibs) return;

        int count = (overkillTier >= 2) ? MathUtils.random(7, 12) : MathUtils.random(3, 6);

        Color tint = (profile == GoreProfile.SKELETAL)
                ? new Color(0.90f, 0.88f, 0.80f, 1.0f) // Ivory bone tint
                : Color.WHITE;

        Vector3 exitNorm = (exitVector != null && exitVector.len2() > 0.001f)
                ? new Vector3(exitVector).nor()
                : Vector3.Y;

        for (int i = 0; i < count; i++) {
            if (activeGibs.size >= MAX_ACTIVE_GIBS) {
                Gib oldest = activeGibs.removeIndex(0);
                gibPool.free(oldest);
            }

            Gib g = gibPool.obtain();
            TextureRegion tex = (gibTextures.size > 0) ? gibTextures.random() : null;

            // Explosion velocity: radial scatter outward with bias along exit vector
            float angle = MathUtils.random(0, 360) * MathUtils.degreesToRadians;
            float radialSpeed = MathUtils.random(2.0f, 6.5f);
            float up = MathUtils.random(2.5f, 7.5f);

            Vector3 vel = new Vector3(
                    MathUtils.cos(angle) * radialSpeed + exitNorm.x * 2.0f,
                    up,
                    MathUtils.sin(angle) * radialSpeed + exitNorm.z * 2.0f
            );

            g.init(origin, vel, tex, tint);
            activeGibs.add(g);
        }
    }

    public void spawnRetroGibs(Vector3 origin, String[] spriteData, Color color) {
        if (spriteData == null || spriteData.length == 0) return;

        int rows = spriteData.length;
        int cols = spriteData[0].length();
        int halfRows = rows / 2;
        int halfCols = cols / 2;

        spawnQuadrantGib(origin, spriteData, 0, 0, halfCols, halfRows, color);
        spawnQuadrantGib(origin, spriteData, halfCols, 0, cols, halfRows, color);
        spawnQuadrantGib(origin, spriteData, 0, halfRows, halfCols, rows, color);
        spawnQuadrantGib(origin, spriteData, halfCols, halfRows, cols, rows, color);
    }

    private void spawnQuadrantGib(Vector3 origin, String[] fullSprite, int startCol, int startRow, int endCol, int endRow, Color color) {
        int height = endRow - startRow;
        String[] chunk = new String[height];
        boolean isEmpty = true;

        for (int i = 0; i < height; i++) {
            if (startRow + i < fullSprite.length) {
                String row = fullSprite[startRow + i];
                int actualStart = Math.max(0, Math.min(row.length(), startCol));
                int actualEnd = Math.max(0, Math.min(row.length(), endCol));
                chunk[i] = row.substring(actualStart, actualEnd);

                for (char c : chunk[i].toCharArray()) {
                    if (c != '.') isEmpty = false;
                }
            } else {
                chunk[i] = "";
            }
        }

        if (isEmpty) return;

        if (activeGibs.size >= MAX_ACTIVE_GIBS) {
            Gib oldest = activeGibs.removeIndex(0);
            gibPool.free(oldest);
        }

        Gib g = gibPool.obtain();
        Vector3 vel = new Vector3(
                MathUtils.random(-1f, 1f),
                MathUtils.random(3f, 6f),
                MathUtils.random(-1f, 1f)
        ).nor().scl(MathUtils.random(3f, 7f));

        g.init(origin, vel, chunk, color);
        activeGibs.add(g);
    }

    // --- Decal Spawning ---

    public void spawnSurfaceDecal(Vector3 pos, Color color, float targetRadius) {
        if (activeSurfaceDecals.size >= MAX_ACTIVE_SURFACE_DECALS) {
            SurfaceDecal old = activeSurfaceDecals.removeIndex(0);
            surfaceDecalPool.free(old);
        }

        SurfaceDecal d = surfaceDecalPool.obtain();
        TextureRegion tex = (smearTextures.size > 0) ? smearTextures.random() : spatterTexture;
        d.init(pos, color, targetRadius, tex);
        activeSurfaceDecals.add(d);
    }

    public void spawnWallDecal(int x, int y, Direction dir, float wallX, float height, float radius, Color color) {
        if (activeWallDecals.size >= MAX_ACTIVE_WALL_DECALS) {
            WallDecal old = activeWallDecals.removeIndex(0);
            int oldKey = (old.gridX * 1000 + old.gridY) * 2 + old.side;
            Array<WallDecal> list = wallDecalsByKey.get(oldKey);
            if (list != null) {
                list.removeValue(old, true);
                if (list.size == 0) wallDecalsByKey.remove(oldKey);
            }
            wallDecalPool.free(old);
        }

        int side = (dir == Direction.EAST || dir == Direction.WEST) ? 0 : 1;
        int key = (x * 1000 + y) * 2 + side;
        if (!wallDecalsByKey.containsKey(key)) {
            wallDecalsByKey.put(key, new Array<>());
        }

        float splatRadius = radius * MathUtils.random(1.5f, 2.5f);
        WallDecal wd = wallDecalPool.obtain();

        TextureRegion tex = null;
        if (spatterTexture != null && MathUtils.randomBoolean(0.35f)) {
            tex = spatterTexture;
        } else if (smearTextures.size > 0) {
            tex = smearTextures.random();
        } else if (dropTextures.size > 0) {
            tex = dropTextures.random();
        }

        wd.init(x, y, dir, wallX, height, splatRadius, color, tex);
        wallDecalsByKey.get(key).add(wd);
        activeWallDecals.add(wd);
    }

    // --- Update Loop ---

    public void update(float delta, Maze currentMaze) {
        update(delta, currentMaze, null);
    }

    public void update(float delta, Maze currentMaze, WorldManager worldManager) {
        // 1. Update Blood Particles & Surface Collisions
        for (int i = activeParticles.size - 1; i >= 0; i--) {
            BloodParticle p = activeParticles.get(i);
            float prevX = p.position.x;
            float prevZ = p.position.z;
            p.update(delta);

            int currGridX = (int) Math.floor(p.position.x);
            int currGridY = (int) Math.floor(p.position.z);
            int prevGridX = (int) Math.floor(prevX);
            int prevGridY = (int) Math.floor(prevZ);

            boolean hitWall = false;
            boolean hitDoorThreshold = false;
            int hitX = prevGridX;
            int hitY = prevGridY;
            Direction hitDir = null;
            float wallX = 0f;

            if (p.position.y >= 0.0f && p.position.y <= 1.0f) {
                // X-crossing
                if (currGridX != prevGridX) {
                    Direction dir = (currGridX > prevGridX) ? Direction.EAST : Direction.WEST;
                    Maze cellMaze = resolveMazeForWorldCell(prevGridX, prevGridY, currentMaze, worldManager);
                    if (cellMaze != null) {
                        int localX = resolveLocalCoord(prevGridX, cellMaze.getWidth());
                        int localY = resolveLocalCoord(prevGridY, cellMaze.getHeight());

                        if (isClosedDoorOrGate(cellMaze, localX, localY)) {
                            hitDoorThreshold = true;
                        } else if (cellMaze.isWallBlocking(localX, localY, dir)) {
                            hitWall = true;
                            hitDir = dir;
                            hitX = prevGridX;
                            hitY = prevGridY;
                            wallX = p.position.z - (float) Math.floor(p.position.z);
                        }
                    }
                }

                // Z-crossing
                if (!hitWall && !hitDoorThreshold && currGridY != prevGridY) {
                    Direction dir = (currGridY > prevGridY) ? Direction.NORTH : Direction.SOUTH;
                    Maze cellMaze = resolveMazeForWorldCell(prevGridX, prevGridY, currentMaze, worldManager);
                    if (cellMaze != null) {
                        int localX = resolveLocalCoord(prevGridX, cellMaze.getWidth());
                        int localY = resolveLocalCoord(prevGridY, cellMaze.getHeight());

                        if (isClosedDoorOrGate(cellMaze, localX, localY)) {
                            hitDoorThreshold = true;
                        } else if (cellMaze.isWallBlocking(localX, localY, dir)) {
                            hitWall = true;
                            hitDir = dir;
                            hitX = prevGridX;
                            hitY = prevGridY;
                            wallX = p.position.x - (float) Math.floor(p.position.x);
                        }
                    }
                }
            }

            // Door threshold deflection: closed door/gate drops blood to floor threshold
            if (hitDoorThreshold) {
                spawnSurfaceDecal(new Vector3(prevX, 0.02f, prevZ), p.color, MathUtils.random(0.14f, 0.22f));
                activeParticles.removeIndex(i);
                particlePool.free(p);
                continue;
            }

            // Static wall impact: stamp wall decal
            if (hitWall && hitDir != null) {
                spawnWallDecal(hitX, hitY, hitDir, wallX, p.position.y, p.size, p.color);
                activeParticles.removeIndex(i);
                particlePool.free(p);
                continue;
            }

            // Floor collision: expand into surface puddle
            if (p.onGround) {
                spawnSurfaceDecal(p.position, p.color, MathUtils.random(0.16f, 0.26f));
                activeParticles.removeIndex(i);
                particlePool.free(p);
                continue;
            }

            if (p.lifeTimer <= 0) {
                activeParticles.removeIndex(i);
                particlePool.free(p);
            }
        }

        // 2. Update Floor Surface Decals
        for (int i = activeSurfaceDecals.size - 1; i >= 0; i--) {
            SurfaceDecal d = activeSurfaceDecals.get(i);
            d.update(delta);
            if (d.lifeTimer <= 0) {
                activeSurfaceDecals.removeIndex(i);
                surfaceDecalPool.free(d);
            }
        }

        // 3. Update Wall Decals
        for (int i = activeWallDecals.size - 1; i >= 0; i--) {
            WallDecal wd = activeWallDecals.get(i);
            wd.update(delta);
            if (wd.lifeTimer <= 0) {
                activeWallDecals.removeIndex(i);
                int key = (wd.gridX * 1000 + wd.gridY) * 2 + wd.side;
                Array<WallDecal> list = wallDecalsByKey.get(key);
                if (list != null) {
                    list.removeValue(wd, true);
                    if (list.size == 0) wallDecalsByKey.remove(key);
                }
                wallDecalPool.free(wd);
            }
        }

        // 4. Update Gibs
        for (int i = activeGibs.size - 1; i >= 0; i--) {
            Gib g = activeGibs.get(i);
            g.update(delta);
            if (g.lifeTimer <= 0) {
                activeGibs.removeIndex(i);
                gibPool.free(g);
            }
        }
    }

    private Maze resolveMazeForWorldCell(int worldGridX, int worldGridY, Maze currentMaze, WorldManager worldManager) {
        if (currentMaze == null) return null;
        if (worldManager == null) return currentMaze;

        int chunkSize = currentMaze.getWidth();
        int chunkX = (int) Math.floor((double) worldGridX / chunkSize);
        int chunkY = (int) Math.floor((double) worldGridY / chunkSize);

        GridPoint2 activeId = worldManager.getCurrentPlayerChunkId();
        if (activeId != null && activeId.x == chunkX && activeId.y == chunkY) {
            return currentMaze;
        }

        return worldManager.getLoadedChunk(new GridPoint2(chunkX, chunkY));
    }

    private int resolveLocalCoord(int worldCoord, int chunkSize) {
        int r = worldCoord % chunkSize;
        return (r < 0) ? r + chunkSize : r;
    }

    private boolean isClosedDoorOrGate(Maze maze, int localX, int localY) {
        if (maze == null || localX < 0 || localX >= maze.getWidth() || localY < 0 || localY >= maze.getHeight()) {
            return false;
        }

        Object obj = maze.getGameObjectAt(localX, localY);
        if (obj instanceof Door) {
            Door door = (Door) obj;
            return !door.isOpen();
        }

        Gate gate = maze.getGateAt(localX, localY);
        if (gate != null) {
            return !gate.isOpen();
        }

        return false;
    }

    // --- Getters ---

    public Array<BloodParticle> getActiveParticles() {
        return activeParticles;
    }

    public Array<SurfaceDecal> getActiveDecals() {
        return activeSurfaceDecals;
    }

    public Array<WallDecal> getActiveWallDecals() {
        return activeWallDecals;
    }

    public Map<Integer, Array<WallDecal>> getAllWallDecals() {
        return wallDecalsByKey;
    }

    public Array<WallDecal> getWallDecals(int x, int y, int side) {
        int key = (x * 1000 + y) * 2 + side;
        return wallDecalsByKey.get(key);
    }

    public Array<Gib> getActiveGibs() {
        return activeGibs;
    }
}
