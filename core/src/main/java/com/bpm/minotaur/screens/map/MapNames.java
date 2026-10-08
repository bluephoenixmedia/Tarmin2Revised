package com.bpm.minotaur.screens.map;

import com.bpm.minotaur.gamedata.map.MapKnowledge;
import com.bpm.minotaur.gamedata.map.MapModel;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.ShelterRoads;
import com.bpm.minotaur.ui.UiNames;

/**
 * The words the expedition map prints. Kept apart so every name the map shows is in
 * one place and written in characters {@code intellivision.ttf} can draw.
 */
final class MapNames {

    private MapNames() {
    }

    static String floor(int floor) {
        return floor <= 1 ? "Surface" : "Stratum " + (floor - 1);
    }

    static String biome(Biome biome) {
        if (biome == null) return "Unknown land";
        switch (biome) {
            case MAZE: return "The Maze";
            case BLIGHT: return "Blighted Marches";
            case MOUNTAINS: return "Mountains";
            case OCEAN: return "Ocean";
            default: return UiNames.of(biome);
        }
    }

    static String road(int road) {
        if (road == ShelterRoads.OFF_ROAD) return "off the roads";
        if (road == ShelterRoads.CASTLE_ROAD) return "the castle road";
        return "seal road " + road;
    }

    static String shelter(MapModel.ShelterMark mark) {
        switch (mark) {
            case HOME: return "Home shelter";
            case LIT: return "Lit shelter";
            case COLD: return "Cold shelter";
            case RUMOURED: return "Rumoured shelter";
            default: return "";
        }
    }

    static String knowledge(MapModel.Knowledge k) {
        switch (k) {
            case VISITED: return "Explored";
            case GLIMPSED: return "Glimpsed from a neighbouring chunk";
            default: return "Unexplored";
        }
    }

    static String pin(MapKnowledge.Pin pin) {
        switch (pin) {
            case DANGER: return "Danger";
            case LOOT: return "Loot";
            case RETURN: return "Return here";
            case TRADER: return "Trader";
            case LOCKED: return "Locked";
            default: return "Unknown";
        }
    }

    /** Doom stages 1-4, as {@code DoomManager.getDoomStage} documents them. */
    static String doom(int stage) {
        switch (stage) {
            case 1: return "Quiescent";
            case 2: return "Restless";
            case 3: return "Corrupted";
            default: return "Tarmin's Wrath";
        }
    }

    static String station(String stationName) {
        try {
            return ShelterAltar.Station.valueOf(stationName).getDisplayName();
        } catch (IllegalArgumentException e) {
            return UiNames.fromConstant(stationName);
        }
    }

    static String chunks(int n) {
        return UiNames.plural(n, "chunk");
    }
}
