package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the war sounds like from where the player stands (Living War W9). Pure: given the
 * encounters and fronts under way, the player's chunk, level and facing, it says how loud each
 * looping bed plays and from which side, and how likely each one-shot is this turn.
 *
 * <p>On the surface: steel and screams from the next chunk; horns, drums and a roar of voices two
 * or three chunks off; a lone horn out to {@link EncounterScheduler#EARSHOT}. Underground: a rumble
 * through the stone from a fight overhead, and the gash's own war shaking its interior.
 */
public final class WarSoundscape {

    /** The looping beds, each a file under {@code sounds/war/}. */
    public enum Bed {
        NEAR("battle_near_loop"),
        FAR("battle_far_loop"),
        DRUMS("war_drums_loop"),
        UNDER("battle_underground_loop");

        public final String file;

        Bed(String file) {
            this.file = file;
        }
    }

    /** A one-shot that may sound this turn. */
    public static final class Shot {
        public final String key;
        public final float chance;
        public final float volume;
        public final float pan;

        Shot(String key, float chance, float volume, float pan) {
            this.key = key;
            this.chance = chance;
            this.volume = volume;
            this.pan = pan;
        }
    }

    /** One turn's mix. */
    public static final class Mix {
        public final Map<Bed, Float> volume = new EnumMap<>(Bed.class);
        public final Map<Bed, Float> pan = new EnumMap<>(Bed.class);
        public final List<Shot> shots = new ArrayList<>();

        public float volume(Bed bed) {
            Float v = volume.get(bed);
            return v == null ? 0f : v;
        }

        public float pan(Bed bed) {
            Float p = pan.get(bed);
            return p == null ? 0f : p;
        }

        void raise(Bed bed, float v, float p) {
            if (v > volume(bed)) {
                volume.put(bed, v);
                pan.put(bed, p);
            }
        }

        public boolean silent() {
            for (float v : volume.values()) if (v > 0f) return false;
            return shots.isEmpty();
        }
    }

    // Levels by distance (W9), before the effects setting.
    static final float NEAR_HERE = 0.55f;
    static final float NEAR_NEXT = 0.42f;
    static final float FAR_2 = 0.35f;
    static final float FAR_3 = 0.25f;
    static final float FAR_EDGE = 0.10f;
    static final float DRUMS_2 = 0.25f;
    static final float DRUMS_3 = 0.15f;
    static final float DRUMS_EDGE = 0.06f;
    static final float COLUMN_NEAR = 0.45f;
    static final float COLUMN_FAR = 0.2f;
    static final float UNDER_CLOSE = 0.35f;
    static final float UNDER_FAR = 0.2f;
    static final float UNDER_GASH = 0.3f;
    /** Chunks overhead a fight is still felt through the stone. */
    static final int UNDER_REACH = 3;

    private WarSoundscape() {
    }

    /**
     * @param gashAtWar the player is in a gash's interior and its holding house is at war: the
     *                  gash shakes whatever the distance (W9)
     */
    public static Mix mix(List<Encounter> encounters, List<Front> fronts, long clock, GridPoint2 player, int level,
            Direction facing, boolean gashAtWar) {
        Mix m = new Mix();
        if (player == null) return m;
        boolean surface = level == 1;
        if (encounters != null) {
            for (Encounter e : encounters) {
                if (e.kind == Encounter.Kind.CAMP) continue;
                GridPoint2 at = e.chunkAt(clock);
                int d = EncounterScheduler.distance(player, at);
                float pan = pan(player, at, facing);
                if (!surface) {
                    if (e.fighting()) under(m, d);
                } else if (e.fighting()) {
                    fight(m, d, pan);
                } else if (e.kind == Encounter.Kind.COLUMN) {
                    if (d <= 1) m.raise(Bed.DRUMS, COLUMN_NEAR, pan);
                    else if (d <= 3) m.raise(Bed.DRUMS, COLUMN_FAR, pan);
                }
            }
        }
        if (fronts != null) {
            for (Front f : fronts) {
                int d = Math.max(0, EncounterScheduler.distance(player, f.center) - Front.RADIUS);
                if (surface) fight(m, d, pan(player, f.center, facing));
                else under(m, d);
            }
        }
        if (!surface && gashAtWar) {
            m.raise(Bed.UNDER, UNDER_GASH, 0f);
            shot(m, "thud_distant_1", 0.05f, 0.5f, 0f);
        }
        return m;
    }

    private static void fight(Mix m, int d, float pan) {
        if (d <= 0) {
            m.raise(Bed.NEAR, NEAR_HERE, 0f);
            m.raise(Bed.DRUMS, DRUMS_3, 0f);
        } else if (d == 1) {
            m.raise(Bed.NEAR, NEAR_NEXT, pan);
            m.raise(Bed.FAR, FAR_3, pan);
            shot(m, "clash_sword", 0.15f, 0.5f, pan);
            shot(m, "volley_archers", 0.06f, 0.5f, pan);
            shot(m, "horn_battle", 0.03f, 0.45f, pan);
        } else if (d <= 3) {
            m.raise(Bed.FAR, d == 2 ? FAR_2 : FAR_3, pan);
            m.raise(Bed.DRUMS, d == 2 ? DRUMS_2 : DRUMS_3, pan);
            shot(m, "horn_battle", 0.04f, d == 2 ? 0.4f : 0.3f, pan);
        } else if (d <= EncounterScheduler.EARSHOT) {
            m.raise(Bed.FAR, FAR_EDGE, pan);
            m.raise(Bed.DRUMS, DRUMS_EDGE, pan);
            shot(m, "horn_battle", 0.015f, 0.18f, pan);
        }
    }

    private static void under(Mix m, int d) {
        if (d > UNDER_REACH) return;
        m.raise(Bed.UNDER, d <= 1 ? UNDER_CLOSE : UNDER_FAR, 0f);
        shot(m, d <= 1 ? "thud_distant_1" : "thud_distant_2", 0.04f, d <= 1 ? 0.5f : 0.3f, 0f);
    }

    /** Keeps the loudest of each one-shot. */
    private static void shot(Mix m, String key, float chance, float volume, float pan) {
        for (int i = 0; i < m.shots.size(); i++) {
            Shot s = m.shots.get(i);
            if (!s.key.equals(key)) continue;
            if (volume > s.volume) m.shots.set(i, new Shot(key, Math.max(chance, s.chance), volume, pan));
            return;
        }
        m.shots.add(new Shot(key, chance, volume, pan));
    }

    /**
     * Left (-1) to right (1) of the player's facing, toward chunk {@code at}; the same convention as
     * {@code SoundManager.playDirectionalSound}.
     */
    static float pan(GridPoint2 player, GridPoint2 at, Direction facing) {
        float dx = at.x - player.x;
        float dy = at.y - player.y;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 0.01f || facing == null) return 0f;
        float p;
        switch (facing) {
            case NORTH: p = dx / dist; break;
            case SOUTH: p = -dx / dist; break;
            case EAST: p = -dy / dist; break;
            default: p = dy / dist; break;
        }
        return Math.max(-1f, Math.min(1f, p));
    }
}
