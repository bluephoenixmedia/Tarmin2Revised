package com.bpm.minotaur.gamedata.monster;

import com.bpm.minotaur.gamedata.spells.SpellDataManager;
import com.bpm.minotaur.gamedata.spells.SpellTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages spells prepared, max spell circle, and per-encounter usage limits for intelligent monsters.
 */
public class MonsterSpellbook {

    private final int maxSpellCircle;
    private final List<String> preparedSpells = new ArrayList<>();
    private final Map<String, Integer> spellUsesRemaining = new HashMap<>();

    public MonsterSpellbook(int maxSpellCircle, List<String> spells, SpellDataManager dataManager) {
        this.maxSpellCircle = Math.max(1, maxSpellCircle);
        if (spells != null) {
            for (String s : spells) {
                if (s == null) continue;
                String id = s.trim().toUpperCase();
                if (!preparedSpells.contains(id)) {
                    preparedSpells.add(id);
                    SpellTemplate t = dataManager != null ? dataManager.getSpellById(id) : null;
                    if (t != null && t.level >= 3) {
                        spellUsesRemaining.put(id, t.level >= 6 ? 1 : 2);
                    }
                }
            }
        }
    }

    /**
     * Procedurally generates a MonsterSpellbook based on intelligence, base level, school affinities, and innate spells.
     */
    public static MonsterSpellbook createForMonster(int intelligence, int baseLevel, List<String> schools,
                                                    List<String> innateSpells, SpellDataManager dataManager) {
        int circleFromLevel = Math.max(1, (baseLevel + 1) / 2);
        int intCap = Math.max(1, intelligence - 10);
        int maxCircle = Math.min(9, Math.min(circleFromLevel, intCap));

        int targetPrepared = Math.max(2, Math.max(0, (intelligence - 10) / 2) + baseLevel / 3);

        List<String> chosen = new ArrayList<>();

        // 1. Add valid innate spells first
        if (innateSpells != null) {
            for (String sp : innateSpells) {
                if (sp == null || sp.isEmpty()) continue;
                String id = sp.trim().toUpperCase();
                SpellTemplate t = dataManager != null ? dataManager.getSpellById(id) : null;
                if (t != null && !chosen.contains(id)) {
                    chosen.add(id);
                }
            }
        }

        // 2. If we need more spells and have dataManager, pull from school affinities
        if (dataManager != null && chosen.size() < targetPrepared && schools != null && !schools.isEmpty()) {
            List<SpellTemplate> candidates = new ArrayList<>();
            for (SpellTemplate t : dataManager.getAllSpells()) {
                if (t == null || t.id == null || chosen.contains(t.id)) continue;
                if (t.level <= maxCircle) {
                    for (String sc : schools) {
                        if (sc != null && t.school != null && t.school.equalsIgnoreCase(sc.trim())) {
                            candidates.add(t);
                            break;
                        }
                    }
                }
            }
            // Shuffle candidates for procedural variety
            Collections.shuffle(candidates);
            for (SpellTemplate cand : candidates) {
                if (chosen.size() >= targetPrepared) break;
                chosen.add(cand.id);
            }
        }

        // 3. Ensure at least one 0-MP cantrip is known for free ranged magical attacks
        boolean hasCantrip = false;
        if (dataManager != null) {
            for (String id : chosen) {
                SpellTemplate t = dataManager.getSpellById(id);
                if (t != null && t.mpCost == 0) {
                    hasCantrip = true;
                    break;
                }
            }
            if (!hasCantrip) {
                // Find a cantrip matching schools, or fallback to chill touch / acid splash
                String cantripPick = "CHILL_TOUCH";
                if (schools != null && schools.contains("EVOCATION")) {
                    cantripPick = "MOTE_OF_LIGHT";
                } else if (schools != null && schools.contains("CONJURATION")) {
                    cantripPick = "ACID_SPLASH";
                }
                if (dataManager.getSpellById(cantripPick) != null && !chosen.contains(cantripPick)) {
                    chosen.add(cantripPick);
                }
            }
        }

        return new MonsterSpellbook(maxCircle, chosen, dataManager);
    }

    public int getMaxSpellCircle() {
        return maxSpellCircle;
    }

    public List<String> getPreparedSpells() {
        return Collections.unmodifiableList(preparedSpells);
    }

    public int getUsesRemaining(String spellId) {
        if (spellId == null) return 0;
        Integer uses = spellUsesRemaining.get(spellId.toUpperCase());
        return uses != null ? uses : 999;
    }

    public boolean canCast(String spellId, int currentMP, SpellDataManager dataManager) {
        if (spellId == null || dataManager == null) return false;
        String id = spellId.toUpperCase();
        if (!preparedSpells.contains(id)) return false;

        SpellTemplate template = dataManager.getSpellById(id);
        if (template == null) return false;

        if (currentMP < template.getMpCost()) return false;

        if (spellUsesRemaining.containsKey(id) && spellUsesRemaining.get(id) <= 0) {
            return false;
        }

        return true;
    }

    public void recordCast(String spellId) {
        if (spellId == null) return;
        String id = spellId.toUpperCase();
        if (spellUsesRemaining.containsKey(id)) {
            int rem = spellUsesRemaining.get(id);
            spellUsesRemaining.put(id, Math.max(0, rem - 1));
        }
    }

    /**
     * Selects an appropriate offensive spell (projectile, burst, or touch) within range.
     */
    public String selectOffensiveSpell(int currentMP, float distanceToTarget, SpellDataManager dataManager) {
        if (dataManager == null) return null;

        String bestSpell = null;
        int highestLevel = -1;

        // Prefer higher level combat nukes if affordable and in range
        for (String id : preparedSpells) {
            if (!canCast(id, currentMP, dataManager)) continue;

            SpellTemplate t = dataManager.getSpellById(id);
            if (t == null) continue;

            String targetType = t.getTargetType() != null ? t.getTargetType().toUpperCase() : "PROJECTILE";
            boolean isOffensive = targetType.equals("PROJECTILE") || targetType.equals("BURST") ||
                    (targetType.equals("TOUCH") && distanceToTarget <= 1.5f) ||
                    targetType.equals("BEAM") || targetType.equals("CONE");

            if (!isOffensive) continue;

            // Check range
            int range = t.getRange() > 0 ? t.getRange() : (targetType.equals("TOUCH") ? 1 : 8);
            if (distanceToTarget > range + 0.5f) continue;

            // Prioritize higher-level spells if MP permits; otherwise fallback to cantrips
            if (t.level > highestLevel) {
                highestLevel = t.level;
                bestSpell = id;
            }
        }

        return bestSpell;
    }

    /**
     * Selects a defensive, healing, or mobility spell if available and castable.
     */
    public String selectDefensiveOrHealSpell(int currentMP, SpellDataManager dataManager) {
        if (dataManager == null) return null;

        for (String id : preparedSpells) {
            if (!canCast(id, currentMP, dataManager)) continue;

            SpellTemplate t = dataManager.getSpellById(id);
            if (t == null) continue;

            String targetType = t.getTargetType() != null ? t.getTargetType().toUpperCase() : "";
            boolean isDefensive = targetType.equals("SELF") ||
                    id.contains("HEAL") || id.contains("SHIELD") ||
                    id.contains("MISTY") || id.contains("TELEPORT") ||
                    id.contains("AID") || id.contains("IRON_SKIN");

            if (isDefensive) {
                return id;
            }
        }

        return null;
    }
}
