package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.gamedata.progression.SkillId;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Death is permanent for the Incarnation (#187): what it built dies with it, what the
 * soul and the world remember survives into the next one.
 */
public class IncarnationDeathTest {

    @After
    public void tearDown() {
        ShelterAltar.getInstance().reset();
    }

    /** A player who has lived a while: levels, trained stats, a skill, and points left to spend. */
    private static Player veteran() {
        Player player = new Player(0, 0);
        player.addExperience(5000, null);
        assertTrue("setup: the veteran has levelled", player.getLevel() >= 5);
        player.allocateAttribute(ShelterAltar.StatType.CONSTITUTION);
        player.allocateAttribute(ShelterAltar.StatType.STRENGTH);
        player.getStats().setStrength(11);
        player.getStats().addUnallocatedSkillPoints(1);
        assertTrue("setup: the veteran learned a skill", player.learnSkill(SkillId.DUAL_WIELDER));
        player.getStats().setArrows(60);
        player.getStats().setShot(12);
        player.getStats().addTemporaryHP(9);
        return player;
    }

    @Test
    public void levelXpAndPointsDieWithTheIncarnation() {
        Player player = veteran();
        player.beginNewIncarnation(Difficulty.MEDIUM);

        PlayerStats fresh = new PlayerStats(Difficulty.MEDIUM);
        PlayerStats stats = player.getStats();
        assertEquals(1, stats.getLevel());
        assertEquals(0, stats.getExperience());
        assertEquals(fresh.getExperienceToNextLevel(), stats.getExperienceToNextLevel());
        assertEquals(0, stats.getUnallocatedAttributePoints());
        assertEquals(0, stats.getUnallocatedSkillPoints());
        assertTrue("skills bought with skill points are lost", stats.getUnlockedSkills().isEmpty());
        assertFalse(player.hasPendingLevelUpModal());
    }

    @Test
    public void attributesAndMaximaReturnToTheStartValues() {
        Player player = veteran();
        player.beginNewIncarnation(Difficulty.MEDIUM);

        PlayerStats stats = player.getStats();
        for (ShelterAltar.StatType stat : new ShelterAltar.StatType[] {
                ShelterAltar.StatType.STRENGTH, ShelterAltar.StatType.DEXTERITY,
                ShelterAltar.StatType.CONSTITUTION, ShelterAltar.StatType.INTELLIGENCE,
                ShelterAltar.StatType.WISDOM, ShelterAltar.StatType.AGILITY}) {
            assertEquals(stat + " back to the start value", 10, stats.getBaseStat(stat));
        }
        assertEquals(10, stats.getCharisma());
        assertEquals(Difficulty.MEDIUM.startWarStrength, stats.getBaseMaxHP());
        assertEquals(Difficulty.MEDIUM.startSpiritualStrength, stats.getMaxMP());
        assertEquals("a new body wakes whole", stats.getMaxHP(), stats.getCurrentHP());
        assertEquals(stats.getMaxMP(), stats.getCurrentMP());
        assertEquals(0, stats.getTemporaryHP());
    }

    @Test
    public void carriedAmmunitionIsLostLikeAnyCarriedGear() {
        Player player = veteran();
        player.beginNewIncarnation(Difficulty.MEDIUM);

        assertEquals(Difficulty.MEDIUM.startArrows, player.getStats().getArrows());
        assertEquals(0, player.getStats().getShot());
    }

    @Test
    public void knownSpellsSurviveButSlotsBelongToTheLevel() {
        Player player = veteran();
        player.checkSpellSlotProgression(null);
        assertTrue("setup: levels opened more slots", player.getUnlockedSpellSlots() > 1);
        player.learnPermanentSpellId("FIREBOLT");
        player.prepareSpell(1, "FIREBOLT");
        assertTrue(player.isSpellPrepared("FIREBOLT"));

        player.beginNewIncarnation(Difficulty.MEDIUM);

        assertTrue("Known Spells are soul memory", player.getKnownSpellIds().contains("FIREBOLT"));
        assertTrue(player.getKnownSpellIds().contains(Player.STARTING_SPELL));
        assertEquals("slots come from levels", Player.slotsForLevel(1), player.getUnlockedSpellSlots());
        assertFalse("a spell cannot stay prepared in a slot the new body has not opened",
                player.isSpellPrepared("FIREBOLT"));
    }

    @Test
    public void altarAscensionIsTheWorldsAndSurvives() {
        ShelterAltar.getInstance().reset();
        ShelterAltar.getInstance().setAscensionTier(ShelterAltar.StatType.STRENGTH, 2);
        Player player = veteran();

        player.beginNewIncarnation(Difficulty.MEDIUM);

        assertEquals(10, player.getStats().getBaseStat(ShelterAltar.StatType.STRENGTH));
        assertEquals("the altar station's bonus is kept", 12, player.getStats().getStrength());
    }

    @Test
    public void aNewIncarnationMatchesANewGame() {
        Player reborn = veteran();
        reborn.beginNewIncarnation(Difficulty.HARD);
        PlayerStats fresh = new PlayerStats(Difficulty.HARD);

        assertEquals(fresh.getBaseMaxHP(), reborn.getStats().getBaseMaxHP());
        assertEquals(fresh.getMaxMP(), reborn.getStats().getMaxMP());
        assertEquals(fresh.getToHitBonus(), reborn.getStats().getToHitBonus());
    }
}
