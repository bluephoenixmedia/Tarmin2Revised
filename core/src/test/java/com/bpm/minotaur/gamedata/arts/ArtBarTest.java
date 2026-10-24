package com.bpm.minotaur.gamedata.arts;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.WeaponFamily;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.save.PlayerSaveData;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** The Art bar's contents and the cooldown rules (#245, spec section 5). */
public class ArtBarTest {

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

    private static Item weapon(String family) {
        ItemTemplate t = new ItemTemplate();
        t.isWeapon = true;
        t.weaponFamily = family;
        return Item.fromTemplate(Item.ItemType.SWORD, t);
    }

    /** The soul knows {@code art}, and this body has trained its Family to {@code rank}. */
    private void know(CombatArt art, Rank rank) {
        player.learnArt(art, null);
        player.getStats().getMastery().setRank(art.family, rank);
    }

    @Test
    public void theRightHandFillsTheFirstTwoSlotsAndTheLeftTheLastTwo() {
        know(CombatArt.BATTERING_RAM, Rank.MASTER);
        know(CombatArt.TOLL_THE_BELL, Rank.MASTER);
        know(CombatArt.CLOSING_THRUST, Rank.EXPERT);
        player.getInventory().setRightHand(weapon("BLUNT"));
        player.getInventory().setLeftHand(weapon("BLADE"));

        assertArrayEquals(new CombatArt[]{CombatArt.BATTERING_RAM, CombatArt.TOLL_THE_BELL, CombatArt.CLOSING_THRUST, null},
                ArtBar.contents(player));
    }

    @Test
    public void swappingHandsSwapsTheSlots() {
        know(CombatArt.BATTERING_RAM, Rank.EXPERT);
        know(CombatArt.CLOSING_THRUST, Rank.EXPERT);
        player.getInventory().setRightHand(weapon("BLADE"));
        player.getInventory().setLeftHand(weapon("BLUNT"));

        assertArrayEquals(new CombatArt[]{CombatArt.CLOSING_THRUST, null, CombatArt.BATTERING_RAM, null},
                ArtBar.contents(player));
    }

    @Test
    public void bareHandsAreUnarmedAndTheSameFamilyTwiceFillsOnce() {
        know(CombatArt.IRON_GRIP, Rank.EXPERT);
        assertArrayEquals(new CombatArt[]{CombatArt.IRON_GRIP, null, null, null}, ArtBar.contents(player));

        know(CombatArt.BATTERING_RAM, Rank.EXPERT);
        player.getInventory().setRightHand(weapon("BLUNT"));
        player.getInventory().setLeftHand(weapon("BLUNT"));
        assertArrayEquals(new CombatArt[]{CombatArt.BATTERING_RAM, null, null, null}, ArtBar.contents(player));
    }

    @Test
    public void aPassiveNeverTakesASlotAndAnUnknownArtLeavesItEmpty() {
        know(CombatArt.RINGING_BLOW, Rank.ADEPT);
        player.getInventory().setRightHand(weapon("BLUNT"));
        assertArrayEquals(new CombatArt[ArtBar.SLOTS], ArtBar.contents(player));
    }

    @Test
    public void slotsShowReadyCoolingKeptAndHeld() {
        know(CombatArt.BATTERING_RAM, Rank.EXPERT);
        know(CombatArt.HELD_ARROW, Rank.EXPERT);
        player.getStats().getMastery().setRank(WeaponFamily.BOW, Rank.NOVICE); // a later body, not retrained
        player.getInventory().setRightHand(weapon("BLUNT"));
        player.getInventory().setLeftHand(weapon("BOW"));

        ArtBar.Slot[] slots = ArtBar.slots(player);
        assertEquals(ArtBar.State.READY, slots[0].state);
        assertFalse(slots[0].kept);
        assertEquals(ArtBar.State.EMPTY, slots[1].state);
        assertTrue("known by the soul, not by this body", slots[2].kept);

        player.getArtCooldowns().start(CombatArt.BATTERING_RAM, 15);
        player.setHeldArt(CombatArt.HELD_ARROW);
        slots = ArtBar.slots(player);
        assertEquals(ArtBar.State.COOLING, slots[0].state);
        assertEquals(15, slots[0].turnsLeft);
        assertEquals(ArtBar.State.HELD, slots[2].state);
    }

    @Test
    public void aKeptArtCoolsForTwiceAsLong() {
        know(CombatArt.BATTERING_RAM, Rank.EXPERT);
        assertEquals(CombatArt.ACTIVE_COOLDOWN, player.artCooldown(CombatArt.BATTERING_RAM));
        player.getStats().getMastery().setRank(WeaponFamily.BLUNT, Rank.ADEPT);
        assertEquals(2 * CombatArt.ACTIVE_COOLDOWN, player.artCooldown(CombatArt.BATTERING_RAM));
    }

    @Test
    public void cooldownsCountTheTurnsAfterTheUseAndEndAtZero() {
        ArtCooldowns cooldowns = new ArtCooldowns();
        cooldowns.start(CombatArt.BATTERING_RAM, 2);
        cooldowns.tick(); // the turn the Art was used in
        assertEquals("the turn of use does not count", 2, cooldowns.turnsLeft(CombatArt.BATTERING_RAM));
        cooldowns.tick();
        assertEquals(1, cooldowns.turnsLeft(CombatArt.BATTERING_RAM));
        cooldowns.tick();
        assertFalse(cooldowns.isCooling(CombatArt.BATTERING_RAM));
        cooldowns.tick();
        assertEquals("never below zero", 0, cooldowns.turnsLeft(CombatArt.BATTERING_RAM));
    }

    @Test
    public void cooldownsSurviveTheSave() {
        know(CombatArt.BATTERING_RAM, Rank.EXPERT);
        player.getArtCooldowns().start(CombatArt.BATTERING_RAM, 9);
        PlayerSaveData data = new PlayerSaveData(player);
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        PlayerSaveData loaded = json.fromJson(PlayerSaveData.class, json.toJson(data));

        ArtCooldowns restored = new ArtCooldowns();
        restored.restore(loaded.artCooldowns);
        assertEquals(9, restored.turnsLeft(CombatArt.BATTERING_RAM));
    }

    @Test
    public void aSavedCooldownForAnArtNoLongerInTheGameIsDropped() {
        Map<String, Integer> saved = new HashMap<>();
        saved.put("NO_SUCH_ART", 4);
        saved.put("BATTERING_RAM", 3);
        ArtCooldowns cooldowns = new ArtCooldowns();
        cooldowns.restore(saved);
        assertEquals(3, cooldowns.turnsLeft(CombatArt.BATTERING_RAM));
    }
}
