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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the global discovery/identification state of items.
 * Handles the randomization of appearances (Potions, Scrolls) to effects
 * and tracks which effects the player has identified.
 * Replaces the specific PotionManager.
 */
public class DiscoveryManager {

    private String getSaveFilePath() {
        return SaveManager.getInstance().getActiveSlotFilePath("discovery_state.json");
    }

    // --- POTIONS ---
    private ObjectMap<ItemType, PotionEffectType> potionMap = new ObjectMap<>();
    private ObjectMap<PotionEffectType, Boolean> potionIdentified = new ObjectMap<>();

    // --- SCROLLS ---
    private ObjectMap<ItemType, ScrollEffectType> scrollMap = new ObjectMap<>();
    private ObjectMap<ScrollEffectType, Boolean> scrollIdentified = new ObjectMap<>();
    /** Randomized cryptic rune label shown on each unidentified scroll appearance, e.g. "XEL'NAGA". */
    private ObjectMap<ItemType, String> scrollRuneNames = new ObjectMap<>();
    /** Dedicated spell scrolls (SCROLL_FIREBALL etc.) aren't part of the randomized
     *  appearance pool -- each ItemType IS the spell 1:1, so identification is
     *  tracked per-type here rather than per-ScrollEffectType. */
    private final Set<ItemType> identifiedDedicatedScrolls = new HashSet<>();

    private static final String[] SCROLL_RUNE_POOL = {
            "XEL'NAGA", "ZIRUVOX", "KATHA-RIN", "MORR'GLYPH", "NUL-DRATH", "VELKHAAR",
            "SEPH-ORUN", "THAAL-KESH", "GRIM VESSEL", "OBLIVION'S WHISPER", "AZH-KOTH", "PYRE-SIGIL"
    };

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

        identifiedDedicatedScrolls.clear();

        // Assign each unidentified scroll appearance a randomized cryptic rune label
        // for this run, e.g. "Scroll labeled XEL'NAGA" -- stable for the session,
        // re-rolled on every new game/apocalypse reset.
        scrollRuneNames.clear();
        List<String> runes = new ArrayList<>();
        Collections.addAll(runes, SCROLL_RUNE_POOL);
        Collections.shuffle(runes);
        for (int i = 0; i < types.size() && i < runes.size(); i++) {
            scrollRuneNames.put(types.get(i), runes.get(i));
        }
    }

    /**
     * The cryptic rune label for an unidentified scroll. Appearance types
     * (SCROLL_A-H) get one of the session's randomly-shuffled runes; any other
     * scroll type (e.g. a dedicated spell scroll like SCROLL_MAGIC_MISSILE, which
     * never enters the appearance pool) still gets a stable, deterministic rune
     * derived from its own type name, so no scroll ever falls back to a bare,
     * unmysterious "Scroll" before it's identified.
     */
    public String getScrollRuneLabel(ItemType appearanceType) {
        String rune = scrollRuneNames.get(appearanceType);
        if (rune == null && appearanceType != null) {
            int idx = Math.floorMod(appearanceType.name().hashCode(), SCROLL_RUNE_POOL.length);
            rune = SCROLL_RUNE_POOL[idx];
        }
        return rune != null ? "Scroll labeled " + rune : "Scroll";
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
        if (effect != null && !isScrollIdentified(effect)) {
            scrollIdentified.put(effect, true);
            if (eventManager != null)
                eventManager.addEvent(
                        new com.bpm.minotaur.gamedata.GameEvent("Identified Scroll of " + effect.getBaseName(), 2.5f));
            updateInventory(player);
        }
    }

    public boolean isDedicatedScrollIdentified(ItemType type) {
        return type != null && identifiedDedicatedScrolls.contains(type);
    }

    /** Identifies a dedicated spell scroll (e.g. SCROLL_MAGIC_MISSILE) by its own
     *  ItemType, since it has no shared ScrollEffectType appearance mapping. */
    public void identifyDedicatedScroll(ItemType type, String spellDisplayName) {
        if (type != null && identifiedDedicatedScrolls.add(type) && eventManager != null) {
            eventManager.addEvent(new com.bpm.minotaur.gamedata.GameEvent(
                    "Identified Scroll of " + titleCase(spellDisplayName), 2.5f));
        }
    }

    /** "MAGIC_MISSILE" -> "Magic Missile". Callers pass the raw spellId, not a pre-formatted name. */
    private String titleCase(String snakeUpper) {
        if (snakeUpper == null || snakeUpper.isEmpty()) {
            return snakeUpper;
        }
        String[] words = snakeUpper.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
        }
        return sb.toString();
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
                return com.bpm.minotaur.gamedata.item.ItemName.natural(item.getFriendlyName()); // "Blue Potion"
            }
        }
        if (item.isScroll() || (item.getType() != null && item.getType().name().startsWith("SCROLL"))) {
            ScrollEffectType effect = item.getScrollEffect();
            if (effect != null) {
                if (item.isIdentified() || isScrollIdentified(effect)) {
                    return "Scroll of " + effect.getBaseName();
                }
                return getScrollRuneLabel(item.getType());
            }
            // Dedicated spell scroll (SCROLL_FIREBALL, etc.): identified per its own
            // ItemType rather than a shared ScrollEffectType appearance.
            if (item.isIdentified() || isDedicatedScrollIdentified(item.getType())) {
                String fn = item.getFriendlyName();
                if (fn != null && !fn.isEmpty()) {
                    return fn;
                }
                if (item.getSpellId() != null) {
                    return "Scroll of " + titleCase(item.getSpellId());
                }
                return "Scroll";
            }
            return getScrollRuneLabel(item.getType());
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

    /**
     * Name to show for an item lying on the ground or just picked up — routes
     * through identification for the categories that hide their true name
     * (potions, rings, scrolls, wands) and falls back to the item's own name
     * otherwise. Shared by pickup notifications and the ground-item info card
     * so both surfaces agree on what "identified" means.
     */
    public String getGroundItemDisplayName(Item item) {
        boolean needsDiscovery = item.isPotion() || item.isRing()
                || item.isScroll()
                || (item.getType() != null && (item.getType().name().startsWith("SCROLL")
                        || item.getType().name().startsWith("WAND_")));
        return needsDiscovery ? getDisplayName(item) : item.getDisplayName();
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
        public ObjectMap<String, String> scrollRuneNamesString;
        public List<String> identifiedDedicatedScrollsString;
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
            state.scrollRuneNamesString = new ObjectMap<>();
            for (ObjectMap.Entry<ItemType, String> entry : scrollRuneNames.entries()) {
                state.scrollRuneNamesString.put(entry.key.name(), entry.value);
            }
            state.identifiedDedicatedScrollsString = new ArrayList<>();
            for (ItemType type : identifiedDedicatedScrolls) {
                state.identifiedDedicatedScrollsString.add(type.name());
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

            FileHandle file = Gdx.files.local(getSaveFilePath());
            SaveManager.getInstance().atomicWriteJson(file, state);
            Gdx.app.log("DiscoveryManager", "Saved discovery state to " + getSaveFilePath());
        } catch (Exception e) {
            Gdx.app.error("DiscoveryManager", "Failed to save state", e);
        }
    }

    public void loadState() {
        try {
            FileHandle file = Gdx.files.local(getSaveFilePath());
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

                scrollRuneNames.clear();
                if (state.scrollRuneNamesString != null) {
                    for (ObjectMap.Entry<String, String> entry : state.scrollRuneNamesString.entries()) {
                        try {
                            scrollRuneNames.put(ItemType.valueOf(entry.key), entry.value);
                        } catch (Exception e) {
                        }
                    }
                }

                identifiedDedicatedScrolls.clear();
                if (state.identifiedDedicatedScrollsString != null) {
                    for (String typeName : state.identifiedDedicatedScrollsString) {
                        try {
                            identifiedDedicatedScrolls.add(ItemType.valueOf(typeName));
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
        return Gdx.files.local(getSaveFilePath()).exists();
    }
}
