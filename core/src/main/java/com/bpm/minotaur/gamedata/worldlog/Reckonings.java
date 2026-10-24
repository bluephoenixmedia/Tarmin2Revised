package com.bpm.minotaur.gamedata.worldlog;

import com.bpm.minotaur.gamedata.history.OldGod;

/**
 * Writes the Knell's Reckonings (#290) from what happened: the Bridge, the life that ended it, the
 * old god spent and the House that rose while Tarmin was gone. ASCII only (UiGlyphs).
 */
public final class Reckonings {

    private Reckonings() {
    }

    /** The Warden fell to {@code slayer}, and its Bridge with it. */
    public static Reckoning wardenFell(String bridge, LifeRecord slayer) {
        String who = nameOf(slayer);
        return new Reckoning(Reckoning.End.WARDEN_FELL,
                capital(bridge) + " ended when " + who + " destroyed its Warden, and the Bridge crumbled back to nothing.",
                "The Warden of " + bridge + " fell to " + who + ". Every stone of the Bridge fell after it, into the dark.");
    }

    /** Tarmin crossed; {@code god} was spent dragging him back; {@code rose} took hell's ground meanwhile. */
    public static Reckoning crossed(String bridge, OldGod god, String rose, LifeRecord last) {
        return new Reckoning(Reckoning.End.CROSSED,
                capital(bridge) + " reached the far shore when " + nameOf(last) + " died, and Tarmin crossed. He laid waste to "
                        + "the world above until " + god.fullName + " gave itself to drag him back. " + capital(rose)
                        + " rose in the Maze while he was gone.",
                "Tarmin walked the world above, and the sky burned. " + capital(god.fullName)
                        + " held him and was spent, and its shrines are dark. Below, " + rose
                        + " took what he had left. A new Bridge is begun.");
    }

    /** Tarmin crossed with no old god left to hold him: the world's only true ending (#278). */
    public static Reckoning lastCrossing(String bridge, String rose, LifeRecord last) {
        return new Reckoning(Reckoning.End.LAST_CROSSING,
                capital(bridge) + " reached the far shore when " + nameOf(last) + " died, and Tarmin crossed. No old god was "
                        + "left to hold him. He did not come back, and " + rose + " rules what is left below.",
                "The last stone of the Bridge was laid, and the Knell rang once more, and never again. Tarmin walks "
                        + "the world above. The soul has no keeper now.");
    }

    /** Tarmin was slain, by the Champion or by a monster, and {@code ending.newTitle} reigns (#291, #292). */
    public static Reckoning ageEnded(String age, LifeRecord last, com.bpm.minotaur.gamedata.history.AgeEnding ending) {
        String who = nameOf(last);
        String old = ending.oldTitle;
        switch (ending.kind) {
            case RETURNED:
                return new Reckoning(Reckoning.End.AGE_ENDED,
                        capital(age) + " ended when " + who + " slew " + old + " and sealed the Rift with the Treasure. "
                                + "The old gods woke, and " + ending.by + " took the empty throne and the name "
                                + ending.newTitle + ".",
                        who + " returned the Treasure to the Rift, and was allowed to die. Hell does not keep an "
                                + "empty throne: " + ending.newTitle + " sits on it now.");
            case THRONE:
                return new Reckoning(Reckoning.End.AGE_ENDED,
                        capital(age) + " ended when " + who + " slew " + old + " and sat down on the throne. "
                                + who + " is " + ending.newTitle + " now, and the next life of the soul is sent to hunt them.",
                        "The soul's own hand slew Tarmin, and the soul's own hand wears the crown. "
                                + ending.newTitle + " begins a new Bridge.");
            default:
                return new Reckoning(Reckoning.End.AGE_ENDED,
                        capital(age) + " ended when " + usurpation(ending) + ", and " + old + " fell. "
                                + capital(ending.by) + " took the throne and the name: " + ending.fullTitle() + ".",
                        old + " is dead, and no seeker struck the blow. Long live " + ending.newTitle + ".");
        }
    }

    private static String usurpation(com.bpm.minotaur.gamedata.history.AgeEnding ending) {
        switch (ending.kind) {
            case NEMESIS: return ending.by + ", grown fat on the soul's lives, challenged Tarmin in the castle hall";
            case MEGABEAST: return ending.by + " broke into Castle Tarmin";
            default: return "the siege of " + ending.by + " broke Castle Tarmin";
        }
    }

    private static String nameOf(LifeRecord life) {
        return life != null ? life.name : "a nameless prisoner";
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
