package com.bpm.minotaur.gamedata.history.town;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * An underground settlement of mortals (plan D37, D40, T4.2): who it answers to, what it is
 * called, and the named folk who keep it. A town is a pure function of the history's seed and
 * where it stands, so it reads the same on every visit and every load.
 */
public final class Town {

    /** What a town's folk do; the merchant is the travelling merchant, seated here. */
    public enum Role {
        MERCHANT("Merchant"), SMITH("Smith"), INNKEEPER("Innkeeper"), QUESTGIVER("Reeve"), ELDER("Elder"), COMMONER("Townsfolk");

        public final String title;

        Role(String title) {
            this.title = title;
        }
    }

    /** One of a town's named folk. */
    public static final class Folk {
        public final int index;
        public final String name;
        public final Role role;
        /** A monster sprite to stand in for them until town art lands. */
        public final String sprite;

        Folk(int index, String name, Role role, String sprite) {
            this.index = index;
            this.name = name;
            this.role = role;
            this.sprite = sprite;
        }

        /** "Brenna the Smith". */
        public String title() {
            return role == Role.COMMONER ? name : name + " the " + role.title;
        }
    }

    public static final int MIN_FOLK = 4;
    public static final int MAX_FOLK = 8;

    private static final String[] PLACE_FIRST = {
            "Lantern", "Mire", "Candle", "Root", "Hollow", "Drip", "Ember", "Moss", "Cinder", "Shard", "Last", "Low"
    };
    private static final String[] PLACE_SECOND = {
            "hold", "deep", "warren", "rest", "hollow", "well", "steading", "reach", "gate", "haven"
    };

    /** "level:x:y": where it stands, and so which town it is. */
    public final String key;
    public final String name;
    public final Allegiance allegiance;
    public final List<Folk> folk;
    /** How the town regards a stranger before they have done anything: some towns shut their doors (D20). */
    public final int welcome;

    /** Chance a town greets strangers with a closed gate, and how cold it is. */
    static final int HOSTILE_PERCENT = 15;
    static final int WARY_PERCENT = 25;

    private Town(String key, String name, Allegiance allegiance, List<Folk> folk, int welcome) {
        this.key = key;
        this.name = name;
        this.allegiance = allegiance;
        this.folk = Collections.unmodifiableList(folk);
        this.welcome = welcome;
    }

    public static String keyOf(int level, int chunkX, int chunkY) {
        return level + ":" + chunkX + ":" + chunkY;
    }

    /** The town standing at {@code key}, as the history seeded with {@code historySeed} knows it. */
    public static Town of(long historySeed, String key) {
        Random rng = new Random(historySeed ^ (key.hashCode() * 0x9E3779B97F4A7C15L) ^ 0x70A7L);
        Allegiance allegiance = Allegiance.values()[rng.nextInt(Allegiance.values().length)];
        String name = PLACE_FIRST[rng.nextInt(PLACE_FIRST.length)] + PLACE_SECOND[rng.nextInt(PLACE_SECOND.length)];
        List<Folk> folk = new ArrayList<>();
        Role[] keepers = {Role.MERCHANT, Role.SMITH, Role.INNKEEPER, Role.QUESTGIVER, Role.ELDER};
        int count = MIN_FOLK + rng.nextInt(MAX_FOLK - MIN_FOLK + 1);
        for (int i = 0; i < count; i++) {
            Role role = i < keepers.length ? keepers[i] : Role.COMMONER;
            if (count < keepers.length && i == count - 1) role = Role.QUESTGIVER;
            folk.add(new Folk(i, allegiance.name(rng), role, allegiance.sprite(rng)));
        }
        int roll = rng.nextInt(100);
        int welcome = roll < HOSTILE_PERCENT ? Standing.HOSTILE - 15 : roll < HOSTILE_PERCENT + WARY_PERCENT ? -10 : 0;
        return new Town(key, name, allegiance, folk, welcome);
    }

    public Folk folk(Role role) {
        for (Folk f : folk) if (f.role == role) return f;
        return null;
    }
}
