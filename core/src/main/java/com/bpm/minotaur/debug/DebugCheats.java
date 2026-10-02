package com.bpm.minotaur.debug;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ShelterChest;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.managers.GameEventManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * What the debug-mode keys actually do, kept apart from the key handling so each can be tested.
 * None of these is reachable unless debug mode (F5) is on; see {@link DebugKeys}.
 */
public final class DebugCheats {

    private DebugCheats() {
    }

    /** Teaches the player every spell id; returns how many were new to them. */
    public static int learnAllSpells(Player player, Collection<String> spellIds) {
        int learned = 0;
        for (String id : spellIds) {
            if (id != null && !player.getKnownSpellIds().contains(id.toUpperCase(java.util.Locale.ROOT))) {
                player.learnPermanentSpellId(id);
                learned++;
            }
        }
        return learned;
    }

    /** Gives exactly the experience for one level, through the normal path so the level-up modal and sounds fire. */
    public static void levelUp(Player player, GameEventManager events) {
        PlayerStats stats = player.getStats();
        int needed = Math.max(1, stats.getExperienceToNextLevel() - stats.getExperience());
        player.addExperience(needed, events);
    }

    /** Full health, mana, food and water. */
    public static void refill(Player player) {
        PlayerStats stats = player.getStats();
        player.heal(stats.getMaxHP());
        player.restoreMP(stats.getMaxMP());
        stats.setSatiety(PlayerStats.STARTING_SATIETY);
        stats.setHydration(PlayerStats.MAX_HYDRATION);
    }

    /** A catalog chest with one of every item type; taking from it never empties it or touches the real stash. */
    public static ShelterChest allItemsChest(Collection<Item.ItemType> types, Function<Item.ItemType, Item> factory) {
        List<Item.ItemType> list = new ArrayList<>(types);
        return ShelterChest.catalog(list, factory);
    }
}
