package com.bpm.minotaur.gamedata.spells;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.utils.DiceRoller;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.CombatManager.HitResult;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.rendering.Animation;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes data-driven spells loaded from spells.json via Open5e.
 */
public class SpellExecutionEngine {

    public static boolean castSpell(String spellId, Player player, Maze maze, GameEventManager eventManager, CombatManager combatManager) {
        if (spellId == null || spellId.isEmpty()) {
            return false;
        }

        SpellTemplate spell = SpellDataManager.getInstance().getSpell(spellId);
        if (spell == null) {
            eventManager.addEvent(new GameEvent("Unknown spell: " + spellId, 1.5f));
            return false;
        }

        // Check MP (Cantrips level 0 cost 0 MP)
        if (spell.getMpCost() > 0 && !player.hasEnoughMana(spell.getMpCost())) {
            eventManager.addEvent(new GameEvent("Not enough MP! (" + spell.getMpCost() + " required)", 1.5f));
            return false;
        }

        if (spell.getMpCost() > 0) {
            player.deductMana(spell.getMpCost());
        }

        eventManager.addEvent(new GameEvent("Cast " + spell.getName() + "!", 1.5f));

        String targetType = spell.getTargetType() != null ? spell.getTargetType().toUpperCase() : "PROJECTILE";

        if ("SELF".equals(targetType)) {
            resolveSelfSpell(spell, player, maze, eventManager);
        } else if ("BURST".equals(targetType) || "CONE".equals(targetType)) {
            resolveBurstSpell(spell, player, maze, eventManager, combatManager);
        } else if ("TOUCH".equals(targetType) || "MELEE_TOUCH".equalsIgnoreCase(spell.getVisualArchetype())) {
            resolveTouchSpell(spell, player, maze, eventManager, combatManager);
        } else {
            resolveProjectileSpell(spell, player, maze, eventManager, combatManager);
        }

        return true;
    }

    private static void resolveSelfSpell(SpellTemplate spell, Player player, Maze maze, GameEventManager eventManager) {
        String name = spell.getName().toLowerCase();

        // Healing
        if (name.contains("cure") || name.contains("heal") || name.contains("aid") || name.contains("restoration")) {
            int amount = DiceRoller.roll(spell.getDamageDice());
            amount = Math.max(1, amount + player.getWisdomModifier());
            player.heal(amount);
            eventManager.addEvent(new GameEvent("Healed " + amount + " HP!", 2.0f));
            return;
        }

        // Teleport / Misty Step
        if (name.contains("misty step") || name.contains("teleport") || name.contains("dimension door")) {
            int attempts = 15;
            while (attempts-- > 0) {
                int tx = (int) (player.getPosition().x + (Math.random() * 8 - 4));
                int ty = (int) (player.getPosition().y + (Math.random() * 8 - 4));
                if (tx > 0 && tx < maze.getWidth() && ty > 0 && ty < maze.getHeight() && !maze.isWall(tx, ty)) {
                    player.setPosition(new GridPoint2(tx, ty));
                    eventManager.addEvent(new GameEvent("You phase through space!", 2.0f));
                    return;
                }
            }
            return;
        }

        // Status Effects & Wards
        if (spell.getStatusEffect() != null && !spell.getStatusEffect().isEmpty()) {
            try {
                StatusEffectType effect = StatusEffectType.valueOf(spell.getStatusEffect().toUpperCase());
                player.getStatusManager().addEffect(effect, 15, 1, false);
                eventManager.addEvent(new GameEvent("Gained " + effect.name() + "!", 2.0f));
                return;
            } catch (Exception ignored) {
            }
        }

        // Default Defense Buff
        player.getStatusManager().addEffect(StatusEffectType.HARDENED, 12, 1, false);
        eventManager.addEvent(new GameEvent("Arcane barrier envelops you! (+AC)", 2.0f));
    }

    private static void resolveProjectileSpell(SpellTemplate spell, Player player, Maze maze, GameEventManager eventManager, CombatManager combatManager) {
        int maxRange = Math.max(2, spell.getRange());
        HitResult hit = (combatManager != null) ? combatManager.raycastProjectile(player.getPosition(), player.getFacing(), maxRange, true) : null;

        Vector2 startPos = player.getPosition().cpy().add(player.getDirectionVector().cpy().scl(0.6f));
        Vector2 targetPos = (hit != null && hit.collisionPoint != null)
                ? new Vector2(hit.collisionPoint.x + 0.5f, hit.collisionPoint.y + 0.5f)
                : startPos.cpy().add(player.getDirectionVector().cpy().scl(maxRange));

        Color color = getColorForDamageType(spell.getDamageType());
        if (combatManager != null && combatManager.getAnimationManager() != null) {
            combatManager.getAnimationManager().addAnimation(new Animation(
                    Animation.AnimationType.PROJECTILE_SPELL,
                    startPos, targetPos,
                    color, 0.5f,
                    new String[] { "*" }));
        }

        if (hit != null && hit.type == HitResult.HitType.MONSTER && hit.hitMonster != null) {
            Monster target = hit.hitMonster;

            // Bad / Nasty immunity check (Spiritual Domain)
            if (Monster.isImmuneToType(target.getType(), DamageType.SPIRITUAL)) {
                eventManager.addEvent(new GameEvent(target.getType() + " is immune to spiritual magic!", 1.5f));
                if (combatManager != null) combatManager.showDamageText(0, hit.collisionPoint);
                return;
            }

            int dmg = DiceRoller.roll(spell.getDamageDice()) + player.getSpellPower();
            dmg = Math.max(1, dmg);

            int actualDmg = target.takeDamage(dmg, DamageType.SPIRITUAL, false);
            if (combatManager != null) combatManager.showDamageText(actualDmg, hit.collisionPoint);
            eventManager.addEvent(new GameEvent(spell.getName() + " hits " + target.getType() + " for " + actualDmg + "!", 1.5f));

            if (spell.getStatusEffect() != null && !spell.getStatusEffect().isEmpty()) {
                try {
                    StatusEffectType effect = StatusEffectType.valueOf(spell.getStatusEffect().toUpperCase());
                    target.getStatusManager().addEffect(effect, 5, 1, false);
                } catch (Exception ignored) {
                }
            }

            if (target.getCurrentHP() <= 0) {
                if (combatManager != null) {
                    if (target == combatManager.getMonster()) {
                        combatManager.handleMonsterDeath();
                    } else {
                        combatManager.handleRemoteKill(target);
                    }
                } else {
                    maze.getMonsters().remove(hit.collisionPoint);
                    player.getStats().addExperience(target.getBaseExperience());
                }
                eventManager.addEvent(new GameEvent("Vanquished " + target.getType() + "!", 2.0f));
            }
        } else {
            eventManager.addEvent(new GameEvent(spell.getName() + " dissipates against the stone.", 1.0f));
        }
    }

    private static void resolveBurstSpell(SpellTemplate spell, Player player, Maze maze, GameEventManager eventManager, CombatManager combatManager) {
        int castRange = Math.min(8, spell.getRange());
        HitResult hit = (combatManager != null) ? combatManager.raycastProjectile(player.getPosition(), player.getFacing(), castRange, true) : null;

        GridPoint2 center = (hit != null && hit.collisionPoint != null)
                ? hit.collisionPoint
                : new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);

        Color color = getColorForDamageType(spell.getDamageType());
        eventManager.addEvent(new GameEvent("BURST! " + spell.getName() + " explodes!", 1.5f));

        int burstRadius = 2;
        List<GridPoint2> monsterPositions = new ArrayList<>(maze.getMonsters().keySet());
        int hits = 0;

        for (GridPoint2 pos : monsterPositions) {
            if (Math.abs(pos.x - center.x) <= burstRadius && Math.abs(pos.y - center.y) <= burstRadius) {
                Monster target = maze.getMonsters().get(pos);
                if (target == null || target.getCurrentHP() <= 0) continue;

                if (Monster.isImmuneToType(target.getType(), DamageType.SPIRITUAL)) {
                    continue;
                }

                int dmg = DiceRoller.roll(spell.getDamageDice()) + player.getSpellPower();
                dmg = Math.max(1, dmg);

                int actual = target.takeDamage(dmg, DamageType.SPIRITUAL, false);
                if (combatManager != null) combatManager.showDamageText(actual, pos);
                hits++;

                if (target.getCurrentHP() <= 0) {
                    if (combatManager != null) {
                        if (target == combatManager.getMonster()) {
                            combatManager.handleMonsterDeath();
                        } else {
                            combatManager.handleRemoteKill(target);
                        }
                    } else {
                        maze.getMonsters().remove(pos);
                        player.getStats().addExperience(target.getBaseExperience());
                    }
                }
            }
        }

        if (hits > 0) {
            eventManager.addEvent(new GameEvent(spell.getName() + " struck " + hits + " monsters!", 1.5f));
        }
    }

    private static void resolveTouchSpell(SpellTemplate spell, Player player, Maze maze, GameEventManager eventManager, CombatManager combatManager) {
        Direction facing = player.getFacing();
        int tx = (int) Math.floor(player.getPosition().x + facing.getVector().x);
        int ty = (int) Math.floor(player.getPosition().y + facing.getVector().y);
        GridPoint2 targetPos = new GridPoint2(tx, ty);

        Monster target = maze.getMonsters().get(targetPos);
        if (target != null && target.getCurrentHP() > 0) {
            if (Monster.isImmuneToType(target.getType(), DamageType.SPIRITUAL)) {
                eventManager.addEvent(new GameEvent(target.getType() + " is immune to spiritual touch!", 1.5f));
                if (combatManager != null) combatManager.showDamageText(0, targetPos);
                return;
            }

            int dmg = DiceRoller.roll(spell.getDamageDice()) + player.getSpellPower();
            int actual = target.takeDamage(dmg, DamageType.SPIRITUAL, false);
            if (combatManager != null) combatManager.showDamageText(actual, targetPos);
            eventManager.addEvent(new GameEvent("Touch of " + spell.getName() + " hits for " + actual + "!", 1.5f));

            if (target.getCurrentHP() <= 0) {
                if (combatManager != null) {
                    if (target == combatManager.getMonster()) {
                        combatManager.handleMonsterDeath();
                    } else {
                        combatManager.handleRemoteKill(target);
                    }
                } else {
                    maze.getMonsters().remove(targetPos);
                    player.getStats().addExperience(target.getBaseExperience());
                }
                eventManager.addEvent(new GameEvent("Vanquished " + target.getType() + "!", 2.0f));
            }
        } else {
            eventManager.addEvent(new GameEvent("You reach out with " + spell.getName() + ", but find only empty air.", 1.0f));
        }
    }

    public static Color getColorForDamageType(String damageType) {
        if (damageType == null) return Color.CYAN;
        switch (damageType.toUpperCase()) {
            case "FIRE": return Color.ORANGE;
            case "COLD": return Color.CYAN;
            case "LIGHTNING": return Color.YELLOW;
            case "ACID": return Color.GREEN;
            case "POISON": return Color.CHARTREUSE;
            case "RADIANT": return Color.GOLD;
            case "NECROTIC": return Color.PURPLE;
            case "THUNDER": return Color.LIGHT_GRAY;
            case "PSYCHIC": return Color.MAGENTA;
            case "FORCE":
            default:
                return Color.SKY;
        }
    }
}
