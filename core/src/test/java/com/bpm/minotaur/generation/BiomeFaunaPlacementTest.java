package com.bpm.minotaur.generation;

import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;

import static org.junit.Assert.*;

/**
 * The biomes' own creatures (#129): the Lizard Warrior in the Lakelands, the Giant Centipede and
 * the Beetlescratch in the Desert, on top of the ordinary spawns, once their depth is reached.
 */
public class BiomeFaunaPlacementTest {

    private static SpawnTableData table;

    @BeforeClass
    public static void load() throws Exception {
        File f = new File("../assets/data/spawntables.json");
        if (!f.exists()) f = new File("assets/data/spawntables.json");
        table = new Json().fromJson(SpawnTableData.class, new FileReader(f));
    }

    @Test
    public void theLakelandsHaveTheirLizardWarriors() {
        assertFalse(LakelandsChunkGenerator.faunaFor(table, 4).contains(MonsterType.LIZARD_WARRIOR));
        assertTrue(LakelandsChunkGenerator.faunaFor(table, 5).contains(MonsterType.LIZARD_WARRIOR));
    }

    @Test
    public void theDesertHasItsCentipedesAndBeetlescratch() {
        assertTrue(DesertChunkGenerator.faunaFor(table, 3).contains(MonsterType.GIANT_CENTIPEDE));
        assertFalse(DesertChunkGenerator.faunaFor(table, 5).contains(MonsterType.BEETLESCRATCH));
        assertTrue(DesertChunkGenerator.faunaFor(table, 6).contains(MonsterType.BEETLESCRATCH));
    }
}
