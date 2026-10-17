package com.bpm.minotaur.gamedata.history.town;

import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.Megabeast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A task a town's reeve gives the player, drawn from the history (plan D39, T4.5): a signet to
 * recover from a broken house, a house's agent to unmask among the townsfolk, a megabeast to
 * kill, a message to carry to another town.
 */
public class Quest {

    public enum Kind { RECOVER_SIGNET, UNMASK_AGENT, SLAY_BEAST, CARRY_MESSAGE }

    public Kind kind;
    /** Where it was given. */
    public String townKey;
    /** RECOVER_SIGNET / UNMASK_AGENT: the house concerned. */
    public int houseId = -1;
    /** SLAY_BEAST: the beast. */
    public int beastId = -1;
    /** UNMASK_AGENT: the folk index of the agent. */
    public int agentIndex = -1;
    /** CARRY_MESSAGE: the town to carry it to. */
    public String toTownKey;
    /**
     * CARRY_MESSAGE in wartime: the war whose front it must be carried through on the surface (plan
     * D39), or -1 when no war was under way and the deep roads serve.
     */
    public int warId = -1;
    /** CARRY_MESSAGE: the player has stood on that war's front with it. */
    public boolean crossedFront;
    public boolean accepted;
    public boolean done;

    /** For the save reader. */
    public Quest() {
    }

    /**
     * The task a town's reeve has for the player, or null if the history gives it none. The same
     * town in the same history always offers the same task.
     */
    public static Quest offer(HistoryWorld world, Town town, List<String> otherTownKeys) {
        Random rng = new Random(world.seed ^ (town.key.hashCode() * 0x2545F4914F6CDD1DL) ^ 0x9E57L);
        List<Kind> kinds = new ArrayList<>();
        List<House> houses = world.livingHouses();
        List<Megabeast> beasts = new ArrayList<>();
        for (Megabeast b : world.megabeasts()) if (b.isAlive() && b.isAwake(world.season())) beasts.add(b);
        kinds.add(Kind.RECOVER_SIGNET);
        if (town.folk.size() > 2) kinds.add(Kind.UNMASK_AGENT);
        if (!beasts.isEmpty()) kinds.add(Kind.SLAY_BEAST);
        if (otherTownKeys != null && !otherTownKeys.isEmpty()) kinds.add(Kind.CARRY_MESSAGE);

        Quest q = new Quest();
        q.townKey = town.key;
        q.kind = kinds.get(rng.nextInt(kinds.size()));
        switch (q.kind) {
            case RECOVER_SIGNET:
                q.houseId = houses.get(rng.nextInt(houses.size())).id;
                break;
            case UNMASK_AGENT:
                q.houseId = houses.get(rng.nextInt(houses.size())).id;
                // Never the reeve who asks, and never the merchant, who answers to no one.
                List<Integer> suspects = new ArrayList<>();
                for (Town.Folk f : town.folk) {
                    if (f.role != Town.Role.QUESTGIVER && f.role != Town.Role.MERCHANT && f.role != Town.Role.EXILE) {
                        suspects.add(f.index);
                    }
                }
                q.agentIndex = suspects.get(rng.nextInt(suspects.size()));
                break;
            case SLAY_BEAST:
                q.beastId = beasts.get(rng.nextInt(beasts.size())).id;
                break;
            case CARRY_MESSAGE:
                q.toTownKey = otherTownKeys.get(rng.nextInt(otherTownKeys.size()));
                List<com.bpm.minotaur.gamedata.history.War> wars = world.activeWars();
                if (!wars.isEmpty()) q.warId = wars.get(rng.nextInt(wars.size())).id;
                break;
        }
        return q;
    }

    /**
     * What the reeve asks, in their words, from the chronicle grammar's town sayings (plan T4.3).
     * The same task is always asked the same way.
     */
    public String ask(HistoryWorld world, Town town, java.util.function.Function<String, Town> towns,
            com.bpm.minotaur.gamedata.history.text.ChronicleGrammar grammar) {
        java.util.Map<String, String> slots = new java.util.HashMap<>();
        slots.put("{town}", town.name);
        slots.put("{house}", house(world));
        String key = "ASK_" + kind.name();
        switch (kind) {
            case SLAY_BEAST:
                Megabeast b = world.megabeast(beastId);
                slots.put("{beast}", b != null ? b.name : "The beast below");
                break;
            case CARRY_MESSAGE:
                Town to = towns.apply(toTownKey);
                slots.put("{destination}", to != null ? to.name : "the next town");
                com.bpm.minotaur.gamedata.history.War war = war(world);
                key += war == null ? "_PEACE" : "_WAR";
                if (war != null) {
                    slots.put("{attacker}", name(world, war.attackerId));
                    slots.put("{defender}", name(world, war.defenderId));
                }
                break;
            default:
                break;
        }
        return grammar.say(key, slots, new Random(townKey.hashCode() * 31L + kind.ordinal()));
    }

    /** The war this message must cross, while it is still being fought; null otherwise. */
    public com.bpm.minotaur.gamedata.history.War war(HistoryWorld world) {
        if (warId < 0) return null;
        for (com.bpm.minotaur.gamedata.history.War w : world.wars()) if (w.id == warId && w.isActive()) return w;
        return null;
    }

    /** Whether the message may be handed over: no war to cross, crossed it, or the war is over. */
    public boolean deliverable(HistoryWorld world) {
        return crossedFront || war(world) == null;
    }

    private static String name(HistoryWorld world, int houseId) {
        House h = world.house(houseId);
        return h != null ? h.name : "a house of the Maze";
    }

    private String house(HistoryWorld world) {
        House h = world.house(houseId);
        return h != null ? h.name : "a house of the Maze";
    }
}
