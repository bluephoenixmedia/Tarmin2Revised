package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class CookingManager {

    // Mapping of MonsterType to the status effect its gib provides
    private final Map<MonsterType, StatusEffectType> gibEffects = new HashMap<>();

    // Keep track of which monster's gib effect has been discovered
    private final Map<MonsterType, Boolean> discoveredGibs = new HashMap<>();

    // Discovered / Mastered Recipes for the Cookbook
    private final Set<String> discoveredRecipes = new HashSet<>();

    // --- Recipe System ---
    public static class CookingRecipe {
        public final String name;
        public final List<ItemType> ingredients;
        public final List<StatusEffectType> guaranteedEffects;
        public final String description;

        public CookingRecipe(String name, String description, List<StatusEffectType> effects, ItemType... ingredients) {
            this.name = name;
            this.description = description;
            this.guaranteedEffects = new ArrayList<>(effects);
            this.ingredients = new ArrayList<>(Arrays.asList(ingredients));
            Collections.sort(this.ingredients, (a, b) -> a.name().compareTo(b.name()));
        }
    }

    private final List<CookingRecipe> recipes = new ArrayList<>();

    // Base effect duration in turns. Skill adds 20 turns per level.
    private static final int BASE_EFFECT_DURATION = 120;
    private static final int SKILL_DURATION_BONUS = 20;

    // Synergy bonus: when 2+ ingredients share the same random gib effect, multiply duration.
    private static final float SYNERGY_DURATION_MULTIPLIER = 2.0f;

    public CookingManager() {
        initializeRandomEffects();
        initializeRecipes();
        // Starter recipes unlocked by default
        discoveredRecipes.add("Hearty Stew");
        discoveredRecipes.add("Warrior's Stew");
    }

    private void initializeRecipes() {
        recipes.clear();

        // Two gibs of the same flesh type — brute-force muscle stew
        recipes.add(new CookingRecipe(
            "Warrior's Stew",
            "A thick stew of doubled flesh. Grants temporary strength.",
            Arrays.asList(StatusEffectType.TEMP_STRENGTH),
            ItemType.GIB_FLESH, ItemType.GIB_FLESH
        ));

        // Organ + Organ — vital regeneration brew
        recipes.add(new CookingRecipe(
            "Vital Brew",
            "A potent organ reduction. Temporarily boosts max health and heals.",
            Arrays.asList(StatusEffectType.TEMP_HEALTH, StatusEffectType.HEALTHY),
            ItemType.GIB_ORGAN, ItemType.GIB_ORGAN
        ));

        // Bile + Glaze — toxin-based immunity cocktail
        recipes.add(new CookingRecipe(
            "Toxic Communion",
            "A foul mix of bile and glaze that hardens the body against poisons and acid.",
            Arrays.asList(StatusEffectType.IMMUNE_BOOSTED, StatusEffectType.RESIST_POISON),
            ItemType.GIB_BILE, ItemType.GIB_GLAZE
        ));

        // Bone + Monster Eye — sight soup
        recipes.add(new CookingRecipe(
            "Sight Soup",
            "Bone-marrow broth with a monster eye. Reveals creatures beyond stone walls.",
            Arrays.asList(StatusEffectType.TELEPATHY),
            ItemType.GIB_BONE, ItemType.MONSTER_EYE
        ));

        // Triple meat — the simplest nourishing hearty campfire stew
        recipes.add(new CookingRecipe(
            "Hearty Stew",
            "Simple but deeply satisfying. Provides steady bodily recovery and warmth.",
            Arrays.asList(StatusEffectType.HEALTHY, StatusEffectType.RECOVERING),
            ItemType.MEAT, ItemType.MEAT, ItemType.MEAT
        ));

        // Flesh + Organ + Monster Eye — warrior's vision, hybrid power/awareness
        recipes.add(new CookingRecipe(
            "Warrior's Vision",
            "A rare three-ingredient brew that sharpens both physical prowess and second sight.",
            Arrays.asList(StatusEffectType.PSYCHIC, StatusEffectType.TEMP_STRENGTH, StatusEffectType.TELEPATHY),
            ItemType.GIB_FLESH, ItemType.GIB_ORGAN, ItemType.MONSTER_EYE
        ));

        // Bile + Bile — corrosive brew that warps the mind
        recipes.add(new CookingRecipe(
            "Bile Broth",
            "Pure concentrated bile. Sharpens intense focus and awakens primal bloodlust.",
            Arrays.asList(StatusEffectType.FOCUSED, StatusEffectType.BLOOD_SURGE),
            ItemType.GIB_BILE, ItemType.GIB_BILE
        ));

        // Glaze + Glaze — protective shell
        recipes.add(new CookingRecipe(
            "Shell Glaze Reduction",
            "A thick reduction of monster carapace. Hardens skin against heavy blows.",
            Arrays.asList(StatusEffectType.HARDENED, StatusEffectType.CARAPACE_HARDENING),
            ItemType.GIB_GLAZE, ItemType.GIB_GLAZE
        ));

        // Bone + Bone — dense mineral soup
        recipes.add(new CookingRecipe(
            "Marrow Broth",
            "Dense with subterranean minerals. A slow burn of fortitude and strength.",
            Arrays.asList(StatusEffectType.TEMP_STRENGTH, StatusEffectType.RECOVERING),
            ItemType.GIB_BONE, ItemType.GIB_BONE
        ));

        // Meat + Meat — field ration hot pot
        recipes.add(new CookingRecipe(
            "Campfire Meat Skillet",
            "Crisp seared cutlets seasoned over open flame.",
            Arrays.asList(StatusEffectType.HEALTHY),
            ItemType.MEAT, ItemType.MEAT
        ));

        // Flesh + Bone + Glaze — apex predator feast
        recipes.add(new CookingRecipe(
            "Apex Predator Feast",
            "A barbaric trifecta of beast essence granting relentless combat instincts.",
            Arrays.asList(StatusEffectType.TEMP_STRENGTH, StatusEffectType.BLOOD_SURGE, StatusEffectType.CARAPACE_HARDENING),
            ItemType.GIB_FLESH, ItemType.GIB_BONE, ItemType.GIB_GLAZE
        ));

        if (Gdx.app != null) {
            Gdx.app.log("CookingManager", "Initialized " + recipes.size() + " cooking recipes.");
        }
    }

    /**
     * Shuffles and assigns random and Qud-style metabolic status effects to each possible monster gib.
     */
    public void initializeRandomEffects() {
        gibEffects.clear();
        discoveredGibs.clear();

        List<StatusEffectType> validEffects = Arrays.asList(
            StatusEffectType.FOCUSED, StatusEffectType.ADRENALINE_BOOST,
            StatusEffectType.HEALTHY, StatusEffectType.IMMUNE_BOOSTED,
            StatusEffectType.TEMP_STRENGTH, StatusEffectType.TEMP_SPEED,
            StatusEffectType.TEMP_HEALTH, StatusEffectType.PSYCHIC,
            StatusEffectType.FLOATING, StatusEffectType.HARDENED,
            StatusEffectType.SUPER_INTELLIGENT, StatusEffectType.OMNISCIENT,
            StatusEffectType.RESIST_FIRE, StatusEffectType.RESIST_COLD,
            StatusEffectType.RESIST_LIGHTNING, StatusEffectType.RESIST_POISON,
            StatusEffectType.TELEPATHY, StatusEffectType.RECOVERING,
            // Caves of Qud Metabolic Triggers
            StatusEffectType.BLOOD_SURGE,
            StatusEffectType.SPIRITUAL_WARD,
            StatusEffectType.CARAPACE_HARDENING,
            StatusEffectType.NIGHT_HUNTER
        );

        Random rand = new Random();
        for (MonsterType monsterType : MonsterType.values()) {
            StatusEffectType assignedEffect = validEffects.get(rand.nextInt(validEffects.size()));
            gibEffects.put(monsterType, assignedEffect);
            discoveredGibs.put(monsterType, false);
        }

        if (Gdx.app != null) {
            Gdx.app.log("CookingManager", "Randomized gib effects for " + MonsterType.values().length + " monsters.");
        }
    }

    public StatusEffectType getEffectForMonster(MonsterType monsterType) {
        return gibEffects.get(monsterType);
    }

    public boolean isGibIdentified(MonsterType monsterType) {
        return discoveredGibs.getOrDefault(monsterType, false);
    }

    public void identifyGib(MonsterType monsterType) {
        discoveredGibs.put(monsterType, true);
    }

    public void identifyAllGibs() {
        for (Map.Entry<MonsterType, Boolean> entry : discoveredGibs.entrySet()) {
            discoveredGibs.put(entry.getKey(), true);
        }
    }

    public Map<MonsterType, StatusEffectType> getAllGibEffects() {
        return Collections.unmodifiableMap(gibEffects);
    }

    public Map<MonsterType, Boolean> getDiscoveredGibs() {
        return Collections.unmodifiableMap(discoveredGibs);
    }

    public boolean isRecipeDiscovered(String recipeName) {
        return discoveredRecipes.contains(recipeName);
    }

    public void discoverRecipe(String recipeName) {
        discoveredRecipes.add(recipeName);
    }

    public Set<String> getDiscoveredRecipeNames() {
        return Collections.unmodifiableSet(discoveredRecipes);
    }

    // --- Recipe Lookup ---

    public CookingRecipe findMatchingRecipe(List<Item> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return null;

        List<ItemType> types = new ArrayList<>();
        for (Item item : ingredients) {
            types.add(item.getType());
        }
        Collections.sort(types, (a, b) -> a.name().compareTo(b.name()));

        for (CookingRecipe recipe : recipes) {
            if (recipe.ingredients.equals(types)) {
                return recipe;
            }
        }
        return null;
    }

    public String getPartialRecipeHint(List<Item> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return null;

        List<ItemType> types = new ArrayList<>();
        for (Item item : ingredients) {
            types.add(item.getType());
        }

        for (CookingRecipe recipe : recipes) {
            if (recipe.ingredients.size() > types.size() && recipe.ingredients.containsAll(types)) {
                return recipe.name;
            }
        }
        return null;
    }

    // --- Caves of Qud Dynamic Procedural Nomenclature ---

    /**
     * Procedurally constructs a rich, evocative dish name from donor monster and ingredients.
     * E.g. "Simmered Wererat Organ Reduction", "Roasted Spider Glaze Stew", "Spiced Minotaur Flesh Medley".
     */
    public String generateProceduralMealName(List<Item> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return "Bland Broth";

        // 1. Identify dominant monster donor
        MonsterType primaryMonster = null;
        for (Item ingr : ingredients) {
            if (ingr.getCorpseSource() != null) {
                primaryMonster = ingr.getCorpseSource();
                break;
            }
        }

        String monsterPrefix = "";
        if (primaryMonster != null) {
            String mName = primaryMonster.name().replace('_', ' ').toLowerCase();
            monsterPrefix = Character.toUpperCase(mName.charAt(0)) + mName.substring(1) + " ";
        } else {
            monsterPrefix = "Wilderness ";
        }

        // 2. Determine culinary technique based on ingredients
        boolean hasBile = ingredients.stream().anyMatch(i -> i.getType() == ItemType.GIB_BILE);
        boolean hasGlaze = ingredients.stream().anyMatch(i -> i.getType() == ItemType.GIB_GLAZE);
        boolean hasOrgan = ingredients.stream().anyMatch(i -> i.getType() == ItemType.GIB_ORGAN);
        boolean hasBone = ingredients.stream().anyMatch(i -> i.getType() == ItemType.GIB_BONE);
        boolean hasMeat = ingredients.stream().anyMatch(i -> i.getType() == ItemType.MEAT || i.getType() == ItemType.GIB_FLESH);

        String technique = "Hearty";
        if (hasBile) {
            technique = "Piquant";
        } else if (hasGlaze) {
            technique = "Caramelized";
        } else if (hasOrgan) {
            technique = "Simmered";
        } else if (hasBone) {
            technique = "Slow-Roasted";
        } else if (hasMeat) {
            technique = "Seared";
        }

        // 3. Determine dish form based on ingredient count
        String dishForm;
        int count = ingredients.size();
        if (count == 1) {
            dishForm = hasBone ? "Marrow Broth" : (hasOrgan ? "Delicacy" : "Cutlet");
        } else if (count == 2) {
            dishForm = (hasBile || hasGlaze) ? "Reduction" : "Stew";
        } else {
            dishForm = (hasMeat && hasOrgan) ? "Feast" : "Pot-Au-Feu";
        }

        return technique + " " + monsterPrefix + dishForm;
    }

    /**
     * Resolves ingredients for the 1-click "Whip Up a Meal" option from unified pantry.
     * Searches for 1-2 available basic meat or food items.
     */
    public List<Item> findQuickCookIngredients(List<Item> pantryItems) {
        List<Item> chosen = new ArrayList<>();
        for (Item item : pantryItems) {
            if (item.getType() == ItemType.MEAT) {
                chosen.add(item);
                if (chosen.size() >= 2) break;
            }
        }
        if (chosen.isEmpty()) {
            for (Item item : pantryItems) {
                if (item.isFood() || item.getType().name().startsWith("GIB_")) {
                    chosen.add(item);
                    if (chosen.size() >= 1) break;
                }
            }
        }
        return chosen;
    }

    // --- Duration & Synergy ---

    public int calculateEffectDuration(int cookingSkill, boolean hasSynergy) {
        int duration = BASE_EFFECT_DURATION + (cookingSkill * SKILL_DURATION_BONUS);
        if (hasSynergy) {
            duration = (int)(duration * SYNERGY_DURATION_MULTIPLIER);
        }
        return duration;
    }

    public boolean detectSynergy(List<Item> ingredients) {
        if (ingredients == null || ingredients.size() < 2) return false;

        Map<StatusEffectType, Integer> effectCounts = new HashMap<>();
        for (Item item : ingredients) {
            MonsterType src = item.getCorpseSource();
            if (src != null) {
                StatusEffectType eff = gibEffects.get(src);
                if (eff != null) {
                    effectCounts.merge(eff, 1, Integer::sum);
                }
            }
        }
        for (int count : effectCounts.values()) {
            if (count >= 2) return true;
        }
        return false;
    }

    public List<CookingRecipe> getAllRecipes() {
        return Collections.unmodifiableList(recipes);
    }
}

