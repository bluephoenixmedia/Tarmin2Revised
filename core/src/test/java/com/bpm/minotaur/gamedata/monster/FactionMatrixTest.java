package com.bpm.minotaur.gamedata.monster;

import org.junit.Test;

import static org.junit.Assert.*;

public class FactionMatrixTest {

    @Test
    public void testDeterministicSeededGeneration() {
        long seed = 987654321L;
        FactionMatrix m1 = new FactionMatrix(seed);
        FactionMatrix m2 = new FactionMatrix(seed);

        for (Faction a : Faction.values()) {
            for (Faction b : Faction.values()) {
                assertEquals("Deterministic relationship mismatch for " + a + " and " + b,
                        m1.getRelation(a, b), m2.getRelation(a, b));
            }
        }
    }

    @Test
    public void testSymmetryAndReflexivity() {
        FactionMatrix matrix = new FactionMatrix(12345L);

        for (Faction a : Faction.values()) {
            if (a == Faction.CHAOS_BERSERK) {
                assertEquals(FactionMatrix.Relation.HOSTILE, matrix.getRelation(a, a));
                assertTrue(matrix.isHostile(a, a));
            } else if (a == Faction.NEUTRAL) {
                assertEquals(FactionMatrix.Relation.NEUTRAL, matrix.getRelation(a, a));
                assertFalse(matrix.isHostile(a, a));
            } else {
                assertEquals(FactionMatrix.Relation.ALLIED, matrix.getRelation(a, a));
                assertFalse(matrix.isHostile(a, a));
            }

            for (Faction b : Faction.values()) {
                // Symmetric: relationship(a, b) == relationship(b, a)
                assertEquals(matrix.getRelation(a, b), matrix.getRelation(b, a));
                assertEquals(matrix.isHostile(a, b), matrix.isHostile(b, a));
            }
        }
    }

    @Test
    public void testSerializationAndDeserialization() {
        FactionMatrix original = new FactionMatrix(55555L);
        String serialized = original.serialize();
        assertNotNull(serialized);
        assertFalse(serialized.isEmpty());

        FactionMatrix restored = FactionMatrix.deserialize(serialized);
        for (Faction a : Faction.values()) {
            for (Faction b : Faction.values()) {
                assertEquals(original.getRelation(a, b), restored.getRelation(a, b));
            }
        }
    }

    @Test
    public void testManualRelationshipOverride() {
        FactionMatrix matrix = new FactionMatrix(42L);
        matrix.setRelation(Faction.GOBLIN_CLANS, Faction.UNDEAD, FactionMatrix.Relation.ALLIED);

        assertEquals(FactionMatrix.Relation.ALLIED, matrix.getRelation(Faction.GOBLIN_CLANS, Faction.UNDEAD));
        assertEquals(FactionMatrix.Relation.ALLIED, matrix.getRelation(Faction.UNDEAD, Faction.GOBLIN_CLANS));
        assertFalse(matrix.isHostile(Faction.GOBLIN_CLANS, Faction.UNDEAD));
    }
}
