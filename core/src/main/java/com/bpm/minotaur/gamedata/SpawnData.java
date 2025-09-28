// Path: core/src/main/java/com/bpm/minotaur/gamedata/SpawnData.java
package com.bpm.minotaur.gamedata;

import java.util.Arrays;
import java.util.List;

public class SpawnData {

    // A simple record to hold monster spawning rules.
    public record MonsterSpawnInfo(Monster.MonsterType type, int minLevel) {}

    // A simple record to hold item spawning rules.
    public record ItemSpawnInfo(Item.ItemType type, int minLevel) {}

    // --- MONSTER SPAWN LISTS ---
  // Monsters are tiered based on the original game's classifications. [cite: 1013, 1024, 1076]

    public static final List<MonsterSpawnInfo> BAD_MONSTERS = Arrays.asList(
        new MonsterSpawnInfo(Monster.MonsterType.GIANT_ANT, 1),
        new MonsterSpawnInfo(Monster.MonsterType.DWARF, 1),
        new MonsterSpawnInfo(Monster.MonsterType.GIANT_SCORPION, 2),
        new MonsterSpawnInfo(Monster.MonsterType.GIANT_SNAKE, 2)
    );

    public static final List<MonsterSpawnInfo> NASTY_MONSTERS = Arrays.asList(
        new MonsterSpawnInfo(Monster.MonsterType.GHOUL, 4),
        new MonsterSpawnInfo(Monster.MonsterType.SKELETON, 4),
        new MonsterSpawnInfo(Monster.MonsterType.CLOAKED_SKELETON, 5)
    );

    public static final List<MonsterSpawnInfo> HORRIBLE_MONSTERS = Arrays.asList(
        new MonsterSpawnInfo(Monster.MonsterType.ALLIGATOR, 7),
        new MonsterSpawnInfo(Monster.MonsterType.WRAITH, 7),
        new MonsterSpawnInfo(Monster.MonsterType.GIANT, 8),
        new MonsterSpawnInfo(Monster.MonsterType.DRAGON, 10),
        new MonsterSpawnInfo(Monster.MonsterType.MINOTAUR, 12) // The Minotaur only appears on level 12 or deeper. [cite: 868]
    );


    // --- ITEM SPAWN LISTS ---
    // Items are also tiered to provide better loot in deeper levels.

    public static final List<ItemSpawnInfo> TIER1_ITEMS = Arrays.asList(
        new ItemSpawnInfo(Item.ItemType.POTION_HEALING, 1),
        new ItemSpawnInfo(Item.ItemType.POTION_STRENGTH, 1),
        new ItemSpawnInfo(Item.ItemType.SCROLL, 2)
    );

    public static final List<ItemSpawnInfo> TIER2_ITEMS = Arrays.asList(
        new ItemSpawnInfo(Item.ItemType.BOOK, 4),
        new ItemSpawnInfo(Item.ItemType.SHIELD, 4),
        new ItemSpawnInfo(Item.ItemType.HELMET, 5)
    );

    public static final List<ItemSpawnInfo> TIER3_ITEMS = Arrays.asList(
        new ItemSpawnInfo(Item.ItemType.SMALL_FIREBALL, 7),
        new ItemSpawnInfo(Item.ItemType.SMALL_LIGHTNING_BOLT, 7),
        new ItemSpawnInfo(Item.ItemType.RING_BLUE, 8)
    );

    // Containers are tiered separately. [cite_start]Better containers hold better loot. [cite: 951]
    public static final List<ItemSpawnInfo> TIER1_CONTAINERS = Arrays.asList(
        new ItemSpawnInfo(Item.ItemType.MONEY_BELT, 1),
        new ItemSpawnInfo(Item.ItemType.SMALL_BAG, 1)
    );

    public static final List<ItemSpawnInfo> TIER2_CONTAINERS = Arrays.asList(
        new ItemSpawnInfo(Item.ItemType.BOX, 3),
        new ItemSpawnInfo(Item.ItemType.PACK, 4)
    );

    public static final List<ItemSpawnInfo> TIER3_CONTAINERS = Arrays.asList(
        new ItemSpawnInfo(Item.ItemType.LARGE_BAG, 6),
        new ItemSpawnInfo(Item.ItemType.CHEST, 8)
    );

    // Treasures that can be found inside containers.
    public static final List<Item.ItemType> TREASURES = Arrays.asList(
        Item.ItemType.COINS,
        Item.ItemType.NECKLACE,
        Item.ItemType.INGOT,
        Item.ItemType.LAMP,
        Item.ItemType.CHALICE,
        Item.ItemType.CROWN
    );
}
