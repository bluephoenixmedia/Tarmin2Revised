package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.ShopkeeperNpc;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.weather.WeatherManager;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.utils.DiceRoller;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Scenery;

import java.util.ArrayList;
import java.util.List;

public class TurnManager {

    private final float BASE_TURN_COST = 12.0f;

    // Metabolic Constants (Per 1.0 Time Unit). Satiety now decays at the same
    // pace as hydration (previously half as fast, 0.02f) so it can actually bind
    // as a real resource pressure across an expedition instead of never mattering.
    public static final float SATIETY_DECAY = 0.04f; // ~3000 turns to starve from full (120)
    public static final float HYDRATION_DECAY = 0.04f; // ~2500 turns to dehydrate from full (100)
    private static final float TEMP_ADJUST_RATE = 0.05f; // Speed of body temp adjustment
    private int ringRechargeCounter = 0;
    private int turnCounter = 0;
    private PlayerStats.SatiationState lastSatiationState = PlayerStats.SatiationState.NORMAL;
    private boolean wasParched = false;
    /** Last Taint tier seen, so crossing into a tier is announced once. Null until the first tick. */
    private com.bpm.minotaur.gamedata.blight.Taint.Tier lastTaintTier = null;
    private int legionMusterTimer = 0;
    private final ShopkeeperAiManager shopkeeperAiManager = new ShopkeeperAiManager();

    public TurnManager() {
    }

    /**
     * Processes monster turns based on speed/energy relative to player action.
     * Assumes Player has just taken an action (costing BASE_TURN_COST adjusted by
     * speed).
     */
    public void processTurn(Maze maze, Player player, MonsterAiManager aiManager, CombatManager combatManager,
            WorldManager worldManager, GameEventManager eventManager) {
        processTurn(maze, player, aiManager, combatManager, worldManager, eventManager, null, null);
    }

    public void processTurn(Maze maze, Player player, MonsterAiManager aiManager, CombatManager combatManager,
            WorldManager worldManager, GameEventManager eventManager, ItemDataManager itemDataManager,
            AssetManager assetManager) {
        if (maze == null || player == null)
            return;

        turnCounter++;
        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().setTurnsLived(turnCounter);
        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().setStrataReached(maze.getLevel());

        // 1. Calculate Time Elapsed for Player Action
        // Time = Cost / Speed.
        // Standard Speed 12 -> Time = 1.0.
        int playerSpeed = player.getEffectiveSpeed();
        float timeElapsed = BASE_TURN_COST / (float) playerSpeed;

        // --- NEW: SURVIVAL METABOLISM ---
        updateMetabolism(player, maze, worldManager, eventManager, timeElapsed);
        // --------------------------------

        // --- MONSTER INTER-ZONAL PURSUIT (Gates & Ladders) ---
        MonsterPursuitManager.getInstance().onPlayerTurn(maze, player, worldManager,
                (combatManager != null && combatManager.getGameScreen() != null) ? combatManager.getGameScreen().getSoundManager() : null,
                eventManager, combatManager);

        // --- RING EFFECTS ---
        // (Moved after metabolism so we can heal starvation damage if we have regen
        // ring?)
        // --- RING RECHARGE (Every 120 turns) ---
        ringRechargeCounter++;
        if (ringRechargeCounter >= 120) {
            ringRechargeCounter = 0;
            if (player.getEquipment() != null) {
                player.getEquipment().rechargeRings(1);
            }
        }

        if (player.getEquipment().hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.REGENERATION)) {
            int regenRate = player.getEquipment().countRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.REGENERATION);
            if (player.getCurrentHP() < player.getMaxHP()) {
                player.heal(regenRate);
            }
        }

        if (player.getEquipment().hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.SEARCHING)) {
            // Placeholder for Searching
        }

        // (WIS passive MP regen is integrated into deterministic natural recovery paced by Wisdom below)

        // 1b. Blood frenzy: a gore-soaked player is scented by beasts across the
        // chunk, which is the mechanic LiquidType.BLOOD advertises.
        applyBloodFrenzy(maze, player);

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

                // --- Traveling Merchant Combat Safety ---
                // A roaming monster adjacent to the merchant attacks it instead of
                // idling, so the merchant can actually be run down and killed.
                if (attackShopkeeperIfAdjacent(monster, maze, eventManager, itemDataManager, assetManager)) {
                    safetyCounter++;
                    if (safetyCounter > 10)
                        break;
                    continue;
                }

                // Act
                aiManager.updateMonster(monster, maze, player, true, combatManager);

                safetyCounter++;
                if (safetyCounter > 10)
                    break; // Prevent infinite loops
            }
        }

        // 4. Traveling Merchant AI (wanders, defends itself, flees)
        if (maze.getShopkeeper() != null) {
            shopkeeperAiManager.update(maze.getShopkeeper(), maze, player, eventManager, itemDataManager, assetManager);
        }

        // 5. Themed chunk objective tick: bramble regrowth and the Rune of
        // Surrender channel, then the completion check for every theme.
        com.bpm.minotaur.generation.theme.ThemeObjectiveManager.onTurn(maze, eventManager);
        if (worldManager != null) {
            worldManager.checkThemeObjective(maze, eventManager);
        }
    }

    /**
     * Wakes nearby beasts while the player is gore-soaked.
     *
     * <p>Standing in a blood pool long enough leaves a scent trail. Beasts and
     * vermin within 10 tiles drop what they are doing and hunt, which is the
     * cost of fighting in the Colosseum's pools rather than backing onto dry
     * stone.
     */
    private void applyBloodFrenzy(Maze maze, Player player) {
        if (maze == null || player == null) return;
        if (maze.getLiquidManager() == null || !maze.getLiquidManager().isBloodFrenzyActive()) return;

        int px = (int) player.getPosition().x;
        int py = (int) player.getPosition().y;

        for (Monster m : maze.getMonsters().values()) {
            if (m == null || !m.isAlive()) continue;
            if (m.getFaction() != com.bpm.minotaur.gamedata.monster.Faction.BEASTS_AND_VERMIN) continue;

            int dx = Math.abs((int) m.getPosition().x - px);
            int dy = Math.abs((int) m.getPosition().y - py);
            if (dx + dy > 10) continue;

            m.setState(Monster.MonsterState.HUNTING);
        }
    }

    /**
     * If the given monster is adjacent to a living traveling merchant, it attacks
     * the merchant instead of the player. Returns true if an attack (or the
     * merchant's death) consumed the monster's action this tick.
     */
    private boolean attackShopkeeperIfAdjacent(Monster monster, Maze maze, GameEventManager eventManager,
            ItemDataManager itemDataManager, AssetManager assetManager) {
        ShopkeeperNpc shopkeeper = maze.getShopkeeper();
        if (shopkeeper == null || !shopkeeper.isAlive() || monster.isAlly())
            return false; // the player's allies never turn on the merchant

        GridPoint2 monsterPos = new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y);
        GridPoint2 shopPos = shopkeeper.getGridPosition();
        int dist = Math.abs(monsterPos.x - shopPos.x) + Math.abs(monsterPos.y - shopPos.y);
        if (dist != 1)
            return false;

        int dmg = Math.max(1, DiceRoller.roll(monster.getDamageDice()));
        shopkeeper.takeDamage(dmg);

        if (eventManager != null) {
            eventManager.addEvent(new GameEvent(
                    "The " + monster.getName() + " strikes the Traveling Merchant! (-" + dmg + ")", 1.5f));
        }

        if (!shopkeeper.isAlive()) {
            List<com.bpm.minotaur.gamedata.item.Item> drops = shopkeeper.createDeathDrops(itemDataManager, assetManager);
            for (com.bpm.minotaur.gamedata.item.Item drop : drops) {
                drop.getPosition().set(shopPos.x + 0.5f, shopPos.y + 0.5f);
                if (!maze.getItems().containsKey(shopPos)) {
                    maze.addItem(drop);
                } else {
                    // Tile occupied; scatter to an adjacent free tile if possible
                    GridPoint2 alt = new GridPoint2(shopPos.x + 1, shopPos.y);
                    if (!maze.getItems().containsKey(alt)) {
                        drop.getPosition().set(alt.x + 0.5f, alt.y + 0.5f);
                        maze.addItem(drop);
                    }
                }
            }
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent(
                        "The Traveling Merchant has fallen! Their wares scatter across the floor.", 3f));
            }
            maze.setShopkeeper(null);
        }

        return true;
    }

    /**
     * Tiered exposure debuffs driven by current body temperature:
     * - Below 35.0C: CHILLED (-20% move/attack speed, via Player#getEffectiveSpeed).
     * - Below 32.0C: HYPOTHERMIA (1 damage every 10 turns, shivers, blurred vision).
     * - Above 38.0C: HEATSTROKE (double thirst decay -- applied in updateMetabolism --
     *   and stamina exhaustion).
     * A small hysteresis band avoids the effect flickering on/off at the boundary.
     */
    public void applyExposureTiers(Player player, PlayerStats stats, GameEventManager eventManager, float bodyTemp) {
        StatusManager sm = player.getStatusManager();

        boolean chilled = bodyTemp < 35.0f;
        boolean hypothermic = bodyTemp < 32.0f;
        boolean heatstroke = bodyTemp > 38.0f;

        if (chilled && !sm.hasEffect(StatusEffectType.CHILLED)) {
            sm.addEffect(StatusEffectType.CHILLED, 999, 1, false);
            if (eventManager != null) eventManager.addEvent(new GameEvent("The cold seeps into your bones. You feel CHILLED.", 2.0f));
        } else if (!chilled && bodyTemp > 35.5f && sm.hasEffect(StatusEffectType.CHILLED)) {
            sm.removeEffect(StatusEffectType.CHILLED);
        }

        if (hypothermic && !sm.hasEffect(StatusEffectType.HYPOTHERMIA)) {
            sm.addEffect(StatusEffectType.HYPOTHERMIA, 999, 1, false);
            if (eventManager != null) eventManager.addEvent(new GameEvent("HYPOTHERMIA sets in! Your body violently shivers.", 2.5f));
        } else if (!hypothermic && bodyTemp > 32.5f && sm.hasEffect(StatusEffectType.HYPOTHERMIA)) {
            sm.removeEffect(StatusEffectType.HYPOTHERMIA);
        }

        if (hypothermic && (turnCounter % 10 == 0)) {
            player.takeTrueDamage(1);
            if (eventManager != null) eventManager.addEvent(new GameEvent("The cold gnaws at your flesh.", 1.5f));
        }

        if (heatstroke && !sm.hasEffect(StatusEffectType.HEATSTROKE)) {
            sm.addEffect(StatusEffectType.HEATSTROKE, 999, 1, false);
            sm.addEffect(StatusEffectType.EXHAUSTED, 999, 1, false);
            if (eventManager != null) eventManager.addEvent(new GameEvent("The heat overwhelms you! HEATSTROKE!", 2.5f));
        } else if (!heatstroke && bodyTemp < 37.5f) {
            if (sm.hasEffect(StatusEffectType.HEATSTROKE)) sm.removeEffect(StatusEffectType.HEATSTROKE);
            if (sm.hasEffect(StatusEffectType.EXHAUSTED)) sm.removeEffect(StatusEffectType.EXHAUSTED);
        }
    }

    private static final float HEAT_SOURCE_RADIUS = 3.5f;
    private static final float HEAT_SOURCE_WARM_RATE = 0.4f; // deg C per time unit, within radius

    /** Warms the player toward 37C when standing near a lit Campfire or Lantern light source. */
    private float applyNearbyHeatSourceWarming(Player player, Maze maze, float currentTemp, float time) {
        if (maze == null || currentTemp >= 37.0f) {
            return currentTemp;
        }
        com.badlogic.gdx.utils.Array<com.bpm.minotaur.lighting.LightSource> lights = maze.getLights();
        if (lights == null) return currentTemp;

        for (com.bpm.minotaur.lighting.LightSource light : lights) {
            if (!light.isActive() || light.getId() == null) continue;
            String id = light.getId();
            boolean isHeatSource = id.contains("campfire") || id.contains("cook_pot") || id.contains("lantern");
            if (!isHeatSource) continue;

            if (light.getPosition().dst(player.getPosition()) <= HEAT_SOURCE_RADIUS) {
                return Math.min(37.0f, currentTemp + HEAT_SOURCE_WARM_RATE * time);
            }
        }
        return currentTemp;
    }

    private boolean isPlayerInDesertShade(Player player, Maze maze) {
        if (maze == null || player == null) return false;
        int px = (int) player.getPosition().x;
        int py = (int) player.getPosition().y;

        int cellData = maze.getWallDataAt(px, py);
        if ((cellData & 0b01010101) != 0) {
            return true;
        }

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = px + dx;
                int ny = py + dy;
                if (nx < 0 || nx >= maze.getWidth() || ny < 0 || ny >= maze.getHeight()) {
                    return true;
                }
                if (maze.isWall(nx, ny)) {
                    return true;
                }
                GridPoint2 pt = new GridPoint2(nx, ny);
                if (maze.getScenery() != null && maze.getScenery().containsKey(pt)) {
                    Scenery s = maze.getScenery().get(pt);
                    if (s != null && s.getType() != Scenery.SceneryType.BUSH) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void updateMetabolism(Player player, Maze maze, WorldManager worldManager, GameEventManager eventManager, float time) {
        PlayerStats stats = player.getStats();

        // 1. Hunger & Thirst Decay (HEATSTROKE doubles thirst decay)
        boolean isHeatstroke = stats.getBodyTemperature() > 38.0f;
        // A heavy load burns food and water faster (the Stressed and Overloaded tiers). The time
        // passed in already stretches with the slower step, so that is taken back out here and
        // only the tier's own drain factor remains.
        float load = player.getMetabolicTimeScale();
        stats.modifySatiety(-SATIETY_DECAY * load * time * com.bpm.minotaur.gamedata.trait.TraitEffects.mult("hungerMult"));
        stats.modifyHydration(-HYDRATION_DECAY * load * (isHeatstroke ? 2.0f : 1.0f) * time * com.bpm.minotaur.gamedata.trait.TraitEffects.mult("thirstMult"));

        // 2. Temperature Logic
        if (worldManager != null && worldManager.getWeatherManager() != null) {
            Biome biome = worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId());
            float ambientTemp = worldManager.getWeatherManager().getAmbientTemperature(biome);
            if (biome == Biome.DESERT && isPlayerInDesertShade(player, maze)) {
                ambientTemp -= 12.0f;
            }
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
                if (player.getEquipment().hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.WARMTH)) {
                    totalWarmth += 20f;
                }
            }

            float stress = 0f;
            if (ambientTemp < 5.0f) {
                // True cold stress (Snow / Blizzard)
                float effectiveCold = (5.0f - ambientTemp) - totalWarmth;
                if (effectiveCold > 0) {
                    stress = -effectiveCold * 0.003f;
                } else {
                    if (currentTemp < 37.0f) {
                        stress = 0.01f;
                    }
                }
            } else if (ambientTemp > 35.0f) {
                // True heat stress (Extreme Desert Heatwave)
                stress = (ambientTemp - 35.0f) * 0.005f;
            } else {
                // Natural homeostasis comfort zone (5°C to 35°C):
                // Body naturally regulates toward healthy 37.0°C
                if (currentTemp < 37.0f)
                    stress = 0.02f;
                if (currentTemp > 37.0f)
                    stress = -0.02f;
                // Snap if close
                if (Math.abs(currentTemp - 37.0f) < 0.1f) {
                    stats.setBodyTemperature(37.0f);
                    stress = 0f;
                }
            }

            // Body temp clamped to a non-lethal safe floor of 30.0°C -- low enough to
            // sustain the Hypothermia exposure tier (< 32.0°C) without being fatal outright.
            float newTemp = Math.max(30.0f, Math.min(41.0f, currentTemp + (stress * time)));

            // Warming up near a lit Campfire / Shelter Lantern
            newTemp = applyNearbyHeatSourceWarming(player, maze, newTemp, time);

            stats.setBodyTemperature(newTemp);

            applyExposureTiers(player, stats, eventManager, newTemp);
        }

        updateTaint(player, maze, worldManager, eventManager, time);

        player.tickForm(eventManager);
        player.tickTrait(maze, eventManager);

        // 3. Natural HP & MP Regeneration (NetHack 3-pillar model)
        PlayerStats.SatiationState satState = stats.getSatiationState();
        int regenInterval = Math.max(1, Math.round(stats.getRegenIntervalTurns() * com.bpm.minotaur.gamedata.trait.TraitEffects.mult("regenMult")))
                * com.bpm.minotaur.gamedata.blight.Taint.regenIntervalMult(stats.getTaint());
        if (satState != PlayerStats.SatiationState.STARVING && (turnCounter % regenInterval == 0)) {
            if (player.getCurrentHP() < stats.getMaxHP()) {
                stats.heal(1);
            }
        }
        // MP natural recovery: Paced by Wisdom when hydrated.
        // Base interval is 24 turns (at WIS 10, mod 0), shortening by 3 turns per WIS mod down to a min of 8 turns (at WIS 20+).
        int wisMod = Math.max(0, (player.getEffectiveWisdom() - 10) / 2);
        int mpInterval = Math.max(8, 24 - (wisMod * 3));
        if (stats.getHydrationFloat() > 0 && (turnCounter % mpInterval == 0)) {
            if (player.getCurrentMP() < stats.getMaxMP()) {
                player.restoreMP(1);
            }
        }

        // 4. Throttled Satiation & Thirst State Notifications (No per-step log spam)
        if (satState != lastSatiationState) {
            lastSatiationState = satState;
            if (eventManager != null) {
                switch (satState) {
                    case HUNGRY:
                        eventManager.addEvent(new GameEvent("You are starting to feel hungry.", 2.0f));
                        break;
                    case STARVING:
                        eventManager.addEvent(new GameEvent("You are starving!", 2.5f));
                        break;
                    case SATIATED:
                        eventManager.addEvent(new GameEvent("You are satiated.", 2.0f));
                        break;
                    case CHOKING:
                        eventManager.addEvent(new GameEvent("You are choking from overeating!", 2.5f));
                        break;
                    default:
                        break;
                }
            }
        } else if (satState == PlayerStats.SatiationState.STARVING && (turnCounter % 50 == 0)) {
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("You are starving!", 2.0f));
            }
        }

        boolean isParched = stats.getHydrationFloat() <= 0;
        if (isParched != wasParched) {
            wasParched = isParched;
            if (isParched && eventManager != null) {
                eventManager.addEvent(new GameEvent("You are parched! Drink water or find a fountain.", 2.5f));
            }
        } else if (isParched && (turnCounter % 50 == 0)) {
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("You are parched!", 2.0f));
            }
        }

        // 5. Starvation & Dehydration Damage (Every 25 / 20 turns)
        if (satState == PlayerStats.SatiationState.STARVING && (turnCounter % 25 == 0)) {
            player.takeTrueDamage(1);
            if (player.getCurrentHP() <= 0 && eventManager != null) {
                eventManager.addEvent(new GameEvent("You starved to death!", 2.0f));
                eventManager.addEvent(new GameEvent(GameEvent.EventType.PLAYER_DIED, null));
            }
        }

        if (isParched && (turnCounter % 20 == 0)) {
            player.takeTrueDamage(1);
            if (player.getCurrentHP() <= 0 && eventManager != null) {
                eventManager.addEvent(new GameEvent("You died of thirst!", 2.0f));
                eventManager.addEvent(new GameEvent(GameEvent.EventType.PLAYER_DIED, null));
            }
        }

        // Hypothermia: Non-lethal sensory feedback (no damage, no death)
        if (stats.getBodyTemperature() <= 35.0f) {
            if (Math.random() < 0.03) {
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("You shiver from the biting chill.", 1.5f));
                }
            }
        }

        // Hyperthermia: Non-lethal sensory feedback (no damage, no death)
        if (stats.getBodyTemperature() >= PlayerStats.BODY_TEMP_OVERHEAT) {
            if (Math.random() < 0.03) {
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("You sweat profusely from the intense heat.", 1.5f));
                }
            }
        }
    }

    /**
     * Blight Taint: rises on the Blight surface, announces each tier it crosses,
     * keeps HP inside the shrunken maximum, and at full Taint rouses the Legion.
     * Underground and elsewhere it neither rises nor falls; only the shelter bed,
     * Ashwater and death clear it.
     */
    private void updateTaint(Player player, Maze maze, WorldManager worldManager, GameEventManager eventManager,
                             float time) {
        PlayerStats stats = player.getStats();
        boolean onBlight = worldManager != null && worldManager.getCurrentLevel() == 1
                && maze.getBiome() == Biome.BLIGHT;

        if (onBlight) {
            DayNightManager dnm = worldManager.getDayNightManager();
            boolean night = dnm != null && (dnm.getPhase() == DayNightManager.Phase.NIGHT
                    || dnm.getPhase() == DayNightManager.Phase.DUSK);
            int px = (int) player.getPosition().x;
            int py = (int) player.getPosition().y;
            boolean inRot = maze.getLiquidManager() != null
                    && maze.getLiquidManager().getLiquidAt(px, py) == com.bpm.minotaur.gamedata.liquid.LiquidType.BLACK_MUCK;
            boolean warded = player.getInventory() != null
                    && player.getInventory().hasItemOfType(com.bpm.minotaur.gamedata.item.Item.ItemType.WARD_CHARM);
            stats.addTaint(com.bpm.minotaur.gamedata.blight.Taint.gainPerTurn(night, inRot, warded) * time);
        }

        com.bpm.minotaur.gamedata.blight.Taint.Tier tier =
                com.bpm.minotaur.gamedata.blight.Taint.tierOf(stats.getTaint());
        if (lastTaintTier != null && tier.ordinal() > lastTaintTier.ordinal() && eventManager != null) {
            String msg = com.bpm.minotaur.gamedata.blight.Taint.onEnter(tier);
            if (msg != null) eventManager.addEvent(new GameEvent(msg, 3.0f));
        }
        lastTaintTier = tier;

        // Wasting shrinks the maximum; current HP must not sit above it. Not a wound:
        // it must neither spend temporary HP nor break a channelled action.
        stats.clampCurrentHPToMax();

        if (onBlight && com.bpm.minotaur.gamedata.blight.Taint.rousesLegion(stats.getTaint())) {
            // The first muster is the moment Taint is full; then one every interval.
            if (legionMusterTimer % com.bpm.minotaur.gamedata.blight.Taint.LEGION_MUSTER_INTERVAL == 0) {
                musterLegion(player, maze, worldManager, eventManager);
            }
            legionMusterTimer++;
        } else {
            legionMusterTimer = 0;
        }
    }

    /**
     * Full Taint: every Legion soldier in the chunk turns on the player, and if
     * there are too few of them a fresh patrol marches in through the gate
     * farthest from the player. Like a gunshot, this ignores line of sight.
     */
    private void musterLegion(Player player, Maze maze, WorldManager worldManager, GameEventManager eventManager) {
        GridPoint2 playerTile = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);
        int legion = 0;
        for (Monster m : maze.getMonsters().values()) {
            if (m == null || m.getFaction() != com.bpm.minotaur.gamedata.monster.Faction.TARMIN_LEGION) continue;
            legion++;
            m.setState(Monster.MonsterState.HUNTING);
            m.setLastKnownTargetPos(new GridPoint2(playerTile));
        }
        if (legion >= com.bpm.minotaur.gamedata.blight.Taint.LEGION_MUSTER_CAP) return;

        com.bpm.minotaur.gamedata.monster.MonsterDataManager data = worldManager.getMonsterDataManager();
        if (data == null) return;
        GridPoint2 far = null;
        float best = -1f;
        for (GridPoint2 gatePos : maze.getGates().keySet()) {
            // Step one tile inside the gate so the patrol stands on open ground.
            int gx = Math.max(1, Math.min(maze.getWidth() - 2, gatePos.x));
            int gy = Math.max(1, Math.min(maze.getHeight() - 2, gatePos.y));
            if (!maze.isPassable(gx, gy) || maze.getMonsters().containsKey(new GridPoint2(gx, gy))) continue;
            float d = playerTile.dst2(gx, gy);
            if (d > best) {
                best = d;
                far = new GridPoint2(gx, gy);
            }
        }
        if (far == null) return;

        Monster.MonsterType type = (turnCounter % 2 == 0) ? Monster.MonsterType.ORC : Monster.MonsterType.HOBGOBLIN;
        Monster patrol = new Monster(type, far.x, far.y,
                com.bpm.minotaur.gamedata.monster.MonsterColor.RED, data, worldManager.getAssetManager());
        patrol.scaleStats(worldManager.calculateEffectiveDifficulty(worldManager.getCurrentPlayerChunkId(), 1));
        patrol.setCurrentHP(patrol.getMaxHP());
        patrol.setFaction(com.bpm.minotaur.gamedata.monster.Faction.TARMIN_LEGION);
        patrol.setState(Monster.MonsterState.HUNTING);
        patrol.setLastKnownTargetPos(new GridPoint2(playerTile));
        maze.addMonster(patrol);
        if (eventManager != null) {
            eventManager.addEvent(new GameEvent("A Legion patrol marches out of the ash toward you.", 2.5f));
        }
    }
}
