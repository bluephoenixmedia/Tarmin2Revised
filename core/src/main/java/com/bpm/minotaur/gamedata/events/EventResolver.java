package com.bpm.minotaur.gamedata.events;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.injury.BodyPart;
import com.bpm.minotaur.gamedata.injury.InjuryType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterVariant;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.ui.UiNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Applies a choice event's choice to the player and the world: gates, costs, the check, and the
 * outcomes. Kept free of UI so every rule here is testable with a plain {@link Player}.
 *
 * <p>Effects last for the current run only. Meta-progression stays with Divinities and the
 * Shelter Altar.
 */
public class EventResolver {

    /** What a resolved choice did, for the window to show. */
    public static final class Result {
        public boolean succeeded;
        public final List<String> messages = new ArrayList<>();
        /** Set when an outcome chains into another event; the window opens it next. */
        public String chainedEventId;
    }

    private final Player player;
    private final Maze maze;
    private final GameEventManager events;
    private final ItemDataManager itemDataManager;
    private final MonsterDataManager monsterDataManager;
    private final AssetManager assetManager;
    private final Random rng;

    public EventResolver(Player player, Maze maze, GameEventManager events, ItemDataManager itemDataManager,
                         MonsterDataManager monsterDataManager, AssetManager assetManager, Random rng) {
        this.player = player;
        this.maze = maze;
        this.events = events;
        this.itemDataManager = itemDataManager;
        this.monsterDataManager = monsterDataManager;
        this.assetManager = assetManager;
        this.rng = rng != null ? rng : new Random();
    }

    // --- Availability --------------------------------------------------------------------------

    public boolean isAvailable(EventChoice choice) {
        return unmetLabel(choice) == null;
    }

    /** Why the choice cannot be taken, for its button; null when it can. */
    public String unmetLabel(EventChoice choice) {
        if (choice.requires != null) {
            for (EventGate gate : choice.requires) {
                if (!isGateMet(gate)) {
                    return gate.label != null ? gate.label : "Unavailable";
                }
            }
        }
        if (choice.costs != null) {
            for (EventOutcome cost : choice.costs) {
                if (!canAfford(cost)) {
                    return "Cannot pay";
                }
            }
        }
        return null;
    }

    boolean isGateMet(EventGate gate) {
        if (gate == null || gate.type == null) {
            return true;
        }
        switch (gate.type) {
            case ATTRIBUTE_MIN:
                return attributeValue(gate.attribute) >= gate.value;
            case HAS_ITEM:
                return countCarried(gate.itemKind, gate.itemId) >= Math.max(1, gate.count);
            case GOLD_MIN:
                return player.getTreasureScore() >= gate.value;
            default:
                return true;
        }
    }

    boolean canAfford(EventOutcome cost) {
        if (cost == null || cost.type == null) {
            return true;
        }
        PlayerStats stats = player.getStats();
        switch (cost.type) {
            case MAX_HP:
                return stats.getBaseMaxHP() + cost.amount >= 1;
            case MAX_MP:
                return stats.getMaxMP() + cost.amount >= 0;
            case GOLD:
                return stats.getTreasureScore() + cost.amount >= 0;
            case DAMAGE:
                return player.getCurrentHP() > cost.amount;
            case DRAIN_MP:
                return player.getCurrentMP() >= cost.amount;
            case SATIETY:
                return stats.getSatiety() + cost.amount >= 0;
            case HYDRATION:
                return stats.getHydration() + cost.amount >= 0;
            case TAKE_ITEM:
                return countCarried(cost.itemKind, cost.itemId) >= Math.max(1, cost.count);
            default:
                return true;
        }
    }

    // --- Odds ----------------------------------------------------------------------------------

    /** The chance this choice succeeds; 1 when it has no check. */
    public float chance(EventChoice choice) {
        if (choice.check == null) {
            return 1f;
        }
        if (choice.check.attribute == null) {
            return EventOdds.luckChance(choice.check.base, player.getLuck());
        }
        return EventOdds.chance(choice.check.base, attributeValue(choice.check.attribute), player.getLuck());
    }

    /** The player's effective attribute by its short name (STR, DEX, CON, INT, WIS, AGI, CHA). */
    public int attributeValue(String attribute) {
        if (attribute == null) {
            return 10;
        }
        switch (attribute) {
            case "STR": return player.getEffectiveStrength();
            case "DEX": return player.getEffectiveDexterity();
            case "CON": return player.getEffectiveConstitution();
            case "INT": return player.getEffectiveIntelligence();
            case "WIS": return player.getEffectiveWisdom();
            case "AGI": return player.getEffectiveAgility();
            case "CHA": return player.getEffectiveCharisma();
            default: return 10;
        }
    }

    // --- Resolution ----------------------------------------------------------------------------

    /** Pays the costs, rolls the check, and applies success or failure. */
    public Result resolve(EventChoice choice) {
        Result result = new Result();
        if (choice.costs != null) {
            for (EventOutcome cost : choice.costs) {
                apply(cost, result);
            }
        }
        result.succeeded = choice.check == null || rng.nextFloat() < chance(choice);
        List<EventOutcome> outcomes = result.succeeded ? choice.success : choice.failure;
        if (outcomes != null) {
            for (EventOutcome outcome : outcomes) {
                apply(outcome, result);
            }
        }
        return result;
    }

    void apply(EventOutcome o, Result result) {
        if (o == null) {
            return;
        }
        if (o.type == null) {
            // A line of narration and nothing else, such as what happens when you walk away.
            if (o.text != null && !o.text.isEmpty()) {
                report(o.text, result);
            }
            return;
        }
        PlayerStats stats = player.getStats();
        switch (o.type) {
            case HEAL:
                player.heal(o.amount);
                break;
            case DAMAGE:
                player.takeStatusEffectDamage(o.amount, DamageType.PHYSICAL);
                break;
            case RESTORE_MP:
                player.restoreMP(o.amount);
                break;
            case DRAIN_MP:
                player.setCurrentMP(Math.max(0, player.getCurrentMP() - o.amount));
                break;
            case MAX_HP:
                stats.setMaxHP(Math.max(1, stats.getBaseMaxHP() + o.amount));
                if (player.getCurrentHP() > player.getMaxHP()) {
                    player.setCurrentHP(player.getMaxHP());
                }
                break;
            case MAX_MP:
                stats.setMaxMP(Math.max(0, stats.getMaxMP() + o.amount));
                if (player.getCurrentMP() > stats.getMaxMP()) {
                    player.setCurrentMP(stats.getMaxMP());
                }
                break;
            case ATTRIBUTE:
                modifyAttribute(o.attribute, o.amount);
                break;
            case LUCK:
                stats.modifyLuck(o.amount);
                break;
            case ADD_STATUS:
                player.getStatusManager().addEffect(StatusEffectType.valueOf(o.status), o.duration, o.potency, false);
                break;
            case CURE_STATUS:
                player.getStatusManager().removeEffect(StatusEffectType.valueOf(o.status));
                break;
            case INJURY:
                player.getInjuryManager().inflictInjury(BodyPart.valueOf(o.bodyPart), InjuryType.valueOf(o.injury),
                        o.severity);
                break;
            case GIVE_ITEM:
                giveItems(o);
                break;
            case TAKE_ITEM:
                takeItems(o.itemKind, o.itemId, Math.max(1, o.count));
                break;
            case GOLD:
                stats.setTreasureScore(Math.max(0, stats.getTreasureScore() + o.amount));
                break;
            case SATIETY:
                stats.modifySatiety(o.amount);
                break;
            case HYDRATION:
                stats.modifyHydration(o.amount);
                break;
            case XP:
                player.addExperience(o.amount, events);
                break;
            case CURSE_ITEM:
                setRandomBeatitude(o.itemKind != null ? o.itemKind : ItemKind.ANY.name(), Item.Beatitude.CURSED);
                break;
            case BLESS_ITEM:
                setRandomBeatitude(o.itemKind != null ? o.itemKind : ItemKind.UNBLESSED.name(), Item.Beatitude.BLESSED);
                break;
            case IDENTIFY_ALL:
                for (Item item : player.getInventory().getAllItems()) {
                    item.setIdentified(true);
                }
                break;
            case SPAWN_MONSTER:
                spawnMonster(o.monsterId);
                break;
            case REVEAL_MAP:
                revealMap();
                break;
            case CHAIN_EVENT:
                result.chainedEventId = o.eventId;
                break;
            default:
                break;
        }
        if (o.text != null && !o.text.isEmpty()) {
            report(o.text, result);
        }
    }

    // --- Items ---------------------------------------------------------------------------------

    private boolean matches(Item item, String kind, String itemId) {
        if (item == null) {
            return false;
        }
        if (itemId != null) {
            return item.getType() != null && item.getType().name().equals(itemId);
        }
        return ItemKind.valueOf(kind != null ? kind : ItemKind.ANY.name()).matches(item);
    }

    /** Carried items of this kind, counting every item in a stack. */
    int countCarried(String kind, String itemId) {
        int count = 0;
        for (Item item : player.getInventory().getAllItems()) {
            if (matches(item, kind, itemId)) {
                count += Math.max(1, item.getStackCount());
            }
        }
        return count;
    }

    private void takeItems(String kind, String itemId, int count) {
        for (int taken = 0; taken < count; taken++) {
            Item match = null;
            for (Item item : player.getInventory().getAllItems()) {
                if (matches(item, kind, itemId)) {
                    match = item;
                    break;
                }
            }
            if (match == null || !player.getInventory().consumeOne(match)) {
                return;
            }
        }
    }

    private void giveItems(EventOutcome o) {
        if (itemDataManager == null) {
            return;
        }
        for (int i = 0; i < Math.max(1, o.count); i++) {
            String id = o.itemId;
            if (id == null && o.itemPool != null && !o.itemPool.isEmpty()) {
                id = o.itemPool.get(rng.nextInt(o.itemPool.size()));
            }
            if (id == null) {
                return;
            }
            int px = (int) player.getPosition().x;
            int py = (int) player.getPosition().y;
            Item item = itemDataManager.createItem(Item.ItemType.valueOf(id), px, py, null, assetManager);
            if (item == null) {
                continue;
            }
            if (!player.getInventory().pickupToBackpack(item) && maze != null) {
                maze.addItem(item);
                post("Your pack is full. It falls at your feet.");
            }
        }
    }

    private void setRandomBeatitude(String kind, Item.Beatitude beatitude) {
        List<Item> candidates = new ArrayList<>();
        for (Item item : player.getInventory().getAllItems()) {
            if (matches(item, kind, null) && item.getBeatitude() != beatitude) {
                candidates.add(item);
            }
        }
        if (!candidates.isEmpty()) {
            candidates.get(rng.nextInt(candidates.size())).setBeatitude(beatitude);
        }
    }

    // --- Player and world ----------------------------------------------------------------------

    private void modifyAttribute(String attribute, int amount) {
        PlayerStats stats = player.getStats();
        switch (attribute) {
            case "STR": stats.modifyStrength(amount); break;
            case "DEX": stats.modifyDexterity(amount); break;
            case "CON": stats.modifyConstitution(amount); break;
            case "INT": stats.modifyIntelligence(amount); break;
            case "WIS": stats.modifyWisdom(amount); break;
            case "AGI": stats.modifyAgility(amount); break;
            case "CHA": stats.modifyCharisma(amount); break;
            default: break;
        }
    }

    private void spawnMonster(String monsterId) {
        if (monsterId == null || maze == null || monsterDataManager == null) {
            return;
        }
        Monster.MonsterType type = Monster.MonsterType.valueOf(monsterId);
        int level = player.getStats().getLevel();
        MonsterVariant variant = monsterDataManager.getRandomVariantForMonster(type, level);
        if (variant == null) {
            if (Gdx.app != null) {
                Gdx.app.error("EventResolver", "No variant of " + monsterId + " to spawn");
            }
            return;
        }
        GridPoint2 tile = findOpenAdjacentTile((int) player.getPosition().x, (int) player.getPosition().y);
        if (tile == null) {
            return;
        }
        Monster monster = new Monster(type, tile.x, tile.y, variant.color, monsterDataManager, assetManager);
        monster.scaleStats(level);
        maze.addMonster(monster);
        post(UiNames.of(type) + " appears!");
    }

    /** An open tile near the player, searched ring by ring; null if none, so a spawn never lands on them. */
    private GridPoint2 findOpenAdjacentTile(int px, int py) {
        for (int radius = 1; radius <= 3; radius++) {
            List<GridPoint2> ring = new ArrayList<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) {
                        continue;
                    }
                    int tx = px + dx;
                    int ty = py + dy;
                    if (!maze.isPassable(tx, ty)) {
                        continue;
                    }
                    GridPoint2 tile = new GridPoint2(tx, ty);
                    Scenery s = maze.getScenery().get(tile);
                    if ((s != null && s.isImpassable()) || maze.getMonsters().containsKey(tile)) {
                        continue;
                    }
                    ring.add(tile);
                }
            }
            if (!ring.isEmpty()) {
                return ring.get(rng.nextInt(ring.size()));
            }
        }
        return null;
    }

    private void revealMap() {
        if (maze == null) {
            return;
        }
        for (int y = 0; y < maze.getHeight(); y++) {
            for (int x = 0; x < maze.getWidth(); x++) {
                maze.markVisited(x, y);
            }
        }
    }

    private void report(String text, Result result) {
        result.messages.add(text);
        post(text);
    }

    private void post(String text) {
        if (events != null) {
            events.addEvent(new GameEvent(text, 3f));
        }
    }
}
