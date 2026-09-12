package com.bpm.minotaur.rendering.animation;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.bpm.minotaur.gamedata.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the kinematic trajectory, timing curves, visual effects, and combat payoff
 * of a single strike in an animation combo string.
 */
public class CombatMotionProfile {

    public float startRotation = -60f;
    public float endRotation = 60f;
    public float startXRel = 0.75f;
    public float endXRel = 0.25f;
    public float startYRel = -0.05f;
    public float endYRel = -0.05f;
    public float arcHeightRel = 0.10f;

    public float duration = 0.32f;
    public float anticipationRatio = 0.20f;
    public float impactRatio = 0.38f;

    public float damageMultiplier = 1.0f;
    public float screenTrauma = 0.15f;
    public int comboStep = 0;
    public String comboName = "STRIKE";
    public boolean isFinisher = false;
    public boolean isOffHand = false;

    public boolean isThrust = false;
    public boolean isRangedDraw = false;
    public boolean isRangedKick = false;
    public boolean isShieldBash = false;

    public Color trailColor = new Color(0.9f, 0.95f, 1f, 0.7f);

    public CombatMotionProfile() {
    }

    public CombatMotionProfile cpy() {
        CombatMotionProfile c = new CombatMotionProfile();
        c.startRotation = this.startRotation;
        c.endRotation = this.endRotation;
        c.startXRel = this.startXRel;
        c.endXRel = this.endXRel;
        c.startYRel = this.startYRel;
        c.endYRel = this.endYRel;
        c.arcHeightRel = this.arcHeightRel;
        c.duration = this.duration;
        c.anticipationRatio = this.anticipationRatio;
        c.impactRatio = this.impactRatio;
        c.damageMultiplier = this.damageMultiplier;
        c.screenTrauma = this.screenTrauma;
        c.comboStep = this.comboStep;
        c.comboName = this.comboName;
        c.isFinisher = this.isFinisher;
        c.isOffHand = this.isOffHand;
        c.isThrust = this.isThrust;
        c.isRangedDraw = this.isRangedDraw;
        c.isRangedKick = this.isRangedKick;
        c.isShieldBash = this.isShieldBash;
        c.trailColor = (this.trailColor != null) ? this.trailColor.cpy() : Color.WHITE;
        return c;
    }

    /**
     * Calculates the animated position and rotation at a given normalized progress [0.0 - 1.0].
     * Uses non-linear asymmetric kinematics:
     * - Phase 1 (0 -> anticipationRatio): Anticipation windup pull-back.
     * - Phase 2 (anticipationRatio -> impactRatio): Explosive forward snap towards impact.
     * - Phase 3 (impactRatio -> 1.0): Snappy follow-through and recovery back to ready stance.
     */
    public MotionState evaluate(float progress) {
        progress = MathUtils.clamp(progress, 0f, 1f);
        MotionState state = new MotionState();

        if (isThrust) {
            evaluateThrust(progress, state);
            return state;
        }

        if (isRangedDraw) {
            evaluateRangedDraw(progress, state);
            return state;
        }

        if (isRangedKick) {
            evaluateRangedKick(progress, state);
            return state;
        }

        if (isShieldBash) {
            evaluateShieldBash(progress, state);
            return state;
        }

        // Standard dynamic swing kinematics
        if (progress < anticipationRatio) {
            // Windup: Pull back slightly in the opposite direction
            float t = progress / anticipationRatio;
            float smooth = t * t;
            state.rotation = MathUtils.lerp(startRotation, startRotation - 12f, smooth);
            state.xRel = MathUtils.lerp(startXRel, startXRel + 0.04f, smooth);
            state.yRel = MathUtils.lerp(startYRel, startYRel + 0.03f, smooth);
        } else if (progress < impactRatio) {
            // Explosive snap: Rapid acceleration to impact point
            float t = (progress - anticipationRatio) / (impactRatio - anticipationRatio);
            float snap = t * t * (3f - 2f * t); // SmoothStep
            state.rotation = MathUtils.lerp(startRotation - 12f, endRotation, snap);
            state.xRel = MathUtils.lerp(startXRel + 0.04f, endXRel, snap);
            state.yRel = MathUtils.lerp(startYRel + 0.03f, endYRel, snap) + MathUtils.sin(t * MathUtils.PI) * arcHeightRel;
        } else {
            // Recovery: Follow through and settle
            float t = (progress - impactRatio) / (1f - impactRatio);
            float recover = t * t;
            state.rotation = MathUtils.lerp(endRotation, startRotation, recover);
            state.xRel = MathUtils.lerp(endXRel, startXRel, recover);
            state.yRel = MathUtils.lerp(endYRel, startYRel, recover);
        }

        return state;
    }

    private void evaluateThrust(float progress, MotionState state) {
        if (progress < anticipationRatio) {
            float t = progress / anticipationRatio;
            state.rotation = MathUtils.lerp(startRotation, startRotation - 5f, t);
            state.xRel = MathUtils.lerp(startXRel, startXRel + 0.05f, t);
            state.yRel = MathUtils.lerp(startYRel, startYRel - 0.08f, t); // Draw back
        } else if (progress < impactRatio) {
            float t = (progress - anticipationRatio) / (impactRatio - anticipationRatio);
            float snap = t * t * (3f - 2f * t);
            state.rotation = MathUtils.lerp(startRotation - 5f, endRotation, snap);
            state.xRel = MathUtils.lerp(startXRel + 0.05f, 0.50f, snap); // Center screen thrust
            state.yRel = MathUtils.lerp(startYRel - 0.08f, 0.18f, snap); // Thrust forward
        } else {
            float t = (progress - impactRatio) / (1f - impactRatio);
            state.rotation = MathUtils.lerp(endRotation, startRotation, t);
            state.xRel = MathUtils.lerp(0.50f, startXRel, t);
            state.yRel = MathUtils.lerp(0.18f, startYRel, t);
        }
    }

    private void evaluateRangedDraw(float progress, MotionState state) {
        if (progress < anticipationRatio) {
            float t = progress / anticipationRatio;
            state.rotation = MathUtils.lerp(0f, -8f, t);
            state.xRel = MathUtils.lerp(startXRel, startXRel + 0.05f, t);
            state.yRel = MathUtils.lerp(startYRel, startYRel - 0.06f, t); // Pull string back
        } else if (progress < impactRatio) {
            // Release snap
            float t = (progress - anticipationRatio) / (impactRatio - anticipationRatio);
            state.rotation = MathUtils.lerp(-8f, 4f, t);
            state.xRel = MathUtils.lerp(startXRel + 0.05f, startXRel - 0.02f, t);
            state.yRel = MathUtils.lerp(startYRel - 0.06f, startYRel + 0.03f, t);
        } else {
            float t = (progress - impactRatio) / (1f - impactRatio);
            state.rotation = MathUtils.lerp(4f, 0f, t);
            state.xRel = MathUtils.lerp(startXRel - 0.02f, startXRel, t);
            state.yRel = MathUtils.lerp(startYRel + 0.03f, startYRel, t);
        }
    }

    private void evaluateRangedKick(float progress, MotionState state) {
        if (progress < impactRatio) {
            // Immediate explosive upward recoil kick
            float t = progress / impactRatio;
            float snap = MathUtils.sin(t * (MathUtils.PI / 2f));
            state.rotation = MathUtils.lerp(0f, 32f, snap);
            state.xRel = MathUtils.lerp(startXRel, startXRel + 0.03f, snap);
            state.yRel = MathUtils.lerp(startYRel, startYRel + 0.12f, snap);
        } else {
            // Slower settle back to rest
            float t = (progress - impactRatio) / (1f - impactRatio);
            state.rotation = MathUtils.lerp(32f, 0f, t * t);
            state.xRel = MathUtils.lerp(startXRel + 0.03f, startXRel, t);
            state.yRel = MathUtils.lerp(startYRel + 0.12f, startYRel, t);
        }
    }

    private void evaluateShieldBash(float progress, MotionState state) {
        if (progress < anticipationRatio) {
            float t = progress / anticipationRatio;
            state.rotation = MathUtils.lerp(15f, 25f, t);
            state.xRel = MathUtils.lerp(0.20f, 0.15f, t);
            state.yRel = MathUtils.lerp(-0.05f, -0.08f, t);
        } else if (progress < impactRatio) {
            float t = (progress - anticipationRatio) / (impactRatio - anticipationRatio);
            float snap = t * t * (3f - 2f * t);
            state.rotation = MathUtils.lerp(25f, -5f, snap);
            state.xRel = MathUtils.lerp(0.15f, 0.42f, snap); // Smash into center-left
            state.yRel = MathUtils.lerp(-0.08f, 0.10f, snap);
        } else {
            float t = (progress - impactRatio) / (1f - impactRatio);
            state.rotation = MathUtils.lerp(-5f, 15f, t);
            state.xRel = MathUtils.lerp(0.42f, 0.20f, t);
            state.yRel = MathUtils.lerp(0.10f, -0.05f, t);
        }
    }

    public static class MotionState {
        public float xRel;
        public float yRel;
        public float rotation;
    }

    /**
     * Generates a complete combo sequence of motion profiles for the specified archetype and item.
     */
    public static List<CombatMotionProfile> buildComboChain(AnimationArchetype archetype, Item weapon, boolean isDualWielding) {
        List<CombatMotionProfile> chain = new ArrayList<>();

        if (isDualWielding) {
            // 3-hit alternating dual wield sequence
            CombatMotionProfile strike1 = createSlashingStrike(0, false, "RIGHT SLASH", 1.0f, 0.15f);
            CombatMotionProfile strike2 = createSlashingStrike(1, true, "OFFHAND CROSS", 1.15f, 0.20f);
            strike2.isOffHand = true;
            strike2.startXRel = 0.25f;
            strike2.endXRel = 0.75f;
            strike2.startRotation = 60f;
            strike2.endRotation = -50f;

            CombatMotionProfile finisher = createSlashingStrike(2, false, "SCISSOR FINISHER", 1.40f, 0.45f);
            finisher.isFinisher = true;
            finisher.duration = 0.36f;
            chain.add(strike1);
            chain.add(strike2);
            chain.add(finisher);
            return chain;
        }

        switch (archetype) {
            case SLASHING_2H: {
                // 2-hit colossal cleave
                CombatMotionProfile sweep = new CombatMotionProfile();
                sweep.comboStep = 0;
                sweep.comboName = "COLOSSAL SWEEP";
                sweep.duration = 0.40f;
                sweep.anticipationRatio = 0.25f;
                sweep.impactRatio = 0.42f;
                sweep.startRotation = -75f;
                sweep.endRotation = 65f;
                sweep.startXRel = 0.85f;
                sweep.endXRel = 0.15f;
                sweep.damageMultiplier = 1.10f;
                sweep.screenTrauma = 0.30f;
                sweep.arcHeightRel = 0.15f;

                CombatMotionProfile titan = new CombatMotionProfile();
                titan.comboStep = 1;
                titan.comboName = "TITAN'S CLEAVE";
                titan.isFinisher = true;
                titan.duration = 0.45f;
                titan.anticipationRatio = 0.28f;
                titan.impactRatio = 0.45f;
                titan.startRotation = -10f;
                titan.endRotation = 15f;
                titan.startXRel = 0.52f;
                titan.endXRel = 0.48f;
                titan.startYRel = 0.35f;
                titan.endYRel = -0.18f;
                titan.damageMultiplier = 1.45f;
                titan.screenTrauma = 0.55f;

                chain.add(sweep);
                chain.add(titan);
                break;
            }

            case BLUNT_CRUSHING: {
                // 3-hit crushing chain
                CombatMotionProfile smash = new CombatMotionProfile();
                smash.comboStep = 0;
                smash.comboName = "HEAVY BATTER";
                smash.startRotation = -65f;
                smash.endRotation = 40f;
                smash.startXRel = 0.80f;
                smash.endXRel = 0.28f;
                smash.screenTrauma = 0.22f;

                CombatMotionProfile sweep = new CombatMotionProfile();
                sweep.comboStep = 1;
                sweep.comboName = "HORIZONTAL CRUSH";
                sweep.startRotation = 50f;
                sweep.endRotation = -45f;
                sweep.startXRel = 0.25f;
                sweep.endXRel = 0.75f;
                sweep.damageMultiplier = 1.15f;
                sweep.screenTrauma = 0.28f;

                CombatMotionProfile skull = new CombatMotionProfile();
                skull.comboStep = 2;
                skull.comboName = "SKULL CRUSHER";
                skull.isFinisher = true;
                skull.duration = 0.38f;
                skull.startRotation = -12f;
                skull.endRotation = 12f;
                skull.startXRel = 0.52f;
                skull.endXRel = 0.50f;
                skull.startYRel = 0.30f;
                skull.endYRel = -0.15f;
                skull.damageMultiplier = 1.40f;
                skull.screenTrauma = 0.50f;

                chain.add(smash);
                chain.add(sweep);
                chain.add(skull);
                break;
            }

            case THRUSTING_PIERCE: {
                boolean isDagger = (weapon != null && weapon.getType() != null
                        && weapon.getType().name().contains("DAGGER"));
                if (isDagger) {
                    // 4-hit dagger flurry
                    chain.add(createThrustStrike(0, "LUNGING STAB", 1.0f, 0.10f));
                    chain.add(createThrustStrike(1, "RISING PUNCTURE", 1.10f, 0.12f));
                    chain.add(createThrustStrike(2, "KIDNEY PIERCE", 1.20f, 0.15f));
                    CombatMotionProfile fin = createThrustStrike(3, "HEARTSEEKER FLURRY", 1.35f, 0.35f);
                    fin.isFinisher = true;
                    chain.add(fin);
                } else {
                    // 3-hit spear/rapier sequence
                    chain.add(createThrustStrike(0, "LUNGE THRUST", 1.0f, 0.15f));
                    chain.add(createThrustStrike(1, "THROAT PIERCE", 1.15f, 0.22f));
                    CombatMotionProfile fin = createThrustStrike(2, "IMPALING SKEWER", 1.35f, 0.40f);
                    fin.isFinisher = true;
                    chain.add(fin);
                }
                break;
            }

            case BRAWLING: {
                // 4-hit boxing sequence
                CombatMotionProfile jab = createBrawlStrike(0, "LEFT JAB", 1.0f, 0.10f, 0.35f, 0.48f);
                CombatMotionProfile cross = createBrawlStrike(1, "RIGHT CROSS", 1.10f, 0.15f, 0.65f, 0.50f);
                CombatMotionProfile hook = createBrawlStrike(2, "LEFT HOOK", 1.20f, 0.20f, 0.30f, 0.52f);
                CombatMotionProfile uppercut = createBrawlStrike(3, "HAYMAKER UPPERCUT", 1.40f, 0.45f, 0.50f, 0.50f);
                uppercut.isFinisher = true;
                chain.add(jab);
                chain.add(cross);
                chain.add(hook);
                chain.add(uppercut);
                break;
            }

            case RANGED_BOW: {
                CombatMotionProfile bow = new CombatMotionProfile();
                bow.comboName = "PRECISION RELEASE";
                bow.isRangedDraw = true;
                bow.duration = 0.35f;
                bow.anticipationRatio = 0.25f;
                bow.impactRatio = 0.38f;
                bow.screenTrauma = 0.08f;
                chain.add(bow);
                break;
            }

            case RANGED_FIREARM: {
                CombatMotionProfile gun = new CombatMotionProfile();
                gun.comboName = "POWDER BLAST";
                gun.isRangedKick = true;
                gun.duration = 0.38f;
                gun.impactRatio = 0.18f;
                gun.screenTrauma = 0.40f;
                chain.add(gun);
                break;
            }

            case SHIELD: {
                CombatMotionProfile bash = new CombatMotionProfile();
                bash.comboName = "SHIELD BASH";
                bash.isShieldBash = true;
                bash.duration = 0.32f;
                bash.anticipationRatio = 0.20f;
                bash.impactRatio = 0.38f;
                bash.screenTrauma = 0.30f;
                chain.add(bash);
                break;
            }

            case SLASHING_1H:
            default: {
                // Standard 3-hit slashing chain
                chain.add(createSlashingStrike(0, false, "DIAGONAL CLEAVE", 1.0f, 0.15f));
                chain.add(createSlashingStrike(1, false, "RISING BACKSLASH", 1.15f, 0.22f));
                CombatMotionProfile fin = createSlashingStrike(2, true, "EXECUTIONER'S SLAM", 1.35f, 0.40f);
                fin.isFinisher = true;
                chain.add(fin);
                break;
            }
        }

        return chain;
    }

    private static CombatMotionProfile createSlashingStrike(int step, boolean reverse, String name, float dmgMult, float trauma) {
        CombatMotionProfile p = new CombatMotionProfile();
        p.comboStep = step;
        p.comboName = name;
        p.damageMultiplier = dmgMult;
        p.screenTrauma = trauma;
        p.duration = 0.32f;

        if (step == 2) {
            // Overhead executioner
            p.startRotation = -15f;
            p.endRotation = 15f;
            p.startXRel = 0.52f;
            p.endXRel = 0.48f;
            p.startYRel = 0.30f;
            p.endYRel = -0.15f;
            p.arcHeightRel = 0.05f;
        } else if (reverse || step == 1) {
            p.startRotation = 50f;
            p.endRotation = -50f;
            p.startXRel = 0.25f;
            p.endXRel = 0.75f;
            p.startYRel = -0.05f;
            p.endYRel = -0.05f;
        } else {
            p.startRotation = -60f;
            p.endRotation = 45f;
            p.startXRel = 0.78f;
            p.endXRel = 0.28f;
            p.startYRel = -0.05f;
            p.endYRel = -0.05f;
        }
        return p;
    }

    private static CombatMotionProfile createThrustStrike(int step, String name, float dmgMult, float trauma) {
        CombatMotionProfile p = new CombatMotionProfile();
        p.comboStep = step;
        p.comboName = name;
        p.damageMultiplier = dmgMult;
        p.screenTrauma = trauma;
        p.isThrust = true;
        p.duration = 0.26f;
        p.anticipationRatio = 0.20f;
        p.impactRatio = 0.36f;
        p.startXRel = 0.65f;
        p.startYRel = -0.05f;
        p.startRotation = -35f;
        p.endRotation = -20f;
        return p;
    }

    private static CombatMotionProfile createBrawlStrike(int step, String name, float dmgMult, float trauma, float startX, float endX) {
        CombatMotionProfile p = new CombatMotionProfile();
        p.comboStep = step;
        p.comboName = name;
        p.damageMultiplier = dmgMult;
        p.screenTrauma = trauma;
        p.duration = 0.24f;
        p.anticipationRatio = 0.18f;
        p.impactRatio = 0.35f;
        p.startXRel = startX;
        p.endXRel = endX;
        p.startYRel = -0.10f;
        p.endYRel = 0.08f;
        p.startRotation = (startX > 0.5f) ? -35f : 35f;
        p.endRotation = (startX > 0.5f) ? 10f : -10f;
        return p;
    }
}
