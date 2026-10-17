package com.bpm.minotaur.gamedata.events;

import java.util.ArrayList;
import java.util.List;

/**
 * One button in a choice event. Resolution runs in order: gates, then costs, then the check,
 * then {@link #success} or {@link #failure}.
 */
public class EventChoice {
    public String text;
    /** Conditions that grey the button out when unmet. */
    public List<EventGate> requires = new ArrayList<>();
    /** Paid up front, before any roll. An unaffordable cost greys the button out like a gate. */
    public List<EventOutcome> costs = new ArrayList<>();
    /** Null means the choice always succeeds. */
    public EventCheck check;
    public List<EventOutcome> success = new ArrayList<>();
    public List<EventOutcome> failure = new ArrayList<>();

    /** True when nothing can grey this choice out: no gates and no costs. */
    public boolean isUngated() {
        return (requires == null || requires.isEmpty()) && (costs == null || costs.isEmpty());
    }
}
