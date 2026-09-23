package com.bpm.minotaur.generation.theme;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The data half of a themed chunk: everything tunable without a rebuild.
 *
 * <p>Layout carving stays in Java because carving an arena is genuinely an
 * algorithm. Prop tables, spawn tables, hazard parameters, fog tint and the
 * entry stinger all live here, loaded from {@code assets/data/themes.json}.
 *
 * <p>See {@code docs/DEsign/Themed Chunk Contract.md} section 5.
 */
public class ThemeDefinition {

    /** A weighted entry in a prop table. */
    public static class PropEntry {
        public final String propId;
        public final int weight;

        public PropEntry(String propId, int weight) {
            this.propId = propId;
            this.weight = Math.max(1, weight);
        }
    }

    /** A weighted entry in an encounter table. */
    public static class MonsterEntry {
        public final MonsterType type;
        public final int weight;
        public final int hpBonus;

        public MonsterEntry(MonsterType type, int weight, int hpBonus) {
            this.type = type;
            this.weight = Math.max(1, weight);
            this.hpBonus = hpBonus;
        }
    }

    private final ChunkTheme theme;
    private final int crestAward;
    private final ThemeObjectiveKind objective;
    private final ThemeHazardKind hazard;
    private final float hazardDensity;
    private final Color fogTint;
    private final float fogDistance;
    private final String stinger;
    private final float propDensity;
    private final List<PropEntry> props;
    private final List<MonsterEntry> monsters;
    private final MonsterType championType;
    private final int championHpBonus;
    private final int objectiveCount;
    private final String runeTexture;

    public ThemeDefinition(ChunkTheme theme, int crestAward, ThemeObjectiveKind objective,
                           ThemeHazardKind hazard, float hazardDensity, Color fogTint,
                           float fogDistance, String stinger, float propDensity, List<PropEntry> props,
                           List<MonsterEntry> monsters, MonsterType championType,
                           int championHpBonus, int objectiveCount, String runeTexture) {
        this.theme = theme;
        this.crestAward = crestAward;
        this.objective = objective;
        this.hazard = hazard;
        this.hazardDensity = hazardDensity;
        this.fogTint = fogTint;
        this.fogDistance = fogDistance;
        this.stinger = stinger;
        this.propDensity = propDensity;
        this.props = props != null ? props : new ArrayList<PropEntry>();
        this.monsters = monsters != null ? monsters : new ArrayList<MonsterEntry>();
        this.championType = championType;
        this.championHpBonus = championHpBonus;
        this.objectiveCount = objectiveCount;
        this.runeTexture = runeTexture;
    }

    public ChunkTheme getTheme() {
        return theme;
    }

    /** Contract slot (f). */
    public int getCrestAward() {
        return crestAward;
    }

    /** Contract slot (e). */
    public ThemeObjectiveKind getObjective() {
        return objective;
    }

    /** Contract slot (c). */
    public ThemeHazardKind getHazard() {
        return hazard;
    }

    public float getHazardDensity() {
        return hazardDensity;
    }

    /** Contract slot (g). */
    public Color getFogTint() {
        return fogTint;
    }

    /**
     * Contract slot (g). Sight range inside this theme, or 0 to keep the
     * biome's. The Graveyard's fog is meant to shorten sight, not merely tint
     * it; a recoloured fog at unchanged range is decoration, not atmosphere.
     */
    public float getFogDistance() {
        return fogDistance;
    }

    /** Contract slot (g). Path to the one-shot sound played on entry. */
    public String getStinger() {
        return stinger;
    }

    public float getPropDensity() {
        return propDensity;
    }

    /** Contract slot (b). */
    public List<PropEntry> getProps() {
        return Collections.unmodifiableList(props);
    }

    /** Contract slot (d). */
    public List<MonsterEntry> getMonsters() {
        return Collections.unmodifiableList(monsters);
    }

    /** Null unless the objective is SLAY_CHAMPION. */
    public MonsterType getChampionType() {
        return championType;
    }

    public int getChampionHpBonus() {
        return championHpBonus;
    }

    /** How many graves to reconsecrate, caches to open, and so on. */
    public int getObjectiveCount() {
        return objectiveCount;
    }

    /** Path to the authored rune sigil PNG shown above the gate. */
    public String getRuneTexture() {
        return runeTexture;
    }

    /** Weighted draw from the prop table. Returns null when the table is empty. */
    public String rollProp(Random rng) {
        return rollWeighted(props, rng);
    }

    /** Weighted draw from the encounter table. Returns null when empty. */
    public MonsterEntry rollMonster(Random rng) {
        if (monsters.isEmpty()) return null;
        int total = 0;
        for (MonsterEntry e : monsters) total += e.weight;
        int roll = rng.nextInt(total);
        for (MonsterEntry e : monsters) {
            roll -= e.weight;
            if (roll < 0) return e;
        }
        return monsters.get(monsters.size() - 1);
    }

    private static String rollWeighted(List<PropEntry> table, Random rng) {
        if (table.isEmpty()) return null;
        int total = 0;
        for (PropEntry e : table) total += e.weight;
        int roll = rng.nextInt(total);
        for (PropEntry e : table) {
            roll -= e.weight;
            if (roll < 0) return e.propId;
        }
        return table.get(table.size() - 1).propId;
    }
}
