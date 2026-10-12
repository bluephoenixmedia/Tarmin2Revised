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
    public boolean isDualStrike = false;

    public enum Quadrant {
        TOP_RIGHT,
        TOP_LEFT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        CENTER
    }

    public Quadrant entryQuadrant = Quadrant.TOP_RIGHT;
    public Quadrant exitQuadrant = Quadrant.BOTTOM_LEFT;
    public boolean isRiposte = false;
    public boolean isCrit = false;

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
        c.isDualStrike = this.isDualStrike;
        c.entryQuadrant = this.entryQuadrant;
        c.exitQuadrant = this.exitQuadrant;
        c.isRiposte = this.isRiposte;
        c.isCrit = this.isCrit;
        c.isThrust = this.isThrust;
        c.isRangedDraw = this.isRangedDraw;
        c.isRangedKick = this.isRangedKick;
        c.isShieldBash = this.isShieldBash;
        c.trailColor = (this.trailColor != null) ? this.trailColor.cpy() : Color.WHITE;
        return c;
    }

    /**
     * Resolves the cut angle in degrees corresponding to this motion profile's strike arc.
     */
    public float getCutAngle() {
        if (entryQuadrant == null || exitQuadrant == null) return 45f;
        if (exitQuadrant == Quadrant.CENTER) {
            return (entryQuadrant == Quadrant.TOP_LEFT || entryQuadrant == Quadrant.BOTTOM_LEFT) ? -20f : 20f;
        }
        switch (exitQuadrant) {
            case BOTTOM_LEFT:
                return (entryQuadrant == Quadrant.TOP_RIGHT) ? 45f : 60f;
            case BOTTOM_RIGHT:
                return (entryQuadrant == Quadrant.TOP_LEFT) ? -45f : -60f;
            case TOP_LEFT:
                return (entryQuadrant == Quadrant.BOTTOM_RIGHT) ? -135f : 135f;
            case TOP_RIGHT:
                return (entryQuadrant == Quadrant.BOTTOM_LEFT) ? 135f : -135f;
            case CENTER:
            default:
                return 0f;
        }
    }

    /**
     * Calculates the animated position and rotation at a given normalized progress [0.0 - 1.0].
     * Uses non-linear asymmetric kinematics:
     * - Phase 1 (0 -> anticipationRatio): Anticipation windup pull-back.
     * - Phase 2 (anticipationRatio -> impactRatio): Explosive forward snap towards impact.
     * - Phase 3 (impactRatio -> 1.0): Snappy follow-through and recovery back to ready stance.
     */
    public static final float IDLE_Y_REL = -0.12f;

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
            // Windup: Surge up from off-screen into strike position
            float t = progress / anticipationRatio;
            float smooth = t * t;
            state.rotation = MathUtils.lerp(startRotation, startRotation - 12f, smooth);
            state.xRel = MathUtils.lerp(startXRel, startXRel + 0.04f, smooth);
            state.yRel = MathUtils.lerp(IDLE_Y_REL, startYRel, smooth);
        } else if (progress < impactRatio) {
            // Explosive snap: Rapid acceleration to impact point
            float t = (progress - anticipationRatio) / (impactRatio - anticipationRatio);
            float snap = t * t * (3f - 2f * t); // SmoothStep
            state.rotation = MathUtils.lerp(startRotation - 12f, endRotation, snap);
            state.xRel = MathUtils.lerp(startXRel + 0.04f, endXRel, snap);
            state.yRel = MathUtils.lerp(startYRel, endYRel, snap) + MathUtils.sin(t * MathUtils.PI) * arcHeightRel;
        } else {
            // Recovery: Follow through and settle back down off-screen
            float t = (progress - impactRatio) / (1f - impactRatio);
            float recover = t * t;
            state.rotation = MathUtils.lerp(endRotation, startRotation, recover);
            state.xRel = MathUtils.lerp(endXRel, startXRel, recover);
            state.yRel = MathUtils.lerp(endYRel, IDLE_Y_REL, recover);
        }

        return state;
    }

    private void evaluateThrust(float progress, MotionState state) {
        if (progress < anticipationRatio) {
            float t = progress / anticipationRatio;
            state.rotation = MathUtils.lerp(startRotation, startRotation - 5f, t);
            state.xRel = MathUtils.lerp(startXRel, startXRel + 0.05f, t);
            state.yRel = MathUtils.lerp(IDLE_Y_REL, startYRel - 0.04f, t);
        } else if (progress < impactRatio) {
            float t = (progress - anticipationRatio) / (impactRatio - anticipationRatio);
            float snap = t * t * (3f - 2f * t);
            state.rotation = MathUtils.lerp(startRotation - 5f, endRotation, snap);
            state.xRel = MathUtils.lerp(startXRel + 0.05f, 0.50f, snap); // Center screen thrust
            state.yRel = MathUtils.lerp(startYRel - 0.04f, 0.12f, snap); // Thrust forward
        } else {
            float t = (progress - impactRatio) / (1f - impactRatio);
            state.rotation = MathUtils.lerp(endRotation, startRotation, t);
            state.xRel = MathUtils.lerp(0.50f, startXRel, t);
            state.yRel = MathUtils.lerp(0.12f, IDLE_Y_REL, t * t);
        }
    }

    private void evaluateRangedDraw(float progress, MotionState state) {
        if (progress < anticipationRatio) {
            float t = progress / anticipationRatio;
            state.rotation = MathUtils.lerp(0f, -8f, t);
            state.xRel = MathUtils.lerp(startXRel, startXRel + 0.05f, t);
            state.yRel = MathUtils.lerp(IDLE_Y_REL, startYRel, t);
        } else if (progress < impactRatio) {
            float t = (progress - anticipationRatio) / (impactRatio - anticipationRatio);
            state.rotation = MathUtils.lerp(-8f, 4f, t);
            state.xRel = MathUtils.lerp(startXRel + 0.05f, startXRel - 0.02f, t);
            state.yRel = MathUtils.lerp(startYRel, startYRel + 0.08f, t);
        } else {
            float t = (progress - impactRatio) / (1f - impactRatio);
            state.rotation = MathUtils.lerp(4f, 0f, t);
            state.xRel = MathUtils.lerp(startXRel - 0.02f, startXRel, t);
            state.yRel = MathUtils.lerp(startYRel + 0.08f, IDLE_Y_REL, t * t);
        }
    }

    private void evaluateRangedKick(float progress, MotionState state) {
        if (progress < impactRatio) {
            float t = progress / impactRatio;
            float snap = MathUtils.sin(t * (MathUtils.PI / 2f));
            state.rotation = MathUtils.lerp(0f, 28f, snap);
            state.xRel = MathUtils.lerp(startXRel, startXRel + 0.03f, snap);
            state.yRel = MathUtils.lerp(IDLE_Y_REL, 0.08f, snap);
        } else {
            float t = (progress - impactRatio) / (1f - impactRatio);
            state.rotation = MathUtils.lerp(28f, 0f, t * t);
            state.xRel = MathUtils.lerp(startXRel + 0.03f, startXRel, t);
            state.yRel = MathUtils.lerp(0.08f, IDLE_Y_REL, t * t);
        }
    }

    private void evaluateShieldBash(float progress, MotionState state) {
        if (progress < anticipationRatio) {
            float t = progress / anticipationRatio;
            state.rotation = MathUtils.lerp(15f, 25f, t);
            state.xRel = MathUtils.lerp(0.18f, 0.12f, t);
            state.yRel = MathUtils.lerp(IDLE_Y_REL, -0.05f, t);
        } else if (progress < impactRatio) {
            float t = (progress - anticipationRatio) / (impactRatio - anticipationRatio);
            float snap = t * t * (3f - 2f * t);
            state.rotation = MathUtils.lerp(25f, -5f, snap);
            state.xRel = MathUtils.lerp(0.12f, 0.40f, snap);
            state.yRel = MathUtils.lerp(-0.05f, 0.08f, snap);
        } else {
            float t = (progress - impactRatio) / (1f - impactRatio);
            state.rotation = MathUtils.lerp(-5f, 15f, t);
            state.xRel = MathUtils.lerp(0.40f, 0.18f, t);
            state.yRel = MathUtils.lerp(0.08f, IDLE_Y_REL, t * t);
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
            finisher.isDualStrike = true;
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

            case AXE_CHOPPING: {
                // 3-hit aggressive downward axe chops and hooking cleaves
                CombatMotionProfile chop = new CombatMotionProfile();
                chop.comboStep = 0;
                chop.comboName = "OVERHEAD CHOP";
                chop.duration = 0.32f;
                chop.anticipationRatio = 0.22f;
                chop.impactRatio = 0.38f;
                chop.startRotation = -18f;
                chop.endRotation = 12f;
                chop.startXRel = 0.62f;
                chop.endXRel = 0.54f;
                chop.startYRel = 0.28f;
                chop.endYRel = -0.16f;
                chop.arcHeightRel = 0.04f;
                chop.damageMultiplier = 1.05f;
                chop.screenTrauma = 0.24f;

                CombatMotionProfile hack = new CombatMotionProfile();
                hack.comboStep = 1;
                hack.comboName = "DIAGONAL HACK";
                hack.duration = 0.30f;
                hack.anticipationRatio = 0.20f;
                hack.impactRatio = 0.36f;
                hack.startRotation = -55f;
                hack.endRotation = 35f;
                hack.startXRel = 0.78f;
                hack.endXRel = 0.32f;
                hack.startYRel = 0.15f;
                hack.endYRel = -0.12f;
                hack.arcHeightRel = 0.08f;
                hack.damageMultiplier = 1.15f;
                hack.screenTrauma = 0.28f;

                CombatMotionProfile hook = new CombatMotionProfile();
                hook.comboStep = 2;
                hook.comboName = "CLEAVING HOOK";
                hook.isFinisher = true;
                hook.duration = 0.38f;
                hook.anticipationRatio = 0.24f;
                hook.impactRatio = 0.42f;
                hook.startRotation = 50f;
                hook.endRotation = -50f;
                hook.startXRel = 0.22f;
                hook.endXRel = 0.80f;
                hook.startYRel = -0.06f;
                hook.endYRel = -0.04f;
                hook.arcHeightRel = 0.10f;
                hook.damageMultiplier = 1.40f;
                hook.screenTrauma = 0.48f;

                chain.add(chop);
                chain.add(hack);
                chain.add(hook);
                break;
            }

            case POLEARM_SWEEP: {
                // 3-hit extended reach polearm sequence: wide horizontal reap, overhead heft, vaulting thrust
                CombatMotionProfile reap = new CombatMotionProfile();
                reap.comboStep = 0;
                reap.comboName = "WIDE REAP";
                reap.duration = 0.38f;
                reap.anticipationRatio = 0.24f;
                reap.impactRatio = 0.42f;
                reap.startRotation = -70f;
                reap.endRotation = 60f;
                reap.startXRel = 0.88f;
                reap.endXRel = 0.16f;
                reap.startYRel = 0.02f;
                reap.endYRel = -0.04f;
                reap.arcHeightRel = 0.12f;
                reap.damageMultiplier = 1.05f;
                reap.screenTrauma = 0.22f;

                CombatMotionProfile heft = new CombatMotionProfile();
                heft.comboStep = 1;
                heft.comboName = "OVERHEAD HEFT";
                heft.duration = 0.36f;
                heft.anticipationRatio = 0.22f;
                heft.impactRatio = 0.40f;
                heft.startRotation = -10f;
                heft.endRotation = 15f;
                heft.startXRel = 0.52f;
                heft.endXRel = 0.48f;
                heft.startYRel = 0.34f;
                heft.endYRel = -0.16f;
                heft.arcHeightRel = 0.06f;
                heft.damageMultiplier = 1.20f;
                heft.screenTrauma = 0.35f;

                CombatMotionProfile thrust = createThrustStrike(2, "VAULTING THRUST", 1.45f, 0.45f);
                thrust.isFinisher = true;
                thrust.duration = 0.34f;
                thrust.startRotation = -30f;
                thrust.endRotation = -15f;
                thrust.startXRel = 0.65f;
                thrust.startYRel = -0.04f;

                chain.add(reap);
                chain.add(heft);
                chain.add(thrust);
                break;
            }

            case FLAIL_WHIP: {
                // 3-hit looping momentum flail chain
                CombatMotionProfile swirl = new CombatMotionProfile();
                swirl.comboStep = 0;
                swirl.comboName = "MOMENTUM SWIRL";
                swirl.duration = 0.35f;
                swirl.anticipationRatio = 0.24f;
                swirl.impactRatio = 0.40f;
                swirl.startRotation = -80f;
                swirl.endRotation = 55f;
                swirl.startXRel = 0.82f;
                swirl.endXRel = 0.20f;
                swirl.startYRel = 0.08f;
                swirl.endYRel = -0.08f;
                swirl.arcHeightRel = 0.18f;
                swirl.damageMultiplier = 1.05f;
                swirl.screenTrauma = 0.20f;

                CombatMotionProfile snap = new CombatMotionProfile();
                snap.comboStep = 1;
                snap.comboName = "SIDE SNAP";
                snap.duration = 0.28f;
                snap.anticipationRatio = 0.18f;
                snap.impactRatio = 0.36f;
                snap.startRotation = 65f;
                snap.endRotation = -55f;
                snap.startXRel = 0.20f;
                snap.endXRel = 0.82f;
                snap.startYRel = -0.05f;
                snap.endYRel = -0.02f;
                snap.arcHeightRel = 0.12f;
                snap.damageMultiplier = 1.15f;
                snap.screenTrauma = 0.25f;

                CombatMotionProfile crush = new CombatMotionProfile();
                crush.comboStep = 2;
                crush.comboName = "OVERHEAD CRUSH";
                crush.isFinisher = true;
                crush.duration = 0.40f;
                crush.anticipationRatio = 0.25f;
                crush.impactRatio = 0.44f;
                crush.startRotation = -15f;
                crush.endRotation = 18f;
                crush.startXRel = 0.50f;
                crush.endXRel = 0.50f;
                crush.startYRel = 0.36f;
                crush.endYRel = -0.20f;
                crush.arcHeightRel = 0.08f;
                crush.damageMultiplier = 1.45f;
                crush.screenTrauma = 0.52f;

                chain.add(swirl);
                chain.add(snap);
                chain.add(crush);
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
            p.entryQuadrant = Quadrant.TOP_RIGHT;
            p.exitQuadrant = Quadrant.CENTER;
        } else if (reverse || step == 1) {
            p.startRotation = 50f;
            p.endRotation = -50f;
            p.startXRel = 0.25f;
            p.endXRel = 0.75f;
            p.startYRel = -0.05f;
            p.endYRel = -0.05f;
            p.entryQuadrant = Quadrant.BOTTOM_LEFT;
            p.exitQuadrant = Quadrant.TOP_RIGHT;
        } else {
            p.startRotation = -60f;
            p.endRotation = 45f;
            p.startXRel = 0.78f;
            p.endXRel = 0.28f;
            p.startYRel = -0.05f;
            p.endYRel = -0.05f;
            p.entryQuadrant = Quadrant.TOP_RIGHT;
            p.exitQuadrant = Quadrant.BOTTOM_LEFT;
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
        p.entryQuadrant = Quadrant.CENTER;
        p.exitQuadrant = Quadrant.CENTER;
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
        p.entryQuadrant = (startX > 0.5f) ? Quadrant.TOP_RIGHT : Quadrant.TOP_LEFT;
        p.exitQuadrant = Quadrant.CENTER;
        return p;
    }

    /**
     * Resolves the maximum combo decay window duration based on archetype tempo.
     */
    public static float getComboWindowMax(AnimationArchetype archetype, Item weapon) {
        if (archetype == AnimationArchetype.BRAWLING) {
            return 0.40f;
        }
        if (archetype == AnimationArchetype.THRUSTING_PIERCE) {
            if (weapon != null && weapon.getType() != null && weapon.getType().name().contains("KNIFE")) {
                return 0.40f;
            }
            if (weapon != null && weapon.getTemplate() != null && weapon.getTemplate().friendlyName != null
                    && weapon.getTemplate().friendlyName.toLowerCase().contains("dagger")) {
                return 0.40f;
            }
        }
        if (archetype == AnimationArchetype.SLASHING_2H || archetype == AnimationArchetype.BLUNT_CRUSHING) {
            return 0.85f;
        }
        if (archetype == AnimationArchetype.RANGED_BOW || archetype == AnimationArchetype.RANGED_FIREARM) {
            return 0.50f;
        }
        return 0.55f;
    }

    /**
     * Returns an expanded pool of 6 to 8 varied kinematic profiles for the given archetype,
     * mapped across entrance and exit quadrants.
     */
    public static List<CombatMotionProfile> getExpandedMotionPool(AnimationArchetype archetype, Item weapon) {
        List<CombatMotionProfile> pool = new ArrayList<>();
        switch (archetype) {
            case SLASHING_2H: {
                // 1. Colossal Sweep (TR -> BL)
                CombatMotionProfile p1 = new CombatMotionProfile();
                p1.comboName = "COLOSSAL SWEEP";
                p1.entryQuadrant = Quadrant.TOP_RIGHT;
                p1.exitQuadrant = Quadrant.BOTTOM_LEFT;
                p1.startRotation = -75f; p1.endRotation = 65f;
                p1.startXRel = 0.85f; p1.endXRel = 0.15f;
                p1.duration = 0.40f; p1.damageMultiplier = 1.10f; p1.screenTrauma = 0.30f;
                pool.add(p1);

                // 2. Low Cleave (BL -> TR)
                CombatMotionProfile p2 = new CombatMotionProfile();
                p2.comboName = "LOW SWEEP";
                p2.entryQuadrant = Quadrant.BOTTOM_LEFT;
                p2.exitQuadrant = Quadrant.TOP_RIGHT;
                p2.startRotation = 65f; p2.endRotation = -60f;
                p2.startXRel = 0.18f; p2.endXRel = 0.82f;
                p2.duration = 0.38f; p2.damageMultiplier = 1.15f; p2.screenTrauma = 0.32f;
                pool.add(p2);

                // 3. Cresting Lop (TL -> BR)
                CombatMotionProfile p3 = new CombatMotionProfile();
                p3.comboName = "CRESTING LOP";
                p3.entryQuadrant = Quadrant.TOP_LEFT;
                p3.exitQuadrant = Quadrant.BOTTOM_RIGHT;
                p3.startRotation = -50f; p3.endRotation = 55f;
                p3.startXRel = 0.20f; p3.endXRel = 0.80f;
                p3.duration = 0.38f; p3.damageMultiplier = 1.15f; p3.screenTrauma = 0.30f;
                pool.add(p3);

                // 4. Heavy Cleave (BR -> TL)
                CombatMotionProfile p4 = new CombatMotionProfile();
                p4.comboName = "HEAVY CLEAVE";
                p4.entryQuadrant = Quadrant.BOTTOM_RIGHT;
                p4.exitQuadrant = Quadrant.TOP_LEFT;
                p4.startRotation = 55f; p4.endRotation = -55f;
                p4.startXRel = 0.80f; p4.endXRel = 0.22f;
                p4.duration = 0.39f; p4.damageMultiplier = 1.20f; p4.screenTrauma = 0.35f;
                pool.add(p4);

                // 5. Iron Bind (CENTER -> TR)
                CombatMotionProfile p5 = new CombatMotionProfile();
                p5.comboName = "IRON BIND";
                p5.entryQuadrant = Quadrant.CENTER;
                p5.exitQuadrant = Quadrant.TOP_RIGHT;
                p5.startRotation = -10f; p5.endRotation = -60f;
                p5.startXRel = 0.50f; p5.endXRel = 0.80f;
                p5.duration = 0.34f; p5.damageMultiplier = 1.05f; p5.screenTrauma = 0.25f;
                pool.add(p5);

                // 6. Overhead Reap (TR -> CENTER)
                CombatMotionProfile p6 = new CombatMotionProfile();
                p6.comboName = "OVERHEAD REAP";
                p6.entryQuadrant = Quadrant.TOP_RIGHT;
                p6.exitQuadrant = Quadrant.CENTER;
                p6.startRotation = -15f; p6.endRotation = 20f;
                p6.startXRel = 0.54f; p6.endXRel = 0.48f;
                p6.startYRel = 0.32f; p6.endYRel = -0.15f;
                p6.duration = 0.42f; p6.damageMultiplier = 1.25f; p6.screenTrauma = 0.40f;
                pool.add(p6);

                // 7. Titan's Cleave (Finisher)
                CombatMotionProfile fin = new CombatMotionProfile();
                fin.comboName = "TITAN'S CLEAVE";
                fin.isFinisher = true;
                fin.entryQuadrant = Quadrant.TOP_RIGHT;
                fin.exitQuadrant = Quadrant.CENTER;
                fin.startRotation = -20f; fin.endRotation = 25f;
                fin.startXRel = 0.52f; fin.endXRel = 0.48f;
                fin.startYRel = 0.38f; fin.endYRel = -0.20f;
                fin.duration = 0.46f; fin.damageMultiplier = 1.50f; fin.screenTrauma = 0.60f;
                pool.add(fin);
                break;
            }

            case AXE_CHOPPING: {
                // 1. Overhead Chop (TR -> CENTER)
                CombatMotionProfile p1 = new CombatMotionProfile();
                p1.comboName = "OVERHEAD CHOP";
                p1.entryQuadrant = Quadrant.TOP_RIGHT; p1.exitQuadrant = Quadrant.CENTER;
                p1.startRotation = -18f; p1.endRotation = 12f;
                p1.startXRel = 0.62f; p1.endXRel = 0.54f; p1.startYRel = 0.28f; p1.endYRel = -0.16f;
                p1.duration = 0.32f; p1.damageMultiplier = 1.05f; p1.screenTrauma = 0.24f;
                pool.add(p1);

                // 2. Diagonal Hack (TR -> BL)
                CombatMotionProfile p2 = new CombatMotionProfile();
                p2.comboName = "DIAGONAL HACK";
                p2.entryQuadrant = Quadrant.TOP_RIGHT; p2.exitQuadrant = Quadrant.BOTTOM_LEFT;
                p2.startRotation = -55f; p2.endRotation = 35f;
                p2.startXRel = 0.78f; p2.endXRel = 0.32f; p2.startYRel = 0.15f; p2.endYRel = -0.12f;
                p2.duration = 0.30f; p2.damageMultiplier = 1.15f; p2.screenTrauma = 0.28f;
                pool.add(p2);

                // 3. Hooking Cleave (BL -> TR)
                CombatMotionProfile p3 = new CombatMotionProfile();
                p3.comboName = "HOOKING CLEAVE";
                p3.entryQuadrant = Quadrant.BOTTOM_LEFT; p3.exitQuadrant = Quadrant.TOP_RIGHT;
                p3.startRotation = 50f; p3.endRotation = -50f;
                p3.startXRel = 0.22f; p3.endXRel = 0.80f; p3.startYRel = -0.06f; p3.endYRel = -0.04f;
                p3.duration = 0.34f; p3.damageMultiplier = 1.20f; p3.screenTrauma = 0.32f;
                pool.add(p3);

                // 4. Woodsman's Notch (TL -> BR)
                CombatMotionProfile p4 = new CombatMotionProfile();
                p4.comboName = "WOODSMAN'S NOTCH";
                p4.entryQuadrant = Quadrant.TOP_LEFT; p4.exitQuadrant = Quadrant.BOTTOM_RIGHT;
                p4.startRotation = -40f; p4.endRotation = 45f;
                p4.startXRel = 0.28f; p4.endXRel = 0.74f;
                p4.duration = 0.31f; p4.damageMultiplier = 1.10f; p4.screenTrauma = 0.25f;
                pool.add(p4);

                // 5. Timber Hew (BR -> TL)
                CombatMotionProfile p5 = new CombatMotionProfile();
                p5.comboName = "TIMBER HEW";
                p5.entryQuadrant = Quadrant.BOTTOM_RIGHT; p5.exitQuadrant = Quadrant.TOP_LEFT;
                p5.startRotation = 45f; p5.endRotation = -40f;
                p5.startXRel = 0.74f; p5.endXRel = 0.28f;
                p5.duration = 0.32f; p5.damageMultiplier = 1.15f; p5.screenTrauma = 0.28f;
                pool.add(p5);

                // 6. Side Chop (CENTER -> TR)
                CombatMotionProfile p6 = new CombatMotionProfile();
                p6.comboName = "SIDE CHOP";
                p6.entryQuadrant = Quadrant.CENTER; p6.exitQuadrant = Quadrant.TOP_RIGHT;
                p6.startRotation = 0f; p6.endRotation = -45f;
                p6.startXRel = 0.50f; p6.endXRel = 0.78f;
                p6.duration = 0.28f; p6.damageMultiplier = 1.05f; p6.screenTrauma = 0.22f;
                pool.add(p6);

                // 7. Cleaving Hook (Finisher)
                CombatMotionProfile fin = new CombatMotionProfile();
                fin.comboName = "CLEAVING HOOK";
                fin.isFinisher = true;
                fin.entryQuadrant = Quadrant.TOP_RIGHT; fin.exitQuadrant = Quadrant.CENTER;
                fin.startRotation = -30f; fin.endRotation = 30f;
                fin.startXRel = 0.58f; fin.endXRel = 0.46f; fin.startYRel = 0.32f; fin.endYRel = -0.18f;
                fin.duration = 0.40f; fin.damageMultiplier = 1.45f; fin.screenTrauma = 0.50f;
                pool.add(fin);
                break;
            }

            case BLUNT_CRUSHING: {
                // 1. Heavy Batter (TR -> BL)
                CombatMotionProfile p1 = new CombatMotionProfile();
                p1.comboName = "HEAVY BATTER";
                p1.entryQuadrant = Quadrant.TOP_RIGHT; p1.exitQuadrant = Quadrant.BOTTOM_LEFT;
                p1.startRotation = -65f; p1.endRotation = 40f;
                p1.startXRel = 0.80f; p1.endXRel = 0.28f;
                p1.duration = 0.34f; p1.damageMultiplier = 1.05f; p1.screenTrauma = 0.24f;
                pool.add(p1);

                // 2. Horizontal Crush (BL -> TR)
                CombatMotionProfile p2 = new CombatMotionProfile();
                p2.comboName = "HORIZONTAL CRUSH";
                p2.entryQuadrant = Quadrant.BOTTOM_LEFT; p2.exitQuadrant = Quadrant.TOP_RIGHT;
                p2.startRotation = 50f; p2.endRotation = -45f;
                p2.startXRel = 0.25f; p2.endXRel = 0.75f;
                p2.duration = 0.32f; p2.damageMultiplier = 1.15f; p2.screenTrauma = 0.28f;
                pool.add(p2);

                // 3. Rising Kinetic Thump (BL -> CENTER)
                CombatMotionProfile p3 = new CombatMotionProfile();
                p3.comboName = "RISING KINETIC THUMP";
                p3.entryQuadrant = Quadrant.BOTTOM_LEFT; p3.exitQuadrant = Quadrant.CENTER;
                p3.startRotation = 40f; p3.endRotation = 0f;
                p3.startXRel = 0.35f; p3.endXRel = 0.50f; p3.startYRel = -0.15f; p3.endYRel = 0.10f;
                p3.duration = 0.30f; p3.damageMultiplier = 1.10f; p3.screenTrauma = 0.26f;
                pool.add(p3);

                // 4. Ankle Smash (TL -> BR)
                CombatMotionProfile p4 = new CombatMotionProfile();
                p4.comboName = "ANKLE SMASH";
                p4.entryQuadrant = Quadrant.TOP_LEFT; p4.exitQuadrant = Quadrant.BOTTOM_RIGHT;
                p4.startRotation = -45f; p4.endRotation = 40f;
                p4.startXRel = 0.30f; p4.endXRel = 0.70f; p4.startYRel = 0.10f; p4.endYRel = -0.14f;
                p4.duration = 0.32f; p4.damageMultiplier = 1.15f; p4.screenTrauma = 0.30f;
                pool.add(p4);

                // 5. Concussion Tap (CENTER -> TR)
                CombatMotionProfile p5 = new CombatMotionProfile();
                p5.comboName = "CONCUSSION TAP";
                p5.entryQuadrant = Quadrant.CENTER; p5.exitQuadrant = Quadrant.TOP_RIGHT;
                p5.startRotation = 5f; p5.endRotation = -40f;
                p5.startXRel = 0.50f; p5.endXRel = 0.75f;
                p5.duration = 0.28f; p5.damageMultiplier = 1.05f; p5.screenTrauma = 0.22f;
                pool.add(p5);

                // 6. Kinetic Hammer (BR -> TL)
                CombatMotionProfile p6 = new CombatMotionProfile();
                p6.comboName = "KINETIC HAMMER";
                p6.entryQuadrant = Quadrant.BOTTOM_RIGHT; p6.exitQuadrant = Quadrant.TOP_LEFT;
                p6.startRotation = 40f; p6.endRotation = -40f;
                p6.startXRel = 0.72f; p6.endXRel = 0.30f;
                p6.duration = 0.33f; p6.damageMultiplier = 1.18f; p6.screenTrauma = 0.32f;
                pool.add(p6);

                // 7. Skull Crusher (Finisher)
                CombatMotionProfile fin = new CombatMotionProfile();
                fin.comboName = "SKULL CRUSHER";
                fin.isFinisher = true;
                fin.entryQuadrant = Quadrant.TOP_RIGHT; fin.exitQuadrant = Quadrant.CENTER;
                fin.startRotation = -15f; fin.endRotation = 15f;
                fin.startXRel = 0.52f; fin.endXRel = 0.50f; fin.startYRel = 0.32f; fin.endYRel = -0.18f;
                fin.duration = 0.42f; fin.damageMultiplier = 1.45f; fin.screenTrauma = 0.55f;
                pool.add(fin);
                break;
            }

            case THRUSTING_PIERCE: {
                // 1. Lunging Stab (CENTER -> CENTER)
                pool.add(createThrustStrike(0, "LUNGING STAB", 1.0f, 0.12f));

                // 2. Rising Puncture (BL -> TR)
                CombatMotionProfile p2 = createThrustStrike(1, "RISING PUNCTURE", 1.10f, 0.15f);
                p2.entryQuadrant = Quadrant.BOTTOM_LEFT; p2.exitQuadrant = Quadrant.TOP_RIGHT;
                p2.startRotation = 15f; p2.endRotation = -15f;
                p2.startXRel = 0.40f; p2.endXRel = 0.65f;
                pool.add(p2);

                // 3. Throat Pierce (TR -> CENTER)
                CombatMotionProfile p3 = createThrustStrike(2, "THROAT PIERCE", 1.15f, 0.18f);
                p3.entryQuadrant = Quadrant.TOP_RIGHT; p3.exitQuadrant = Quadrant.CENTER;
                p3.startRotation = -25f; p3.endRotation = -10f;
                pool.add(p3);

                // 4. Kidney Pierce (BR -> TL)
                CombatMotionProfile p4 = createThrustStrike(3, "KIDNEY PIERCE", 1.18f, 0.20f);
                p4.entryQuadrant = Quadrant.BOTTOM_RIGHT; p4.exitQuadrant = Quadrant.TOP_LEFT;
                pool.add(p4);

                // 5. Probing Dart (TL -> CENTER)
                CombatMotionProfile p5 = createThrustStrike(4, "PROBING DART", 1.05f, 0.12f);
                p5.entryQuadrant = Quadrant.TOP_LEFT; p5.exitQuadrant = Quadrant.CENTER;
                pool.add(p5);

                // 6. Swift Point (CENTER -> TR)
                CombatMotionProfile p6 = createThrustStrike(5, "SWIFT POINT", 1.12f, 0.15f);
                p6.entryQuadrant = Quadrant.CENTER; p6.exitQuadrant = Quadrant.TOP_RIGHT;
                pool.add(p6);

                // 7. Impaling Skewer (Finisher)
                CombatMotionProfile fin = createThrustStrike(6, "IMPALING SKEWER", 1.45f, 0.45f);
                fin.isFinisher = true;
                fin.duration = 0.32f;
                fin.entryQuadrant = Quadrant.CENTER; fin.exitQuadrant = Quadrant.CENTER;
                pool.add(fin);
                break;
            }

            case BRAWLING: {
                pool.add(createBrawlStrike(0, "LEFT JAB", 1.0f, 0.10f, 0.35f, 0.48f));
                CombatMotionProfile cross = createBrawlStrike(1, "RIGHT CROSS", 1.10f, 0.15f, 0.65f, 0.50f);
                cross.entryQuadrant = Quadrant.TOP_RIGHT; cross.exitQuadrant = Quadrant.BOTTOM_LEFT;
                pool.add(cross);

                CombatMotionProfile hook = createBrawlStrike(2, "LEFT HOOK", 1.20f, 0.20f, 0.30f, 0.52f);
                hook.entryQuadrant = Quadrant.BOTTOM_LEFT; hook.exitQuadrant = Quadrant.TOP_RIGHT;
                pool.add(hook);

                CombatMotionProfile body = createBrawlStrike(3, "LEAD BODY BLOW", 1.15f, 0.18f, 0.40f, 0.55f);
                body.entryQuadrant = Quadrant.BOTTOM_RIGHT; body.exitQuadrant = Quadrant.TOP_LEFT;
                pool.add(body);

                CombatMotionProfile palm = createBrawlStrike(4, "KINETIC PALM", 1.12f, 0.15f, 0.55f, 0.45f);
                palm.entryQuadrant = Quadrant.CENTER; palm.exitQuadrant = Quadrant.CENTER;
                pool.add(palm);

                CombatMotionProfile elbow = createBrawlStrike(5, "ELBOW SMASH", 1.25f, 0.25f, 0.60f, 0.42f);
                elbow.entryQuadrant = Quadrant.TOP_RIGHT; elbow.exitQuadrant = Quadrant.CENTER;
                pool.add(elbow);

                CombatMotionProfile uppercut = createBrawlStrike(6, "HAYMAKER UPPERCUT", 1.45f, 0.50f, 0.50f, 0.50f);
                uppercut.isFinisher = true;
                uppercut.entryQuadrant = Quadrant.BOTTOM_LEFT; uppercut.exitQuadrant = Quadrant.CENTER;
                pool.add(uppercut);
                break;
            }

            case SLASHING_1H:
            default: {
                // 1. Diagonal Cleave (TR -> BL)
                CombatMotionProfile p1 = createSlashingStrike(0, false, "DIAGONAL CLEAVE", 1.0f, 0.15f);
                p1.entryQuadrant = Quadrant.TOP_RIGHT; p1.exitQuadrant = Quadrant.BOTTOM_LEFT;
                pool.add(p1);

                // 2. Rising Backslash (BL -> TR)
                CombatMotionProfile p2 = createSlashingStrike(1, true, "RISING BACKSLASH", 1.15f, 0.20f);
                p2.entryQuadrant = Quadrant.BOTTOM_LEFT; p2.exitQuadrant = Quadrant.TOP_RIGHT;
                pool.add(p2);

                // 3. Horizon Cut (TL -> TR)
                CombatMotionProfile p3 = new CombatMotionProfile();
                p3.comboName = "HORIZON CUT";
                p3.entryQuadrant = Quadrant.TOP_LEFT; p3.exitQuadrant = Quadrant.TOP_RIGHT;
                p3.startRotation = -50f; p3.endRotation = 50f;
                p3.startXRel = 0.22f; p3.endXRel = 0.78f;
                p3.duration = 0.30f; p3.damageMultiplier = 1.15f; p3.screenTrauma = 0.22f;
                pool.add(p3);

                // 4. Crescent Carve (BR -> TL)
                CombatMotionProfile p4 = new CombatMotionProfile();
                p4.comboName = "CRESCENT CARVE";
                p4.entryQuadrant = Quadrant.BOTTOM_RIGHT; p4.exitQuadrant = Quadrant.TOP_LEFT;
                p4.startRotation = 55f; p4.endRotation = -55f;
                p4.startXRel = 0.78f; p4.endXRel = 0.22f;
                p4.duration = 0.31f; p4.damageMultiplier = 1.15f; p4.screenTrauma = 0.24f;
                pool.add(p4);

                // 5. Sidestep Gash (BL -> TL)
                CombatMotionProfile p5 = new CombatMotionProfile();
                p5.comboName = "SIDESTEP GASH";
                p5.entryQuadrant = Quadrant.BOTTOM_LEFT; p5.exitQuadrant = Quadrant.TOP_LEFT;
                p5.startRotation = 45f; p5.endRotation = -35f;
                p5.startXRel = 0.30f; p5.endXRel = 0.35f; p5.startYRel = -0.12f; p5.endYRel = 0.15f;
                p5.duration = 0.28f; p5.damageMultiplier = 1.10f; p5.screenTrauma = 0.20f;
                pool.add(p5);

                // 6. Swift Pommel Strike (CENTER -> TR)
                CombatMotionProfile p6 = new CombatMotionProfile();
                p6.comboName = "SWIFT POMMEL STRIKE";
                p6.entryQuadrant = Quadrant.CENTER; p6.exitQuadrant = Quadrant.TOP_RIGHT;
                p6.startRotation = -10f; p6.endRotation = -40f;
                p6.startXRel = 0.50f; p6.endXRel = 0.72f;
                p6.duration = 0.25f; p6.damageMultiplier = 1.05f; p6.screenTrauma = 0.18f;
                pool.add(p6);

                // 7. Executioner's Slam (Finisher)
                CombatMotionProfile fin = createSlashingStrike(2, false, "EXECUTIONER'S SLAM", 1.45f, 0.45f);
                fin.isFinisher = true;
                fin.entryQuadrant = Quadrant.TOP_RIGHT; fin.exitQuadrant = Quadrant.CENTER;
                pool.add(fin);
                break;
            }
        }
        return pool;
    }

    /**
     * Resolves the next physically continuous strike in the combo chain using quadrant matching.
     */
    public static CombatMotionProfile getNextContinuousStrike(
            AnimationArchetype archetype,
            Item weapon,
            CombatMotionProfile previousStrike,
            boolean isFinisher,
            boolean isCrit,
            boolean isDualWielding,
            boolean wasDeflected
    ) {
        // 1. Armor / Shield Deflection Riposte
        if (wasDeflected) {
            CombatMotionProfile riposte = new CombatMotionProfile();
            riposte.comboName = "DEFLECTION RIPOSTE";
            riposte.isRiposte = true;
            riposte.duration = 0.24f;
            riposte.damageMultiplier = 1.25f;
            riposte.screenTrauma = 0.30f;
            riposte.anticipationRatio = 0.15f;
            riposte.impactRatio = 0.32f;
            riposte.startRotation = 20f;
            riposte.endRotation = -40f;
            riposte.startXRel = 0.38f;
            riposte.endXRel = 0.65f;
            riposte.startYRel = 0.05f;
            riposte.endYRel = -0.06f;
            riposte.entryQuadrant = (previousStrike != null) ? previousStrike.entryQuadrant : Quadrant.CENTER;
            riposte.exitQuadrant = Quadrant.CENTER;
            return riposte;
        }

        List<CombatMotionProfile> pool = getExpandedMotionPool(archetype, weapon);
        if (pool.isEmpty()) {
            return createSlashingStrike(0, false, "STRIKE", 1.0f, 0.15f);
        }

        // 2. Critical Strike or Fatal Finisher
        if (isFinisher || isCrit) {
            for (CombatMotionProfile p : pool) {
                if (p.isFinisher) {
                    CombatMotionProfile fin = p.cpy();
                    fin.comboStep = (previousStrike != null) ? previousStrike.comboStep + 1 : 2;
                    fin.isFinisher = true;
                    fin.isCrit = isCrit;
                    if (isCrit) {
                        fin.damageMultiplier = Math.max(fin.damageMultiplier, 1.50f);
                        fin.screenTrauma = Math.max(fin.screenTrauma, 0.60f);
                        String critName = SpecialMoveRegistry.resolveMoveName(fin, weapon, true);
                        if (critName != null && !critName.equals("STRIKE")) {
                            fin.comboName = critName;
                        }
                    }
                    return fin;
                }
            }
        }

        // 3. Dual Wield alternating strike
        if (isDualWielding && previousStrike != null && !previousStrike.isOffHand) {
            CombatMotionProfile offhand = createSlashingStrike(previousStrike.comboStep + 1, true, "OFFHAND CROSS", 1.15f, 0.20f);
            offhand.isOffHand = true;
            offhand.startXRel = 0.25f;
            offhand.endXRel = 0.75f;
            offhand.startRotation = 60f;
            offhand.endRotation = -50f;
            offhand.entryQuadrant = Quadrant.BOTTOM_LEFT;
            offhand.exitQuadrant = Quadrant.TOP_RIGHT;
            return offhand;
        }

        // 4. Opener (no prior strike)
        if (previousStrike == null) {
            for (CombatMotionProfile p : pool) {
                if (!p.isFinisher && (p.entryQuadrant == Quadrant.TOP_RIGHT || p.entryQuadrant == Quadrant.CENTER)) {
                    CombatMotionProfile opener = p.cpy();
                    opener.comboStep = 0;
                    return opener;
                }
            }
            CombatMotionProfile fallback = pool.get(0).cpy();
            fallback.comboStep = 0;
            return fallback;
        }

        // 5. Quadrant Matching: find strikes whose entryQuadrant matches previousStrike.exitQuadrant
        List<CombatMotionProfile> candidates = new ArrayList<>();
        for (CombatMotionProfile p : pool) {
            if (!p.isFinisher && p.entryQuadrant == previousStrike.exitQuadrant) {
                candidates.add(p);
            }
        }

        if (!candidates.isEmpty()) {
            int nextStep = previousStrike.comboStep + 1;
            int idx = nextStep % candidates.size();
            CombatMotionProfile next = candidates.get(idx).cpy();
            next.comboStep = nextStep;
            return next;
        }

        // Fallback: pick any non-finisher from center or first entry
        for (CombatMotionProfile p : pool) {
            if (!p.isFinisher) {
                CombatMotionProfile next = p.cpy();
                next.comboStep = previousStrike.comboStep + 1;
                return next;
            }
        }

        CombatMotionProfile fallback = pool.get(0).cpy();
        fallback.comboStep = previousStrike.comboStep + 1;
        return fallback;
    }
}
