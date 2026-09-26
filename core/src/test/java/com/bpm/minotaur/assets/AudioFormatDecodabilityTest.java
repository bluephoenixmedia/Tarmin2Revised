package com.bpm.minotaur.assets;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Every WAV the game loads must be one the backend can actually decode.
 *
 * <p>A 24-bit PCM bag_open.wav killed the game the moment a new expedition
 * started: SoundManager is built in the GameScreen constructor, so nothing
 * before the main menu touches it and a boot test walks straight past the
 * problem. The file was valid audio and played fine in any editor -- it was
 * only LibGDX's decoder that refused it.
 *
 * <p>The rules below are LibGDX's own, read off
 * {@code Wav$WavInputStream} in gdx-backend-lwjgl3: PCM (format 1) must be
 * 8- or 16-bit, and IEEE float (format 3) must be 32- or 64-bit. Note that
 * 32-bit is legal for float and illegal for PCM, so bit depth alone is not
 * the test -- {@code pickup_item.wav} is float/32 and loads correctly.
 */
public class AudioFormatDecodabilityTest {

    private static final int FORMAT_PCM = 1;
    private static final int FORMAT_IEEE_FLOAT = 3;

    private File soundsDir() {
        File dir = new File("assets/sounds");
        if (!dir.isDirectory()) dir = new File("../assets/sounds");
        return dir;
    }

    @Test
    public void everyWavFileCanBeDecodedByTheAudioBackend() throws IOException {
        File dir = soundsDir();
        assertTrue("Cannot locate assets/sounds from " + new File(".").getAbsolutePath(),
                dir.isDirectory());

        List<File> wavs = new ArrayList<>();
        collectWavs(dir, wavs);
        assertFalse("Found no .wav files at all -- the test is not looking where it thinks",
                wavs.isEmpty());

        List<String> rejected = new ArrayList<>();
        for (File wav : wavs) {
            String why = whyUndecodable(wav);
            if (why != null) rejected.add(wav.getPath() + " -- " + why);
        }

        assertEquals("These WAV files will throw GdxRuntimeException when loaded: " + rejected,
                0, rejected.size());
    }

    private void collectWavs(File dir, List<File> out) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File f : entries) {
            if (f.isDirectory()) collectWavs(f, out);
            else if (f.getName().toLowerCase().endsWith(".wav")) out.add(f);
        }
    }

    /** @return null when the file is fine, else the reason the backend rejects it. */
    private String whyUndecodable(File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());
        if (data.length < 44) return "shorter than a WAV header";

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data[0] != 'R' || data[1] != 'I' || data[2] != 'F' || data[3] != 'F') {
            return "not a RIFF file";
        }

        // Walk the chunks: fmt is not always the first one. bag_open.wav carried
        // a leading JUNK chunk, which a fixed offset of 12 would have misread.
        int pos = 12;
        while (pos + 8 <= data.length) {
            String id = "" + (char) data[pos] + (char) data[pos + 1]
                    + (char) data[pos + 2] + (char) data[pos + 3];
            int size = buf.getInt(pos + 4);
            if (size < 0) return "declares a negative chunk size";
            if ("fmt ".equals(id)) {
                int format = buf.getShort(pos + 8) & 0xFFFF;
                int bits = buf.getShort(pos + 22) & 0xFFFF;
                if (format == FORMAT_PCM) {
                    return (bits == 8 || bits == 16) ? null
                            : "PCM must be 8- or 16-bit, is " + bits + "-bit";
                }
                if (format == FORMAT_IEEE_FLOAT) {
                    return (bits == 32 || bits == 64) ? null
                            : "float must be 32- or 64-bit, is " + bits + "-bit";
                }
                return "unsupported audio format code " + format + " (only PCM and float decode)";
            }
            pos += 8 + size + (size & 1); // chunks are word-aligned
        }
        return "has no fmt chunk";
    }

    @Test
    public void theCheckerActuallyRejectsABadHeader() throws IOException {
        // Without this, a checker that returned null unconditionally would make
        // the test above pass on any input.
        File tmp = File.createTempFile("bad24bit", ".wav");
        try {
            Files.write(tmp.toPath(), pcmWavHeader(24));
            assertNotNull("A 24-bit PCM header must be reported as undecodable",
                    whyUndecodable(tmp));
            Files.write(tmp.toPath(), pcmWavHeader(16));
            assertNull("A 16-bit PCM header must be accepted", whyUndecodable(tmp));
        } finally {
            assertTrue(tmp.delete() || !tmp.exists());
        }
    }

    private byte[] pcmWavHeader(int bits) {
        ByteBuffer b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16);
        b.putShort((short) FORMAT_PCM).putShort((short) 2).putInt(44100);
        b.putInt(44100 * 2 * bits / 8).putShort((short) (2 * bits / 8)).putShort((short) bits);
        b.put("data".getBytes()).putInt(0);
        return b.array();
    }
}
