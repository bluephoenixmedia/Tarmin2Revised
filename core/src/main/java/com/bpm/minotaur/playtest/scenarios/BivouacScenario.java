package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.shelter.Bivouac;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

/**
 * The Bivouac (Living War A60-A63): a quiet night out on the road, then an ambushed one by a fire,
 * waking with something on top of the sleeper and the blows already falling.
 */
public final class BivouacScenario implements PlaytestScenario {

    /** A chunk well out along no road in particular. */
    private static final GridPoint2 FIELD = new GridPoint2(7, 7);

    @Override
    public String name() {
        return "bivouac";
    }

    @Override
    public String description() {
        return "Pitches a bivouac, sleeps a quiet night, then is ambushed by a fire and wakes to free rounds.";
    }

    private static boolean shown(PlaytestContext ctx) {
        return ctx.getMaze() != null && ctx.getMaze() == ctx.getWorldManager().getCurrentMaze();
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        Item[] kit = {null};
        script.once("make the kit", () -> {
            ctx.outfitHero(5);
            kit[0] = ctx.getGame().getItemDataManager().createItem(Item.ItemType.BIVOUAC_KIT, 0, 0, ItemColor.TAN, ctx.getGame().getAssetManager());
            ctx.getPlayer().getInventory().pickup(kit[0]);
            ctx.assertEquals(Bivouac.KIT_NIGHTS, Bivouac.nightsLeft(kit[0]), "a new kit");
        });

        script.once("no bivouac on warded ground", () -> ctx.getPlayer().useItem(kit[0], ctx.getEventManager(), null, ctx.getMaze()));
        script.wait("refused", 10);
        script.once("the shelter refuses", () -> ctx.assertTrue(!(ctx.getGame().getScreen() instanceof com.bpm.minotaur.screens.ChoiceScreen),
                "no prompt on sanctuary ground"));

        script.once("the Wayfarer's Tome teaches a ward", () -> {
            Item tome = ctx.getGame().getItemDataManager().createItem(Item.ItemType.TOME_OF_THE_WAYFARER, 0, 0, ItemColor.PURPLE, ctx.getGame().getAssetManager());
            ctx.getPlayer().getInventory().pickup(tome);
            ctx.assertTrue(ctx.getPlayer().beginTomeStudy(tome, ctx.getMaze(), ctx.getEventManager()), "studied at the shelter");
            // Off the warded tiles a study is channelled, turn by turn.
            ctx.log("Tome study: channelled " + (ctx.getPlayer().getActiveTomeStudy() != null));
            for (int i = 0; i < 20 && ctx.getPlayer().getActiveTomeStudy() != null; i++) ctx.getPlayer().advanceTomeStudy(ctx.getMaze(), ctx.getEventManager());
            ctx.assertTrue(ctx.getPlayer().getPendingTomeChoice() != null, "the study ends in a Tome Choice");
            ctx.log("Tome Choice: " + ctx.getPlayer().getPendingTomeChoice().getOptions());
            ctx.assertTrue(ctx.getPlayer().chooseTomeSpell("SLEEPERS_WARD", ctx.getEventManager()), "the ward is on offer");
            ctx.assertTrue(!ctx.getPlayer().isSpellPrepared("SLEEPERS_WARD"), "a ward holds no slot");
        });

        script.once("the bench's field gear", () -> {
            ctx.getGame().setScreen(new com.bpm.minotaur.screens.CraftingScreen(ctx.getGame(), ctx.getGameScreen(), ctx.getPlayer(), ctx.getGameScreen().getCraftingManager()));
        });
        script.wait("the bench opens", 10);
        script.once("to field gear", () -> ((com.bpm.minotaur.screens.CraftingScreen) ctx.getGame().getScreen()).switchTab(4));
        script.wait("the tab draws", 10);
        script.shot("biv07_bench_wards", ctx);
        script.once("back to the world", () -> ctx.getGame().setScreen(ctx.getGameScreen()));
        script.wait("back", 10);

        script.once("out into the field", () -> ctx.getGameScreen().travelToChunk(FIELD, new GridPoint2(16, 16)));
        script.until("arrive", 300, () -> shown(ctx));
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.wait("look about", 5);
        clearGround(script, ctx);

        // A quiet night.
        long[] clock = {0};
        int[] hp = {0};
        script.once("unroll the kit", () -> {
            ctx.getPlayer().getStats().setCurrentHP(ctx.getPlayer().getStats().getMaxHP() / 4);
            hp[0] = ctx.getPlayer().getStats().getCurrentHP();
            clock[0] = ctx.getWorldManager().getHistory().warClock();
            ctx.getGameScreen().decideNextNight(new Bivouac.Night(-1, 0));
            ctx.getPlayer().useItem(kit[0], ctx.getEventManager(), null, ctx.getMaze());
        });
        script.until("the question", 120, () -> ctx.getGame().getScreen() instanceof com.bpm.minotaur.screens.ChoiceScreen);
        script.shot("biv01_pitch", ctx);
        script.once("sleep", () -> ctx.assertTrue(ctx.choose("Sleep"), "sleep is on offer"));
        script.wait("morning", 15);
        script.once("the world moved and the sleeper rested", () -> {
            long moved = ctx.getWorldManager().getHistory().warClock() - clock[0];
            ctx.log("Clock moved " + moved + ", HP " + hp[0] + " to " + ctx.getPlayer().getStats().getCurrentHP()
                    + " of " + ctx.getPlayer().getStats().getMaxHP() + ", kit " + Bivouac.nightsLeft(kit[0]));
            ctx.assertTrue(moved >= com.bpm.minotaur.managers.HistoryManager.SLEEP_TURNS, "a whole night passed");
            ctx.assertTrue(ctx.getPlayer().getStats().getCurrentHP() > hp[0], "rested");
            ctx.assertTrue(ctx.getPlayer().getStats().getCurrentHP() < ctx.getPlayer().getStats().getMaxHP(), "but only by half");
            ctx.assertEquals(Bivouac.KIT_NIGHTS - 1, Bivouac.nightsLeft(kit[0]), "a night's wear");
        });
        script.shot("biv02_morning", ctx);
        clearGround(script, ctx);

        // Warded nights: every ward on offer, then the Alarm's, then all three together.
        script.once("the wards to hand", () -> {
            for (com.bpm.minotaur.gamedata.shelter.Ward ward : com.bpm.minotaur.gamedata.shelter.Ward.values()) {
                ctx.getPlayer().learnPermanentSpellId(ward.spellId);
                for (int i = 0; i < 2; i++) {
                    ctx.getPlayer().getInventory().pickup(ctx.getGame().getItemDataManager().createItem(ward.itemType, 0, 0, ItemColor.TAN, ctx.getGame().getAssetManager()));
                }
            }
            ctx.getPlayer().getStats().setMaxMP(100);
            ctx.getPlayer().getStats().setCurrentMP(100);
            ctx.assertTrue(!ctx.getPlayer().isSpellPrepared("WATCHFUL_SLEEP"), "a ward holds no slot");
            hp[0] = ctx.getPlayer().getStats().getCurrentHP();
            ctx.getGameScreen().decideNextNight(new Bivouac.Night(60, 3));
            ctx.getPlayer().useItem(kit[0], ctx.getEventManager(), null, ctx.getMaze());
        });
        script.until("the wards on offer", 120, () -> ctx.getGame().getScreen() instanceof com.bpm.minotaur.screens.ChoiceScreen);
        script.shot("biv04_all_wards_on_offer", ctx);
        script.once("set the bells", () -> ctx.assertTrue(ctx.choose("Alarm: Tripwire Bells"), "the bells are on offer"));
        script.until("the question comes back", 120, () -> ctx.getGame().getScreen() instanceof com.bpm.minotaur.screens.ChoiceScreen);
        script.once("sleep under the bells", () -> ctx.assertTrue(ctx.choose("Sleep"), "sleep is still on offer"));
        script.wait("the bells", 15);
        script.once("heard coming, and nothing struck", () -> {
            int beside = 0;
            int near = 0;
            for (Monster m : ctx.getMaze().getMonsters().values()) {
                if (m == null || !m.isAlive()) continue;
                near++;
                if (m.getPosition().dst(ctx.getPlayer().getPosition()) <= 1.5f) beside++;
            }
            ctx.log("Alarm: " + near + " coming, " + beside + " beside, HP " + hp[0] + " to " + ctx.getPlayer().getStats().getCurrentHP());
            ctx.assertTrue(near >= 1, "something is coming");
            ctx.assertEquals(0, beside, "none beside the sleeper");
            ctx.assertTrue(ctx.getPlayer().getStats().getCurrentHP() >= hp[0], "no free rounds");
        });
        script.shot("biv05_alarm", ctx);
        clearGround(script, ctx);
        script.once("all three", () -> {
            ctx.getGameScreen().decideNextNight(new Bivouac.Night(30, 2));
            ctx.getPlayer().useItem(kit[0], ctx.getEventManager(), null, ctx.getMaze());
        });
        script.until("prompt", 120, () -> ctx.getGame().getScreen() instanceof com.bpm.minotaur.screens.ChoiceScreen);
        script.once("raise a shield", () -> ctx.assertTrue(ctx.choose("Shield: Staked Hide Screen"), "the screen"));
        script.until("prompt", 120, () -> ctx.getGame().getScreen() instanceof com.bpm.minotaur.screens.ChoiceScreen);
        script.once("cast the watch", () -> ctx.assertTrue(ctx.choose("Alarm: Watchful Sleep (5 MP)"), "the spell"));
        script.until("prompt", 120, () -> ctx.getGame().getScreen() instanceof com.bpm.minotaur.screens.ChoiceScreen);
        script.once("cast the veil", () -> ctx.assertTrue(ctx.choose("Concealment: Veil of the Sleeper (10 MP)"), "the veil"));
        script.until("prompt", 120, () -> ctx.getGame().getScreen() instanceof com.bpm.minotaur.screens.ChoiceScreen);
        script.shot("biv06_three_readied", ctx);
        script.once("no fire inside the veil, and sleep", () -> {
            ctx.assertTrue(!((com.bpm.minotaur.screens.ChoiceScreen) ctx.getGame().getScreen()).labels().contains("Sleep by a fire (burns a Tinder Bundle)"), "no fire on offer");
            clock[0] = ctx.getPlayer().getStats().getCurrentMP();
            ctx.assertTrue(ctx.choose("Sleep"), "sleep");
        });
        script.wait("that night", 15);
        script.once("all three were paid for", () -> {
            int mp = ctx.getPlayer().getStats().getCurrentMP();
            ctx.log("Three wards: MP " + clock[0] + " to " + mp);
            ctx.assertTrue(ctx.getPlayer().getKnownSpellIds().contains("WATCHFUL_SLEEP"), "still known");
            int screens = 0;
            for (Item i : ctx.getPlayer().getInventory().getAllItems()) {
                if (i != null && i.getType() == Item.ItemType.STAKED_HIDE_SCREEN) screens++;
            }
            ctx.assertEquals(1, screens, "one of two screens spent");
            for (Monster m : ctx.getMaze().getMonsters().values()) {
                if (m == null || !m.isAlive()) continue;
                ctx.log("Saw through the veil: " + m.getName() + " (intelligence " + m.getIntelligence() + ", caster " + m.isSpellcaster() + ")");
                ctx.assertTrue(!com.bpm.minotaur.gamedata.shelter.Ward.concealmentHidesFrom(m.getIntelligence(), m.isSpellcaster()), "only the clever or magical find a veiled sleeper");
            }
        });
        clearGround(script, ctx);

        // An ambushed one, by a fire.
        script.once("bank up a fire", () -> {
            ctx.press(Input.Keys.END);
            ctx.getPlayer().setPosition(ctx.getPlayer().getPosition().x, ctx.getPlayer().getPosition().y);
            hp[0] = ctx.getPlayer().getStats().getCurrentHP();
            clock[0] = ctx.getWorldManager().getHistory().warClock();
            ctx.getGameScreen().decideNextNight(new Bivouac.Night(60, 3));
            ctx.getPlayer().useItem(kit[0], ctx.getEventManager(), null, ctx.getMaze());
        });
        script.until("the question again", 120, () -> ctx.getGame().getScreen() instanceof com.bpm.minotaur.screens.ChoiceScreen);
        script.once("sleep by the fire", () -> ctx.assertTrue(ctx.choose("Sleep by a fire (burns a Tinder Bundle)"), "the hero starts with tinder"));
        script.wait("waking", 15);
        script.once("cut short, and they are on the sleeper", () -> {
            long moved = ctx.getWorldManager().getHistory().warClock() - clock[0];
            GridPoint2 at = new GridPoint2((int) ctx.getPlayer().getPosition().x, (int) ctx.getPlayer().getPosition().y);
            int beside = 0;
            for (Monster m : ctx.getMaze().getMonsters().values()) {
                if (m != null && m.isAlive() && Math.abs((int) m.getPosition().x - at.x) + Math.abs((int) m.getPosition().y - at.y) <= 1) beside++;
            }
            ctx.log("Clock moved " + moved + ", " + beside + " beside the sleeper, HP " + hp[0] + " to " + ctx.getPlayer().getStats().getCurrentHP());
            ctx.assertTrue(moved <= 61, "the sleep stopped at the turn they came, not " + moved);
            ctx.assertTrue(beside >= 1, "something stands beside the sleeper");
        });
        script.shot("biv03_ambushed", ctx);
        clearGround(script, ctx);
        script.once("back beside the fire", () -> ctx.getPlayer().useItem(kit[0], ctx.getEventManager(), null, ctx.getMaze()));
        script.until("the question over a fire", 120, () -> ctx.getGame().getScreen() instanceof com.bpm.minotaur.screens.ChoiceScreen);
        script.once("no veil over a fire", () -> {
            java.util.Set<String> labels = ((com.bpm.minotaur.screens.ChoiceScreen) ctx.getGame().getScreen()).labels();
            ctx.assertTrue(!labels.contains("Concealment: Ash-and-Bone Circle") && !labels.contains("Concealment: Veil of the Sleeper (10 MP)"), "Concealment refuses a lit fire");
            ctx.assertTrue(ctx.choose("Not here"), "decline");
        });
    }

    /** Whatever already walks here is put out of the way: this is about the night, not the chunk. */
    private static void clearGround(PlaytestScript script, PlaytestContext ctx) {
        script.once("clear the ground", () -> {
            for (Monster m : new java.util.ArrayList<>(ctx.getMaze().getMonsters().values())) ctx.getMaze().removeMonster(m);
            ctx.dismissModals();
        });
    }
}
