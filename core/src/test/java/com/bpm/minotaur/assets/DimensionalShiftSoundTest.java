package com.bpm.minotaur.assets;

import org.junit.Test;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * The dimensional shift effect plays whenever the world shifts into or out of Retro. It was
 * delivered as 24-bit PCM, which libGDX cannot decode, and a sound the backend rejects is silently
 * skipped, so the shift would have been mute. This pins the converted file to something the
 * backend can play.
 */
public class DimensionalShiftSoundTest {

    private static File sound() {
        File f = new File("assets/sounds/dimensional_shift.wav");
        return f.isFile() ? f : new File("../assets/sounds/dimensional_shift.wav");
    }

    @Test
    public void theEffectExists() {
        assertTrue("dimensional_shift.wav is missing: " + sound().getAbsolutePath(), sound().isFile());
    }

    @Test
    public void theEffectIsSixteenBitPcmTheBackendCanDecode() throws Exception {
        byte[] data = Files.readAllBytes(sound().toPath());
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("RIFF", new String(data, 0, 4, "US-ASCII"));
        assertEquals("WAVE", new String(data, 8, 4, "US-ASCII"));

        int pos = 12;
        int format = -1;
        int bits = -1;
        int channels = -1;
        int rate = -1;
        int dataBytes = -1;
        while (pos + 8 <= data.length) {
            String id = new String(data, pos, 4, "US-ASCII");
            int size = buf.getInt(pos + 4);
            if (id.equals("fmt ")) {
                format = buf.getShort(pos + 8) & 0xFFFF;
                channels = buf.getShort(pos + 10);
                rate = buf.getInt(pos + 12);
                bits = buf.getShort(pos + 22);
            } else if (id.equals("data")) {
                dataBytes = size;
                break;
            }
            pos += 8 + size + (size & 1);
        }

        assertEquals("PCM", 1, format);
        assertEquals("libGDX decodes only 8- and 16-bit PCM", 16, bits);
        assertTrue(dataBytes > 0);
        double seconds = dataBytes / (double) (rate * channels * (bits / 8));
        assertTrue("the shift should be a few seconds long, was " + seconds, seconds > 2.0 && seconds < 12.0);
    }
}
