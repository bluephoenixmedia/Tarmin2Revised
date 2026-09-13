package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Alignment;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.spells.SpellDataManager;
import com.bpm.minotaur.gamedata.spells.SpellTemplate;
import com.bpm.minotaur.managers.DivinityManager;
import com.bpm.minotaur.managers.DoomManager;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.WorldManager;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class NetHackDifficultyDistributionTest {

    private WorldManager worldManager;
    private ItemDataManager itemDataManager;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
        worldManager = new WorldManager(GameMode.CLASSIC, Difficulty.MEDIUM, 1, null, null, null, null, null, null);
        itemDataManager = new ItemDataManager();
        DoomManager.getInstance().resetExpeditionTurns();
        DoomManager.getInstance().resetDeaths();
    }

    @Test
    public void testEffectiveDifficultyFormulaAndSanctuaryBuffer() {
        // NetHack formula:
        // EL = depth + floor(D_xy / 2)
        // EDL = floor((EL + playerLevel) / 2)
        // Sanctuary Buffer: D_xy <= 1 on Z = 1 caps EDL at 2

        // 1. Starting Shelter Chunk (0, 0) at Z = 1 with PL = 1
        // EL = 1 + 0 = 1, EDL = (1 + 1) / 2 = 1.
        int edl1 = worldManager.calculateEffectiveDifficulty(new GridPoint2(0, 0), 1, 1);
        assertEquals(1, edl1);

        // 2. High level player in Starting Shelter Chunk (0, 0) at Z = 1 with PL = 15
        // Uncapped EDL = (1 + 15) / 2 = 8 -> Capped at 2 due to Sanctuary Buffer
        int edlSanctuary = worldManager.calculateEffectiveDifficulty(new GridPoint2(0, 0), 1, 15);
        assertEquals(2, edlSanctuary);

        // 3. Adjacent chunk (1, 0) (D_xy = 1) at Z = 1 with PL = 20
        // Uncapped EDL = (1 + 20) / 2 = 10 -> Capped at 2 due to Sanctuary Buffer
        int edlAdj = worldManager.calculateEffectiveDifficulty(new GridPoint2(1, 0), 1, 20);
        assertEquals(2, edlAdj);

        // 4. Overland chunk outside sanctuary (2, 0) (D_xy = 2) at Z = 1 with PL = 10
        // EL = 1 + (2 / 2) = 2. Uncapped EDL = (2 + 10) / 2 = 6. D_xy > 1 -> NOT capped
        int edlOverland = worldManager.calculateEffectiveDifficulty(new GridPoint2(2, 0), 1, 10);
        assertEquals(6, edlOverland);

        // 5. Subterranean Stratum (0, 0) at Z = 3 with PL = 1
        // EL = 3 + 0 = 3, EDL = (3 + 1) / 2 = 2. Z > 1 -> NOT capped
        int edlSub = worldManager.calculateEffectiveDifficulty(new GridPoint2(0, 0), 3, 1);
        assertEquals(2, edlSub);

        // 6. Deep subterranean with distance: Chunk (2, 2) (D_xy = 4) at Z = 4 with PL = 6
        // EL = 4 + 2 = 6, EDL = (6 + 6) / 2 = 6
        int edlDeep = worldManager.calculateEffectiveDifficulty(new GridPoint2(2, 2), 4, 6);
        assertEquals(6, edlDeep);
    }

    @Test
    public void testMonsterLowerBoundPruning() {
        // Pruning rule: skip if template.baseLevel < max(1, ctx.edl() / 4)
        Map<String, MonsterTemplate> registry = new HashMap<>();

        MonsterTemplate rat = new MonsterTemplate();
        rat.baseLevel = 1;
        rat.frequency = 10;
        registry.put("RAT", rat);

        MonsterTemplate goblin = new MonsterTemplate();
        goblin.baseLevel = 3;
        goblin.frequency = 10;
        registry.put("GOBLIN", goblin);

        MonsterTemplate troll = new MonsterTemplate();
        troll.baseLevel = 6;
        troll.frequency = 10;
        registry.put("TROLL", troll);

        NetHackRNG rng = new NetHackRNG(new Random(42));
        MonsterSpawner spawner = new MonsterSpawner(registry, rng);

        // At EDL = 12, minLevel = max(1, 12 / 4) = 3.
        // Rat (baseLevel 1) MUST be pruned.
        SpawnContext ctxHigh = new SpawnContext(4, 12, false, Alignment.NEUTRAL, 0,
                Collections.emptySet(), false, 12, new GridPoint2(0, 0), 1);

        for (int i = 0; i < 50; i++) {
            Optional<Map.Entry<String, MonsterTemplate>> spawn = spawner.spawnRandomMonster(ctxHigh);
            assertTrue(spawn.isPresent());
            assertNotEquals("RAT should be pruned at EDL 12", "RAT", spawn.get().getKey());
            assertTrue(spawn.get().getValue().baseLevel >= 3);
        }

        // At EDL = 2, minLevel = max(1, 2 / 4) = 1. Rat (baseLevel 1) is allowed.
        SpawnContext ctxLow = new SpawnContext(1, 1, false, Alignment.NEUTRAL, 0,
                Collections.emptySet(), false, 2, new GridPoint2(0, 0), 1);

        boolean spawnedRat = false;
        for (int i = 0; i < 50; i++) {
            Optional<Map.Entry<String, MonsterTemplate>> spawn = spawner.spawnRandomMonster(ctxLow);
            if (spawn.isPresent() && "RAT".equals(spawn.get().getKey())) {
                spawnedRat = true;
                break;
            }
        }
        assertTrue("Rat should be eligible at EDL 2", spawnedRat);
    }

    @Test
    public void testOutOfDepthGeneration() {
        // Surface (depth = 1): 0% OOD chance -> monster baseLevel <= edl strictly
        // Subterranean (depth >= 2): 15% OOD chance -> can spawn up to edl + 1 + rn2(3)
        Map<String, MonsterTemplate> registry = new HashMap<>();

        MonsterTemplate level2Monster = new MonsterTemplate();
        level2Monster.baseLevel = 2;
        level2Monster.frequency = 10;
        registry.put("LEVEL2", level2Monster);

        MonsterTemplate level3Monster = new MonsterTemplate();
        level3Monster.baseLevel = 3;
        level3Monster.frequency = 10;
        registry.put("LEVEL3", level3Monster);

        NetHackRNG rng = new NetHackRNG(new Random(12345));
        MonsterSpawner spawner = new MonsterSpawner(registry, rng);

        // Surface Context: EDL = 2, Depth = 1
        SpawnContext surfaceCtx = new SpawnContext(1, 2, false, Alignment.NEUTRAL, 0,
                Collections.emptySet(), false, 2, new GridPoint2(0, 0), 1);
        assertTrue(surfaceCtx.isSurface());

        for (int i = 0; i < 100; i++) {
            Optional<Map.Entry<String, MonsterTemplate>> spawn = spawner.spawnRandomMonster(surfaceCtx);
            if (spawn.isPresent()) {
                assertTrue("Surface cannot spawn OOD monsters above EDL 2",
                        spawn.get().getValue().baseLevel <= 2);
            }
        }

        // Subterranean Context: EDL = 2, Depth = 2
        SpawnContext subCtx = new SpawnContext(2, 2, false, Alignment.NEUTRAL, 0,
                Collections.emptySet(), false, 2, new GridPoint2(0, 0), 1);
        assertTrue(subCtx.isSubterranean());

        boolean sawOod = false;
        for (int i = 0; i < 200; i++) {
            Optional<Map.Entry<String, MonsterTemplate>> spawn = spawner.spawnRandomMonster(subCtx);
            if (spawn.isPresent() && spawn.get().getValue().baseLevel > 2) {
                sawOod = true;
                break;
            }
        }
        assertTrue("Subterranean strata should generate OOD monsters with 15% chance", sawOod);
    }

    @Test
    public void testColorTierEDLBracketsAndOOD() {
        // Brackets:
        // EDL 1-3: TAN (0)
        // EDL 4-6: ORANGE (1)
        // EDL 7-9: BLUE (2)
        // EDL 10-12: WHITE (3)
        // EDL 13-15: PINK (4)
        // EDL 16+: PURPLE (5)
        assertEquals(0, ItemDataManager.getTierIndexForEDL(1));
        assertEquals(0, ItemDataManager.getTierIndexForEDL(3));
        assertEquals(1, ItemDataManager.getTierIndexForEDL(4));
        assertEquals(1, ItemDataManager.getTierIndexForEDL(6));
        assertEquals(2, ItemDataManager.getTierIndexForEDL(7));
        assertEquals(2, ItemDataManager.getTierIndexForEDL(9));
        assertEquals(3, ItemDataManager.getTierIndexForEDL(10));
        assertEquals(3, ItemDataManager.getTierIndexForEDL(12));
        assertEquals(4, ItemDataManager.getTierIndexForEDL(13));
        assertEquals(4, ItemDataManager.getTierIndexForEDL(15));
        assertEquals(5, ItemDataManager.getTierIndexForEDL(16));
        assertEquals(5, ItemDataManager.getTierIndexForEDL(25));

        // Test OOD rolling
        int plusTwoCount = 0;
        int plusOneCount = 0;
        int sameCount = 0;
        int trials = 10000;

        for (int i = 0; i < trials; i++) {
            int rolled = itemDataManager.rollColorTierWithOOD(0);
            if (rolled == 2) plusTwoCount++;
            else if (rolled == 1) plusOneCount++;
            else if (rolled == 0) sameCount++;
        }

        // Expected: ~1% (+2), ~10% (+1), ~89% (same)
        double ratePlusTwo = (double) plusTwoCount / trials;
        double ratePlusOne = (double) plusOneCount / trials;
        assertEquals(0.01, ratePlusTwo, 0.005);
        assertEquals(0.10, ratePlusOne, 0.015);
    }

    @Test
    public void testDynamicSpellScrollGenerationAndScribing() {
        Map<String, ItemTemplate> registry = new HashMap<>();
        ItemTemplate baseScroll = new ItemTemplate();
        baseScroll.isScrollAppearance = true;
        registry.put("SCROLL", baseScroll);

        NetHackRNG rng = new NetHackRNG(new Random(777));
        ItemSpawner spawner = new ItemSpawner(registry, rng);

        // Ensure SpellDataManager fallback is available
        SpellDataManager.getInstance().load();

        // 1. Check Dynamic Scroll generation by EDL
        Map.Entry<String, ItemTemplate> scrollEdl1 = spawner.spawnDynamicSpellScroll(2);
        assertNotNull(scrollEdl1);
        assertEquals("SCROLL", scrollEdl1.getKey());
        assertTrue(scrollEdl1.getValue().friendlyName.startsWith("Scroll of "));
        assertTrue(scrollEdl1.getValue().friendlyName.contains("("));

        // 2. Test Scribing dynamic scroll into Player spellbook
        Player player = new Player(100, 10);
        GameEventManager eventManager = new GameEventManager();
        DivinityManager.getInstance().addDivinities(50);

        Item scrollItem = new Item(Item.ItemType.SCROLL, 0, 0, ItemColor.WHITE, itemDataManager, null);
        scrollItem.setName("Scroll of Magic Arrow (MAGIC_ARROW)");
        scrollItem.setSpellId("MAGIC_ARROW");
        player.getInventory().pickup(scrollItem);

        assertFalse(player.getKnownSpellIds().contains("MAGIC_ARROW"));
        boolean scribed = player.scribeScroll(scrollItem, eventManager);
        assertTrue("Player should successfully transcribe dynamic spell scroll", scribed);
        assertTrue("Player spellbook should now contain MAGIC_ARROW", player.getKnownSpellIds().contains("MAGIC_ARROW"));
    }

    @Test
    public void testDoomClockEscalationAndRestReset() {
        DoomManager doom = DoomManager.getInstance();
        doom.resetExpeditionTurns();
        doom.resetDeaths();

        // Stage 1: Quiescent (0-400 turns, 0 deaths)
        assertEquals(1, doom.getDoomStage());
        assertEquals(300, doom.getSpawnInterval());
        assertEquals(0, doom.getDoomEDLBonus());
        assertFalse(doom.shouldSpawnShadowAffixes());

        // Advance to Stage 2: Restless (401-800 turns)
        for (int i = 0; i < 405; i++) {
            doom.advanceExpeditionTurn();
        }
        assertEquals(2, doom.getDoomStage());
        assertEquals(200, doom.getSpawnInterval());
        assertEquals(1, doom.getDoomEDLBonus());
        assertEquals(1.05f, doom.getEscalationSpeedMultiplier(), 0.001f);

        // Advance to Stage 3: Corrupted (801-1200 turns)
        for (int i = 0; i < 400; i++) {
            doom.advanceExpeditionTurn();
        }
        assertEquals(3, doom.getDoomStage());
        assertEquals(120, doom.getSpawnInterval());
        assertEquals(2, doom.getDoomEDLBonus());
        assertTrue(doom.shouldSpawnShadowAffixes());

        // Advance to Stage 4: Tarmin's Wrath (1201+ turns)
        for (int i = 0; i < 405; i++) {
            doom.advanceExpeditionTurn();
        }
        assertEquals(4, doom.getDoomStage());
        assertEquals(80, doom.getSpawnInterval());
        assertEquals(3, doom.getDoomEDLBonus());

        // Resting at shelter bed resets expedition turns back to Stage 1
        doom.resetExpeditionTurns();
        assertEquals(0, doom.getExpeditionTurns());
        assertEquals(1, doom.getDoomStage());
        assertEquals(300, doom.getSpawnInterval());
        assertEquals(0, doom.getDoomEDLBonus());
    }

    @Test
    public void testMilestoneTarminTomeUnlocks() {
        Player player = new Player(100, 10);
        GameEventManager eventManager = new GameEventManager();

        // Initial unlocked slots
        assertEquals(5, player.getUnlockedSpellSlots());

        // Test using tomes
        Item tomeInitiate = new Item(Item.ItemType.TOME_OF_THE_INITIATE, 0, 0, ItemColor.PURPLE, itemDataManager, null);
        player.getInventory().pickup(tomeInitiate);
        player.useItem(tomeInitiate, eventManager, null, null);
        assertTrue(player.getUnlockedSpellSlots() >= 2);

        Item tomeElements = new Item(Item.ItemType.TOME_OF_ELEMENTS, 0, 0, ItemColor.PURPLE, itemDataManager, null);
        player.getInventory().pickup(tomeElements);
        player.useItem(tomeElements, eventManager, null, null);
        assertTrue(player.getUnlockedSpellSlots() >= 3);

        Item tomeArcane = new Item(Item.ItemType.TOME_OF_THE_ARCANE, 0, 0, ItemColor.PURPLE, itemDataManager, null);
        player.getInventory().pickup(tomeArcane);
        player.useItem(tomeArcane, eventManager, null, null);
        assertTrue(player.getUnlockedSpellSlots() >= 4);

        Item tomeTarmin = new Item(Item.ItemType.TOME_OF_TARMIN, 0, 0, ItemColor.PURPLE, itemDataManager, null);
        player.getInventory().pickup(tomeTarmin);
        player.useItem(tomeTarmin, eventManager, null, null);
        assertEquals(5, player.getUnlockedSpellSlots());
    }
}
