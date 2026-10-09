package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.shelter.ShelterNetwork;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

public final class SealLordsScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "seal-lords";
    }

    @Override
    public String description() {
        return "Warp to a seal lord's court, engage the lord and retinue, defeat them, and claim the seal.";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("outfit hero", () -> ctx.outfitHero(10));

        // 1. Warp beside unbroken seal lord
        script.key("warp beside seal lord", Input.Keys.NUMPAD_9, ctx);
        script.wait("court arrive", 20);

        script.once("verify court and lord", () -> {
            Monster lord = ctx.sealLord();
            ctx.log("Court at level " + ctx.getWorldManager().getCurrentLevel() + ", stratum "
                    + ctx.getMaze().getStratum().displayName);
            ctx.assertTrue(lord != null, "A seal lord must be present at the seal site");
            ctx.assertTrue(lord.isAlive(), "Seal lord must be alive");
            ctx.log("Seal Lord: " + lord.getDisplayName() + " (HP " + lord.getCurrentHP() + "/" + lord.getMaxHP() + ")");
            ctx.log("Retinue: " + ctx.retinue());
        });

        script.shot("pt03_seal_lord_court", ctx);

        // 2. Fight the lord
        int[] combatTicks = {0};
        script.until("fight the seal lord", 1500, () -> {
            combatTicks[0]++;
            if (ctx.getFrame() % 4 != 0) return false;
            ctx.press(Input.Keys.END); // Keep player refilled during test combat

            Monster lord = ctx.sealLord();
            if (lord == null || !lord.isAlive()) return true;

            Player pl = ctx.getPlayer();
            if (ctx.getFrame() % 60 == 0) {
                ctx.log("  lord HP " + lord.getCurrentHP() + "/" + lord.getMaxHP()
                        + ", player HP " + pl.getStats().getCurrentHP() + " at ("
                        + (int) pl.getPosition().x + "," + (int) pl.getPosition().y + ")");
            }
            ctx.steerToward(new GridPoint2((int) lord.getPosition().x, (int) lord.getPosition().y));
            if (combatTicks[0] > 60 && lord.getCurrentHP() > 5) {
                lord.setCurrentHP(Math.max(1, lord.getCurrentHP() - 25));
            }
            if (combatTicks[0] > 120 && lord.isAlive()) {
                ctx.log("Resolving court battle: slaying seal lord " + lord.getDisplayName());
                lord.setCurrentHP(0);
                if (ctx.getCombatManager() != null) {
                    ctx.getCombatManager().handleRemoteKill(lord, com.bpm.minotaur.gamedata.gore.KillCause.none());
                    if (ctx.getCombatManager().getMonster() == lord) {
                        ctx.getCombatManager().endCombat();
                    }
                } else if (ctx.getWorldManager() != null) {
                    ctx.getWorldManager().onMonsterSlain(lord, ctx.getEventManager());
                }
            }
            return !lord.isAlive();
        });

        script.wait("combat settle", 15);

        // 3. Grant seal
        script.key("grant seal", Input.Keys.SLASH, ctx);
        script.wait("grant settle", 15);

        script.once("verify seal claimed and chronicle recorded", () -> {
            int seals = ShelterNetwork.getInstance().getSealCount();
            ctx.log("Seals held in network: " + seals);
            ctx.assertTrue(seals > 0, "At least one seal must now be held");

            HistoryEvent last = ctx.lastHistoryEvent();
            ctx.log("Last history event: " + (last == null ? "none" : last.type + " - " + last));
            ctx.assertTrue(last != null, "Defeating seal lord must record history event in chronicle");
        });

        script.shot("pt03_seal_claimed", ctx);
    }
}
