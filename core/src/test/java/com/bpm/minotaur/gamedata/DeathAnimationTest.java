package com.bpm.minotaur.gamedata;

import com.bpm.minotaur.gamedata.monster.DeathAnimation;
import com.bpm.minotaur.gamedata.monster.DeathAnimationCatalog;
import com.bpm.minotaur.gamedata.prop.PropCatalog;
import org.junit.Before;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import static org.junit.Assert.*;

/**
 * The baked death atlases, and the corpse that keeps their last frame.
 *
 * <p>The atlases come from {@code tools/build_death_animations.py}; these
 * checks are what a regenerated atlas has to keep true for the renderer.
 */
public class DeathAnimationTest {

    @Before
    public void fresh() {
        DeathAnimationCatalog.resetInstance();
    }

    @Test
    public void everyAtlasMatchesItsGridAndEveryFrameStandsOnTheFloor() throws Exception {
        int checked = 0;
        for (DeathAnimation anim : DeathAnimationCatalog.getInstance().all()) {
            String name = anim.getSheetPath();
            BufferedImage img = ImageIO.read(PropCatalog.resolve(name).file());
            int rows = (anim.getFrameCount() + anim.getColumns() - 1) / anim.getColumns();
            assertEquals(name + " width", anim.getColumns() * anim.getFrameWidth(), img.getWidth());
            assertEquals(name + " height", rows * anim.getFrameHeight(), img.getHeight());
            assertTrue(name + " needs a death, not a still", anim.getFrameCount() >= 4);
            assertTrue(name + " body larger than its cell",
                    anim.getBodyWidth() <= anim.getFrameWidth() && anim.getBodyHeight() <= anim.getFrameHeight());

            for (int i = 0; i < anim.getFrameCount(); i++) {
                int x0 = anim.frameX(i), y0 = anim.frameY(i);
                int floorRow = y0 + anim.getFrameHeight() - 1;
                boolean onFloor = false;
                boolean leftHalf = false, rightHalf = false;
                for (int x = x0; x < x0 + anim.getFrameWidth(); x++) {
                    if ((img.getRGB(x, floorRow) >>> 24) > 16) onFloor = true;
                    for (int y = y0; y <= floorRow; y += 2) {
                        if ((img.getRGB(x, y) >>> 24) > 16) {
                            if (x < x0 + anim.getFrameWidth() / 2) leftHalf = true; else rightHalf = true;
                            break;
                        }
                    }
                }
                assertTrue(name + " frame " + i + " floats above the floor line", onFloor);
                assertTrue(name + " frame " + i + " is not centred in its cell", leftHalf && rightHalf);
            }
            assertNotNull(anim.getMonsterTexture() + " must name a real monster texture",
                    PropCatalog.resolve(anim.getMonsterTexture()));
            checked++;
        }
        assertEquals("every sheet except the merchant's is wired", 41, checked);
    }

    @Test
    public void everyDeathLastsLongEnoughToWatch() {
        for (DeathAnimation anim : DeathAnimationCatalog.getInstance().all()) {
            String name = anim.getSheetPath();
            assertTrue(name + " frames flash past", anim.getFrameDuration() >= 0.15f);
            assertTrue(name + " frames stutter", anim.getFrameDuration() <= 0.35f);
            assertTrue(name + " is over before it registers", anim.getDuration() >= 1.5f);
        }
    }

    @Test
    public void theAnimationSettlesOnTheLastFrame() {
        DeathAnimation goblin = DeathAnimationCatalog.getInstance().forMonsterTexture("images/monsters/goblin.png");
        assertNotNull(goblin);
        assertEquals(16, goblin.getFrameCount());
        assertEquals(0, goblin.frameAt(0f));
        assertEquals(1, goblin.frameAt(goblin.getFrameDuration() * 1.5f));
        assertEquals(goblin.getFinalFrame(), goblin.frameAt(Float.MAX_VALUE));
    }

    @Test
    public void sheetsWithUnevenRowsKeepTheirOwnFrameCounts() {
        DeathAnimationCatalog c = DeathAnimationCatalog.getInstance();
        assertEquals(7, c.forMonsterTexture("images/monsters/dwarf.png").getFrameCount());
        assertEquals(13, c.forMonsterTexture("images/monsters/troll.png").getFrameCount());
        assertEquals(9, c.forMonsterTexture("images/monsters/purple_worm.png").getFrameCount());
        assertEquals(8, c.forMonsterTexture("images/monsters/giant.png").getFrameCount());
        assertEquals(8, c.forMonsterTexture("images/monsters/giant_scorpion.png").getFrameCount());
    }

    @Test
    public void aMonsterWithoutDeathArtHasNone() {
        assertNull(DeathAnimationCatalog.getInstance().forMonsterTexture("images/monsters/cube.png"));
        assertNull(DeathAnimationCatalog.getInstance().forMonsterTexture(null));
    }

    @Test
    public void theDeathClockCountsRenderedFramesNotWallTime() {
        Scenery corpse = new Scenery(Scenery.SceneryType.MONSTER_REMAINS, 0, 0, "x.png");
        corpse.startDeathAnimation();
        assertEquals(0f, corpse.getDeathAnimSeconds(), 0f);

        // A two-second hitch while the sheet loads moves it on by one short step only.
        corpse.advanceDeathAnimation(2f, 1f / 30f, 1L);
        assertEquals(1f / 30f, corpse.getDeathAnimSeconds(), 1e-6f);

        // A second draw in the same render frame does not count twice.
        corpse.advanceDeathAnimation(1f / 60f, 1f / 30f, 1L);
        assertEquals(1f / 30f, corpse.getDeathAnimSeconds(), 1e-6f);

        corpse.advanceDeathAnimation(1f / 60f, 1f / 30f, 2L);
        assertEquals(1f / 30f + 1f / 60f, corpse.getDeathAnimSeconds(), 1e-6f);
    }

    @Test
    public void aSavedCorpseComesBackAsTheFinalFrame() {
        Scenery corpse = new Scenery(Scenery.SceneryType.MONSTER_REMAINS, 3, 4,
                "images/monsters/death_frames/goblin.png");
        corpse.setDeathAnimId("images/monsters/goblin.png");
        corpse.setDeathWidth(0.8f);
        corpse.setDeathHeight(0.8f);
        corpse.startDeathAnimation();

        ChunkData.SceneryData saved = new ChunkData.SceneryData(corpse);
        assertEquals("images/monsters/goblin.png", saved.deathAnimId);
        assertEquals(0.8f, saved.deathWidth, 0f);
        assertEquals(0.8f, saved.deathHeight, 0f);

        Scenery restored = new Scenery(saved.type, saved.x, saved.y, saved.texturePath);
        restored.setDeathAnimId(saved.deathAnimId);
        DeathAnimation anim = DeathAnimationCatalog.getInstance().forMonsterTexture(restored.getDeathAnimId());
        assertEquals(anim.getFinalFrame(), anim.frameAt(restored.getDeathAnimSeconds()));
    }
}
