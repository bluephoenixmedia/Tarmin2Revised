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
        MERCHANT("Merchant"), SMITH("Smith"), INNKEEPER("Innkeeper"), QUESTGIVER("Reeve"), ELDER("Elder"), COMMONER("Townsfolk"),
        /** A sworn sword of a fallen house, taken in (plan D37): a character of the history. */
        EXILE("Exile");

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
        /** The house figure this is (an exile), or -1. */
        public final int figureId;
        /** The settlement's mortal this is (ADR 0005), or -1 for a made-up town's folk. */
        public final int mortalId;

        Folk(int index, String name, Role role, String sprite) {
            this(index, name, role, sprite, -1, -1);
        }

        Folk(int index, String name, Role role, String sprite, int figureId, int mortalId) {
            this.index = index;
            this.name = name;
            this.role = role;
            this.sprite = sprite;
            this.figureId = figureId;
            this.mortalId = mortalId;
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
    /** Its settlement has betrayed the mortals to a house (plan T4.6): it turns on every stranger. */
    public final boolean betrayed;

    /** Chance a town greets strangers with a closed gate, and how cold it is. */
    static final int HOSTILE_PERCENT = 15;
    static final int WARY_PERCENT = 25;

    private Town(String key, String name, Allegiance allegiance, List<Folk> folk, int welcome, boolean betrayed) {
        this.key = key;
        this.name = name;
        this.allegiance = allegiance;
        this.folk = Collections.unmodifiableList(folk);
        this.welcome = welcome;
        this.betrayed = betrayed;
    }

    public static String keyOf(int level, int chunkX, int chunkY) {
        return level + ":" + chunkX + ":" + chunkY;
    }

    /**
     * A made-up town at {@code key}, for a town found once every settlement of the history is
     * spoken for (ADR 0005): the same every time, but its folk are no one the history knows.
     */
    public static Town of(long historySeed, String key) {
        Random rng = new Random(historySeed ^ (key.hashCode() * 0x9E3779B97F4A7C15L) ^ 0x70A7L);
        Allegiance allegiance = Allegiance.values()[rng.nextInt(Allegiance.values().length)];
        String name = placeName(rng);
        List<Folk> folk = new ArrayList<>();
        Role[] seats = seatsFor(rng);
        for (int i = 0; i < seats.length; i++) {
            folk.add(new Folk(i, allegiance.givenName(rng), seats[i], allegiance.folkSprite(rng)));
        }
        return new Town(key, name, allegiance, folk, welcomeRoll(rng), false);
    }

    /** The town at {@code key} that the history's settlement {@code s} is: its name and its living keepers. */
    public static Town of(com.bpm.minotaur.gamedata.history.HistoryWorld world, Settlement s, String key) {
        List<Folk> folk = new ArrayList<>();
        for (int seat = 0; seat < s.seats.length; seat++) {
            Mortal m = world.mortal(s.holders[seat]);
            folk.add(new Folk(seat, m.name, s.seats[seat], m.sprite, -1, m.id));
        }
        return new Town(key, s.name, s.allegiance, folk, s.welcome, s.betrayed);
    }

    /** A place name: "Lanternhold". */
    public static String placeName(Random rng) {
        return PLACE_FIRST[rng.nextInt(PLACE_FIRST.length)] + PLACE_SECOND[rng.nextInt(PLACE_SECOND.length)];
    }

    /** The seats of a town: the five keepers, then townsfolk; a small town's last seat is the reeve's. */
    public static Role[] seatsFor(Random rng) {
        Role[] keepers = {Role.MERCHANT, Role.SMITH, Role.INNKEEPER, Role.QUESTGIVER, Role.ELDER};
        int count = MIN_FOLK + rng.nextInt(MAX_FOLK - MIN_FOLK + 1);
        Role[] seats = new Role[count];
        for (int i = 0; i < count; i++) {
            seats[i] = i < keepers.length ? keepers[i] : Role.COMMONER;
            if (count < keepers.length && i == count - 1) seats[i] = Role.QUESTGIVER;
        }
        return seats;
    }

    /** How a town greets a stranger: most kindly, some warily, a few with a shut gate (D20). */
    public static int welcomeRoll(Random rng) {
        int roll = rng.nextInt(100);
        return roll < HOSTILE_PERCENT ? Standing.HOSTILE - 15 : roll < HOSTILE_PERCENT + WARY_PERCENT ? -10 : 0;
    }

    /** A hooded stand-in for a sword of the Maze living among mortals. */
    static final String EXILE_SPRITE = "CLOAKED_SKELETON";

    /** This town, having taken in the history's figure {@code figureId}, named {@code name}. */
    public Town withExile(int figureId, String name) {
        List<Folk> more = new ArrayList<>(folk);
        more.add(new Folk(folk.size(), name, Role.EXILE, EXILE_SPRITE, figureId, -1));
        return new Town(key, this.name, allegiance, more, welcome, betrayed);
    }

    public Folk folk(Role role) {
        for (Folk f : folk) if (f.role == role) return f;
        return null;
    }
}
