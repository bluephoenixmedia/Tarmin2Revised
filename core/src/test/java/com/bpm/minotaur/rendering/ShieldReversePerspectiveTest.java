package com.bpm.minotaur.rendering;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.Assert.*;

public class ShieldReversePerspectiveTest {

    private static final String[] SHIELD_ASSETS = {
            "assets/images/armor/small_shield.png",
            "assets/images/armor/large_shield.png",
            "assets/images/armor/buckler.png",
            "assets/images/armor/shield.png",
            "assets/images/armor/gallic_shield.png",
            "assets/images/armor/hoplite_shield.png",
            "assets/images/armor/body_shield_1.png",
            "assets/images/armor/body_shield_2.png",
            "assets/images/weapons/buckler_spiked.png",
            "assets/images/weapons/shield_spike.png"
    };

    private Item createItem(ItemType type, String friendlyName, String texturePath, boolean isWeapon, boolean isShield) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = friendlyName;
        t.texturePath = texturePath;
        t.isWeapon = isWeapon;
        t.isShield = isShield;
        return Item.fromTemplate(type, t);
    }

    private File resolveAssetFile(String relativePath) {
        File f = new File(relativePath);
        if (!f.exists()) {
            f = new File("../" + relativePath);
        }
        return f;
    }

    @Test
    public void testAllTenReverseShieldFilesExistAndMatchDimensions() throws Exception {
        for (String frontPath : SHIELD_ASSETS) {
            String reversePath = frontPath.replace(".png", "_reverse.png");
            File frontFile = resolveAssetFile(frontPath);
            File reverseFile = resolveAssetFile(reversePath);

            assertTrue("Front asset must exist: " + frontFile.getPath(), frontFile.exists());
            assertTrue("Reverse asset must exist: " + reverseFile.getPath(), reverseFile.exists());

            BufferedImage frontImg = ImageIO.read(frontFile);
            BufferedImage reverseImg = ImageIO.read(reverseFile);

            assertNotNull("Front image must decode: " + frontPath, frontImg);
            assertNotNull("Reverse image must decode: " + reversePath, reverseImg);

            assertEquals("Width must match 1:1 for " + reversePath, frontImg.getWidth(), reverseImg.getWidth());
            assertEquals("Height must match 1:1 for " + reversePath, frontImg.getHeight(), reverseImg.getHeight());
        }
    }

    @Test
    public void testOffHandShieldMirroringPrevention() {
        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);

        // Standard 1H off-hand weapon (dagger/sword)
        Item offHandDagger = createItem(ItemType.SWORD, "Iron Dagger", "images/weapons/dagger.png", true, false);
        overlay.setEquipment(null, offHandDagger);

        // Shield in off-hand
        Item smallShield = createItem(ItemType.SHIELD, "Small Shield", "images/armor/small_shield.png", false, true);
        overlay.setEquipment(null, smallShield);
        assertFalse("Shield in off hand must NOT be horizontally mirrored", overlay.getOffHandItem().isWeapon() && !overlay.getOffHandItem().isShield());

        // Spiked Buckler as hybrid shield-weapon
        Item spikedBuckler = createItem(ItemType.SHIELD, "Buckler, Spiked", "images/weapons/buckler_spiked.png", true, true);
        overlay.setEquipment(null, spikedBuckler);
        assertTrue("Spiked buckler is classified as shield", overlay.getOffHandItem().isShield());
    }

    @Test
    public void testReverseTextureFlagOnOverlayRefresh() {
        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);

        Item shield = createItem(ItemType.SHIELD, "Body Shield", "images/armor/body_shield_1.png", false, true);
        overlay.setEquipment(null, shield);
        assertEquals(shield, overlay.getOffHandItem());

        overlay.forceRefreshEquipment(null, shield);
        assertEquals(shield, overlay.getOffHandItem());
    }
}
