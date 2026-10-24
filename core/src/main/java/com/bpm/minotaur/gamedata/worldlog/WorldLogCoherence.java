package com.bpm.minotaur.gamedata.worldlog;

import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.Nemesis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks that a World Log and the history agree with themselves (#269, #286): what one record or
 * Telling says of a life, no other contradicts. Returns the disagreements in words; none is good.
 * Bridges and Ages join the check when they exist.
 */
public final class WorldLogCoherence {

    private WorldLogCoherence() {
    }

    public static List<String> check(WorldLog log, HistoryWorld world) {
        List<String> problems = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Map<String, List<String>> felledBy = new HashMap<>();
        for (LifeRecord life : log.lives()) {
            if (!names.add(life.name)) problems.add(life.name + " is the name of two lives");
            if (life.isDead() && life.killer != null) {
                felledBy.computeIfAbsent(life.killer, k -> new ArrayList<>()).add(life.name);
            }
            String home = life.homeName();
            String nth = Tellings.ordinal(life.number);
            for (Telling t : life.tellings) {
                for (String text : new String[]{t.plain, t.mythic}) {
                    // A name that starts a sentence is capitalised there ("The Hooded Tam"): same name.
                    String lower = text == null ? "" : text.toLowerCase();
                    if (text == null || !lower.contains(life.name.toLowerCase()) || !lower.contains(home.toLowerCase())
                            || !text.contains(nth)) {
                        problems.add(t.teller + " tells life " + life.number + " (" + life.name + " of " + home
                                + ", the " + nth + ") as: " + text);
                    }
                }
            }
        }

        Set<String> nemesisNames = new HashSet<>();
        for (Nemesis n : world.nemeses()) {
            if (!nemesisNames.add(n.name)) problems.add("two Nemeses are named " + n.name);
            if (names.contains(n.name)) problems.add(n.name + " is the name of a Nemesis and of a life");
            List<String> felled = felledBy.getOrDefault(n.name, new ArrayList<>());
            if (!felled.equals(n.victims)) {
                for (String life : felled) {
                    if (!n.victims.contains(life)) {
                        problems.add(life + " fell to " + n.name + ", but " + n.name + " never took that life");
                    }
                }
                if (n.tally() != felled.size()) {
                    problems.add(n.name + " counts " + n.tally() + " lives taken, the log " + felled.size());
                }
            }
        }

        // Every Age but the one being lived has ended, and says how; every life stands in one (#291).
        Map<Integer, Integer> inAges = new HashMap<>();
        for (Age age : log.ages()) {
            if (age.index < log.currentAge() && age.reckoning == null) {
                problems.add(age.name + " ended, but no Reckoning tells how");
            }
            for (Bridge bridge : age.bridges) {
                for (Era era : bridge.eras) {
                    for (LifeRecord life : era.lives) inAges.merge(life.number, 1, Integer::sum);
                }
            }
        }
        for (LifeRecord life : log.lives()) {
            int count = inAges.getOrDefault(life.number, 0);
            if (count != 1) problems.add("life " + life.number + " stands in " + count + " Ages, not one");
        }

        // Every Bridge but the one being built has ended, and says how (#290).
        Map<Integer, Integer> inBridges = new HashMap<>();
        for (Bridge bridge : log.bridges()) {
            if (bridge.index < log.currentBridge() && bridge.reckoning == null) {
                problems.add(bridge.name + " ended, but no Reckoning tells how");
            }
            // A Bridge ends by its own Reckoning, or by its Age's, which tells of the Age instead.
            String tellsOf = bridge.reckoning != null && bridge.reckoning.end == Reckoning.End.AGE_ENDED ? "age" : "bridge";
            if (bridge.reckoning != null && (bridge.reckoning.plain == null || !bridge.reckoning.plain.toLowerCase().contains(tellsOf))) {
                problems.add(bridge.name + "'s Reckoning does not tell of the Bridge: " + bridge.reckoning.plain);
            }
            for (Era era : bridge.eras) {
                for (LifeRecord life : era.lives) inBridges.merge(life.number, 1, Integer::sum);
            }
        }
        for (LifeRecord life : log.lives()) {
            int count = inBridges.getOrDefault(life.number, 0);
            if (count != 1) problems.add("life " + life.number + " stands in " + count + " Bridges, not one");
        }

        Map<Integer, Integer> inEras = new HashMap<>();
        for (Era era : log.eras()) {
            for (LifeRecord life : era.lives) inEras.merge(life.number, 1, Integer::sum);
        }
        for (LifeRecord life : log.lives()) {
            int count = inEras.getOrDefault(life.number, 0);
            if (count != 1) problems.add("life " + life.number + " stands in " + count + " Eras, not one");
        }
        return problems;
    }
}
