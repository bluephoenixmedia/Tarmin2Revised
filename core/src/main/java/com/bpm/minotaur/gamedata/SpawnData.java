// Path: core/src/main/java/com/bpm/minotaur/gamedata/SpawnData.java
package com.bpm.minotaur.gamedata;

import java.util.Arrays;
import java.util.List;

public class SpawnData {

    // A simple record to hold monster spawning rules.
    public record MonsterSpawnInfo(Monster.MonsterType type, int minLevel, int maxLevel) {}

    // A simple record to hold item spawning rules.
    public record ItemSpawnInfo(Item.ItemType type, int minLevel, int maxLevel) {}

    // A simple record to hold treasure spawning rules
    public record TreasureSpawnInfo(Item.ItemType type, int baseValue, int levelModifier) {}

    // --- MONSTER SPAWN LISTS ---
    // Monsters are tiered based on the original game's classifications.

    public static final List<MonsterSpawnInfo> BAD_MONSTERS = Arrays.asList(
        new MonsterSpawnInfo(Monster.MonsterType.GIANT_ANT, 1, 4),
        new MonsterSpawnInfo(Monster.MonsterType.DWARF, 1, 5),
        new MonsterSpawnInfo(Monster.MonsterType.GIANT_SCORPION, 2, 6),
        new MonsterSpawnInfo(Monster.MonsterType.GIANT_SNAKE, 2, 7)
    );

    public static final List<MonsterSpawnInfo> NASTY_MONSTERS = Arrays.asList(
        new MonsterSpawnInfo(Monster.MonsterType.GHOUL, 4, 8),
        new MonsterSpawnInfo(Monster.MonsterType.SKELETON, 4, 9),
        new MonsterSpawnInfo(Monster.MonsterType.CLOAKED_SKELETON, 5, 10)
    );

    public static final List<MonsterSpawnInfo> HORRIBLE_MONSTERS = Arrays.asList(
        new MonsterSpawnInfo(Monster.MonsterType.ALLIGATOR, 7, 12),
        new MonsterSpawnInfo(Monster.MonsterType.WRAITH, 7, 12),
        new MonsterSpawnInfo(Monster.MonsterType.GIANT, 8, 13),
        new MonsterSpawnInfo(Monster.MonsterType.DRAGON, 10, 14),
        new MonsterSpawnInfo(Monster.MonsterType.MINOTAUR, 12, 16) // The Minotaur only appears on level 12 or deeper.
    );


    // --- ITEM SPAWN LISTS ---
    // Items are also tiered to provide better loot in deeper levels.

    public static final List<ItemSpawnInfo> TIER1_ITEMS = Arrays.asList(
        new ItemSpawnInfo(Item.ItemType.SMALL_POTION, 1, 5),
        new ItemSpawnInfo(Item.ItemType.LARGE_POTION, 1, 5),
        new ItemSpawnInfo(Item.ItemType.SCROLL, 2, 6),
        new ItemSpawnInfo(Item.ItemType.BOW, 1, 4),
        new ItemSpawnInfo(Item.ItemType.KNIFE, 1, 5),
        new ItemSpawnInfo(Item.ItemType.AXE, 2, 6)
    );

    public static final List<ItemSpawnInfo> TIER2_ITEMS = Arrays.asList(
        new ItemSpawnInfo(Item.ItemType.BOOK, 4, 8),
        new ItemSpawnInfo(Item.ItemType.SMALL_SHIELD, 4, 9),
        new ItemSpawnInfo(Item.ItemType.HELMET, 5, 10),
        new ItemSpawnInfo(Item.ItemType.DART, 3, 7),
        new ItemSpawnInfo(Item.ItemType.SPEAR, 4, 8),
        new ItemSpawnInfo(Item.ItemType.CROSSBOW, 5, 9)
    );

    public static final List<ItemSpawnInfo> TIER3_ITEMS = Arrays.asList(
        new ItemSpawnInfo(Item.ItemType.SMALL_FIREBALL, 7, 12),
        new ItemSpawnInfo(Item.ItemType.SMALL_LIGHTNING, 7, 12),
        new ItemSpawnInfo(Item.ItemType.LARGE_FIREBALL, 9, 14),
        new ItemSpawnInfo(Item.ItemType.LARGE_LIGHTNING, 9, 14),
        new ItemSpawnInfo(Item.ItemType.RING_BLUE, 8, 13),
        new ItemSpawnInfo(Item.ItemType.RING_PINK, 10, 15),
        new ItemSpawnInfo(Item.ItemType.RING_PURPLE, 12, 16)
    );

    // Containers are tiered separately. Better containers hold better loot.
    public static final List<ItemSpawnInfo> TIER1_CONTAINERS = Arrays.asList(
        new ItemSpawnInfo(Item.ItemType.MONEY_BELT, 1, 4),
        new ItemSpawnInfo(Item.ItemType.SMALL_BAG, 1, 5)
    );

    public static final List<ItemSpawnInfo> TIER2_CONTAINERS = Arrays.asList(
        new ItemSpawnInfo(Item.ItemType.BOX, 3, 7),
        new ItemSpawnInfo(Item.ItemType.MEDIUM_PACK, 4, 8)
    );

    public static final List<ItemSpawnInfo> TIER3_CONTAINERS = Arrays.asList(
        new ItemSpawnInfo(Item.ItemType.LARGE_BAG, 6, 10),
        new ItemSpawnInfo(Item.ItemType.REGULAR_CHEST, 8, 12)
    );

    // Treasures that can be found inside containers.
    public static final List<TreasureSpawnInfo> TREASURES = Arrays.asList(
        new TreasureSpawnInfo(Item.ItemType.COINS, 10, 2),
        new TreasureSpawnInfo(Item.ItemType.NECKLACE, 20, 3),
        new TreasureSpawnInfo(Item.ItemType.INGOT, 50, 5),
        new TreasureSpawnInfo(Item.ItemType.LAMP, 100, 8),
        new TreasureSpawnInfo(Item.ItemType.CHALICE, 120, 10),
        new TreasureSpawnInfo(Item.ItemType.CROWN, 300, 12)
    );
}
