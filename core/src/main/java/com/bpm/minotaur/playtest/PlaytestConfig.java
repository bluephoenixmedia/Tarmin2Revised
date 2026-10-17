package com.bpm.minotaur.playtest;

public final class PlaytestConfig {

    private final boolean enabled;
    private final String scenario;
    private final boolean headless;
    private final int turns;
    private final Long seed;
    private final int turbo;
    private final String screenshotMode;

    private PlaytestConfig(boolean enabled, String scenario, boolean headless, int turns, Long seed, int turbo, String screenshotMode) {
        this.enabled = enabled;
        this.scenario = scenario;
        this.headless = headless;
        this.turns = turns;
        this.seed = seed;
        this.turbo = turbo;
        this.screenshotMode = screenshotMode;
    }

    public static PlaytestConfig parse(String[] args) {
        if (args == null || args.length == 0) {
            return new PlaytestConfig(false, "all", false, 500, null, 1, "checkpoints");
        }

        boolean enabled = false;
        String scenario = "all";
        boolean headless = false;
        int turns = 500;
        Long seed = null;
        int turbo = 1;
        String screenshotMode = "checkpoints";

        for (String arg : args) {
            if (arg == null) continue;
            String lower = arg.trim().toLowerCase();
            if (lower.equals("--playtest")) {
                enabled = true;
            } else if (lower.startsWith("--playtest=")) {
                enabled = true;
                scenario = lower.substring("--playtest=".length()).trim();
                if (scenario.isEmpty()) scenario = "all";
            } else if (lower.equals("--headless")) {
                headless = true;
            } else if (lower.startsWith("--turns=")) {
                try {
                    turns = Integer.parseInt(lower.substring("--turns=".length()).trim());
                } catch (NumberFormatException ignored) {}
            } else if (lower.startsWith("--seed=")) {
                try {
                    seed = Long.parseLong(lower.substring("--seed=".length()).trim());
                } catch (NumberFormatException ignored) {}
            } else if (lower.startsWith("--turbo=")) {
                try {
                    turbo = Integer.parseInt(lower.substring("--turbo=".length()).trim());
                } catch (NumberFormatException ignored) {}
            } else if (lower.startsWith("--screenshots=")) {
                screenshotMode = lower.substring("--screenshots=".length()).trim();
            }
        }

        if (headless && turbo == 1) {
            turbo = 10; // Default turbo speed in headless mode
        }

        return new PlaytestConfig(enabled, scenario, headless, turns, seed, turbo, screenshotMode);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getScenario() {
        return scenario;
    }

    public boolean isHeadless() {
        return headless;
    }

    public int getTurns() {
        return turns;
    }

    public Long getSeed() {
        return seed;
    }

    public int getTurbo() {
        return turbo;
    }

    public String getScreenshotMode() {
        return screenshotMode;
    }
}
