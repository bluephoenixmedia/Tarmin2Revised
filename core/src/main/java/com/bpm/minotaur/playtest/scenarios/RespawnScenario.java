package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;
import com.bpm.minotaur.screens.GameScreen;
import com.bpm.minotaur.screens.PlayerDeathScreen;

/**
 * Dies for real and wakes in the shelter, twice (fixes 2026-10-10, items 1 and 3): the first spell
 * slot must still hold Mote of Light, as on a new game, and the quick slots must survive.
 */
public final class RespawnScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "respawn";
    }

    @Override
    public String description() {
        return "Die and awaken in the shelter twice; check spell slot 1 and the quick slots each time.";
    }

    private static String state(PlaytestContext ctx) {
        Player p = ctx.getPlayer();
        StringBuilder quick = new StringBuilder();
        for (Item i : p.getInventory().getQuickSlots()) quick.append(i == null ? "-" : i.getType().name()).append(' ');
        return "slot 1 " + p.getPreparedSpells()[0] + ", known " + p.getKnownSpellIds() + ", quick [" + quick.toString().trim() + "]";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        // Item 3: quick slot items vanishing at some facings after deaths. Fill the belt, carried through the deaths, then turn.
        script.once("fill the quick slots", () -> {
            Item.ItemType[] kinds = {Item.ItemType.POTION_PINK, Item.ItemType.POTION_BLUE, Item.ItemType.FLOUR_SACK};
            Item[] quick = ctx.getPlayer().getInventory().getQuickSlots();
            for (int i = 0; i < kinds.length && i < quick.length; i++) {
                quick[i] = ctx.getGame().getItemDataManager().createItem(kinds[i], 0, 0,
                        com.bpm.minotaur.gamedata.item.ItemColor.GRAY, ctx.getGame().getAssetManager());
            }
            ctx.log("Quick slots filled: " + state(ctx));
        });
        script.once("before", () -> ctx.log("Before any death: " + state(ctx)));
        for (int death = 1; death <= 2; death++) {
            final int n = death;
            script.once("die (" + n + ")", () -> ctx.getPlayer().getStats().setCurrentHP(0));
            script.until("the death screen (" + n + ")", 1200, () -> {
                if (ctx.getGame().getScreen() instanceof PlayerDeathScreen) return true;
                if (ctx.getFrame() % 20 == 0) ctx.press(Input.Keys.PERIOD);
                return false;
            });
            script.wait("mourn (" + n + ")", 30);
            script.until("awaken (" + n + ")", 600, () -> {
                if (ctx.getGame().getScreen() instanceof GameScreen) return true;
                if (ctx.getFrame() % 20 == 0) ctx.press(Input.Keys.ENTER);
                return false;
            });
            script.wait("wake (" + n + ")", 40);
            script.once("settle traits (" + n + ")", ctx::settleTraits);
            script.wait("traits settle (" + n + ")", 15);
            script.once("after death " + n, () -> {
                ctx.log("After death " + n + ": " + state(ctx));
                ctx.assertTrue("MOTE_OF_LIGHT".equals(ctx.getPlayer().getPreparedSpells()[0]),
                        "spell slot 1 holds Mote of Light after a respawn, as on a new game (death " + n + ")");
            });
            script.shot("respawn_" + n, ctx);
        }
        for (int turn = 0; turn < 4; turn++) {
            final int t = turn;
            script.wait("face (" + t + ")", 20);
            script.once("note facing " + t, () -> ctx.log("Facing " + ctx.getPlayer().getFacing() + ": " + state(ctx)));
            script.shot("quickslots_facing_" + t, ctx);
            script.key("turn (" + t + ")", Input.Keys.RIGHT, ctx);
        }
    }
}
