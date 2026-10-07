package com.bpm.minotaur.gamedata.shelter;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.generation.ShelterRoads;
import com.bpm.minotaur.managers.BiomeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Which beacons stand on the horizon from where the player is.
 *
 * <p>The renderer draws only the current chunk, so a shelter's smoke cannot be
 * world geometry; the sky draws each beacon at the real bearing instead. This
 * decides what the sky shows, and stays free of rendering so it can be tested.
 * See docs/DEsign/Requirements_ Shelter Roads.md, section 6.
 */
public final class BeaconPlanner {

    /** Smoke rises from a shelter not yet claimed; a claimed one glows steady; a seal site is a pillar. */
    public enum Kind { SMOKE, GLOW, PILLAR }

    public static final class Beacon {
        private final GridPoint2 chunk;
        private final float dx;
        private final float dy;
        private final Kind kind;
        private final Color color;
        private final boolean spent;

        Beacon(GridPoint2 chunk, float dx, float dy, Kind kind, Color color, boolean spent) {
            this.chunk = chunk;
            this.dx = dx;
            this.dy = dy;
            this.kind = kind;
            this.color = color;
            this.spent = spent;
        }

        public GridPoint2 getChunk() { return new GridPoint2(chunk); }
        /** From the player to the beacon, in chunks (x east, y north). */
        public float getDx() { return dx; }
        public float getDy() { return dy; }
        public Kind getKind() { return kind; }
        public Color getColor() { return new Color(color); }
        /** The road's seal is won: the beacon burns dimmed. */
        public boolean isSpent() { return spent; }
    }

    /** Closer than this, in chunks, the player is standing in the beacon's chunk. */
    private static final float HERE = 0.75f;

    private BeaconPlanner() {
    }

    /**
     * @param px the player's position in chunk space (a chunk's centre is its integer coordinate)
     */
    public static List<Beacon> visible(BiomeManager biomes, ShelterNetwork network, float px, float py, float range) {
        List<Beacon> out = new ArrayList<>();
        ShelterRoads roads = biomes == null ? null : biomes.getRoads();
        if (roads == null) return out;

        GridPoint2 center = new GridPoint2(Math.round(px), Math.round(py));
        for (ShelterRoads.Site site : roads.sheltersWithin(center, range + 1, biomes::isOpenLand)) {
            GridPoint2 c = site.getChunk();
            boolean spent = !site.isOffRoad() && network.hasSeal(site.getRoad());
            Kind kind = network.isClaimed(c) ? Kind.GLOW : Kind.SMOKE;
            add(out, c, px, py, range, kind, BeaconPalette.roadColor(site.getRoad()), spent);
        }
        for (ShelterRoads.Road road : roads.getRoads()) {
            if (road.getKind() != ShelterRoads.Kind.SEAL) continue;
            add(out, road.getEnd(), px, py, range, Kind.PILLAR, BeaconPalette.roadColor(road.getIndex()),
                    network.hasSeal(road.getIndex()));
        }
        return out;
    }

    private static void add(List<Beacon> out, GridPoint2 c, float px, float py, float range,
                            Kind kind, Color color, boolean spent) {
        float dx = c.x - px;
        float dy = c.y - py;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < HERE || d > range) return;
        out.add(new Beacon(c, dx, dy, kind, color, spent));
    }
}
