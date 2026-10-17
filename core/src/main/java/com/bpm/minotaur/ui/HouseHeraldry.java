package com.bpm.minotaur.ui;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.history.Doctrine;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;

/**
 * A Maze house's colours, from its doctrine's heraldry in {@code doctrines.json}. These are the
 * only colours in the UI that come from data rather than {@link UiTheme}: a house is known by its
 * banner, and the banner is authored with the doctrine.
 */
public final class HouseHeraldry {

    private HouseHeraldry() {
    }

    public static Color primary(HistoryWorld world, int houseId, DoctrineCatalog catalog) {
        Doctrine d = doctrine(world, houseId, catalog);
        return d == null || d.primaryColor == null ? UiTheme.TEXT_DIM : Color.valueOf(d.primaryColor);
    }

    public static Color secondary(HistoryWorld world, int houseId, DoctrineCatalog catalog) {
        Doctrine d = doctrine(world, houseId, catalog);
        return d == null || d.secondaryColor == null ? UiTheme.TEXT_DIM : Color.valueOf(d.secondaryColor);
    }

    private static Doctrine doctrine(HistoryWorld world, int houseId, DoctrineCatalog catalog) {
        House h = world.house(houseId);
        return h == null ? null : catalog.get(h.doctrineId);
    }
}
