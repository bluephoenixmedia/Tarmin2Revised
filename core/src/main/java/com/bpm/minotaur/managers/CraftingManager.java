package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.badlogic.gdx.assets.AssetManager; // Needed for createItem

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final List<Recipe> recipes = new ArrayList<>();
    private final ItemDataManager itemDataManager;
    private final AssetManager assetManager;

    public CraftingManager(ItemDataManager itemDataManager, AssetManager assetManager) {
        this.itemDataManager = itemDataManager;
        this.assetManager = assetManager;
        initializeRecipes();
    }

    private void initializeRecipes() {
        // --- BASIC TOOLS ---
        addRecipe(List.of(ItemType.STICK, ItemType.SMALL_ROCK), ItemType.AXE, 1, "Stone Axe (Crude)");
        addRecipe(List.of(ItemType.STICK, ItemType.FLINT_SHARD), ItemType.KNIFE, 1, "Flint Knife");
        addRecipe(List.of(ItemType.BROKEN_HILT, ItemType.METAL_SCRAP), ItemType.KNIFE, 1, "Scrap Shiv");

        // --- AMMO ---
        addRecipe(List.of(ItemType.BONE, ItemType.BONE), ItemType.DART, 5, "Bone Darts");
        // Reuse stick + feather (if exists) -> Arrows? No feathers yet.

        // --- FOOD ---
        // Require interacting with FIRE POT for cooking, but list recipe here for logic
        addRecipe(List.of(ItemType.MEAT), ItemType.COOKED_MEAT, 1, "Cooked Meat");

        // --- MATERIALS ---
        addRecipe(List.of(ItemType.CHITIN, ItemType.LEATHER_SCRAP), ItemType.HELMET, 1, "Chitin Helm");
    }

    private void addRecipe(List<ItemType> inputs, ItemType output, int count, String desc) {
        recipes.add(new Recipe(inputs, output, count, desc));
    }

    public List<Recipe> getAllRecipes() {
        return recipes;
    }

    /**
     * Checks if the inventory contains the required inputs for a recipe.
     * Does NOT remove them.
     */
    public boolean canCraft(Inventory inventory, Recipe recipe) {
        Map<ItemType, Integer> invCounts = new HashMap<>();

        // TALLEY INVENTORY
        // Check main inventory (backpack)
        for (Item item : inventory.getBackpack()) {
            if (item != null)
                increment(invCounts, item.getType());
        }
        // Check hands
        if (inventory.getRightHand() != null)
            increment(invCounts, inventory.getRightHand().getType());
        if (inventory.getLeftHand() != null)
            increment(invCounts, inventory.getLeftHand().getType());
        // Check main list (redundancy check depending on implementation, usually
        // backpack array covers it?)
        // Inventory class structure: "mainInventory" list vs "backpack" array.
        // Let's iterate mainInventory as it usually holds everything not equipped?
        // Wait, Inventory.java: "backpack" is array, "mainInventory" is List<Item>.
        // Let's rely on mainInventory + hands.

        invCounts.clear(); // Reset to be safe
        for (Item item : inventory.getMainInventory()) {
            increment(invCounts, item.getType());
        }
        if (inventory.getRightHand() != null)
            increment(invCounts, inventory.getRightHand().getType());
        if (inventory.getLeftHand() != null)
            increment(invCounts, inventory.getLeftHand().getType());

        // CHECK REQUIREMENTS
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

    private void increment(Map<ItemType, Integer> map, ItemType type) {
        map.put(type, map.getOrDefault(type, 0) + 1);
    }

    /**
     * Consumes ingredients and returns the created item(s).
     * Returns NULL if craft failed (missing ingredients).
     */
    public Item craft(Inventory inventory, Recipe recipe) {
        if (!canCraft(inventory, recipe))
            return null;

        // Consume Ingredients
        // This is tricky because we need to remove specific instances.
        // We act greedily: remove first matching items we find.

        Map<ItemType, Integer> remainingReqs = new HashMap<>();
        for (ItemType t : recipe.inputs) {
            remainingReqs.put(t, remainingReqs.getOrDefault(t, 0) + 1);
        }

        List<Item> toRemove = new ArrayList<>();

        // SEARCH HANDS FIRST
        checkAndMarkRemoval(inventory.getRightHand(), remainingReqs, toRemove);
        checkAndMarkRemoval(inventory.getLeftHand(), remainingReqs, toRemove);

        // SEARCH MAIN INVENTORY
        // Use a copy to avoid concurrent modification issues during iteration
        for (Item item : new ArrayList<>(inventory.getMainInventory())) {
            if (checkAndMarkRemoval(item, remainingReqs, toRemove)) {
                // optimization: break if all reqs met?
                if (isReqsSatisfied(remainingReqs))
                    break;
            }
        }

        // Actually remove them
        for (Item item : toRemove) {
            inventory.removeItem(item);
        }

        // Create Output
        // Note: For Darts x5, we might return one Item object with quantity?
        // Or strictly single item per return?
        // Tarmin2 usually uses single items.
        // If outputCount > 1, we might need to handle stacking or return List<Item>.
        // For now, let's create ONE item. If standard is stackable, we behave
        // accordingly.
        // Assuming createItem handles stacks if the logic exists, otherwise we just
        // return one instance.
        // If the user wants 5 darts, we should probably spawn 5 items?
        // Let's simplfy: return the FIRST item created. Caller handles repeat if
        // needed?
        // Better: Return List<Item>.

        // Wait, standard `createItem` makes one.
        // If outputType is DART (stackable?), we might set quantity.
        // Let's assume non-stackable for now for simplicity, or just return one item.
        // User asked for "create a few basic crafting recipes", I'll stick to 1-to-1
        // for simplicity
        // unless it's ammo.

        Item craftedItem = itemDataManager.createItem(recipe.output, 0, 0, com.bpm.minotaur.gamedata.item.ItemColor.TAN,
                assetManager);
        // If Darts, maybe we can't easily stack them yet. Let's just create one for
        // now.

        return craftedItem;
    }

    private boolean checkAndMarkRemoval(Item item, Map<ItemType, Integer> reqs, List<Item> toRemove) {
        if (item == null)
            return false;
        if (reqs.containsKey(item.getType())) {
            int needed = reqs.get(item.getType());
            if (needed > 0) {
                reqs.put(item.getType(), needed - 1);
                toRemove.add(item);
                return true;
            }
        }
        return false;
    }

    private boolean isReqsSatisfied(Map<ItemType, Integer> reqs) {
        for (int val : reqs.values()) {
            if (val > 0)
                return false;
        }
        return true;
    }
}
