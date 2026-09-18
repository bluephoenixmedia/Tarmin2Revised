package com.bpm.minotaur.paperdoll;

import com.bpm.minotaur.paperdoll.calibration.CalibrationStore;
import com.bpm.minotaur.paperdoll.calibration.LayerCalibration;
import com.bpm.minotaur.paperdoll.calibration.PaperdollLayerMap;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PaperDoll2DTest {

    private static final int EXPECTED_WIDTH = 1024;
    private static final int EXPECTED_HEIGHT = 1536;

    private static CalibrationStore calibration;
    private static PaperdollLayerMap layerMap;

    private static File resolveFile(String relativePath) {
        File f = new File(relativePath);
        if (!f.exists()) {
            f = new File("../" + relativePath);
        }
        return f;
    }

    private static String read(String relativePath) throws IOException {
        return new String(Files.readAllBytes(resolveFile(relativePath).toPath()), StandardCharsets.UTF_8);
    }

    @BeforeClass
    public static void loadPipelineData() throws IOException {
        calibration = new CalibrationStore();
        calibration.parse(read("assets/data/paperdoll_calibration.json"));

        layerMap = new PaperdollLayerMap();
        layerMap.parse(read("assets/data/paperdoll_layers.json"));
    }

    @Test
    public void testMasterCanvasAssetsExistAndHaveStandardDimensions() throws IOException {
        String[] requiredImages = {
                "assets/images/paperdoll/base_father.png",
                "assets/images/paperdoll/head/bascinet.png",
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

    /**
     * Items that used to be served by a duplicate alias PNG must still resolve.
     *
     * The baker used to copy one image under every name a lookup might guess --
     * head/basinet.png beside head/bascinet.png, chest/banded_mail.png beside
     * chest/banded.png. Those copies are gone; the layer map carries the aliasing now.
     * This is the invariant the old filename-existence assertions were really after.
     */
    @Test
    public void testFormerAliasItemsStillResolveThroughTheLayerMap() {
        String[][] cases = {
                {"BASINET", "head/bascinet"},
                {"BANDED_MAIL", "chest/banded"},
                {"BREASTPLATE", "chest/breastplate"},
                {"GREAT_HELM", "head/great_helm"},
        };
        for (String[] c : cases) {
            PaperdollLayerMap.LayerRef ref = layerMap.resolve(c[0]);
            assertNotNull("item should map to a layer: " + c[0], ref);
            assertEquals(c[0] + " should resolve to its canonical layer", c[1], ref.layerId());
        }
    }

    /** Every layer the map points at must actually be on disk at canvas size. */
    @Test
    public void testEveryMappedLayerExistsOnTheMasterCanvas() throws IOException {
        List<String> missing = new ArrayList<String>();
        List<String> wrongSize = new ArrayList<String>();

        for (Map.Entry<String, LayerCalibration> entry : calibration.all().entrySet()) {
            String layerId = entry.getKey();
            File file = resolveFile("assets/images/paperdoll/" + layerId + ".png");
            if (!file.exists()) {
                missing.add(layerId);
                continue;
            }
            BufferedImage img = ImageIO.read(file);
            if (img == null || img.getWidth() != EXPECTED_WIDTH || img.getHeight() != EXPECTED_HEIGHT) {
                wrongSize.add(layerId);
            }
        }

        assertTrue("calibrated layers with no image on disk: " + missing, missing.isEmpty());
        assertTrue("layers not on the 1024x1536 master canvas: " + wrongSize, wrongSize.isEmpty());
    }

    /**
     * Calibration is only trustworthy if the pipeline actually wrote it. A file that
     * silently emptied out would leave every layer on the identity fallback, which
     * looks like "the doll is broken again" rather than like a missing file.
     */
    @Test
    public void testCalibrationCoversTheBakedLayerSet() {
        assertTrue("expected calibration for the full baked layer set, got " + calibration.size(),
                calibration.size() >= 300);

        LayerCalibration breastplate = calibration.get("chest/breastplate");
        assertTrue("a baked layer should carry a real placement, not identity",
                !breastplate.isIdentity());
        assertNotNull("seed calibrations record the source they were measured against",
                breastplate.sourceHash);
    }

    /** No calibration entry should point at a layer nothing can equip. */
    @Test
    public void testLayerMapOnlyReferencesCalibratedLayers() {
        List<String> orphans = new ArrayList<String>();
        for (String item : new String[]{"BASINET", "BANDED_MAIL", "BREASTPLATE", "GREAT_HELM"}) {
            PaperdollLayerMap.LayerRef ref = layerMap.resolve(item);
            if (ref != null && !calibration.has(ref.layerId())) {
                orphans.add(item + " -> " + ref.layerId());
            }
        }
        assertTrue("mapped layers missing calibration: " + orphans, orphans.isEmpty());
    }
}
