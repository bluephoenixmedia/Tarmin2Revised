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

    /** Talk: a greeting that knows who they are and what they think of the player. */
    public String greet() {
        if (hostile()) return clean(folk.name + " will not look at you. \"Get out of " + town.name + ".\"");
        Random rng = new Random(town.key.hashCode() * 31L + folk.index + history.world().liveSeasons());
        if (folk.role == Town.Role.EXILE) return clean(folk.name + ": \"" + exileLine(rng) + "\"");
        String[] lines;
        switch (folk.role) {
            case SMITH: lines = new String[]{"Steel keeps. Everything else rusts.", "Bring me iron from the dead and I will make it sing."}; break;
            case INNKEEPER: lines = new String[]{"Sit. Drink. Nobody asks names under the ground.", "The beds are hard and the walls are thick. That is the whole of the welcome."}; break;
            case QUESTGIVER: lines = new String[]{"There is always work for someone who comes back.", "You have the look of someone the Maze has not finished with."}; break;
            case ELDER: lines = new String[]{"We were farmers once, before the gashes. Now we are moles.", "Every year the houses come closer. Every year we dig deeper."}; break;
            default: lines = new String[]{"Mind the dark past the lamps.", "You came from up there? Gods."}; break;
        }
        return clean(folk.name + ": \"" + lines[rng.nextInt(lines.length)] + "\"");
    }

    /** An exile speaks of the house they served, which the history broke. */
    private String exileLine(Random rng) {
        com.bpm.minotaur.gamedata.history.Figure me = history.world().figure(folk.figureId);
        com.bpm.minotaur.gamedata.history.House served = me != null ? history.world().house(me.houseId) : null;
        String house = served != null ? served.name : "a house that is gone";
        String[] lines = {
                "I was sworn to " + house + ". The blood I served is ash, and I am still here. Make of that what you like.",
                "Ask the chronicles what became of " + house + ". I was there. I would rather not tell it.",
                "The mortals let me sit by their fire. " + house + " never once did that."
        };
        return lines[rng.nextInt(lines.length)];
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
        if (out.isEmpty()) out.add(clean(folk.name + ": \"Quiet seasons. I do not trust them.\""));
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
            finish(delivery, history.town(delivery.townKey));
            return clean(folk.name + " reads the message twice and burns it. \"Tell them it is done.\"");
        }
        Quest q = reevesTask();
        if (q == null) return clean(folk.name + ": \"Nothing for you. Ask the reeve.\"");
        String asked = q.ask(history.world(), town, history::town);
        if (!q.accepted) {
            history.acceptQuest(q);
            return clean(folk.name + ": \"" + asked + "\"");
        }
        switch (q.kind) {
            case RECOVER_SIGNET:
                if (pack.giveSignet(q.houseId)) {
                    finish(q, town);
                    return clean(folk.name + " turns the signet over in their fingers. \"This will do.\"");
                }
                break;
            case SLAY_BEAST:
                Megabeast b = history.world().megabeast(q.beastId);
                if (b == null || !b.isAlive()) {
                    finish(q, town);
                    return clean(folk.name + ": \"We heard it die. The whole deep heard it.\"");
                }
                if (b.isPacified()) {
                    finish(q, town);
                    return clean(folk.name + ": \"It has not come for us since you fed it. Strange coin, but it spends.\"");
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
            return clean(folk.name + " goes white, then runs. The town catches them at the gate.");
        }
        history.standing().change(town, -Standing.FAVOUR);
        return clean(folk.name + " is innocent, and " + town.name + " will remember you said otherwise.");
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

    private static String clean(String s) {
        return UiGlyphs.sanitize(s);
    }
}
