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
    /**
     * The seeker's kit bonus: the top of LootTable's tier 2. With the best tier-3 kit (+6 claymore,
     * full plate at +6) a lord fell in 98 turns for one draught; with plain kit it took fifteen.
     */
    static final int KIT_BONUS = 3;
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
        script.once("arm the seeker as the middle levels do", () -> {
            // Middling loot, not the best: the top of LootTable's tier 2 (levels 6-15), which a seeker
            // reaching a court has had seasons to find. A broadsword and chain mail at +3, and a shield.
            Player p = ctx.getPlayer();
            com.bpm.minotaur.gamedata.item.ItemDataManager items = ctx.getGame().getItemDataManager();
            com.badlogic.gdx.assets.AssetManager assets = ctx.getGame().getAssetManager();
            Item blade = items.createItem(Item.ItemType.SWORD_BROAD, 0, 0, ItemColor.PURPLE, assets);
            blade.addModifier(new com.bpm.minotaur.gamedata.item.ItemModifier(
                    com.bpm.minotaur.gamedata.ModifierType.BONUS_DAMAGE, KIT_BONUS, "+2"));
            p.getInventory().setRightHand(blade);
            p.getInventory().setLeftHand(items.createItem(Item.ItemType.LARGE_SHIELD, 0, 0, ItemColor.GRAY, assets));
            Item mail = items.createItem(Item.ItemType.CHAIN_MAIL, 0, 0, ItemColor.GRAY, assets);
            mail.addModifier(new com.bpm.minotaur.gamedata.item.ItemModifier(
                    com.bpm.minotaur.gamedata.ModifierType.BONUS_AC, KIT_BONUS, "+2"));
            p.getEquipment().setWornChest(mail);
            ctx.press(Input.Keys.END);
            ctx.log("Seeker: level " + p.getLevel() + ", HP " + p.getStats().getCurrentHP() + "/" + p.getStats().getMaxHP()
                    + ", AC " + p.getArmorClass() + ", " + blade.getDisplayName());
        });

        int[] turns = {0};
        int[] drinks = {0};
        int[] startHp = {0};
        int[] still = {0};
        int[] regroups = {0};
        GridPoint2[] lastPos = {null};
        com.badlogic.gdx.math.GridPoint2[] court = {null};
        int[] firstTurn = {0};
        Monster[] faced = {null};
        boolean[] died = {false};
        script.once("face the lord", () -> {
            firstTurn[0] = ctx.getScreen().getTurnCount();
            court[0] = new com.badlogic.gdx.math.GridPoint2(ctx.getWorldManager().getCurrentPlayerChunkId());
            Monster lord = ctx.sealLord();
            ctx.assertTrue(lord != null, "A seal lord must hold court here");
            faced[0] = lord;
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
            // The lord the seeker faced, not whatever ctx finds: a maze swapped under the seeker has none.
            Monster lord = faced[0];
            if (!lord.isAlive()) return true;
            ctx.assertTrue(ctx.getMaze().getMonsters().containsValue(lord),
                    "the duel stays in the court (the lord is no longer in this maze)");
            Player p = ctx.getPlayer();
            if (p.getStats().getCurrentHP() <= 0) {
                died[0] = true;
                return true;
            }
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
            boolean engaged = ctx.canStrike(at, lordAt);
            still[0] = engaged || !at.equals(lastPos[0]) ? 0 : still[0] + 1;
            lastPos[0] = at;
            if (still[0] >= 40) {
                // The driver is lost in the hall, which measures the driver and not the lord: stand beside it again.
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    GridPoint2 beside = new GridPoint2(lordAt.x + d[0], lordAt.y + d[1]);
                    if (ctx.getMaze().isPassable(beside.x, beside.y) && ctx.canStrike(beside, lordAt)) {
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
            // A court may kill a seeker who came underprepared (the designer's call): a death is an
            // outcome, not a failure. What must hold is that the duel ends, and a fallen lord's seal
            // goes somewhere -- to the seeker who fought it, or to whoever else struck it down.
            boolean lordFell = !faced[0].isAlive();
            boolean seekerFell = died[0];
            boolean bearer = false;
            for (Monster m : ctx.getMaze().getMonsters().values()) {
                if (m != null && m.getSealRole() == Monster.SEAL_BEARER && m.isAlive()) bearer = true;
            }
            String outcome = lordFell ? (bearer ? "the lord fell to another hand, which took the seal" : "the lord fell")
                    : seekerFell ? "the court killed the seeker" : "the lord stands";
            ctx.log("Duel at regional level " + courtLevel[0] + ": " + outcome + " after " + turns[0] + " turns; "
                    + drinks[0] + " draughts drunk (" + (drinks[0] <= POTIONS ? "within" : "beyond") + " the "
                    + POTIONS + " a seeker carries); " + regroups[0] + " regroups; seals held "
                    + ShelterNetwork.getInstance().getSealCount());
            ctx.assertTrue(lordFell || seekerFell, "a duel ends: the lord falls or the seeker does, within " + TURN_LIMIT + " turns");
            ctx.assertTrue(!lordFell || bearer || ShelterNetwork.getInstance().getSealCount() > 0,
                    "a fallen lord's seal goes to the seeker, or to whoever struck it down");
        });
        script.shot("duel_02_after", ctx);
    }
}
