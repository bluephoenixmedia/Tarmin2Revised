package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the war sounds like from where the player stands (Living War A53, A55, A56). Pure: given the
 * {@link WarSources} under way, the player's chunk, level and facing, it says how loud each looping
 * bed plays and from which side, and how likely each one-shot is this turn. With no source in range
 * it is silent: there is no ambient war.
 *
 * <p>Underground, only a battle overhead is heard, and a gash shakes only when told it does
 * ({@link WarSources#gashShakes}).
 */
public final class WarSoundscape {

    /** Chunks a fight is heard across (W9). */
    public static final int EARSHOT = 6;

    /** The looping beds, each a file under {@code sounds/war/}. */
    public enum Bed {
        NEAR("battle_near_loop"),
        FAR("battle_far_loop"),
        DRUMS("war_drums_loop"),
        UNDER("battle_underground_loop"),
        CAMP("camp_loop"),
        MARCH("march_loop"),
        CROWS("crows_loop");

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
        /** Underground: the stone shakes with it, and the lights tremble (W9). */
        public final boolean tremor;

        Shot(String key, float chance, float volume, float pan) {
            this(key, chance, volume, pan, false);
        }

        Shot(String key, float chance, float volume, float pan, boolean tremor) {
            this.key = key;
            this.chance = chance;
            this.volume = volume;
            this.pan = pan;
            this.tremor = tremor;
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
    static final float MARCH_NEAR = 0.45f;
    static final float DRUMS_FAINT = 0.15f;
    static final float PARTY_NEAR = 0.3f;
    static final float CAMP_HERE = 0.40f;
    static final float CAMP_NEXT = 0.20f;
    static final float CROWS_NEAR = 0.3f;
    static final float UNDER_CLOSE = 0.35f;
    static final float UNDER_FAR = 0.2f;
    static final float UNDER_GASH = 0.3f;
    /** Chunks overhead a fight is still felt through the stone. */
    static final int UNDER_REACH = 3;

    private WarSoundscape() {
    }

    /**
     * @param gashShakes the player is in a gash's interior and its seat is invested or assaulted, or a
     *                   battle is fought over it (A56)
     */
    public static Mix mix(WarSources sources, GridPoint2 player, int level, Direction facing, boolean gashShakes) {
        Mix m = new Mix();
        if (player == null) return m;
        boolean surface = level == 1;
        List<WarSources.Source> all = sources.all();
        for (int i = 0; i < all.size(); i++) {
            WarSources.Source src = all.get(i);
            int d = src.distance(player);
            if (!surface) {
                if (src.kind == WarSources.Kind.BATTLE) under(m, d);
                continue;
            }
            GridPoint2 at = new GridPoint2(src.x, src.y);
            float pan = pan(player, at, facing);
            switch (src.kind) {
                case BATTLE:
                    fight(m, d, pan);
                    break;
                case MARCH:
                    if (d <= 1) {
                        m.raise(Bed.DRUMS, MARCH_NEAR, pan);
                        m.raise(Bed.MARCH, MARCH_NEAR, pan);
                    } else if (d <= 3) {
                        m.raise(Bed.DRUMS, DRUMS_FAINT, pan);
                    }
                    break;
                case PARTY:
                case BURNING:
                    if (d <= 1) m.raise(Bed.MARCH, PARTY_NEAR, pan);
                    break;
                case CAMP:
                    camp(m, d, pan);
                    break;
                case INVESTED:
                    invested(m, d, pan);
                    break;
                case CASTLE_SIEGE:
                    siege(m, d, pan);
                    break;
                case AFTERMATH:
                    if (d <= 1 && src.strength > 0f) m.raise(Bed.CROWS, CROWS_NEAR, pan);
                    break;
                default:
                    break;
            }
        }
        if (!surface && gashShakes) {
            m.raise(Bed.UNDER, UNDER_GASH, 0f);
            tremor(m, "thud_distant_1", 0.05f, 0.5f);
        }
        return m;
    }

    private static void camp(Mix m, int d, float pan) {
        if (d == 0) m.raise(Bed.CAMP, CAMP_HERE, 0f);
        else if (d == 1) m.raise(Bed.CAMP, CAMP_NEXT, pan);
    }

    /** Camp noise and the works going up; farther off, the occasional thud and a horn. */
    private static void invested(Mix m, int d, float pan) {
        if (d <= 1) {
            camp(m, d, pan);
            shot(m, "thud_distant_1", 0.08f, 0.4f, pan);
        } else if (d <= 3) {
            shot(m, "thud_distant_2", 0.05f, 0.25f, pan);
            shot(m, "horn_battle", 0.02f, 0.2f, pan);
        }
    }

    /** A55's first row: steel, screams and volleys; then roar, drums and a horn; then a faint roar and a lone horn. */
    private static void fight(Mix m, int d, float pan) {
        if (d <= 1) {
            m.raise(Bed.NEAR, d == 0 ? NEAR_HERE : NEAR_NEXT, d == 0 ? 0f : pan);
            shot(m, "clash_sword", 0.15f, 0.5f, pan);
            shot(m, "volley_archers", 0.06f, 0.5f, pan);
            // Men crying out, and the beasts of the roster.
            shot(m, "monster_grunt_light", 0.08f, 0.45f, pan);
            shot(m, "monster_roar", 0.04f, 0.4f, pan);
        } else if (d <= 3) {
            m.raise(Bed.FAR, d == 2 ? FAR_2 : FAR_3, pan);
            m.raise(Bed.DRUMS, d == 2 ? DRUMS_2 : DRUMS_3, pan);
            shot(m, "horn_battle", 0.04f, d == 2 ? 0.4f : 0.3f, pan);
        } else if (d <= EARSHOT) {
            m.raise(Bed.FAR, FAR_EDGE, pan);
            shot(m, "horn_battle", 0.015f, 0.18f, pan);
        }
    }

    /** The siege of the castle (W34): its engines' barrage and fire, and the Legion's great horn. */
    private static void siege(Mix m, int d, float pan) {
        if (d > EARSHOT) return;
        float near = 1f - d / (float) (EARSHOT + 1);
        shot(m, "siege_barrage", 0.05f, 0.25f + 0.4f * near, pan);
        shot(m, "siege_bursts", 0.04f, 0.2f + 0.35f * near, pan);
        shot(m, "horn_great", 0.02f, 0.2f + 0.4f * near, pan);
    }

    private static void under(Mix m, int d) {
        if (d > UNDER_REACH) return;
        m.raise(Bed.UNDER, d <= 1 ? UNDER_CLOSE : UNDER_FAR, 0f);
        if (d <= 1) tremor(m, "thud_distant_1", 0.04f, 0.5f);
        else shot(m, "thud_distant_2", 0.04f, 0.3f, 0f);
    }

    /**
     * The horns at a battle's start and at its rout (A55): certain, out to earshot, on the surface.
     * Only what changes between one waking turn and the next: what changed in a sleep is news, not a sound.
     */
    public static final class BattleWatch {
        /** A longer jump than this was a sleep or a wait. */
        static final int AWAKE_TURNS = 5;

        private final java.util.Map<Integer, GridPoint2> going = new java.util.HashMap<>();
        private long lastClock = Long.MIN_VALUE;
        private final java.util.Set<Integer> watched = new java.util.HashSet<>();

        /** @param watchedBattle the battle the player is in, or -1: its own director blows its horns */
        public List<Shot> onTurn(WarSources sources, long clock, GridPoint2 player, int level, Direction facing, int watchedBattle) {
            if (watchedBattle >= 0) watched.add(watchedBattle);
            java.util.Map<Integer, GridPoint2> now = new java.util.HashMap<>();
            List<WarSources.Source> all = sources.all();
            for (int i = 0; i < all.size(); i++) {
                WarSources.Source s = all.get(i);
                if (s.kind == WarSources.Kind.BATTLE) now.put(s.id, new GridPoint2(s.x, s.y));
            }
            List<Shot> out = new ArrayList<>();
            if (lastClock != Long.MIN_VALUE && clock - lastClock <= AWAKE_TURNS && level == 1 && player != null) {
                for (java.util.Map.Entry<Integer, GridPoint2> e : now.entrySet()) {
                    if (!going.containsKey(e.getKey()) && !watched.contains(e.getKey())) horn(out, "horn_battle", e.getValue(), player, facing);
                }
                for (java.util.Map.Entry<Integer, GridPoint2> e : going.entrySet()) {
                    if (!now.containsKey(e.getKey()) && !watched.remove(e.getKey())) horn(out, "war_horn", e.getValue(), player, facing);
                }
            }
            going.clear();
            going.putAll(now);
            lastClock = clock;
            return out;
        }

        private static void horn(List<Shot> out, String key, GridPoint2 at, GridPoint2 player, Direction facing) {
            int d = Encounter.distance(player, at);
            if (d > EARSHOT) return;
            out.add(new Shot(key, 1f, 0.2f + 0.4f * (1f - d / (float) (EARSHOT + 1)), pan(player, at, facing)));
        }
    }

    /** A thud close enough overhead to shake the stone. */
    private static void tremor(Mix m, String key, float chance, float volume) {
        m.shots.removeIf(s -> s.key.equals(key));
        m.shots.add(new Shot(key, chance, volume, 0f, true));
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
