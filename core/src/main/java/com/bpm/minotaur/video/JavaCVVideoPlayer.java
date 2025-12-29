package com.bpm.minotaur.video;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.ffmpeg.global.avutil;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaCVVideoPlayer implements Disposable {

    private FFmpegFrameGrabber grabber;
    private Texture texture;
    private Pixmap pixmap;
    private AudioDevice audioDevice;

    private Thread playbackThread;
    private volatile boolean isPlaying = false;
    private final Object frameLock = new Object();
    private ByteBuffer transferBuffer;
    private int width, height;
    private boolean newFrameReady = false;

    private OnCompletionListener completionListener;

    public interface OnCompletionListener {
        void onCompletion(FileHandle file);
    }

    public void setOnCompletionListener(OnCompletionListener listener) {
        this.completionListener = listener;
    }

    public void play(FileHandle file) {
        stop(); // Stop previous if any

        // Set playing true immediately to reflect intent, preventing external checks
        // from thinking we are done.
        isPlaying = true;
        playbackThread = new Thread(() -> {
            try {
                // Initialize grabber
                // Try to resolve as physical file first for performance/seeking
                java.io.File f = null;
                try {
                    f = file.file();
                    if (!f.exists()) {
                        java.io.File f2 = new java.io.File("assets/" + file.path());
                        if (f2.exists())
                            f = f2;
                    }
                } catch (Exception ignored) {
                }

                if (f != null && f.exists()) {
                    grabber = new FFmpegFrameGrabber(f);
                } else {
                    grabber = new FFmpegFrameGrabber(file.read());
                }
                grabber.setFormat("mp4");
                // Force RGBA output for direct upload to LibGDX Texture
                grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
                // Force 16-bit PCM for audio
                grabber.setSampleFormat(avutil.AV_SAMPLE_FMT_S16);
                grabber.start();

                width = grabber.getImageWidth();
                height = grabber.getImageHeight();
                double frameRate = grabber.getFrameRate();
                if (frameRate <= 0)
                    frameRate = 30; // Fallback

                // Init audio
                int channels = grabber.getAudioChannels();
                int sampleRate = grabber.getSampleRate();
                if (channels > 0) {
                    // LibGDX AudioDevice expects isMono boolean. 1 channel = mono = true.
                    audioDevice = Gdx.audio.newAudioDevice(sampleRate, channels == 1);
                }

                isPlaying = true;

                int frameCount = 0;
                long lastVideoTime = System.nanoTime();
                long frameDurationNs = (long) (1_000_000_000.0 / frameRate);

                while (isPlaying) {
                    Frame frame = grabber.grab();
                    if (frame == null) {
                        break;
                    }

                    // Handle Audio (Process immediately)
                    if (frame.samples != null && audioDevice != null) {
                        ShortBuffer sb = (ShortBuffer) frame.samples[0];
                        if (sb != null) {
                            int limit = sb.limit();
                            // Re-use array
                            if (audioBuffer == null || audioBuffer.length < limit) {
                                audioBuffer = new short[limit * 2]; // Allocate with some headroom
                            }
                            sb.get(audioBuffer, 0, limit);
                            // Write samples (this BLOCKS if buffer is full, helping sync)
                            audioDevice.writeSamples(audioBuffer, 0, limit);
                        }
                    }

                    // Handle Video
                    if (frame.image != null) {
                        frameCount++;

                        // Copy frame data safely
                        synchronized (frameLock) {
                            ByteBuffer src = (ByteBuffer) frame.image[0];
                            src.clear();

                            if (transferBuffer == null || transferBuffer.capacity() < src.capacity()) {
                                transferBuffer = ByteBuffer.allocateDirect(src.capacity());
                            }
                            transferBuffer.clear();
                            transferBuffer.put(src);
                            transferBuffer.flip();
                            newFrameReady = true;
                        }

                        // Robust Timing Logic:
                        // We want to maintain 1/FPS interval between video frames.
                        // We count time from the *previous* video frame to this one.
                        long now = System.nanoTime();
                        long elapsedNs = now - lastVideoTime;
                        long sleepNs = frameDurationNs - elapsedNs;

                        if (sleepNs > 0) {
                            try {
                                long sleepMs = sleepNs / 1_000_000;
                                int sleepNanos = (int) (sleepNs % 1_000_000);
                                Thread.sleep(sleepMs, sleepNanos);
                            } catch (InterruptedException e) {
                                isPlaying = false;
                                break;
                            }
                            // Adjust lastVideoTime perfectly to maintain drift-free sync
                            lastVideoTime += frameDurationNs;
                        } else {
                            // We are late (maybe due to audio blocking or slow decoding).
                            // Don't sleep. Update lastVideoTime to now to prevent "fast forward"
                            // catch-up effect if we are VERY late, or keep it relative if slightly late?
                            // For simplicity/robustness against massive lag: reset to now.
                            lastVideoTime = now;
                        }
                    }
                }

                // Cleanup
                grabber.stop();
                grabber.release();
                isPlaying = false;

                if (completionListener != null) {
                    Gdx.app.postRunnable(() -> completionListener.onCompletion(file));
                }

            } catch (Exception e) {
                Gdx.app.error("JavaCVVideoPlayer", "Playback error", e);
                isPlaying = false;
                // Treat error as completion or just stop
                if (completionListener != null) {
                    Gdx.app.postRunnable(() -> completionListener.onCompletion(file));
                }
            }
        });

        playbackThread.setDaemon(true);
        playbackThread.setName("VideoPlaybackThread");
        playbackThread.start();
    }

    private short[] audioBuffer;

    public void update() {
        if (!isPlaying && texture == null)
            return;

        boolean frameUpdated = false;

        synchronized (frameLock) {
            if (newFrameReady && transferBuffer != null) {
                // Ensure texture/pixmap exists
                if (texture == null || texture.getWidth() != width || texture.getHeight() != height) {
                    if (texture != null)
                        texture.dispose();
                    if (pixmap != null)
                        pixmap.dispose();

                    pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
                    texture = new Texture(pixmap);
                }

                // Upload data to Pixmap (CPU copy)
                ByteBuffer dest = pixmap.getPixels();
                dest.clear(); // Reset destination buffer position
                transferBuffer.rewind(); // Reset source buffer position

                if (dest.capacity() != transferBuffer.capacity()) {
                    // Safety check
                } else {
                    dest.put(transferBuffer);
                    dest.rewind(); // Prepare for reading
                    frameUpdated = true;
                }

                newFrameReady = false;
            }
        }

        // Upload to GPU (Texture) OUTSIDE the lock to avoid blocking the decoder thread
        if (frameUpdated && texture != null) {
            texture.draw(pixmap, 0, 0);
        }
    }

    public Texture getTexture() {
        return texture;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void stop() {
        isPlaying = false;
        if (playbackThread != null) {
            try {
                playbackThread.join(1000); // Wait briefly
            } catch (InterruptedException ignored) {
            }
            playbackThread = null;
        }
        if (audioDevice != null) {
            audioDevice.dispose();
            audioDevice = null;
        }
    }

    @Override
    public void dispose() {
        stop();
        if (texture != null)
            texture.dispose();
        if (pixmap != null)
            pixmap.dispose();
        if (audioDevice != null)
            audioDevice.dispose();
    }
}
