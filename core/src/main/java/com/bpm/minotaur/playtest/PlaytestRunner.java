package com.bpm.minotaur.playtest;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.playtest.scenarios.*;
import com.bpm.minotaur.screens.GameScreen;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public final class PlaytestRunner {

    private final Tarmin2 game;
    private final PlaytestConfig config;
    private final PlaytestReport report = new PlaytestReport();
    private final List<PlaytestScenario> scenarioQueue = new ArrayList<>();
    private FileHandle outDir;

    private int currentScenarioIndex = -1;
    private PlaytestScenario currentScenario;
    private PlaytestContext currentContext;
    private PlaytestScript currentScript;
    private int stepIndex;
    private int stepTicks;
    private long scenarioStartTime;
    private boolean finished;

    private static final long GLOBAL_WATCHDOG_MS = 120_000; // 120 seconds max per scenario

    public PlaytestRunner(Tarmin2 game) {
        this(game, PlaytestConfig.parse(Tarmin2.getStartupArgs()));
    }

    public PlaytestRunner(Tarmin2 game, PlaytestConfig config) {
        this.game = game;
        this.config = config;
    }

    public void start() {
        outDir = Gdx.files.local("core/build/playtest");
        outDir.mkdirs();

        registerScenarios();

        Gdx.app.log("Playtest", "Starting QA Playtest Suite with " + scenarioQueue.size() + " scenarios (scenario="
                + config.getScenario() + ", headless=" + config.isHeadless() + ", turbo=" + config.getTurbo() + ")");

        nextScenario();
        Gdx.app.postRunnable(this::tick);
    }

    private void registerScenarios() {
        String target = config.getScenario().toLowerCase().trim();

        List<PlaytestScenario> catalog = List.of(
                new ShelterRoadsScenario(),
                new StrataDescentScenario(),
                new SealLordsScenario(),
                new SealLordDuelScenario(),
                new BridgeOfSoulsScenario(),
                new RespawnScenario(),
                new TarminsKnellScenario(),
                new TownsAndTradeScenario(),
                new SurfaceWarsMegabeastsScenario(),
                new CastleTarminScenario(),
                new DeathAndRemainsScenario(),
                new StochasticExplorerScenario()
        );

        if (target.equals("all")) {
            // "all" runs all 7 milestone scenarios
            for (PlaytestScenario s : catalog) {
                if (!s.name().equals("explore")) {
                    scenarioQueue.add(s);
                }
            }
        } else {
            for (PlaytestScenario s : catalog) {
                if (s.name().equalsIgnoreCase(target)) {
                    scenarioQueue.add(s);
                    break;
                }
            }
            if (scenarioQueue.isEmpty()) {
                Gdx.app.error("Playtest", "Unknown scenario '" + target + "', defaulting to all milestone scenarios");
                for (PlaytestScenario s : catalog) {
                    if (!s.name().equals("explore")) scenarioQueue.add(s);
                }
            }
        }
    }

    private void nextScenario() {
        currentScenarioIndex++;
        if (currentScenarioIndex >= scenarioQueue.size()) {
            finishSuite();
            return;
        }

        currentScenario = scenarioQueue.get(currentScenarioIndex);
        stepIndex = 0;
        stepTicks = 0;
        scenarioStartTime = System.currentTimeMillis();

        Gdx.app.log("Playtest", "=================================================");
        Gdx.app.log("Playtest", "STARTING SCENARIO: " + currentScenario.name());
        Gdx.app.log("Playtest", "DESCRIPTION: " + currentScenario.description());
        Gdx.app.log("Playtest", "=================================================");

        // Dispose previous GameScreen and its native Bullet resources
        if (currentContext != null && currentContext.getScreen() != null) {
            try {
                currentContext.getScreen().dispose();
            } catch (Throwable t) {
                Gdx.app.error("Playtest", "Error disposing previous screen: " + t.getMessage());
            }
        }

        // Fresh GameScreen isolation
        com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance().resetForNewGame();
        GameScreen freshScreen = new GameScreen(game, 1, Difficulty.MEDIUM, GameMode.ADVANCED);
        ShelterAltar.getInstance().setDebugAllUnlocked(true);
        game.setScreen(freshScreen);

        PlaytestReport.ScenarioResult res = report.beginScenario(currentScenario.name());
        currentContext = new PlaytestContext(game, freshScreen, config, res, outDir);
        currentScript = new PlaytestScript();

        try {
            currentScenario.buildScript(currentScript, currentContext);
        } catch (Throwable t) {
            handleScenarioFailure("Failed to build script for " + currentScenario.name(), t);
        }
    }

    private void tick() {
        if (finished) return;

        int turbo = Math.max(1, config.getTurbo());
        for (int i = 0; i < turbo; i++) {
            if (!advanceOneTick()) {
                break;
            }
        }

        if (!finished) {
            Gdx.app.postRunnable(this::tick);
        }
    }

    private boolean advanceOneTick() {
        if (currentScript == null || currentScenario == null) return false;

        // Global watchdog check
        if (System.currentTimeMillis() - scenarioStartTime > GLOBAL_WATCHDOG_MS) {
            handleScenarioFailure("Global Watchdog Timeout: Scenario exceeded " + (GLOBAL_WATCHDOG_MS / 1000) + "s", null);
            return false;
        }

        List<PlaytestScript.Step> steps = currentScript.getSteps();
        if (stepIndex >= steps.size()) {
            // Scenario passed!
            currentContext.log("Scenario " + currentScenario.name() + " finished successfully with " + steps.size() + " steps.");
            currentContext.getScenarioResult().finish(true, steps.size(), null, null, null);
            nextScenario();
            return false;
        }

        PlaytestScript.Step s = steps.get(stepIndex);
        try {
            boolean done = s.tick.getAsBoolean();
            stepTicks++;
            currentContext.incrementFrame();

            if (done) {
                stepIndex++;
                stepTicks = 0;
            } else if (stepTicks >= s.maxTicks) {
                handleScenarioFailure("Step timeout: '" + s.name + "' exceeded maxTicks=" + s.maxTicks, null);
                return false;
            }
        } catch (Throwable t) {
            handleScenarioFailure("Exception in step '" + s.name + "': " + t.getMessage(), t);
            return false;
        }

        return true;
    }

    private void handleScenarioFailure(String message, Throwable error) {
        String shotName = "failure_" + currentScenario.name();
        if (currentContext != null) {
            currentContext.capture(shotName);
        }

        String stackTrace = null;
        if (error != null) {
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            stackTrace = sw.toString();
        }

        if (error != null) {
            Gdx.app.error("Playtest", "SCENARIO FAILED: " + message, error);
        } else {
            Gdx.app.error("Playtest", "SCENARIO FAILED: " + message);
        }
        if (currentContext != null && currentContext.getScenarioResult() != null) {
            currentContext.getScenarioResult().finish(false, stepIndex, message, stackTrace, shotName + ".png");
        }

        nextScenario();
    }

    public static boolean hadFailure = false;

    private void finishSuite() {
        finished = true;
        report.save(outDir);

        Gdx.app.log("Playtest", "=================================================");
        Gdx.app.log("Playtest", "QA PLAYTEST SUITE COMPLETED");
        Gdx.app.log("Playtest", String.format("Total: %d | Passed: %d | Failed: %d",
                report.getResults().size(), report.getPassedCount(), report.getFailedCount()));
        Gdx.app.log("Playtest", "Artifacts saved to " + outDir.path());
        Gdx.app.log("Playtest", "=================================================");

        hadFailure = !report.isAllPassed();
        if (hadFailure) {
            Gdx.app.error("Playtest", "PLAYTEST SUITE FAILED (" + report.getFailedCount() + " failures).");
        } else {
            Gdx.app.log("Playtest", "ALL SCENARIOS PASSED.");
        }

        Gdx.app.exit();
    }

    public PlaytestReport getReport() {
        return report;
    }
}
