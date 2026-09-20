package com.bpm.minotaur.generation;

import com.bpm.minotaur.gamedata.item.Item;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Firearms are gated by depth.
 *
 * <p>With their authored dice kept, an arquebus rolls 2d8 -- roughly twice a longbow --
 * so a level-1 character finding one would trivialise the early game.
 *
 * <p>The depths come from the level-gated {@code itemSpawnTable} in spawntables.json,
 * which encodes exactly this intent and is never read by any code. Rather than revive
 * all 401 of its entries, or fix the variant {@code minLevel} fallback (which silently
 * returns {@code variants.get(0)} when nothing matches, and would change spawning for
 * every item in the game), the firearm depths are honoured here and nowhere else.
 */
public class FirearmSpawnRuleTest {

    @Test
    public void testAnythingThatIsNotAFirearmIsUnaffected() {
        assertTrue(FirearmSpawnRule.canSpawnAtDepth("SWORD", 1));
        assertTrue(FirearmSpawnRule.canSpawnAtDepth("BOW", 1));
        assertTrue(FirearmSpawnRule.canSpawnAtDepth("QUIVER", 1));
        assertTrue("an unknown id must never be blocked", FirearmSpawnRule.canSpawnAtDepth("NOT_AN_ITEM", 1));
        assertTrue(FirearmSpawnRule.canSpawnAtDepth(null, 1));
    }

    @Test
    public void testNoFirearmAppearsAtTheSurface() {
        for (Item.ItemType type : com.bpm.minotaur.gamedata.firearm.FirearmProfile.all()) {
            assertFalse(type + " must not drop at depth 1",
                    FirearmSpawnRule.canSpawnAtDepth(type.name(), 1));
        }
    }

    @Test
    public void testSidearmsAppearBeforeLongGuns() {
        // The starwheel pistol and blunderbuss were authored at minLevel 2; the
        // arquebus at 8 and the musket at 10.
        assertTrue(FirearmSpawnRule.canSpawnAtDepth("PISTOL_STARWHEEL", 2));
        assertTrue(FirearmSpawnRule.canSpawnAtDepth("BLUNDERBUS", 2));

        assertFalse(FirearmSpawnRule.canSpawnAtDepth("ARQUEBUS", 2));
        assertFalse(FirearmSpawnRule.canSpawnAtDepth("MUSKET", 2));
    }

    @Test
    public void testTheHeaviestGunsAreADeepFind() {
        assertFalse(FirearmSpawnRule.canSpawnAtDepth("ARQUEBUS", 7));
        assertTrue(FirearmSpawnRule.canSpawnAtDepth("ARQUEBUS", 8));

        assertFalse(FirearmSpawnRule.canSpawnAtDepth("MUSKET", 9));
        assertTrue(FirearmSpawnRule.canSpawnAtDepth("MUSKET", 10));
    }

    @Test
    public void testEveryFirearmIsReachableEventually() {
        for (Item.ItemType type : com.bpm.minotaur.gamedata.firearm.FirearmProfile.all()) {
            assertTrue(type + " must become findable at depth",
                    FirearmSpawnRule.canSpawnAtDepth(type.name(), 20));
        }
    }

    @Test
    public void testEveryFirearmHasADepthDeclared() {
        // A firearm added to FirearmProfile without a depth here would silently drop
        // from level 1, which is the failure this gate exists to prevent.
        for (Item.ItemType type : com.bpm.minotaur.gamedata.firearm.FirearmProfile.all()) {
            assertTrue(type + " has no declared minimum depth",
                    FirearmSpawnRule.minDepth(type.name()) > 1);
        }
    }
}
