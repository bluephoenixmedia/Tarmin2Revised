package com.bpm.minotaur.generation;

import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.Beatitude;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemColor; // Added Import
import com.bpm.minotaur.gamedata.item.ItemCategory;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.ItemVariant;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.monster.MonsterVariant;
import com.bpm.minotaur.managers.SpawnLogger;

import java.util.Map;
import java.util.Random;

public class MonsterFactory {

    private final MonsterDataManager monsterDataManager;
    private final ItemDataManager itemDataManager;
    private final AssetManager assetManager;
    private final NetHackRNG rng;
    private final ItemSpawner itemSpawner;

    public MonsterFactory(MonsterDataManager monsterDataManager, ItemDataManager itemDataManager,
            AssetManager assetManager, NetHackRNG rng, ItemSpawner itemSpawner) {
        this.monsterDataManager = monsterDataManager;
        this.itemDataManager = itemDataManager;
        this.assetManager = assetManager;
        this.rng = rng;
        this.itemSpawner = itemSpawner;
    }

    public Monster createMonster(MonsterType type, int level, SpawnContext ctx) {
        // 1. Get Template & Variant
        MonsterTemplate template = monsterDataManager.getTemplate(type);
        MonsterVariant variant = monsterDataManager.getRandomVariantForMonster(type, level);

        MonsterColor color = (variant != null) ? variant.color : MonsterColor.WHITE;

        // 2. Instantiate (Position will be set by caller)
        Monster monster = new Monster(type, 0, 0, color, monsterDataManager, assetManager);

        // 3. Scale stats by dungeon level, then spawn at full HP.
        // The template already has balanced HP values from monsters.json.
        // Rolling random HP on top of template maxHP caused monsters to spawn at
        // ~25% health because scaleStats() skips the currentHP reset at level 1.
        monster.scaleStats(level);
        monster.setCurrentHP(monster.getMaxHP());

        // 4. Generate Inventory
        generateInventory(monster, level, ctx);

        return monster;
    }

    private void generateInventory(Monster monster, int difficulty, SpawnContext ctx) {
        MonsterTemplate t = monster.getTemplate();
        if (t == null)
            return;

        // 4.1 Weapons
        // Weighted chance based on monster type logic/family
        boolean userOfWeapons = isUserOfWeapons(monster);

        if (userOfWeapons) {
            generateWeapon(monster);
            if (rng.rn2(2) == 0) { // 50% chance for armor if it uses weapons
                generateArmor(monster);
            }
        }

        // 4.2 Utility Items (Potions/Wands)
        // Intelligent monsters get tools
        if (monster.getIntelligence() >= 8) { // Assuming 0-20 scale
            if (rng.rn2(6) == 0) {
                generateMiscItem(monster, ItemCategory.POTION);
            }
            if (rng.rn2(10) == 0) {
                generateMiscItem(monster, ItemCategory.WAND);
            }
        }

        // 4.3 Gold
        if (monster.getIntelligence() > 5) {
            int goldAmount = rng.d(difficulty, 10) + difficulty;
            // We need to create gold item, but ItemDataManager usually handles "Gold" as an
            // item type or currency.
            // Assuming ItemCategory.GOLD exists and spawns a gold pile.
            if (rng.rn2(3) == 0) {
                generateMiscItem(monster, ItemCategory.GOLD);
                // Note: Ideally set quantity here, but ItemSpawner just gives template.
            }
        }
    }

    private boolean isUserOfWeapons(Monster monster) {
        String name = monster.getType().name(); // Use getType() which returns Enum
        // Check for specific humanoid types
        // This list should be more comprehensive or data-driven in future
        return name.contains("ORC") || name.contains("GOBLIN") || name.contains("KOBOLD")
                || name.contains("DWARF") || name.contains("GNOME") || name.contains("ELF")
                || name.contains("HUMAN") || name.contains("SKELETON") || name.contains("ZOMBIE")
                || name.contains("VAMPIRE") || name.contains("LICH") || name.contains("MINOTAUR")
                || name.contains("GIANT") || name.contains("HOBGOBLIN") || name.contains("TROGLODYTE");
    }

    private void generateWeapon(Monster monster) {
        Map.Entry<String, ItemTemplate> entry = itemSpawner.spawnItemByCategory(ItemCategory.WAR_WEAPON);
        if (entry != null) {
            addItemToMonster(monster, entry);
        }
    }

    private void generateArmor(Monster monster) {
        Map.Entry<String, ItemTemplate> entry = itemSpawner.spawnItemByCategory(ItemCategory.ARMOR);
        if (entry != null) {
            addItemToMonster(monster, entry);
        }
    }

    private void generateMiscItem(Monster monster, ItemCategory category) {
        Map.Entry<String, ItemTemplate> entry = itemSpawner.spawnItemByCategory(category);
        if (entry != null) {
            addItemToMonster(monster, entry);
        }
    }

    private void addItemToMonster(Monster monster, Map.Entry<String, ItemTemplate> entry) {
        if (entry == null)
            return;

        ItemType type;
        try {
            type = ItemType.valueOf(entry.getKey());
        } catch (Exception e) {
            return;
        }

        // Logic to instantiate item
        // We use ItemDataManager to create the actual item instance
        // Note: Position is irrelevant for inventory
        ItemVariant variant = itemDataManager.getRandomVariantForItem(type, 1);
        ItemColor c = (variant != null) ? variant.color : ItemColor.TAN; // Changed WOOD to TAN

        Item item = itemDataManager.createItem(type, 0, 0, c, assetManager);
        if (item != null) {
            // Apply Enchantment Logic (NetHack Style)
            applyEnchantment(item);
            monster.getInventory().pickup(item); // Changed from addItem to pickup
        }
    }

    private void applyEnchantment(Item item) {
        if (item.isWeapon() || item.isArmor()) {
            // Uncursed standard: 0
            // Blessed: +1 bias
            // Cursed: -1 bias
            // We need to roll BUC first

            // Simple BUC prob: 90% uncursed, 5% bless, 5% curse
            int bucRoll = rng.rn2(20);
            if (bucRoll == 0)
                item.setBeatitude(Beatitude.BLESSED);
            else if (bucRoll == 1)
                item.setBeatitude(Beatitude.CURSED);
            else
                item.setBeatitude(Beatitude.UNCURSED);

            int enchant = 0;
            if (item.getBeatitude() == Beatitude.BLESSED) {
                enchant = rng.rn2(3) == 0 ? rng.rn2(3) : 1; // Bias +1
            } else if (item.getBeatitude() == Beatitude.CURSED) {
                enchant = -rng.rnd(2); // -1 or -2
            } else {
                if (rng.rn2(40) == 0)
                    enchant = rng.rn2(3); // rare +1/+2
            }

            // Use rne for higher chance? Report says: rne(x)
            // Report: "rn2(x) == 0" loop inside rne.

            item.setEnchantment(enchant);
        }
    }
}
