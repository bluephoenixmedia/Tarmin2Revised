package com.bpm.minotaur.lwjgl3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Writes any uncaught exception to logs/crash_&lt;time&gt;.log before the game dies.
 *
 * A crash's stack trace otherwise only reaches the console the game was launched from,
 * and the game's own logs just stop mid-line -- so a crash seen while playing left
 * nothing behind to diagnose it from.
 */
final class CrashReporter {

    private CrashReporter() {
    }

    /** Covers every thread other than the one running the game loop. */
    static void install() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            write(thread, error);
            error.printStackTrace();
        });
    }

    /** Records a crash and returns where it was written, or null if it could not be. */
    static File write(Thread thread, Throwable error) {
        try {
            File dir = new File("logs");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File file = new File(dir, "crash_" + stamp + ".log");
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8))) {
                out.println("Tarmin2 crashed at " + new Date() + " on thread \"" + thread.getName() + "\"");
                out.println("Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name"));
                out.println();
                error.printStackTrace(out);
            }
            System.err.println("Crash report written to " + file.getAbsolutePath());
            return file;
        } catch (Throwable ignored) {
            // Reporting must never hide the original crash.
            return null;
        }
    }
}
