package com.bpm.minotaur.managers;

import com.badlogic.gdx.Input;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Turns a pressed key into the rebindable action bound to it, so the game reads the same
 * key the Controls screen shows (#171). Actions are the {@link SettingsManager} binding
 * names; nothing here knows a default key.
 */
public final class ActionKeys {

    /** Actions the exploring player's key switch dispatches. */
    public static final List<String> WORLD_ACTIONS = List.of(
            "MOVE_FORWARD", "MOVE_BACK", "TURN_LEFT", "TURN_RIGHT",
            "INTERACT", "PICKUP_DROP", "USE_ITEM", "DESCEND",
            "REST", "FIRST_AID", "FIELD_CRAFTING", "FIELD_COOKING");

    /** Hand and pack actions, live out of combat, on the player's turn and in the combat menu. */
    public static final List<String> HAND_ACTIONS = List.of("SWAP_HANDS", "SWAP_PACK", "ROTATE_PACK");

    private final ToIntFunction<String> keyOf;

    public ActionKeys(ToIntFunction<String> keyOf) {
        this.keyOf = keyOf;
    }

    /** Reads the live bindings in {@link SettingsManager}. */
    public static ActionKeys live() {
        return new ActionKeys(SettingsManager.getInstance()::getKey);
    }

    /** True when {@code keycode} is the key bound to {@code action}. */
    public boolean is(int keycode, String action) {
        return keycode != Input.Keys.UNKNOWN && keyOf.applyAsInt(action) == keycode;
    }

    /** The first of {@code actions} bound to {@code keycode}, or null. */
    public String resolve(int keycode, List<String> actions) {
        for (String action : actions) {
            if (is(keycode, action)) return action;
        }
        return null;
    }

    /** The bound key's display name, for messages that tell the player what to press. */
    public String keyName(String action) {
        return Input.Keys.toString(keyOf.applyAsInt(action));
    }
}
