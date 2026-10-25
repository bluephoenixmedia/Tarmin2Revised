package com.bpm.minotaur.ui;

import com.bpm.minotaur.gamedata.arts.ArtBar;
import com.bpm.minotaur.gamedata.arts.CombatArt;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.WeaponFamily;
import com.bpm.minotaur.gamedata.player.Mastery;
import com.bpm.minotaur.gamedata.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The words that show Mastery and the Combat Arts (#249; spec section 6). The Skill Tree gives
 * exact counts; the inspector and messages give rank names only. Every line is plain ASCII in the
 * font's set.
 */
public final class MasteryText {

    private MasteryText() {
    }

    /** The ten Weapon Families, in the order the Skill Tree lists them. */
    public static List<WeaponFamily> families() {
        List<WeaponFamily> out = new ArrayList<>();
        for (WeaponFamily family : WeaponFamily.values()) {
            if (family != WeaponFamily.NONE) out.add(family);
        }
        return Collections.unmodifiableList(out);
    }

    /** "Axe: Adept, 62 / 150 full hits"; a Master has no next rank, so "Blade: Master, 412 full hits". */
    public static String familyLine(Mastery mastery, WeaponFamily family) {
        Mastery.Rank rank = mastery.rankOf(family);
        int hits = mastery.hitsOf(family);
        int next = Mastery.hitsForNextRank(rank);
        String count = next > 0 ? hits + " / " + next : String.valueOf(hits);
        return UiNames.of(family) + ": " + UiNames.of(rank) + ", " + count + " full hits";
    }

    /** "Ringing Blow -- Blunt Adept Passive" */
    public static String artLine(CombatArt art) {
        return UiNames.of(art) + " -- " + UiNames.of(art.family) + " " + UiNames.of(art.rank) + " " + UiNames.of(art.kind);
    }

    /** "retrain at Adept" for an Art the soul kept but this body has not reached; else null. */
    public static String retrainNote(Player player, CombatArt art) {
        return ArtBar.isKept(player, art) ? "retrain at " + UiNames.of(art.rank) : null;
    }

    /** The inspector's line for a weapon: "Axe, your rank Adept"; null for something with no Family. */
    public static String inspectorValue(Player player, Item weapon) {
        WeaponFamily family = weapon == null ? WeaponFamily.NONE : weapon.getWeaponFamily();
        if (family == WeaponFamily.NONE) return null;
        return UiNames.of(family) + ", your rank " + UiNames.of(player.getStats().getMastery().rankOf(family));
    }

    /** The pause screen's line: "Arts remembered: 3". */
    public static String artsRemembered(Player player) {
        return "Arts remembered: " + player.getKnownArts().size();
    }

    /** The Art-learned card's second line: "Blunt - Expert Active". */
    public static String cardSubtitle(CombatArt art) {
        return UiNames.of(art.family) + " - " + UiNames.of(art.rank) + " " + UiNames.of(art.kind);
    }
}
