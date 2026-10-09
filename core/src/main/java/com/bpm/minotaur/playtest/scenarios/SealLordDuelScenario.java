package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.shelter.ShelterNetwork;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

/**
 * A seal lord fought for real (Houses of the Maze balance): a seeker of the level the world expects
 * at that court, in ordinary kit,
 * no help with the lord's health, and a refill only when the seeker would otherwise be drinking
 * a potion. It answers the question the play-test raised -- can a seal be won? -- with numbers:
 * how many turns, and how many potions' worth of help.
 */
public final class SealLordDuelScenario implements PlaytestScenario {

    /** A seeker at the middle levels carries about this many healing draughts into a court. */
    static final int POTIONS = 6;
    /** World turns, not driver ticks: a swing, a step or a turn on the spot each take one. */
    static final int TURN_LIMIT = 400;
    /** Below this share of health, a seeker drinks. */
    static final float DRINK_AT = 0.35f;

    @Override
    public String name() {
        return "seal-lord-duel";
    }

    @Override
    public String description() {
        return "Fight a seal lord for real with a seeker levelled for its region, and count the cost.";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.key("warp beside a seal lord", Input.Keys.NUMPAD_9, ctx);
        script.wait("court arrive", 20);
        int[] courtLevel = {1};
        script.once("level the seeker for this region", () -> {
            com.badlogic.gdx.math.GridPoint2 c = ctx.getWorldManager().getCurrentPlayerChunkId();
            // The level the world expects here: depth plus half the distance (WorldManager's EDL).
            int regional = ctx.getWorldManager().getCurrentLevel() + (Math.abs(c.x) + Math.abs(c.y)) / 2;
            courtLevel[0] = regional;
            while (ctx.getPlayer().getLevel() < regional) ctx.press(Input.Keys.PAGE_UP);
        });
        script.wait("level-up opens", 10);
        script.once("decide later", ctx::dismissModals);
        script.once("arm the seeker", () -> {
            Player p = ctx.getPlayer();
            p.getInventory().setRightHand(ctx.getGame().getItemDataManager().createItem(Item.ItemType.SWORD_BROAD,
                    0, 0, ItemColor.PURPLE, ctx.getGame().getAssetManager()));
            p.getInventory().setLeftHand(ctx.getGame().getItemDataManager().createItem(Item.ItemType.LARGE_SHIELD,
                    0, 0, ItemColor.GRAY, ctx.getGame().getAssetManager()));
            p.getEquipment().setWornChest(ctx.getGame().getItemDataManager().createItem(Item.ItemType.CHAIN_MAIL,
                    0, 0, ItemColor.GRAY, ctx.getGame().getAssetManager()));
            ctx.press(Input.Keys.END);
            ctx.log("Seeker: level " + p.getLevel() + ", HP " + p.getStats().getCurrentHP() + "/" + p.getStats().getMaxHP()
                    + ", AC " + p.getArmorClass());
        });

        int[] turns = {0};
        int[] drinks = {0};
        int[] startHp = {0};
        int[] still = {0};
        int[] regroups = {0};
        GridPoint2[] lastPos = {null};
        com.badlogic.gdx.math.GridPoint2[] court = {null};
        int[] firstTurn = {0};
        script.once("face the lord", () -> {
            firstTurn[0] = ctx.getScreen().getTurnCount();
            court[0] = new com.badlogic.gdx.math.GridPoint2(ctx.getWorldManager().getCurrentPlayerChunkId());
            Monster lord = ctx.sealLord();
            ctx.assertTrue(lord != null, "A seal lord must hold court here");
            startHp[0] = lord.getMaxHP();
            ctx.log("Lord: " + lord.getDisplayName() + " (" + lord.getType() + ", HP " + lord.getMaxHP() + ", bite "
                    + lord.getDamageDice() + ", AC " + lord.getArmorClass() + "), retinue " + ctx.retinue());
        });
        script.shot("duel_01_court", ctx);

        script.until("the duel", TURN_LIMIT * 40, () -> {
            if (ctx.getFrame() % 4 != 0) return false;
            ctx.assertTrue(court[0].equals(ctx.getWorldManager().getCurrentPlayerChunkId())
                    && ctx.getWorldManager().getCurrentLevel() == com.bpm.minotaur.managers.SealCourt.COURT_LEVEL,
                    "the duel stays in the court (strayed to " + ctx.getWorldManager().getCurrentPlayerChunkId() + ")");
            Monster lord = ctx.sealLord();
            if (lord == null || !lord.isAlive()) return true;
            Player p = ctx.getPlayer();
            if (p.getStats().getCurrentHP() <= 0) return true;
            if (p.getStats().getCurrentHP() < p.getStats().getMaxHP() * DRINK_AT) {
                drinks[0]++;
                ctx.press(Input.Keys.END); // a healing draught, counted
            }
            int was = turns[0];
            turns[0] = ctx.getScreen().getTurnCount() - firstTurn[0];
            if (turns[0] / 40 > was / 40) {
                ctx.log("  turn " + turns[0] + ": lord HP " + lord.getCurrentHP() + "/" + lord.getMaxHP()
                        + ", seeker HP " + p.getStats().getCurrentHP() + ", draughts " + drinks[0]);
            }
            GridPoint2 at = new GridPoint2((int) p.getPosition().x, (int) p.getPosition().y);
            GridPoint2 lordAt = new GridPoint2((int) lord.getPosition().x, (int) lord.getPosition().y);
            boolean engaged = Math.abs(at.x - lordAt.x) + Math.abs(at.y - lordAt.y) == 1;
            still[0] = engaged || !at.equals(lastPos[0]) ? 0 : still[0] + 1;
            lastPos[0] = at;
            if (still[0] >= 40) {
                // The driver is lost in the hall, which measures the driver and not the lord: stand beside it again.
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    if (ctx.getMaze().isPassable(lordAt.x + d[0], lordAt.y + d[1])) {
                        p.setPosition(lordAt.x + d[0] + 0.5f, lordAt.y + d[1] + 0.5f);
                        regroups[0]++;
                        ctx.log("  turn " + turns[0] + ": the seeker was lost at " + at + "; regrouped beside the lord");
                        break;
                    }
                }
                still[0] = 0;
            }
            ctx.steerToward(lordAt);
            return turns[0] >= TURN_LIMIT;
        });
        script.wait("settle", 15);
        script.once("count the cost", () -> {
            Monster lord = ctx.sealLord();
            boolean won = lord == null || !lord.isAlive();
            ctx.log("Duel at regional level " + courtLevel[0] + ": " + (won ? "the lord fell" : "the lord stands")
                    + " after " + turns[0] + " turns; " + drinks[0] + " draughts drunk; " + regroups[0]
                    + " regroups; seals held "
                    + ShelterNetwork.getInstance().getSealCount());
            ctx.assertTrue(!won || ShelterNetwork.getInstance().getSealCount() > 0, "a lord the seeker broke gives up its seal");
            ctx.assertTrue(won, "A seeker of the region's level breaks a seal lord within " + TURN_LIMIT + " turns");
            ctx.assertTrue(drinks[0] <= POTIONS, "and needs no more than " + POTIONS + " draughts to do it (needed " + drinks[0] + ")");
        });
        script.shot("duel_02_after", ctx);
    }
}
