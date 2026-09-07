package com.bpm.minotaur.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

import java.io.File;

/**
 * Utility to request high-performance discrete GPU execution (e.g. NVIDIA / AMD)
 * on dual-GPU laptops (Windows Optimus / AMD Enduro), and log active GPU capabilities.
 */
public class GpuHelper {

    private static final String TAG = "GpuHelper";

    /**
     * Attempts to register high-performance GPU preferences on Windows
     * via DirectX UserGpuPreferences registry keys.
     */
    public static void requestHighPerformanceGpu() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            return;
        }

        try {
            // Environment hints for graphics drivers
            System.setProperty("__NV_PRIME_RENDER_OFFLOAD", "1");
            System.setProperty("__GLX_VENDOR_LIBRARY_NAME", "nvidia");
            System.setProperty("SHIM_MCCOMPAT", "0x800000001");

            // Locate the executing java / javaw binary
            String javaHome = System.getProperty("java.home");
            if (javaHome != null) {
                File javaExe = new File(javaHome, "bin/java.exe");
                File javawExe = new File(javaHome, "bin/javaw.exe");

                if (javaExe.exists()) {
                    setDirectXPreference(javaExe.getAbsolutePath());
                }
                if (javawExe.exists()) {
                    setDirectXPreference(javawExe.getAbsolutePath());
                }
            }

            // Also check current process command if available (Java 9+)
            ProcessHandle.current().info().command().ifPresent(cmd -> {
                File exe = new File(cmd);
                if (exe.exists()) {
                    setDirectXPreference(exe.getAbsolutePath());
                }
            });
        } catch (Throwable t) {
            // Gracefully ignore any system security or process access restrictions
            System.err.println("[" + TAG + "] Could not configure Windows GPU preference: " + t.getMessage());
        }
    }

    private static void setDirectXPreference(String executablePath) {
        try {
            // Value 2 corresponds to High Performance GPU (Discrete GPU)
            ProcessBuilder pb = new ProcessBuilder(
                    "reg", "add",
                    "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences",
                    "/v", executablePath,
                    "/t", "REG_SZ",
                    "/d", "GpuPreference=2;",
                    "/f"
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.waitFor();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Logs GPU renderer, vendor, and OpenGL driver version to Gdx application log.
     * Must be called after the OpenGL context has been created.
     */
    public static void logGpuInfo() {
        try {
            String renderer = Gdx.gl.glGetString(GL20.GL_RENDERER);
            String vendor   = Gdx.gl.glGetString(GL20.GL_VENDOR);
            String version  = Gdx.gl.glGetString(GL20.GL_VERSION);
            String slVersion = Gdx.gl.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION);

            Gdx.app.log(TAG, "==================================================");
            Gdx.app.log(TAG, "Active GPU Hardware Details:");
            Gdx.app.log(TAG, "  Renderer: " + renderer);
            Gdx.app.log(TAG, "  Vendor:   " + vendor);
            Gdx.app.log(TAG, "  GL Vers:  " + version);
            Gdx.app.log(TAG, "  GLSL:     " + slVersion);
            Gdx.app.log(TAG, "==================================================");
        } catch (Throwable t) {
            Gdx.app.error(TAG, "Failed to query OpenGL strings: " + t.getMessage());
        }
    }
}
