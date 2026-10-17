package com.bpm.minotaur.gamedata;

import com.bpm.minotaur.gamedata.bones.BonesData;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * A slain monster and a dead hero leave different things behind.
 *
 * <p>They used to share one scenery type, and "monster corpse" was defined as
 * "a hero-bones corpse whose bonesData happens to be null". That definition is
 * what let a hero's remains degrade into butcherable meat the moment the bones
 * data failed to survive a save.
 */
public class MonsterRemainsTest {

    @Test
    public void monsterRemainsAreTheirOwnKindOfThing() {
        Scenery remains = new Scenery(Scenery.SceneryType.MONSTER_REMAINS, 3, 4, "images/monsters/goblin.png");

        assertTrue("Remains must be reachable by the corpse interaction", remains.isCorpse());
        assertTrue(remains.isMonsterRemains());
        assertFalse("A monster is not a dead hero", remains.isDecomposingCorpse());
    }

    @Test
    public void heroBonesAreNotMonsterRemains() {
        Scenery bones = new Scenery(Scenery.SceneryType.DECOMPOSING_CORPSE, 1, 1,
                "images/scenery/decomposing_corpse.png");

        assertTrue(bones.isCorpse());
        assertTrue(bones.isDecomposingCorpse());
        assertFalse("A hero's bones must never be classified as a carcass",
                bones.isMonsterRemains());
    }

    @Test
    public void aGorePileIsRemainsToo() {
        Scenery gore = new Scenery(Scenery.SceneryType.GORE_PILE, 2, 2, "images/gore/gib3.png");

        assertTrue(gore.isCorpse());
        assertTrue(gore.isMonsterRemains());
        assertFalse(gore.isDecomposingCorpse());
    }

    @Test
    public void remainsNeverBlockACorridor() {
        // The hero-bones defaults set impassable, and every monster spawn had to
        // undo it by hand. A kill in a corridor must not wall the player in.
        assertFalse(new Scenery(Scenery.SceneryType.MONSTER_REMAINS, 0, 0, "x.png").isImpassable());
        assertFalse(new Scenery(Scenery.SceneryType.GORE_PILE, 0, 0, "x.png").isImpassable());
    }

    @Test
    public void heroBonesSurviveASaveRoundTrip() {
        // The live bug: SceneryData never carried bonesData, and chunks rebuild
        // on every gate and ladder -- so ghost, epitaph and grave loot were lost
        // almost immediately, not merely on quit.
        Scenery bones = new Scenery(Scenery.SceneryType.DECOMPOSING_CORPSE, 5, 6,
                "images/scenery/decomposing_corpse.png");
        BonesData data = new BonesData();
        data.playerName = "Dennis the Unlucky";
        bones.setBonesData(data);

        ChunkData.SceneryData saved = new ChunkData.SceneryData(bones);

        assertNotNull("Bones data must be written to the save", saved.bonesData);
        assertEquals("Dennis the Unlucky", saved.bonesData.playerName);
    }

    @Test
    public void monsterRemainsCarryTheCreatureTheyCameFrom() {
        Scenery remains = new Scenery(Scenery.SceneryType.MONSTER_REMAINS, 7, 8, "images/monsters/ogre.png");
        remains.setCorpseMonsterName("Ogre");

        ChunkData.SceneryData saved = new ChunkData.SceneryData(remains);

        assertEquals(Scenery.SceneryType.MONSTER_REMAINS, saved.type);
        assertEquals("Ogre", saved.corpseMonsterName);
        assertNull("A monster leaves no bones data", saved.bonesData);
    }
}
