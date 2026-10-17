package com.bpm.minotaur.gamedata.history.town;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.generation.StratumMap;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.Test;

import static org.junit.Assert.*;

/** Plan T4.2, D37: where towns stand, and who keeps them. */
public class TownTest {

    @Test
    public void townsStandOnlyOnStrataTwoToFourInStrataThatAllowThem() {
        int towns = 0, eligible = 0;
        for (int level = 1; level <= 6; level++) {
            for (int x = -30; x <= 30; x++) {
                for (int y = -30; y <= 30; y++) {
                    GridPoint2 c = new GridPoint2(x, y);
                    boolean allows = StratumMap.of(5L, c, level).allowsTowns;
                    boolean town = TownSites.isTown(5L, c, level);
                    if (town) {
                        towns++;
                        assertTrue(level >= TownSites.MIN_LEVEL && level <= TownSites.MAX_LEVEL);
                        assertTrue("town on a stratum that allows one", allows);
                    }
                    if (allows && level >= TownSites.MIN_LEVEL && level <= TownSites.MAX_LEVEL) eligible++;
                }
            }
        }
        float perTown = (float) eligible / towns;
        assertTrue("one town per " + perTown + " eligible chunks", perTown >= 10 && perTown <= 15);
    }

    @Test
    public void aTownIsTheSameTownEveryTime() {
        Town a = Town.of(77L, Town.keyOf(3, 4, -2));
        Town b = Town.of(77L, Town.keyOf(3, 4, -2));
        assertEquals(a.name, b.name);
        assertEquals(a.allegiance, b.allegiance);
        assertEquals(a.folk.size(), b.folk.size());
        for (int i = 0; i < a.folk.size(); i++) assertEquals(a.folk.get(i).name, b.folk.get(i).name);
    }

    @Test
    public void aTownKeepsFourToEightFolkWithAMerchantAndAReeve() {
        for (int i = 0; i < 200; i++) {
            Town t = Town.of(i, Town.keyOf(2 + i % 3, i, -i));
            assertTrue(t.folk.size() >= Town.MIN_FOLK && t.folk.size() <= Town.MAX_FOLK);
            assertNotNull(t.folk(Town.Role.MERCHANT));
            assertNotNull("every town has someone with work for the player", t.folk(Town.Role.QUESTGIVER));
            for (Town.Folk f : t.folk) {
                assertEquals(f.title(), UiGlyphs.sanitize(f.title()));
                com.bpm.minotaur.gamedata.monster.Monster.MonsterType.valueOf(f.sprite);
            }
            assertEquals(t.name, UiGlyphs.sanitize(t.name));
        }
    }

    @Test
    public void theMortalPowersFeud() {
        assertTrue(Allegiance.GOBLIN_CLANS.feudsWith(Allegiance.REFUGEE_COUNCIL));
        assertFalse(Allegiance.GOBLIN_CLANS.feudsWith(Allegiance.GOBLIN_CLANS));
        assertFalse("the council and the covenant keep an uneasy peace", Allegiance.REFUGEE_COUNCIL.feudsWith(Allegiance.OUTCAST_COVENANT));
    }

    @Test
    public void someTownsAreHostileToStrangersFromTheStart() {
        Standing s = new Standing();
        int hostile = 0;
        for (int i = 0; i < 400; i++) {
            if (s.isHostile(Town.of(i, Town.keyOf(4, i, i)))) hostile++;
        }
        assertTrue("hostile at first: " + hostile + "/400", hostile > 30 && hostile < 100);
    }
}
