package com.bpm.minotaur;

/**
 * What build this is, for the corner of the title screen and for bug reports.
 *
 * <p>TITLE-8: the title screen had no version anywhere, which makes a player's screenshot
 * unattributable to a build. The value is a literal rather than something read from the jar
 * manifest so it is correct when running from Gradle, from the IDE and from a distribution
 * alike; {@code BuildInfoTest} reads {@code gradle.properties} and fails if the two drift.
 */
public final class BuildInfo {

    private BuildInfo() {
    }

    /** Must match {@code projectVersion} in {@code gradle.properties}. */
    public static final String VERSION = "0.0.2-SNAPSHOT";

    /** The version as it should appear on screen. */
    public static String displayVersion() {
        return "v" + VERSION;
    }
}
