package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.Megabeast;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/** Plan T3.3: a megabeast of the history, standing in the strata. */
public class BeastForgeTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static Monster forged(Megabeast b, int hp) {
        Monster m = new Monster(Monster.MonsterType.DRAGON, 80, 15);
        m.setDamageDice("3d10+5");
        BeastForge.dress(m, b, catalog.beasts(), hp);
        return m;
    }

    @Test
    public void aBeastIsNamedHugeAndAnswersToNoHouse() {
        HistoryManager h = HistoryManager.create(6L, catalog);
        Megabeast b = h.world().megabeasts().get(0);
        Monster m = forged(b, Integer.MAX_VALUE);
        assertEquals(b.name, m.getDisplayName());
        assertEquals(b.id, m.getMegabeastId());
        assertEquals(Faction.CHAOS_BERSERK, m.getFaction());
        assertEquals(BeastForge.wholeHp(b, catalog.beasts()), m.getMaxHP());
        assertEquals(m.getMaxHP(), m.getCurrentHP());
        assertTrue("several hundred hit points", m.getMaxHP() >= 300);
        assertEquals(Monster.Affinity.WEAK, m.getAffinity(DamageType.valueOf(b.weakness)));
    }

    @Test
    public void aBeastCarriesItsWounds() {
        Megabeast b = HistoryManager.create(7L, catalog).world().megabeasts().get(1);
        assertEquals(150, forged(b, 150).getCurrentHP());
    }

    @Test
    public void aChunkNeverKeepsAMegabeast() {
        Maze maze = new Maze(3, new int[12][12]);
        Monster goblin = new Monster(Monster.MonsterType.GOBLIN, 8, 10);
        goblin.getPosition().set(2, 2);
        maze.addMonster(goblin);
        Monster beast = forged(HistoryManager.create(8L, catalog).world().megabeasts().get(0), Integer.MAX_VALUE);
        beast.getPosition().set(6, 6);
        maze.addMonster(beast);
        assertEquals(1, new ChunkData(maze).monsters.size());
    }

    @Test
    public void slayingABeastIsChronicled() {
        HistoryManager h = HistoryManager.create(9L, catalog);
        Megabeast b = h.world().megabeasts().get(0);
        String told = SealCourt.onSlain(forged(b, 10), h, com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance());
        assertTrue(told, told.contains(b.name));
        assertFalse(b.isAlive());
        assertEquals(EventType.MEGABEAST_SLAIN, h.world().events().get(h.world().events().size() - 1).type);
    }

    @Test
    public void aDeeperLairMakesAGreaterBeast() {
        Megabeast shallow = new Megabeast(0, "A", "wyrm", "bone", "cinders", "FIRE", 2, 0);
        Megabeast deep = new Megabeast(1, "B", "wyrm", "bone", "cinders", "FIRE", 8, 0);
        assertTrue(BeastForge.wholeHp(deep, catalog.beasts()) > BeastForge.wholeHp(shallow, catalog.beasts()));
        assertTrue(forged(deep, Integer.MAX_VALUE).getDamageDice().compareTo(forged(shallow, Integer.MAX_VALUE).getDamageDice()) != 0);
    }
}
