package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.bpm.minotaur.gamedata.arts.CombatArt;
import com.bpm.minotaur.gamedata.item.WeaponFamily;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;
import com.bpm.minotaur.screens.GameScreen;
import com.bpm.minotaur.screens.PauseScreen;
import com.bpm.minotaur.screens.SkillTreeScreen;

/**
 * Showing Mastery and the Arts (#249): the Art-learned card, the Skill Tree's Weapon Mastery page
 * with a kept Art to retrain, and the pause screen's count of Arts remembered. Screenshots each.
 */
public final class MasteryUiScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "mastery-ui";
    }

    @Override
    public String description() {
        return "Learn an Art (card), open the Skill Tree's Weapon Mastery page, then pause.";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("train and learn", () -> {
            Player p = ctx.getPlayer();
            p.getStats().getMastery().setRank(WeaponFamily.AXE, Rank.ADEPT);
            for (int i = 0; i < 12; i++) p.getStats().getMastery().recordHit(WeaponFamily.AXE);
            p.getStats().getMastery().setRank(WeaponFamily.BLUNT, Rank.NOVICE); // below Ringing Blow: kept
            p.learnArt(CombatArt.RINGING_BLOW, null);
            p.learnArt(CombatArt.WIDE_CHOP, null);
            p.learnArt(CombatArt.HEW_THROUGH, ctx.getEventManager()); // this one announces itself
        });
        script.wait("the card shows", 40);
        script.shot("art_learned_card", ctx);
        script.once("open the Skill Tree", () -> {
            GameScreen gs = ctx.getGameScreen();
            ctx.getGame().setScreen(new SkillTreeScreen(ctx.getGame(), gs, ctx.getPlayer(), ctx.getMaze()));
        });
        script.wait("tree opens", 10);
        script.shot("skill_tree_skills", ctx);
        script.key("Weapon Mastery page", Input.Keys.M, ctx);
        script.wait("page shows", 10);
        script.shot("skill_tree_mastery", ctx);
        script.once("pause", () -> ctx.getGame().setScreen(new PauseScreen(ctx.getGame(), ctx.getGameScreen())));
        script.wait("pause opens", 10);
        script.shot("pause_arts_remembered", ctx);
        script.once("back", () -> ctx.getGame().setScreen(ctx.getGameScreen()));
        script.wait("back in the maze", 10);
    }
}
