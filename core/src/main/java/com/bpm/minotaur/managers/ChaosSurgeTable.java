package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.polymorph.PolymorphEngine;
import com.bpm.minotaur.utils.DiceRoller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The 20-event wild magic surge catalog for The Laughing Void.
 * Symmetrical, cosmic-scale events affecting player and monsters equally.
 */
public final class ChaosSurgeTable {

    private static final Random RNG = new Random();
    private static final Map<String, ChaosSurgeEvent> BY_ID = new LinkedHashMap<>();
    private static final List<ChaosSurgeEvent> ALL_EVENTS = new ArrayList<>();

    private static void register(ChaosSurgeEvent event) {
        BY_ID.put(event.getId(), event);
        ALL_EVENTS.add(event);
    }

    static {
        // =====================================================================
        // Category 1: Atmospheric / Weather
        // =====================================================================

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "acidic_stardust"; }
            @Override public String getName() { return "Acidic Stardust"; }
            @Override public String getDescription() {
                return "Corrosive neon stardust rains from the heavens. Exposed entities suffer 1-2 acid damage each turn.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("Corrosive neon stardust rains down, sizzling upon living flesh!", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {
                if (context == null) return;
                int dmg = DiceRoller.roll("1d2");
                if (dmg <= 0) dmg = 1;

                if (context.player != null) {
                    context.player.takeDamage(dmg, DamageType.POISON);
                    if (context.eventManager != null) {
                        context.eventManager.addEvent(new GameEvent("Acidic stardust burns you for " + dmg + " acid damage!", 1.5f));
                    }
                }
                if (context.maze != null) {
                    for (Monster m : context.maze.getMonsters().values()) {
                        if (m != null && m.isAlive()) {
                            m.takeDamage(dmg, DamageType.POISON);
                        }
                    }
                }
            }
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The acidic stardust melts away into harmless mist.", 2.5f));
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "dense_neon_fog"; }
            @Override public String getName() { return "Dense Neon Fog"; }
            @Override public String getDescription() {
                return "A vivid neon fog blankets reality, restricting sight and missile distance to 2 paces.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("Dense neon fog blankets reality! Vision and ranged combat capped at 2 paces!", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {}
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The neon fog rolls away, restoring distant sight.", 2.5f));
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "zephyr_gale"; }
            @Override public String getName() { return "Zephyr Gale"; }
            @Override public String getDescription() {
                return "Erratic crosswinds whip through corridors, blowing missiles and combatants off balance.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("Whistling zephyr gales whip through the corridors!", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {
                if (context == null || context.maze == null) return;
                if (RNG.nextFloat() < 0.20f && context.player != null) {
                    Direction dir = Direction.values()[RNG.nextInt(Direction.values().length)];
                    int nx = (int) context.player.getPosition().x + (int) dir.getVector().x;
                    int ny = (int) context.player.getPosition().y + (int) dir.getVector().y;
                    if (context.maze.isPassable(nx, ny) && context.maze.getMonsters().get(new GridPoint2(nx, ny)) == null) {
                        context.player.getPosition().set(nx + 0.5f, ny + 0.5f);
                        if (context.eventManager != null) {
                            context.eventManager.addEvent(new GameEvent("A wild gust of wind shoves you " + dir.name() + "!", 1.5f));
                        }
                    }
                }
            }
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The zephyr gales subside into a quiet breeze.", 2.5f));
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "ash_of_madness"; }
            @Override public String getName() { return "Ash of Madness"; }
            @Override public String getDescription() {
                return "Lilac ash drifts through the air. Any melee strike has a 25% chance to confuse the victim.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("Lilac ash of madness drifts softly through the air...", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {
                if (context == null) return;
                if (RNG.nextFloat() < 0.12f && context.player != null && context.player.getStatusManager() != null) {
                    context.player.getStatusManager().addEffect(StatusEffectType.CONFUSION, 2, 1, false);
                    if (context.eventManager != null) {
                        context.eventManager.addEvent(new GameEvent("The lilac ash fills your head with buzzing nonsense!", 1.5f));
                    }
                }
            }
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The lilac ash dissolves; minds clear.", 2.5f));
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "radiant_aurora"; }
            @Override public String getName() { return "Radiant Aurora"; }
            @Override public String getDescription() {
                return "A shimmering cyan aurora floods the firmament, mending all living flesh 1 HP every turn.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("A pulsing cyan aurora floods the heavens! All flesh begins to mend.", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {
                if (context == null) return;
                if (context.player != null && context.player.getStats() != null) {
                    context.player.getStats().heal(1);
                }
                if (context.maze != null) {
                    for (Monster m : context.maze.getMonsters().values()) {
                        if (m != null && m.isAlive()) {
                            m.setCurrentHP(Math.min(m.getMaxHP(), m.getCurrentHP() + 1));
                        }
                    }
                }
            }
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The radiant aurora dims into quiet twilight.", 2.5f));
                }
            }
        });

        // =====================================================================
        // Category 2: Combat Inversions
        // =====================================================================

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "inverted_duality"; }
            @Override public String getName() { return "Inverted Duality"; }
            @Override public String getDescription() {
                return "The physical and spiritual planes swap places. Physical strikes target the soul, spells smash flesh.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("INVERTED DUALITY! Physical and Spiritual damage trade places!", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {}
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("Physical and Spiritual planes settle back into their original alignment.", 2.5f));
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "mirror_blood"; }
            @Override public String getName() { return "Mirror Blood"; }
            @Override public String getDescription() {
                return "A lattice of crimson light binds all living flesh. 50% of all damage dealt recoils onto the attacker.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("MIRROR BLOOD! Crimson threads bind all souls -- attacks recoil 50% damage!", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {}
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The crimson lattice snaps; wounds no longer mirror onto their dealer.", 2.5f));
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "glass_cannons"; }
            @Override public String getName() { return "Glass Cannons"; }
            @Override public String getDescription() {
                return "All souls crystallize into brittle glass. All damage dealt is quadrupled (400%), and all damage taken is quadrupled!";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("GLASS CANNONS! All bodies crystallize -- 400% damage dealt AND taken!", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {}
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The brittle crystalline glaze melts away. Resilience returns.", 2.5f));
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "jesters_farce"; }
            @Override public String getName() { return "Jester's Farce"; }
            @Override public String getDescription() {
                return "Vitality inverts into farce: healing effects deal damage instead, while attacks occasionally mend the victim.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("JESTER'S FARCE! Healing deals damage, and cruelty becomes kindness!", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {}
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The farce concludes; healing restores life once more.", 2.5f));
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "pacifism_wave"; }
            @Override public String getName() { return "Pacifism Wave"; }
            @Override public String getDescription() {
                return "A profound lethargy dulls all wrath: damage is cut by 75%, but movement speed is doubled.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("PACIFISM WAVE! All damage is reduced by 75%!", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {}
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The pacifying haze dissolves. Lethality returns.", 2.5f));
                }
            }
        });

        // =====================================================================
        // Category 3: Spawns & Transmutations
        // =====================================================================

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "clown_court"; }
            @Override public String getName() { return "Clown Court"; }
            @Override public String getDescription() {
                return "The Laughing Void's court materializes! Ethereal tricksters dance and mock all combatants.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("THE CLOWN COURT! Ethereal phantoms dance and giggle in the void!", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {
                if (context == null || context.eventManager == null) return;
                if (RNG.nextFloat() < 0.25f) {
                    String[] pranks = {
                        "A spectral clown juggles incandescent embers before dissolving!",
                        "An ethereal jester bows mockingly at your struggle!",
                        "Hollow laughter echoes through the cold stone corridors!",
                        "A phantom trickster rings a silent brass bell!"
                    };
                    context.eventManager.addEvent(new GameEvent(pranks[RNG.nextInt(pranks.length)], 1.5f));
                }
            }
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The spectral tricksters bow in unison and vanish into thin air.", 2.5f));
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "item_transmutation"; }
            @Override public String getName() { return "Item Transmutation"; }
            @Override public String getDescription() {
                return "Physical matter destabilizes: floor items randomly mutate into other forms.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("ITEM TRANSMUTATION! Floor items soften and shift form!", 3.0f));
                }
                mutateFloorItem(context);
            }
            @Override public void onTurnTick(ChaosEventContext context) {
                if (RNG.nextFloat() < 0.20f) {
                    mutateFloorItem(context);
                }
            }
            private void mutateFloorItem(ChaosEventContext context) {
                if (context == null || context.maze == null || context.player == null) return;
                if (context.maze.getItems().isEmpty()) return;
                List<GridPoint2> tiles = new ArrayList<>(context.maze.getItems().keySet());
                GridPoint2 targetTile = tiles.get(RNG.nextInt(tiles.size()));
                PolymorphEngine.polymorphItemAt(context.maze, targetTile, context.player, context.eventManager);
            }
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("Molecular stability reasserts itself; items solidify.", 2.5f));
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "mass_polymorph"; }
            @Override public String getName() { return "Mass Polymorph"; }
            @Override public String getDescription() {
                return "A wave of protean mutability washes over the strata, transforming creatures into kindred beasts.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("MASS POLYMORPH! Reality bends as dungeon denizens mutate!", 3.0f));
                }
                polymorphRandomMonster(context);
            }
            @Override public void onTurnTick(ChaosEventContext context) {
                if (RNG.nextFloat() < 0.15f) {
                    polymorphRandomMonster(context);
                }
            }
            private void polymorphRandomMonster(ChaosEventContext context) {
                if (context == null || context.maze == null || context.player == null) return;
                List<Monster> candidates = new ArrayList<>();
                for (Monster m : context.maze.getMonsters().values()) {
                    if (m != null && m.isAlive() && !m.isBridgeBoss()) {
                        candidates.add(m);
                    }
                }
                if (!candidates.isEmpty()) {
                    Monster chosen = candidates.get(RNG.nextInt(candidates.size()));
                    PolymorphEngine.polymorphMonster(chosen, context.maze, context.player, null, context.eventManager, false);
                }
            }
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The mutable tide recedes. Forms harden into permanence.", 2.5f));
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "animate_ground"; }
            @Override public String getName() { return "Animate Ground"; }
            @Override public String getDescription() {
                return "The stone foundation churns with chaotic life. Rhythmic tremors shake all combatants.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("ANIMATE GROUND! The dungeon floor ripples like liquid stone!", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {
                if (context == null || context.eventManager == null) return;
                if (RNG.nextFloat() < 0.20f) {
                    context.eventManager.addEvent(new GameEvent("The earth groans and buckles beneath your boots!", 1.5f));
                }
            }
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The churning earth settles into cold stone.", 2.5f));
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "soul_bloom"; }
            @Override public String getName() { return "Soul Bloom"; }
            @Override public String getDescription() {
                return "Slain souls release vibrant spiritual blossoms that pulse with restorative ether.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("SOUL BLOOM! Ethereal blossoms sprout across the ley-lines!", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {}
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The radiant soul blossoms dissolve into spiritual dust.", 2.5f));
                }
            }
        });

        // =====================================================================
        // Category 4: Bizarre Rules
        // =====================================================================

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "slipstream"; }
            @Override public String getName() { return "Slipstream"; }
            @Override public String getDescription() {
                return "Frictional physics vanish! Moving forward slides effortlessly across smooth stone.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("SLIPSTREAM! Friction vanishes into a frictionless cosmic glaze!", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {}
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("Frictional drag returns to the world.", 2.5f));
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "time_dilation"; }
            @Override public String getName() { return "Time Dilation"; }
            @Override public String getDescription() {
                return "Chronological flow wobbles wildly: entities occasionally experience temporal accelerations.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("TIME DILATION! The ticking of eternity stutters and surges!", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {
                if (context == null || context.player == null) return;
                if (RNG.nextFloat() < 0.20f && context.player.getStatusManager() != null) {
                    context.player.getStatusManager().addEffect(StatusEffectType.SUPER_SPEED, 2, 1, false);
                    if (context.eventManager != null) {
                        context.eventManager.addEvent(new GameEvent("A temporal rip accelerates your perceptions!", 1.5f));
                    }
                }
            }
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The gears of chronos synchronize back to steady rhythm.", 2.5f));
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "inverted_gravity"; }
            @Override public String getName() { return "Inverted Gravity"; }
            @Override public String getDescription() {
                return "The gravity well weakens to a whisper. Falling damage is abolished and bodies float in mid-air.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("INVERTED GRAVITY! Weightlessness lifts all matter off the stones!", 3.0f));
                }
                if (context != null && context.player != null && context.player.getStatusManager() != null) {
                    context.player.getStatusManager().addEffect(StatusEffectType.FLOATING, 999, 1, false);
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {}
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("Gravity returns with a heavy thud.", 2.5f));
                }
                if (context != null && context.player != null && context.player.getStatusManager() != null) {
                    context.player.getStatusManager().removeEffect(StatusEffectType.FLOATING);
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "glitch_telekinesis"; }
            @Override public String getName() { return "Glitch Telekinesis"; }
            @Override public String getDescription() {
                return "Invisible phantom hands fling loose objects and missiles randomly across corridors.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("GLITCH TELEKINESIS! Poltergeist energies toss loose objects!", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {
                if (context == null || context.eventManager == null) return;
                if (RNG.nextFloat() < 0.20f) {
                    context.eventManager.addEvent(new GameEvent("A loose pebble is violently hurled down the dark corridor!", 1.5f));
                }
            }
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The erratic telekinetic pulses fade into silence.", 2.5f));
                }
            }
        });

        register(new ChaosSurgeEvent() {
            @Override public String getId() { return "wand_overcharge"; }
            @Override public String getName() { return "Wand Overcharge"; }
            @Override public String getDescription() {
                return "Conduits overflow with raw wild mana: wands cost 0 charges to zap.";
            }
            @Override public void onActivate(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("WAND OVERCHARGE! Wands crackle with boundless mana!", 3.0f));
                }
            }
            @Override public void onTurnTick(ChaosEventContext context) {}
            @Override public void onExpire(ChaosEventContext context) {
                if (context != null && context.eventManager != null) {
                    context.eventManager.addEvent(new GameEvent("The crackling wild mana surge dissipates.", 2.5f));
                }
            }
        });
    }

    public static List<ChaosSurgeEvent> getAllEvents() {
        return Collections.unmodifiableList(ALL_EVENTS);
    }

    public static ChaosSurgeEvent getEventById(String id) {
        return BY_ID.get(id);
    }

    public static void registerAll(ChaosManager manager) {
        if (manager == null) return;
        for (ChaosSurgeEvent event : ALL_EVENTS) {
            manager.registerEvent(event);
        }
    }

    private ChaosSurgeTable() {}
}
