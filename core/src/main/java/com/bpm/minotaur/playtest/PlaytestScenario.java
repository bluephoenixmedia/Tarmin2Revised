package com.bpm.minotaur.playtest;

public interface PlaytestScenario {

    /** Identifier used by --playtest=<name> (e.g., "shelter-roads", "towns"). */
    String name();

    /** Human-readable description of what this scenario tests. */
    String description();

    /** Builds the steps to execute against the scenario context. */
    void buildScript(PlaytestScript script, PlaytestContext ctx);
}
