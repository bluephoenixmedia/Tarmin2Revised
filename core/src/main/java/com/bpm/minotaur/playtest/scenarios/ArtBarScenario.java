package com.bpm.minotaur.playtest.scenarios;

import com.bpm.minotaur.gamedata.arts.ArtBar;
import com.bpm.minotaur.gamedata.arts.CombatArt;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.WeaponFamily;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

/**
 * The Art bar on the HUD (#245): a club and a bow in hand fill all four slots, one Art kept from an
 * earlier life (hollow rank mark), then one cooling and one Held. Screenshots each state.
 */
public final class ArtBarScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "art-bar";
    }

    @Override
    public String description() {
        return "Fill the Art bar from both hands and show ready, kept, cooling and Held slots.";
    }

    private static Item make(PlaytestContext ctx, Item.ItemType type) {
        return ctx.getGame().getItemDataManager().createItem(type, 0, 0, ItemColor.GRAY, ctx.getGame().getAssetManager());
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.shot("art_bar_empty", ctx);
        script.once("arm and teach", () -> {
            Player p = ctx.getPlayer();
            p.getInventory().setRightHand(make(ctx, Item.ItemType.CLUB));
            p.getInventory().setLeftHand(make(ctx, Item.ItemType.BOW_ELVEN));
            for (CombatArt art : new CombatArt[]{CombatArt.BATTERING_RAM, CombatArt.TOLL_THE_BELL,
                    CombatArt.HELD_ARROW, CombatArt.RAIN_OF_SHAFTS}) {
                p.learnArt(art, null);
            }
            p.getStats().getMastery().setRank(WeaponFamily.BLUNT, Rank.MASTER);
            p.getStats().getMastery().setRank(WeaponFamily.BOW, Rank.EXPERT); // Rain of Shafts is kept, not retrained
            ArtBar.Slot[] slots = ArtBar.slots(p);
            StringBuilder line = new StringBuilder("Art bar:");
            for (ArtBar.Slot s : slots) line.append(' ').append(s.art).append('/').append(s.state).append(s.kept ? "/kept" : "");
            ctx.log(line.toString());
            ctx.assertTrue(slots[0].art == CombatArt.BATTERING_RAM && slots[3].art == CombatArt.RAIN_OF_SHAFTS,
                    "right hand fills slots 1-2, left hand slots 3-4");
            ctx.assertTrue(slots[3].kept && !slots[0].kept, "a Master Art above the body's rank shows as kept");
        });
        script.wait("bar fills", 10);
        script.shot("art_bar_ready", ctx);
        script.once("use slot 1 at nothing", () -> {
            ctx.getGameScreen().useArt(0);
            ctx.assertTrue(!ctx.getPlayer().getArtCooldowns().isCooling(CombatArt.BATTERING_RAM),
                    "Battering Ram at an empty tile spends nothing");
        });
        script.once("cool and hold", () -> {
            ctx.getPlayer().getArtCooldowns().start(CombatArt.TOLL_THE_BELL, 42);
            ctx.getPlayer().setHeldArt(CombatArt.HELD_ARROW);
        });
        script.wait("bar updates", 10);
        script.shot("art_bar_cooling_held", ctx);
    }
}
