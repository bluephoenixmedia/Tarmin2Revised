package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.WeaponFamily;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;
import org.junit.Test;

import static org.junit.Assert.*;

/** Mastery: full hits in a Weapon Family raise its rank (#226, #231). */
public class MasteryTest {

    private static Mastery afterHits(WeaponFamily family, int hits) {
        Mastery m = new Mastery();
        for (int i = 0; i < hits; i++) m.recordHit(family);
        return m;
    }

    @Test
    public void tenFullHitsMakeANovice() {
        assertEquals(Rank.UNTRAINED, afterHits(WeaponFamily.BLADE, 9).rankOf(WeaponFamily.BLADE));
        assertEquals(Rank.NOVICE, afterHits(WeaponFamily.BLADE, 10).rankOf(WeaponFamily.BLADE));
    }

    @Test
    public void theLadderClimbsAt50And150And400() {
        assertEquals(Rank.ADEPT, afterHits(WeaponFamily.AXE, 50).rankOf(WeaponFamily.AXE));
        assertEquals(Rank.EXPERT, afterHits(WeaponFamily.AXE, 150).rankOf(WeaponFamily.AXE));
        assertEquals(Rank.MASTER, afterHits(WeaponFamily.AXE, 400).rankOf(WeaponFamily.AXE));
        assertEquals(Rank.MASTER, afterHits(WeaponFamily.AXE, 2000).rankOf(WeaponFamily.AXE));
    }

    @Test
    public void eachFamilyTrainsAlone() {
        Mastery m = afterHits(WeaponFamily.BOW, 50);
        assertEquals(Rank.ADEPT, m.rankOf(WeaponFamily.BOW));
        assertEquals(Rank.UNTRAINED, m.rankOf(WeaponFamily.CROSSBOW));
    }

    @Test
    public void eachRankIsOnePointToHit() {
        assertEquals(0, afterHits(WeaponFamily.BLUNT, 9).toHitBonus(WeaponFamily.BLUNT));
        assertEquals(1, afterHits(WeaponFamily.BLUNT, 10).toHitBonus(WeaponFamily.BLUNT));
        assertEquals(2, afterHits(WeaponFamily.BLUNT, 50).toHitBonus(WeaponFamily.BLUNT));
        assertEquals(4, afterHits(WeaponFamily.BLUNT, 400).toHitBonus(WeaponFamily.BLUNT));
    }

    @Test
    public void aHitReportsTheRankItReachesOnlyWhenItRises() {
        Mastery m = afterHits(WeaponFamily.DAGGER, 9);
        assertEquals(Rank.NOVICE, m.recordHit(WeaponFamily.DAGGER));
        assertNull("no rise on the eleventh hit", m.recordHit(WeaponFamily.DAGGER));
    }

    private static com.bpm.minotaur.gamedata.item.Item weapon(String family) {
        com.bpm.minotaur.gamedata.item.ItemTemplate t = new com.bpm.minotaur.gamedata.item.ItemTemplate();
        t.isWeapon = true;
        t.weaponFamily = family;
        return com.bpm.minotaur.gamedata.item.Item.fromTemplate(com.bpm.minotaur.gamedata.item.Item.ItemType.AXE, t);
    }

    @Test
    public void aSwungWeaponTrainsItsHomeFamily() {
        assertEquals(WeaponFamily.AXE, Mastery.familyOfHit(weapon("AXE"), false));
    }

    @Test
    public void aThrownWeaponTrainsThrown() {
        assertEquals(WeaponFamily.THROWN, Mastery.familyOfHit(weapon("AXE"), true));
    }

    @Test
    public void bareHandsTrainUnarmed() {
        assertEquals(WeaponFamily.UNARMED, Mastery.familyOfHit(null, false));
    }

    @Test
    public void somethingWithNoFamilyTrainsNothingEvenWhenThrown() {
        assertEquals(WeaponFamily.NONE, Mastery.familyOfHit(weapon("NONE"), false));
        assertEquals(WeaponFamily.NONE, Mastery.familyOfHit(weapon("NONE"), true));
        assertEquals("an item that is no weapon at all", WeaponFamily.NONE, Mastery.familyOfHit(weapon(null), false));
    }

    @Test
    public void aPassiveProcsAtBaseOnItsOwnRankAndGrowsAQuarterPerRankAbove() {
        // The worked example from #226: a 20% bleed learned at Adept is 20 / 25 / 30%.
        assertEquals(0.20f, 0.20f * Mastery.procMultiplier(Rank.ADEPT, Rank.ADEPT), 1e-6);
        assertEquals(0.25f, 0.20f * Mastery.procMultiplier(Rank.ADEPT, Rank.EXPERT), 1e-6);
        assertEquals(0.30f, 0.20f * Mastery.procMultiplier(Rank.ADEPT, Rank.MASTER), 1e-6);
    }

    @Test
    public void aPassiveNeverPassesOneAndAHalfTimesItsBase() {
        assertEquals(1.5f, Mastery.procMultiplier(Rank.NOVICE, Rank.MASTER), 1e-6);
    }

    @Test
    public void aKeptArtBelowItsRankProcsAtHalfAndCoolsDownTwiceAsLong() {
        assertEquals(0.5f, Mastery.procMultiplier(Rank.EXPERT, Rank.NOVICE), 1e-6);
        assertEquals(2f, Mastery.cooldownMultiplier(Rank.EXPERT, Rank.NOVICE), 1e-6);
        assertEquals(1f, Mastery.cooldownMultiplier(Rank.EXPERT, Rank.EXPERT), 1e-6);
        assertEquals(1f, Mastery.cooldownMultiplier(Rank.EXPERT, Rank.MASTER), 1e-6);
    }

    @Test
    public void aFamilyCanBeSetStraightToARankAsIfTrainedThere() {
        Mastery m = new Mastery();
        m.setRank(WeaponFamily.BLADE, Rank.ADEPT);
        assertEquals(Rank.ADEPT, m.rankOf(WeaponFamily.BLADE));
        assertEquals(2, m.toHitBonus(WeaponFamily.BLADE));
        assertNull("one more hit is not a new rank", m.recordHit(WeaponFamily.BLADE));
        m.setRank(WeaponFamily.BLADE, Rank.UNTRAINED);
        assertEquals(Rank.UNTRAINED, m.rankOf(WeaponFamily.BLADE));
    }

    @Test
    public void noFamilyNeverTrains() {
        Mastery m = afterHits(WeaponFamily.NONE, 500);
        assertEquals(Rank.UNTRAINED, m.rankOf(WeaponFamily.NONE));
        assertEquals(0, m.toHitBonus(WeaponFamily.NONE));
    }
}
