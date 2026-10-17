package com.bpm.minotaur.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Which layer of the interface currently owns the player's input (SPEC section 5.6).
 *
 * <p>Gameplay is always the bottom of the stack and is never pushed. Opening a panel or a modal
 * pushes a context; closing it pops. Two things read the stack:
 *
 * <ul>
 *   <li>The HUD, to decide whether to draw the world interaction prompt. The level-up overlay
 *       had a corpse prompt ("[ E ] Loot Remains") drawn straight across its attribute list,
 *       because the prompt's hide-list named four overlays and the level-up modal was not one
 *       of them (LEVELUP-1, HUD-1).</li>
 *   <li>{@code GameScreen.keyDown}, so the keys a panel uses cannot also reach the world
 *       underneath. The level-up overlay allocates attributes with 1-6, which are the quick-slot
 *       keys, and E harvests (LEVELUP-5).</li>
 * </ul>
 *
 * <p>Balanced push/pop across screen transitions is the obvious failure mode, so
 * {@link #resetToGameplay()} exists and {@code GameScreen.show()} calls it: returning to the
 * world is by definition gameplay, whatever a panel forgot to pop on its way out.
 *
 * <p>Single-threaded by construction -- libGDX runs all of this on the render thread.
 */
public final class UiContexts {

    private UiContexts() {
    }

    /** How much of the interface below a context it takes over. */
    public enum Kind {
        /** A full screen or a docked pane. Keys below it are blocked; the world keeps running. */
        PANEL,
        /** Blocks everything below it, including other panels, and dims them. */
        MODAL
    }

    private static final class Entry {
        final String name;
        final Kind kind;

        Entry(String name, Kind kind) {
            this.name = name;
            this.kind = kind;
        }
    }

    private static final List<Entry> STACK = new ArrayList<>();

    /**
     * Pushes a context, or moves it to the top if it is already on the stack.
     *
     * <p>Idempotent on purpose: a screen whose {@code show()} runs twice must not leave a second
     * entry that its single {@code hide()} cannot clear.
     */
    public static void push(String name, Kind kind) {
        if (name == null) {
            return;
        }
        pop(name);
        STACK.add(new Entry(name, kind));
    }

    /** Removes a context wherever it sits. Unknown names are ignored. */
    public static void pop(String name) {
        if (name == null) {
            return;
        }
        for (int i = STACK.size() - 1; i >= 0; i--) {
            if (STACK.get(i).name.equals(name)) {
                STACK.remove(i);
                return;
            }
        }
    }

    /** Drops everything back to gameplay. Called whenever the world screen takes focus. */
    public static void resetToGameplay() {
        STACK.clear();
    }

    /** True when anything at all is open over the world. */
    public static boolean isAnyOpen() {
        return !STACK.isEmpty();
    }

    /** True when a modal is open, so nothing below it may act on a key. */
    public static boolean isModalOpen() {
        for (int i = STACK.size() - 1; i >= 0; i--) {
            if (STACK.get(i).kind == Kind.MODAL) {
                return true;
            }
        }
        return false;
    }

    /** The name of the context that owns input, or {@code "GAMEPLAY"}. */
    public static String top() {
        return STACK.isEmpty() ? "GAMEPLAY" : STACK.get(STACK.size() - 1).name;
    }

    /** True when {@code name} is the context that owns input. */
    public static boolean isTop(String name) {
        return top().equals(name);
    }

    /** How deep the stack is, for tests and the debug overlay. */
    public static int depth() {
        return STACK.size();
    }
}
