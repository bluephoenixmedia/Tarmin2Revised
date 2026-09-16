package com.bpm.minotaur.gamedata.monster;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Manages interpersonal relationships between Factions.
 * Generated procedurally per run seed to create unpredictable alliances and feuds.
 */
public class FactionMatrix {

    public enum Relation {
        HOSTILE,
        NEUTRAL,
        ALLIED
    }

    private final Map<Faction, Map<Faction, Relation>> matrix = new EnumMap<>(Faction.class);

    public FactionMatrix() {
        initDefault();
    }

    public FactionMatrix(long worldSeed) {
        initDefault();
        generateFromSeed(worldSeed);
    }

    private void initDefault() {
        matrix.clear();
        for (Faction a : Faction.values()) {
            Map<Faction, Relation> row = new EnumMap<>(Faction.class);
            for (Faction b : Faction.values()) {
                if (a == b) {
                    row.put(b, a == Faction.CHAOS_BERSERK ? Relation.HOSTILE : Relation.ALLIED);
                } else if (a == Faction.CHAOS_BERSERK || b == Faction.CHAOS_BERSERK) {
                    row.put(b, Relation.HOSTILE);
                } else if (a == Faction.NEUTRAL || b == Faction.NEUTRAL) {
                    row.put(b, Relation.NEUTRAL);
                } else {
                    row.put(b, Relation.HOSTILE); // Default to hostile until seeded
                }
            }
            matrix.put(a, row);
        }
    }

    /**
     * Seeds procedural relationships among the 5 core factions:
     * 60% Hostile, 25% Neutral, 15% Allied.
     */
    public void generateFromSeed(long seed) {
        Random rng = new Random(seed ^ 0x5F3759DF);
        Faction[] coreFactions = {
                Faction.TARMIN_LEGION,
                Faction.GOBLIN_CLANS,
                Faction.UNDEAD,
                Faction.BEASTS_AND_VERMIN,
                Faction.OUTCASTS_AND_HERMITS
        };

        for (int i = 0; i < coreFactions.length; i++) {
            for (int j = i + 1; j < coreFactions.length; j++) {
                Faction a = coreFactions[i];
                Faction b = coreFactions[j];

                float roll = rng.nextFloat();
                Relation rel;
                if (roll < 0.60f) {
                    rel = Relation.HOSTILE;
                } else if (roll < 0.85f) {
                    rel = Relation.NEUTRAL;
                } else {
                    rel = Relation.ALLIED;
                }

                setRelation(a, b, rel);
            }
        }
    }

    public void setRelation(Faction a, Faction b, Relation rel) {
        matrix.computeIfAbsent(a, k -> new EnumMap<>(Faction.class)).put(b, rel);
        matrix.computeIfAbsent(b, k -> new EnumMap<>(Faction.class)).put(a, rel);
    }

    public Relation getRelation(Faction a, Faction b) {
        if (a == null || b == null) return Relation.NEUTRAL;
        if (a == Faction.CHAOS_BERSERK || b == Faction.CHAOS_BERSERK) return Relation.HOSTILE;
        if (a == Faction.NEUTRAL || b == Faction.NEUTRAL) return Relation.NEUTRAL;
        if (a == b) return Relation.ALLIED;

        Map<Faction, Relation> row = matrix.get(a);
        if (row != null && row.containsKey(b)) {
            return row.get(b);
        }
        return Relation.HOSTILE;
    }

    public boolean isHostile(Faction a, Faction b) {
        return getRelation(a, b) == Relation.HOSTILE;
    }

    public boolean isAllied(Faction a, Faction b) {
        return getRelation(a, b) == Relation.ALLIED;
    }

    public boolean isNeutral(Faction a, Faction b) {
        return getRelation(a, b) == Relation.NEUTRAL;
    }

    /**
     * Serializes matrix into a compact string representation for save/load.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        Faction[] factions = Faction.values();
        for (int i = 0; i < factions.length; i++) {
            for (int j = i + 1; j < factions.length; j++) {
                sb.append(factions[i].name()).append(":")
                  .append(factions[j].name()).append(":")
                  .append(getRelation(factions[i], factions[j]).name()).append(";");
            }
        }
        return sb.toString();
    }

    /**
     * Restores matrix from serialized string.
     */
    public static FactionMatrix deserialize(String data) {
        FactionMatrix fm = new FactionMatrix();
        if (data == null || data.trim().isEmpty()) return fm;

        String[] pairs = data.split(";");
        for (String pair : pairs) {
            String[] parts = pair.split(":");
            if (parts.length == 3) {
                try {
                    Faction a = Faction.valueOf(parts[0]);
                    Faction b = Faction.valueOf(parts[1]);
                    Relation rel = Relation.valueOf(parts[2]);
                    fm.setRelation(a, b, rel);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return fm;
    }
}
