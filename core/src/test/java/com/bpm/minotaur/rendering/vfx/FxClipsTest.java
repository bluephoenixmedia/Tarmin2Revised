package com.bpm.minotaur.rendering.vfx;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;

import static org.junit.Assert.*;

public class FxClipsTest {

    private static final String SAMPLE = "{\"atlas\":\"images/fx/a.png\",\"atlasWidth\":256,\"atlasHeight\":64,"
            + "\"clips\":{\"boom\":{\"frames\":[[0,0,32,32],[34,0,32,32],[68,0,32,32],[102,0,32,32]],"
            + "\"duration\":0.4,\"additive\":true,\"frameWidth\":32,\"frameHeight\":32}}}";

    @Test
    public void aClipIsReadFromTheData() {
        FxClips clips = FxClips.parse(new JsonReader().parse(SAMPLE));

        assertEquals("images/fx/a.png", clips.atlasPath());
        assertTrue(clips.has("boom"));
        FxClips.Clip boom = clips.get("boom");
        assertEquals(4, boom.frameCount());
        assertEquals(0.4f, boom.duration, 0.0001f);
        assertTrue(boom.additive);
        assertArrayEquals(new int[] { 34, 0, 32, 32 }, boom.frame(1));
    }

    @Test
    public void progressMapsOntoFramesAndNeverRunsOffTheEnd() {
        FxClips.Clip boom = FxClips.parse(new JsonReader().parse(SAMPLE)).get("boom");

        assertEquals(0, boom.frameIndexAt(0f));
        assertEquals(1, boom.frameIndexAt(0.30f));
        assertEquals(2, boom.frameIndexAt(0.55f));
        assertEquals(3, boom.frameIndexAt(0.99f));
        assertEquals("a finished clip holds its last frame", 3, boom.frameIndexAt(1f));
        assertEquals(3, boom.frameIndexAt(5f));
        assertEquals(0, boom.frameIndexAt(-1f));
    }

    @Test
    public void anUnknownClipIsReportedNotThrown() {
        FxClips clips = FxClips.parse(new JsonReader().parse(SAMPLE));
        assertFalse(clips.has("nope"));
        assertNull(clips.get("nope"));
    }

    @Test
    public void withoutAFileSystemTheLibraryIsEmptyNotFatal() {
        // A hit must never fail because its effect cannot load; the combat tests run with no files at all.
        com.badlogic.gdx.Files saved = com.badlogic.gdx.Gdx.files;
        com.badlogic.gdx.Gdx.files = null;
        try {
            FxClips clips = FxClips.loadOrEmpty();
            assertNotNull(clips);
            assertFalse(clips.has(FxClipIds.ALERT));
            assertNull(clips.getFrame(FxClipIds.ALERT, 0.5f));
        } finally {
            com.badlogic.gdx.Gdx.files = saved;
        }
    }

    @Test
    public void theShippedClipsAreAllPresentAndInsideTheAtlas() throws Exception {
        File f = new File("../assets/data/fx.json");
        if (!f.exists()) {
            f = new File("assets/data/fx.json");
        }
        JsonValue root = new JsonReader().parse(new FileReader(f));
        FxClips clips = FxClips.parse(root);
        int w = root.getInt("atlasWidth");
        int h = root.getInt("atlasHeight");

        for (String id : FxClipIds.ALL) {
            assertTrue("fx.json is missing the clip " + id, clips.has(id));
            FxClips.Clip clip = clips.get(id);
            assertTrue(id + " has no frames", clip.frameCount() > 0);
            assertTrue(id + " has no duration", clip.duration > 0f);
            for (int i = 0; i < clip.frameCount(); i++) {
                int[] r = clip.frame(i);
                assertTrue(id + " frame " + i + " leaves the atlas",
                        r[0] >= 0 && r[1] >= 0 && r[0] + r[2] <= w && r[1] + r[3] <= h);
            }
        }
        assertTrue("the atlas image exists", new File("../assets/" + clips.atlasPath()).exists()
                || new File("assets/" + clips.atlasPath()).exists());
    }
}
