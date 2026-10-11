package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.favour.Favour;
import com.bpm.minotaur.gamedata.shelter.SealedGates;
import com.bpm.minotaur.managers.HistoryManager;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

/**
 * The Living War, phase 5 (W33-W37): a house besieges Castle Tarmin; the player, a friend of the
 * besieger with one seal, finds the breach beside the gate, forces it, and comes down into the
 * castle's halls, where the assault and the Legion are fighting before the sealed throne door.
 */
public final class LivingWarSiegeScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "living-war-siege";
    }

    @Override
    public String description() {
        return "Lays a siege of Castle Tarmin, forces the breach with one seal as the besieger's friend, and walks the halls.";
    }

    private static boolean shown(PlaytestContext ctx) {
        return ctx.getMaze() != null && ctx.getMaze() == ctx.getWorldManager().getCurrentMaze();
    }

    private static GridPoint2 propAt(PlaytestContext ctx, String propId) {
        for (java.util.Map.Entry<GridPoint2, Scenery> e : ctx.getMaze().getScenery().entrySet()) {
            if (propId.equals(e.getValue().getPropId())) return e.getKey();
        }
        return null;
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("outfit hero", () -> ctx.outfitHero(12));

        script.once("a house lays siege to the castle", () -> {
            HistoryManager h = ctx.getWorldManager().getHistory();
            HistoryWorld w = h.world();
            War war = null;
            for (War x : w.activeWars()) if (w.tarminHouse() != null && x.involves(w.tarminHouse().id)) war = x;
            if (war == null) {
                // No one is at war with the Antlered Host in this world: someone declares, for the test.
                for (com.bpm.minotaur.gamedata.history.House x : w.livingHouses()) {
                    if (!x.holdsCastle && w.activeWarBetween(x.id, w.tarminHouse().id) == null) {
                        com.bpm.minotaur.gamedata.history.PlayerDeed d = new com.bpm.minotaur.gamedata.history.PlayerDeed(
                                com.bpm.minotaur.gamedata.history.PlayerDeed.Kind.OPENING_WAR, x.id, w.liveSeasons());
                        d.other = w.tarminHouse().id;
                        HistorySimulator.applyDeed(w, d);
                        break;
                    }
                }
                for (War x : w.activeWars()) if (x.involves(w.tarminHouse().id)) war = x;
            }
            ctx.assertTrue(war != null, "a war on the castle");
            HistorySimulator.forceSiege(w, war, 6);
            int besieger = war.enemyOf(w.tarminHouse().id);
            h.favour().set(besieger, Favour.FRIEND_AT);
            com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance().awardSeal(0);
            ctx.log("Siege by " + w.house(besieger).name + "; seals held "
                    + com.bpm.minotaur.gamedata.blight.CastleGate.sealsHeld());
            GridPoint2 castle = ctx.getWorldManager().castleSite();
            ctx.assertTrue(castle != null, "the world has a castle");
            ctx.getGameScreen().travelToChunk(castle, new GridPoint2(4, 4));
        });
        script.until("arrive at the castle", 300, () -> shown(ctx));
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.wait("the siege is seen", 15);

        GridPoint2[] breach = {null};
        script.once("the breach is in the wall", () -> {
            breach[0] = propAt(ctx, SealedGates.BREACH_PROP);
            ctx.log("Breach at " + breach[0] + ", gate at " + propAt(ctx, "castle_gate"));
            ctx.assertTrue(breach[0] != null, "a breach beside the castle gate (W35)");
            for (Direction d : Direction.values()) {
                int x = breach[0].x - (int) d.getVector().x, y = breach[0].y - (int) d.getVector().y;
                if (!ctx.getMaze().isPassable(x, y) || ctx.getMaze().isWallBlocking(x, y, d)) continue;
                if (ctx.getMaze().getMonsters().containsKey(new GridPoint2(x, y))) continue;
                ctx.getPlayer().setPosition(x + 0.5f, y + 0.5f);
                ctx.getPlayer().setFacing(d);
                return;
            }
        });
        script.wait("facing the breach", 10);
        script.once("where the player stands", () -> {
            com.badlogic.gdx.math.Vector2 p = ctx.getPlayer().getPosition();
            Direction f = ctx.getPlayer().getFacing();
            int ax = (int) p.x + (int) f.getVector().x, ay = (int) p.y + (int) f.getVector().y;
            Scenery ahead = ctx.getMaze().getScenery().get(new GridPoint2(ax, ay));
            ctx.log("Player at " + p + " facing " + f + "; ahead (" + ax + "," + ay + ") "
                    + (ahead == null ? "nothing" : ahead.getPropId()) + ", passable " + ctx.getMaze().isPassable(ax, ay));
        });
        script.shot("ls01_breach", ctx);
        script.key("force the breach", Input.Keys.UP, ctx);
        script.until("into the halls", 300, () -> ctx.getWorldManager().getCurrentLevel() == 2 && shown(ctx));
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.wait("the halls", 15);
        script.once("the halls: the assault, the Legion, and the sealed throne door", () -> {
            ctx.assertEquals(2, ctx.getWorldManager().getCurrentLevel(), "beneath the castle");
            ctx.assertTrue(propAt(ctx, SealedGates.THRONE_DOOR_PROP) != null, "the inner door before the throne (W36)");
            HistoryManager hm = ctx.getWorldManager().getHistory();
            ctx.log("Siege now: " + hm.world().activeSiege() + "; chunk " + ctx.getWorldManager().getCurrentPlayerChunkId()
                    + " castle " + ctx.getWorldManager().castleSite() + "; spent " + hm.encounterLedger().spent);
            ctx.log("In the halls: theme " + ctx.getMaze().getChunkTheme() + ", soldiers of the war " + ctx.countWarBand()
                    + ", monsters " + ctx.getMaze().getMonsters().size());
            ctx.assertTrue(ctx.countWarBand() > 0, "both sides fight in the halls");
        });
        script.shot("ls02_halls", ctx);
    }
}
