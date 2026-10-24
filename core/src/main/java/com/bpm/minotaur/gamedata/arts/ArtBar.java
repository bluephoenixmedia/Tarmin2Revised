package com.bpm.minotaur.gamedata.arts;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.WeaponFamily;
import com.bpm.minotaur.gamedata.player.Mastery;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;
import com.bpm.minotaur.gamedata.player.Player;

/**
 * The Art bar (#245; spec section 5): four slots whose contents follow the hands. There is no
 * assignment screen. Slots 1 and 2 hold the right hand's Expert and Master Arts, slots 3 and 4 the
 * left hand's, so a key always means the same slot. A slot is empty when the soul lacks that Art,
 * or when both hands hold the same Family.
 */
public final class ArtBar {

    public static final int SLOTS = 4;

    /** How a slot shows. */
    public enum State {
        EMPTY,
        READY,
        /** Dimmed, with the turns left. */
        COOLING,
        /** A readied Art, drawn and waiting (Held Arrow). */
        HELD
    }

    /** One slot as the HUD draws it. */
    public static final class Slot {
        public final CombatArt art;
        public final State state;
        public final int turnsLeft;
        /** Kept from an earlier life, not retrained by this body: a hollow rank mark, doubled cooldown. */
        public final boolean kept;

        Slot(CombatArt art, State state, int turnsLeft, boolean kept) {
            this.art = art;
            this.state = state;
            this.turnsLeft = turnsLeft;
            this.kept = kept;
        }
    }

    private ArtBar() {
    }

    /** The four slots' Arts, null where a slot is empty. */
    public static CombatArt[] contents(Player player) {
        CombatArt[] slots = new CombatArt[SLOTS];
        if (player == null) return slots;
        WeaponFamily right = player.masteryFamilyOf(player.getInventory().getRightHand(), false);
        WeaponFamily left = leftFamily(player);
        fill(player, slots, 0, right);
        if (left != right) fill(player, slots, 2, left);
        return slots;
    }

    /** The four slots as the HUD draws them. */
    public static Slot[] slots(Player player) {
        CombatArt[] arts = contents(player);
        Slot[] out = new Slot[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            CombatArt art = arts[i];
            if (art == null) {
                out[i] = new Slot(null, State.EMPTY, 0, false);
                continue;
            }
            int turns = player.getArtCooldowns().turnsLeft(art);
            State state = art == player.getHeldArt() ? State.HELD : turns > 0 ? State.COOLING : State.READY;
            out[i] = new Slot(art, state, turns, isKept(player, art));
        }
        return out;
    }

    /** Known by the soul, but this body's rank in the Family is below the rank that taught it. */
    public static boolean isKept(Player player, CombatArt art) {
        Rank body = player.getStats().getMastery().rankOf(art.family);
        return body.ordinal() < art.rank.ordinal();
    }

    /** The left hand's Family; null when it holds no weapon, or the body has no hands to hold one. */
    private static WeaponFamily leftFamily(Player player) {
        Item left = player.getInventory().getLeftHand();
        if (left == null || player.isPolymorphed() || !left.isWeapon()) return null;
        return Mastery.familyOfHit(left, false);
    }

    private static void fill(Player player, CombatArt[] slots, int at, WeaponFamily family) {
        if (family == null || family == WeaponFamily.NONE) return;
        CombatArt expert = CombatArt.of(family, Rank.EXPERT);
        CombatArt master = CombatArt.of(family, Rank.MASTER);
        if (expert != null && player.knowsArt(expert)) slots[at] = expert;
        if (master != null && player.knowsArt(master)) slots[at + 1] = master;
    }
}
