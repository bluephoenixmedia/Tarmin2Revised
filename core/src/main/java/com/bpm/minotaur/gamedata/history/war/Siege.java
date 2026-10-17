package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.favour.Favour;

import java.util.ArrayList;
import java.util.List;

/**
 * The siege of Castle Tarmin as the player meets it (Living War W33-W37): the fighting under its
 * walls, heard and seen from afar; the besieger's camps ringing the castle; and the breach in its
 * wall, which asks fewer seals of a friend of the besieger than of a stranger.
 */
public final class Siege {

    /** Seals the breach asks of a friend of, or one sworn to, the besieger (W35). */
    public static final int FRIEND_SEALS = 1;
    /** Seals it asks of anyone else; the gate itself still asks all three. */
    public static final int STRANGER_SEALS = 2;
    /** The level of Castle Tarmin's halls, beneath the castle (W36). */
    public static final int HALLS_LEVEL = 2;
    /** Chunks from the castle the besieger's camps stand. */
    static final int CAMP_RING = 2;

    private Siege() {
    }

    /** Seals the breach asks: one if the player is a friend of the besieger or sworn to it, else two. */
    public static int sealsForBreach(Favour.Tier besiegerStance, boolean swornToBesieger) {
        return swornToBesieger || besiegerStance == Favour.Tier.FRIEND ? FRIEND_SEALS : STRANGER_SEALS;
    }

    /** The besieger of the siege under way, or -1. */
    public static int besieger(HistoryWorld world) {
        War w = world == null ? null : world.activeSiege();
        return w == null || world.tarminHouse() == null ? -1 : w.enemyOf(world.tarminHouse().id);
    }

    /**
     * The fighting under the castle's walls while a siege holds (W34): heard and seen as smoke across
     * the overland, never walked into -- the castle is entered by its breach. Empty without a siege.
     */
    public static List<Encounter> at(HistoryWorld world, GridPoint2 castle) {
        List<Encounter> out = new ArrayList<>();
        War w = world == null ? null : world.activeSiege();
        if (w == null || castle == null || world.tarminHouse() == null) return out;
        out.add(new Encounter(Encounter.Kind.SIEGE, -3, w.id, w.enemyOf(world.tarminHouse().id), world.tarminHouse().id,
                0, Long.MAX_VALUE, castle, castle));
        return out;
    }

    /** The besieger's camps on each side of the castle (W34). */
    public static List<Encounter> camps(HistoryWorld world, GridPoint2 castle) {
        List<Encounter> out = new ArrayList<>();
        War w = world == null ? null : world.activeSiege();
        if (w == null || castle == null || world.tarminHouse() == null) return out;
        int besieger = w.enemyOf(world.tarminHouse().id);
        int[][] sides = {{CAMP_RING, 0}, {-CAMP_RING, 0}, {0, CAMP_RING}, {0, -CAMP_RING}};
        for (int[] d : sides) {
            GridPoint2 at = new GridPoint2(castle.x + d[0], castle.y + d[1]);
            out.add(new Encounter(Encounter.Kind.CAMP, -4, w.id, besieger, -1, 0, Long.MAX_VALUE, at, at));
        }
        return out;
    }
}
