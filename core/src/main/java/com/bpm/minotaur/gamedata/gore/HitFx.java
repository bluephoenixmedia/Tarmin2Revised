package com.bpm.minotaur.gamedata.gore;

import com.bpm.minotaur.rendering.vfx.FxClipIds;

/**
 * Which effect a blow on a monster shows where it lands.
 *
 * <p>Something that bleeds spurts: a small burst for an ordinary hit, a large one for a critical
 * hit, a killing blow, or a blow that took a real share of its health. Something that does not
 * bleed (a ghost, a golem, a slime) never shows blood: spirits puff into smoke and everything else
 * throws sparks. The floor stains and gore particles are the gore system's; this is only the hit.
 */
public final class HitFx {

    /** A blow that takes this share of the monster's maximum health is a heavy one. */
    public static final float HEAVY_HIT_SHARE = 0.30f;

    public static final class Spec {
        public final String clipId;
        /** Size of the effect, in tiles across. */
        public final float scale;

        Spec(String clipId, float scale) {
            this.clipId = clipId;
            this.scale = scale;
        }
    }

    private HitFx() {
    }

    public static Spec forHit(GoreProfile profile, float damageShare, boolean crit, boolean kill) {
        GoreProfile p = (profile != null) ? profile : GoreProfile.FLESH;
        if (!p.hasBlood) {
            return p == GoreProfile.INCORPOREAL
                    ? new Spec(FxClipIds.HIT_SMOKE, 0.9f)
                    : new Spec(FxClipIds.HIT_SPARKS, 0.7f);
        }
        boolean large = crit || kill || damageShare >= HEAVY_HIT_SHARE;
        return large ? new Spec(FxClipIds.BLOOD_LARGE, 1.0f) : new Spec(FxClipIds.BLOOD_SMALL, 0.55f);
    }
}
