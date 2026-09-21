package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class MonsterTextureLoadingTest {

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> null
            );
        }
        if (Gdx.files == null) {
            Gdx.files = (Files) Proxy.newProxyInstance(
                    Files.class.getClassLoader(),
                    new Class<?>[]{Files.class},
                    (proxy, method, args) -> {
                        if ("local".equals(method.getName()) || "internal".equals(method.getName())) {
                            String path = (String) args[0];
                            File file = new File(path);
                            if (!file.exists()) {
                                file = new File("assets/" + path);
                            }
                            if (!file.exists()) {
                                file = new File("../assets/" + path);
                            }
                            return new FileHandle(file);
                        }
                        return null;
                    }
            );
        }
    }

    @After
    public void tearDown() {
        Gdx.app = null;
        Gdx.files = null;
    }

    @Test
    public void testAllMonsterTexturesExistAndLoad() {
        MonsterDataManager manager = new MonsterDataManager();
        manager.load();

        // 1. Verify Giant Scorpion has single texture and no directional textures
        MonsterTemplate scorpion = manager.getTemplate(Monster.MonsterType.GIANT_SCORPION);
        assertNotNull("Giant Scorpion template should exist", scorpion);
        assertEquals("images/monsters/giant_scorpion.png", scorpion.texturePath);
        assertNull("Giant Scorpion should no longer have directionTextures", scorpion.directionTextures);
        FileHandle scorpionHandle = Gdx.files.internal(scorpion.texturePath);
        assertTrue("Giant Scorpion texture must exist on disk: " + scorpion.texturePath, scorpionHandle.exists());

        // 2. Verify Ghast uses PNG texture
        MonsterTemplate ghast = manager.getTemplate(Monster.MonsterType.GHAST);
        assertNotNull("Ghast template should exist", ghast);
        assertEquals("images/monsters/ghast.png", ghast.texturePath);
        assertTrue("Ghast texture must exist on disk: " + ghast.texturePath, Gdx.files.internal(ghast.texturePath).exists());

        // 3. Verify Beholder uses PNG texture
        MonsterTemplate beholder = manager.getTemplate(Monster.MonsterType.BEHOLDER);
        assertNotNull("Beholder template should exist", beholder);
        assertEquals("images/monsters/beholder.png", beholder.texturePath);
        assertTrue("Beholder texture must exist on disk: " + beholder.texturePath, Gdx.files.internal(beholder.texturePath).exists());

        // 4. Verify Spider uses PNG texture and exists on disk
        MonsterTemplate spider = manager.getTemplate(Monster.MonsterType.SPIDER);
        assertNotNull("Spider template should exist", spider);
        assertEquals("images/monsters/spider.png", spider.texturePath);
        assertTrue("Spider texture must exist on disk: " + spider.texturePath, Gdx.files.internal(spider.texturePath).exists());
        assertEquals(MonsterFamily.BEAST, spider.family);

        // 5. Verify all monster textures in monsterTemplates exist on disk
        List<String> missingFiles = new ArrayList<>();
        int verifiedCount = 0;

        for (MonsterTemplate template : manager.getAllTemplates()) {
            if (template.texturePath != null && !template.texturePath.trim().isEmpty()) {
                FileHandle handle = Gdx.files.internal(template.texturePath);
                if (!handle.exists()) {
                    missingFiles.add(template.texturePath);
                } else {
                    verifiedCount++;
                }
            }

            if (template.directionTextures != null) {
                MonsterTemplate.DirectionTextures dt = template.directionTextures;
                for (String p : new String[]{dt.north, dt.east, dt.west}) {
                    if (p != null && !p.isEmpty() && !Gdx.files.internal(p).exists()) {
                        missingFiles.add("directional -> " + p);
                    }
                }
                if (dt.southFrames != null) {
                    for (String sf : dt.southFrames) {
                        if (sf != null && !sf.isEmpty() && !Gdx.files.internal(sf).exists()) {
                            missingFiles.add("southFrame -> " + sf);
                        }
                    }
                }
            }
        }

        assertTrue("Found missing monster textures: " + missingFiles, missingFiles.isEmpty());
        assertTrue("Expected at least 30 monster textures to be verified, but found " + verifiedCount, verifiedCount >= 30);
    }
}
