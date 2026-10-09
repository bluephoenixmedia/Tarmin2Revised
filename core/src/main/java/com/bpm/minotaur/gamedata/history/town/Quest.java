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
                break;
        }
        return q;
    }

    /** What the reeve asks, in their words. */
    public String ask(HistoryWorld world, Town town, java.util.function.Function<String, Town> towns) {
        switch (kind) {
            case RECOVER_SIGNET:
                return "A signet of " + house(world) + " would buy us a season's peace with the Maze. Bring me one, from its broken dead.";
            case UNMASK_AGENT:
                return "Someone in " + town.name + " carries word to " + house(world) + ". Find them. Name them to their face.";
            case SLAY_BEAST:
                Megabeast b = world.megabeast(beastId);
                return (b != null ? b.name : "The beast below") + " has taken our hunters. Kill it, and the town will owe you.";
            case CARRY_MESSAGE:
                Town to = towns.apply(toTownKey);
                return "Carry this to the elder of " + (to != null ? to.name : "the next town") + ". The roads above are war now; go under them.";
            default:
                return "";
        }
    }

    private String house(HistoryWorld world) {
        House h = world.house(houseId);
        return h != null ? h.name : "a house of the Maze";
    }
}
