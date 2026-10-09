package com.bpm.minotaur.playtest;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaytestReportTest {

    @Test
    public void testReportGeneration() {
        PlaytestReport report = new PlaytestReport();
        
        PlaytestReport.ScenarioResult r1 = report.beginScenario("shelter-roads");
        r1.log("Step 1 done");
        r1.log("Step 2 done");
        r1.finish(true, 120, null, null, null);

        PlaytestReport.ScenarioResult r2 = report.beginScenario("seal-lords");
        r2.log("Engaged boss");
        r2.finish(false, 300, "Boss did not spawn", "NullPointerException at line 42", "failure_seal_lords.png");

        assertEquals(2, report.getResults().size());
        assertEquals(1, report.getPassedCount());
        assertEquals(1, report.getFailedCount());
        assertFalse(report.isAllPassed());

        String text = report.formatTextSummary();
        assertTrue(text.contains("shelter-roads: PASSED"));
        assertTrue(text.contains("seal-lords: FAILED"));
        assertTrue(text.contains("Boss did not spawn"));

        String json = report.formatJson();
        assertTrue(json.contains("\"name\": \"shelter-roads\""));
        assertTrue(json.contains("\"passed\": true"));
        assertTrue(json.contains("\"name\": \"seal-lords\""));
        assertTrue(json.contains("\"passed\": false"));
    }
}
