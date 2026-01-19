package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlchemyManager {

    private static AlchemyManager instance;
    private List<Recipe> recipes = new ArrayList<>();

    public static AlchemyManager getInstance() {
        if (instance == null) {
            instance = new AlchemyManager();
        }
        return instance;
    }

    private AlchemyManager() {
        initRecipes();
    }

    private void initRecipes() {
        // Recipe 1: Minor Feral Draught
        // 3x Flesh + 1x Bone = Minor Feral Draught
        Map<ItemType, Integer> feralInputs = new HashMap<>();
        feralInputs.put(ItemType.GIB_FLESH, 3);
        feralInputs.put(ItemType.GIB_BONE, 1);
        recipes.add(new Recipe("Minor Feral Draught", ItemType.POTION_FERAL_DRAUGHT, feralInputs));

        // Recipe 2: Titan's Sludge
        // 1x Gib Bile + 2x Gib Organ = Titan's Sludge
        Map<ItemType, Integer> titanInputs = new HashMap<>();
        titanInputs.put(ItemType.GIB_BILE, 1);
        titanInputs.put(ItemType.GIB_ORGAN, 2);
        recipes.add(new Recipe("Titan's Sludge", ItemType.POTION_TITAN_SLUDGE, titanInputs));
    }

    public List<Recipe> getRecipes() {
        return recipes;
    }

    public boolean canCraft(Player player, Recipe recipe) {
        if (player == null || recipe == null)
            return false;
        Inventory inv = player.getInventory();

        // Count items in inventory
        Map<ItemType, Integer> counts = new HashMap<>();
        for (Item item : inv.getAllItems()) {
            if (item != null) {
                counts.put(item.getType(), counts.getOrDefault(item.getType(), 0) + 1);
            }
        }

        // Check requirements
        for (Map.Entry<ItemType, Integer> entry : recipe.inputs.entrySet()) {
            ItemType requiredType = entry.getKey();
            int requiredAmount = entry.getValue();
            if (counts.getOrDefault(requiredType, 0) < requiredAmount) {
                return false;
            }
        }

        return true;
    }

    public Item craft(Player player, Recipe recipe, com.bpm.minotaur.gamedata.item.ItemDataManager itemDataManager,
            com.badlogic.gdx.assets.AssetManager assetManager) {
        if (!canCraft(player, recipe))
            return null;

        // Consume ingredients
        Inventory inv = player.getInventory();
        for (Map.Entry<ItemType, Integer> entry : recipe.inputs.entrySet()) {
            ItemType typeToRemove = entry.getKey();
            int amountToRemove = entry.getValue();

            int removed = 0;
            List<Item> toRemove = new ArrayList<>();
            for (Item item : inv.getAllItems()) {
                if (item != null && item.getType() == typeToRemove) {
                    toRemove.add(item);
                    removed++;
                    if (removed >= amountToRemove)
                        break;
                }
            }

            for (Item item : toRemove) {
                inv.removeItem(item);
            }
        }

        // Create Result
        Item result = itemDataManager.createItem(recipe.outputType,
                (int) player.getPosition().x, (int) player.getPosition().y,
                com.bpm.minotaur.gamedata.item.ItemColor.WHITE, assetManager);

        // Try to pickup
        if (result != null) {
            if (!inv.pickup(result)) {
                // Return result (caller handles dropping)
            }
        }

        return result;
    }

    public static class Recipe {
        public String name;
        public ItemType outputType;
        public Map<ItemType, Integer> inputs;

        public Recipe(String name, ItemType outputType, Map<ItemType, Integer> inputs) {
            this.name = name;
            this.outputType = outputType;
            this.inputs = inputs;
        }
    }
}
