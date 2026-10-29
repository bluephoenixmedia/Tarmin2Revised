package com.bpm.minotaur.gamedata.history;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.ShelterRoads;
import com.bpm.minotaur.managers.BiomeManager;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/** PROTOTYPE, wipe me: exports real worlds for the army-simulation prototype (wayfinder #322). */
public class ZzArmyProtoExportTmp {

    static final int R = 60;
    static final long[] SEEDS = {1000L, 8919L, 24757L, 40595L, 135623L, 151461L};

    @Test
    public void export() throws Exception {
        DoctrineCatalog catalog = DoctrineCatalogTest.loadCatalog();
        StringBuilder js = new StringBuilder("const WORLDS = [\n");
        for (long seed : SEEDS) {
            BiomeManager bm = new BiomeManager(seed);
            ShelterRoads roads = bm.getRoads();
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            PlayerDeed opening = HistorySimulator.openingWar(w);
            if (opening != null) HistorySimulator.applyDeed(w, opening);
            SeatMap seats = SeatMap.of(w, roads, bm.getCastleSite(), seed);

            StringBuilder grid = new StringBuilder();
            for (int y = -R; y <= R; y++) for (int x = -R; x <= R; x++) grid.append(code(bm.getBiome(new GridPoint2(x, y))));

            js.append("{seed:").append(seed).append(",R:").append(R).append(",grid:\"").append(grid).append("\",\n");
            js.append(" castle:").append(pt(bm.getCastleSite())).append(",roads:[");
            for (ShelterRoads.Road r : roads.getRoads()) {
                js.append("{end:").append(pt(r.getEnd())).append(",shelters:[");
                for (GridPoint2 s : r.getShelters()) js.append(pt(s)).append(',');
                js.append("]},");
            }
            js.append("],\n houses:[\n");
            for (House h : w.livingHouses()) {
                GridPoint2 s = seats.seat(h.id);
                if (s == null) continue;
                if (!bm.isOpenLand(s) && !h.holdsCastle) s = nearestOpen(bm, s); // the #330 fix, applied here
                Doctrine d = catalog.get(h.doctrineId);
                js.append("  {id:").append(h.id).append(",name:").append(q(h.name))
                        .append(",kind:").append(q(h.holdsCastle ? "castle" : h.isGreat() ? "great" : "lesser"))
                        .append(",strength:").append(Math.round(h.strength)).append(",cap:").append(Math.round(h.strengthCap()))
                        .append(",liege:").append(h.liegeId).append(",color:").append(q("#" + (d == null ? "888888" : d.primaryColor)))
                        .append(",doctrine:").append(q(d == null ? h.doctrineId : d.name))
                        .append(",seat:").append(pt(s)).append(",figures:[");
                Figure lord = w.lordOf(h);
                List<Figure> members = w.livingMembers(h);
                for (Figure f : members) {
                    if (f.hostageOf >= 0) continue;
                    String role = lord != null && f.id == lord.id ? "lord" : f.role == Figure.Role.SWORN_SWORD ? "sworn" : "kin";
                    js.append("{id:").append(f.id).append(",name:").append(q(f.name)).append(",role:").append(q(role)).append(",traits:[");
                    for (Trait t : f.traits) js.append(q(t.name())).append(',');
                    js.append("]},");
                }
                js.append("]},\n");
            }
            js.append(" ],\n wars:[");
            for (War war : w.activeWars()) js.append("{a:").append(war.attackerId).append(",d:").append(war.defenderId).append(",age:").append(w.season - war.startSeason).append("},");
            js.append("],\n allies:[");
            List<House> live = w.livingHouses();
            for (int i = 0; i < live.size(); i++) for (int j = i + 1; j < live.size(); j++)
                if (w.stance(live.get(i).id, live.get(j).id) == HistoryWorld.Stance.ALLIED) js.append('[').append(live.get(i).id).append(',').append(live.get(j).id).append("],");
            js.append("],\n grudges:[");
            for (Grudge g : w.grudges()) js.append("{h:").append(g.holderId).append(",a:").append(g.againstId).append(",w:").append(String.format(java.util.Locale.ROOT, "%.2f", g.weight)).append("},");
            js.append("]},\n");
        }
        js.append("];\n");
        Files.write(Paths.get(System.getProperty("java.io.tmpdir"), "army_proto_worlds.js"), js.toString().getBytes(StandardCharsets.UTF_8));
    }

    static GridPoint2 nearestOpen(BiomeManager bm, GridPoint2 s) {
        for (int r = 1; r < 40; r++)
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                GridPoint2 c = new GridPoint2(s.x + dx, s.y + dy);
                if (bm.isOpenLand(c)) return c;
            }
        return s;
    }

    static char code(Biome b) {
        switch (b) {
            case MAZE: return 'Z';
            case FOREST: return 'F';
            case DESERT: return 'D';
            case MOUNTAINS: return 'M';
            case LAKELANDS: return 'L';
            case TUNDRA: return 'T';
            case BLIGHT: return 'B';
            default: return 'O';
        }
    }

    static String pt(GridPoint2 p) {
        return "[" + p.x + "," + p.y + "]";
    }

    static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
