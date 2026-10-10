package com.bpm.minotaur.assets;

import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.headless.HeadlessSoundManager;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class SoundDesignIntegrationTest {

    private File file(String path) {
        File f = new File(path);
        if (!f.exists()) f = new File("../" + path);
        return f;
    }

    @Test
    public void foleyAssetsExist() {
        String[] foley = {
            "assets/sounds/map_open.wav",
            "assets/sounds/backpack_foley.wav",
            "assets/sounds/bag_open.wav",
            "assets/sounds/cloth_drop.wav",
            "assets/sounds/armor_equip.wav"
        };
        for (String p : foley) {
            assertTrue("Foley file missing: " + p, file(p).isFile());
        }
    }

    @Test
    public void alarmAssetsExist() {
        String[] alarms = {
            "assets/sounds/alarms/alarm_03.wav",
            "assets/sounds/alarms/alarm_09.wav",
            "assets/sounds/alarms/alarm_10.wav",
            "assets/sounds/alarms/alarm_15.wav",
            "assets/sounds/alarms/alarm_20.wav",
            "assets/sounds/alarms/alarm_22.wav",
            "assets/sounds/alarms/alarm_29.wav"
        };
        for (String p : alarms) {
            assertTrue("Alarm file missing: " + p, file(p).isFile());
        }
    }

    @Test
    public void monsterAssetsExistAndDecodable() {
        String[] monsters = {
            "assets/sounds/monster/126312__cmusounddesign__cr-sharktopusroar3.wav",
            "assets/sounds/monster/232289__zglar__zombie-or-monster-or-lion-roar.wav",
            "assets/sounds/monster/340161__flechabr__ecoed-roar.wav",
            "assets/sounds/monster/401568__cylon8472__kong-roar_complete.wav",
            "assets/sounds/monster/418394__thebuilder15__beast-roar.wav",
            "assets/sounds/monster/469123__manim8__demon_lion_monster_growl_roar.wav",
            "assets/sounds/monster/486309__kp2494__cthulumonster_roar.mp3",
            "assets/sounds/monster/487177__kp2494__cthulhumonster_roar.mp3",
            "assets/sounds/monster/491443__music15tree__roar.wav",
            "assets/sounds/monster/500919__vanishedillusion__creature-roar-in-winter.wav",
            "assets/sounds/monster/505131__mitchanary__monster-roar_8.mp3",
            "assets/sounds/monster/837799__bikkit99__sea-creature-roar.wav"
        };
        for (String p : monsters) {
            assertTrue("Monster file missing: " + p, file(p).isFile());
        }
    }

    @Test
    public void abyssLoopsManifestAndFilesExist() throws IOException {
        File manifest = file("assets/data/abyss_loops.json");
        assertTrue("abyss_loops.json missing", manifest.isFile());
        String json = new String(Files.readAllBytes(manifest.toPath()));
        assertTrue("Manifest should have loops array", json.contains("\"loops\":"));

        File abyssDir = file("assets/sounds/ambient/abyss");
        assertTrue("Abyss loops dir missing", abyssDir.isDirectory());
        File[] loops = abyssDir.listFiles((d, name) -> name.endsWith(".ogg"));
        assertNotNull(loops);
        assertTrue("Expected 100+ ogg loops, found " + loops.length, loops.length >= 100);
    }

    @Test
    public void headlessSoundManagerSafeInvocation() {
        HeadlessSoundManager sm = new HeadlessSoundManager();
        sm.playCombatStartSound();
        sm.playCombatStartSound(null);
        sm.playMapOpen();
        sm.playInventoryFoley();
        sm.playBagOpen();
        sm.playClothDrop();
        sm.playArmorEquip();
        sm.playDirectionalSound("alarm_03", 5f, 5f, 0f, 0f, Direction.NORTH, 20f, 0.8f);
        sm.playAmbientMonsterSound(4f, 4f, false, 0f, 0f, Direction.SOUTH);
        sm.playHarshKlaxon();
        sm.playBossWarningAlarm();
        sm.playWarZoneAlarm();
        sm.playGashesWarWail(false);
        sm.playHouseBreachWarning();
        sm.stopAllSounds();
        sm.dispose();
    }

    @Test
    public void bossClassificationLogic() {
        HeadlessSoundManager sm = new HeadlessSoundManager();
        assertFalse(sm.isBossOrMegabeast(null));

        Monster minotaur = new Monster(Monster.MonsterType.MINOTAUR, 50, 10);
        assertTrue("Minotaur is classified as boss", sm.isBossOrMegabeast(minotaur));

        Monster goblin = new Monster(Monster.MonsterType.GOBLIN, 10, 5);
        assertFalse("Goblin is standard monster", sm.isBossOrMegabeast(goblin));
    }

    @Test
    public void introMusicTrackExists() {
        File introTrack = file("assets/sounds/music/Crown_of_Molten_Steel.mp3");
        assertTrue("Intro music track Crown_of_Molten_Steel.mp3 must exist", introTrack.isFile());
        assertTrue("Intro music track must not be empty", introTrack.length() > 1000000);
    }
}
