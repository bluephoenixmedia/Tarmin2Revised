package com.bpm.minotaur.gamedata.spells;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
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
import com.bpm.minotaur.rendering.AnimationManager;
import com.bpm.minotaur.screens.GameScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized execution engine for data-driven spells loaded from spells.json via Open5e.
 * Orchestrates:
 * - First-person hand raising with school-specific glowing palm runes
 * - Procedural synthesized sound archetypes in SoundManager
 * - Screen-space shader post-processing (radial shockwaves, chromatic aberration, vignettes, glitch)
 * - 3D hallway particle cascades and retro sprite ballistics
 * - Environmental scorch and elemental surface decals
 * - Bespoke multi-stage flourishes for iconic spells (The Fab Five)
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

        VisualArchetype archetype = spell.getVisualArchetypeEnum();
        GameScreen gs = (combatManager != null) ? combatManager.getGameScreen() : null;

        // 1. Trigger First-Person Hand-Raise Animation with Glowing Mystic Rune on Palm
        if (gs != null && gs.getSpellCastOverlay() != null) {
            gs.getSpellCastOverlay().triggerCast(0.8f, null, spell.getRuneSchool(), archetype.getPrimaryColor());
        }

        // 2. Trigger Domain-Specific Synthesized Audio
        if (combatManager != null && combatManager.getSoundManager() != null) {
            combatManager.getSoundManager().playSpellSound(archetype);
        }

        // 3. Trigger Screen-Space Post-Processing Shader FX
        if (gs != null && gs.getSpellPostProcessor() != null) {
            gs.getSpellPostProcessor().triggerArchetypeFX(archetype, 0.5f, 0.5f);
        }

        eventManager.addEvent(new GameEvent("Cast " + spell.getName() + "!", 1.5f));

        // 4. Check for Iconic Bespoke Flourishes (The Fab Five)
        String bespoke = spell.getBespokeEffect();
        if ("FIREBALL".equalsIgnoreCase(bespoke)) {
            resolveFireballBespoke(spell, archetype, player, maze, eventManager, combatManager, gs);
            return true;
        } else if ("MAGIC_MISSILE".equalsIgnoreCase(bespoke)) {
            resolveMagicMissileBespoke(spell, archetype, player, maze, eventManager, combatManager, gs);
            return true;
        } else if ("MISTY_STEP".equalsIgnoreCase(bespoke)) {
            resolveMistyStepBespoke(spell, archetype, player, maze, eventManager, combatManager, gs);
            return true;
        } else if ("THUNDERWAVE".equalsIgnoreCase(bespoke)) {
            resolveThunderwaveBespoke(spell, archetype, player, maze, eventManager, combatManager, gs);
            return true;
        } else if ("SHIELD".equalsIgnoreCase(bespoke)) {
            resolveShieldBespoke(spell, archetype, player, maze, eventManager, combatManager, gs);
            return true;
        }

        // 5. Standard Dispatch by Target Type
        String targetType = spell.getTargetType() != null ? spell.getTargetType().toUpperCase() : "PROJECTILE";

        if ("SELF".equals(targetType)) {
            resolveSelfSpell(spell, archetype, player, maze, eventManager, gs);
        } else if ("BURST".equals(targetType) || "CONE".equals(targetType)) {
            resolveBurstSpell(spell, archetype, player, maze, eventManager, combatManager, gs);
        } else if ("TOUCH".equals(targetType) || "MELEE_TOUCH".equalsIgnoreCase(spell.getVisualArchetype())) {
            resolveTouchSpell(spell, archetype, player, maze, eventManager, combatManager, gs);
        } else {
            resolveProjectileSpell(spell, archetype, player, maze, eventManager, combatManager, gs);
        }

        return true;
    }

    // =========================================================================
    // THE FAB FIVE: BESPOKE ICONIC SPELL IMPLEMENTATIONS
    // =========================================================================

    private static void resolveFireballBespoke(SpellTemplate spell, VisualArchetype archetype, Player player, Maze maze,
                                               GameEventManager eventManager, CombatManager combatManager, GameScreen gs) {
        int castRange = Math.min(12, Math.max(4, spell.getRange()));
        HitResult hit = (combatManager != null) ? combatManager.raycastProjectile(player.getPosition(), player.getFacing(), castRange, true) : null;

        Vector2 startPos = player.getPosition().cpy().add(player.getDirectionVector().cpy().scl(0.6f));
        Vector2 targetPos = (hit != null && hit.collisionPoint != null)
                ? new Vector2(hit.collisionPoint.x + 0.5f, hit.collisionPoint.y + 0.5f)
                : startPos.cpy().add(player.getDirectionVector().cpy().scl(castRange));

        // Fast flying fiery bead
        if (combatManager != null && combatManager.getAnimationManager() != null) {
            combatManager.getAnimationManager().addAnimation(new Animation(
                    Animation.AnimationType.PROJECTILE_SPELL,
                    startPos, targetPos,
                    archetype.getPrimaryColor(), 0.35f,
                    new String[] { "*" }));
        }

        // On impact at target tile:
        GridPoint2 center = (hit != null && hit.collisionPoint != null) ? hit.collisionPoint : new GridPoint2((int) targetPos.x, (int) targetPos.y);

        // 1. Radial Screen Shockwave & Camera Trauma
        if (gs != null) {
            if (gs.getSpellPostProcessor() != null) {
                gs.getSpellPostProcessor().triggerShockwave(0.5f, 0.55f, 0.65f, 0.12f);
                gs.getSpellPostProcessor().triggerVignette(new Color(1f, 0.35f, 0.05f, 1f), 0.9f, 0.7f);
            }
            gs.addTrauma(0.45f);
        }

        // 2. Fiery 3D Particle Cascade
        Vector3 impact3d = new Vector3(center.x + 0.5f, 0.5f, center.y + 0.5f);
        if (combatManager != null && combatManager.getAnimationManager() != null) {
            combatManager.getAnimationManager().spawnArchetypeCascade(impact3d, VisualArchetype.EXPLOSIVE_BURST, 40);
        }

        // 3. Lingering Environmental Scorch Marks
        if (maze != null && maze.getGoreManager() != null) {
            maze.getGoreManager().spawnElementalScorch(impact3d, archetype.getDecalColor(), 0.6f);
        }

        // 4. AoE 3x3 Damage
        int hits = applyAreaDamage(center, 1, spell, player, maze, combatManager, eventManager);
        eventManager.addEvent(new GameEvent("FIREBALL erupts into a roaring inferno! (" + hits + " hit)", 2.0f));
    }

    private static void resolveMagicMissileBespoke(SpellTemplate spell, VisualArchetype archetype, Player player, Maze maze,
                                                   GameEventManager eventManager, CombatManager combatManager, GameScreen gs) {
        int castRange = Math.max(3, spell.getRange());
        HitResult hit = (combatManager != null) ? combatManager.raycastProjectile(player.getPosition(), player.getFacing(), castRange, true) : null;

        Vector2 startPos = player.getPosition().cpy().add(player.getDirectionVector().cpy().scl(0.6f));
        Vector2 targetPos = (hit != null && hit.collisionPoint != null)
                ? new Vector2(hit.collisionPoint.x + 0.5f, hit.collisionPoint.y + 0.5f)
                : startPos.cpy().add(player.getDirectionVector().cpy().scl(castRange));

        // Spawn 3 staggered spiraling cyan darts
        if (combatManager != null && combatManager.getAnimationManager() != null) {
            for (int i = 0; i < 3; i++) {
                float angleOffset = (i - 1) * 0.15f;
                Vector2 dartStart = startPos.cpy().add(new Vector2(-player.getDirectionVector().y, player.getDirectionVector().x).scl(angleOffset));
                Color dartColor = (i % 2 == 0) ? archetype.getPrimaryColor() : archetype.getSecondaryColor();

                combatManager.getAnimationManager().addAnimation(new Animation(
                        Animation.AnimationType.PROJECTILE_SPELL,
                        dartStart, targetPos,
                        dartColor, 0.35f + (i * 0.10f),
                        new String[] { "o" }));
            }

            Vector3 target3d = new Vector3(targetPos.x, 0.5f, targetPos.y);
            combatManager.getAnimationManager().spawnArchetypeCascade(target3d, VisualArchetype.FORCE_MISSILE, 15);
        }

        // Hit resolution: deals 3 distinct dart strikes
        if (hit != null && hit.type == HitResult.HitType.MONSTER && hit.hitMonster != null) {
            Monster target = hit.hitMonster;
            int totalDmg = 0;
            for (int i = 0; i < 3; i++) {
                int dartDmg = DiceRoller.roll("1d4") + 1;
                int actual = target.takeDamage(dartDmg, DamageType.SPIRITUAL, false);
                totalDmg += actual;
            }
            if (combatManager != null) combatManager.showDamageText(totalDmg, hit.collisionPoint);
            eventManager.addEvent(new GameEvent("Magic Missiles strike " + target.getType() + " for " + totalDmg + " damage!", 1.8f));

            if (target.getCurrentHP() <= 0) {
                handleKill(target, hit.collisionPoint, player, maze, combatManager, eventManager);
            }
        } else {
            eventManager.addEvent(new GameEvent("Magic Missiles detonate against the corridor wall.", 1.2f));
        }
    }

    private static void resolveMistyStepBespoke(SpellTemplate spell, VisualArchetype archetype, Player player, Maze maze,
                                                GameEventManager eventManager, CombatManager combatManager, GameScreen gs) {
        // 1. Trigger Screen Glitch & Chromatic Aberration
        if (gs != null && gs.getSpellPostProcessor() != null) {
            gs.getSpellPostProcessor().triggerGlitch(0.9f, 0.40f);
            gs.getSpellPostProcessor().triggerChromaticAberration(0.85f, 0.45f);
        }

        // 2. Phase 3-4 tiles along facing corridor
        Direction facing = player.getFacing();
        int stepDist = 3;
        GridPoint2 dest = null;

        for (int d = stepDist; d >= 1; d--) {
            int tx = (int) player.getPosition().x + (int) facing.getVector().x * d;
            int ty = (int) player.getPosition().y + (int) facing.getVector().y * d;
            if (tx > 0 && tx < maze.getWidth() && ty > 0 && ty < maze.getHeight()
                    && !maze.isWall(tx, ty) && !maze.getMonsters().containsKey(new GridPoint2(tx, ty))) {
                dest = new GridPoint2(tx, ty);
                break;
            }
        }

        if (dest != null) {
            player.setPosition(dest);
            eventManager.addEvent(new GameEvent("You dissolve in mist and step across space!", 2.0f));

            if (combatManager != null && combatManager.getAnimationManager() != null) {
                Vector3 dest3d = new Vector3(dest.x + 0.5f, 0.5f, dest.y + 0.5f);
                combatManager.getAnimationManager().spawnArchetypeCascade(dest3d, VisualArchetype.SPATIAL_WARP, 25);
            }
        } else {
            eventManager.addEvent(new GameEvent("The space ahead is blocked!", 1.5f));
        }
    }

    private static void resolveThunderwaveBespoke(SpellTemplate spell, VisualArchetype archetype, Player player, Maze maze,
                                                  GameEventManager eventManager, CombatManager combatManager, GameScreen gs) {
        // 1. Strong Concussive Shockwave & Heavy Camera Shake
        if (gs != null) {
            if (gs.getSpellPostProcessor() != null) {
                gs.getSpellPostProcessor().triggerShockwave(0.5f, 0.55f, 0.70f, 0.15f);
            }
            gs.addTrauma(0.55f);
        }

        Direction facing = player.getFacing();
        int fx = (int) facing.getVector().x;
        int fy = (int) facing.getVector().y;

        Vector3 front3d = new Vector3(player.getPosition().x + fx * 1.2f, 0.5f, player.getPosition().y + fy * 1.2f);
        if (combatManager != null && combatManager.getAnimationManager() != null) {
            combatManager.getAnimationManager().spawnArchetypeCascade(front3d, VisualArchetype.THUNDER_CONCUSSION, 30);
        }

        // Crack decal on the floor
        if (maze != null && maze.getGoreManager() != null) {
            maze.getGoreManager().spawnElementalScorch(front3d, archetype.getDecalColor(), 0.5f);
        }

        // Affect target tile immediately in front
        GridPoint2 targetTile = new GridPoint2((int) player.getPosition().x + fx, (int) player.getPosition().y + fy);
        Monster target = maze.getMonsters().get(targetTile);

        if (target != null && target.getCurrentHP() > 0) {
            int dmg = DiceRoller.roll(spell.getDamageDice()) + player.getSpellPower();
            int actual = target.takeDamage(dmg, DamageType.SPIRITUAL, false);
            if (combatManager != null) combatManager.showDamageText(actual, targetTile);

            // Pushback mechanic: attempt to push monster back 1 tile
            GridPoint2 pushTile = new GridPoint2(targetTile.x + fx, targetTile.y + fy);
            if (!maze.isWall(pushTile.x, pushTile.y) && !maze.getMonsters().containsKey(pushTile)) {
                maze.getMonsters().remove(targetTile);
                maze.getMonsters().put(pushTile, target);
                eventManager.addEvent(new GameEvent("THUNDERWAVE blasts " + target.getType() + " back!", 1.8f));
            } else {
                eventManager.addEvent(new GameEvent("THUNDERWAVE slams " + target.getType() + " for " + actual + "!", 1.8f));
            }

            if (target.getCurrentHP() <= 0) {
                handleKill(target, targetTile, player, maze, combatManager, eventManager);
            }
        } else {
            eventManager.addEvent(new GameEvent("A deafening thunderclap rings through the hallway!", 1.5f));
        }
    }

    private static void resolveShieldBespoke(SpellTemplate spell, VisualArchetype archetype, Player player, Maze maze,
                                             GameEventManager eventManager, CombatManager combatManager, GameScreen gs) {
        // 1. Protective Cyan Vignette
        if (gs != null && gs.getSpellPostProcessor() != null) {
            gs.getSpellPostProcessor().triggerVignette(new Color(0.25f, 0.75f, 1.0f, 1.0f), 0.9f, 0.6f);
        }

        // 2. Shield Particle Flash around Player
        if (combatManager != null && combatManager.getAnimationManager() != null) {
            Vector3 player3d = new Vector3(player.getPosition().x, 0.5f, player.getPosition().y);
            combatManager.getAnimationManager().spawnArchetypeCascade(player3d, VisualArchetype.ARCANE_WARD, 20);
        }

        // 3. Apply Hardened AC Buff
        player.getStatusManager().addEffect(StatusEffectType.HARDENED, 18, 1, false);
        eventManager.addEvent(new GameEvent("A crystalline force barrier envelops you! (+AC)", 2.0f));
    }

    // =========================================================================
    // STANDARD ARCHETYPE HANDLERS
    // =========================================================================

    private static void resolveSelfSpell(SpellTemplate spell, VisualArchetype archetype, Player player, Maze maze,
                                         GameEventManager eventManager, GameScreen gs) {
        String name = spell.getName().toLowerCase();

        // Healing
        if (name.contains("cure") || name.contains("heal") || name.contains("aid") || name.contains("restoration")) {
            int amount = DiceRoller.roll(spell.getDamageDice());
            amount = Math.max(1, amount + player.getWisdomModifier());
            player.heal(amount);
            eventManager.addEvent(new GameEvent("Healed " + amount + " HP!", 2.0f));
            if (gs != null && gs.getSpellPostProcessor() != null) {
                gs.getSpellPostProcessor().triggerVignette(new Color(1f, 0.9f, 0.3f, 1f), 0.8f, 0.5f);
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

    private static void resolveProjectileSpell(SpellTemplate spell, VisualArchetype archetype, Player player, Maze maze,
                                               GameEventManager eventManager, CombatManager combatManager, GameScreen gs) {
        int maxRange = Math.max(2, spell.getRange());
        HitResult hit = (combatManager != null) ? combatManager.raycastProjectile(player.getPosition(), player.getFacing(), maxRange, true) : null;

        Vector2 startPos = player.getPosition().cpy().add(player.getDirectionVector().cpy().scl(0.6f));
        Vector2 targetPos = (hit != null && hit.collisionPoint != null)
                ? new Vector2(hit.collisionPoint.x + 0.5f, hit.collisionPoint.y + 0.5f)
                : startPos.cpy().add(player.getDirectionVector().cpy().scl(maxRange));

        // Ballistic projectile animation
        if (combatManager != null && combatManager.getAnimationManager() != null) {
            combatManager.getAnimationManager().addAnimation(new Animation(
                    Animation.AnimationType.PROJECTILE_SPELL,
                    startPos, targetPos,
                    archetype.getPrimaryColor(), 0.45f,
                    new String[] { archetype.getParticleAscii() }));

            Vector3 hit3d = new Vector3(targetPos.x, 0.5f, targetPos.y);
            combatManager.getAnimationManager().spawnArchetypeCascade(hit3d, archetype, 18);
        }

        // Environmental Decal on impact
        if (maze != null && maze.getGoreManager() != null) {
            Vector3 hit3d = new Vector3(targetPos.x, 0.05f, targetPos.y);
            maze.getGoreManager().spawnElementalScorch(hit3d, archetype.getDecalColor(), 0.35f);
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
                handleKill(target, hit.collisionPoint, player, maze, combatManager, eventManager);
            }
        } else {
            eventManager.addEvent(new GameEvent(spell.getName() + " impacts the corridor wall.", 1.0f));
        }
    }

    private static void resolveBurstSpell(SpellTemplate spell, VisualArchetype archetype, Player player, Maze maze,
                                          GameEventManager eventManager, CombatManager combatManager, GameScreen gs) {
        int castRange = Math.min(8, spell.getRange());
        HitResult hit = (combatManager != null) ? combatManager.raycastProjectile(player.getPosition(), player.getFacing(), castRange, true) : null;

        GridPoint2 center = (hit != null && hit.collisionPoint != null)
                ? hit.collisionPoint
                : new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);

        Vector3 center3d = new Vector3(center.x + 0.5f, 0.5f, center.y + 0.5f);
        if (combatManager != null && combatManager.getAnimationManager() != null) {
            combatManager.getAnimationManager().spawnArchetypeCascade(center3d, archetype, 25);
        }

        if (maze != null && maze.getGoreManager() != null) {
            maze.getGoreManager().spawnElementalScorch(center3d, archetype.getDecalColor(), 0.45f);
        }

        int hits = applyAreaDamage(center, 2, spell, player, maze, combatManager, eventManager);
        eventManager.addEvent(new GameEvent("BURST! " + spell.getName() + " strikes " + hits + " targets!", 1.5f));
    }

    private static void resolveTouchSpell(SpellTemplate spell, VisualArchetype archetype, Player player, Maze maze,
                                          GameEventManager eventManager, CombatManager combatManager, GameScreen gs) {
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
                handleKill(target, targetPos, player, maze, combatManager, eventManager);
            }
        } else {
            eventManager.addEvent(new GameEvent("You reach out with " + spell.getName() + ", but find only empty air.", 1.0f));
        }
    }

    private static int applyAreaDamage(GridPoint2 center, int radius, SpellTemplate spell, Player player,
                                       Maze maze, CombatManager combatManager, GameEventManager eventManager) {
        List<GridPoint2> monsterPositions = new ArrayList<>(maze.getMonsters().keySet());
        int hits = 0;

        for (GridPoint2 pos : monsterPositions) {
            if (Math.abs(pos.x - center.x) <= radius && Math.abs(pos.y - center.y) <= radius) {
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
                    handleKill(target, pos, player, maze, combatManager, eventManager);
                }
            }
        }
        return hits;
    }

    private static void handleKill(Monster target, GridPoint2 pos, Player player, Maze maze,
                                   CombatManager combatManager, GameEventManager eventManager) {
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
        eventManager.addEvent(new GameEvent("Vanquished " + target.getType() + "!", 2.0f));
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
