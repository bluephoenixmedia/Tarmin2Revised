package com.bpm.minotaur.gamedata.worldlog;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A span of the World Log between stages of Tarmin's Hunger (#269, #281), named like a Dwarf
 * Fortress Age. The words darken as the Hunger rises. ASCII only (UiGlyphs).
 */
public final class Era {

    /** The Hunger at which each later stage begins: the Doom stages, so an Era is a stage. */
    private static final int[] STAGE_STARTS = {
            com.bpm.minotaur.managers.DoomManager.DEATHS_FOR_STAGE_2,
            com.bpm.minotaur.managers.DoomManager.DEATHS_FOR_STAGE_3,
            com.bpm.minotaur.managers.DoomManager.DEATHS_FOR_STAGE_4
    };

    private static final String[][] WORDS = {
            {"First Stones", "Raids", "Tithes", "Lanterns", "Small Graves", "Iron Collars", "Thin Bread"},
            {"Ash", "Long Knives", "the Gnawed Bone", "Silent Bells", "Red Mud", "Broken Oaths"},
            {"the Bleeding Seam", "Black Banners", "Hollow Saints", "the Hungry Dark", "Bone Roads"},
            {"the Last Stones", "the Far Shore", "Ashen Crowns", "the Knell", "Dying Gods"}
    };

    public static final int STAGES = WORDS.length;

    /** 0 for the first Era, up to {@link #STAGES} - 1. */
    public final int stage;
    public final String name;
    /** Newest first. */
    public final List<LifeRecord> lives = new ArrayList<>();

    Era(int stage, String name) {
        this.stage = stage;
        this.name = name;
    }

    /** The Era a life belongs to, from the Hunger it woke at. */
    public static int stageOf(int hunger) {
        int stage = 0;
        for (int start : STAGE_STARTS) {
            if (hunger >= start) stage++;
        }
        return stage;
    }

    /** "the Era of Ash and Broken Oaths": the same seed and stage always give the same name. */
    static String nameFor(long seed, int stage) {
        String[] words = WORDS[stage];
        Random rng = new Random(seed * 31 + stage);
        int a = rng.nextInt(words.length);
        int b = (a + 1 + rng.nextInt(words.length - 1)) % words.length;
        return "the Era of " + words[a] + " and " + words[b];
    }
}
