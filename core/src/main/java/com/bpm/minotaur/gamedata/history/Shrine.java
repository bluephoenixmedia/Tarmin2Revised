package com.bpm.minotaur.gamedata.history;

import com.bpm.minotaur.gamedata.encounters.Encounter;
import com.bpm.minotaur.gamedata.encounters.EncounterChoice;

import java.util.ArrayList;

/**
 * A statue encounter as its old god's shrine (#287): the god named with its Kindred, and its rites
 * retitled to the god with their effects untouched. A dark shrine, its god spent holding Tarmin
 * back, offers nothing but the way out. The loaded encounter is never changed.
 */
public final class Shrine {

    private Shrine() {
    }

    public static Encounter view(Encounter base, OldGod god, boolean dark) {
        Encounter view = new Encounter();
        view.id = base.id;
        view.title = base.title;
        view.imagePath = base.imagePath;
        view.minDepth = base.minDepth;
        view.maxDepth = base.maxDepth;
        view.choices = new ArrayList<>();
        if (dark) {
            String shrine = base.title.startsWith("The ") ? base.title : "The " + base.title;
            view.text = shrine + " stands dark. " + capital(god.fullName) + " is spent, and nothing answers.";
            view.choices.add(wayOut(base));
            return view;
        }
        view.text = base.text + "\n\nThis is the shrine of " + god.fullName + ", of " + god.domain + ". "
                + god.kindred.title + ": " + god.kindred.fear + ".";
        for (EncounterChoice choice : base.choices) {
            EncounterChoice rite = copy(choice);
            if (!isLeave(choice)) rite.text = god.riteOwner() + ": " + choice.text;
            view.choices.add(rite);
        }
        return view;
    }

    /** Walking away: no check, no cost, no effect. */
    public static boolean isLeave(EncounterChoice c) {
        return c.type == EncounterChoice.CheckType.LEAVE && c.requirement == null && c.cost == null
                && c.successEffect == null && c.failEffect == null;
    }

    private static EncounterChoice wayOut(Encounter base) {
        for (EncounterChoice c : base.choices) {
            if (isLeave(c)) return copy(c);
        }
        EncounterChoice leave = new EncounterChoice();
        leave.text = "Leave the dark shrine.";
        return leave;
    }

    private static EncounterChoice copy(EncounterChoice c) {
        EncounterChoice copy = new EncounterChoice();
        copy.text = c.text;
        copy.type = c.type;
        copy.successChance = c.successChance;
        copy.requirement = c.requirement;
        copy.cost = c.cost;
        copy.successEffect = c.successEffect;
        copy.failEffect = c.failEffect;
        copy.monsterId = c.monsterId;
        return copy;
    }

    private static String capital(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
