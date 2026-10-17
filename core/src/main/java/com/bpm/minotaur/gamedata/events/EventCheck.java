package com.bpm.minotaur.gamedata.events;

/** A roll against one attribute, or a pure luck roll when {@link #attribute} is null. */
public class EventCheck {
    /** One of STR, DEX, CON, INT, WIS, AGI, CHA; null for a luck roll. */
    public String attribute;
    /** The chance at attribute 10 and luck 0. */
    public float base = 0.5f;
}
