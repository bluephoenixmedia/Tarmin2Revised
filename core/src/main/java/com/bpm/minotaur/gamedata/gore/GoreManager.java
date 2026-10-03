package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.bpm.minotaur.gamedata.ChunkData;
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

    // --- High-Performance Pool Budgets (at GoreLevel.NORMAL; see GoreLevel#budget) ---
    // Blood stays until recycled, so these decide how painted a room can get.
    public static final int MAX_ACTIVE_PARTICLES = 400;
    public static final int MAX_ACTIVE_GIBS = 150;
    public static final int MAX_ACTIVE_SURFACE_DECALS = 600;
    public static final int MAX_ACTIVE_WALL_DECALS = 400;

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

    // Scratch state reused by every spawn: a burst of 60 drops must not
    // allocate 120 objects in the frame combat is busiest (guide, "Zero
    // Allocations"). Pool.obtain() + init() copy out of these.
    private final Vector3 tmpVel = new Vector3();
    private final Vector3 tmpDir = new Vector3();
    private final Vector3 tmpPos = new Vector3();
    private final Color tmpColor = new Color();
    private final GridPoint2 tmpChunkId = new GridPoint2();
    private static final Color BONE_TINT = new Color(0.90f, 0.88f, 0.80f, 1.0f);

    // Where the player stands, in world coordinates (x, maze-y). A full pool
    // recycles whatever lies farthest from here, so the fight in front of the
    // player is never the blood that disappears.
    private float viewerX, viewerZ;
    private boolean hasViewer;

    public GoreManager() {
    }

    public void setViewer(float worldX, float worldZ) {
        this.viewerX = worldX;
        this.viewerZ = worldZ;
        this.hasViewer = true;
    }

    private static int particleBudget() {
        return GoreLevel.current().budget(MAX_ACTIVE_PARTICLES);
    }

    private static int gibBudget() {
        return GoreLevel.current().budget(MAX_ACTIVE_GIBS);
    }

    private static int surfaceBudget() {
        return GoreLevel.current().budget(MAX_ACTIVE_SURFACE_DECALS);
    }

    private static int wallBudget() {
        return GoreLevel.current().budget(MAX_ACTIVE_WALL_DECALS);
    }

    private float viewerDist2(float x, float z) {
        float dx = x - viewerX;
        float dz = z - viewerZ;
        return dx * dx + dz * dz;
    }

    private void makeRoomForGib() {
        while (activeGibs.size >= gibBudget() && activeGibs.size > 0) {
            int victim = 0;
            if (hasViewer) {
                float best = -1f;
                for (int i = 0; i < activeGibs.size; i++) {
                    Gib g = activeGibs.get(i);
                    float d = viewerDist2(g.position.x, g.position.z);
                    if (d > best) {
                        best = d;
                        victim = i;
                    }
                }
            }
            gibPool.free(activeGibs.removeIndex(victim));
        }
    }

    private void makeRoomForSurfaceDecal() {
        while (activeSurfaceDecals.size >= surfaceBudget() && activeSurfaceDecals.size > 0) {
            int victim = 0;
            if (hasViewer) {
                float best = -1f;
                for (int i = 0; i < activeSurfaceDecals.size; i++) {
                    SurfaceDecal d = activeSurfaceDecals.get(i);
                    float dist = viewerDist2(d.position.x, d.position.z);
                    if (dist > best) {
                        best = dist;
                        victim = i;
                    }
                }
            }
            surfaceDecalPool.free(activeSurfaceDecals.removeIndex(victim));
        }
    }

    private void makeRoomForWallDecal() {
        while (activeWallDecals.size >= wallBudget() && activeWallDecals.size > 0) {
            int victim = 0;
            if (hasViewer) {
                float best = -1f;
                for (int i = 0; i < activeWallDecals.size; i++) {
                    WallDecal w = activeWallDecals.get(i);
                    float dist = viewerDist2(w.gridX + 0.5f, w.gridY + 0.5f);
                    if (dist > best) {
                        best = dist;
                        victim = i;
                    }
                }
            }
            removeWallDecalAt(victim);
        }
    }

    private void removeWallDecalAt(int index) {
        WallDecal old = activeWallDecals.removeIndex(index);
        int oldKey = (old.gridX * 1000 + old.gridY) * 2 + old.side;
        Array<WallDecal> list = wallDecalsByKey.get(oldKey);
        if (list != null) {
            list.removeValue(old, true);
            if (list.size == 0) wallDecalsByKey.remove(oldKey);
        }
        wallDecalPool.free(old);
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

    private static boolean goreOff() {
        return !GoreLevel.current().enabled();
    }

    public void spawnBloodSpray(Vector3 origin, Vector3 direction, int intensity) {
        spawnBloodSpray(origin, direction, intensity, GoreProfile.FLESH);
    }

    public void spawnBloodSpray(Vector3 origin, Vector3 direction, int intensity, GoreProfile profile) {
        if (profile == null) profile = GoreProfile.FLESH;

        // Incorporeal creatures emit no blood or particles
        if (profile == GoreProfile.INCORPOREAL) return;
        if (goreOff()) return;

        int count = GoreLevel.current().count(Math.max(6, intensity * 12));

        // Budget check
        int availableSlots = particleBudget() - activeParticles.size;
        count = Math.min(count, availableSlots);
        if (count <= 0) return;

        Color baseColor = (profile.primaryColor != null) ? profile.primaryColor : UNIFIED_BLOOD_COLOR;

        for (int i = 0; i < count; i++) {
            BloodParticle p = particlePool.obtain();
            float spreadX = MathUtils.random(-0.4f, 0.4f);
            float spreadY = MathUtils.random(-0.1f, 0.6f);
            float spreadZ = MathUtils.random(-0.4f, 0.4f);
            float speed = MathUtils.random(3.0f, 8.5f);

            Vector3 vel = tmpVel.set(direction).scl(0.65f).add(spreadX, spreadY, spreadZ).nor().scl(speed);

            Color particleColor = tmpColor.set(baseColor);
            if (profile == GoreProfile.FLESH || profile == GoreProfile.SKELETAL) {
                float roll = MathUtils.random();
                if (roll < 0.25f) {
                    // Darker coagulated drop
                    particleColor.r *= 0.6f;
                    particleColor.g *= 0.5f;
                    particleColor.b *= 0.5f;
                } else if (roll < 0.65f) {
                    // Arterial / vibrant spray
                    particleColor.r = Math.min(1.0f, particleColor.r * 1.25f);
                    particleColor.g *= 0.8f;
                    particleColor.b *= 0.8f;
                }
            }

            float size = (profile == GoreProfile.SKELETAL)
                    ? MathUtils.random(0.03f, 0.06f)
                    : MathUtils.random(0.035f, 0.07f);
            float life = MathUtils.random(0.8f, 2.5f);

            TextureRegion tex = (dropTextures.size > 0) ? dropTextures.random() : null;

            p.init(origin, vel, particleColor, life, size, tex);
            activeParticles.add(p);
        }
    }

    /**
     * Erupts an immediate burst of blood droplets directly from a localized wound site on a creature,
     * spraying outward towards the attacker and falling to the floor.
     *
     * @param woundSite 3D world coordinates of the wound decal on the creature's surface
     * @param surfaceNormal Direction outward from the wound surface (typically towards attacker)
     * @param damage Impact force / severity of the blow
     * @param profile Creature's gore physiology
     */
    public void spawnWoundBloodBurst(Vector3 woundSite, Vector3 surfaceNormal, int damage, GoreProfile profile) {
        if (woundSite == null) return;
        if (profile == null) profile = GoreProfile.FLESH;
        if (!profile.hasBlood) return;
        if (profile == GoreProfile.INCORPOREAL) return;
        if (goreOff()) return;

        int count = GoreLevel.current().count(MathUtils.clamp(8 + damage * 2 / 3, 8, 24));
        int availableSlots = particleBudget() - activeParticles.size;
        count = Math.min(count, availableSlots);
        if (count <= 0) return;

        Color baseColor = (profile.primaryColor != null) ? profile.primaryColor : UNIFIED_BLOOD_COLOR;
        Vector3 outDir = (surfaceNormal != null && !surfaceNormal.isZero()) ? tmpDir.set(surfaceNormal).nor() : tmpDir.set(0, 0.3f, 1).nor();

        for (int i = 0; i < count; i++) {
            BloodParticle p = particlePool.obtain();
            // Conical splash outward from wound site with upward loft
            float spreadX = MathUtils.random(-0.5f, 0.5f);
            float spreadY = MathUtils.random(0.05f, 0.65f);
            float spreadZ = MathUtils.random(-0.5f, 0.5f);
            float speed = MathUtils.random(2.5f, 6.5f);

            Vector3 vel = tmpVel.set(outDir).scl(0.7f).add(spreadX, spreadY, spreadZ).nor().scl(speed);

            Color particleColor = tmpColor.set(baseColor);
            if (profile == GoreProfile.FLESH || profile == GoreProfile.SKELETAL) {
                float roll = MathUtils.random();
                if (roll < 0.35f) {
                    particleColor.r *= 0.65f;
                    particleColor.g *= 0.55f;
                    particleColor.b *= 0.55f;
                } else if (roll < 0.70f) {
                    particleColor.r = Math.min(1.0f, particleColor.r * 1.2f);
                    particleColor.g *= 0.85f;
                    particleColor.b *= 0.85f;
                }
            }

            float size = (profile == GoreProfile.SKELETAL)
                    ? MathUtils.random(0.03f, 0.065f)
                    : MathUtils.random(0.04f, 0.08f);
            float life = MathUtils.random(0.6f, 1.8f);

            TextureRegion tex = (dropTextures.size > 0) ? dropTextures.random() : null;

            p.init(woundSite, vel, particleColor, life, size, tex);
            activeParticles.add(p);
        }
    }

    public void spawnGibExplosion(Vector3 origin) {
        spawnGibExplosion(origin, Vector3.Y, 1, GoreProfile.FLESH);
    }

    public void spawnGibExplosion(Vector3 origin, Vector3 exitVector, int overkillTier, GoreProfile profile) {
        if (profile == null) profile = GoreProfile.FLESH;
        if (!profile.hasGibs) return;
        if (goreOff()) return;

        int count = GoreLevel.current().count((overkillTier >= 2) ? MathUtils.random(7, 12) : MathUtils.random(3, 6));

        Color tint = (profile == GoreProfile.SKELETAL)
                ? BONE_TINT // Ivory bone tint
                : Color.WHITE;

        Vector3 exitNorm = (exitVector != null && exitVector.len2() > 0.001f)
                ? tmpDir.set(exitVector).nor()
                : Vector3.Y;

        for (int i = 0; i < count; i++) {
            makeRoomForGib();

            Gib g = gibPool.obtain();
            TextureRegion tex = (gibTextures.size > 0) ? gibTextures.random() : null;

            // Explosion velocity: radial scatter outward with bias along exit vector
            float angle = MathUtils.random(0, 360) * MathUtils.degreesToRadians;
            float radialSpeed = MathUtils.random(2.0f, 6.5f);
            float up = MathUtils.random(2.5f, 7.5f);

            Vector3 vel = tmpVel.set(
                    MathUtils.cos(angle) * radialSpeed + exitNorm.x * 2.0f,
                    up,
                    MathUtils.sin(angle) * radialSpeed + exitNorm.z * 2.0f
            );

            g.init(origin, vel, tex, tint);
            activeGibs.add(g);
        }

        if (profile.hasBlood && profile.createsFloorStains) {
            Color stainCol = (profile.primaryColor != null) ? profile.primaryColor : UNIFIED_BLOOD_COLOR;
            spawnSurfaceDecal(origin, stainCol, MathUtils.random(0.20f, 0.35f));
            for (int i = 0; i < Math.min(2, overkillTier); i++) {
                float ox = MathUtils.random(-0.35f, 0.35f);
                float oz = MathUtils.random(-0.35f, 0.35f);
                spawnSurfaceDecal(tmpPos.set(origin.x + ox, origin.y, origin.z + oz), stainCol, MathUtils.random(0.15f, 0.25f));
            }
        }
    }

    public Gib spawnSeveredLimbGib(Vector3 origin, Vector3 velocity, Texture tex, float[] polyVertices, float[] polyUVs, float[] seamVertices, GoreProfile profile) {
        if (goreOff()) return null;
        makeRoomForGib();

        Gib g = gibPool.obtain();
        Color tint = (profile != null && profile.primaryColor != null) ? profile.primaryColor : Color.WHITE;
        g.initSeveredLimb(origin, velocity, tex, polyVertices, polyUVs, seamVertices, tint);
        activeGibs.add(g);
        return g;
    }

    public void spawnArterialFountain(Vector3 origin, Vector3 dir, float duration, GoreProfile profile) {
        if (profile == null) profile = GoreProfile.FLESH;
        if (goreOff()) return;
        if (!profile.hasBlood) {
            spawnBloodSpray(origin, dir, 8, profile);
            return;
        }

        int count = Math.min(GoreLevel.current().count(60), particleBudget() - activeParticles.size);
        if (count <= 0) return;

        Color fountainColor = tmpColor.set(profile.primaryColor);
        fountainColor.r = Math.min(1.0f, fountainColor.r * 1.3f);

        Vector3 fountainDir = (dir != null && dir.len2() > 0.01f) ? tmpDir.set(dir).nor() : tmpDir.set(0, 1, 0);

        for (int i = 0; i < count; i++) {
            BloodParticle p = particlePool.obtain();
            float spreadX = MathUtils.random(-0.25f, 0.25f);
            float spreadY = MathUtils.random(0.4f, 1.2f);
            float spreadZ = MathUtils.random(-0.25f, 0.25f);
            float speed = MathUtils.random(5.0f, 11.0f);

            Vector3 vel = tmpVel.set(fountainDir.x * 0.4f + spreadX, spreadY, fountainDir.z * 0.4f + spreadZ).nor().scl(speed);
            float size = MathUtils.random(0.04f, 0.08f);
            float life = MathUtils.random(1.0f, 2.5f);
            TextureRegion tex = (dropTextures.size > 0) ? dropTextures.random() : null;

            p.init(origin, vel, fountainColor, life, size, tex);
            activeParticles.add(p);
        }
    }

    public void spawnCrushShatter(Vector3 origin, GoreProfile profile) {
        if (profile == null) profile = GoreProfile.FLESH;
        spawnBloodSpray(origin, Vector3.Y, 10, profile);
        spawnGibExplosion(origin, Vector3.Y, 2, profile);
    }

    public void spawnRetroGibs(Vector3 origin, String[] spriteData, Color color) {
        if (spriteData == null || spriteData.length == 0) return;
        if (goreOff()) return;

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

        makeRoomForGib();

        Gib g = gibPool.obtain();
        Vector3 vel = tmpVel.set(
                MathUtils.random(-1f, 1f),
                MathUtils.random(3f, 6f),
                MathUtils.random(-1f, 1f)
        ).nor().scl(MathUtils.random(3f, 7f));

        g.init(origin, vel, chunk, color);
        activeGibs.add(g);
    }

    /**
     * Same texture-selection weighting used internally by {@link #spawnWallDecal},
     * exposed so other systems (e.g. the first-person weapon overlay) can stamp a
     * blood decal that looks like it belongs to the same family as world decals.
     */
    public TextureRegion getRandomBloodTexture() {
        if (spatterTexture != null && MathUtils.randomBoolean(0.35f)) {
            return spatterTexture;
        } else if (smearTextures.size > 0) {
            return smearTextures.random();
        } else if (dropTextures.size > 0) {
            return dropTextures.random();
        }
        return null;
    }

    // --- Decal Spawning ---

    /**
     * The maze the gore simulation is currently running against.
     *
     * <p>Held here rather than passed to each spawn call because decals are
     * created from ten different places -- kill stains, bleed trails, droplets
     * landing -- and guarding only the ones that happened to have a maze in
     * scope left the commonest source of doorway blood unguarded.
     */
    private Maze decalMaze;

    /**
     * False only for tiles that cannot hold a floor decal: doorways and gate
     * thresholds, which have no floor quad and no framing walls.
     *
     * <p>Deliberately fails open. Decal positions are z-up maze coordinates, but
     * callers differ over whether they are chunk-local or chunk-absolute, so an
     * out-of-range tile means "I cannot tell" rather than "reject". Guessing the
     * other way would silently delete blood from every chunk but the origin.
     */
    public static boolean canHoldSurfaceDecal(com.bpm.minotaur.gamedata.Maze maze, Vector3 pos) {
        if (maze == null || pos == null) return true;

        int tileX = com.badlogic.gdx.math.MathUtils.floor(pos.x);
        int tileY = com.badlogic.gdx.math.MathUtils.floor(pos.z);
        if (tileX < 0 || tileY < 0 || tileX >= maze.getWidth() || tileY >= maze.getHeight()) {
            return true;
        }

        if (maze.getGateAt(tileX, tileY) != null) return false;
        return !(maze.getGameObjectAt(tileX, tileY) instanceof com.bpm.minotaur.gamedata.Door);
    }

    public void spawnSurfaceDecal(Vector3 pos, Color color, float targetRadius) {
        if (goreOff()) return;
        if (pos != null && growNearbyPuddle(pos, color, targetRadius)) return;
        placeSurfaceDecal(pos, color, targetRadius, true);
    }

    /** How near a landing drop must be to an existing puddle to feed it, in tiles. */
    public static final float PUDDLE_MERGE_RADIUS = 0.25f;
    /** The largest a puddle grows from merged drops. */
    public static final float MAX_PUDDLE_RADIUS = 0.6f;
    /** Share of a drop's area a puddle gains; most of the drop splashes, not pools. */
    private static final float MERGE_AREA_SHARE = 0.35f;

    /**
     * Feeds the nearest blood puddle within {@link #PUDDLE_MERGE_RADIUS}
     * instead of laying a new decal (guide step 4). Without this a spray
     * reads as scattered dots and fills the pool long before a floor looks
     * soaked.
     */
    private boolean growNearbyPuddle(Vector3 pos, Color color, float radius) {
        SurfaceDecal nearest = null;
        float bestD2 = PUDDLE_MERGE_RADIUS * PUDDLE_MERGE_RADIUS;
        for (int i = 0; i < activeSurfaceDecals.size; i++) {
            SurfaceDecal d = activeSurfaceDecals.get(i);
            if (!d.isBlood) continue;
            float dx = d.position.x - pos.x;
            float dz = d.position.z - pos.z;
            float d2 = dx * dx + dz * dz;
            if (d2 <= bestD2) {
                bestD2 = d2;
                nearest = d;
            }
        }
        if (nearest == null) return false;

        float grown = (float) Math.sqrt(nearest.targetSize * nearest.targetSize
                + MERGE_AREA_SHARE * radius * radius);
        nearest.feed(Math.min(MAX_PUDDLE_RADIUS, grown));
        return true;
    }

    /** A floor mark regardless of the gore level: scorch and frost are not blood. */
    private void placeSurfaceDecal(Vector3 pos, Color color, float targetRadius, boolean blood) {
        // A doorway has no floor quad and no framing walls, so blood there reads
        // as hanging in the opening rather than lying on the ground.
        if (!canHoldSurfaceDecal(decalMaze, pos)) return;

        makeRoomForSurfaceDecal();

        SurfaceDecal d = surfaceDecalPool.obtain();
        TextureRegion tex = (smearTextures.size > 0) ? smearTextures.random() : spatterTexture;
        d.init(pos, color, targetRadius, tex);
        d.isBlood = blood;
        activeSurfaceDecals.add(d);
    }

    /**
     * Spawns elemental ground marks (scorch, frost, acid, holy rune) at the specified position.
     */
    public void spawnElementalScorch(Vector3 pos, Color color, float radius) {
        placeSurfaceDecal(pos, color, radius, false);
        for (int i = 0; i < 2; i++) {
            float ox = MathUtils.random(-0.3f, 0.3f);
            float oz = MathUtils.random(-0.3f, 0.3f);
            Vector3 splatPos = tmpPos.set(pos.x + ox, pos.y, pos.z + oz);
            placeSurfaceDecal(splatPos, color, radius * MathUtils.random(0.45f, 0.75f), false);
        }
    }

    public void spawnWallDecal(int x, int y, Direction dir, float wallX, float height, float radius, Color color) {
        if (goreOff()) return;
        makeRoomForWallDecal();

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
        if (GoreLevel.current().drips() && wd.radius >= WallDecal.DRIP_MIN_RADIUS && MathUtils.randomBoolean(0.6f)) {
            boolean toFloor = MathUtils.randomBoolean(0.35f);
            float gap = wd.floorGap();
            wd.startDrip(toFloor ? gap : gap * MathUtils.random(0.25f, 0.8f),
                    MathUtils.random(0.06f, 0.16f), toFloor);
        }
        wallDecalsByKey.get(key).add(wd);
        activeWallDecals.add(wd);
    }

    // --- Update Loop ---

    public void update(float delta, Maze currentMaze) {
        update(delta, currentMaze, null);
    }

    public void update(float delta, Maze currentMaze, WorldManager worldManager) {
        this.decalMaze = currentMaze;
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
                spawnSurfaceDecal(tmpPos.set(prevX, 0.02f, prevZ), p.color, MathUtils.random(0.14f, 0.22f));
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
        boolean persistent = GoreLevel.current().persistent();
        for (int i = activeSurfaceDecals.size - 1; i >= 0; i--) {
            SurfaceDecal d = activeSurfaceDecals.get(i);
            d.update(delta, persistent);
            if (d.lifeTimer <= 0) {
                activeSurfaceDecals.removeIndex(i);
                surfaceDecalPool.free(d);
            }
        }

        // 3. Update Wall Decals
        for (int i = activeWallDecals.size - 1; i >= 0; i--) {
            WallDecal wd = activeWallDecals.get(i);
            wd.update(delta, persistent);
            if (wd.takeDripLanding()) {
                pourDripOntoFloor(wd);
            }
            if (wd.lifeTimer <= 0) {
                removeWallDecalAt(i);
            }
        }

        // 4. Update Gibs
        for (int i = activeGibs.size - 1; i >= 0; i--) {
            Gib g = activeGibs.get(i);
            g.update(delta, persistent);
            if (g.lifeTimer <= 0) {
                activeGibs.removeIndex(i);
                gibPool.free(g);
            }
        }
    }

    /** A drip that reached the floor pools at the foot of its wall. */
    private void pourDripOntoFloor(WallDecal wd) {
        float inset = 0.06f;
        float x = wd.gridX + 0.5f;
        float z = wd.gridY + 0.5f;
        if (wd.dir != null) {
            switch (wd.dir) {
                case EAST: x = wd.gridX + 1f - inset; z = wd.gridY + wd.wallX; break;
                case WEST: x = wd.gridX + inset; z = wd.gridY + wd.wallX; break;
                case NORTH: x = wd.gridX + wd.wallX; z = wd.gridY + 1f - inset; break;
                case SOUTH: x = wd.gridX + wd.wallX; z = wd.gridY + inset; break;
                default: break;
            }
        }
        spawnSurfaceDecal(tmpPos.set(x, SurfaceDecal.FLOOR_Y, z), wd.color, MathUtils.random(0.10f, 0.16f));
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

        return worldManager.getLoadedChunk(tmpChunkId.set(chunkX, chunkY));
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

    // --- Chunk Persistence Serialization ---

    public void exportChunkGore(GridPoint2 chunkId, ChunkData chunkData) {
        if (chunkId == null || chunkData == null) return;
        chunkData.surfaceDecals.clear();
        chunkData.wallDecals.clear();
        chunkData.gibs.clear();

        for (int i = 0; i < activeSurfaceDecals.size; i++) {
            SurfaceDecal d = activeSurfaceDecals.get(i);
            // z is the maze's second axis; y is height off the floor, and
            // reading it put every stain in chunk row 0.
            int cx = (int) Math.floor(d.position.x / 36f);
            int cy = (int) Math.floor(d.position.z / 36f);
            if (cx == chunkId.x && cy == chunkId.y) {
                ChunkData.DecalData data = new ChunkData.DecalData();
                data.x = d.position.x;
                data.y = d.position.y;
                data.z = d.position.z;
                data.size = d.size;
                data.r = d.color.r;
                data.g = d.color.g;
                data.b = d.color.b;
                data.a = d.color.a;
                data.lifeTimer = d.lifeTimer;
                chunkData.surfaceDecals.add(data);
            }
        }

        for (int i = 0; i < activeWallDecals.size; i++) {
            WallDecal d = activeWallDecals.get(i);
            int cx = (int) Math.floor((float) d.gridX / 36f);
            int cy = (int) Math.floor((float) d.gridY / 36f);
            if (cx == chunkId.x && cy == chunkId.y) {
                ChunkData.WallDecalData data = new ChunkData.WallDecalData();
                data.gridX = d.gridX;
                data.gridY = d.gridY;
                data.dir = d.dir != null ? d.dir.name() : "NORTH";
                data.wallX = d.wallX;
                data.height = d.height;
                data.radius = d.radius;
                data.r = d.color.r;
                data.g = d.color.g;
                data.b = d.color.b;
                data.a = d.color.a;
                data.lifeTimer = d.lifeTimer;
                data.dripLength = d.dripLength;
                chunkData.wallDecals.add(data);
            }
        }

        for (int i = 0; i < activeGibs.size; i++) {
            Gib g = activeGibs.get(i);
            if (g.onGround) {
                int cx = (int) Math.floor(g.position.x / 36f);
                int cy = (int) Math.floor(g.position.z / 36f);
                if (cx == chunkId.x && cy == chunkId.y) {
                    ChunkData.GibData data = new ChunkData.GibData();
                    data.x = g.position.x;
                    data.y = g.position.y;
                    data.z = g.position.z;
                    data.rotation = g.rotation;
                    data.r = g.color.r;
                    data.g = g.color.g;
                    data.b = g.color.b;
                    data.a = g.color.a;
                    data.lifeTimer = g.lifeTimer;
                    chunkData.gibs.add(data);
                }
            }
        }
    }

    public void importChunkGore(GridPoint2 chunkId, ChunkData chunkData) {
        if (chunkId == null || chunkData == null) return;

        // The manager outlives chunk loads, so blood from an earlier visit may
        // still be live; the save is the authority for its chunk.
        clearChunkGore(chunkId);

        if (chunkData.surfaceDecals != null) {
            for (ChunkData.DecalData data : chunkData.surfaceDecals) {
                if (activeSurfaceDecals.size >= surfaceBudget()) break;
                SurfaceDecal decal = surfaceDecalPool.obtain();
                decal.position.set(data.x, data.y, data.z);
                decal.restoreColor(data.r, data.g, data.b, data.a);
                decal.size = data.size;
                decal.initialSize = data.size;
                decal.targetSize = data.size;
                decal.expandTimer = SurfaceDecal.EXPAND_DURATION;
                decal.maxLife = SurfaceDecal.MAX_DECAL_LIFE;
                decal.lifeTimer = data.lifeTimer > 0 ? data.lifeTimer : SurfaceDecal.MAX_DECAL_LIFE;
                decal.textureRegion = (dropTextures.size > 0) ? dropTextures.first() : null;
                activeSurfaceDecals.add(decal);
            }
        }

        if (chunkData.wallDecals != null) {
            for (ChunkData.WallDecalData data : chunkData.wallDecals) {
                if (activeWallDecals.size >= wallBudget()) break;
                WallDecal decal = wallDecalPool.obtain();
                Direction dir = Direction.NORTH;
                try {
                    if (data.dir != null) dir = Direction.valueOf(data.dir);
                } catch (Exception ignored) {}

                Color c = new Color(data.r, data.g, data.b, data.a);
                decal.init(data.gridX, data.gridY, dir, data.wallX, data.height, data.radius, c,
                        (smearTextures.size > 0) ? smearTextures.first() : null);
                decal.restoreColor(data.r, data.g, data.b, data.a);
                // A saved drip has finished running; it comes back as it was left.
                decal.dripLength = data.dripLength;
                decal.lifeTimer = data.lifeTimer > 0 ? data.lifeTimer : WallDecal.MAX_WALL_DECAL_LIFE;
                activeWallDecals.add(decal);

                int key = (data.gridX * 1000 + data.gridY) * 2 + decal.side;
                Array<WallDecal> list = wallDecalsByKey.computeIfAbsent(key, k -> new Array<>());
                list.add(decal);
            }
        }

        if (chunkData.gibs != null) {
            for (ChunkData.GibData data : chunkData.gibs) {
                if (activeGibs.size >= gibBudget()) break;
                Gib gib = gibPool.obtain();
                gib.position.set(data.x, data.y, data.z);
                gib.velocity.setZero();
                gib.color.set(data.r, data.g, data.b, data.a);
                gib.rotation = data.rotation;
                gib.rotationalVelocity = 0f;
                gib.onGround = true;
                gib.lifeTimer = data.lifeTimer > 0 ? data.lifeTimer : Gib.MAX_GIB_LIFE;
                gib.textureRegion = (gibTextures.size > 0) ? gibTextures.first() : null;
                activeGibs.add(gib);
            }
        }
    }

    private static boolean inChunk(float worldX, float worldZ, GridPoint2 chunkId) {
        return (int) Math.floor(worldX / 36f) == chunkId.x && (int) Math.floor(worldZ / 36f) == chunkId.y;
    }

    private void clearChunkGore(GridPoint2 chunkId) {
        for (int i = activeSurfaceDecals.size - 1; i >= 0; i--) {
            SurfaceDecal d = activeSurfaceDecals.get(i);
            if (inChunk(d.position.x, d.position.z, chunkId)) {
                surfaceDecalPool.free(activeSurfaceDecals.removeIndex(i));
            }
        }
        for (int i = activeWallDecals.size - 1; i >= 0; i--) {
            WallDecal w = activeWallDecals.get(i);
            if (inChunk(w.gridX, w.gridY, chunkId)) {
                removeWallDecalAt(i);
            }
        }
        for (int i = activeGibs.size - 1; i >= 0; i--) {
            Gib g = activeGibs.get(i);
            if (g.onGround && inChunk(g.position.x, g.position.z, chunkId)) {
                gibPool.free(activeGibs.removeIndex(i));
            }
        }
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
