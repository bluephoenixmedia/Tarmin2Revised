package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.Item.ItemType; // Assuming ItemType is inner enum
import com.bpm.minotaur.gamedata.item.PotionEffectType;
import com.bpm.minotaur.gamedata.player.Player;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class PotionManager {

    private static final String SAVE_FILE = "saves/world/potion_state.json";

    // This is the per-save-game randomized map.
    // Maps "POTION_BLUE" -> HEALING
    private ObjectMap<String, PotionEffectType> effectMap = new ObjectMap<>();

    // This tracks what the player has identified *this game*.
    // Maps HEALING -> true
    private ObjectMap<PotionEffectType, Boolean> identifiedStatus = new ObjectMap<>();

    private final Json json;
    private final GameEventManager eventManager;

    public PotionManager(GameEventManager eventManager) {
        this.eventManager = eventManager;
        this.json = new Json();
        this.json.setUsePrototypes(false);
    }

    /**
     * Generates a new random mapping for a new game.
     * @param potionAppearanceTypes All ItemTypes marked as a potion appearance.
     */
    public void initializeNewGame(List<ItemType> potionAppearanceTypes) {

        Gdx.app.log("PotionManager [DEBUG]", "initializeNewGame STARTING...");

        effectMap.clear();
        identifiedStatus.clear();

        List<PotionEffectType> effects = new ArrayList<>();
        Collections.addAll(effects, PotionEffectType.values());
        Collections.shuffle(effects);

        List<ItemType> types = new ArrayList<>(potionAppearanceTypes);
        Collections.shuffle(types);

        int count = Math.min(types.size(), effects.size());
        for (int i = 0; i < count; i++) {
            effectMap.put(types.get(i).name(), effects.get(i));
            Gdx.app.log("PotionManager", "Mapping: " + types.get(i).name() + " -> " + effects.get(i).name());
        }

        for (PotionEffectType effect : PotionEffectType.values()) {
            identifiedStatus.put(effect, true);
        }
        Gdx.app.log("PotionManager", "Initialized new game with " + count + " potion mappings.");
    }

    /**
     * Consumes a potion, applies its effect, and handles identification.
     * @param player The player.
     * @param potion The Item instance being consumed.
     */
    public void consumePotion(Player player, Item potion) {

        Gdx.app.log("PotionManager [DEBUG]", "consumePotion called for: " + potion.getFriendlyName());

        if (potion.getTrueEffect() == null) {
            Gdx.app.error("PotionManager [DEBUG]", "CRITICAL: Potion " + potion.getTypeName() + " (friendly: " + potion.getFriendlyName() + ") has no true effect!");            return;
        }

        PotionEffectType effect = potion.getTrueEffect();

        StatusManager statusManager = player.getStatusManager();

        // 1. Apply the effect
        Gdx.app.log("PotionManager", "Player consumed " + potion.getTypeName() + ", applying effect: " + effect.name());
        effect.applyEffect(player, statusManager);

        // 2. Show consume message
        eventManager.addEvent(new com.bpm.minotaur.gamedata.GameEvent(effect.getConsumeMessage(), 2.0f));

        // 3. Check for self-identification
        if (!isEffectIdentified(effect) && effect.doesSelfIdentify()) {
            identifyEffect(player, effect);
        }
    }

    /**
     * Marks an effect as identified and updates player inventory.
     * @param player The player.
     * @param effect The effect to identify.
     */
    public void identifyEffect(Player player, PotionEffectType effect) {
        if (isEffectIdentified(effect)) return; // Already known

        identifiedStatus.put(effect, true);
        Gdx.app.log("PotionManager", "Identified effect: " + effect.name());

        eventManager.addEvent(new com.bpm.minotaur.gamedata.GameEvent("You identified the Potion of " + effect.getBaseName(), 2.5f));

        // 3. Update all matching items in player's inventory
        for (Item item : player.getInventory().getAllItems()) { // We'll add getAllItems() to Inventory
            if (item.getTrueEffect() == effect && !item.isIdentified()) {
                item.setIdentified(true);
                // Dynamically update the item's name
                item.setName("Potion of " + effect.getBaseName());
            }
        }
    }

    public PotionEffectType getEffectForAppearance(String appearanceId) {
        return effectMap.get(appearanceId);
    }

    public boolean isEffectIdentified(PotionEffectType effect) {
        return identifiedStatus.get(effect, false);
    }

    /**
     * Gets the correct display name for a potion item.
     * @param appearanceTemplate The "base" template (e.g., "Blue Potion").
     * @param effect The true effect (e.g., HEALING).
     * @param isIdentified The identified status.
     * @return "Potion of Healing" or "Blue Potion"
     */
    public String getPotionDisplayName(ItemTemplate appearanceTemplate, PotionEffectType effect, boolean isIdentified) {
        if (isIdentified && effect != null) {
            return "Potion of " + effect.getBaseName();
        } else {
            return appearanceTemplate.friendlyName;
        }
    }

    // --- Save/Load ---
    // A private inner class for serialization
    private static class PotionSaveState {
        public ObjectMap<String, PotionEffectType> effectMap;
        public ObjectMap<PotionEffectType, Boolean> identifiedStatus;
    }

    public void saveState() {
        try {
            PotionSaveState state = new PotionSaveState();
            state.effectMap = this.effectMap;
            state.identifiedStatus = this.identifiedStatus;

            FileHandle file = Gdx.files.local(SAVE_FILE);
            file.writeString(json.prettyPrint(state), false);
            Gdx.app.log("PotionManager", "Saved potion state.");
        } catch (Exception e) {
            Gdx.app.error("PotionManager", "Failed to save potion state", e);
        }
    }

    public void loadState() {
        try {
            FileHandle file = Gdx.files.local(SAVE_FILE);
            if (file.exists()) {
                PotionSaveState state = json.fromJson(PotionSaveState.class, file);
                this.effectMap = state.effectMap;
                this.identifiedStatus = state.identifiedStatus;
                Gdx.app.log("PotionManager", "Loaded potion state.");
            } else {
                Gdx.app.error("PotionManager", "No potion save state found. This should only happen if loading a pre-potion-system save.");
                // The game loading logic must handle this.
            }
        } catch (Exception e) {
            Gdx.app.error("PotionManager", "Failed to load potion state", e);
        }
    }

    public boolean hasSaveState() {
        return Gdx.files.local(SAVE_FILE).exists();
    }
}
