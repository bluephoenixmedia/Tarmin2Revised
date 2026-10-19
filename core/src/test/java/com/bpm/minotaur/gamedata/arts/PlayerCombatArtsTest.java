package com.bpm.minotaur.gamedata.arts;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.WeaponFamily;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.save.PlayerSaveData;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Reaching a rank teaches its Combat Art, and the soul keeps it across deaths (#241). */
public class PlayerCombatArtsTest {

    /** Keeps every event, typed ones included. */
    private static final class Events extends GameEventManager {
        final List<GameEvent> all = new ArrayList<>();

        @Override
        public void addEvent(GameEvent event) {
            all.add(event);
        }

        boolean said(String text) {
            return all.stream().anyMatch(e -> e.message != null && e.message.contains(text));
        }

        long learned(CombatArt art) {
            return all.stream().filter(e -> e.type == GameEvent.EventType.ART_LEARNED && e.payload == art).count();
        }
    }

    private Player player;
    private Events events;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        player = new Player(1, 1);
        events = new Events();
    }

    private static Item sword() {
        ItemTemplate t = new ItemTemplate();
        t.isWeapon = true;
        t.weaponFamily = "BLADE";
        return Item.fromTemplate(Item.ItemType.SWORD, t);
    }

    private void fullHits(int n) {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 1;
        Monster foe = new Monster(Monster.MonsterType.KOBOLD, 10, 10);
        foe.setTemplate(t);
        Item sword = sword();
        for (int i = 0; i < n; i++) player.trainMastery(sword, false, foe, events);
    }

    @Test
    public void aNewBodyStartsKnowingNoArts() {
        assertTrue(player.getKnownArts().isEmpty());
    }

    @Test
    public void reachingAdeptTeachesTheFamilysPassiveAndAnnouncesIt() {
        fullHits(50);

        assertTrue(player.knowsArt(CombatArt.ANSWERING_STEEL));
        assertEquals(1, events.learned(CombatArt.ANSWERING_STEEL));
        assertTrue(events.said("Answering Steel"));
    }

    @Test
    public void reachingNoviceTeachesNothing() {
        fullHits(10);
        assertTrue(player.getKnownArts().isEmpty());
    }

    @Test
    public void theSoulKeepsItsArtsAndTheNextBodyRemembers() {
        fullHits(50);
        player.beginNewIncarnation(Difficulty.MEDIUM);
        assertTrue("the soul keeps it", player.knowsArt(CombatArt.ANSWERING_STEEL));

        events.all.clear();
        fullHits(50);

        assertTrue(events.said("Your arm remembers Answering Steel."));
        assertEquals("no second card for an Art already known", 0, events.learned(CombatArt.ANSWERING_STEEL));
    }

    @Test
    public void aKeptArtBelowItsRankFiresAtHalfAndCoolsDownTwiceAsLong() {
        player.getStats().getMastery().setRank(WeaponFamily.BLUNT, Rank.MASTER);
        fullHits(0);
        player.learnArt(CombatArt.RINGING_BLOW, events);
        player.learnArt(CombatArt.BATTERING_RAM, events);
        player.beginNewIncarnation(Difficulty.MEDIUM); // the new body is Untrained

        assertEquals(0.125f, player.artChance(CombatArt.RINGING_BLOW), 1e-6);
        assertEquals(30, player.artCooldown(CombatArt.BATTERING_RAM));

        player.getStats().getMastery().setRank(WeaponFamily.BLUNT, Rank.EXPERT);
        assertEquals(0.3125f, player.artChance(CombatArt.RINGING_BLOW), 1e-6); // +25% of base a rank above
        assertEquals(15, player.artCooldown(CombatArt.BATTERING_RAM));
    }

    @Test
    public void anArtTheSoulDoesNotKnowNeverFires() {
        assertEquals(0f, player.artChance(CombatArt.RINGING_BLOW), 0f);
        assertEquals(0, player.artCooldown(CombatArt.BATTERING_RAM));
    }

    @Test
    public void noChanceRisesPastCertain() {
        player.learnArt(CombatArt.HOLD_THE_LINE, events);
        player.getStats().getMastery().setRank(WeaponFamily.POLEARM, Rank.MASTER);
        assertEquals(1f, player.artChance(CombatArt.HOLD_THE_LINE), 0f);

        player.getStats().getMastery().setRank(WeaponFamily.POLEARM, Rank.UNTRAINED);
        assertEquals("a kept Art below its rank still halves", 0.5f, player.artChance(CombatArt.HOLD_THE_LINE), 0f);
    }

    @Test
    public void anArtIsLearnedEvenWithNoOneListening() {
        player.learnArt(CombatArt.TANGLE, null);
        assertTrue(player.knowsArt(CombatArt.TANGLE));
    }

    @Test
    public void knownArtsSurviveSaveAndLoad() {
        fullHits(50);
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        PlayerSaveData restored = json.fromJson(PlayerSaveData.class, json.toJson(new PlayerSaveData(player)));
        Player loaded = new Player(2, 2);
        restored.applyToPlayer(loaded, null, null);

        assertTrue(loaded.knowsArt(CombatArt.ANSWERING_STEEL));
    }

    @Test
    public void aSaveFromBeforeTheArtsLoadsKnowingNone() {
        Player loaded = new Player(2, 2);
        new PlayerSaveData().applyToPlayer(loaded, null, null);
        assertTrue(loaded.getKnownArts().isEmpty());
    }
}
