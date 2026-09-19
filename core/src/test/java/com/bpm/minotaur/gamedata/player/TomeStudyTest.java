package com.bpm.minotaur.gamedata.player;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.save.ItemSaveData;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Studying a Tome: instant in the Shelter; in the field a channelled action over
 * several turns that damage or a hostile coming into view interrupts. Progress is
 * kept on the Tome.
 */
public class TomeStudyTest {

    private Player player;
    private Maze field;
    private GameEventManager events;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().reset();
        com.bpm.minotaur.gamedata.spells.SpellDataManager.getInstance().load();
        player = new Player(1, 1);
        field = new Maze(2, new int[12][12]);
        events = new GameEventManager();
    }

    private Item tome(Item.ItemType type) {
        Item tome = Item.fromTemplate(type, new ItemTemplate());
        player.getInventory().pickupToBackpack(tome);
        return tome;
    }

    /** Takes the first spell the pending Tome Choice offers. */
    private String chooseFirstOption() {
        String id = player.getPendingTomeChoice().getOptions().get(0);
        assertTrue(player.chooseTomeSpell(id, events));
        return id;
    }

    /** Runs world turns until the study stops, returning the last step. */
    private TomeStudy.Step studyUntilStopped() {
        TomeStudy.Step step = TomeStudy.Step.CONTINUE;
        for (int turn = 0; turn < 100 && step == TomeStudy.Step.CONTINUE; turn++) {
            step = player.advanceTomeStudy(field, events);
        }
        return step;
    }

    @Test
    public void studyingInTheShelterIsInstant() {
        Maze shelter = new Maze(1, new int[12][12]);
        shelter.addHomeTile(new GridPoint2(1, 1));
        Item tome = tome(Item.ItemType.TOME_OF_THE_INITIATE);

        assertTrue(player.beginTomeStudy(tome, shelter, events));

        assertNull("Nothing left to channel", player.getActiveTomeStudy());
        assertNotNull("The Tome Choice opens at once", player.getPendingTomeChoice());
    }

    @Test
    public void choosingASpellUnlocksTheSlotAndPreparesTheSpellInIt() {
        Maze shelter = new Maze(1, new int[12][12]);
        shelter.addHomeTile(new GridPoint2(1, 1));
        Item tome = tome(Item.ItemType.TOME_OF_THE_INITIATE);
        player.beginTomeStudy(tome, shelter, events);
        assertEquals("Nothing is granted until a spell is chosen", 1, player.getUnlockedSpellSlots());

        String chosen = chooseFirstOption();

        assertNull(player.getPendingTomeChoice());
        assertEquals(2, player.getUnlockedSpellSlots());
        assertTrue(player.getKnownSpellIds().contains(chosen));
        assertEquals("The spell goes straight into the new slot", chosen, player.getPreparedSpell(1));
        assertFalse("The Tome is used up", player.getInventory().getMainInventory().contains(tome));
    }

    @Test
    public void onlyAnOfferedSpellCanBeChosen() {
        Maze shelter = new Maze(1, new int[12][12]);
        shelter.addHomeTile(new GridPoint2(1, 1));
        player.beginTomeStudy(tome(Item.ItemType.TOME_OF_THE_INITIATE), shelter, events);

        assertFalse(player.chooseTomeSpell("WISH", events));
        assertNotNull(player.getPendingTomeChoice());
    }

    @Test
    public void aRepeatTomeTeachesASpellWithoutANewSlot() {
        Maze shelter = new Maze(1, new int[12][12]);
        shelter.addHomeTile(new GridPoint2(1, 1));
        player.setUnlockedSpellSlots(2);
        player.learnSpellId("FIREBALL");
        player.prepareSpell(1, "FIREBALL");
        player.beginTomeStudy(tome(Item.ItemType.TOME_OF_THE_INITIATE), shelter, events);

        String chosen = chooseFirstOption();

        assertEquals(2, player.getUnlockedSpellSlots());
        assertTrue(player.getKnownSpellIds().contains(chosen));
        assertEquals("Every unlocked slot was full, so the spell waits in the Spellbook",
                "MOTE_OF_LIGHT", player.getPreparedSpell(0));
        assertEquals("FIREBALL", player.getPreparedSpell(1));
    }

    @Test
    public void aRepeatTomeWithNothingLeftToTeachIsKept() {
        player.setUnlockedSpellSlots(2);
        for (String id : com.bpm.minotaur.gamedata.spells.TomeChoice.candidates(
                com.bpm.minotaur.gamedata.spells.Tome.INITIATE, java.util.Collections.emptyList(),
                com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().getTomeChoicePerks())) {
            player.learnSpellId(id);
        }
        Item tome = tome(Item.ItemType.TOME_OF_THE_INITIATE);

        assertFalse(player.beginTomeStudy(tome, field, events));
        assertNull(player.getActiveTomeStudy());
        assertTrue(player.getInventory().getMainInventory().contains(tome));
    }

    @Test
    public void fieldStudyTakesTheTomesTurnsThenUnlocksItsSlot() {
        Item tome = tome(Item.ItemType.TOME_OF_ELEMENTS);
        int turns = TomeStudy.turnsToStudy(Item.ItemType.TOME_OF_ELEMENTS);
        assertEquals(15, turns);

        assertTrue(player.beginTomeStudy(tome, field, events));
        assertNotNull(player.getActiveTomeStudy());

        for (int turn = 1; turn < turns; turn++) {
            assertEquals(TomeStudy.Step.CONTINUE, player.advanceTomeStudy(field, events));
        }
        assertEquals(1, player.getUnlockedSpellSlots());

        assertEquals(TomeStudy.Step.COMPLETE, player.advanceTomeStudy(field, events));
        assertNull(player.getActiveTomeStudy());
        chooseFirstOption();
        assertEquals(3, player.getUnlockedSpellSlots());
        assertFalse(player.getInventory().getMainInventory().contains(tome));
    }

    @Test
    public void studyTurnsGrowWithEachTome() {
        assertEquals(10, TomeStudy.turnsToStudy(Item.ItemType.TOME_OF_THE_INITIATE));
        assertEquals(15, TomeStudy.turnsToStudy(Item.ItemType.TOME_OF_ELEMENTS));
        assertEquals(20, TomeStudy.turnsToStudy(Item.ItemType.TOME_OF_THE_ARCANE));
        assertEquals(25, TomeStudy.turnsToStudy(Item.ItemType.TOME_OF_TARMIN));
    }

    @Test
    public void damageInterruptsAndProgressIsKeptOnTheTome() {
        Item tome = tome(Item.ItemType.TOME_OF_THE_INITIATE);
        player.beginTomeStudy(tome, field, events);
        for (int turn = 0; turn < 4; turn++) {
            player.advanceTomeStudy(field, events);
        }

        player.getStats().setCurrentHP(player.getStats().getCurrentHP() - 1);
        assertEquals(TomeStudy.Step.INTERRUPTED_BY_DAMAGE, player.advanceTomeStudy(field, events));
        assertNull(player.getActiveTomeStudy());
        assertEquals(4, tome.getStudyProgress());
        assertTrue("An interrupted Tome is not used up", player.getInventory().getMainInventory().contains(tome));

        // Picking it up again resumes where it stopped.
        player.beginTomeStudy(tome, field, events);
        for (int turn = 0; turn < 5; turn++) {
            assertEquals(TomeStudy.Step.CONTINUE, player.advanceTomeStudy(field, events));
        }
        assertEquals(TomeStudy.Step.COMPLETE, player.advanceTomeStudy(field, events));
    }

    @Test
    public void aHostileComingIntoViewInterrupts() {
        Item tome = tome(Item.ItemType.TOME_OF_THE_INITIATE);
        player.beginTomeStudy(tome, field, events);
        player.advanceTomeStudy(field, events);

        field.addMonster(new Monster(Monster.MonsterType.GOBLIN, 10, 10, 5, 1));

        assertEquals(TomeStudy.Step.INTERRUPTED_BY_HOSTILE, studyUntilStopped());
        assertEquals(1, tome.getStudyProgress());
    }

    @Test
    public void cannotStartWithAHostileInView() {
        Item tome = tome(Item.ItemType.TOME_OF_THE_INITIATE);
        field.addMonster(new Monster(Monster.MonsterType.GOBLIN, 10, 10, 5, 1));

        assertFalse(player.beginTomeStudy(tome, field, events));
        assertNull(player.getActiveTomeStudy());
    }

    @Test
    public void cancellingKeepsProgress() {
        Item tome = tome(Item.ItemType.TOME_OF_THE_INITIATE);
        player.beginTomeStudy(tome, field, events);
        player.advanceTomeStudy(field, events);
        player.advanceTomeStudy(field, events);

        player.cancelTomeStudy(events);

        assertNull(player.getActiveTomeStudy());
        assertEquals(2, tome.getStudyProgress());
    }

    @Test
    public void studyProgressSurvivesSaving() {
        Item tome = tome(Item.ItemType.TOME_OF_TARMIN);
        tome.setStudyProgress(7);

        Json json = new Json();
        ItemSaveData restored = json.fromJson(ItemSaveData.class, json.toJson(new ItemSaveData(tome)));

        assertEquals(7, restored.studyProgress);
    }

    @Test
    public void studyProgressSurvivesATomeLeftOnTheGround() {
        Item tome = tome(Item.ItemType.TOME_OF_TARMIN);
        tome.setStudyProgress(9);

        Json json = new Json();
        com.bpm.minotaur.gamedata.ChunkData.ItemData restored = json.fromJson(
                com.bpm.minotaur.gamedata.ChunkData.ItemData.class,
                json.toJson(new com.bpm.minotaur.gamedata.ChunkData.ItemData(tome)));

        assertEquals(9, restored.studyProgress);
    }

    @Test
    public void aTomeNoLongerCarriedUnlocksNothing() {
        Item tome = tome(Item.ItemType.TOME_OF_THE_INITIATE);
        player.beginTomeStudy(tome, field, events);
        player.getInventory().removeItem(tome);

        studyUntilStopped();

        assertNull(player.getPendingTomeChoice());
        assertEquals(1, player.getUnlockedSpellSlots());
    }
}
