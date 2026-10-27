package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.bpm.minotaur.managers.ChaosManager;
import com.bpm.minotaur.managers.ChaosSurgeEvent;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;
import com.bpm.minotaur.rendering.FirstPersonRenderer;
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

    /** The game screen keeps its renderer private; a play-test may look. */
    private static Skybox3DRenderer sky(PlaytestContext ctx) {
        try {
            java.lang.reflect.Field f = ctx.getGameScreen().getClass().getDeclaredField("firstPersonRenderer");
            f.setAccessible(true);
            return ((FirstPersonRenderer) f.get(ctx.getGameScreen())).getSkybox3DRenderer();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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
            ctx.log("During surge: state " + s.getVoidState() + ", alpha " + s.getVoidAlpha());
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
            ctx.log("After surge: state " + s.getVoidState() + ", alpha " + s.getVoidAlpha());
            ctx.assertTrue(s.getVoidAlpha() < 0.01f, "the Void leaves the sky after the surge");
            ctx.assertEquals(Skybox3DRenderer.VoidManifestationState.INACTIVE, s.getVoidState(), "lifecycle at rest");
        });
        script.shot("void_02_after_surge", ctx);
    }
}
