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

    // World center is around (180, -180). Castle gate entrance is at (180, 1.5, -147).
    public static final Vector3 CASTLE_ENTRANCE = new Vector3(180f, 1.5f, -147f);

    private final CatmullRomSpline<Vector3> positionSpline;
    private final CatmullRomSpline<Vector3> lookAtSpline;

    // Atmospheric Biome Colors
    private static final Color LAKELANDS_FOG = new Color(0.35f, 0.45f, 0.65f, 1.0f);
    private static final Color FOREST_FOG = new Color(0.08f, 0.16f, 0.10f, 1.0f);
    private static final Color DESERT_FOG = new Color(0.65f, 0.55f, 0.38f, 1.0f);
    private static final Color MOUNTAINS_FOG = new Color(0.28f, 0.30f, 0.38f, 1.0f);
    private static final Color CASTLE_FOG = new Color(0.18f, 0.16f, 0.24f, 1.0f);

    public AttractSplinePath() {
        // Control Points defining the authored 90-second flight path skimming and weaving through the maze corridors
        Vector3[] posKnots = new Vector3[]{
                new Vector3(275f, 1.6f, -165f), // 0: Lakelands low water canal skim
                new Vector3(245f, 1.8f, -220f), // 1: Lakelands sunken ruin corridors
                new Vector3(220f, 2.0f, -260f), // 2: Forest margin mossy cliff corridor
                new Vector3(175f, 1.7f, -285f), // 3: Deep ancient forest canopy pathway
                new Vector3(125f, 2.2f, -260f), // 4: Forest northwest clearings & ruins
                new Vector3(95f,  3.8f, -215f), // 5: West mountain gorge ascent
                new Vector3(88f,  4.2f, -155f), // 6: High mountain gorge pass
                new Vector3(115f, 2.4f, -100f), // 7: Canyon descent into desert labyrinth
                new Vector3(155f, 1.8f, -85f),  // 8: Desert sunlit sandstone canyon corridor
                new Vector3(180f, 2.2f, -120f), // 9: Southern grand avenue approach to Castle Tarmin
                new Vector3(180f, 1.6f, -152f), // 10: Skimming over castle moat through fortress gate
                new Vector3(180f, 1.5f, -178f), // 11: Grand castle royal crossroad & pillared hall
                new Vector3(200f, 2.6f, -195f), // 12: Castle inner rampart skim over stone labyrinth
                new Vector3(235f, 2.0f, -175f)  // 13: Banking over eastern moat toward lakelands
        };

        Vector3[] lookKnots = new Vector3[]{
                new Vector3(255f, 1.5f, -190f), // 0: Looking along mist-shrouded water canal
                new Vector3(230f, 1.7f, -250f), // 1: Looking toward ancient woodland entrance
                new Vector3(185f, 1.8f, -280f), // 2: Looking down winding mossy cliff trail
                new Vector3(135f, 1.9f, -270f), // 3: Looking along canopy corridor toward clearings
                new Vector3(105f, 2.5f, -225f), // 4: Looking toward mountain gorge entrance
                new Vector3(88f,  3.5f, -165f), // 5: Looking up along mountain gorge pass
                new Vector3(105f, 2.8f, -115f), // 6: Looking down into sandstone canyon labyrinth
                new Vector3(150f, 1.8f, -85f),  // 7: Looking along winding desert slot canyon
                new Vector3(180f, 2.0f, -110f), // 8: Looking down southern desert avenue
                new Vector3(180f, 1.6f, -150f), // 9: Looking directly at castle south moat & gatehouse
                new Vector3(180f, 1.5f, -180f), // 10: Looking into grand castle royal corridor
                new Vector3(195f, 1.8f, -195f), // 11: Looking down stone corridor past pillared chambers
                new Vector3(225f, 2.2f, -180f), // 12: Looking east over castle walls toward horizon
                new Vector3(270f, 1.6f, -165f)  // 13: Gazing down into misty lakelands canals
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
