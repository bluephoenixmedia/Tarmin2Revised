package com.bpm.minotaur.gamedata.player;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.WeaponFamily;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;
import com.bpm.minotaur.gamedata.save.PlayerSaveData;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** The protagonist trains Mastery by landing full hits on things worth fighting (#226, #231). */
public class PlayerMasteryTest {

    private Player player;
    private GameEventManager events;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        player = new Player(1, 1);
        events = new GameEventManager();
    }

    private static Monster monsterOfLevel(int level) {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = level;
        Monster m = new Monster(Monster.MonsterType.KOBOLD, 10, 10);
        m.setTemplate(t);
        return m;
    }

    private static Item axe() {
        ItemTemplate t = new ItemTemplate();
        t.isWeapon = true;
        t.weaponFamily = "AXE";
        return Item.fromTemplate(Item.ItemType.AXE, t);
    }

    private void hit(Item weapon, Monster target, int times) {
        for (int i = 0; i < times; i++) player.trainMastery(weapon, false, target, events);
    }

    private boolean saidAnything(String text) {
        return events.getMessageEvents().stream().anyMatch(e -> e.message.contains(text));
    }

    @Test
    public void tenHitsOnAWorthyFoeMakeANoviceAndSaySo() {
        hit(axe(), monsterOfLevel(1), 10);

        assertEquals(Rank.NOVICE, player.getStats().getMastery().rankOf(WeaponFamily.AXE));
        assertTrue(saidAnything("Your Axe Mastery rises to Novice"));
    }

    @Test
    public void aPolymorphedBodyFightsWithItsOwnClawsAndTrainsUnarmed() {
        player.enterForm(new com.bpm.minotaur.gamedata.polymorph.PlayerForm("Wolf", 20, 6, 1f, "1d6", false, false, 100));
        hit(axe(), monsterOfLevel(1), 10);

        assertEquals(Rank.NOVICE, player.getStats().getMastery().rankOf(WeaponFamily.UNARMED));
        assertEquals(Rank.UNTRAINED, player.getStats().getMastery().rankOf(WeaponFamily.AXE));
        assertEquals("the claws get Unarmed's bonus, not the axe's", 1, player.masteryToHitBonus(axe(), false));
    }

    @Test
    public void aTrivialFoeTeachesNothing() {
        player.setLevel(5);
        hit(axe(), monsterOfLevel(2), 10); // three levels below: Trivial, the Vengeance rule

        assertEquals(Rank.UNTRAINED, player.getStats().getMastery().rankOf(WeaponFamily.AXE));
        assertFalse(saidAnything("Mastery"));
    }

    @Test
    public void aNewIncarnationStartsUntrained() {
        hit(axe(), monsterOfLevel(1), 50);
        player.getStats().beginNewIncarnation(Difficulty.MEDIUM);

        assertEquals(Rank.UNTRAINED, player.getStats().getMastery().rankOf(WeaponFamily.AXE));
    }

    @Test
    public void masterySurvivesSaveAndLoad() {
        hit(axe(), monsterOfLevel(1), 50);
        hit(null, monsterOfLevel(1), 10);

        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        PlayerSaveData restored = json.fromJson(PlayerSaveData.class, json.toJson(new PlayerSaveData(player)));
        Player loaded = new Player(2, 2);
        restored.applyToPlayer(loaded, null, null);

        assertEquals(Rank.ADEPT, loaded.getStats().getMastery().rankOf(WeaponFamily.AXE));
        assertEquals(Rank.NOVICE, loaded.getStats().getMastery().rankOf(WeaponFamily.UNARMED));
    }

    @Test
    public void aSaveFromBeforeMasteryLoadsUntrained() {
        Player loaded = new Player(2, 2);
        new PlayerSaveData().applyToPlayer(loaded, null, null);

        assertEquals(Rank.UNTRAINED, loaded.getStats().getMastery().rankOf(WeaponFamily.AXE));
    }
}
