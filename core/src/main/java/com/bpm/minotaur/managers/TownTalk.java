package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.Megabeast;
import com.bpm.minotaur.gamedata.history.text.ChronicleGrammar;
import com.bpm.minotaur.gamedata.history.text.Headlines;
import com.bpm.minotaur.gamedata.history.town.Quest;
import com.bpm.minotaur.gamedata.history.town.Standing;
import com.bpm.minotaur.gamedata.history.town.Town;
import com.bpm.minotaur.ui.UiGlyphs;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A conversation with one of a town's folk (plan D38, T4.3): Talk, Trade, Rumours, Quest, Leave.
 * Everything said comes from the history -- the season's news as rumour, the reeve's work from
 * what the houses and beasts are doing -- so it can be tested without a screen.
 */
public final class TownTalk {

    /** What the town needs from the game it cannot see itself: the player's pack. */
    public interface Pack {
        /** Whether the pack holds a signet taken from house {@code houseId}. */
        boolean hasSignet(int houseId);

        /** Hands over a signet of house {@code houseId}; true if there was one. */
        boolean giveSignet(int houseId);

        /** The town's thanks, made real. */
        void reward(String what);
    }

    public final Town town;
    public final Town.Folk folk;
    private final HistoryManager history;
    private final DoctrineCatalog catalog;
    private final ChronicleGrammar grammar;
    private final List<String> otherTowns;
    private final Pack pack;

    public TownTalk(HistoryManager history, Town town, Town.Folk folk, List<String> otherTowns,
            DoctrineCatalog catalog, ChronicleGrammar grammar, Pack pack) {
        this.history = history;
        this.town = town;
        this.folk = folk;
        this.otherTowns = otherTowns;
        this.catalog = catalog;
        this.grammar = grammar;
        this.pack = pack;
    }

    public String header() {
        return clean(folk.title() + " of " + town.name + ", " + town.allegiance.displayName);
    }

    public String standingWord() {
        return history.standing().word(town);
    }

    public boolean hostile() {
        return history.standing().isHostile(town);
    }

    /**
     * Talk: a greeting from the chronicle grammar (plan T4.3) that knows who they are, what they
     * think of the player, and what the houses are doing above.
     */
    public String greet() {
        Random rng = new Random(town.key.hashCode() * 31L + folk.index + history.world().liveSeasons());
        if (hostile()) {
            return clean(folk.name + " will not look at you. \"" + fill(pick(ChronicleGrammar.TALK_HOSTILE, rng), rng) + "\"");
        }
        return clean(folk.name + ": \"" + fill(pick(folk.role.name(), rng), rng) + "\"");
    }

    private String pick(String key, Random rng) {
        List<String> lines = grammar != null ? grammar.talkLines(key) : java.util.Collections.<String>emptyList();
        return lines.isEmpty() ? "..." : lines.get(rng.nextInt(lines.size()));
    }

    private String fill(String line, Random rng) {
        HistoryWorld w = history.world();
        String war = "the houses";
        List<com.bpm.minotaur.gamedata.history.War> wars = w.activeWars();
        if (!wars.isEmpty()) {
            com.bpm.minotaur.gamedata.history.War at = wars.get(rng.nextInt(wars.size()));
            com.bpm.minotaur.gamedata.history.House a = w.house(at.attackerId);
            com.bpm.minotaur.gamedata.history.House b = w.house(at.defenderId);
            if (a != null && b != null) war = a.name + " and " + b.name;
        }
        com.bpm.minotaur.gamedata.history.Figure me = folk.figureId >= 0 ? w.figure(folk.figureId) : null;
        com.bpm.minotaur.gamedata.history.House served = me != null ? w.house(me.houseId) : null;
        // Who held this seat before them, as the history remembers it (ADR 0005).
        com.bpm.minotaur.gamedata.history.town.Mortal keeper = w.mortal(folk.mortalId);
        com.bpm.minotaur.gamedata.history.town.Mortal before = keeper != null ? w.mortal(keeper.predecessorId) : null;
        String allegiance = town.allegiance.displayName;
        return line.replace("{town}", town.name)
                .replace("{allegiance}", Character.toUpperCase(allegiance.charAt(0)) + allegiance.substring(1))
                .replace("{house}", served != null ? served.name : "a house that is gone")
                .replace("{war}", war)
                .replace("{predecessor}", before != null ? before.name : "the founders");
    }

    /** Rumours: the loudest news of the last few seasons, as it travels down here. */
    public List<String> rumours() {
        HistoryWorld w = history.world();
        List<HistoryEvent> recent = new ArrayList<>();
        int since = w.season() - RUMOUR_SEASONS;
        for (int i = w.events().size() - 1; i >= 0 && recent.size() < 60; i--) {
            HistoryEvent e = w.events().get(i);
            if (e.season < since) break;
            recent.add(0, e);
        }
        List<String> out = new ArrayList<>();
        for (String line : Headlines.of(w, recent, RUMOURS, grammar, catalog)) out.add(clean(line));
        if (out.isEmpty()) out.add(say("QUIET_SEASONS"));
        return out;
    }

    static final int RUMOUR_SEASONS = 8;
    static final int RUMOURS = 3;

    // ------------------------------------------------------------------ quests

    /** The reeve's task here, offered or under way; null for anyone else, or once done. */
    public Quest reevesTask() {
        if (folk.role != Town.Role.QUESTGIVER) return null;
        Quest q = history.quest(town.key);
        if (q == null) q = Quest.offer(history.world(), town, otherTowns);
        return q.done ? null : q;
    }

    /** Whether there is quest business with this person at all. */
    public boolean hasQuestBusiness() {
        return reevesTask() != null || deliveryHere() != null;
    }

    /** Quest: offer, accept, report progress, or hand in. Returns what is said. */
    public String quest() {
        Quest delivery = deliveryHere();
        if (delivery != null) {
            if (!delivery.deliverable(history.world())) {
                return say("DELIVERY_REFUSED");
            }
            finish(delivery, history.town(delivery.townKey));
            return say("DELIVERED");
        }
        Quest q = reevesTask();
        if (q == null) return say("NOTHING_HERE");
        String asked = q.ask(history.world(), town, history::town, grammar);
        if (!q.accepted) {
            history.acceptQuest(q);
            return clean(folk.name + ": \"" + asked + "\"");
        }
        switch (q.kind) {
            case RECOVER_SIGNET:
                if (pack.giveSignet(q.houseId)) {
                    finish(q, town);
                    return say("SIGNET_TAKEN");
                }
                break;
            case SLAY_BEAST:
                Megabeast b = history.world().megabeast(q.beastId);
                if (b == null || !b.isAlive()) {
                    finish(q, town);
                    return say("BEAST_DEAD");
                }
                if (b.isPacified()) {
                    finish(q, town);
                    return say("BEAST_PACIFIED");
                }
                break;
            default:
                break;
        }
        return clean(folk.name + ": \"" + asked + "\"");
    }

    /** Whether the player could accuse this person of serving a house. */
    public boolean canAccuse() {
        Quest q = history.quest(town.key);
        return q != null && q.accepted && !q.done && q.kind == Quest.Kind.UNMASK_AGENT && folk.role != Town.Role.QUESTGIVER
                && folk.role != Town.Role.MERCHANT && folk.role != Town.Role.EXILE;
    }

    /** Accuse: right, and the town is grateful; wrong, and it is not. */
    public String accuse() {
        Quest q = history.quest(town.key);
        if (q == null || !canAccuse()) return "";
        if (q.agentIndex == folk.index) {
            finish(q, town);
            return say("ACCUSED_RIGHT");
        }
        history.standing().change(town, -Standing.FAVOUR);
        return say("ACCUSED_WRONG");
    }

    /** A message being carried to this elder, if any. */
    private Quest deliveryHere() {
        if (folk.role != Town.Role.ELDER) return null;
        for (Quest q : history.quests()) {
            if (q.accepted && !q.done && q.kind == Quest.Kind.CARRY_MESSAGE && town.key.equals(q.toTownKey)) return q;
        }
        return null;
    }

    private void finish(Quest q, Town giver) {
        history.completeQuest(q, giver);
        history.standing().change(giver, Standing.FAVOUR);
        pack.reward(giver.name);
    }

    /** A town saying from the grammar (plan T4.3), spoken by this person in this town. */
    private String say(String key) {
        java.util.Map<String, String> slots = new java.util.HashMap<>();
        slots.put("{name}", folk.name);
        slots.put("{town}", town.name);
        Random rng = new Random(town.key.hashCode() * 17L + folk.index + key.hashCode() + history.world().liveSeasons());
        return clean(grammar.say(key, slots, rng));
    }

    private static String clean(String s) {
        return UiGlyphs.sanitize(s);
    }
}
