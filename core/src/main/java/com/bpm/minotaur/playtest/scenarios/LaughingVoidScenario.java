package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.bpm.minotaur.managers.ChaosManager;
import com.bpm.minotaur.managers.ChaosSurgeEvent;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;
import com.bpm.minotaur.rendering.Skybox3DRenderer;

/**
 * Raises a short Chaos Surge and checks The Laughing Void comes and goes with it: in the sky
 * while the surge runs, gone again a few seconds after it ends.
 */
public final class LaughingVoidScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "laughing-void";
    }

    @Override
    public String description() {
        return "A three-turn Chaos Surge: the Laughing Void must manifest, then leave the sky when it ends.";
    }

    /** Both render engines keep a sky of their own, privately; a play-test may look at each. */
    private static java.util.List<Skybox3DRenderer> skies(PlaytestContext ctx) {
        java.util.List<Skybox3DRenderer> out = new java.util.ArrayList<>();
        try {
            Object screen = ctx.getGameScreen();
            for (String name : new String[]{"firstPersonRenderer", "world3DRenderer"}) {
                java.lang.reflect.Field f = screen.getClass().getDeclaredField(name);
                f.setAccessible(true);
                Object renderer = f.get(screen);
                java.lang.reflect.Field sf = renderer.getClass().getDeclaredField("skybox3DRenderer");
                sf.setAccessible(true);
                Object sky = sf.get(renderer);
                if (sky != null) out.add((Skybox3DRenderer) sky);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    /** The sky the frame is drawn with: the one whose Void has moved off rest, or the first. */
    private static Skybox3DRenderer sky(PlaytestContext ctx) {
        Skybox3DRenderer best = null;
        for (Skybox3DRenderer s : skies(ctx)) {
            if (best == null || s.getVoidAlpha() > best.getVoidAlpha()) best = s;
        }
        return best;
    }

    private static String describe(PlaytestContext ctx) {
        StringBuilder b = new StringBuilder();
        for (Skybox3DRenderer s : skies(ctx)) b.append(s.getVoidState()).append('/').append(s.getVoidAlpha()).append(' ');
        return b.toString().trim();
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("step out of camp", () -> {
            for (int i = 0; i < 6; i++) ctx.press(Input.Keys.UP);
        });
        script.wait("walked", 30);

        script.once("raise a three-turn surge", () -> {
            ChaosManager chaos = ChaosManager.getInstance();
            ChaosSurgeEvent jester = chaos.getEvent("jesters_farce");
            chaos.triggerSurge(jester, 3, ctx.getMaze(), ctx.getPlayer(), ctx.getWorldManager(), ctx.getEventManager());
            ctx.assertTrue(chaos.isSurgeActive(), "surge is running");
        });
        script.wait("void fades in", 240);
        script.once("void is up", () -> {
            Skybox3DRenderer s = sky(ctx);
            ctx.log("During surge (each sky): " + describe(ctx));
            ctx.assertTrue(s.getVoidAlpha() > 0.5f, "the Void manifests during the surge");
        });
        script.shot("void_01_during_surge", ctx);

        script.once("wait out the surge", () -> {
            for (int i = 0; i < 5; i++) ctx.press(Input.Keys.PERIOD);
        });
        script.until("surge over", 600, () -> {
            if (ctx.getFrame() % 10 == 0) ctx.press(Input.Keys.PERIOD);
            return !ChaosManager.getInstance().isSurgeActive();
        });
        script.once("surge has ended", () -> ctx.log("Surge ended at state " + sky(ctx).getVoidState()));
        script.wait("void fades out", 300);
        script.once("void is gone", () -> {
            Skybox3DRenderer s = sky(ctx);
            ctx.log("After surge (each sky): " + describe(ctx));
            ctx.assertTrue(s.getVoidAlpha() < 0.01f, "the Void leaves the sky after the surge");
            for (Skybox3DRenderer each : skies(ctx)) {
                ctx.assertTrue(each.getVoidAlpha() < 0.01f, "every sky is clear of the Void");
            }
        });
        script.shot("void_02_after_surge", ctx);
    }
}
