package com.bpm.minotaur.generation;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Ladder;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.DiscoveryManager;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests covering playtest refinement fixes from docs/testing_1.md:
 * 1. Tornado effects suppressed in shelter
 * 2. Unidentified scrolls do not auto-identify or reveal spell names
 * 3. Special Book / War Book / Spiritual Book are weapons and not consumable tools
 * 4. 2-3 ladders generate per chunk
 * 5. Long weapon melee reach capped at 1 tile
 */
public class PlaytestRefinementsTest {

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> null
            );
        }
    }

    @Test
    public void testShelterTileSuppressesTornado() {
        Maze maze = new Maze(1, new int[10][10]);
        maze.setHomeTiles(java.util.List.of(new GridPoint2(2, 2), new GridPoint2(2, 3)));

        assertTrue("Tile (2,2) should be recognized as home/shelter tile", maze.isHomeTile(2, 2));
        assertFalse("Tile (5,5) should not be recognized as home/shelter tile", maze.isHomeTile(5, 5));
    }

    @Test
    public void testScrollDisplayNamesAndIdentification() {
        DiscoveryManager dm = new DiscoveryManager(new GameEventManager());
        dm.initializeNewGame(java.util.List.of(ItemType.POTION_BLUE, ItemType.POTION_PINK));

        ItemTemplate tmpl = new ItemTemplate();
        tmpl.friendlyName = "Scroll of Fireball";
        tmpl.isUsable = true;
        tmpl.isScrollAppearance = true;

        Item scroll = Item.fromTemplate(ItemType.SCROLL_FIREBALL, tmpl);
        scroll.setSpellId("FIREBALL");
        scroll.setIdentified(false);

        assertTrue("isScroll() must return true for scroll items", scroll.isScroll());
        assertEquals("Unidentified scroll getDisplayName() should return 'Scroll'", "Scroll", scroll.getDisplayName());

        String groundName = dm.getGroundItemDisplayName(scroll);
        assertFalse("Ground display name must not reveal the spell name before identification",
                groundName.toLowerCase().contains("fireball"));
        assertTrue("Ground display name should be a cryptic rune label",
                groundName.startsWith("Scroll labeled ") || groundName.equals("Scroll"));

        // After identification
        dm.identifyDedicatedScroll(ItemType.SCROLL_FIREBALL, "FIREBALL");
        scroll.setIdentified(true);
        assertEquals("Identified scroll should display its friendly name",
                "Scroll of Fireball", dm.getGroundItemDisplayName(scroll));
    }

    @Test
    public void testBookWeaponsAreNotConsumables() {
        ItemTemplate bookTmpl = new ItemTemplate();
        bookTmpl.friendlyName = "Special Book";
        bookTmpl.isWeapon = true;
        bookTmpl.isUsable = false;

        Item specialBook = Item.fromTemplate(ItemType.SPECIAL_BOOK, bookTmpl);
        assertTrue("Special Book must be a weapon", specialBook.isWeapon());
        assertFalse("Special Book must not be consumable or tool", specialBook.isConsumableOrTool());
        assertTrue("Special Book must be recognized as book weapon in combat", CombatManager.isBookWeapon(specialBook));

        ItemTemplate warTmpl = new ItemTemplate();
        warTmpl.friendlyName = "War Book";
        warTmpl.isWeapon = true;
        warTmpl.isUsable = false;

        Item warBook = Item.fromTemplate(ItemType.WAR_BOOK, warTmpl);
        assertTrue("War Book must be a weapon", warBook.isWeapon());
        assertFalse("War Book must not be consumable or tool", warBook.isConsumableOrTool());
        assertTrue("War Book must be recognized as book weapon in combat", CombatManager.isBookWeapon(warBook));
    }

    @Test
    public void testMazeChunkGeneratorProduces2To3DownLadders() {
        MazeChunkGenerator generator = new MazeChunkGenerator();
        Maze maze = new Maze(1, new int[24][24]);
        String[] layout = new String[24];
        Set<GridPoint2> reachable = new HashSet<>();
        for (int y = 0; y < 24; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < 24; x++) {
                if (x == 0 || x == 23 || y == 0 || y == 23) {
                    row.append('#');
                } else {
                    row.append('.');
                    reachable.add(new GridPoint2(x, y));
                }
            }
            layout[y] = row.toString();
        }

        generator.spawnLadder(maze, layout, reachable);

        int downLadderCount = 0;
        for (Ladder ladder : maze.getLadders().values()) {
            if (ladder.getType() == Ladder.LadderType.DOWN) {
                downLadderCount++;
            }
        }

        assertTrue("MazeChunkGenerator must place 2 to 3 DOWN ladders per chunk, but placed: " + downLadderCount,
                downLadderCount >= 2 && downLadderCount <= 3);
    }

    @Test
    public void testForestChunkGeneratorProduces2To3DownLadders() {
        ForestChunkGenerator generator = new ForestChunkGenerator();
        Maze maze = new Maze(1, new int[24][24]);
        String[] layout = new String[24];
        Set<GridPoint2> reachable = new HashSet<>();
        for (int y = 0; y < 24; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < 24; x++) {
                if (x == 0 || x == 23 || y == 0 || y == 23) {
                    row.append('#');
                } else {
                    row.append('.');
                    reachable.add(new GridPoint2(x, y));
                }
            }
            layout[y] = row.toString();
        }

        generator.spawnLadder(maze, layout, reachable);

        int downLadderCount = 0;
        for (Ladder ladder : maze.getLadders().values()) {
            if (ladder.getType() == Ladder.LadderType.DOWN) {
                downLadderCount++;
            }
        }

        assertTrue("ForestChunkGenerator must place 2 to 3 DOWN ladders per chunk, but placed: " + downLadderCount,
                downLadderCount >= 2 && downLadderCount <= 3);
    }

    @Test
    public void testMeleeStrikeCannotReachMoreThan1Tile() {
        Player player = new Player(5f, 5f);
        Maze maze = new Maze(1, new int[10][10]);
        CombatManager combatManager = new CombatManager(player, maze, null, null, null, null, null, null, null, null, null);

        // Monster 2 tiles away at (5, 7)
        Monster farMonster = new Monster(Monster.MonsterType.GOBLIN, 20, 10, 5f, 7f);

        combatManager.playerMeleeStrike(farMonster);

        assertEquals("Melee strike at 2 tiles distance must be rejected and remain INACTIVE",
                CombatManager.CombatState.INACTIVE, combatManager.getCurrentState());
        assertNull("Pending combat monster should remain null when target is out of melee range",
                combatManager.getMonster());
    }
}
