package com.bpm.minotaur.gamedata.history;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class DoctrineCatalogTest {

    static String readAsset(String relativePath) throws IOException {
        File f = new File("assets/" + relativePath);
        if (!f.isFile()) f = new File("../assets/" + relativePath);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    public static DoctrineCatalog loadCatalog() throws IOException {
        return DoctrineCatalog.fromJson(readAsset(DoctrineCatalog.DATA_PATH)).withBeasts(
                com.bpm.minotaur.gamedata.history.beast.MegabeastCatalog.fromJson(
                        readAsset(com.bpm.minotaur.gamedata.history.beast.MegabeastCatalog.DATA_PATH)));
    }

    @Test
    public void everyRosterAndBossEntryIsARealMonster() throws IOException {
        JsonValue monsters = new JsonReader().parse(readAsset("data/monsters.json"));
        Set<String> known = new HashSet<>();
        for (JsonValue m : monsters) known.add(m.name);

        for (Doctrine d : loadCatalog().all()) {
            assertTrue(d.id + " boss base " + d.bossBase, known.contains(d.bossBase));
            for (String r : d.roster) {
                assertTrue(d.id + " roster entry " + r, known.contains(r));
            }
        }
    }

    @Test
    public void everyDoctrineIsComplete() throws IOException {
        DoctrineCatalog catalog = loadCatalog();
        int selectable = 0;
        for (Doctrine d : catalog.all()) {
            assertNotNull(d.id, d.name);
            assertNotNull(d.id, d.creed);
            assertFalse(d.id, d.roster.isEmpty());
            assertNotNull(d.id, d.interiorTheme);
            assertNotNull(d.id, d.voice);
            assertFalse(d.id, d.traitWeights.isEmpty());
            for (String t : d.traitWeights.keySet()) Trait.valueOf(t);
            assertFalse(d.id, d.givenNames.isEmpty());
            assertFalse(d.id, d.givenEndings.isEmpty());
            assertFalse(d.id, d.houseNames.isEmpty());
            assertFalse(d.id, d.charges.isEmpty());
            assertFalse(d.id, d.mottos.isEmpty());
            if (!d.unique) selectable++;
        }
        assertTrue("six to eight doctrines", catalog.all().size() >= 6 && catalog.all().size() <= 8);
        assertTrue("great and lesser houses need room to differ", selectable >= 6);
        assertNotNull("Tarmin-Zul's doctrine", catalog.get(DoctrineCatalog.TARMIN_ZUL_DOCTRINE));
    }
}
