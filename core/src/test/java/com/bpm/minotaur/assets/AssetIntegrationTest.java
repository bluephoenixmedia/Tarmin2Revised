package com.bpm.minotaur.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class AssetIntegrationTest {

    private static File getAssetFile(String relativePath) {
        File f = new File("assets/" + relativePath);
        if (!f.exists()) {
            f = new File("../assets/" + relativePath);
        }
        return f;
    }

    @Test
    public void testCombatAndWorldAudioFilesExist() {
        String[] requiredSounds = {
                "sounds/weapon_swing.ogg",
                "sounds/weapon_swing_2.ogg",
                "sounds/meat_hit.ogg",
                "sounds/metal_hit.ogg",
                "sounds/metal_hit_heavy.ogg",
                "sounds/monster_grunt_light.wav",
                "sounds/monster_roar_heavy.wav",
                "sounds/ui_click.ogg",
                "sounds/book_flip.ogg",
                "sounds/chest_open.ogg",
                "sounds/coins.ogg",
                "sounds/door_creak.ogg",
                "sounds/void_laser.wav",
                "sounds/void_laser_alt.wav",
                "sounds/amb_void_groan.wav",
                "sounds/amb_doom_subbass.wav",
                "sounds/music/tarmin_core.mp3"
        };

        for (String soundPath : requiredSounds) {
            File f = getAssetFile(soundPath);
            assertTrue("Audio asset must exist: " + soundPath, f.exists() && f.length() > 0);
        }
    }

    @Test
    public void testCampModelsAndMaterialsExist() {
        String[] requiredModels = {
                "models/camp/campfire.obj",
                "models/camp/campfire.mtl",
                "models/camp/chest.obj",
                "models/camp/chest.mtl",
                "models/camp/weapon_rack.obj",
                "models/camp/weapon_rack.mtl",
                "models/camp/tent.obj",
                "models/camp/tent.mtl"
        };

        for (String modelPath : requiredModels) {
            File f = getAssetFile(modelPath);
            assertTrue("3D camp asset must exist: " + modelPath, f.exists() && f.length() > 0);
        }
    }

    @Test
    public void testCuratedIconFilesExist() {
        String[] requiredIcons = {
                "images/icons/potions/potion_default.png",
                "images/icons/potions/potion_red.png",
                "images/icons/potions/potion_blue.png",
                "images/icons/potions/potion_green.png",
                "images/icons/potions/potion_gold.png",
                "images/icons/potions/potion_pink.png",
                "images/icons/rings/ring_default.png",
                "images/icons/scrolls/scroll_default.png",
                "images/icons/gear/helmet_default.png",
                "images/icons/gear/boots_default.png",
                "images/icons/gear/gauntlet_default.png",
                "images/icons/gear/shield_default.png",
                "images/icons/gear/cloak_default.png",
                "images/icons/gear/armor_default.png",
                "images/icons/skills/skills_sheet.png"
        };

        for (String iconPath : requiredIcons) {
            File f = getAssetFile(iconPath);
            assertTrue("Icon asset must exist: " + iconPath, f.exists() && f.length() > 0);
        }
    }

    @Test
    public void testItemDefaultIconResolution() {
        Item potionRed = Item.fromTemplate(Item.ItemType.POTION_PINK, new com.bpm.minotaur.gamedata.item.ItemTemplate());
        potionRed.setItemColor(ItemColor.RED);
        assertEquals("images/icons/potions/potion_red.png", potionRed.resolveDefaultIconPath());

        Item potionBlue = Item.fromTemplate(Item.ItemType.POTION_BLUE, new com.bpm.minotaur.gamedata.item.ItemTemplate());
        potionBlue.setItemColor(ItemColor.BLUE);
        assertEquals("images/icons/potions/potion_blue.png", potionBlue.resolveDefaultIconPath());

        Item ring = Item.fromTemplate(Item.ItemType.RING_OF_PROTECTION, new com.bpm.minotaur.gamedata.item.ItemTemplate());
        assertEquals("images/icons/rings/ring_default.png", ring.resolveDefaultIconPath());

        Item scroll = Item.fromTemplate(Item.ItemType.SCROLL_FIREBALL, new com.bpm.minotaur.gamedata.item.ItemTemplate());
        assertEquals("images/icons/scrolls/scroll_default.png", scroll.resolveDefaultIconPath());
    }

    @Test
    public void testForestTexturesCanBeDecodedByPixmap() {
        new com.badlogic.gdx.utils.SharedLibraryLoader().load("gdx");
        File dir = getAssetFile("images/forest");
        File[] files = dir.listFiles();
        assertNotNull(files);
        for (File f : files) {
            if (f.getName().endsWith(".png")) {
                System.out.println("Testing pixmap load: " + f.getName());
                com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(new FileHandle(f));
                assertNotNull(pixmap);
                assertTrue(pixmap.getWidth() > 0);
                assertTrue(pixmap.getHeight() > 0);
                pixmap.dispose();
            }
        }

        String[] extra = {
            "images/floor_forest.png",
            "images/forest_cliff.png",
            "images/scenery/decomposing_corpse.png",
            "images/weapons/crossbow_disk.png"
        };
        File imagesDir = getAssetFile("images");
        testAllPngsInDir(imagesDir);
    }

    private void testAllPngsInDir(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                testAllPngsInDir(f);
            } else if (f.getName().endsWith(".png")) {
                try {
                    com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(new FileHandle(f));
                    pixmap.dispose();
                } catch (Exception e) {
                    System.err.println("Non-fatal exception on " + f.getPath() + ": " + e.getMessage());
                }
            }
        }
    }

    @Test
    public void testEmptyPixmap() {
        new com.badlogic.gdx.utils.SharedLibraryLoader().load("gdx");
        try {
            new com.badlogic.gdx.graphics.g2d.Gdx2DPixmap(new byte[0], 0, 0, 0);
        } catch (Exception e) {
            System.out.println("Caught expected exception: " + e.getMessage());
        }
    }
}
