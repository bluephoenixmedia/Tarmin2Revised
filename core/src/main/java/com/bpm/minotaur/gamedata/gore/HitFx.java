package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterFamily;
import com.bpm.minotaur.rendering.vfx.FxClipIds;

/**
 * Which effect a blow on a monster shows where it lands.
 *
 * <p>Something that bleeds spurts: a small burst for an ordinary hit, a large one for a critical
 * hit, a killing blow, or a blow that took a real share of its health. What does not bleed never
 * shows blood: the undead and spirits puff into smoke, constructs throw sparks, slimes smoke.
 *
 * <p>This is decided by what the creature is, not by its {@link GoreProfile}. A skeleton's profile
 * has blood, because its floor stains are marrow and that is the gore system's business; a
 * skeleton still does not spurt red when struck. The decals and gore particles stay the gore
 * system's; this is only the hit animation.
 */
public final class HitFx {

    /** A blow that takes this share of the monster's maximum health is a heavy one. */
    public static final float HEAVY_HIT_SHARE = 0.30f;

    /** Height above the floor of a monster's body, where a blow lands. */
    private static final float BODY_HEIGHT = 0.5f;

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

    public static Spec forHit(GoreProfile profile, MonsterFamily family, Monster.MonsterType type,
            float damageShare, boolean crit, boolean kill) {
        GoreProfile p = (profile != null) ? profile : GoreProfile.FLESH;

        if (type == Monster.MonsterType.IRON_GOLEM || type == Monster.MonsterType.GARGOYLE) {
            return new Spec(FxClipIds.HIT_SPARKS, 0.7f);
        }
        // With gore off nothing bleeds: a hit still lands, as a puff.
        if (!GoreLevel.current().enabled()
                || family == MonsterFamily.UNDEAD || p == GoreProfile.INCORPOREAL || p == GoreProfile.SLIME || !p.hasBlood) {
            return new Spec(FxClipIds.HIT_SMOKE, 0.9f);
        }
        boolean large = crit || kill || damageShare >= HEAVY_HIT_SHARE;
        return large ? new Spec(FxClipIds.BLOOD_LARGE, 1.0f) : new Spec(FxClipIds.BLOOD_SMALL, 0.55f);
    }

    /**
     * Where to place the effect for a monster standing at (tileX, tileY). The 3D world draws a
     * monster at z = -y and the animation renderer negates the z it is given, so the z stored here
     * is +y. Storing -y drew the effect on the mirrored tile.
     */
    public static Vector3 position(float tileX, float tileY) {
        return new Vector3(tileX, BODY_HEIGHT, tileY);
    }
}
