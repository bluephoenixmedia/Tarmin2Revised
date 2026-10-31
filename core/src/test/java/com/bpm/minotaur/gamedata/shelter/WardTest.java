package com.bpm.minotaur.gamedata.shelter;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.spells.SpellDataManager;
import com.bpm.minotaur.gamedata.spells.SpellTemplate;
import com.bpm.minotaur.gamedata.spells.Tome;
import com.bpm.minotaur.gamedata.spells.TomeChoice;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/** Living War A64: the three wards, alone and together, and the tome that teaches their spells. */
public class WardTest {

    private static final TomeChoice.Perks BASE = new TomeChoice.Perks(3, 0, 8);

    @Before
    public void loadSpells() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (proxy, method, args) -> null);
        }
        if (Gdx.files == null) {
            Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(com.badlogic.gdx.Files.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Files.class}, (proxy, method, args) -> {
                        if ("local".equals(method.getName()) || "internal".equals(method.getName())) {
                            File file = new File("assets/" + args[0]);
                            if (!file.exists()) file = new File("../assets/" + args[0]);
                            return new FileHandle(file);
                        }
                        return null;
                    });
        }
        if (!SpellDataManager.getInstance().isLoaded()) SpellDataManager.getInstance().load();
    }

    private static NightsWards with(Ward... wards) {
        NightsWards night = new NightsWards();
        for (Ward ward : wards) night.lay(ward);
        return night;
    }

    @Test
    public void aShieldTakesTheFirstTwentyAndNoMore() {
        Ward.Shield shield = new Ward.Shield(with(Ward.SHIELD).shield());
        assertEquals(0, shield.absorb(15));
        assertEquals(3, shield.absorb(8));
        assertEquals(9, shield.absorb(9));
        assertEquals(0, new NightsWards().shield());
    }

    @Test
    public void anAlarmGivesNoFreeRoundsEvenToAFireDrawnAmbush() {
        assertEquals(1, new NightsWards().freeRounds(false));
        assertEquals(2, new NightsWards().freeRounds(true));
        assertEquals(0, with(Ward.ALARM).freeRounds(false));
        assertEquals(0, with(Ward.ALARM).freeRounds(true));
    }

    @Test
    public void anAlarmIsNoHelpAgainstAnArmyArriving() {
        assertEquals(NightsWards.Waking.ARMY, with(Ward.ALARM).waking(true, 0));
        assertEquals("nor does it outrank one", NightsWards.Waking.ARMY, with(Ward.ALARM).waking(true, 3));
        assertEquals(NightsWards.Waking.ARMY, new NightsWards().waking(true, 0));
    }

    @Test
    public void whatWakesTheSleeper() {
        assertEquals(NightsWards.Waking.MORNING, new NightsWards().waking(false, 0));
        assertEquals(NightsWards.Waking.AMBUSH, new NightsWards().waking(false, 2));
        assertEquals(NightsWards.Waking.ALARM, with(Ward.ALARM).waking(false, 2));
        assertEquals("a Concealment the ambush saw through is still an ambush", NightsWards.Waking.AMBUSH, with(Ward.CONCEALMENT).waking(false, 1));
    }

    @Test
    public void concealmentHidesFromTheDullAndNotTheCleverOrTheMagical() {
        NightsWards veiled = with(Ward.CONCEALMENT);
        assertTrue(veiled.hiddenFrom(11, false));
        assertFalse("clever", veiled.hiddenFrom(12, false));
        assertFalse("a dull spellcaster still sees", veiled.hiddenFrom(3, true));
        assertFalse("no veil, no hiding", new NightsWards().hiddenFrom(3, false));
    }

    @Test
    public void concealmentAndALitFireCannotBeCombined() {
        NightsWards night = new NightsWards();
        assertFalse("not over a fire", night.canLay(Ward.CONCEALMENT, true, true));
        assertFalse("not over a fire", night.canCast(Ward.CONCEALMENT, true, 99, true));
        assertTrue(night.canKindle());
        night.lay(Ward.CONCEALMENT);
        assertFalse("no fire inside it", night.canKindle());
        night.takeDown(Ward.CONCEALMENT);
        assertTrue(night.canKindle());
    }

    @Test
    public void allThreeStackAndEachDoesItsOwnWork() {
        NightsWards night = with(Ward.SHIELD, Ward.ALARM, Ward.CONCEALMENT);
        assertEquals(3, night.wards().size());
        assertEquals(0, night.freeRounds(false));
        assertEquals(Ward.SHIELD_ABSORBS, night.shield());
        assertTrue(night.hiddenFrom(5, false));
        assertFalse(night.hiddenFrom(15, false));
    }

    @Test
    public void eachIsPaidSeparatelyAndTheMpIsSpokenFor() {
        NightsWards night = new NightsWards();
        night.cast(Ward.SHIELD);
        night.cast(Ward.ALARM);
        assertEquals(13, night.mpDue());
        assertFalse("13 of 20 spoken for leaves 7", night.canCast(Ward.CONCEALMENT, true, 20, false));
        assertTrue(night.canCast(Ward.CONCEALMENT, true, 23, false));
        assertFalse("unknown", night.canCast(Ward.CONCEALMENT, false, 99, false));
        night.takeDown(Ward.SHIELD);
        night.lay(Ward.SHIELD);
        assertEquals("a screen costs no MP", 5, night.mpDue());
        assertFalse("readied once", night.canLay(Ward.SHIELD, true, false));
        assertFalse("none carried", new NightsWards().canLay(Ward.ALARM, false, false));
    }

    @Test
    public void theSpellsMatchTheirWardsInTheData() {
        for (Ward ward : Ward.values()) {
            SpellTemplate spell = SpellDataManager.getSpell(ward.spellId);
            assertNotNull(ward.spellId, spell);
            assertEquals(ward.spellName, spell.getName());
            assertEquals(ward.mpCost, spell.getMpCost());
            assertTrue(spell.isInTomePool(Tome.WAYFARER));
            assertEquals(ward, Ward.ofSpell(ward.spellId));
        }
        assertNull(Ward.ofSpell("MOTE_OF_LIGHT"));
    }

    @Test
    public void theWayfarersTomeOffersTheThreeAndNothingElseOffersThem() {
        TomeChoice choice = TomeChoice.offer(Tome.WAYFARER, null, Collections.emptyList(), BASE, new Random(1));
        assertEquals(new HashSet<>(Arrays.asList("SLEEPERS_WARD", "WATCHFUL_SLEEP", "VEIL_OF_THE_SLEEPER")), new HashSet<>(choice.getOptions()));
        for (Tome tome : Tome.values()) {
            List<String> filler = TomeChoice.generalCandidates(tome, Collections.emptyList(), BASE);
            for (Ward ward : Ward.values()) assertFalse(tome + " " + ward, filler.contains(ward.spellId));
        }
        for (SpellTemplate found : SpellDataManager.getInstance().getFoundSpells()) assertNull(found.getId(), Ward.ofSpell(found.getId()));
        for (SpellTemplate found : SpellDataManager.getInstance().getSpellsByLevel(1)) assertNull(found.getId(), Ward.ofSpell(found.getId()));
        for (SpellTemplate found : SpellDataManager.getInstance().getSpellsBySchoolAndMaxLevel(null, 9)) assertNull(found.getId(), Ward.ofSpell(found.getId()));
    }

    @Test
    public void aWardSpellHoldsNoSpellSlot() {
        Player player = new Player(5f, 5f);
        assertEquals(-1, player.learnAndPrepareIfSlotFree("WATCHFUL_SLEEP"));
        assertTrue(player.getKnownSpellIds().contains("WATCHFUL_SLEEP"));
        assertFalse(player.isSpellPrepared("WATCHFUL_SLEEP"));
        assertEquals(Player.SlotChange.CAST_ON_PITCHING, player.assignSpellSlot(0, "WATCHFUL_SLEEP", new Maze(2, new int[16][16])));
        assertFalse(player.isSpellPrepared("WATCHFUL_SLEEP"));
    }

    @Test
    public void theWayfarersTomeIsATomeAndNotCargo() {
        assertEquals(Tome.WAYFARER, Tome.of(Item.ItemType.TOME_OF_THE_WAYFARER));
        assertEquals("it unlocks no slot", 0, Tome.WAYFARER.getSlotNumber());
        assertFalse("it goes in a pocket", Item.fromTemplate(Item.ItemType.TOME_OF_THE_WAYFARER, new com.bpm.minotaur.gamedata.item.ItemTemplate()).isCargo());
    }
}
