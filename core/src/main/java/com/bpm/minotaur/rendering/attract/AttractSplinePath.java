package com.bpm.minotaur.rendering.attract;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.generation.Biome;

/**
 * Authored closed-loop 3D Catmull-Rom spline controlling the cinematic camera flyover.
 * Paces through Lakelands, Forest, Desert, Mountain crags, Castle battlements,
 * and orbits the High Spire summit at apex altitude before looping seamlessly.
 */
public class AttractSplinePath {

    public static final float LOOP_DURATION = 90.0f; // 90-second grand tour

    // World center is around (180, -180). Castle gate entrance is at (180, 1.5, -165).
    public static final Vector3 CASTLE_ENTRANCE = new Vector3(180f, 1.5f, -165f);

    private final CatmullRomSpline<Vector3> positionSpline;
    private final CatmullRomSpline<Vector3> lookAtSpline;

    // Atmospheric Biome Colors
    private static final Color LAKELANDS_FOG = new Color(0.35f, 0.45f, 0.65f, 1.0f);
    private static final Color FOREST_FOG = new Color(0.08f, 0.16f, 0.10f, 1.0f);
    private static final Color DESERT_FOG = new Color(0.65f, 0.55f, 0.38f, 1.0f);
    private static final Color MOUNTAINS_FOG = new Color(0.28f, 0.30f, 0.38f, 1.0f);
    private static final Color CASTLE_FOG = new Color(0.18f, 0.16f, 0.24f, 1.0f);

    public AttractSplinePath() {
        // Control Points defining the authored 90-second flight path across the 100-chunk world
        Vector3[] posKnots = new Vector3[]{
                new Vector3(275f, 1.8f, -165f), // 0: Lakelands low water skim
                new Vector3(255f, 2.2f, -210f), // 1: Lakelands sunken ruins
                new Vector3(225f, 2.6f, -255f), // 2: Forest margin
                new Vector3(180f, 1.9f, -280f), // 3: Deep ancient forest canopy
                new Vector3(125f, 3.5f, -265f), // 4: Forest northwest clearings
                new Vector3(95f,  7.2f, -220f), // 5: West mountain ridge ascent
                new Vector3(85f,  8.5f, -160f), // 6: High mountain pass
                new Vector3(110f, 4.0f, -105f), // 7: Canyon descent into desert
                new Vector3(155f, 2.8f, -80f),  // 8: Desert sunlit dunes
                new Vector3(185f, 2.4f, -95f),  // 9: Approaching castle south perimeter
                new Vector3(175f, 7.8f, -145f), // 10: Castle outer ramparts & courtyard
                new Vector3(195f, 19.5f, -172f),// 11: High spire orbit (apex altitude)
                new Vector3(165f, 20.0f, -188f),// 12: Apex panorama horizon view
                new Vector3(210f, 8.5f, -175f)  // 13: Diving descent towards lakelands
        };

        Vector3[] lookKnots = new Vector3[]{
                new Vector3(250f, 1.5f, -185f), // 0: Looking across misty lake
                new Vector3(230f, 1.8f, -235f), // 1: Looking toward ancient forest line
                new Vector3(195f, 2.2f, -270f), // 2: Looking between moss-covered trunks
                new Vector3(145f, 2.5f, -275f), // 3: Looking along canopy corridor
                new Vector3(105f, 5.0f, -240f), // 4: Looking up toward craggy cliffs
                new Vector3(88f,  6.5f, -180f), // 5: Looking through mountain gorge
                new Vector3(98f,  4.5f, -125f), // 6: Looking down into sunlit desert
                new Vector3(140f, 2.5f, -90f),  // 7: Looking along sandstone canyon
                new Vector3(175f, 4.0f, -120f), // 8: Looking toward distant castle silhouette
                new Vector3(180f, 9.0f, -160f), // 9: Looking at castle fortress gate
                new Vector3(180f, 18.0f, -180f),// 10: Looking up at soaring high spire
                new Vector3(180f, 22.0f, -180f),// 11: Locked onto glowing spire beacon
                new Vector3(250f, 12.0f, -165f),// 12: Sweeping panorama over kingdom
                new Vector3(260f, 2.5f, -168f)  // 13: Gazing down at incoming lake mist
        };

        this.positionSpline = new CatmullRomSpline<>(posKnots, true);
        this.lookAtSpline = new CatmullRomSpline<>(lookKnots, true);
    }

    public float getLoopDuration() {
        return LOOP_DURATION;
    }

    /**
     * Evaluates camera position at the given flight time in seconds.
     */
    public Vector3 getPosition(float time, Vector3 out) {
        float normalized = (time % LOOP_DURATION) / LOOP_DURATION;
        if (normalized < 0) normalized += 1.0f;
        positionSpline.valueAt(out, normalized);
        return out;
    }

    /**
     * Evaluates look-at target at the given flight time in seconds.
     */
    public Vector3 getLookAt(float time, Vector3 out) {
        float normalized = (time % LOOP_DURATION) / LOOP_DURATION;
        if (normalized < 0) normalized += 1.0f;
        lookAtSpline.valueAt(out, normalized);
        return out;
    }

    /**
     * Computes aerodynamic banking roll angle in degrees based on turn curvature.
     */
    public float getRollDegrees(float time) {
        float dt = 0.4f;
        Vector3 pPrev = new Vector3();
        Vector3 pCurr = new Vector3();
        Vector3 pNext = new Vector3();

        getPosition(time - dt, pPrev);
        getPosition(time, pCurr);
        getPosition(time + dt, pNext);

        Vector3 d1 = new Vector3(pCurr).sub(pPrev).nor();
        Vector3 d2 = new Vector3(pNext).sub(pCurr).nor();

        // 2D horizontal cross product indicates turn direction and sharpness
        float turn = (d1.x * d2.z - d1.z * d2.x);
        // Bank into curve, clamped between -12 and +12 degrees
        return MathUtils.clamp(turn * 280.0f, -12.0f, 12.0f);
    }

    /**
     * Identifies the primary biome corresponding to the flight time.
     */
    public Biome getBiomeAt(float time) {
        float t = time % LOOP_DURATION;
        if (t < 0) t += LOOP_DURATION;

        if (t < 18.0f) {
            return Biome.LAKELANDS;
        } else if (t < 42.0f) {
            return Biome.FOREST;
        } else if (t < 52.0f) {
            return Biome.MOUNTAINS;
        } else if (t < 66.0f) {
            return Biome.DESERT;
        } else if (t < 86.0f) {
            return Biome.MAZE; // Castle Tarmin citadel & spire
        } else {
            return Biome.LAKELANDS;
        }
    }

    /**
     * Computes interpolated atmospheric fog color and parameters for the current position.
     */
    public void getAtmosphere(float time, Color outFogColor, float[] outFogDist) {
        Biome biome = getBiomeAt(time);
        switch (biome) {
            case LAKELANDS:
                outFogColor.set(LAKELANDS_FOG);
                outFogDist[0] = 65f;
                break;
            case FOREST:
                outFogColor.set(FOREST_FOG);
                outFogDist[0] = 50f;
                break;
            case DESERT:
                outFogColor.set(DESERT_FOG);
                outFogDist[0] = 85f;
                break;
            case MOUNTAINS:
                outFogColor.set(MOUNTAINS_FOG);
                outFogDist[0] = 95f;
                break;
            case MAZE:
            default:
                outFogColor.set(CASTLE_FOG);
                outFogDist[0] = 75f;
                break;
        }
    }

    /**
     * Evaluates camera position during the 0.8s expedition dive transition.
     * Smoothly accelerates from the current flyover position toward the fortress gate entrance.
     */
    public void evaluateDive(Vector3 startPos, float progress01, Vector3 out) {
        float p = MathUtils.clamp(progress01, 0f, 1f);
        // Ease-in (acceleration) curve for dramatic dive
        float ease = p * p;
        out.set(startPos).lerp(CASTLE_ENTRANCE, ease);
    }
}
