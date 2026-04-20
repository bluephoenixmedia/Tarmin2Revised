package com.bpm.minotaur.managers;

import com.badlogic.gdx.ApplicationLogger;
import com.badlogic.gdx.Gdx;

/**
 * A logger that writes to both the standard LibGDX application logger (console)
 * and the BalanceLogger (file).
 */
public class DualLogger implements ApplicationLogger {

    private final ApplicationLogger consoleLogger;

    public DualLogger(ApplicationLogger existingLogger) {
        this.consoleLogger = existingLogger;
    }

    @Override
    public void log(String tag, String message) {
        // 1. Console
        if (consoleLogger != null) {
            consoleLogger.log(tag, message);
        } else {
            System.out.println("[" + tag + "] " + message);
        }

        // 2. File (BalanceLogger)
        // Avoid infinite recursion if BalanceLogger uses Gdx.app.log
        if (!"BALANCE_LOG".equals(tag)) {
            BalanceLogger.getInstance().log(tag, message);
        }
    }

    @Override
    public void log(String tag, String message, Throwable exception) {
        log(tag, message + "\n" + getStackTrace(exception));
        exception.printStackTrace(); // Ensure it hits console stderr
    }

    @Override
    public void error(String tag, String message) {
        // 1. Console
        if (consoleLogger != null) {
            consoleLogger.error(tag, message);
        } else {
            System.err.println("[" + tag + "] ERROR: " + message);
        }

        // 2. File
        BalanceLogger.getInstance().log("ERROR", "[" + tag + "] " + message);
    }

    @Override
    public void error(String tag, String message, Throwable exception) {
        error(tag, message + "\n" + getStackTrace(exception));
        exception.printStackTrace();
    }

    @Override
    public void debug(String tag, String message) {
        // 1. Console
        if (consoleLogger != null) {
            consoleLogger.debug(tag, message);
        }

        // 2. File - We might want to filter debugs if they are too verbose,
        // but user asked for EVERYTHING.
        BalanceLogger.getInstance().log("DEBUG", "[" + tag + "] " + message);
    }

    @Override
    public void debug(String tag, String message, Throwable exception) {
        debug(tag, message + "\n" + getStackTrace(exception));
    }

    private String getStackTrace(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }
}
