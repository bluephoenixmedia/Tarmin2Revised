package com.bpm.minotaur.paperdoll;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PaperDoll2DTest {

    private static final int EXPECTED_WIDTH = 1024;
    private static final int EXPECTED_HEIGHT = 1536;

    private File resolveFile(String relativePath) {
        File f = new File(relativePath);
        if (!f.exists()) {
            f = new File("../" + relativePath);
        }
        return f;
    }

    @Test
    public void testMasterCanvasAssetsExistAndHaveStandardDimensions() throws IOException {
        String[] requiredImages = {
                "assets/images/paperdoll/base_father.png",
                "assets/images/paperdoll/head/bascinet.png",
                "assets/images/paperdoll/head/basinet.png",
                "assets/images/paperdoll/chest/breastplate.png",
                "assets/images/paperdoll/arms/chainmail_arms.png",
                "assets/images/paperdoll/legs/bronze_leggings.png",
                "assets/images/paperdoll/feet/boots.png",
                "assets/images/paperdoll/shield/buckler.png",
                "tools/paperdoll_templates/base_father_1024x1536.png",
                "tools/paperdoll_templates/slot_guides_1024x1536.png"
        };

        for (String path : requiredImages) {
            File file = resolveFile(path);
            assertTrue("File should exist: " + path, file.exists());
            BufferedImage img = ImageIO.read(file);
            assertNotNull("Should decode as image: " + path, img);
            assertEquals("Width must be 1024 for " + path, EXPECTED_WIDTH, img.getWidth());
            assertEquals("Height must be 1536 for " + path, EXPECTED_HEIGHT, img.getHeight());
        }
    }

    @Test
    public void testLayerZOrderingMatchesSpecification() {
        // Spec order:
        // 1. Back Cloak (Z=10)
        // 2. Base Father Character (Z=20)
        // 3. Legs/Trousers (Z=30)
        // 4. Boots (Z=40)
        // 5. Chest Armor (Z=50)
        // 6. Arms/Pauldrons (Z=60)
        // 7. Gauntlets/Hands (Z=70)
        // 8. Main Hand Weapon (Z=80)
        // 9. Off-Hand Shield (Z=85)
        // 10. Helmet/Headgear (Z=90)
        // 11. Front Cloak Clasp (Z=95)

        assertEquals(10, PaperDoll2DWidget.PaperDollSlot.CLOAK_BACK.zIndex);
        assertEquals(20, PaperDoll2DWidget.PaperDollSlot.BASE_FATHER.zIndex);
        assertEquals(30, PaperDoll2DWidget.PaperDollSlot.LEGS.zIndex);
        assertEquals(40, PaperDoll2DWidget.PaperDollSlot.BOOTS.zIndex);
        assertEquals(50, PaperDoll2DWidget.PaperDollSlot.CHEST.zIndex);
        assertEquals(60, PaperDoll2DWidget.PaperDollSlot.ARMS.zIndex);
        assertEquals(70, PaperDoll2DWidget.PaperDollSlot.HANDS.zIndex);
        assertEquals(80, PaperDoll2DWidget.PaperDollSlot.WEAPON_MAIN.zIndex);
        assertEquals(85, PaperDoll2DWidget.PaperDollSlot.SHIELD_OFF.zIndex);
        assertEquals(90, PaperDoll2DWidget.PaperDollSlot.HELMET.zIndex);
        assertEquals(95, PaperDoll2DWidget.PaperDollSlot.CLOAK_FRONT.zIndex);

        assertTrue(PaperDoll2DWidget.PaperDollSlot.CLOAK_BACK.zIndex < PaperDoll2DWidget.PaperDollSlot.BASE_FATHER.zIndex);
        assertTrue(PaperDoll2DWidget.PaperDollSlot.BASE_FATHER.zIndex < PaperDoll2DWidget.PaperDollSlot.LEGS.zIndex);
        assertTrue(PaperDoll2DWidget.PaperDollSlot.LEGS.zIndex < PaperDoll2DWidget.PaperDollSlot.BOOTS.zIndex);
        assertTrue(PaperDoll2DWidget.PaperDollSlot.BOOTS.zIndex < PaperDoll2DWidget.PaperDollSlot.CHEST.zIndex);
        assertTrue(PaperDoll2DWidget.PaperDollSlot.CHEST.zIndex < PaperDoll2DWidget.PaperDollSlot.ARMS.zIndex);
        assertTrue(PaperDoll2DWidget.PaperDollSlot.ARMS.zIndex < PaperDoll2DWidget.PaperDollSlot.HANDS.zIndex);
        assertTrue(PaperDoll2DWidget.PaperDollSlot.HANDS.zIndex < PaperDoll2DWidget.PaperDollSlot.WEAPON_MAIN.zIndex);
        assertTrue(PaperDoll2DWidget.PaperDollSlot.WEAPON_MAIN.zIndex < PaperDoll2DWidget.PaperDollSlot.SHIELD_OFF.zIndex);
        assertTrue(PaperDoll2DWidget.PaperDollSlot.SHIELD_OFF.zIndex < PaperDoll2DWidget.PaperDollSlot.HELMET.zIndex);
        assertTrue(PaperDoll2DWidget.PaperDollSlot.HELMET.zIndex < PaperDoll2DWidget.PaperDollSlot.CLOAK_FRONT.zIndex);
    }

    @Test
    public void testFolderNamesMatchSlotDesign() {
        assertEquals("head", PaperDoll2DWidget.PaperDollSlot.HELMET.folderName);
        assertEquals("chest", PaperDoll2DWidget.PaperDollSlot.CHEST.folderName);
        assertEquals("arms", PaperDoll2DWidget.PaperDollSlot.ARMS.folderName);
        assertEquals("hands", PaperDoll2DWidget.PaperDollSlot.HANDS.folderName);
        assertEquals("legs", PaperDoll2DWidget.PaperDollSlot.LEGS.folderName);
        assertEquals("feet", PaperDoll2DWidget.PaperDollSlot.BOOTS.folderName);
        assertEquals("cloak", PaperDoll2DWidget.PaperDollSlot.CLOAK_BACK.folderName);
        assertEquals("cloak", PaperDoll2DWidget.PaperDollSlot.CLOAK_FRONT.folderName);
        assertEquals("weapon", PaperDoll2DWidget.PaperDollSlot.WEAPON_MAIN.folderName);
        assertEquals("shield", PaperDoll2DWidget.PaperDollSlot.SHIELD_OFF.folderName);
    }

    @Test
    public void testBatchBakedArmorAssetsExistAndMatchStandardDimensions() throws IOException {
        String[] batchSampleImages = {
                "assets/images/paperdoll/head/great_helm.png",
                "assets/images/paperdoll/head/full_plate_helmet.png",
                "assets/images/paperdoll/head/leather_helmet.png",
                "assets/images/paperdoll/head/hoplite_helmet.png",
                "assets/images/paperdoll/chest/full_plate.png",
                "assets/images/paperdoll/chest/leather_plate.png",
                "assets/images/paperdoll/chest/hoplite_armor.png",
                "assets/images/paperdoll/chest/chain_mail.png",
                "assets/images/paperdoll/chest/banded_mail.png",
                "assets/images/paperdoll/arms/full_plate_arms.png",
                "assets/images/paperdoll/arms/leather_arms.png",
                "assets/images/paperdoll/arms/wood_bone_arms.png",
                "assets/images/paperdoll/hands/full_plate_gauntlets.png",
                "assets/images/paperdoll/hands/bronze_gauntlets.png",
                "assets/images/paperdoll/hands/chainmail_gauntlets.png",
                "assets/images/paperdoll/legs/full_plate_leggings.png",
                "assets/images/paperdoll/legs/leather_leggings.png",
                "assets/images/paperdoll/legs/hoplite_leggings.png",
                "assets/images/paperdoll/feet/full_plate_boots.png",
                "assets/images/paperdoll/feet/leather_boots.png",
                "assets/images/paperdoll/feet/hoplite_boots.png",
                "assets/images/paperdoll/shield/hoplite_shield.png",
                "assets/images/paperdoll/shield/large_shield.png",
                "assets/images/paperdoll/shield/body_shield_1.png",
                "assets/images/paperdoll/cloak/cloak.png"
        };

        for (String path : batchSampleImages) {
            File file = resolveFile(path);
            assertTrue("Sample asset should exist: " + path, file.exists());
            BufferedImage img = ImageIO.read(file);
            assertNotNull("Should decode as image: " + path, img);
            assertEquals("Width must be 1024 for " + path, EXPECTED_WIDTH, img.getWidth());
            assertEquals("Height must be 1536 for " + path, EXPECTED_HEIGHT, img.getHeight());
        }
    }
}
