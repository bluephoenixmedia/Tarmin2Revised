package com.bpm.minotaur.playtest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class PlaytestScript {

    public static final class Step {
        public final String name;
        public final int maxTicks;
        public final BooleanSupplier tick;

        public Step(String name, int maxTicks, BooleanSupplier tick) {
            this.name = name;
            this.maxTicks = maxTicks;
            this.tick = tick;
        }
    }

    private final List<Step> steps = new ArrayList<>();

    public void add(Step step) {
        steps.add(step);
    }

    public void wait(String name, int ticks) {
        int[] counter = {0};
        add(new Step(name, ticks + 10, () -> ++counter[0] >= ticks));
    }

    public void once(String name, Runnable action) {
        add(new Step(name, 1, () -> {
            action.run();
            return true;
        }));
    }

    public void key(String name, int keycode, PlaytestContext ctx) {
        once(name, () -> ctx.press(keycode));
        wait(name + " (settle)", 8);
    }

    public void repeat(String name, int times, int keycode, PlaytestContext ctx) {
        int[] done = {0};
        add(new Step(name, times * 8 + 20, () -> {
            if (ctx.getFrame() % 6 != 0) return false;
            ctx.press(com.badlogic.gdx.Input.Keys.END);
            ctx.press(keycode);
            return ++done[0] >= times;
        }));
    }

    public void shot(String name, PlaytestContext ctx) {
        once("screenshot " + name, () -> ctx.capture(name));
    }

    public void until(String name, int maxTicks, BooleanSupplier condition) {
        add(new Step(name, maxTicks, condition));
    }

    public List<Step> getSteps() {
        return steps;
    }
}
