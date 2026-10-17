package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.history.town.Town;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Test;

import static org.junit.Assert.*;

/** Plan T4.2, T4.4: a town raised in its chunk, its folk and its guards. */
public class TownBuilderTest {

    private final BattleDirector.Recruiter recruit = (type, x, y) -> {
        Monster m = new Monster(Monster.MonsterType.valueOf(type), 20, 12);
        m.getPosition().set(x, y);
        return m;
    };

    private static int folkStanding(Maze maze) {
        int n = 0;
        for (Scenery s : maze.getScenery().values()) if (s.isTownsfolk()) n++;
        return n;
    }

    @Test
    public void everyKeeperButTheMerchantStandsAtTheirPlaceAndTalks() {
        Town town = Town.of(5L, Town.keyOf(2, 3, 4));
        Maze maze = new Maze(2, new int[36][36]);
        TownBuilder.populate(maze, town, null, null, null, false, recruit);
        assertEquals(town.folk.size() - 1, folkStanding(maze));
        for (Scenery s : maze.getScenery().values()) {
            assertTrue(s.isImpassable());
            assertEquals(town.key, s.getTownKey());
            assertNotEquals(Town.Role.MERCHANT, town.folk.get(s.getFolkIndex()).role);
        }
        TownBuilder.populate(maze, town, null, null, null, false, recruit);
        assertEquals("standing them again does not double them", town.folk.size() - 1, folkStanding(maze));
    }

    @Test
    public void guardsKeepThePeaceUnlessTheTownIsHostile() {
        Town town = Town.of(6L, Town.keyOf(3, 1, 1));
        Maze calm = new Maze(3, new int[36][36]);
        TownBuilder.populate(calm, town, null, null, null, false, recruit);
        int guards = 0;
        for (Monster m : calm.getMonsters().values()) {
            assertEquals(town.key, m.getTownKey());
            assertTrue(m.isPeaceful());
            guards++;
        }
        assertEquals(TownBuilder.GUARD_POSTS.length, guards);

        Maze angry = new Maze(3, new int[36][36]);
        TownBuilder.populate(angry, town, null, null, null, true, recruit);
        for (Monster m : angry.getMonsters().values()) assertFalse(m.isPeaceful());
    }

    @Test
    public void aSavedTownKeepsNeitherItsFolkNorItsGuards() {
        Town town = Town.of(7L, Town.keyOf(4, -2, 5));
        Maze maze = new Maze(4, new int[36][36]);
        TownBuilder.populate(maze, town, null, null, null, false, recruit);
        ChunkData saved = new ChunkData(maze);
        assertTrue(saved.monsters.isEmpty());
        assertTrue(saved.scenery.isEmpty());
    }
}
