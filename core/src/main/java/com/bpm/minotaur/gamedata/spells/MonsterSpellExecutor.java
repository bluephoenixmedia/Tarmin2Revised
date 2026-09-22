package com.bpm.minotaur.gamedata.spells;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.rendering.Animation;
import com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType;
import com.bpm.minotaur.screens.GameScreen;
import com.bpm.minotaur.utils.DiceRoller;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles casting and resolution of spells invoked by monsters targeting the player or themselves.
 */
public class MonsterSpellExecutor {

    public static boolean castMonsterSpell(Monster caster, String spellId, Player player, Maze maze,
                                          GameEventManager eventManager, CombatManager combatManager) {
        if (caster == null || spellId == null || player == null) {
            return false;
        }

        SpellTemplate spell = SpellDataManager.getInstance().getSpell(spellId);
        if (spell == null) {
            return false;
        }

        // Deduct MP if spell has an MP cost
        if (spell.getMpCost() > 0) {
            if (!caster.hasEnoughMana(spell.getMpCost())) {
                return false;
            }
            caster.deductMana(spell.getMpCost());
        }

        // Record cast in monster's spellbook (usage tracking for Level 3+ spells)
        if (caster.getSpellbook() != null) {
            caster.getSpellbook().recordCast(spellId);
        }

        VisualArchetype archetype = spell.getVisualArchetypeEnum();
        Color primaryColor = archetype != null ? archetype.getPrimaryColor() : Color.PURPLE;

        // 1. Monster Sprite Telegraph Flash
        caster.triggerSpellFlash(primaryColor, 0.45f);

        // 2. Synthesized Sound
        if (combatManager != null && combatManager.getSoundManager() != null && archetype != null) {
            combatManager.getSoundManager().playSpellSound(archetype);
        }

        // 3. Combat Event Log
        String monsterName = caster.getMonsterType() != null ? caster.getMonsterType() : "Monster";
        if (eventManager != null) {
            eventManager.addEvent(new GameEvent(monsterName + " casts " + spell.getName() + "!", 2.0f));
        }

        GameScreen gs = (combatManager != null) ? combatManager.getGameScreen() : null;

        // 4. Dispatch based on Target Type
        String targetType = spell.getTargetType() != null ? spell.getTargetType().toUpperCase() : "PROJECTILE";

        if ("SELF".equals(targetType) || spellId.contains("HEAL") || spellId.contains("SHIELD") || spellId.contains("MISTY") || spellId.contains("TELEPORT")) {
            resolveMonsterSelfSpell(caster, spell, archetype, maze, eventManager, combatManager, gs);
        } else if ("BURST".equals(targetType) || "CONE".equals(targetType) || "FIREBALL".equalsIgnoreCase(spellId)) {
            resolveMonsterBurstSpell(caster, spell, archetype, player, maze, eventManager, combatManager, gs);
        } else {
            resolveMonsterProjectileSpell(caster, spell, archetype, player, maze, eventManager, combatManager, gs);
        }

        return true;
    }

    private static void resolveMonsterProjectileSpell(Monster caster, SpellTemplate spell, VisualArchetype archetype,
                                                     Player player, Maze maze, GameEventManager eventManager,
                                                     CombatManager combatManager, GameScreen gs) {
        Vector2 startPos = caster.getPosition().cpy();
        Vector2 targetPos = player.getPosition().cpy();
        float dist = startPos.dst(targetPos);
        float duration = Math.max(0.18f, Math.min(0.5f, dist / 12f));

        Color color = archetype != null ? archetype.getPrimaryColor() : Color.MAGENTA;

        if (combatManager != null && combatManager.getAnimationManager() != null) {
            combatManager.getAnimationManager().addAnimation(new Animation(
                    Animation.AnimationType.PROJECTILE_SPELL,
                    startPos, targetPos,
                    color, duration,
                    new String[] { "*" }));
        }

        // Saving throw vs Spell DC: 8 + (INT - 10) / 2
        int spellDC = 8 + Math.max(0, (caster.getIntelligence() - 10) / 2);
        int statBonus = 0;
        if (player.getStats() != null) {
            String dmgType = spell.getDamageType() != null ? spell.getDamageType().toUpperCase() : "";
            if (dmgType.equals("PSYCHIC") || (spell.getSchool() != null && spell.getSchool().equalsIgnoreCase("enchantment"))) {
                statBonus = (player.getStats().getWisdom() - 10) / 2;
            } else if (dmgType.equals("POISON") || dmgType.equals("NECROTIC") || dmgType.equals("COLD")) {
                statBonus = (player.getStats().getConstitution() - 10) / 2;
            } else {
                statBonus = (player.getStats().getDexterity() - 10) / 2;
            }
        }

        int d20 = DiceRoller.d20();
        boolean saveSuccess = (d20 + statBonus) >= spellDC;

        int baseDmg = DiceRoller.roll(spell.getDamageDice() != null && !spell.getDamageDice().equals("0") ? spell.getDamageDice() : "1d8+2");
        if (baseDmg < 1) baseDmg = 1;

        int finalDmg = saveSuccess ? Math.max(1, baseDmg / 2) : baseDmg;

        DamageType dt = DamageType.MAGICAL;
        try {
            if (spell.getDamageType() != null) {
                dt = DamageType.valueOf(spell.getDamageType().toUpperCase());
            }
        } catch (Exception ignored) {}

        int taken = player.takeDamage(finalDmg, dt);

        if (combatManager != null) {
            combatManager.showPlayerDamageText(taken, false, dt);
        }

        if (saveSuccess) {
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("You resisted some of the spell's effect! (" + taken + " dmg)", 1.5f));
            }
        } else {
            // Apply status effects if failed save
            applySpellStatusEffectsToPlayer(spell, player, eventManager, caster);
        }

        // Visual screen trauma / post-processing shake
        if (gs != null) {
            gs.addTrauma(0.3f);
            if (gs.getSpellPostProcessor() != null && archetype != null) {
                gs.getSpellPostProcessor().triggerVignette(color, 0.4f, 0.35f);
            }
        }
    }

    private static void resolveMonsterBurstSpell(Monster caster, SpellTemplate spell, VisualArchetype archetype,
                                                 Player player, Maze maze, GameEventManager eventManager,
                                                 CombatManager combatManager, GameScreen gs) {
        Vector2 startPos = caster.getPosition().cpy();
        Vector2 targetPos = player.getPosition().cpy();
        float dist = startPos.dst(targetPos);
        float duration = Math.max(0.2f, Math.min(0.5f, dist / 14f));

        Color color = archetype != null ? archetype.getPrimaryColor() : Color.ORANGE;

        if (combatManager != null && combatManager.getAnimationManager() != null) {
            combatManager.getAnimationManager().addAnimation(new Animation(
                    Animation.AnimationType.PROJECTILE_SPELL,
                    startPos, targetPos,
                    color, duration,
                    new String[] { "*" }));

            Vector3 impact3d = new Vector3(targetPos.x, 0.5f, targetPos.y);
            combatManager.getAnimationManager().spawnExplosion(ExplosionType.FIRE, impact3d, 1.5f, 0.5f);
        }

        int spellDC = 8 + Math.max(0, (caster.getIntelligence() - 10) / 2);
        int dexBonus = (player.getStats() != null) ? (player.getStats().getDexterity() - 10) / 2 : 0;
        boolean saveSuccess = (DiceRoller.d20() + dexBonus) >= spellDC;

        int baseDmg = DiceRoller.roll(spell.getDamageDice() != null && !spell.getDamageDice().equals("0") ? spell.getDamageDice() : "3d6");
        int finalDmg = saveSuccess ? Math.max(1, baseDmg / 2) : baseDmg;

        DamageType dt = DamageType.MAGICAL;
        try {
            if (spell.getDamageType() != null) {
                dt = DamageType.valueOf(spell.getDamageType().toUpperCase());
            }
        } catch (Exception ignored) {}

        int taken = player.takeDamage(finalDmg, dt);

        if (combatManager != null) {
            combatManager.showPlayerDamageText(taken, false, dt);
        }

        if (saveSuccess) {
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("You dodged the brunt of the blast! (" + taken + " dmg)", 1.5f));
            }
        } else {
            applySpellStatusEffectsToPlayer(spell, player, eventManager, caster);
        }

        if (gs != null) {
            gs.addTrauma(0.45f);
            if (gs.getSpellPostProcessor() != null) {
                gs.getSpellPostProcessor().triggerShockwave(0.5f, 0.5f, 0.5f, 0.1f);
            }
        }
    }

    private static void resolveMonsterSelfSpell(Monster caster, SpellTemplate spell, VisualArchetype archetype,
                                               Maze maze, GameEventManager eventManager,
                                               CombatManager combatManager, GameScreen gs) {
        String id = spell.getId() != null ? spell.getId().toUpperCase() : "";

        if (id.contains("MISTY") || id.contains("TELEPORT")) {
            // Find an open tile in maze 2-4 tiles away from player
            GridPoint2 casterPos = new GridPoint2((int) caster.getPosition().x, (int) caster.getPosition().y);
            List<GridPoint2> candidates = new ArrayList<>();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    int nx = casterPos.x + dx;
                    int ny = casterPos.y + dy;
                    if (Math.abs(dx) + Math.abs(dy) >= 2 && maze != null && !maze.isWall(nx, ny)) {
                        GridPoint2 pt = new GridPoint2(nx, ny);
                        if (!maze.getMonsters().containsKey(pt)) {
                            candidates.add(pt);
                        }
                    }
                }
            }
            if (!candidates.isEmpty()) {
                GridPoint2 dest = candidates.get((int) (Math.random() * candidates.size()));
                maze.getMonsters().remove(casterPos);
                caster.getPosition().set(dest.x + 0.5f, dest.y + 0.5f);
                maze.getMonsters().put(dest, caster);
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent(caster.getMonsterType() + " vanishes into thin air!", 1.8f));
                }
            }
        } else {
            // Heal / Shield
            int healAmount = DiceRoller.roll(spell.getDamageDice() != null && !spell.getDamageDice().equals("0") ? spell.getDamageDice() : "2d8+4");
            int healed = caster.heal(healAmount);
            if (combatManager != null) {
                combatManager.showDamageText(healed, new GridPoint2((int) caster.getPosition().x, (int) caster.getPosition().y), "+", Color.GREEN, false, DamageType.SPIRITUAL);
            }
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent(caster.getMonsterType() + " heals " + healed + " HP!", 1.5f));
            }
        }
    }

    private static void applySpellStatusEffectsToPlayer(SpellTemplate spell, Player player,
            GameEventManager eventManager, Monster caster) {
        if (spell == null || player == null) return;

        String id = spell.getId() != null ? spell.getId().toUpperCase() : "";
        String dt = spell.getDamageType() != null ? spell.getDamageType().toUpperCase() : "";

        if (id.contains("POISON") || dt.equals("POISON") || dt.equals("ACID")) {
            if (player.getStatusManager() != null) {
                // Same dose curve as a venomous bite, keyed off the caster.
                int casterLevel = (caster != null) ? caster.getLevel() : 1;
                int ticks = com.bpm.minotaur.gamedata.effects.PoisonDose.ticksFor(casterLevel);
                int potency = com.bpm.minotaur.gamedata.effects.PoisonDose.potencyFor(casterLevel);
                player.getStatusManager().addEffect(StatusEffectType.POISONED, ticks, potency, false);
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent(
                            "Toxic magic poisons your veins! (" + ticks + " turns)", 2.0f));
                }
            }
        } else if (id.contains("CHILL") || id.contains("FROST") || dt.equals("COLD")) {
            if (player.getStatusManager() != null) {
                player.getStatusManager().addEffect(StatusEffectType.FREEZING, 8, 1, false);
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("Freezing frost numbs your limbs!", 2.0f));
                }
            }
        } else if (id.contains("ENFEEBLEMENT") || id.contains("WEAK")) {
            if (player.getStatusManager() != null) {
                player.getStatusManager().addEffect(StatusEffectType.WEAKENED, 10, 2, false);
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("A dark ray saps your strength!", 2.0f));
                }
            }
        } else if (id.contains("SLOW") || id.contains("HOLD")) {
            if (player.getStatusManager() != null) {
                player.getStatusManager().addEffect(StatusEffectType.SLOWED, 6, 2, false);
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("Arcane power binds your movements!", 2.0f));
                }
            }
        } else if (id.contains("FIRE") || dt.equals("FIRE")) {
            if (player.getStatusManager() != null) {
                player.getStatusManager().addEffect(StatusEffectType.HOT, 6, 1, false);
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("Flames scorch your skin!", 2.0f));
                }
            }
        }
    }
}
