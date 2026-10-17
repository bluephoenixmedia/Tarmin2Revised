package com.bpm.minotaur.playtest;

import com.bpm.minotaur.playtest.scenarios.*;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class PlaytestScenarioCatalogTest {

    @Test
    public void testScenarioCatalogIntegrity() {
        List<PlaytestScenario> scenarios = List.of(
                new ShelterRoadsScenario(),
                new StrataDescentScenario(),
                new SealLordsScenario(),
                new TownsAndTradeScenario(),
                new SurfaceWarsMegabeastsScenario(),
                new CastleTarminScenario(),
                new DeathAndRemainsScenario(),
                new StochasticExplorerScenario()
        );

        assertEquals(8, scenarios.size());

        for (PlaytestScenario s : scenarios) {
            assertNotNull("Scenario name cannot be null", s.name());
            assertFalse("Scenario name cannot be empty", s.name().trim().isEmpty());
            assertNotNull("Scenario description cannot be null", s.description());
            assertFalse("Scenario description cannot be empty", s.description().trim().isEmpty());
        }
    }
}
