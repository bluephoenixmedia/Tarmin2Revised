package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.weather.WeatherManager;
import com.bpm.minotaur.gamedata.GameEvent;

import java.util.ArrayList;
import java.util.List;

public class TurnManager {

    private final float BASE_TURN_COST = 12.0f;

    // Metabolic Constants (Per 1.0 Time Unit)
    private static final float SATIETY_DECAY = 0.02f; // ~5000 turns to starve
    private static final float HYDRATION_DECAY = 0.04f; // ~2500 turns to dehydrate
    private static final float TEMP_ADJUST_RATE = 0.05f; // Speed of body temp adjustment

    public TurnManager() {
    }

    /**
     * Processes monster turns based on speed/energy relative to player action.
     * Assumes Player has just taken an action (costing BASE_TURN_COST adjusted by
     * speed).
     */
    public void processTurn(Maze maze, Player player, MonsterAiManager aiManager, CombatManager combatManager,
            WorldManager worldManager, GameEventManager eventManager) {
        if (maze == null || player == null)
            return;

        // 1. Calculate Time Elapsed for Player Action
        // Time = Cost / Speed.
        // Standard Speed 12 -> Time = 1.0.
        int playerSpeed = player.getEffectiveSpeed();
        float timeElapsed = BASE_TURN_COST / (float) playerSpeed;

        // --- NEW: SURVIVAL METABOLISM ---
        updateMetabolism(player, worldManager, eventManager, timeElapsed);
        // --------------------------------

        // --- RING EFFECTS ---
        // (Moved after metabolism so we can heal starvation damage if we have regen
        // ring?)
        // Actually Regeneration ring logic was duplicated. Fixed here.
        if (player.getEquipment().hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.REGENERATION)) {
            // Regeneration: 1 HP per turn
            if (player.getCurrentHP() < player.getMaxHP()) {
                player.heal(1);
            }
        }

        if (player.getEquipment().hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.SEARCHING)) {
            // Placeholder for Searching
        }

        // WIS passive MP regeneration: each WIS modifier point = 10% chance per turn to restore 1 MP
        int wisModifier = Math.max(0, (player.getEffectiveWisdom() - 10) / 2);
        if (wisModifier > 0 && player.getCurrentMP() < player.getEffectiveMaxMP()) {
            if (Math.random() < wisModifier * 0.10) {
                player.getStats().restoreMP(1);
            }
        }
        // -----------------------------------

        // 2. Distribute Energy to Monsters
        List<Monster> monsters = new ArrayList<>(maze.getMonsters().values());
        for (Monster monster : monsters) {
            if (monster.getWarStrength() <= 0)
                continue; // Skip dead logic

            // --- COMBAT LOCK FIX ---
            if (combatManager != null && combatManager.getMonster() == monster) {
                continue; // Skip the monster actively dueling the player (it acts via CombatManager)
            }
            // -----------------------

            int monsterSpeed = monster.getEffectiveSpeed();
            float energyGain = timeElapsed * monsterSpeed;

            monster.addEnergy(energyGain);

            // 3. Process Actions if Energy Threshold Met
            // Use loop for very fast monsters taking multiple actions
            int safetyCounter = 0;
            while (monster.getEnergy() >= BASE_TURN_COST) {
                // Consume Energy
                monster.setEnergy(monster.getEnergy() - BASE_TURN_COST);

                // Act
                aiManager.updateMonster(monster, maze, player, true, combatManager);

                safetyCounter++;
                if (safetyCounter > 10)
                    break; // Prevent infinite loops
            }
        }
    }

    private void updateMetabolism(Player player, WorldManager worldManager, GameEventManager eventManager, float time) {
        PlayerStats stats = player.getStats();

        // 1. Hunger & Thirst Decay
        stats.modifySatiety(-SATIETY_DECAY * time);
        stats.modifyHydration(-HYDRATION_DECAY * time);

        // 2. Temperature Logic
        if (worldManager != null && worldManager.getWeatherManager() != null) {
            Biome biome = worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId());
            float ambientTemp = worldManager.getWeatherManager().getAmbientTemperature(biome);
            float currentTemp = stats.getBodyTemperature();

            // Simple insulation: basic clothes reduce rate of change towards ambient
            // TODO: Query armor warmth
            // Simple insulation: basic clothes reduce rate of change towards ambient
            // TODO: Query armor warmth

            // If freezing, armor helps. If hot, armor hurts (maybe?).
            // For now, let's assume body naturally returns to 37 if ambient is reasonable.

            // Approach ambient

            // Body Homeostasis: The body fights to stay at 37.
            // If ambient is < 37, body loses heat.
            // If ambient > 37, body gains heat.

            // Let's model "Feels Like" vs Body Temp.
            // If ambient is 20C, body maintains 37C easily.
            // Only EXTREME temps shift body temp.

            float totalWarmth = 0f;
            if (player.getEquipment() != null) {
                for (com.bpm.minotaur.gamedata.item.Item i : player.getEquipment().getAllEquipped()) {
                    if (i != null)
                        totalWarmth += i.getWarmthBonus();
                }
            }

            float stress = 0f;
            if (ambientTemp < 10.0f) {
                // Cold stress
                float effectiveCold = (10.0f - ambientTemp) - totalWarmth;
                if (effectiveCold > 0) {
                    stress = -effectiveCold * 0.01f;
                } else {
                    // Warm enough, maybe slowly recover to 37
                    if (currentTemp < 37.0f) {
                        stress = 0.005f;
                    }
                }
            } else if (ambientTemp > 30.0f) {
                stress = (ambientTemp - 30.0f) * 0.01f; // Heat stress (Armor makes it worse? For now ignore)
            } else {
                // Recovery to 37
                if (currentTemp < 37.0f)
                    stress = 0.01f;
                if (currentTemp > 37.0f)
                    stress = -0.01f;
                // Snap if close
                if (Math.abs(currentTemp - 37.0f) < 0.1f) {
                    stats.setBodyTemperature(37.0f);
                    stress = 0f;
                }
            }

            stats.setBodyTemperature(currentTemp + (stress * time));
        }

        // 3. Effects / Damage
        // Starvation
        if (stats.getSatietyFloat() <= 0) {
            if (Math.random() < 0.1) { // Chance to take damage every turn
                player.takeTrueDamage(1);
                if (eventManager != null)
                    eventManager.addEvent(new GameEvent("You are starving!", 1.0f));
            }
        }

        // Dehydration
        if (stats.getHydrationFloat() <= 0) {
            if (Math.random() < 0.2) { // Thirst hurts more
                player.takeTrueDamage(1);
                if (eventManager != null)
                    eventManager.addEvent(new GameEvent("You are parched!", 1.0f));
            }
        }

        // Hypothermia
        if (stats.getBodyTemperature() < PlayerStats.BODY_TEMP_FREEZING) {
            if (Math.random() < 0.2) {
                player.takeTrueDamage(1);
                if (eventManager != null)
                    eventManager.addEvent(new GameEvent("You are freezing to death!", 1.0f));
            }
        }

        // Hyperthermia
        if (stats.getBodyTemperature() > PlayerStats.BODY_TEMP_OVERHEAT) {
            if (Math.random() < 0.2) {
                player.takeTrueDamage(1);
                if (eventManager != null)
                    eventManager.addEvent(new GameEvent("You are overheating!", 1.0f));
            }
        }
    }
}
