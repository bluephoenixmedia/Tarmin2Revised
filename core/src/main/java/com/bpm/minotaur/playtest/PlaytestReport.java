package com.bpm.minotaur.playtest;

import com.badlogic.gdx.files.FileHandle;

import java.util.ArrayList;
import java.util.List;

public final class PlaytestReport {

    public static final class ScenarioResult {
        private final String name;
        private boolean passed;
        private long startTimeMs;
        private long durationMs;
        private int stepsCompleted;
        private final List<String> logs = new ArrayList<>();
        private String failureMessage;
        private String failureStackTrace;
        private String failureScreenshot;

        public ScenarioResult(String name) {
            this.name = name;
            this.startTimeMs = System.currentTimeMillis();
        }

        public void log(String message) {
            logs.add(message);
        }

        public void finish(boolean passed, int stepsCompleted, String failureMessage, String failureStackTrace, String failureScreenshot) {
            this.passed = passed;
            this.stepsCompleted = stepsCompleted;
            this.failureMessage = failureMessage;
            this.failureStackTrace = failureStackTrace;
            this.failureScreenshot = failureScreenshot;
            this.durationMs = System.currentTimeMillis() - startTimeMs;
        }

        public String getName() {
            return name;
        }

        public boolean isPassed() {
            return passed;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public int getStepsCompleted() {
            return stepsCompleted;
        }

        public List<String> getLogs() {
            return logs;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public String getFailureStackTrace() {
            return failureStackTrace;
        }

        public String getFailureScreenshot() {
            return failureScreenshot;
        }
    }

    private final List<ScenarioResult> results = new ArrayList<>();
    private final long suiteStartTimeMs = System.currentTimeMillis();

    public ScenarioResult beginScenario(String name) {
        ScenarioResult result = new ScenarioResult(name);
        results.add(result);
        return result;
    }

    public List<ScenarioResult> getResults() {
        return results;
    }

    public int getPassedCount() {
        int count = 0;
        for (ScenarioResult r : results) {
            if (r.isPassed()) count++;
        }
        return count;
    }

    public int getFailedCount() {
        int count = 0;
        for (ScenarioResult r : results) {
            if (!r.isPassed()) count++;
        }
        return count;
    }

    public boolean isAllPassed() {
        return !results.isEmpty() && getFailedCount() == 0;
    }

    public String formatTextSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("====================================================\n");
        sb.append("TARMIN 2 - AUTOMATED QA PLAYTEST REPORT\n");
        sb.append("====================================================\n\n");
        sb.append(String.format("Total: %d | Passed: %d | Failed: %d\n\n",
                results.size(), getPassedCount(), getFailedCount()));

        sb.append("----------------- SCENARIO SUMMARY -----------------\n");
        for (ScenarioResult r : results) {
            sb.append(String.format("- %s: %s (%d ms, %d steps)\n",
                    r.getName(),
                    r.isPassed() ? "PASSED" : "FAILED",
                    r.getDurationMs(),
                    r.getStepsCompleted()));
            if (!r.isPassed() && r.getFailureMessage() != null) {
                sb.append("  REASON: ").append(r.getFailureMessage()).append("\n");
                if (r.getFailureScreenshot() != null) {
                    sb.append("  SCREENSHOT: ").append(r.getFailureScreenshot()).append("\n");
                }
            }
        }
        sb.append("\n------------------ DETAILED LOGS -------------------\n");
        for (ScenarioResult r : results) {
            sb.append("\n>>> [").append(r.getName()).append("] <<<\n");
            for (String line : r.getLogs()) {
                sb.append("  ").append(line).append("\n");
            }
            if (!r.isPassed() && r.getFailureStackTrace() != null) {
                sb.append("  --- STACK TRACE ---\n");
                sb.append("  ").append(r.getFailureStackTrace().replace("\n", "\n  ")).append("\n");
            }
        }
        return sb.toString();
    }

    public String formatJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"total\": ").append(results.size()).append(",\n");
        sb.append("  \"passed\": ").append(getPassedCount()).append(",\n");
        sb.append("  \"failed\": ").append(getFailedCount()).append(",\n");
        sb.append("  \"durationMs\": ").append(System.currentTimeMillis() - suiteStartTimeMs).append(",\n");
        sb.append("  \"allPassed\": ").append(isAllPassed()).append(",\n");
        sb.append("  \"scenarios\": [\n");

        for (int i = 0; i < results.size(); i++) {
            ScenarioResult r = results.get(i);
            sb.append("    {\n");
            sb.append("      \"name\": \"").append(escapeJson(r.getName())).append("\",\n");
            sb.append("      \"passed\": ").append(r.isPassed()).append(",\n");
            sb.append("      \"durationMs\": ").append(r.getDurationMs()).append(",\n");
            sb.append("      \"stepsCompleted\": ").append(r.getStepsCompleted()).append(",\n");
            if (r.getFailureMessage() != null) {
                sb.append("      \"failureMessage\": \"").append(escapeJson(r.getFailureMessage())).append("\",\n");
            }
            if (r.getFailureScreenshot() != null) {
                sb.append("      \"failureScreenshot\": \"").append(escapeJson(r.getFailureScreenshot())).append("\",\n");
            }
            sb.append("      \"logCount\": ").append(r.getLogs().size()).append("\n");
            sb.append("    }").append(i < results.size() - 1 ? "," : "").append("\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public void save(FileHandle directory) {
        directory.mkdirs();
        directory.child("report.txt").writeString(formatTextSummary(), false);
        directory.child("report.json").writeString(formatJson(), false);
    }
}
