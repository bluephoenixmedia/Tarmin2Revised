package com.bpm.minotaur.gamedata.polymorph;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.MagicResistance;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.PotionEffectType;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.monster.MonsterVariant;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.ui.UiNames;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The one polymorph engine behind the wand, the scroll and the spells: it changes a monster into
 * another monster, an item into another of its kind, or the player into a monster's body.
 */
public final class PolymorphEngine {

    private static final Random RNG = new Random();

    private PolymorphEngine() {
    }

    // ------------------------------------------------------------------ monsters

    /** Why this monster cannot be polymorphed, or null if it can. */
    public static String monsterRefusal(Monster target) {
        if (target.isBridgeBoss()) {
            return "it is beyond your power";
        }
        return null;
    }

    /**
     * Turns a monster into another, keeping its share of hit points. Magic Resistance can throw it
     * off, a charm does not survive it, and the original is replaced in the maze.
     *
     * @return true when the monster changed
     */
    public static boolean polymorphMonster(Monster target, Maze maze, Player player, CombatManager combat,
            GameEventManager events, boolean anyLevel) {
        MonsterDataManager data = player.getMonsterDataManager();
        if (target == null || maze == null || data == null) {
            return false;
        }
        String who = UiNames.of(target.getType());
        String refusal = monsterRefusal(target);
        if (refusal != null) {
            say(events, "The " + who + " cannot be changed: " + refusal + ".");
            return false;
        }
        if (MagicResistance.resists(target.getMagicResistance())) {
            say(events, "The " + who + " shrugs off the transformation!");
            return false;
        }
        Monster.MonsterType next = PolymorphRules.pickMonsterType(baseLevels(data, player.getAssetManager()), target.getType(),
                target.getLevel(), anyLevel, RNG);
        if (next == null) {
            say(events, "The " + who + " twists, then settles back as it was.");
            return false;
        }
        MonsterVariant variant = data.getRandomVariantForMonster(next, Math.max(1, target.getLevel()));
        if (variant == null) {
            say(events, "The " + who + " twists, then settles back as it was.");
            return false;
        }

        GridPoint2 at = new GridPoint2((int) target.getPosition().x, (int) target.getPosition().y);
        float share = target.getMaxHP() > 0 ? target.getCurrentHP() / (float) target.getMaxHP() : 1f;
        Monster made = new Monster(next, at.x, at.y, variant.color, data, player.getAssetManager());
        made.scaleStats(Math.max(1, target.getLevel()));
        made.setCurrentHP(Math.max(1, Math.round(made.getMaxHP() * share)));

        if (combat != null && combat.getMonster() == target) {
            combat.endCombat();
        }
        maze.getMonsters().remove(at);
        maze.getMonsters().put(at, made);
        say(events, "The " + who + " becomes a " + UiNames.of(next) + "!");
        return true;
    }

    /** Things polymorph must never produce: the world's bosses and things that are not real monsters. */
    private static final java.util.Set<Monster.MonsterType> NEVER_MADE = java.util.EnumSet.of(
            Monster.MonsterType.BRINGER_OF_DEATH, Monster.MonsterType.MIMIC);

    private static Map<Monster.MonsterType, Integer> baseLevels(MonsterDataManager data, AssetManager assets) {
        Map<Monster.MonsterType, Integer> levels = new EnumMap<>(Monster.MonsterType.class);
        for (Monster.MonsterType type : data.getLoadedTypes()) {
            if (NEVER_MADE.contains(type) || type.name().startsWith("PLAYER_")) {
                continue;
            }
            MonsterTemplate t = data.getTemplate(type);
            // A monster whose picture is not loaded would come out invisible, so it is never offered.
            if (t != null && hasLoadedPicture(t, assets)) {
                levels.put(type, t.baseLevel);
            }
        }
        return levels;
    }

    private static boolean hasLoadedPicture(MonsterTemplate t, AssetManager assets) {
        if (assets == null) {
            return true; // nothing to check against (headless)
        }
        if (t.texturePath != null && !t.texturePath.isEmpty() && assets.isLoaded(t.texturePath, Texture.class)) {
            return true;
        }
        if (t.variants != null) {
            for (MonsterVariant v : t.variants) {
                if (v != null && v.texturePath != null && assets.isLoaded(v.texturePath, Texture.class)) {
                    return true;
                }
            }
        }
        return t.directionTextures != null;
    }

    // ------------------------------------------------------------------ items

    /**
     * Turns the item lying on a tile into another of its kind. A potion keeps its look but takes a
     * new effect. Scrolls, wands, rings, keys, containers and treasure resist.
     *
     * @return true when the item changed
     */
    public static boolean polymorphItemAt(Maze maze, GridPoint2 tile, Player player, GameEventManager events) {
        Item old = maze == null || tile == null ? null : maze.getItems().get(tile);
        ItemDataManager data = player.getItemDataManager();
        if (old == null || data == null) {
            return false;
        }
        String name = com.bpm.minotaur.gamedata.item.ItemName.natural(old.getFriendlyName());

        if (old.isPotion()) {
            PotionEffectType[] all = PotionEffectType.values();
            PotionEffectType now = old.getTrueEffect();
            PotionEffectType next = all[RNG.nextInt(all.length)];
            if (next == now) {
                next = all[(next.ordinal() + 1) % all.length];
            }
            old.setTrueEffect(next);
            say(events, "The " + name + " swirls and shifts colour.");
            return true;
        }

        Map<Item.ItemType, String> categories = new EnumMap<>(Item.ItemType.class);
        for (Item.ItemType type : data.getLoadedTypes()) {
            ItemTemplate t = data.getTemplate(type);
            categories.put(type, PolymorphRules.categoryOf(t));
        }
        List<Item.ItemType> options = PolymorphRules.sameCategory(categories, old.getType());
        if (options.isEmpty()) {
            say(events, "The " + name + " shivers, but nothing changes.");
            return false;
        }
        Item.ItemType next = options.get(RNG.nextInt(options.size()));
        ItemColor color = old.getItemColor() != null ? old.getItemColor() : ItemColor.GRAY;
        Item made = data.createItem(next, tile.x, tile.y, color, player.getAssetManager());
        if (made == null) {
            return false;
        }
        made.setBeatitude(old.getBeatitude());
        made.getPosition().set(tile.x + 0.5f, tile.y + 0.5f);
        maze.getItems().put(tile, made);
        say(events, "The " + name + " changes into " + com.bpm.minotaur.gamedata.item.ItemName.natural(made.getFriendlyName()) + "!");
        return true;
    }

    // ------------------------------------------------------------------ the player

    /**
     * Turns the player into a monster for 100 to 300 turns. A constitution check can go badly
     * (system shock): it costs 1d10 hit points and leaves a weak form. A form too big for armour
     * bursts the body armour.
     *
     * @return true when the player changed
     */
    public static boolean polymorphSelf(Player player, GameEventManager events, boolean anyLevel) {
        MonsterDataManager data = player.getMonsterDataManager();
        if (data == null) {
            return false;
        }
        if (player.isPolymorphed()) {
            say(events, "You are already in another body.");
            return false;
        }
        int level = Math.max(1, player.getStats().getLevel());
        boolean shock = RNG.nextInt(20) + 1 + player.getStats().getConModifier() < 10;
        if (shock) {
            int dmg = 1 + RNG.nextInt(10);
            say(events, "System shock! The change tears at you for " + dmg + " damage.");
            player.getStats().setWarStrength(Math.max(1, player.getStats().getWarStrength() - dmg));
        }
        Monster.MonsterType next = PolymorphRules.pickMonsterType(baseLevels(data, player.getAssetManager()), null,
                shock ? Math.max(1, level - 2) : level, anyLevel, RNG);
        if (next == null) {
            say(events, "The magic fizzles.");
            return false;
        }
        MonsterTemplate t = data.getTemplate(next);
        PlayerForm form = PlayerForm.of(UiNames.of(next), t, PlayerForm.randomTurns(RNG));
        player.enterForm(form);
        say(events, "You turn into a " + form.name() + "!");
        if (form.burstsArmor() && player.getEquipment().getWornChest() != null) {
            player.getEquipment().setWornChest(null);
            say(events, "Your body armour bursts apart!");
        }
        if (!form.hasHands()) {
            say(events, "You have no hands: you cannot cast spells or use items.");
        }
        return true;
    }

    private static void say(GameEventManager events, String text) {
        if (events != null) {
            events.addEvent(new GameEvent(text, 2.5f));
        }
    }
}
