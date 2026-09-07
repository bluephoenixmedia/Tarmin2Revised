package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.ModifierType;
import com.bpm.minotaur.gamedata.dice.Die;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.item.ShelterChest;
import com.bpm.minotaur.gamedata.player.Player;

import com.bpm.minotaur.gamedata.Inventory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central engine for the Shelter Artisan Workshop.
 * Manages Salvage & Scrapping, Equipment Honing & Refinement,
 * Monster Trophy Infusions, and Bone Relics / Talismans.
 * Seamlessly pools materials from both the Player's Inventory and the Shelter Chest.
 */
public class CraftingManager {

    public static class Recipe {
        public final List<ItemType> inputs;
        public final ItemType output;
        public final int outputCount;
        public final String description;

        public Recipe(List<ItemType> inputs, ItemType output, int outputCount, String description) {
            this.inputs = inputs;
            this.output = output;
            this.outputCount = outputCount;
            this.description = description;
        }
    }

    private final List<Recipe> legacyRecipes = new ArrayList<>();
    private final ItemDataManager itemDataManager;
    private final AssetManager assetManager;
    private final OssuaryManager ossuaryManager;

    public CraftingManager(ItemDataManager itemDataManager, AssetManager assetManager) {
        this.itemDataManager = itemDataManager;
        this.assetManager = assetManager;
        this.ossuaryManager = new OssuaryManager();
        initializeLegacyRecipes();
    }

    private void initializeLegacyRecipes() {
        legacyRecipes.add(new Recipe(List.of(ItemType.STICK, ItemType.SMALL_ROCK), ItemType.AXE, 1, "Stone Axe (Crude)"));
        legacyRecipes.add(new Recipe(List.of(ItemType.STICK, ItemType.FLINT_SHARD), ItemType.KNIFE, 1, "Flint Knife"));
        legacyRecipes.add(new Recipe(List.of(ItemType.BROKEN_HILT, ItemType.METAL_SCRAP), ItemType.KNIFE, 1, "Scrap Shiv"));
        legacyRecipes.add(new Recipe(List.of(ItemType.BONE, ItemType.BONE), ItemType.DART, 5, "Bone Darts"));
        legacyRecipes.add(new Recipe(List.of(ItemType.MEAT), ItemType.COOKED_MEAT, 1, "Cooked Meat"));
        legacyRecipes.add(new Recipe(List.of(ItemType.CHITIN, ItemType.LEATHER_SCRAP), ItemType.HELMET, 1, "Chitin Helm"));
    }

    public List<Recipe> getAllRecipes() {
        return legacyRecipes;
    }

    public boolean canCraft(Inventory inventory, Recipe recipe) {
        if (inventory == null || recipe == null) return false;
        Map<ItemType, Integer> invCounts = new HashMap<>();
        for (Item item : inventory.getMainInventory()) {
            if (item != null) invCounts.put(item.getType(), invCounts.getOrDefault(item.getType(), 0) + 1);
        }
        if (inventory.getRightHand() != null) invCounts.put(inventory.getRightHand().getType(), invCounts.getOrDefault(inventory.getRightHand().getType(), 0) + 1);
        if (inventory.getLeftHand() != null) invCounts.put(inventory.getLeftHand().getType(), invCounts.getOrDefault(inventory.getLeftHand().getType(), 0) + 1);

        Map<ItemType, Integer> reqCounts = new HashMap<>();
        for (ItemType t : recipe.inputs) {
            reqCounts.put(t, reqCounts.getOrDefault(t, 0) + 1);
        }
        for (Map.Entry<ItemType, Integer> req : reqCounts.entrySet()) {
            if (invCounts.getOrDefault(req.getKey(), 0) < req.getValue()) {
                return false;
            }
        }
        return true;
    }

    public Item craft(Inventory inventory, Recipe recipe) {
        if (!canCraft(inventory, recipe)) return null;
        for (ItemType t : recipe.inputs) {
            for (Item it : new ArrayList<>(inventory.getAllItems())) {
                if (it != null && it.getType() == t) {
                    inventory.removeItem(it);
                    break;
                }
            }
        }
        return itemDataManager.createItem(recipe.output, 0, 0, ItemColor.TAN, assetManager);
    }

    public ItemDataManager getItemDataManager() {
        return itemDataManager;
    }

    // =========================================================================
    // 1. UNIFIED STORAGE HELPERS
    // =========================================================================

    public int countMaterial(Player player, ItemType type) {
        return countMaterial(player != null ? player.getInventory() : null, type);
    }

    public int countMaterial(Inventory inventory, ItemType type) {
        if (type == null) return 0;
        int count = 0;
        if (inventory != null) {
            for (Item item : inventory.getAllItems()) {
                if (item != null && item.getType() == type) {
                    count++;
                }
            }
        }
        ShelterChest chest = ShelterChest.getInstance();
        if (chest != null) {
            for (Item item : chest.getItems()) {
                if (item != null && item.getType() == type) {
                    count++;
                }
            }
        }
        return count;
    }

    public boolean consumeMaterial(Player player, ItemType type, int amount) {
        return consumeMaterial(player != null ? player.getInventory() : null, type, amount);
    }

    public boolean consumeMaterial(Inventory inventory, ItemType type, int amount) {
        if (countMaterial(inventory, type) < amount) {
            return false;
        }
        int remaining = amount;

        // 1. Consume from player inventory
        if (inventory != null) {
            List<Item> packItems = new ArrayList<>(inventory.getAllItems());
            for (Item item : packItems) {
                if (remaining <= 0) break;
                if (item != null && item.getType() == type) {
                    inventory.removeItem(item);
                    remaining--;
                }
            }
        }

        // 2. Consume from shelter chest if needed
        if (remaining > 0) {
            ShelterChest chest = ShelterChest.getInstance();
            if (chest != null) {
                List<Item> chestItems = new ArrayList<>(chest.getItems());
                for (Item item : chestItems) {
                    if (remaining <= 0) break;
                    if (item != null && item.getType() == type) {
                        chest.removeItem(item);
                        remaining--;
                    }
                }
            }
        }

        return remaining == 0;
    }

    public boolean depositItem(Player player, Item item) {
        return depositItem(player != null ? player.getInventory() : null, item);
    }

    public boolean depositItem(Inventory inventory, Item item) {
        if (item == null) return false;
        if (inventory != null) {
            if (inventory.pickupToBackpack(item)) {
                return true;
            }
        }
        ShelterChest chest = ShelterChest.getInstance();
        if (chest != null && !chest.isFull()) {
            return chest.addItem(item);
        }
        return false;
    }

    // =========================================================================
    // 2. SALVAGE & SCRAPPING PILLAR
    // =========================================================================

    /**
     * Calculates what physical component types are yielded by salvaging an item.
     */
    public List<ItemType> getSalvageYield(Item item) {
        List<ItemType> yield = new ArrayList<>();
        if (item == null) return yield;

        ItemType type = item.getType();
        if (type == null) return yield;

        // 1. Check for specific Debris & Junk
        switch (type) {
            case BROKEN_HILT:
                yield.add(ItemType.METAL_SCRAP);
                yield.add(ItemType.STICK);
                return yield;
            case BENT_NAIL:
            case RUSTED_CHAIN:
            case METAL_SCRAP:
                yield.add(ItemType.METAL_SCRAP);
                return yield;
            case RUSTY_WEAPON:
            case RUSTY_ARMOR:
                yield.add(ItemType.METAL_SCRAP);
                yield.add(ItemType.METAL_SCRAP);
                return yield;
            case DIRTY_CLOTH:
            case ROTTEN_ROPE:
            case DEAD_PLANT_MATTER:
                yield.add(ItemType.LEATHER_SCRAP);
                return yield;
            case RAT_SKULL:
            case BONES:
            case ANCIENT_FOSSIL:
                yield.add(ItemType.BONE);
                return yield;
            case CERAMIC_SHARD:
            case LOOSE_BRICK:
            case FLINT_SHARD:
                yield.add(ItemType.SMALL_ROCK);
                return yield;
            case CHARRED_WOOD:
            case HOLLOW_LOG:
            case TWISTED_ROOT:
                yield.add(ItemType.STICK);
                return yield;
            case BAT_WING:
            case SPIDER_SILK:
                yield.add(ItemType.LEATHER_SCRAP);
                return yield;
            default:
                break;
        }

        // 2. Equipment Salvage
        if (item.isWeapon()) {
            boolean isWood = type == ItemType.BOW || type == ItemType.CROSSBOW
                    || type == ItemType.CLUB || type == ItemType.QUARTERSTAFF
                    || type.name().contains("BOW") || type.name().contains("CLUB") || type.name().contains("STAFF");
            if (isWood) {
                yield.add(ItemType.STICK);
                yield.add(ItemType.STICK);
                yield.add(ItemType.METAL_SCRAP);
            } else {
                yield.add(ItemType.METAL_SCRAP);
                yield.add(ItemType.METAL_SCRAP);
                yield.add(ItemType.STICK);
                if (item.getBaseValue() > 50 || item.getEnhancementLevel() > 0) {
                    yield.add(ItemType.METAL_SCRAP);
                }
            }
            return yield;
        }

        if (item.isArmor() || item.isShield()) {
            boolean isLeatherOrCloth = type == ItemType.LEATHER_ARMOR || type == ItemType.LEATHER_HELM
                    || type == ItemType.LEATHER_BOOTS || type == ItemType.LEATHER_LEGGINGS
                    || type == ItemType.PADDED_ARMOR || type == ItemType.PADDED_BOOTS
                    || type == ItemType.PADDED_HELMET || type == ItemType.PADDED_LEGGINGS
                    || type == ItemType.HIDE_ARMOR || type == ItemType.HIDE_BOOTS
                    || type == ItemType.HIDE_HELMET || type == ItemType.HIDE_LEGGINGS;
            boolean isChitin = type == ItemType.CHITIN || type.name().contains("CHITIN");
            boolean isBone = type == ItemType.WOOD_BONE_ARMOR || type.name().contains("BONE");

            if (isLeatherOrCloth) {
                yield.add(ItemType.LEATHER_SCRAP);
                yield.add(ItemType.LEATHER_SCRAP);
            } else if (isChitin) {
                yield.add(ItemType.CHITIN);
                yield.add(ItemType.LEATHER_SCRAP);
            } else if (isBone) {
                yield.add(ItemType.BONE);
                yield.add(ItemType.LEATHER_SCRAP);
            } else {
                yield.add(ItemType.METAL_SCRAP);
                yield.add(ItemType.METAL_SCRAP);
                yield.add(ItemType.LEATHER_SCRAP);
                if (item.getBaseValue() > 60 || item.getEnhancementLevel() > 0) {
                    yield.add(ItemType.METAL_SCRAP);
                }
            }
            return yield;
        }

        // Default fallback for any other clutter
        yield.add(ItemType.METAL_SCRAP);
        return yield;
    }

    /**
     * Determines whether an item is considered low-value dungeon clutter/debris
     * suitable for the "Quick-Scrap All Junk" button.
     */
    public boolean isJunkDebris(Item item) {
        if (item == null) return false;
        ItemType type = item.getType();
        if (type == null) return false;

        switch (type) {
            case BROKEN_HILT:
            case BENT_NAIL:
            case RUSTED_CHAIN:
            case METAL_SCRAP:
            case RUSTY_WEAPON:
            case RUSTY_ARMOR:
            case DIRTY_CLOTH:
            case ROTTEN_ROPE:
            case DEAD_PLANT_MATTER:
            case RAT_SKULL:
            case BONES:
            case ANCIENT_FOSSIL:
            case CERAMIC_SHARD:
            case LOOSE_BRICK:
            case FLINT_SHARD:
            case CHARRED_WOOD:
            case HOLLOW_LOG:
            case TWISTED_ROOT:
            case BAT_WING:
            case SPIDER_SILK:
            case SLIME_RESIDUE:
            case MOSS_CLUMP:
            case PARCHMENT_SCRAP:
            case BAT_GUANO:
            case FUNGAL_SPORE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Dismantles a single item into its component scrap materials.
     * Consumes the target item from player inventory or shelter chest,
     * then deposits all yielded scrap items.
     */
    public List<Item> salvageItem(Player player, Item item) {
        return salvageItem(player != null ? player.getInventory() : null, item);
    }

    public List<Item> salvageItem(Inventory inventory, Item item) {
        List<Item> createdScraps = new ArrayList<>();
        if (item == null) return createdScraps;

        List<ItemType> yieldTypes = getSalvageYield(item);

        // Remove item from wherever it resides
        if (inventory != null) {
            inventory.removeItem(item);
        }
        ShelterChest chest = ShelterChest.getInstance();
        if (chest != null) {
            chest.removeItem(item);
        }

        // Create and deposit yields
        for (ItemType scrapType : yieldTypes) {
            Item scrap = itemDataManager.createItem(scrapType, 0, 0, ItemColor.GRAY, assetManager);
            if (scrap != null) {
                depositItem(inventory, scrap);
                createdScraps.add(scrap);
            }
        }

        return createdScraps;
    }

    public int salvageAllJunk(Player player) {
        return salvageAllJunk(player != null ? player.getInventory() : null);
    }

    public int salvageAllJunk(Inventory inventory) {
        List<Item> toScrap = new ArrayList<>();

        if (inventory != null) {
            for (Item item : inventory.getAllItems()) {
                if (isJunkDebris(item)) {
                    toScrap.add(item);
                }
            }
        }

        ShelterChest chest = ShelterChest.getInstance();
        if (chest != null) {
            for (Item item : chest.getItems()) {
                if (isJunkDebris(item)) {
                    toScrap.add(item);
                }
            }
        }

        int count = 0;
        for (Item junk : toScrap) {
            salvageItem(inventory, junk);
            count++;
        }

        return count;
    }

    // =========================================================================
    // 3. HONING & REFINEMENT PILLAR
    // =========================================================================

    public int getHoneCostScrap(Item item) {
        if (item == null) return 0;
        int currentLevel = item.getEnhancementLevel();
        switch (currentLevel) {
            case 0: return 3;   // Tier +1
            case 1: return 6;   // Tier +2
            case 2: return 12;  // Tier +3
            default: return 0;
        }
    }

    public ItemType getHoneMaterialType(Item item) {
        if (item == null) return ItemType.METAL_SCRAP;
        if (item.isArmor() && !item.isShield()) {
            ItemType t = item.getType();
            if (t != null && (t.name().contains("LEATHER") || t.name().contains("PADDED") || t.name().contains("HIDE"))) {
                return ItemType.LEATHER_SCRAP;
            }
        }
        return ItemType.METAL_SCRAP;
    }

    public boolean requiresStrangeMetal(Item item) {
        if (item == null) return false;
        return item.getEnhancementLevel() == 2;
    }

    public boolean canHone(Item item, Player player) {
        return canHone(item, player != null ? player.getInventory() : null);
    }

    public boolean canHone(Item item, Inventory inventory) {
        if (item == null) return false;
        if (!item.isWeapon() && !item.isArmor() && !item.isShield()) return false;
        if (item.getEnhancementLevel() >= 3) return false;

        ItemType scrapType = getHoneMaterialType(item);
        int cost = getHoneCostScrap(item);
        if (countMaterial(inventory, scrapType) < cost) return false;

        if (requiresStrangeMetal(item)) {
            if (countMaterial(inventory, ItemType.STRANGE_METAL) < 1) return false;
        }

        return true;
    }

    public boolean honeItem(Player player, Item item) {
        return honeItem(player != null ? player.getInventory() : null, item);
    }

    public boolean honeItem(Inventory inventory, Item item) {
        if (!canHone(item, inventory)) return false;

        ItemType scrapType = getHoneMaterialType(item);
        int cost = getHoneCostScrap(item);
        boolean needsStrange = requiresStrangeMetal(item);

        // Deduct materials
        if (!consumeMaterial(inventory, scrapType, cost)) return false;
        if (needsStrange) {
            consumeMaterial(inventory, ItemType.STRANGE_METAL, 1);
        }

        int newLevel = item.getEnhancementLevel() + 1;
        item.removeEnhancementModifier();

        if (item.isWeapon()) {
            item.addModifier(new ItemModifier(ModifierType.BONUS_DAMAGE, newLevel, "+" + newLevel));
        } else {
            item.addModifier(new ItemModifier(ModifierType.BONUS_AC, newLevel, "+" + newLevel));
        }

        return true;
    }

    // =========================================================================
    // 4. TROPHY INFUSION PILLAR (SOCKETS)
    // =========================================================================

    public boolean isValidTrophy(Item trophy) {
        if (trophy == null || trophy.getType() == null) return false;
        switch (trophy.getType()) {
            case CLAW:
            case TOOTH:
            case MONSTER_EYE:
            case CHITIN:
            case BLOOD_VIAL:
            case GIB_BILE:
            case STRANGE_METAL:
            case GLOWING_LICHEN:
            case BAT_WING:
            case GIB_ORGAN:
                return true;
            default:
                return false;
        }
    }

    public boolean canInfuse(Item item, Item trophy, Player player) {
        return canInfuse(item, trophy, player != null ? player.getInventory() : null);
    }

    public boolean canInfuse(Item item, Item trophy, Inventory inventory) {
        if (item == null || trophy == null) return false;
        if (!item.isWeapon() && !item.isArmor() && !item.isShield()) return false;
        if (item.getTrophyInfusionCount() >= 2) return false;
        if (!isValidTrophy(trophy)) return false;

        return countMaterial(inventory, trophy.getType()) >= 1;
    }

    public ItemModifier getInfusionModifier(Item item, Item trophy) {
        if (item == null || trophy == null || trophy.getType() == null) return null;
        boolean isWeapon = item.isWeapon();

        switch (trophy.getType()) {
            case CLAW:
                return isWeapon
                        ? new ItemModifier(ModifierType.ADD_BLEED_DAMAGE, 2, "Barbed")
                        : new ItemModifier(ModifierType.BONUS_CRIT_CHANCE, 3, "Spiked");
            case TOOTH:
                return isWeapon
                        ? new ItemModifier(ModifierType.BONUS_CRIT_MULTIPLIER, 5, "Serrated")
                        : new ItemModifier(ModifierType.BONUS_DAMAGE, 1, "Vicious");
            case MONSTER_EYE:
                return isWeapon
                        ? new ItemModifier(ModifierType.BONUS_CRIT_CHANCE, 5, "Keen")
                        : new ItemModifier(ModifierType.BONUS_DODGE, 5, "Foresight");
            case CHITIN:
                return isWeapon
                        ? new ItemModifier(ModifierType.BONUS_ABSORB, 1, "Reinforced")
                        : new ItemModifier(ModifierType.RESIST_POISON, 15, "Carapace");
            case BLOOD_VIAL:
            case GIB_BILE:
                return isWeapon
                        ? new ItemModifier(ModifierType.ADD_POISON_DAMAGE, 2, "Venomous")
                        : new ItemModifier(ModifierType.RESIST_DISEASE, 15, "Inoculated");
            case STRANGE_METAL:
            case GLOWING_LICHEN:
                return isWeapon
                        ? new ItemModifier(ModifierType.ADD_FIRE_DAMAGE, 2, "Flaming")
                        : new ItemModifier(ModifierType.RESIST_FIRE, 15, "Insulated");
            case BAT_WING:
            case GIB_ORGAN:
                return isWeapon
                        ? new ItemModifier(ModifierType.ADD_DARK_DAMAGE, 2, "Shadow")
                        : new ItemModifier(ModifierType.RESIST_DARK, 15, "Shrouded");
            default:
                return new ItemModifier(ModifierType.BONUS_LUCK, 1, "Lucky");
        }
    }

    public boolean infuseTrophy(Player player, Item item, Item trophy) {
        return infuseTrophy(player != null ? player.getInventory() : null, item, trophy);
    }

    public boolean infuseTrophy(Inventory inventory, Item item, Item trophy) {
        if (!canInfuse(item, trophy, inventory)) return false;

        ItemModifier mod = getInfusionModifier(item, trophy);
        if (mod == null) return false;

        if (!consumeMaterial(inventory, trophy.getType(), 1)) return false;

        item.addModifier(mod);
        return true;
    }

    // =========================================================================
    // 5. OSSUARY & BONE RELIC PILLAR
    // =========================================================================

    public OssuaryManager getOssuaryManager() {
        return ossuaryManager;
    }

    public Die craftBoneDie(Item structure, Item edge, Item core) {
        return ossuaryManager.craftBoneDie(structure, edge, core);
    }

    public boolean canForgeTalisman(Player player, Item bone, Item trophy) {
        return canForgeTalisman(player != null ? player.getInventory() : null, bone, trophy);
    }

    public boolean canForgeTalisman(Inventory inventory, Item bone, Item trophy) {
        if (bone == null || trophy == null) return false;
        boolean isBone = bone.getType() == ItemType.BONE || bone.getType() == ItemType.BONES || bone.getType() == ItemType.RAT_SKULL;
        if (!isBone) return false;
        if (!isValidTrophy(trophy)) return false;

        if (countMaterial(inventory, bone.getType()) < 1) return false;
        if (countMaterial(inventory, trophy.getType()) < 1) return false;
        if (countMaterial(inventory, ItemType.METAL_SCRAP) < 1) return false;

        return true;
    }

    public Item forgeBoneTalisman(Player player, Item bone, Item trophy) {
        return forgeBoneTalisman(player != null ? player.getInventory() : null, bone, trophy);
    }

    public Item forgeBoneTalisman(Inventory inventory, Item bone, Item trophy) {
        if (!canForgeTalisman(inventory, bone, trophy)) return null;

        if (!consumeMaterial(inventory, bone.getType(), 1)) return null;
        if (!consumeMaterial(inventory, trophy.getType(), 1)) return null;
        if (!consumeMaterial(inventory, ItemType.METAL_SCRAP, 1)) return null;

        // Create base amulet
        Item talisman = itemDataManager.createItem(ItemType.AMULET, 0, 0, ItemColor.WHITE, assetManager);
        if (talisman == null) return null;

        switch (trophy.getType()) {
            case MONSTER_EYE:
                talisman.addModifier(new ItemModifier(ModifierType.BONUS_WISDOM, 2, "Gazing"));
                talisman.addModifier(new ItemModifier(ModifierType.BONUS_CRIT_CHANCE, 5, "Insight"));
                break;
            case CLAW:
                talisman.addModifier(new ItemModifier(ModifierType.BONUS_STRENGTH, 2, "Feral"));
                talisman.addModifier(new ItemModifier(ModifierType.BONUS_CRIT_MULTIPLIER, 5, "Rending"));
                break;
            case TOOTH:
                talisman.addModifier(new ItemModifier(ModifierType.BONUS_AGILITY, 2, "Predator"));
                talisman.addModifier(new ItemModifier(ModifierType.BONUS_DODGE, 5, "Cunning"));
                break;
            case CHITIN:
                talisman.addModifier(new ItemModifier(ModifierType.BONUS_CONSTITUTION, 2, "Carapace"));
                talisman.addModifier(new ItemModifier(ModifierType.BONUS_ABSORB, 1, "Ward"));
                break;
            case BLOOD_VIAL:
            case GIB_BILE:
                talisman.addModifier(new ItemModifier(ModifierType.BONUS_MAX_HP, 10, "Vital"));
                talisman.addModifier(new ItemModifier(ModifierType.RESIST_POISON, 15, "Inoculated"));
                break;
            default:
                talisman.addModifier(new ItemModifier(ModifierType.BONUS_LUCK, 2, "Fortune"));
                break;
        }

        depositItem(inventory, talisman);
        return talisman;
    }
}
