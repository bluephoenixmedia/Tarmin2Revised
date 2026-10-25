package com.bpm.minotaur.ui;

import com.bpm.minotaur.gamedata.arts.CombatArt;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.WeaponFamily;
import com.bpm.minotaur.gamedata.player.Mastery;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;
import com.bpm.minotaur.gamedata.player.Player;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** The words that show Mastery and the Combat Arts (#249, spec section 6). */
public class MasteryTextTest {

    private Player player;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        player = new Player(3f, 3f);
    }

    private Mastery mastery() {
        return player.getStats().getMastery();
    }

    private void hits(WeaponFamily family, int n) {
        for (int i = 0; i < n; i++) mastery().recordHit(family);
    }

    @Test
    public void aFamilyLineGivesTheRankAndTheExactCountToTheNext() {
        hits(WeaponFamily.AXE, 62);
        assertEquals("Axe: Adept, 62 / 150 full hits", MasteryText.familyLine(mastery(), WeaponFamily.AXE));
        assertEquals("Bow: Untrained, 0 / 10 full hits", MasteryText.familyLine(mastery(), WeaponFamily.BOW));
    }

    @Test
    public void aMasterHasNoNextRankToCountTo() {
        mastery().setRank(WeaponFamily.BLADE, Rank.MASTER);
        hits(WeaponFamily.BLADE, 12);
        assertEquals("Blade: Master, 412 full hits", MasteryText.familyLine(mastery(), WeaponFamily.BLADE));
    }

    @Test
    public void theSectionListsAllTenFamilies() {
        List<WeaponFamily> families = MasteryText.families();
        assertEquals(10, families.size());
        assertFalse(families.contains(WeaponFamily.NONE));
    }

    @Test
    public void aKeptArtThisBodyHasNotReachedSaysWhereToRetrain() {
        player.learnArt(CombatArt.RINGING_BLOW, null);
        mastery().setRank(WeaponFamily.BLUNT, Rank.NOVICE);
        assertEquals("Ringing Blow -- Blunt Adept Passive", MasteryText.artLine(CombatArt.RINGING_BLOW));
        assertEquals("retrain at Adept", MasteryText.retrainNote(player, CombatArt.RINGING_BLOW));

        mastery().setRank(WeaponFamily.BLUNT, Rank.ADEPT);
        assertNull("trained again: nothing to say", MasteryText.retrainNote(player, CombatArt.RINGING_BLOW));
    }

    @Test
    public void theInspectorShowsTheFamilyAndOnlyTheRankName() {
        ItemTemplate t = new ItemTemplate();
        t.isWeapon = true;
        t.weaponFamily = "AXE";
        Item axe = Item.fromTemplate(Item.ItemType.SWORD, t);
        hits(WeaponFamily.AXE, 62);

        assertEquals("Axe, your rank Adept", MasteryText.inspectorValue(player, axe));

        ItemTemplate rock = new ItemTemplate();
        assertNull("no Family, no line", MasteryText.inspectorValue(player, Item.fromTemplate(Item.ItemType.SWORD, rock)));
    }

    @Test
    public void thePauseLedgerCountsTheSoulsArts() {
        assertEquals("Arts remembered: 0", MasteryText.artsRemembered(player));
        player.learnArt(CombatArt.RINGING_BLOW, null);
        player.learnArt(CombatArt.BATTERING_RAM, null);
        assertEquals("Arts remembered: 2", MasteryText.artsRemembered(player));
    }

    @Test
    public void theArtCardNamesTheFamilyRankAndKind() {
        assertEquals("Blunt - Expert Active", MasteryText.cardSubtitle(CombatArt.BATTERING_RAM));
        assertEquals("Blunt - Master Capstone", MasteryText.cardSubtitle(CombatArt.TOLL_THE_BELL));
    }

    @Test
    public void everyLineSurvivesTheFont() {
        hits(WeaponFamily.DAGGER, 3);
        for (WeaponFamily family : MasteryText.families()) {
            String line = MasteryText.familyLine(mastery(), family);
            assertEquals(line, UiGlyphs.sanitize(line));
        }
        for (CombatArt art : CombatArt.values()) {
            for (String line : new String[]{MasteryText.artLine(art), MasteryText.cardSubtitle(art), art.describe()}) {
                assertEquals(line, UiGlyphs.sanitize(line));
            }
        }
    }
}
