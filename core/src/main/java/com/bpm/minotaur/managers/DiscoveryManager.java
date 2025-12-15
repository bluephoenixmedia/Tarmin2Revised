package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.PotionEffectType;
import com.bpm.minotaur.gamedata.item.ScrollEffectType;
import com.bpm.minotaur.gamedata.item.WandEffectType;
import com.bpm.minotaur.gamedata.item.RingEffectType;
import com.bpm.minotaur.gamedata.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages the global discovery/identification state of items.
 * Handles the randomization of appearances (Potions, Scrolls) to effects
 * and tracks which effects the player has identified.
 * Replaces the specific PotionManager.
 */
public class DiscoveryManager {

    private static final String SAVE_FILE = "saves/world/discovery_state.json";

    // --- POTIONS ---
    private ObjectMap<ItemType, PotionEffectType> potionMap = new ObjectMap<>();
    private ObjectMap<PotionEffectType, Boolean> potionIdentified = new ObjectMap<>();

    // --- SCROLLS ---
    private ObjectMap<ItemType, ScrollEffectType> scrollMap = new ObjectMap<>();
    private ObjectMap<ScrollEffectType, Boolean> scrollIdentified = new ObjectMap<>();

    // --- WANDS ---
    private ObjectMap<ItemType, WandEffectType> wandMap = new ObjectMap<>();
    private ObjectMap<WandEffectType, Boolean> wandIdentified = new ObjectMap<>();

    // --- RINGS ---
    private ObjectMap<ItemType, RingEffectType> ringMap = new ObjectMap<>();
    private ObjectMap<RingEffectType, Boolean> ringIdentified = new ObjectMap<>();

    private final Json json;
    private final GameEventManager eventManager;

    public DiscoveryManager(GameEventManager eventManager) {
        this.eventManager = eventManager;
        this.json = new Json();
        this.json.setUsePrototypes(false);
    }

    /**
     * Initializes the randomized mappings for a new game.
     */
    public void initializeNewGame(List<ItemType> potionAppearanceTypes) {
        Gdx.app.log("DiscoveryManager", "Initializing new game discovery state...");

        // 1. Initialize Potions
        initializePotions(potionAppearanceTypes);

        // 2. Initialize Scrolls
        List<ItemType> scrollTypes = new ArrayList<>();
        scrollTypes.add(ItemType.SCROLL_A);
        scrollTypes.add(ItemType.SCROLL_B);
        scrollTypes.add(ItemType.SCROLL_C);
        scrollTypes.add(ItemType.SCROLL_D);
        scrollTypes.add(ItemType.SCROLL_E);
        scrollTypes.add(ItemType.SCROLL_F);
        scrollTypes.add(ItemType.SCROLL_G);
        scrollTypes.add(ItemType.SCROLL_H);
        initializeScrolls(scrollTypes);

        // 3. Initialize Wands
        List<ItemType> wandTypes = new ArrayList<>();
        wandTypes.add(ItemType.WAND_A);
        wandTypes.add(ItemType.WAND_B);
        wandTypes.add(ItemType.WAND_C);
        wandTypes.add(ItemType.WAND_D);
        wandTypes.add(ItemType.WAND_E);
        wandTypes.add(ItemType.WAND_F);
        wandTypes.add(ItemType.WAND_G);
        wandTypes.add(ItemType.WAND_H);
        wandTypes.add(ItemType.WAND_H);
        initializeWands(wandTypes);

        // 4. Initialize Rings
        List<ItemType> ringTypes = new ArrayList<>();
        ringTypes.add(ItemType.SMALL_RING);
        ringTypes.add(ItemType.LARGE_RING);
        ringTypes.add(ItemType.RING_BLUE);
        ringTypes.add(ItemType.RING_PINK);
        ringTypes.add(ItemType.RING_PURPLE);
        initializeRings(ringTypes);
    }

    private void initializePotions(List<ItemType> appearanceTypes) {
        potionMap.clear();
        potionIdentified.clear();

        List<PotionEffectType> effects = new ArrayList<>();
        Collections.addAll(effects, PotionEffectType.values());
        Collections.shuffle(effects);

        List<ItemType> types = new ArrayList<>(appearanceTypes);
        Collections.shuffle(types);

        int count = Math.min(types.size(), effects.size());
        for (int i = 0; i < count; i++) {
            potionMap.put(types.get(i), effects.get(i));
            Gdx.app.log("DiscoveryManager", "Mapped Potion: " + types.get(i).name() + " -> " + effects.get(i).name());
        }

        // Initialize identification status (default false)
        for (PotionEffectType effect : PotionEffectType.values()) {
            potionIdentified.put(effect, false);
        }
    }

    private void initializeScrolls(List<ItemType> appearanceTypes) {
        scrollMap.clear();
        scrollIdentified.clear();
        List<ScrollEffectType> effects = new ArrayList<>();
        Collections.addAll(effects, ScrollEffectType.values());
        Collections.shuffle(effects);
        List<ItemType> types = new ArrayList<>(appearanceTypes);
        Collections.shuffle(types);
        int count = Math.min(types.size(), effects.size());
        for (int i = 0; i < count; i++) {
            scrollMap.put(types.get(i), effects.get(i));
        }
        for (ScrollEffectType effect : ScrollEffectType.values()) {
            scrollIdentified.put(effect, false);
        }
    }

    private void initializeWands(List<ItemType> appearanceTypes) {
        wandMap.clear();
        wandIdentified.clear();
        List<WandEffectType> effects = new ArrayList<>();
        Collections.addAll(effects, WandEffectType.values());
        Collections.shuffle(effects);
        List<ItemType> types = new ArrayList<>(appearanceTypes);
        Collections.shuffle(types);
        int count = Math.min(types.size(), effects.size());
        for (int i = 0; i < count; i++) {
            wandMap.put(types.get(i), effects.get(i));
        }
        for (WandEffectType effect : WandEffectType.values()) {
            wandIdentified.put(effect, false);
        }
    }

    private void initializeRings(List<ItemType> appearanceTypes) {
        ringMap.clear();
        ringIdentified.clear();
        List<RingEffectType> effects = new ArrayList<>();
        Collections.addAll(effects, RingEffectType.values());
        Collections.shuffle(effects);
        List<ItemType> types = new ArrayList<>(appearanceTypes);
        Collections.shuffle(types);
        int count = Math.min(types.size(), effects.size());
        for (int i = 0; i < count; i++) {
            ringMap.put(types.get(i), effects.get(i));
        }
        for (RingEffectType effect : RingEffectType.values()) {
            ringIdentified.put(effect, false);
        }
    }

    // --- Potion Logic ---

    public PotionEffectType getPotionEffect(ItemType appearanceType) {
        return potionMap.get(appearanceType);
    }

    public boolean isPotionIdentified(PotionEffectType effect) {
        if (effect == null)
            return false;
        return potionIdentified.get(effect, false);
    }

    public void identifyPotion(Player player, PotionEffectType effect) {
        if (isPotionIdentified(effect))
            return;

        potionIdentified.put(effect, true);
        Gdx.app.log("DiscoveryManager", "Identified Potion Effect: " + effect.name());

        if (eventManager != null) {
            eventManager.addEvent(
                    new com.bpm.minotaur.gamedata.GameEvent("Identified: Potion of " + effect.getBaseName(), 2.5f));
        }

        // Update inventory
        updateInventory(player);
    }

    // --- Scroll Logic ---
    public ScrollEffectType getScrollEffect(ItemType appearanceType) {
        return scrollMap.get(appearanceType);
    }

    public boolean isScrollIdentified(ScrollEffectType effect) {
        return effect != null && scrollIdentified.get(effect, false);
    }

    public void identifyScroll(Player player, ScrollEffectType effect) {
        if (!isScrollIdentified(effect)) {
            scrollIdentified.put(effect, true);
            if (eventManager != null)
                eventManager.addEvent(
                        new com.bpm.minotaur.gamedata.GameEvent("Identified Scroll of " + effect.getBaseName(), 2.5f));
            updateInventory(player);
        }
    }

    // --- Wand Logic ---
    public WandEffectType getWandEffect(ItemType appearanceType) {
        return wandMap.get(appearanceType);
    }

    public boolean isWandIdentified(WandEffectType effect) {
        return effect != null && wandIdentified.get(effect, false);
    }

    public void identifyWand(Player player, WandEffectType effect) {
        if (!isWandIdentified(effect)) {
            wandIdentified.put(effect, true);
            if (eventManager != null)
                eventManager.addEvent(
                        new com.bpm.minotaur.gamedata.GameEvent("Identified Wand of " + effect.getBaseName(), 2.5f));
            updateInventory(player);
        }
    }

    // --- Ring Logic ---
    public RingEffectType getRingEffect(ItemType appearanceType) {
        return ringMap.get(appearanceType);
    }

    public boolean isRingIdentified(RingEffectType effect) {
        return effect != null && ringIdentified.get(effect, false);
    }

    public void identifyRing(Player player, RingEffectType effect) {
        if (!isRingIdentified(effect)) {
            ringIdentified.put(effect, true);
            if (eventManager != null)
                eventManager.addEvent(
                        new com.bpm.minotaur.gamedata.GameEvent("Identified Ring of " + effect.getBaseName(), 2.5f));
            updateInventory(player);
        }
    }

    // --- Generic Updates ---

    public void updateInventory(Player player) {
        if (player == null || player.getInventory() == null)
            return;

        for (Item item : player.getInventory().getAllItems()) {
            if (item.isPotion()) {
                PotionEffectType effect = item.getTrueEffect();
                if (effect != null && isPotionIdentified(effect)) {
                    item.setIdentified(true);
                    item.setName("Potion of " + effect.getBaseName());
                }
            }
            // Scrolls
            if (item.getType().name().startsWith("SCROLL_")) {
                ScrollEffectType effect = item.getScrollEffect();
                if (effect != null && isScrollIdentified(effect)) {
                    item.setIdentified(true);
                    item.setName("Scroll of " + effect.getBaseName());
                }
            }
            // Wands
            if (item.getType().name().startsWith("WAND_")) {
                WandEffectType effect = item.getWandEffect();
                if (effect != null && isWandIdentified(effect)) {
                    item.setIdentified(true);
                    item.setName("Wand of " + effect.getBaseName());
                }
            }
            // Rings
            if (item.isRing() && item.getRingEffect() != null) {
                RingEffectType effect = item.getRingEffect();
                if (isRingIdentified(effect)) {
                    item.setIdentified(true);
                    item.setName("Ring of " + effect.getBaseName());
                }
            }
        }
    }

    public String getDisplayName(Item item) {
        if (item.isPotion()) {
            PotionEffectType effect = item.getTrueEffect();
            if (item.isIdentified() || isPotionIdentified(effect)) {
                return "Potion of " + (effect != null ? effect.getBaseName() : "Unknown");
            } else {
                return item.getFriendlyName(); // "Blue Potion"
            }
        }
        if (item.getType().name().startsWith("SCROLL_")) {
            ScrollEffectType effect = item.getScrollEffect();
            if (item.isIdentified() || isScrollIdentified(effect)) {
                return "Scroll of " + (effect != null ? effect.getBaseName() : "Unknown");
            } else {
                return item.getFriendlyName(); // "Labeled SCROLL_A" -> "Scroll labeled ZELGO" (TODO: friendly names in
                                               // template)
            }
        }
        if (item.getType().name().startsWith("WAND_")) {
            WandEffectType effect = item.getWandEffect();
            if (item.isIdentified() || isWandIdentified(effect)) {
                return "Wand of " + (effect != null ? effect.getBaseName() : "Unknown");
            } else {
                return item.getFriendlyName();
            }
        }
        return item.getFriendlyName();
    }

    // Fix for Ring Display Name
    public String getDisplayNameForRing(Item item) {
        RingEffectType effect = item.getRingEffect();
        if (item.isIdentified() || isRingIdentified(effect)) {
            return "Ring of " + (effect != null ? effect.getBaseName() : "Unknown");
        } else {
            return item.getFriendlyName();
        }
    }
    // --- Save/Load ---

    private static class DiscoverySaveState {
        public ObjectMap<String, String> potionMapString; // JSON doesn't like Enum keys sometimes, safer to stringify
        public ObjectMap<String, Boolean> potionIdentifiedString;
        public ObjectMap<String, String> scrollMapString;
        public ObjectMap<String, Boolean> scrollIdentifiedString;
        public ObjectMap<String, String> wandMapString;
        public ObjectMap<String, Boolean> wandIdentifiedString;
        public ObjectMap<String, String> ringMapString;
        public ObjectMap<String, Boolean> ringIdentifiedString;
    }

    public void saveState() {
        try {
            DiscoverySaveState state = new DiscoverySaveState();

            // Convert to String maps for safe serialization
            state.potionMapString = new ObjectMap<>();
            for (ObjectMap.Entry<ItemType, PotionEffectType> entry : potionMap.entries()) {
                state.potionMapString.put(entry.key.name(), entry.value.name());
            }

            state.potionIdentifiedString = new ObjectMap<>();
            for (ObjectMap.Entry<PotionEffectType, Boolean> entry : potionIdentified.entries()) {
                state.potionIdentifiedString.put(entry.key.name(), entry.value);
            }

            // Scrolls
            state.scrollMapString = new ObjectMap<>();
            for (ObjectMap.Entry<ItemType, ScrollEffectType> entry : scrollMap.entries()) {
                state.scrollMapString.put(entry.key.name(), entry.value.name());
            }
            state.scrollIdentifiedString = new ObjectMap<>();
            for (ObjectMap.Entry<ScrollEffectType, Boolean> entry : scrollIdentified.entries()) {
                state.scrollIdentifiedString.put(entry.key.name(), entry.value);
            }

            // Wands
            state.wandMapString = new ObjectMap<>();
            for (ObjectMap.Entry<ItemType, WandEffectType> entry : wandMap.entries()) {
                state.wandMapString.put(entry.key.name(), entry.value.name());
            }
            state.wandIdentifiedString = new ObjectMap<>();
            for (ObjectMap.Entry<WandEffectType, Boolean> entry : wandIdentified.entries()) {
                state.wandIdentifiedString.put(entry.key.name(), entry.value);
            }

            FileHandle file = Gdx.files.local(SAVE_FILE);
            file.writeString(json.prettyPrint(state), false);
            Gdx.app.log("DiscoveryManager", "Saved discovery state.");
        } catch (Exception e) {
            Gdx.app.error("DiscoveryManager", "Failed to save state", e);
        }
    }

    public void loadState() {
        try {
            FileHandle file = Gdx.files.local(SAVE_FILE);
            if (file.exists()) {
                DiscoverySaveState state = json.fromJson(DiscoverySaveState.class, file);

                potionMap.clear();
                if (state.potionMapString != null) {
                    for (ObjectMap.Entry<String, String> entry : state.potionMapString.entries()) {
                        try {
                            potionMap.put(ItemType.valueOf(entry.key), PotionEffectType.valueOf(entry.value));
                        } catch (Exception e) {
                            Gdx.app.error("DiscoveryManager", "Bad potion load: " + entry.key);
                        }
                    }
                }

                potionIdentified.clear();
                if (state.potionIdentifiedString != null) {
                    for (ObjectMap.Entry<String, Boolean> entry : state.potionIdentifiedString.entries()) {
                        try {
                            potionIdentified.put(PotionEffectType.valueOf(entry.key), entry.value);
                        } catch (Exception e) {
                            Gdx.app.error("DiscoveryManager", "Bad potion ID load: " + entry.key);
                        }
                    }
                }

                scrollMap.clear();
                if (state.scrollMapString != null) {
                    for (ObjectMap.Entry<String, String> entry : state.scrollMapString.entries()) {
                        try {
                            scrollMap.put(ItemType.valueOf(entry.key), ScrollEffectType.valueOf(entry.value));
                        } catch (Exception e) {
                        }
                    }
                }
                scrollIdentified.clear();
                if (state.scrollIdentifiedString != null) {
                    for (ObjectMap.Entry<String, Boolean> entry : state.scrollIdentifiedString.entries()) {
                        try {
                            scrollIdentified.put(ScrollEffectType.valueOf(entry.key), entry.value);
                        } catch (Exception e) {
                        }
                    }
                }

                wandMap.clear();
                if (state.wandMapString != null) {
                    for (ObjectMap.Entry<String, String> entry : state.wandMapString.entries()) {
                        try {
                            wandMap.put(ItemType.valueOf(entry.key), WandEffectType.valueOf(entry.value));
                        } catch (Exception e) {
                        }
                    }
                }
                wandIdentified.clear();
                if (state.wandIdentifiedString != null) {
                    for (ObjectMap.Entry<String, Boolean> entry : state.wandIdentifiedString.entries()) {
                        try {
                            wandIdentified.put(WandEffectType.valueOf(entry.key), entry.value);
                        } catch (Exception e) {
                        }
                    }
                }

                // Rings
                ringMap.clear();
                if (state.ringMapString != null) {
                    for (ObjectMap.Entry<String, String> entry : state.ringMapString.entries()) {
                        try {
                            ringMap.put(ItemType.valueOf(entry.key), RingEffectType.valueOf(entry.value));
                        } catch (Exception e) {
                        }
                    }
                }
                ringIdentified.clear();
                if (state.ringIdentifiedString != null) {
                    for (ObjectMap.Entry<String, Boolean> entry : state.ringIdentifiedString.entries()) {
                        try {
                            ringIdentified.put(RingEffectType.valueOf(entry.key), entry.value);
                        } catch (Exception e) {
                        }
                    }
                }

                Gdx.app.log("DiscoveryManager", "Loaded discovery state.");
            }
        } catch (Exception e) {
            Gdx.app.error("DiscoveryManager", "Failed to load state", e);
        }
    }

    public boolean hasSaveState() {
        return Gdx.files.local(SAVE_FILE).exists();
    }
}
