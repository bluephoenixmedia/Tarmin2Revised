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

    public static final float BASE_TURN_COST = 12.0f;

    // Metabolic Constants (Per 1.0 Time Unit). Hunger and thirst burn on their own scales;
    // see Hunger and Thirst.
    private static final float TEMP_ADJUST_RATE = 0.05f; // Speed of body temp adjustment
    private int ringRechargeCounter = 0;
    private int turnCounter = 0;
    private com.bpm.minotaur.gamedata.player.Hunger.Stage lastHungerStage = com.bpm.minotaur.gamedata.player.Hunger.Stage.NOT_HUNGRY;
    private com.bpm.minotaur.gamedata.player.Thirst.Stage lastThirstStage = com.bpm.minotaur.gamedata.player.Thirst.Stage.HYDRATED;
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

    /**
     * The Shelter Altar's Provisions tree: its rations are handed over on the first turn
     * of an expedition, i.e. the first turn outside the shelter (#101). Without an item
     * factory the pack simply waits for a turn that has one.
     */
    private void handOverAltarProvisions(Maze maze, Player player, GameEventManager eventManager,
            ItemDataManager itemDataManager, AssetManager assetManager) {
        com.bpm.minotaur.gamedata.progression.ShelterAltar altar =
                com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance();
        if (maze.isHomeTile((int) player.getPosition().x, (int) player.getPosition().y)) {
            altar.onShelterTurn();
            return;
        }
        if (itemDataManager == null) return;
        int bonusProvisions = altar.claimExpeditionProvisions();
        if (bonusProvisions <= 0) return;
        com.bpm.minotaur.gamedata.item.Item bonusFood = itemDataManager.createItem(
                com.bpm.minotaur.gamedata.item.Item.ItemType.FOOD, 0, 0,
                com.bpm.minotaur.gamedata.item.ItemColor.TAN, assetManager);
        if (bonusFood == null) return;
        bonusFood.setStackCount(bonusProvisions);
        if (!player.getInventory().pickupToBackpack(bonusFood)) {
            bonusFood.setPosition((int) player.getPosition().x, (int) player.getPosition().y);
            maze.addItem(bonusFood);
        }
        if (eventManager != null) {
            eventManager.addEvent(new GameEvent("The altar's provisions: " + bonusProvisions
                    + (bonusProvisions == 1 ? " ration" : " rations") + " packed for the road.", 3f));
        }
    }

    public void processTurn(Maze maze, Player player, MonsterAiManager aiManager, CombatManager combatManager,
            WorldManager worldManager, GameEventManager eventManager, ItemDataManager itemDataManager,
            AssetManager assetManager) {
        if (maze == null || player == null)
            return;

        turnCounter++;
        {
            PlayerStats ts = player.getStats();
            com.bpm.minotaur.gamedata.player.Hunger.Stage sat = ts.getHungerStage();
            if (worldManager != null) {
                com.bpm.minotaur.telemetry.TelemetryManager.getInstance().setContext(
                        worldManager.getDifficulty() != null ? worldManager.getDifficulty().name() : null,
                        worldManager.getWorldSeed(), com.bpm.minotaur.BuildInfo.VERSION);
            }
            com.bpm.minotaur.telemetry.TelemetryManager.getInstance().notePlayerLevel(player.getLevel());
            // Below half health with the starting heal ready, once a save: say which key (#263).
            com.bpm.minotaur.managers.HintManager.getInstance().checkHealReady(player, eventManager);
            com.bpm.minotaur.telemetry.TelemetryManager.getInstance().onTurn(turnCounter, maze.getLevel(),
                    maze.isHomeTile((int) player.getPosition().x, (int) player.getPosition().y),
                    player.getCurrentHP(), player.getCurrentMP(),
                    sat.isHungry(),
                    ts.getThirstStage().isThirsty(),
                    ts.getBodyTemperature() < 35.0f,
                    player.getStatusManager() != null
                            && player.getStatusManager().hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.POISONED));
        }
        handOverAltarProvisions(maze, player, eventManager, itemDataManager, assetManager);

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
            if (combatManager != null && combatManager.isInCombat() && combatManager.getMonster() == monster) {
                continue; // Skip the monster actively dueling the player in modal combat (it acts via CombatManager)
            }
            // -----------------------

            int monsterSpeed = monster.getEffectiveSpeed();
            float energyGain = timeElapsed * monsterSpeed;

            monster.addEnergy(energyGain);

            // Ambient speech barks for nearby active monsters
            if (monster.canSpeak() && player != null) {
                float dist = player.getPosition().dst(monster.getPosition());
                if (dist <= 6.0f && com.badlogic.gdx.math.MathUtils.randomBoolean(0.20f)) {
                    boolean comprehend = player.getStatusManager().hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.COMPREHEND_LANGUAGES);
                    String phrase = com.bpm.minotaur.gamedata.monster.MonsterSpeechManager.getSpeechPhrase(monster, comprehend);
                    monster.speak(phrase, 3.5f);
                    monster.resetSpeechCooldown(com.badlogic.gdx.math.MathUtils.random(10f, 18f));
                }
            }

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

    /** The Exposure band seen last turn, so each change is announced once. */
    private com.bpm.minotaur.gamedata.player.Exposure.Band lastExposureBand =
            com.bpm.minotaur.gamedata.player.Exposure.Band.COMFORTABLE;
    /** Whether the player stood in freezing water last turn: the plunge is felt on entry. */
    private boolean wasInFreezingWater = false;

    /**
     * What each Exposure band does (#194), from the body temperature:
     * - Chilly: a warning.
     * - Cold: -10% speed; Freezing: -20% and no natural regen (both applied where speed and
     *   regen are computed). CHILLED marks Cold and colder.
     * - Hypothermic: HYPOTHERMIA, 1 damage every 10 turns and an occasional lost action.
     * - Above 38.0C: HEATSTROKE (double thirst decay -- applied in updateMetabolism -- and
     *   stamina exhaustion), unchanged.
     */
    public void applyExposureTiers(Player player, PlayerStats stats, GameEventManager eventManager, float bodyTemp) {
        StatusManager sm = player.getStatusManager();
        com.bpm.minotaur.gamedata.player.Exposure.Band band =
                com.bpm.minotaur.gamedata.player.Exposure.bandOf(bodyTemp);

        if (band != lastExposureBand && eventManager != null) {
            String notice = exposureNotice(band, lastExposureBand);
            if (notice != null) eventManager.addEvent(new GameEvent(notice, 2.5f));
        }
        lastExposureBand = band;

        boolean cold = band.isCold();
        if (cold && eventManager != null) {
            com.bpm.minotaur.managers.HintManager.getInstance().checkCold(eventManager);
        }
        boolean hypothermic = band == com.bpm.minotaur.gamedata.player.Exposure.Band.HYPOTHERMIC;
        boolean heatstroke = bodyTemp > com.bpm.minotaur.gamedata.player.Exposure.HOT_ABOVE;

        if (cold && !sm.hasEffect(StatusEffectType.CHILLED)) {
            sm.addEffect(StatusEffectType.CHILLED, 999, 1, false);
        } else if (!cold && sm.hasEffect(StatusEffectType.CHILLED)) {
            sm.removeEffect(StatusEffectType.CHILLED);
        }

        if (hypothermic && !sm.hasEffect(StatusEffectType.HYPOTHERMIA)) {
            sm.addEffect(StatusEffectType.HYPOTHERMIA, 999, 1, false);
        } else if (!hypothermic && sm.hasEffect(StatusEffectType.HYPOTHERMIA)) {
            sm.removeEffect(StatusEffectType.HYPOTHERMIA);
        }

        if (hypothermic && (turnCounter % com.bpm.minotaur.gamedata.player.Exposure.HYPOTHERMIA_DAMAGE_EVERY == 0)) {
            player.takeTrueDamage(1);
            if (eventManager != null) eventManager.addEvent(new GameEvent("The cold gnaws at your flesh.", 1.5f));
        }
        if (hypothermic && !sm.hasEffect(StatusEffectType.SLEEP)
                && Math.random() < com.bpm.minotaur.gamedata.player.Exposure.HYPOTHERMIA_LOST_ACTION_CHANCE) {
            // SLEEP is the player's existing pass-the-turn state, as fainting uses it.
            sm.addEffect(StatusEffectType.SLEEP, 1, 1, false);
            if (eventManager != null) eventManager.addEvent(new GameEvent("You shake uncontrollably and lose a moment.", 1.5f));
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

    /** The line for moving into {@code band}, or null when the move needs none. */
    static String exposureNotice(com.bpm.minotaur.gamedata.player.Exposure.Band band,
                                 com.bpm.minotaur.gamedata.player.Exposure.Band previous) {
        boolean colder = band.ordinal() > previous.ordinal();
        switch (band) {
            case WARM: return previous.ordinal() > com.bpm.minotaur.gamedata.player.Exposure.Band.COMFORTABLE.ordinal()
                    ? "Warmth floods back into your limbs." : null;
            case CHILLY: return colder ? "You feel chilly. Find a fire or a cloak before it bites." : null;
            case COLD: return colder ? "You are cold. Your movements stiffen." : "You are a little less cold.";
            case FREEZING: return colder ? "You are freezing. Your wounds will not knit until you are warm."
                    : "The worst of the cold lets go.";
            case HYPOTHERMIC: return "HYPOTHERMIA sets in! Your body violently shivers.";
            default: return null;
        }
    }

    /**
     * One turn of Exposure (#194): the air (surface biome, hour and weather, or the stratum
     * underground), the worn warmth (rags included), the strongest heat in reach, and wetness
     * from rain, wading and freezing water. Moves the body temperature and applies the bands.
     */
    private void updateExposure(Player player, Maze maze, WorldManager worldManager, GameEventManager eventManager,
                                float time) {
        PlayerStats stats = player.getStats();
        int px = (int) player.getPosition().x;
        int py = (int) player.getPosition().y;
        boolean underground = maze.getLevel() > 1;
        boolean sheltered = !underground && maze.isHomeTile(px, py);

        Biome biome = null;
        com.bpm.minotaur.weather.WeatherType weather = null;
        boolean night = false;
        float ambient;
        if (underground) {
            ambient = com.bpm.minotaur.gamedata.player.Exposure.strataAmbient(maze.getStratum());
        } else if (worldManager != null && worldManager.getWeatherManager() != null) {
            biome = worldManager.getBiomeManager() != null
                    ? worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId()) : maze.getBiome();
            ambient = worldManager.getWeatherManager().getAmbientTemperature(biome);
            weather = worldManager.getWeatherManager().getCurrentWeather();
            DayNightManager dnm = worldManager.getDayNightManager();
            night = dnm != null && (dnm.getPhase() == DayNightManager.Phase.NIGHT
                    || dnm.getPhase() == DayNightManager.Phase.DUSK);
            if (biome == Biome.DESERT && isPlayerInDesertShade(player, maze)) {
                ambient -= 12.0f;
            }
        } else {
            return; // no world to feel
        }
        if (sheltered) {
            // Under a shelter's roof: out of the wind and the rain, if not yet warm.
            ambient = Math.max(ambient, com.bpm.minotaur.gamedata.player.Exposure.SHELTER_AMBIENT_FLOOR);
            weather = null;
        }

        // Clothing
        float warmth = com.bpm.minotaur.gamedata.player.Exposure.RAGS_WARMTH;
        float coverage = 0f;
        if (player.getEquipment() != null) {
            for (com.bpm.minotaur.gamedata.item.Item i : player.getEquipment().getAllEquipped()) {
                if (i == null) continue;
                warmth += i.getWarmthBonus();
                coverage += i.getCoverage();
            }
            if (player.getEquipment().hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.WARMTH)) {
                warmth += com.bpm.minotaur.gamedata.player.Exposure.RING_OF_WARMTH;
            }
        }

        com.bpm.minotaur.gamedata.player.Exposure.Heat heat = heatAt(player, maze, worldManager, sheltered, px, py);

        // Water underfoot
        float wetness = stats.getWetness();
        com.bpm.minotaur.gamedata.liquid.LiquidType liquid = maze.getLiquidAt(px, py);
        boolean inFreezingWater = false;
        if (com.bpm.minotaur.gamedata.player.Exposure.wets(liquid) && !isImmuneTo(maze, player, liquid)) {
            if (com.bpm.minotaur.gamedata.player.Exposure.isFreezingWater(liquid, biome, night, ambient)) {
                inFreezingWater = true;
                wetness = com.bpm.minotaur.gamedata.player.Exposure.afterSoaking();
                if (!wasInFreezingWater) {
                    stats.setBodyTemperature(Math.max(com.bpm.minotaur.gamedata.player.Exposure.BODY_FLOOR,
                            stats.getBodyTemperature() - com.bpm.minotaur.gamedata.player.Exposure.FREEZING_WATER_PLUNGE));
                    if (eventManager != null) {
                        eventManager.addEvent(new GameEvent("The freezing water knocks the breath out of you. You are SOAKED.", 2.5f));
                    }
                }
            } else {
                wetness = com.bpm.minotaur.gamedata.player.Exposure.afterWading(wetness);
            }
        }
        wasInFreezingWater = inFreezingWater;

        // Rain, then drying
        float rain = (underground || weather == null) ? 0f : com.bpm.minotaur.gamedata.player.Exposure.rainPerTurn(weather);
        com.bpm.minotaur.gamedata.player.Exposure.Wetness before = stats.getWetnessLevel();
        stats.setWetness(com.bpm.minotaur.gamedata.player.Exposure.wetnessAfter(wetness, rain, coverage, heat, time));
        com.bpm.minotaur.gamedata.player.Exposure.Wetness after = stats.getWetnessLevel();
        if (after != before && eventManager != null) {
            if (after == com.bpm.minotaur.gamedata.player.Exposure.Wetness.DRY) {
                eventManager.addEvent(new GameEvent("You are dry again.", 1.5f));
            } else if (after.ordinal() > before.ordinal() && !inFreezingWater) {
                eventManager.addEvent(new GameEvent(after == com.bpm.minotaur.gamedata.player.Exposure.Wetness.SOAKED
                        ? "You are SOAKED through. The cold bites three times as hard."
                        : "You are WET. The cold bites twice as hard.", 2.0f));
            }
        }

        float newTemp = com.bpm.minotaur.gamedata.player.Exposure.step(stats.getBodyTemperature(), ambient, warmth,
                stats.getWetnessLevel(), heat, time);
        stats.setBodyTemperature(newTemp);
        applyExposureTiers(player, stats, eventManager, newTemp);
    }

    private static boolean isImmuneTo(Maze maze, Player player, com.bpm.minotaur.gamedata.liquid.LiquidType liquid) {
        return maze.getLiquidManager() != null && maze.getLiquidManager().isImmuneToLiquid(player, liquid);
    }

    /**
     * The strongest heat in reach: Molten Fire beside you, a lit shelter's hearth, a field fire
     * or campfire within {@link com.bpm.minotaur.gamedata.player.Exposure#FIRE_RADIUS}, then a lit
     * carried lantern.
     */
    private com.bpm.minotaur.gamedata.player.Exposure.Heat heatAt(Player player, Maze maze, WorldManager worldManager,
                                                                 boolean sheltered, int px, int py) {
        int mr = com.bpm.minotaur.gamedata.player.Exposure.MOLTEN_RADIUS;
        for (int dy = -mr; dy <= mr; dy++) {
            for (int dx = -mr; dx <= mr; dx++) {
                if (maze.getLiquidAt(px + dx, py + dy) == com.bpm.minotaur.gamedata.liquid.LiquidType.MOLTEN_FIRE) {
                    return com.bpm.minotaur.gamedata.player.Exposure.Heat.MOLTEN;
                }
            }
        }
        if (sheltered && maze.isSanctuary()) {
            return com.bpm.minotaur.gamedata.player.Exposure.Heat.HEARTH;
        }
        if (maze.fieldFireWarming(px, py) != null || isBesideCampfireLight(maze, px, py)) {
            return com.bpm.minotaur.gamedata.player.Exposure.Heat.FIELD_FIRE;
        }
        if (player.hasLantern() && worldManager != null && worldManager.getLightingManager() != null
                && worldManager.getLightingManager().isLanternLit()) {
            return com.bpm.minotaur.gamedata.player.Exposure.Heat.LANTERN;
        }
        return com.bpm.minotaur.gamedata.player.Exposure.Heat.NONE;
    }

    /** A campfire, cook pot or hearth light within fire radius: the world's own fires warm as a field fire does. */
    private static boolean isBesideCampfireLight(Maze maze, int px, int py) {
        com.badlogic.gdx.utils.Array<com.bpm.minotaur.lighting.LightSource> lights = maze.getLights();
        if (lights == null) return false;
        int r = com.bpm.minotaur.gamedata.player.Exposure.FIRE_RADIUS;
        for (com.bpm.minotaur.lighting.LightSource light : lights) {
            if (!light.isActive() || light.getId() == null) continue;
            String id = light.getId();
            if (!(id.contains("campfire") || id.contains("cook_pot") || id.contains("hearth"))) continue;
            int lx = (int) Math.floor(light.getPosition().x);
            int ly = (int) Math.floor(light.getPosition().y);
            if (Math.max(Math.abs(lx - px), Math.abs(ly - py)) <= r) return true;
        }
        return false;
    }

    /**
     * Burns the chunk's field fires a turn (#194). A fire that goes out takes its flames and
     * light with it. While it burns, monsters that can see it within
     * {@link com.bpm.minotaur.gamedata.shelter.FieldFire#SIGHT_RADIUS} come to look.
     */
    void tickFieldFires(Maze maze, GameEventManager eventManager) {
        if (maze == null || maze.getFieldFires().isEmpty()) return;
        java.util.Iterator<com.bpm.minotaur.gamedata.shelter.FieldFire> it = maze.getFieldFires().iterator();
        while (it.hasNext()) {
            com.bpm.minotaur.gamedata.shelter.FieldFire fire = it.next();
            if (fire.burn()) {
                it.remove();
                maze.removeLight(fire.lightId());
                Scenery s = maze.getScenery().get(new GridPoint2(fire.x, fire.y));
                if (s != null && s.getTexturePath() != null && s.getTexturePath().contains("campfire")) {
                    maze.removeScenery(fire.x, fire.y);
                }
                if (eventManager != null) eventManager.addEvent(new GameEvent("Your fire gutters out to ash.", 2.0f));
                continue;
            }
            if (turnCounter % com.bpm.minotaur.gamedata.shelter.FieldFire.SIGHT_EVERY == 0) {
                drawMonstersToFire(maze, fire);
            }
        }
    }

    /** Monsters that can see the flames turn toward them, as a gunshot turns them toward its sound. */
    static int drawMonstersToFire(Maze maze, com.bpm.minotaur.gamedata.shelter.FieldFire fire) {
        com.badlogic.gdx.math.Vector2 at = new com.badlogic.gdx.math.Vector2(fire.x + 0.5f, fire.y + 0.5f);
        float r2 = com.bpm.minotaur.gamedata.shelter.FieldFire.SIGHT_RADIUS * com.bpm.minotaur.gamedata.shelter.FieldFire.SIGHT_RADIUS;
        int drawn = 0;
        for (Monster m : maze.getMonsters().values()) {
            if (m == null || !m.isAlive() || m.getState() == Monster.MonsterState.HUNTING) continue;
            if (m.getPosition().dst2(at) > r2) continue;
            if (!com.bpm.minotaur.gamedata.laser.LaserBurst.hasLineOfSight(maze, m.getPosition(), at)) continue;
            m.setState(Monster.MonsterState.HUNTING);
            m.setLastKnownTargetPos(new GridPoint2(fire.x, fire.y));
            drawn++;
        }
        return drawn;
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

    /**
     * Hunger's stage effects, NetHack-style (#191): a notice on every change of stage, a
     * reminder every 50 turns while Weak or worse, a chance to faint each turn while Fainting,
     * and death once Starved. Weak's STR and to-hit penalty is applied by PlayerStats.
     */
    private void updateHunger(Player player, com.bpm.minotaur.gamedata.player.Hunger.Stage stage, GameEventManager eventManager) {
        com.bpm.minotaur.gamedata.player.Hunger.Stage previous = lastHungerStage;
        lastHungerStage = stage;
        if (stage != previous) {
            String notice = null;
            switch (stage) {
                case SATIATED: notice = "You are satiated."; break;
                case HUNGRY: notice = "You are beginning to feel hungry."; break;
                case WEAK: notice = "You are beginning to feel weak."; break;
                case FAINTING: notice = "You feel faint from hunger."; break;
                default: break;
            }
            if (notice != null && eventManager != null) {
                eventManager.addEvent(new GameEvent(notice, 2.5f));
            }
        } else if (stage.isWeakened() && stage != com.bpm.minotaur.gamedata.player.Hunger.Stage.STARVED
                && turnCounter % 50 == 0 && eventManager != null) {
            eventManager.addEvent(new GameEvent(stage == com.bpm.minotaur.gamedata.player.Hunger.Stage.WEAK
                    ? "You are weak from hunger." : "You are fainting from lack of food.", 2.0f));
        }

        if (stage == com.bpm.minotaur.gamedata.player.Hunger.Stage.STARVED) {
            if (player.getCurrentHP() > 0) {
                com.bpm.minotaur.telemetry.TelemetryManager.getInstance().setSurvivalDeath("starvation");
                player.takeTrueDamage(player.getCurrentHP());
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("You die from starvation.", 2.0f));
                    eventManager.addEvent(new GameEvent(GameEvent.EventType.PLAYER_DIED, null));
                }
            }
            return;
        }

        if (stage == com.bpm.minotaur.gamedata.player.Hunger.Stage.FAINTING && player.getStatusManager() != null
                && !player.getStatusManager().hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.SLEEP)
                && Math.random() < com.bpm.minotaur.gamedata.player.Hunger.faintChance(player.getStats().getSatietyFloat())) {
            // Helpless: SLEEP is the player's existing pass-the-turn state.
            int turns = com.bpm.minotaur.gamedata.player.Hunger.faintTurns((float) Math.random());
            player.getStatusManager().addEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.SLEEP, turns, 1, false);
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("You faint from lack of food.", 2.5f));
            }
        }
    }

    /**
     * Thirst's stage effects (#193), mirroring {@link #updateHunger}: a notice on every change
     * of stage, a reminder every 50 turns while Dehydrated or worse, a chance to pass out each
     * turn while Collapsed, and death past the line. Dehydrated's DEX and speed penalties are
     * applied by PlayerStats and Player.
     */
    private void updateThirst(Player player, com.bpm.minotaur.gamedata.player.Thirst.Stage stage, GameEventManager eventManager) {
        com.bpm.minotaur.gamedata.player.Thirst.Stage previous = lastThirstStage;
        lastThirstStage = stage;
        if (stage != previous) {
            String notice = null;
            switch (stage) {
                case THIRSTY: notice = "You are thirsty."; break;
                case DEHYDRATED: notice = "You are dehydrated. Your hands are slow."; break;
                case COLLAPSE: notice = "Your vision swims from thirst."; break;
                default: break;
            }
            if (notice != null && eventManager != null) {
                eventManager.addEvent(new GameEvent(notice, 2.5f));
            }
        } else if (stage.isDehydrated() && stage != com.bpm.minotaur.gamedata.player.Thirst.Stage.DEAD
                && turnCounter % 50 == 0 && eventManager != null) {
            eventManager.addEvent(new GameEvent(stage == com.bpm.minotaur.gamedata.player.Thirst.Stage.DEHYDRATED
                    ? "You are dehydrated." : "You are collapsing from thirst.", 2.0f));
        }

        if (stage == com.bpm.minotaur.gamedata.player.Thirst.Stage.DEAD) {
            if (player.getCurrentHP() > 0) {
                com.bpm.minotaur.telemetry.TelemetryManager.getInstance().setSurvivalDeath("thirst");
                player.takeTrueDamage(player.getCurrentHP());
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("You die of thirst.", 2.0f));
                    eventManager.addEvent(new GameEvent(GameEvent.EventType.PLAYER_DIED, null));
                }
            }
            return;
        }

        if (stage == com.bpm.minotaur.gamedata.player.Thirst.Stage.COLLAPSE && player.getStatusManager() != null
                && !player.getStatusManager().hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.SLEEP)
                && Math.random() < com.bpm.minotaur.gamedata.player.Thirst.collapseChance(player.getStats().getHydrationFloat())) {
            int turns = com.bpm.minotaur.gamedata.player.Thirst.collapseTurns((float) Math.random());
            player.getStatusManager().addEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.SLEEP, turns, 1, false);
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("You collapse from thirst.", 2.5f));
            }
        }
    }

    /** Outdoors on the surface in rain or storm, a carried waterskin slowly fills (#193). */
    private void refillWaterskinInRain(Player player, Maze maze, WorldManager worldManager, GameEventManager eventManager) {
        if (worldManager == null || worldManager.getWeatherManager() == null || maze == null) return;
        if (maze.getLevel() > 1 || turnCounter % com.bpm.minotaur.gamedata.player.Thirst.RAIN_REFILL_TURNS != 0) return;
        com.bpm.minotaur.weather.WeatherType weather = worldManager.getWeatherManager().getCurrentWeather();
        if (weather != com.bpm.minotaur.weather.WeatherType.RAIN && weather != com.bpm.minotaur.weather.WeatherType.STORM) return;
        if (maze.isIndoors((int) player.getPosition().x, (int) player.getPosition().y)) return;
        com.bpm.minotaur.gamedata.item.Item skin = player.getInventory() != null
                ? com.bpm.minotaur.gamedata.item.Waterskin.findRainCatcher(player.getInventory().getAllItems()) : null;
        if (skin != null && com.bpm.minotaur.gamedata.item.Waterskin.catchRain(skin)
                && skin.getCurrentCharges() >= skin.getMaxCharges() && eventManager != null) {
            eventManager.addEvent(new GameEvent("The rain has filled your waterskin.", 2.0f));
        }
    }

    private void updateMetabolism(Player player, Maze maze, WorldManager worldManager, GameEventManager eventManager, float time) {
        PlayerStats stats = player.getStats();

        // 1. Hunger & Thirst Decay (HEATSTROKE doubles thirst decay)
        boolean isHeatstroke = stats.getBodyTemperature() > 38.0f;
        // A heavy load burns food and water faster (the Stressed and Overloaded tiers). The time
        // passed in already stretches with the slower step, so that is taken back out here and
        // only the tier's own drain factor remains.
        float load = player.getMetabolicTimeScale();
        stats.modifySatiety(-com.bpm.minotaur.gamedata.player.Hunger.burn(load, time, com.bpm.minotaur.gamedata.trait.TraitEffects.mult("hungerMult")));
        stats.modifyHydration(-com.bpm.minotaur.gamedata.player.Thirst.burn(load, time,
                (isHeatstroke ? 2.0f : 1.0f) * com.bpm.minotaur.gamedata.trait.TraitEffects.mult("thirstMult")
                        * com.bpm.minotaur.gamedata.player.Thirst.illnessMult(player.getInjuryManager() != null
                                ? player.getInjuryManager().getIllnessStage() : null)));
        refillWaterskinInRain(player, maze, worldManager, eventManager);

        // 2. Exposure (#194): air, wetness, clothing and heat move the body temperature
        updateExposure(player, maze, worldManager, eventManager, time);
        tickFieldFires(maze, eventManager);

        updateTaint(player, maze, worldManager, eventManager, time);

        player.tickForm(eventManager);
        player.tickTrait(maze, eventManager);
        player.tickPoultice(eventManager);

        // 3. Natural HP & MP Regeneration (NetHack 3-pillar model). Hunger never blocks it (#191).
        com.bpm.minotaur.gamedata.player.Hunger.Stage satState = stats.getHungerStage();
        int regenInterval = Math.max(1, Math.round(stats.getRegenIntervalTurns() * com.bpm.minotaur.gamedata.trait.TraitEffects.mult("regenMult")))
                * com.bpm.minotaur.gamedata.blight.Taint.regenIntervalMult(stats.getTaint());
        // A waterborne illness blocks it until cured (#193); thirst never does.
        boolean illnessBlocksRegen = player.getInjuryManager() != null
                && player.getInjuryManager().blocksNaturalRegen();
        // Freezing and Hypothermic stop it too (#194).
        boolean coldBlocksRegen = stats.getExposureBand().blocksRegen;
        if (!illnessBlocksRegen && !coldBlocksRegen && turnCounter % regenInterval == 0) {
            if (player.getCurrentHP() < stats.getMaxHP()) {
                stats.heal(1);
                com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordRegen(1, 0);
            }
        }
        // MP natural recovery: Paced by Wisdom when hydrated.
        int mpInterval = PlayerStats.mpRegenIntervalTurns(player.getEffectiveWisdom());
        if (stats.getHydrationFloat() > 0 && (turnCounter % mpInterval == 0)) {
            if (player.getCurrentMP() < stats.getMaxMP()) {
                player.restoreMP(1);
                com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordRegen(0, 1);
            }
        }

        // 4. Throttled hunger notices, then what the stage does (#191)
        updateHunger(player, satState, eventManager);

        // 5. Throttled thirst notices, then what the stage does (#193)
        updateThirst(player, stats.getThirstStage(), eventManager);

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
