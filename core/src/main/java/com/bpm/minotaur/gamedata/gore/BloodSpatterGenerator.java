package com.bpm.minotaur.gamedata.gore;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Decides where a fight's blood lands on the father, in master-canvas pixels (+y down).
 *
 * Aimed with the body landmarks from assets/data/paperdoll_landmarks.json (pinned by
 * BloodSpatterGeneratorTest), because blood that lands where the fighting happens is
 * what sells it: spray from a victim in front of him lands on the chest, the face and
 * above all the weapon arm, while his own wounds can open anywhere and run downward.
 *
 * The weapon arm is the viewer's LEFT: the doll faces out of the screen, so his right
 * hand is on our left.
 *
 * Deterministic for a given Random, so the stains are testable.
 */
public final class BloodSpatterGenerator {

    // Landmarks, master-canvas pixels.
    static final float[] WEAPON_SHOULDER = {319f, 313f};
    static final float[] WEAPON_HAND = {180f, 776f};
    static final float[] OFF_SHOULDER = {731f, 313f};
    static final float[] OFF_HAND = {865.5f, 776f};
    static final float[] CHIN = {525.5f, 258f};
    static final float[] HEAD_TOP = {526f, 70f};
    static final float[] HIP_LEFT = {393.5f, 1058f};
    static final float[] HIP_RIGHT = {656f, 1058f};
    static final float[] ANKLE_LEFT = {339f, 1398f};
    static final float[] ANKLE_RIGHT = {717f, 1398f};
    static final float WAIST_Y = 729f;

    private static final float CANVAS_W = 1024f;
    private static final float CANVAS_H = 1536f;

    private enum Region { CHEST, WEAPON_ARM, OFF_ARM, FACE, LEGS }

    // Where spray from something he is hitting lands: out front, favouring his sword arm.
    private static final Region[] SPRAY_REGIONS = {
            Region.CHEST, Region.WEAPON_ARM, Region.FACE, Region.OFF_ARM, Region.LEGS};
    private static final float[] SPRAY_WEIGHTS = {0.48f, 0.29f, 0.08f, 0.05f, 0.10f};

    // Where he gets hurt: anywhere, but a wound on the legs is as likely as one on the arm.
    private static final Region[] WOUND_REGIONS = {
            Region.CHEST, Region.WEAPON_ARM, Region.OFF_ARM, Region.LEGS, Region.FACE};
    private static final float[] WOUND_WEIGHTS = {0.35f, 0.13f, 0.12f, 0.25f, 0.15f};

    private BloodSpatterGenerator() {
    }

    /**
     * A hit that drew blood from what he was fighting. Intensity is the same scale the
     * world spray uses: 2 chip, 5 solid, 8 massive.
     */
    public static List<BloodStain> forHitDealt(int intensity, int rgb, Random rng) {
        List<BloodStain> out = new ArrayList<BloodStain>();
        if (intensity <= 0) {
            return out;
        }
        // A few flecks per blow: the build-up is meant to take a whole expedition.
        int droplets = (intensity + 1) / 2;
        for (int i = 0; i < droplets; i++) {
            float drip = rng.nextFloat() < 0.15f ? 0.8f + rng.nextFloat() * 1.5f : 0f;
            out.add(stainIn(pick(SPRAY_REGIONS, SPRAY_WEIGHTS, rng), 5f + rng.nextFloat() * 8f, drip, rgb, rng));
        }
        if (rng.nextFloat() < intensity / 10f) {
            float drip = rng.nextFloat() < 0.5f ? 1f + rng.nextFloat() * 2f : 0f;
            out.add(stainIn(pick(SPRAY_REGIONS, SPRAY_WEIGHTS, rng), 14f + rng.nextFloat() * 12f, drip, rgb, rng));
        }
        return out;
    }

    /** The killing blow: a drenching splash, heavier the more overkill it carried. */
    public static List<BloodStain> forKill(int intensity, int rgb, Random rng) {
        List<BloodStain> out = new ArrayList<BloodStain>();
        if (intensity <= 0) {
            return out;
        }
        int droplets = intensity;
        for (int i = 0; i < droplets; i++) {
            float drip = rng.nextFloat() < 0.25f ? 1f + rng.nextFloat() * 2f : 0f;
            out.add(stainIn(pick(SPRAY_REGIONS, SPRAY_WEIGHTS, rng), 5f + rng.nextFloat() * 9f, drip, rgb, rng));
        }
        int splashes = 1 + intensity / 4;
        for (int i = 0; i < splashes; i++) {
            float drip = rng.nextFloat() < 0.7f ? 1.5f + rng.nextFloat() * 2.5f : 0f;
            out.add(stainIn(pick(SPRAY_REGIONS, SPRAY_WEIGHTS, rng), 22f + rng.nextFloat() * 26f, drip, rgb, rng));
        }
        return out;
    }

    /** His own blood: one wound sized by the damage, running down, with a little spatter. */
    public static List<BloodStain> forWound(int damage, int maxHp, int rgb, Random rng) {
        List<BloodStain> out = new ArrayList<BloodStain>();
        if (damage <= 0) {
            return out;
        }
        float share = Math.min(1f, damage / (float) Math.max(1, maxHp));
        Region region = pick(WOUND_REGIONS, WOUND_WEIGHTS, rng);
        BloodStain wound = stainIn(region, 12f + share * 36f, 2f + rng.nextFloat() * 4f, rgb, rng);
        out.add(wound);
        int droplets = 2 + rng.nextInt(3);
        for (int i = 0; i < droplets; i++) {
            float angle = rng.nextFloat() * (float) (Math.PI * 2);
            float dist = wound.radius * (1.5f + rng.nextFloat() * 2f);
            out.add(new BloodStain(
                    clamp(wound.x + (float) Math.cos(angle) * dist, CANVAS_W),
                    clamp(wound.y + (float) Math.sin(angle) * dist, CANVAS_H),
                    4f + rng.nextFloat() * 5f, 0f, rng.nextInt(), rgb));
        }
        return out;
    }

    private static Region pick(Region[] regions, float[] weights, Random rng) {
        float r = rng.nextFloat();
        for (int i = 0; i < regions.length; i++) {
            r -= weights[i];
            if (r <= 0f) {
                return regions[i];
            }
        }
        return regions[regions.length - 1];
    }

    private static BloodStain stainIn(Region region, float radius, float drip, int rgb, Random rng) {
        float x;
        float y;
        switch (region) {
            case WEAPON_ARM: {
                // Mostly the forearm and hand: that is what is out in front, swinging.
                float t = 0.3f + rng.nextFloat() * 0.7f;
                x = lerp(WEAPON_SHOULDER[0], WEAPON_HAND[0], t) + gauss(rng, 28f);
                y = lerp(WEAPON_SHOULDER[1], WEAPON_HAND[1], t) + gauss(rng, 28f);
                break;
            }
            case OFF_ARM: {
                float t = 0.3f + rng.nextFloat() * 0.7f;
                x = lerp(OFF_SHOULDER[0], OFF_HAND[0], t) + gauss(rng, 28f);
                y = lerp(OFF_SHOULDER[1], OFF_HAND[1], t) + gauss(rng, 28f);
                break;
            }
            case FACE: {
                float cx = (CHIN[0] + HEAD_TOP[0]) / 2f;
                // Lower face and beard rather than the crown: spray arrives from below eye level.
                float cy = lerp(HEAD_TOP[1], CHIN[1], 0.7f);
                x = cx + gauss(rng, 50f);
                y = cy + gauss(rng, 45f);
                // The face is small: splashes there are flecks, or it drowns within a dozen fights.
                radius *= 0.6f;
                break;
            }
            case LEGS: {
                boolean left = rng.nextBoolean();
                float[] hip = left ? HIP_LEFT : HIP_RIGHT;
                float[] ankle = left ? ANKLE_LEFT : ANKLE_RIGHT;
                float t = rng.nextFloat();
                x = lerp(hip[0], ankle[0], t) + gauss(rng, 32f);
                y = lerp(hip[1], ankle[1], t) + gauss(rng, 20f);
                break;
            }
            case CHEST:
            default: {
                float cx = (WEAPON_SHOULDER[0] + OFF_SHOULDER[0]) / 2f;
                float cy = lerp(WEAPON_SHOULDER[1], WAIST_Y, 0.45f);
                x = cx + gauss(rng, 110f);
                y = cy + gauss(rng, 115f);
                break;
            }
        }
        return new BloodStain(clamp(x, CANVAS_W), clamp(y, CANVAS_H), radius, drip, rng.nextInt(), rgb);
    }

    private static float gauss(Random rng, float sd) {
        return (float) rng.nextGaussian() * sd;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float max) {
        return Math.max(0f, Math.min(max - 1f, v));
    }

    /** 0xRRGGBB from a libGDX-style float colour, so data classes stay free of Color. */
    public static int rgb(float r, float g, float b) {
        return (Math.round(r * 255f) & 0xFF) << 16 | (Math.round(g * 255f) & 0xFF) << 8 | (Math.round(b * 255f) & 0xFF);
    }
}
