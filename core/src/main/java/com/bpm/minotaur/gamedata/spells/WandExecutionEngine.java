package com.bpm.minotaur.gamedata.spells;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.WandEffectType;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.lighting.LightSource;
import com.bpm.minotaur.lighting.LightingManager;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.CombatManager.HitResult;
import com.bpm.minotaur.managers.DiscoveryManager;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.rendering.Animation;
import com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType;
import com.bpm.minotaur.screens.GameScreen;
import com.bpm.minotaur.utils.DiceRoller;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized execution and animation engine for wands.
 * Orchestrates:
 * - First-person hand raising with school-specific glowing palm runes and weapon overlay thrust
 * - Domain-specific synthesized audio in SoundManager
 * - Screen-space shader post-processing (shockwaves, chromatic aberration, vignettes, glitch)
 * - 3D hallway particle cascades, projectile ballistics, and billboard explosions
 * - Environmental scorch decals and wall destruction for digging
 * - Monster damage, status afflictions, teleportation rifts, and illumination
 */
public class WandExecutionEngine {

    public static boolean zapWand(Item wand, Player player, Direction dir, Maze maze,
                                  DiscoveryManager discoveryManager, GameEventManager eventManager,
                                  CombatManager combatManager) {
        if (wand == null) {
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("Zap what?", 1.0f));
            }
            return false;
        }

        if (wand.getCharges() <= 0) {
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("Nothing happens. The wand is depleted.", 1.5f));
            }
            return false;
        }

        if (player == null || maze == null) {
            return false;
        }

        // Decrement charge
        wand.decrementCharges();

        if (dir == null) {
            dir = player.getFacing();
        }

        WandEffectType effect = wand.getWandEffect();
        if (effect == null) {
            if (discoveryManager != null) {
                effect = discoveryManager.getWandEffect(wand.getType());
            }
            if (effect == null) {
                effect = WandEffectType.MAGIC_MISSILE;
            }
            wand.setWandEffect(effect);
        }

        GameScreen gs = (combatManager != null) ? combatManager.getGameScreen() : null;

        // Visual Archetype & Configuration
        VisualArchetype archetype = getArchetypeForWand(effect);
        ExplosionType explosionType = getExplosionForWand(effect);
        String runeSchool = getSchoolForWand(effect);
        Color spellColor = archetype.getPrimaryColor();

        // 1. First-Person Hand-Raise / Wand Thrust Animation
        if (gs != null) {
            if (gs.getSpellCastOverlay() != null) {
                gs.getSpellCastOverlay().triggerCast(0.65f, null, runeSchool, spellColor);
            }
            if (gs.getWeaponOverlay() != null && player.getInventory() != null
                    && player.getInventory().getRightHand() == wand) {
                gs.getWeaponOverlay().triggerAttack(wand);
            }
        }

        // 2. Synthesized & Sampled Spell Audio
        if (combatManager != null && combatManager.getSoundManager() != null) {
            combatManager.getSoundManager().playSpellSound(archetype);
        }

        // 3. Screen-Space Post-Processing Shader FX
        triggerShaderFX(gs, effect, archetype);

        // Zap message
        String zapMsg = effect.getZapMessage();
        if (zapMsg != null && eventManager != null) {
            eventManager.addEvent(new GameEvent(zapMsg, 1.8f));
        }

        // 4. Raycast in facing direction
        int castRange = getRangeForWand(effect);
        HitResult hit = raycast(player, dir, castRange, maze, combatManager);

        Vector2 startPos = player.getPosition().cpy().add(player.getDirectionVector().cpy().scl(0.6f));
        Vector2 targetPos = (hit != null && hit.collisionPoint != null)
                ? new Vector2(hit.collisionPoint.x + 0.5f, hit.collisionPoint.y + 0.5f)
                : startPos.cpy().add(player.getDirectionVector().cpy().scl(castRange));

        // 5. 3D Projectile Ballistics & Impact Explosions
        spawnProjectilesAndVFX(effect, archetype, explosionType, startPos, targetPos, player, combatManager, maze);

        // 6. Resolve Specific Wand Mechanics (Damage, Teleport, Light, Digging)
        resolveWandEffect(effect, hit, targetPos, player, dir, maze, discoveryManager, eventManager, combatManager, gs);

        // 7. Auto-identification
        if (discoveryManager != null && !discoveryManager.isWandIdentified(effect)) {
            discoveryManager.identifyWand(player, effect);
            wand.setIdentified(true);
            wand.setName("Wand of " + effect.getBaseName());
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("You discovered it was a Wand of " + effect.getBaseName() + "!", 2.5f));
            }
        }

        return true;
    }

    public static VisualArchetype getArchetypeForWand(WandEffectType effect) {
        if (effect == null) return VisualArchetype.FORCE_MISSILE;
        switch (effect) {
            case FIRE:
                return VisualArchetype.FLAME_BOLT;
            case COLD:
                return VisualArchetype.FROST_RAY;
            case MAGIC_MISSILE:
                return VisualArchetype.FORCE_MISSILE;
            case DIGGING:
                return VisualArchetype.THUNDER_CONCUSSION;
            case LIGHT:
                return VisualArchetype.HOLY_RADIANCE;
            case TELEPORTATION:
                return VisualArchetype.SPATIAL_WARP;
            default:
                return VisualArchetype.FORCE_MISSILE;
        }
    }

    public static ExplosionType getExplosionForWand(WandEffectType effect) {
        if (effect == null) return ExplosionType.STANDARD;
        switch (effect) {
            case FIRE:
                return ExplosionType.FIRE;
            case COLD:
                return ExplosionType.ICE;
            case MAGIC_MISSILE:
                return ExplosionType.CONCUSSIVE;
            case DIGGING:
                return ExplosionType.CONCUSSIVE;
            case LIGHT:
                return ExplosionType.HOLY_CROSS;
            case TELEPORTATION:
                return ExplosionType.VOID;
            default:
                return ExplosionType.STANDARD;
        }
    }

    public static String getSchoolForWand(WandEffectType effect) {
        if (effect == null) return "EVOCATION";
        switch (effect) {
            case FIRE:
            case COLD:
            case MAGIC_MISSILE:
                return "EVOCATION";
            case DIGGING:
                return "TRANSMUTATION";
            case LIGHT:
                return "DIVINATION";
            case TELEPORTATION:
                return "CONJURATION";
            default:
                return "EVOCATION";
        }
    }

    public static int getRangeForWand(WandEffectType effect) {
        if (effect == null) return 8;
        switch (effect) {
            case MAGIC_MISSILE:
            case LIGHT:
                return 10;
            case DIGGING:
                return 6;
            case FIRE:
            case COLD:
            case TELEPORTATION:
            default:
                return 8;
        }
    }

    private static void triggerShaderFX(GameScreen gs, WandEffectType effect, VisualArchetype archetype) {
        if (gs == null || gs.getSpellPostProcessor() == null) return;

        switch (effect) {
            case FIRE:
                gs.getSpellPostProcessor().triggerArchetypeFX(VisualArchetype.FLAME_BOLT, 0.5f, 0.5f);
                break;
            case COLD:
                gs.getSpellPostProcessor().triggerArchetypeFX(VisualArchetype.FROST_RAY, 0.5f, 0.5f);
                break;
            case MAGIC_MISSILE:
                gs.getSpellPostProcessor().triggerArchetypeFX(VisualArchetype.FORCE_MISSILE, 0.5f, 0.5f);
                break;
            case DIGGING:
                gs.getSpellPostProcessor().triggerShockwave(0.5f, 0.55f, 0.65f, 0.12f);
                gs.addTrauma(0.40f);
                break;
            case LIGHT:
                gs.getSpellPostProcessor().triggerVignette(new Color(1f, 0.95f, 0.6f, 1f), 0.9f, 0.7f);
                break;
            case TELEPORTATION:
                gs.getSpellPostProcessor().triggerGlitch(0.85f, 0.40f);
                gs.getSpellPostProcessor().triggerChromaticAberration(0.80f, 0.45f);
                break;
        }
    }

    private static HitResult raycast(Player player, Direction dir, int castRange, Maze maze, CombatManager combatManager) {
        if (combatManager != null) {
            return combatManager.raycastProjectile(player.getPosition(), dir, castRange, true, true);
        }

        // Headless / fallback raycast
        int startX = (int) player.getPosition().x;
        int startY = (int) player.getPosition().y;
        int dx = (int) dir.getVector().x;
        int dy = (int) dir.getVector().y;
        int cx = startX;
        int cy = startY;

        for (int i = 0; i < castRange; i++) {
            if (maze.isWallBlocking(cx, cy, dir)) {
                return new HitResult(new GridPoint2(cx, cy), HitResult.HitType.WALL, null);
            }
            cx += dx;
            cy += dy;
            GridPoint2 cPos = new GridPoint2(cx, cy);
            if (cx < 0 || cx >= maze.getWidth() || cy < 0 || cy >= maze.getHeight()) {
                return new HitResult(cPos, HitResult.HitType.OUT_OF_BOUNDS, null);
            }
            if (maze.getMonsters().containsKey(cPos)) {
                return new HitResult(cPos, HitResult.HitType.MONSTER, maze.getMonsters().get(cPos));
            }
            if (maze.isWall(cx, cy)) {
                return new HitResult(cPos, HitResult.HitType.WALL, null);
            }
        }
        return new HitResult(new GridPoint2(cx, cy), HitResult.HitType.NOTHING, null);
    }

    private static void spawnProjectilesAndVFX(WandEffectType effect, VisualArchetype archetype,
                                               ExplosionType explosionType, Vector2 startPos, Vector2 targetPos,
                                               Player player, CombatManager combatManager, Maze maze) {
        if (combatManager == null || combatManager.getAnimationManager() == null) return;

        if (effect == WandEffectType.MAGIC_MISSILE) {
            // Spawn 3 staggered spiraling cyan darts
            for (int i = 0; i < 3; i++) {
                float angleOffset = (i - 1) * 0.15f;
                Vector2 dartStart = startPos.cpy().add(
                        new Vector2(-player.getDirectionVector().y, player.getDirectionVector().x).scl(angleOffset));
                Color dartColor = (i % 2 == 0) ? archetype.getPrimaryColor() : archetype.getSecondaryColor();

                combatManager.getAnimationManager().addAnimation(new Animation(
                        Animation.AnimationType.PROJECTILE_SPELL,
                        dartStart, targetPos,
                        dartColor, 0.30f + (i * 0.08f),
                        new String[] { "o" }));
            }
            Vector3 target3d = new Vector3(targetPos.x, 0.5f, targetPos.y);
            combatManager.getAnimationManager().spawnExplosion(explosionType, target3d, 1.3f, 0.45f);
            combatManager.getAnimationManager().spawnArchetypeCascade(target3d, VisualArchetype.FORCE_MISSILE, 20);
        } else {
            // Standard single projectile spell animation
            combatManager.getAnimationManager().addAnimation(new Animation(
                    Animation.AnimationType.PROJECTILE_SPELL,
                    startPos, targetPos,
                    archetype.getPrimaryColor(), 0.35f,
                    new String[] { archetype.getParticleAscii() }));

            Vector3 hit3d = new Vector3(targetPos.x, 0.5f, targetPos.y);
            combatManager.getAnimationManager().spawnExplosion(explosionType, hit3d, 1.5f, 0.55f);
            combatManager.getAnimationManager().spawnArchetypeCascade(hit3d, archetype, 22);
        }

        // Elemental floor decal on impact
        if (maze != null && maze.getGoreManager() != null) {
            Vector3 decal3d = new Vector3(targetPos.x, 0.05f, targetPos.y);
            maze.getGoreManager().spawnElementalScorch(decal3d, archetype.getDecalColor(), 0.45f);
        }
    }

    private static void resolveWandEffect(WandEffectType effect, HitResult hit, Vector2 targetPos,
                                          Player player, Direction dir, Maze maze,
                                          DiscoveryManager discoveryManager, GameEventManager eventManager,
                                          CombatManager combatManager, GameScreen gs) {
        boolean hitMonster = (hit != null && hit.type == HitResult.HitType.MONSTER && hit.hitMonster != null);
        Monster target = hitMonster ? hit.hitMonster : null;

        switch (effect) {
            case FIRE:
                if (target != null) {
                    if (Monster.isImmuneToType(target.getType(), DamageType.SPIRITUAL)) {
                        if (eventManager != null) eventManager.addEvent(new GameEvent(target.getType() + " is immune to fire magic!", 1.5f));
                        if (combatManager != null) combatManager.showDamageText(0, hit.collisionPoint);
                        return;
                    }
                    int dmg = DiceRoller.roll("3d6") + 2;
                    int actual = target.takeDamage(dmg, DamageType.SPIRITUAL, false);
                    if (combatManager != null) combatManager.showDamageText(actual, hit.collisionPoint, "", Color.ORANGE);
                    if (eventManager != null) eventManager.addEvent(new GameEvent("The roaring flame engulfs " + target.getType() + " for " + actual + " damage!", 1.8f));
                    if (target.getCurrentHP() <= 0) {
                        handleKill(target, hit.collisionPoint, player, maze, combatManager, eventManager);
                    } else if (combatManager != null && combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                        combatManager.startCombat(target);
                    }
                } else {
                    if (eventManager != null) eventManager.addEvent(new GameEvent("A blast of fire erupts and scorches the stones.", 1.2f));
                }
                break;

            case COLD:
                if (target != null) {
                    if (Monster.isImmuneToType(target.getType(), DamageType.SPIRITUAL)) {
                        if (eventManager != null) eventManager.addEvent(new GameEvent(target.getType() + " is immune to frost magic!", 1.5f));
                        if (combatManager != null) combatManager.showDamageText(0, hit.collisionPoint);
                        return;
                    }
                    int dmg = DiceRoller.roll("2d8") + 2;
                    int actual = target.takeDamage(dmg, DamageType.SPIRITUAL, false);
                    target.getStatusManager().addEffect(StatusEffectType.FREEZING, 5, 1, false);
                    if (combatManager != null) combatManager.showDamageText(actual, hit.collisionPoint, "", Color.CYAN);
                    if (eventManager != null) eventManager.addEvent(new GameEvent("The freezing beam crystallizes " + target.getType() + " for " + actual + " damage!", 1.8f));
                    if (target.getCurrentHP() <= 0) {
                        handleKill(target, hit.collisionPoint, player, maze, combatManager, eventManager);
                    } else if (combatManager != null && combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                        combatManager.startCombat(target);
                    }
                } else {
                    if (eventManager != null) eventManager.addEvent(new GameEvent("The freezing beam coats the corridor in rime and ice.", 1.2f));
                }
                break;

            case MAGIC_MISSILE:
                if (target != null) {
                    int totalDmg = 0;
                    for (int i = 0; i < 3; i++) {
                        int dartDmg = DiceRoller.roll("1d4") + 1;
                        totalDmg += target.takeDamage(dartDmg, DamageType.SPIRITUAL, false);
                    }
                    if (combatManager != null) combatManager.showDamageText(totalDmg, hit.collisionPoint, "", Color.CYAN);
                    if (eventManager != null) eventManager.addEvent(new GameEvent("Glowing arcane missiles strike " + target.getType() + " for " + totalDmg + " damage!", 1.8f));
                    if (target.getCurrentHP() <= 0) {
                        handleKill(target, hit.collisionPoint, player, maze, combatManager, eventManager);
                    } else if (combatManager != null && combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                        combatManager.startCombat(target);
                    }
                } else {
                    if (eventManager != null) eventManager.addEvent(new GameEvent("Magic missiles streak down the hall and detonate against stone.", 1.2f));
                }
                break;

            case DIGGING:
                if (target != null) {
                    int dmg = DiceRoller.roll("3d6");
                    int actual = target.takeDamage(dmg, DamageType.PHYSICAL, false);
                    if (combatManager != null) combatManager.showDamageText(actual, hit.collisionPoint, "", Color.valueOf("E67E22"));
                    if (eventManager != null) eventManager.addEvent(new GameEvent("The concussive beam blasts " + target.getType() + " for " + actual + " damage!", 1.8f));

                    // Pushback: push monster back 1 tile along beam direction if open
                    int pushX = hit.collisionPoint.x + (int) dir.getVector().x;
                    int pushY = hit.collisionPoint.y + (int) dir.getVector().y;
                    GridPoint2 pushTile = new GridPoint2(pushX, pushY);
                    if (!maze.isWall(pushX, pushY) && !maze.getMonsters().containsKey(pushTile)) {
                        maze.getMonsters().remove(hit.collisionPoint);
                        maze.getMonsters().put(pushTile, target);
                    }

                    if (target.getCurrentHP() <= 0) {
                        handleKill(target, hit.collisionPoint, player, maze, combatManager, eventManager);
                    } else if (combatManager != null && combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                        combatManager.startCombat(target);
                    }
                } else if (hit != null && hit.type == HitResult.HitType.WALL && hit.collisionPoint != null) {
                    int wx = hit.collisionPoint.x;
                    int wy = hit.collisionPoint.y;
                    // Do not break the outer edge of the map
                    if (wx > 0 && wx < maze.getWidth() - 1 && wy > 0 && wy < maze.getHeight() - 1) {
                        maze.setTile(wx, wy, 0); // 0 is floor
                        if (maze.getScenery() != null) {
                            maze.getScenery().remove(hit.collisionPoint);
                        }
                        if (gs != null) {
                            gs.invalidateMeshCache();
                        }
                        if (eventManager != null) {
                            eventManager.addEvent(new GameEvent("The stone wall shatters into rubble!", 2.0f));
                        }
                    } else {
                        if (eventManager != null) {
                            eventManager.addEvent(new GameEvent("The beam strikes impenetrable bedrock!", 1.5f));
                        }
                    }
                } else {
                    if (eventManager != null) {
                        eventManager.addEvent(new GameEvent("The digging beam drills through the air and dissipates.", 1.2f));
                    }
                }
                break;

            case LIGHT:
                GridPoint2 lightPos = (hit != null && hit.collisionPoint != null)
                        ? hit.collisionPoint
                        : new GridPoint2((int) targetPos.x, (int) targetPos.y);

                // Add lingering point light source in corridor
                maze.addLight(new LightSource("wand_light_" + lightPos.x + "_" + lightPos.y,
                        targetPos.x, targetPos.y,
                        LightingManager.COLOR_TORCH, 7.0f, 1.4f,
                        LightSource.FlickerProfile.STEADY));

                // Grant Mote of Light to player
                player.getStatusManager().addEffect(StatusEffectType.MOTE_OF_LIGHT, 100, 1, false);

                if (target != null) {
                    boolean isUndeadOrDark = isUndeadOrDemon(target);
                    int dmg = isUndeadOrDark ? DiceRoller.roll("3d8") : DiceRoller.roll("1d6");
                    int actual = target.takeDamage(dmg, DamageType.SPIRITUAL, false);
                    if (combatManager != null) combatManager.showDamageText(actual, hit.collisionPoint, "", Color.YELLOW);
                    if (eventManager != null) {
                        String desc = isUndeadOrDark
                                ? "The searing sunlance incinerates " + target.getType() + " for " + actual + " radiant damage!"
                                : "The lance of light burns " + target.getType() + " for " + actual + " damage!";
                        eventManager.addEvent(new GameEvent(desc, 1.8f));
                    }
                    if (target.getCurrentHP() <= 0) {
                        handleKill(target, hit.collisionPoint, player, maze, combatManager, eventManager);
                    } else if (combatManager != null && combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                        combatManager.startCombat(target);
                    }
                } else {
                    if (eventManager != null) {
                        eventManager.addEvent(new GameEvent("A brilliant sunlance illuminates the dungeon hallway!", 2.0f));
                    }
                }
                break;

            case TELEPORTATION:
                if (target != null) {
                    GridPoint2 startTile = hit.collisionPoint;
                    GridPoint2 destTile = findRandomFloorTile(maze, startTile);

                    if (destTile != null) {
                        if (combatManager != null && combatManager.getAnimationManager() != null) {
                            Vector3 arrive3d = new Vector3(destTile.x + 0.5f, 0.5f, destTile.y + 0.5f);
                            combatManager.getAnimationManager().spawnExplosion(ExplosionType.WIND, arrive3d, 1.5f, 0.50f);
                        }

                        maze.getMonsters().remove(startTile);
                        target.getPosition().set(destTile.x + 0.5f, destTile.y + 0.5f);
                        maze.getMonsters().put(destTile, target);

                        if (combatManager != null && combatManager.getMonster() == target) {
                            combatManager.endCombat();
                        }

                        if (eventManager != null) {
                            eventManager.addEvent(new GameEvent(target.getType() + " vanishes into an astral rift!", 2.0f));
                        }
                    } else {
                        if (eventManager != null) {
                            eventManager.addEvent(new GameEvent("The teleportation energies destabilize and collapse.", 1.5f));
                        }
                    }
                } else {
                    if (eventManager != null) {
                        eventManager.addEvent(new GameEvent("A chaotic spatial tear ripples across the hallway.", 1.2f));
                    }
                }
                break;
        }
    }

    private static boolean isUndeadOrDemon(Monster monster) {
        if (monster == null) return false;
        String typeName = monster.getMonsterType().toUpperCase();
        return typeName.contains("SKELETON") || typeName.contains("ZOMBIE") || typeName.contains("GHOUL")
                || typeName.contains("WRAITH") || typeName.contains("VAMPIRE") || typeName.contains("SHADOW")
                || typeName.contains("LICH") || typeName.contains("DEMON") || typeName.contains("SPECTRE");
    }

    private static GridPoint2 findRandomFloorTile(Maze maze, GridPoint2 exclude) {
        if (maze == null) return null;
        for (int tries = 0; tries < 40; tries++) {
            int tx = (int) (Math.random() * maze.getWidth());
            int ty = (int) (Math.random() * maze.getHeight());
            if (tx <= 0 || tx >= maze.getWidth() - 1 || ty <= 0 || ty >= maze.getHeight() - 1) continue;
            GridPoint2 cand = new GridPoint2(tx, ty);
            if (!cand.equals(exclude) && maze.isPassable(tx, ty)
                    && !maze.isWall(tx, ty)
                    && !maze.getMonsters().containsKey(cand)) {
                return cand;
            }
        }
        return null;
    }

    private static void handleKill(Monster target, GridPoint2 targetPos, Player player, Maze maze,
                                   CombatManager combatManager, GameEventManager eventManager) {
        if (combatManager != null) {
            combatManager.handleRemoteKill(target);
        } else {
            maze.getMonsters().remove(targetPos);
            player.getStats().addExperience(target.getBaseExperience());
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("Killed " + target.getMonsterType() + "!", 2f));
            }
        }
    }
}
