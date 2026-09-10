package com.bpm.minotaur.managers;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages post-victory difficulty modifiers (Hades-style Heat system).
 * Allows players to choose pact modifiers to scale challenge and rewards.
 */
public class TormentManager {

    private static TormentManager instance;

    public enum TormentModifier {
        FRENZIED_FOES("Frenzied Foes", "Monsters move 25% faster", 1),
        DEADLY_BLOWS("Deadly Blows", "Monsters deal 25% more damage", 2),
        BARREN_DEPTHS("Barren Depths", "Dungeon loot spawn rates reduced by 30%", 2),
        DILUTED_ELIXIRS("Diluted Elixirs", "Potions and food restore 35% less HP/MP", 1),
        CHAMPION_FOES("Champion Foes", "Monsters have higher base attributes", 2),
        CRITICAL_BRIDGE("Fragile Bridge", "Apocalypse Doom cap reduced from 50 to 30 deaths", 3);

        private final String title;
        private final String description;
        private final int heatValue;

        TormentModifier(String title, String description, int heatValue) {
            this.title = title;
            this.description = description;
            this.heatValue = heatValue;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public int getHeatValue() {
            return heatValue;
        }
    }

    private final Set<TormentModifier> activeModifiers = new HashSet<>();

    private TormentManager() {
    }

    public static synchronized TormentManager getInstance() {
        if (instance == null) {
            instance = new TormentManager();
        }
        return instance;
    }

    public int getHeatLevel() {
        int heat = 0;
        for (TormentModifier mod : activeModifiers) {
            heat += mod.getHeatValue();
        }
        return heat;
    }

    public boolean hasModifier(TormentModifier modifier) {
        return activeModifiers.contains(modifier);
    }

    public void toggleModifier(TormentModifier modifier) {
        if (activeModifiers.contains(modifier)) {
            activeModifiers.remove(modifier);
        } else {
            activeModifiers.add(modifier);
        }
    }

    public void setModifier(TormentModifier modifier, boolean enabled) {
        if (enabled) {
            activeModifiers.add(modifier);
        } else {
            activeModifiers.remove(modifier);
        }
    }

    public Set<TormentModifier> getActiveModifiers() {
        return Collections.unmodifiableSet(activeModifiers);
    }

    public void clearModifiers() {
        activeModifiers.clear();
    }

    // --- Gameplay Scaling Hooks ---

    public float getMonsterSpeedMultiplier() {
        return hasModifier(TormentModifier.FRENZIED_FOES) ? 1.25f : 1.0f;
    }

    public float getMonsterDamageMultiplier() {
        return hasModifier(TormentModifier.DEADLY_BLOWS) ? 1.25f : 1.0f;
    }

    public float getLootSpawnMultiplier() {
        return hasModifier(TormentModifier.BARREN_DEPTHS) ? 0.70f : 1.0f;
    }

    public float getHealingMultiplier() {
        return hasModifier(TormentModifier.DILUTED_ELIXIRS) ? 0.65f : 1.0f;
    }

    public int getMaxAllowedDeaths() {
        return hasModifier(TormentModifier.CRITICAL_BRIDGE) ? 30 : 50;
    }
}
