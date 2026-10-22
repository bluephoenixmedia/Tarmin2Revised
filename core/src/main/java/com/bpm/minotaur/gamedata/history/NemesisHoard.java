package com.bpm.minotaur.gamedata.history;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.save.ItemSaveData;

import java.util.ArrayList;
import java.util.List;

/**
 * The gear each Nemesis took from the lives it ended (#284), given up when it falls. Kept in the
 * slot beside the history, not in it: items are not deeds, and the replay never needs them.
 */
public final class NemesisHoard {

    /** One Nemesis's spoils. */
    public static final class Spoils {
        public int nemesisId;
        public ArrayList<ItemSaveData> items = new ArrayList<>();
    }

    private ArrayList<Spoils> spoils = new ArrayList<>();

    /** Keeps {@code item} for the Nemesis; false for what every life gets back anyway. */
    public boolean take(int nemesisId, ItemSaveData item) {
        if (item == null || item.type == null || returnsEveryLife(item.type)) return false;
        spoilsOf(nemesisId, true).items.add(item);
        return true;
    }

    /** Everything the Nemesis holds, handed over once; empty after. */
    public List<ItemSaveData> claim(int nemesisId) {
        Spoils s = spoilsOf(nemesisId, false);
        if (s == null) return new ArrayList<>();
        spoils.remove(s);
        return s.items;
    }

    private static boolean returnsEveryLife(Item.ItemType type) {
        return type == com.bpm.minotaur.gamedata.item.FathersSword.TYPE || type == Item.ItemType.BRASS_LANTERN;
    }

    private Spoils spoilsOf(int nemesisId, boolean create) {
        for (Spoils s : spoils) {
            if (s.nemesisId == nemesisId) return s;
        }
        if (!create) return null;
        Spoils s = new Spoils();
        s.nemesisId = nemesisId;
        spoils.add(s);
        return s;
    }
}
