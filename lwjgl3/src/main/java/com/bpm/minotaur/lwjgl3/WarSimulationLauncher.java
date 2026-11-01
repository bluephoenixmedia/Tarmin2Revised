package com.bpm.minotaur.lwjgl3;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.beast.MegabeastCatalog;
import com.bpm.minotaur.gamedata.history.war.army.WarSimulation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Runs the war with nobody watching and prints its budget and what it did (Living War A59).
 * {@code ./gradlew :lwjgl3:runWarSimulation -PwarArgs="--seasons=40"}. Runs from the assets folder.
 */
public final class WarSimulationLauncher {

    public static void main(String[] args) throws IOException {
        int seasons = 40;
        float crossroads = -1f;
        int seeds = WarSimulation.DEFAULT_SEEDS.length;
        for (String a : args) {
            if (a.startsWith("--seasons=")) seasons = Integer.parseInt(a.substring("--seasons=".length()));
            if (a.startsWith("--crossroads=")) crossroads = Float.parseFloat(a.substring("--crossroads=".length()));
            if (a.startsWith("--seeds=")) seeds = Integer.parseInt(a.substring("--seeds=".length()));
        }
        DoctrineCatalog catalog = DoctrineCatalog.fromJson(read(DoctrineCatalog.DATA_PATH))
                .withBeasts(MegabeastCatalog.fromJson(read(MegabeastCatalog.DATA_PATH)));
        java.util.List<String> report = crossroads < 0f ? WarSimulation.run(catalog, WarSimulation.seeds(seeds), seasons)
                : WarSimulation.run(catalog, WarSimulation.seeds(seeds), seasons, crossroads);
        for (String line : report) System.out.println(line);
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
