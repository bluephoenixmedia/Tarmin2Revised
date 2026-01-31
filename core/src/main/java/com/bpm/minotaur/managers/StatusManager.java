// MODIFIED FILE: core/src/main/java/com/bpm/minotaur/managers/StatusManager.java
package com.bpm.minotaur.managers;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.effects.ActiveStatusEffect;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;

public class StatusManager {

    // Using ObjectMap for efficient enum-key mapping
    private final ObjectMap<StatusEffectType, ActiveStatusEffect> activeEffects = new ObjectMap<>();

    // We'll need the GameEventManager to publish events (e.g., "Player cured of
    // poison")
    private transient GameEventManager eventManager;
    private transient Object owner; // The entity owning this manager (Player or Monster)

    // Constructor for LibGDX JSON serialization
    public StatusManager() {
    }

    public void initialize(GameEventManager eventManager, Object owner) {
        this.eventManager = eventManager;
        this.owner = owner;
    }

    /**
     * Applies a new status effect or updates/stacks an existing one.
     * 
     * @param type      The effect to apply
     * @param duration  Duration in turns. -1 for infinite.
     * @param potency   Potency (e.g., damage amount, stat boost)
     * @param stackable If true, adds duration. If false, resets duration.
     */
    public void addEffect(StatusEffectType type, int duration, int potency, boolean stackable) {
        if (activeEffects.containsKey(type) && stackable) {
            // Stack or refresh existing effect
            ActiveStatusEffect existing = activeEffects.get(type);
            existing.addDuration(duration);
            existing.setPotency(Math.max(potency, existing.getPotency())); // e.g., take the stronger poison
        } else {
            // Add new effect
            activeEffects.put(type, new ActiveStatusEffect(type, duration, potency));
        }

        // Post an event
        if (eventManager != null) {
            // --- THE FIX IS HERE ---
            // We must create a new GameEvent and use the 'addEvent' method.
            eventManager.addEvent(new GameEvent(GameEvent.EventType.EFFECT_APPLIED, type.name()));
            // --- END FIX ---
        }
    }

    /**
     * Removes an effect immediately.
     * 
     * @param type The effect to remove.
     */
    public void removeEffect(StatusEffectType type) {
        if (activeEffects.remove(type) != null) {

            // --- Handle Effect Expiration/Removal Consequences ---
            if (type == StatusEffectType.BERZERK && owner instanceof com.bpm.minotaur.gamedata.player.Player) {
                com.bpm.minotaur.gamedata.player.Player p = (com.bpm.minotaur.gamedata.player.Player) owner;
                int dmg = Math.max(1, p.getCurrentHP() / 2);
                p.takeDamage(dmg, com.bpm.minotaur.gamedata.DamageType.PHYSICAL); // Fatigue damage
                if (eventManager != null) {
                    eventManager.addEvent(
                            new com.bpm.minotaur.gamedata.GameEvent("The rage fades, leaving you exhausted!", 2.5f));
                    eventManager
                            .addEvent(new com.bpm.minotaur.gamedata.GameEvent("You collapse (-" + dmg + " HP)", 2.0f));
                }
            }
            // ----------------------------------------------------

            if (eventManager != null) {
                // --- THE FIX IS HERE ---
                // We must create a new GameEvent and use the 'addEvent' method.
                eventManager.addEvent(new GameEvent(GameEvent.EventType.EFFECT_CURED, type.name()));
                // --- END FIX ---
            }
        }
    }

    /**
     * Checks if an entity has a given effect.
     */
    public boolean hasEffect(StatusEffectType type) {
        return activeEffects.containsKey(type);
    }

    /**
     * Gets the active effect instance (e.g., to check potency).
     */
    public ActiveStatusEffect getEffect(StatusEffectType type) {
        return activeEffects.get(type);
    }

    /**
     * Main update tick, called once per game turn for the entity.
     * Decrements all durations and removes expired effects.
     */
    public void updateTurn() {
        Array<StatusEffectType> toRemove = new Array<>();

        for (ActiveStatusEffect effect : activeEffects.values()) {
            if (effect.updateTurn()) {
                toRemove.add(effect.getType());
            }
        }

        for (StatusEffectType type : toRemove) {
            removeEffect(type);
        }
    }

    /**
     * Returns all active effects, e.g., for the HUD to draw.
     */
    public ObjectMap.Values<ActiveStatusEffect> getActiveEffects() {
        return activeEffects.values();
    }
}
