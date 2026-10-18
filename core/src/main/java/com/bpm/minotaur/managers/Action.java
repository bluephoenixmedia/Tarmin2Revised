package com.bpm.minotaur.managers;

/**
 * The rebindable action ids: the keys of {@link SettingsManager}'s bindings and saved settings.
 * Use these instead of string literals, so a typo is a compile error, not a badge that quietly
 * shows the wrong key.
 */
public final class Action {
    public static final String MOVE_FORWARD = "MOVE_FORWARD";
    public static final String MOVE_BACK = "MOVE_BACK";
    public static final String TURN_LEFT = "TURN_LEFT";
    public static final String TURN_RIGHT = "TURN_RIGHT";
    public static final String INTERACT = "INTERACT";
    public static final String PICKUP_DROP = "PICKUP_DROP";
    public static final String USE_ITEM = "USE_ITEM";
    public static final String DESCEND = "DESCEND";
    public static final String QUICK_HEAL = "QUICK_HEAL";
    public static final String FIRST_AID = "FIRST_AID";
    public static final String SWAP_HANDS = "SWAP_HANDS";
    public static final String SWAP_PACK = "SWAP_PACK";
    public static final String ROTATE_PACK = "ROTATE_PACK";
    public static final String ATTACK = "ATTACK";
    public static final String FIRE_RANGED = "FIRE_RANGED";
    public static final String MAP = "MAP";
    public static final String FIELD_CRAFTING = "FIELD_CRAFTING";
    public static final String FIELD_COOKING = "FIELD_COOKING";
    public static final String SKILL_TREE = "SKILL_TREE";
    public static final String TOGGLE_LANTERN = "TOGGLE_LANTERN";
    public static final String SPELLBOOK = "SPELLBOOK";
    public static final String MAP_WAYPOINT = "MAP_WAYPOINT";
    public static final String MAP_SUGGEST = "MAP_SUGGEST";
    public static final String MAP_PIN = "MAP_PIN";
    public static final String MAP_RECENTER = "MAP_RECENTER";

    private Action() {
    }
}
