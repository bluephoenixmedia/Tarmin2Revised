package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import java.util.ArrayList;
import java.util.List;

public class TurnManager {

    private final float BASE_TURN_COST = 12.0f;

    public TurnManager() {
    }

    /**
     * Processes monster turns based on speed/energy relative to player action.
     * Assumes Player has just taken an action (costing BASE_TURN_COST adjusted by
     * speed).
     */
    public void processTurn(Maze maze, Player player, MonsterAiManager aiManager, CombatManager combatManager) {
        if (maze == null || player == null)
            return;

        // 1. Calculate Time Elapsed for Player Action
        // Time = Cost / Speed.
        // Standard Speed 12 -> Time = 1.0.
        // Speed 24 -> Time = 0.5.
        // Speed 6 -> Time = 2.0.
        int playerSpeed = player.getEffectiveSpeed();
        float timeElapsed = BASE_TURN_COST / (float) playerSpeed;

        // --- RING EFFECTS Processed Here ---
        if (player.getEquipment().hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.REGENERATION)) {
            // Regeneration: 1 HP per turn
            if (player.getCurrentHP() < player.getMaxHP()) {
                player.heal(1);
            }
        }

        if (player.getEquipment().hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.SEARCHING)) {
            // Searching: Placeholder for now, maybe reveal adjacent traps later
            // For now, just a tiny chance to reveal a random adjacent item if hidden? (Not
            // impl yet)
            // maze.detectSecretDoors(player.getPosition()); (Future)
        }
        // -----------------------------------

        // 2. Distribute Energy to Monsters
        // --- RING EFFECTS Processed Here ---
        if (player.getEquipment().hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.REGENERATION)) {
            if (player.getCurrentHP() < player.getMaxHP()) {
                player.heal(1);
            }
        }
        if (player.getEquipment().hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.SEARCHING)) {
            // Placeholder for Searching
        }
        // -----------------------------------
        List<Monster> monsters = new ArrayList<>(maze.getMonsters().values());
        for (Monster monster : monsters) {
            if (monster.getWarStrength() <= 0)
                continue; // Skip dead logic

            // --- COMBAT LOCK FIX ---
            if (combatManager != null && combatManager.getMonster() == monster) {
                continue; // Skip the monster actively dueling the player (it acts via CombatManager)
            }
            // -----------------------

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
}
