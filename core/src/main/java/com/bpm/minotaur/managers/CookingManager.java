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
import java.util.List;
import java.util.Map;
import java.util.Random;

public class CookingManager {

    // Mapping of MonsterType to the status effect its gib provides
    private Map<MonsterType, StatusEffectType> gibEffects = new HashMap<>();

    // Keep track of which monster's gib effect has been discovered
    private Map<MonsterType, Boolean> discoveredGibs = new HashMap<>();

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

    // Base effect duration in turns. Skill adds 15 turns per level.
    private static final int BASE_EFFECT_DURATION = 100;
    private static final int SKILL_DURATION_BONUS = 15;

    // Synergy bonus: when 2+ ingredients share the same random gib effect, multiply duration.
    private static final float SYNERGY_DURATION_MULTIPLIER = 2.0f;

    public CookingManager() {
        initializeRandomEffects();
        initializeRecipes();
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
            "A potent organ reduction. Temporarily boosts max health.",
            Arrays.asList(StatusEffectType.TEMP_HEALTH),
            ItemType.GIB_ORGAN, ItemType.GIB_ORGAN
        ));

        // Bile + Glaze — toxin-based immunity cocktail
        recipes.add(new CookingRecipe(
            "Toxic Communion",
            "A foul mix of bile and glaze that hardens the body against poisons.",
            Arrays.asList(StatusEffectType.IMMUNE_BOOSTED, StatusEffectType.RESIST_POISON),
            ItemType.GIB_BILE, ItemType.GIB_GLAZE
        ));

        // Bone + Monster Eye — sight soup
        recipes.add(new CookingRecipe(
            "Sight Soup",
            "Bone-marrow broth with a monster eye. Reveals creatures beyond walls.",
            Arrays.asList(StatusEffectType.TELEPATHY),
            ItemType.GIB_BONE, ItemType.MONSTER_EYE
        ));

        // Triple meat — the simplest nourishing hearty stew
        recipes.add(new CookingRecipe(
            "Hearty Stew",
            "Simple but filling. Provides steady recovery.",
            Arrays.asList(StatusEffectType.HEALTHY),
            ItemType.MEAT, ItemType.MEAT, ItemType.MEAT
        ));

        // Flesh + Organ + Monster Eye — warrior's vision, hybrid power/awareness
        recipes.add(new CookingRecipe(
            "Warrior's Vision",
            "A rare three-ingredient brew that sharpens both body and mind.",
            Arrays.asList(StatusEffectType.PSYCHIC, StatusEffectType.TEMP_STRENGTH),
            ItemType.GIB_FLESH, ItemType.GIB_ORGAN, ItemType.MONSTER_EYE
        ));

        // Bile + Bile — corrosive brew that warps the mind
        recipes.add(new CookingRecipe(
            "Bile Broth",
            "Pure concentrated bile. Dangerous — causes hallucinations but sharpens focus.",
            Arrays.asList(StatusEffectType.FOCUSED, StatusEffectType.HALLUCINATING),
            ItemType.GIB_BILE, ItemType.GIB_BILE
        ));

        // Glaze + Glaze — protective shell
        recipes.add(new CookingRecipe(
            "Shell Glaze Reduction",
            "A thick reduction of monster carapace. Hardens the skin.",
            Arrays.asList(StatusEffectType.HARDENED),
            ItemType.GIB_GLAZE, ItemType.GIB_GLAZE
        ));

        // Bone + Bone — dense mineral soup
        recipes.add(new CookingRecipe(
            "Marrow Broth",
            "Dense with minerals. A slow burn of strength.",
            Arrays.asList(StatusEffectType.TEMP_STRENGTH, StatusEffectType.RECOVERING),
            ItemType.GIB_BONE, ItemType.GIB_BONE
        ));

        Gdx.app.log("CookingManager", "Initialized " + recipes.size() + " cooking recipes.");
    }

    /**
     * Shuffles and assigns a random status effect to each possible monster gib.
     * This is called when a new game starts.
     */
    public void initializeRandomEffects() {
        gibEffects.clear();
        discoveredGibs.clear();

        // Only use buff/intrinsic effects for random gib assignments so cooking is rewarding
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
            // A few mild debuffs to keep it interesting
            StatusEffectType.CONFUSED, StatusEffectType.HALLUCINATING,
            StatusEffectType.BERZERK
        );

        Random rand = new Random();
        for (MonsterType monsterType : MonsterType.values()) {
            StatusEffectType assignedEffect = validEffects.get(rand.nextInt(validEffects.size()));
            gibEffects.put(monsterType, assignedEffect);
            discoveredGibs.put(monsterType, false);
        }

        Gdx.app.log("CookingManager", "Randomized gib effects for " + MonsterType.values().length + " monsters.");
    }

    /**
     * Get the effect assigned to a specific monster's gib.
     */
    public StatusEffectType getEffectForMonster(MonsterType monsterType) {
        return gibEffects.get(monsterType);
    }

    /**
     * Returns true if the player has identified the effect of this monster's gib.
     */
    public boolean isGibIdentified(MonsterType monsterType) {
        return discoveredGibs.getOrDefault(monsterType, false);
    }

    /**
     * Identify a monster's gib effect.
     */
    public void identifyGib(MonsterType monsterType) {
        discoveredGibs.put(monsterType, true);
    }

    /**
     * Iterates over all known gibs and identifies them.
     */
    public void identifyAllGibs() {
        for (Map.Entry<MonsterType, Boolean> entry : discoveredGibs.entrySet()) {
            discoveredGibs.put(entry.getKey(), true);
        }
    }

    /**
     * Returns all monster types with their discovered state (for the Gib Codex).
     */
    public Map<MonsterType, StatusEffectType> getAllGibEffects() {
        return Collections.unmodifiableMap(gibEffects);
    }

    public Map<MonsterType, Boolean> getDiscoveredGibs() {
        return Collections.unmodifiableMap(discoveredGibs);
    }

    // --- Recipe Lookup ---

    /**
     * Returns the matching recipe for the given ingredient list, or null if none match.
     * Matching is order-independent.
     */
    public CookingRecipe findMatchingRecipe(List<Item> ingredients) {
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

    /**
     * Returns a partial match hint — the name of any recipe where the current
     * ingredients are a subset of the recipe's ingredients.
     */
    public String getPartialRecipeHint(List<Item> ingredients) {
        if (ingredients.isEmpty()) return null;

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

    // --- Duration Calculation ---

    /**
     * Calculates effect duration in turns, factoring in cooking skill and synergy.
     */
    public int calculateEffectDuration(int cookingSkill, boolean hasSynergy) {
        int duration = BASE_EFFECT_DURATION + (cookingSkill * SKILL_DURATION_BONUS);
        if (hasSynergy) {
            duration = (int)(duration * SYNERGY_DURATION_MULTIPLIER);
        }
        return duration;
    }

    /**
     * Returns true when two or more ingredients from the list share the same
     * random gib effect (triggering a synergy bonus).
     */
    public boolean detectSynergy(List<Item> ingredients) {
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
