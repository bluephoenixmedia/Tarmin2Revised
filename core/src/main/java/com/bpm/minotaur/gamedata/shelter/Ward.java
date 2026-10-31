package com.bpm.minotaur.gamedata.shelter;

import com.bpm.minotaur.gamedata.item.Item;

/**
 * A protection laid over a Bivouac for one night (Living War A64): crafted, or cast on pitching.
 * Each is paid for separately and they stack. Ward spells are cast from the Bivouac's own prompt,
 * so they hold no Spell Slot and are taught by the Wayfarer's Tome alone.
 */
public enum Ward {
    SHIELD("Shield", Item.ItemType.STAKED_HIDE_SCREEN, "Staked Hide Screen", "SLEEPERS_WARD", "Sleeper's Ward", 8),
    ALARM("Alarm", Item.ItemType.TRIPWIRE_BELLS, "Tripwire Bells", "WATCHFUL_SLEEP", "Watchful Sleep", 5),
    CONCEALMENT("Concealment", Item.ItemType.ASH_BONE_CIRCLE, "Ash-and-Bone Circle", "VEIL_OF_THE_SLEEPER", "Veil of the Sleeper", 10);

    /** What a Shield takes of the free rounds. */
    public static final int SHIELD_ABSORBS = 20;
    /** How near a hostile comes before an Alarm wakes the sleeper. */
    public static final int ALARM_TILES = 6;
    /** The intelligence at which a monster sees through Concealment, as any spellcaster does. */
    public static final int CONCEALMENT_SEEN_AT_INTELLIGENCE = 12;

    public final String label;
    public final Item.ItemType itemType;
    public final String itemName;
    public final String spellId;
    public final String spellName;
    public final int mpCost;

    Ward(String label, Item.ItemType itemType, String itemName, String spellId, String spellName, int mpCost) {
        this.label = label;
        this.itemType = itemType;
        this.itemName = itemName;
        this.spellId = spellId;
        this.spellName = spellName;
        this.mpCost = mpCost;
    }

    /** The ward this spell lays, or null for any other spell. */
    public static Ward ofSpell(String spellId) {
        for (Ward ward : values()) {
            if (ward.spellId.equalsIgnoreCase(spellId)) return ward;
        }
        return null;
    }

    /** Whether Concealment hides a sleeper from this monster. */
    public static boolean concealmentHidesFrom(int intelligence, boolean spellcaster) {
        return intelligence < CONCEALMENT_SEEN_AT_INTELLIGENCE && !spellcaster;
    }

    /** What is left of a Shield through the free rounds. */
    public static final class Shield {
        private int left;

        public Shield(int left) {
            this.left = left;
        }

        /** Takes what it can of a blow; returns what gets through. */
        public int absorb(int damage) {
            int taken = Math.min(left, damage);
            left -= taken;
            return damage - taken;
        }
    }
}
