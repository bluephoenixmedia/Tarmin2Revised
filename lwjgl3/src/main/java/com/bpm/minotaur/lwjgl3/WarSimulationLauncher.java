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
        for (String a : args) {
            if (a.startsWith("--seasons=")) seasons = Integer.parseInt(a.substring("--seasons=".length()));
        }
        DoctrineCatalog catalog = DoctrineCatalog.fromJson(read(DoctrineCatalog.DATA_PATH))
                .withBeasts(MegabeastCatalog.fromJson(read(MegabeastCatalog.DATA_PATH)));
        for (String line : WarSimulation.run(catalog, WarSimulation.DEFAULT_SEEDS, seasons)) System.out.println(line);
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
