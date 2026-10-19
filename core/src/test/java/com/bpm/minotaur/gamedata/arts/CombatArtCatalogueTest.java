package com.bpm.minotaur.gamedata.arts;

import com.bpm.minotaur.gamedata.item.WeaponFamily;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;
import org.junit.Test;

import static org.junit.Assert.*;

/** The thirty Combat Arts of the spec (docs/FEatures/combat-arts-spec.md, #241). */
public class CombatArtCatalogueTest {

    @Test
    public void everyFamilyTeachesAPassiveAnActiveAndACapstone() {
        for (WeaponFamily family : WeaponFamily.values()) {
            if (family == WeaponFamily.NONE) continue;
            assertEquals(family + " Adept", CombatArt.Kind.PASSIVE, CombatArt.of(family, Rank.ADEPT).kind);
            assertEquals(family + " Expert", CombatArt.Kind.ACTIVE, CombatArt.of(family, Rank.EXPERT).kind);
            assertEquals(family + " Master", CombatArt.Kind.CAPSTONE, CombatArt.of(family, Rank.MASTER).kind);
        }
        assertEquals(30, CombatArt.values().length);
    }

    @Test
    public void novicesAndNoFamilyTeachNothing() {
        assertNull(CombatArt.of(WeaponFamily.BLADE, Rank.NOVICE));
        assertNull(CombatArt.of(WeaponFamily.BLADE, Rank.UNTRAINED));
        assertNull(CombatArt.of(WeaponFamily.NONE, Rank.ADEPT));
    }

    @Test
    public void theArtsCarryTheSpecsNamesAndStartingNumbers() {
        CombatArt ringing = CombatArt.of(WeaponFamily.BLUNT, Rank.ADEPT);
        assertEquals(CombatArt.RINGING_BLOW, ringing);
        assertEquals(0.25f, ringing.baseChance, 1e-6);

        assertEquals(0.30f, CombatArt.ANSWERING_STEEL.baseChance, 1e-6);
        assertEquals(15, CombatArt.HELD_ARROW.cooldownTurns);
        assertEquals(50, CombatArt.TOLL_THE_BELL.cooldownTurns);
        assertEquals(5, CombatArt.TOLL_THE_BELL.durationTurns);
        assertEquals(WeaponFamily.THROWN, CombatArt.SHATTERING_THROW.family);
        assertEquals(Rank.EXPERT, CombatArt.MARKED_SKULL.rank);
    }
}
