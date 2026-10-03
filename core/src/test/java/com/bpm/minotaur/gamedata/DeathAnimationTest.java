package com.bpm.minotaur.gamedata;

import com.bpm.minotaur.gamedata.monster.DeathAnimation;
import com.bpm.minotaur.gamedata.monster.DeathAnimationCatalog;
import com.bpm.minotaur.gamedata.prop.PropCatalog;
import org.junit.Before;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.Assert.*;

/** The hand-cut death sheets: every frame rectangle must lie on its sheet, and a corpse must keep its last frame. */
public class DeathAnimationTest {

    @Before
    public void fresh() {
        DeathAnimationCatalog.resetInstance();
    }

    @Test
    public void everyFrameLiesInsideItsSheet() throws Exception {
        int checked = 0;
        for (DeathAnimation anim : DeathAnimationCatalog.getInstance().all()) {
            File sheet = PropCatalog.resolve(anim.getSheetPath()).file();
            BufferedImage img = ImageIO.read(sheet);
            assertTrue(anim.getMonsterTexture() + " needs at least two frames", anim.getFrameCount() >= 2);
            for (int i = 0; i < anim.getFrameCount(); i++) {
                int[] f = anim.getFrame(i);
                String where = anim.getSheetPath() + " frame " + i;
                assertEquals(where, 4, f.length);
                assertTrue(where, f[0] >= 0 && f[1] >= 0 && f[2] > 0 && f[3] > 0);
                assertTrue(where + " overruns the sheet",
                        f[0] + f[2] <= img.getWidth() && f[1] + f[3] <= img.getHeight());
            }
            assertNotNull(anim.getMonsterTexture() + " must name a real monster texture",
                    PropCatalog.resolve(anim.getMonsterTexture()));
            checked++;
        }
        assertEquals("every sheet except the merchant's is wired", 41, checked);
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
    public void aSavedCorpseComesBackAsTheFinalFrame() {
        Scenery corpse = new Scenery(Scenery.SceneryType.MONSTER_REMAINS, 3, 4,
                "images/monsters/death_spritesheets/goblin.png");
        corpse.setDeathAnimId("images/monsters/goblin.png");
        corpse.setDeathHeight(0.8f);
        corpse.startDeathAnimation();
        assertTrue("still playing just after death", corpse.getDeathAnimSeconds() < 5f);

        ChunkData.SceneryData saved = new ChunkData.SceneryData(corpse);
        assertEquals("images/monsters/goblin.png", saved.deathAnimId);
        assertEquals(0.8f, saved.deathHeight, 0f);

        Scenery restored = new Scenery(saved.type, saved.x, saved.y, saved.texturePath);
        restored.setDeathAnimId(saved.deathAnimId);
        restored.setDeathHeight(saved.deathHeight);
        DeathAnimation anim = DeathAnimationCatalog.getInstance().forMonsterTexture(restored.getDeathAnimId());
        assertEquals(anim.getFinalFrame(), anim.frameAt(restored.getDeathAnimSeconds()));
    }
}
