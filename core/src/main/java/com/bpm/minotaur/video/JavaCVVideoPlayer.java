package com.bpm.minotaur.video;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.ffmpeg.global.avutil;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaCVVideoPlayer implements Disposable {

    private FFmpegFrameGrabber grabber;
    private Texture texture;
    private Pixmap pixmap;

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
                grabber.start();

                width = grabber.getImageWidth();
                height = grabber.getImageHeight();
                double frameRate = grabber.getFrameRate();
                if (frameRate <= 0)
                    frameRate = 30; // Fallback

                isPlaying = true;

                int frameCount = 0;
                while (isPlaying) {
                    long startTime = System.currentTimeMillis();

                    Frame frame = grabber.grabImage();
                    if (frame == null) {
                        Gdx.app.log("JavaCVVideoPlayer", "Frame is null, stopping. Count: " + frameCount);
                        break; // End of stream
                    }
                    if (frame.image == null) {
                        // Sometimes audio frames return non-null frame but null image?
                        // But grabImage() should handle this.
                        // Gdx.app.log("JavaCVVideoPlayer", "Frame image is null");
                        continue;
                    }

                    frameCount++;

                    // Copy frame data safely
                    synchronized (frameLock) {
                        ByteBuffer src = (ByteBuffer) frame.image[0];
                        // IMPORTANT: Reset position of source buffer just in case
                        src.clear();

                        if (transferBuffer == null || transferBuffer.capacity() < src.capacity()) {
                            transferBuffer = ByteBuffer.allocateDirect(src.capacity());
                        }
                        transferBuffer.clear();
                        transferBuffer.put(src); // Copy data
                        transferBuffer.flip();
                        newFrameReady = true;
                    }

                    // Throttle to frame rate
                    long processingTime = System.currentTimeMillis() - startTime;
                    long sleepTime = (long) (1000.0 / frameRate) - processingTime;

                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            isPlaying = false;
                            break;
                        }
                    }
                }
                Gdx.app.log("JavaCVVideoPlayer", "Playback loop finished. Total frames: " + frameCount);

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

    public void update() {
        if (!isPlaying && texture == null)
            return;

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

                // Upload data
                ByteBuffer dest = pixmap.getPixels();
                dest.clear(); // Reset destination buffer position
                transferBuffer.rewind(); // Reset source buffer position (already flipped in thread, but good safety)

                if (dest.capacity() != transferBuffer.capacity()) {
                    // This should rarely happen if width/height match, but safety first
                    Gdx.app.error("JavaCVVideoPlayer",
                            "Buffer mismatch! Dest: " + dest.capacity() + ", Src: " + transferBuffer.capacity());
                } else {
                    dest.put(transferBuffer);
                    dest.rewind(); // Prepare for reading by Texture

                    // Re-bind texture with new data
                    texture.draw(pixmap, 0, 0);
                }

                newFrameReady = false;
            }
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
    }

    @Override
    public void dispose() {
        stop();
        if (texture != null)
            texture.dispose();
        if (pixmap != null)
            pixmap.dispose();
    }
}
