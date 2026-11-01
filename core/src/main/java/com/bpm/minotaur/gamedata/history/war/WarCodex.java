package com.bpm.minotaur.gamedata.history.war;

import java.util.List;

/**
 * What the Living War teaches in words (Living War A51): the three lines of the War Table's first
 * opening, and the "Living War" page of the Codex. Every string here must survive {@code UiGlyphs.sanitize}.
 */
public final class WarCodex {

    /** Shown the first time the War Table is opened. */
    public static final List<String> FIRST_OPENING = List.of(
            "The houses are at war, and their armies march whether you watch or not.",
            "These marks show where they were reported. Only a siege is certain.",
            "When you reach a fight, you choose a side.");

    /** One heading of the page, and what it says. */
    public static final class Section {
        public final String heading;
        public final String body;

        Section(String heading, String body) {
            this.heading = heading;
            this.body = body;
        }
    }

    public static final List<Section> PAGE = List.of(
            new Section("ARMS",
                    "A house's arms tell you its doctrine before its soldiers do. The field, the division and the border "
                            + "are drawn from what it believes, so two banners alike in form fight alike."),
            new Section("MARKS ON THE MAP",
                    "A host you saw is marked where you saw it, and that mark ages as it marches on. Word from the War Table "
                            + "or a townsperson gives only a rough region, older still. Tracks on the ground bring a mark up to date. "
                            + "Only a siege is certain: it cannot move."),
            new Section("CHOOSING A SIDE",
                    "Coming upon a battle, help one side or keep away. The side you help fights beside you until it ends. "
                            + "Stand to the end for a share of its thanks; leave early and you are owed nothing."),
            new Section("FAVOUR AND THE OATH",
                    "A house may count you an Enemy, Distrusted, Unknown, Tolerated or a Friend. Tolerated, its soldiers leave you be "
                            + "unless struck. A Friend may rest and trade at its camps, and kneel at its banner to swear to it. "
                            + "What pleases a house displeases its enemies. Break an oath and every house hears of it."),
            new Section("FOLLOWING A HOST",
                    "Sworn, you may ask a camped host of your liege to take you along, and press the march key to go where it goes. "
                            + "You never steer it. Around an enemy's walls the same key waits in the siege until something comes. "
                            + "Its fire is a Bivouac with its watch kept for you."),
            new Section("CONTRACTS",
                    "A house's captain sets its sworn a task at its banner: each names a real host, captain, column, camp or seat. "
                            + "If that thing is gone before you get to it the task is void, with no penalty and no pay. "
                            + "If it fell in a battle you fought in for your liege, it counts as done. Return to a banner to be paid."),
            new Section("BIVOUAC AND WARDS",
                    "Away from a Shelter, a Tinder Bundle lights a fire to sleep beside. It rests you by half, and the armies march "
                            + "while you sleep. Wards set around the fire are what stand between you and the night."));

    private WarCodex() {
    }
}
