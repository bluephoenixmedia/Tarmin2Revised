package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Pathfinder;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Plays the game for a while, as a tester would, and writes down what it saw (Houses of the Maze
 * play-test). It is not a screen: the real {@link GameScreen} stays the game's screen, and this
 * driver presses real keys into it from a runnable posted every frame -- so a conversation or a
 * shop the game opens with {@code setScreen} opens exactly as it would for a player.
 *
 * <p>Boot with {@code ./gradlew lwjgl3:run --args="--playtest"}. Screenshots and a report go to
 * {@code core/build/playtest/}.
 */
public final class PlaytestDriver {

    private final Tarmin2 game;
    private GameScreen screen;
    private final List<Step> steps = new ArrayList<>();
    private final StringBuilder report = new StringBuilder();
    private int stepIndex;
    private int frame;
    private int stepFrames;
    private FileHandle out;
    private int stuck;

    /** One beat of the play: runs every frame until it says it is done, or its frames run out. */
    private static final class Step {
        final String name;
        final int maxFrames;
        final BooleanSupplier tick;

        Step(String name, int maxFrames, BooleanSupplier tick) {
            this.name = name;
            this.maxFrames = maxFrames;
            this.tick = tick;
        }
    }

    public PlaytestDriver(Tarmin2 game) {
        this.game = game;
    }

    /** Starts the game and the play. */
    public void start() {
        out = Gdx.files.local("core/build/playtest");
        out.mkdirs();
        screen = new GameScreen(game, 1, Difficulty.MEDIUM, GameMode.ADVANCED);
        com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().setDebugAllUnlocked(true);
        game.setScreen(screen);
        script();
        Gdx.app.postRunnable(this::tick);
    }

    // ------------------------------------------------------------------ the play

    private void script() {
        wait("settle", 20);
        add(new Step("choose a personality", 120, () -> {
            if (game.getScreen() == screen && screen.getPlayer() != null && !screen.getPlayer().needsTraitOffer()
                    && screen.getPlayer().getPendingTraitOffer().isEmpty()) {
                return true;
            }
            if (frame % 10 == 0) press(Input.Keys.NUM_1);
            return false;
        }));
        wait("settle", 20);
        once("outfit the tester", () -> {
            Player p = screen.getPlayer();
            for (int i = 0; i < 8; i++) press(Input.Keys.PAGE_UP);
            com.bpm.minotaur.gamedata.item.ItemDataManager items = game.getItemDataManager();
            p.getInventory().setRightHand(items.createItem(com.bpm.minotaur.gamedata.item.Item.ItemType.SWORD_BROAD, 0, 0,
                    com.bpm.minotaur.gamedata.item.ItemColor.PURPLE, game.getAssetManager()));
            p.getInventory().setLeftHand(items.createItem(com.bpm.minotaur.gamedata.item.Item.ItemType.LARGE_SHIELD, 0, 0,
                    com.bpm.minotaur.gamedata.item.ItemColor.GRAY, game.getAssetManager()));
            p.getEquipment().setWornChest(items.createItem(com.bpm.minotaur.gamedata.item.Item.ItemType.CHAIN_MAIL, 0, 0,
                    com.bpm.minotaur.gamedata.item.ItemColor.GRAY, game.getAssetManager()));
            press(Input.Keys.END);
            log("Tester: level " + p.getLevel() + ", HP " + p.getStats().getCurrentHP() + "/" + p.getStats().getMaxHP());
        });
        wait("the level-up screen opens", 10);
        key("decide the level-up later", Input.Keys.ESCAPE);
        once("history", () -> {
            com.bpm.minotaur.managers.HistoryManager h = screen.getWorldManager().getHistory();
            log("History: " + h.world().houses().size() + " houses, " + h.world().activeWars().size() + " wars active, "
                    + h.world().megabeasts().size() + " megabeasts, year " + (h.world().year() + 1));
            for (int g = 0; g < 3; g++) {
                log("  Gash " + g + " " + h.world().gashName(g) + ": " + com.bpm.minotaur.gamedata.boss.SealLord.compose(
                        h.world(), g, com.bpm.minotaur.gamedata.history.DoctrineCatalog.getInstance()).name);
            }
        });

        // 1. A battle on the surface, out on the castle road.
        key("battle: warp out to a road shelter", Input.Keys.SEMICOLON);
        wait("battle: arrive", 30);
        once("battle: where", () -> log("Standing at chunk " + screen.getWorldManager().getCurrentPlayerChunkId() + " ("
                + screen.getMaze().getBiome() + "), sanctuary tile: " + screen.getMaze().isSanctuaryTile(
                (int) screen.getPlayer().getPosition().x, (int) screen.getPlayer().getPosition().y)));
        key("battle: sound the horns", Input.Keys.NUMPAD_MULTIPLY);
        shot("pt01_horns");
        repeat("battle: wait out the warning", com.bpm.minotaur.managers.BattleDirector.WARNING_TURNS + 1, Input.Keys.PERIOD);
        once("battle: lines closed", () -> log("Battle " + phase() + ": " + countWarBand() + " soldiers on the field, "
                + guardsAtGates() + " near the gates"));
        shot("pt02_lines_closed");
        add(new Step("battle: stand in it until it breaks", 2400, () -> {
            if (frame % 6 != 0) return false;
            press(Input.Keys.END);
            press(Input.Keys.PERIOD);
            if (frame % 120 == 0) log("  turn: " + phase() + ", " + countWarBand() + " soldiers, player HP "
                    + screen.getPlayer().getStats().getCurrentHP());
            return screen.getWarManager().active() == null;
        }));
        once("battle: aftermath", () -> {
            log("Battle over (" + phase() + "). Items on the field: " + screen.getMaze().getItems().size()
                    + ". Signet rings: " + countItems(com.bpm.minotaur.gamedata.item.Item.ItemType.SIGNET_RING));
            com.bpm.minotaur.gamedata.history.HistoryEvent last = lastEvent();
            log("  Last chronicled: " + (last == null ? "-" : last.type + " " + last));
        });
        shot("pt03_after_battle");

        // 2. Below a seal site: the lord's court.
        key("court: warp beside a seal lord", Input.Keys.NUMPAD_9);
        wait("court: arrive", 10);
        once("court: who holds it", () -> {
            Monster lord = sealLord();
            log("Court at level " + screen.getWorldManager().getCurrentLevel() + ", stratum "
                    + screen.getMaze().getStratum().displayName + ": "
                    + (lord == null ? "NO LORD FOUND" : lord.getDisplayName() + " (" + lord.getType() + ", HP " + lord.getCurrentHP()
                    + "/" + lord.getMaxHP() + ", dice " + lord.getDamageDice() + ")") + ", retinue " + retinue());
        });
        shot("pt04_court");
        add(new Step("court: fight the lord", 9000, () -> {
            if (frame % 4 != 0) return false;
            press(Input.Keys.END);
            Monster lord = sealLord();
            if (lord == null) return true;
            if (frame % 200 == 0) {
                Player pl = screen.getPlayer();
                GridPoint2 at = new GridPoint2((int) pl.getPosition().x, (int) pl.getPosition().y);
                GridPoint2 to = new GridPoint2((int) lord.getPosition().x, (int) lord.getPosition().y);
                log("  lord HP " + lord.getCurrentHP() + "/" + lord.getMaxHP() + " at " + to + ", player HP "
                        + pl.getStats().getCurrentHP() + " at " + at + " facing " + pl.getFacing() + ", path "
                        + Pathfinder.findPath(screen.getMaze(), pl, at, to, true).size() + ", combat "
                        + screen.getCombatManager().getCurrentState() + ", screen " + game.getScreen().getClass().getSimpleName());
            }
            steerToward(new GridPoint2((int) lord.getPosition().x, (int) lord.getPosition().y));
            return false;
        }));
        once("court: the seal", () -> {
            log("Lord standing: " + (sealLord() != null) + ". Seals held: "
                    + com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance().getSealCount());
            com.bpm.minotaur.gamedata.history.HistoryEvent last = lastEvent();
            log("  Last chronicled: " + (last == null ? "-" : last.type + " " + last));
        });
        shot("pt05_court_after");

        // 3. A megabeast comes calling.
        key("beast: call one here", Input.Keys.NUMPAD_DIVIDE);
        repeat("beast: wait for it", 3, Input.Keys.PERIOD);
        once("beast: arrived", () -> {
            Monster beast = null;
            for (Monster m : screen.getMaze().getMonsters().values()) if (m != null && m.getMegabeastId() >= 0) beast = m;
            log("Megabeast here: " + (beast == null ? "NONE" : beast.getDisplayName() + " HP " + beast.getCurrentHP()
                    + ", weak to " + beast.getExtraWeakness()));
        });
        shot("pt06_beast");

        // 4. A town below.
        key("town: warp into the nearest", Input.Keys.NUMPAD_0);
        wait("town: arrive", 10);
        once("town: what is here", () -> {
            com.bpm.minotaur.gamedata.history.town.Town t = screen.getWorldManager().townHere();
            log("Town: " + (t == null ? "NONE" : t.name + " of " + t.allegiance.displayName + ", " + t.folk.size() + " folk")
                    + "; folk standing: " + folkHere().size() + ", guards: " + guards()
                    + ", merchant: " + (screen.getMaze().getShopkeeper() != null)
                    + ", stratum: " + screen.getMaze().getStratum().displayName);
        });
        shot("pt07_town");
        add(new Step("town: walk up to the reeve", 1200, () -> {
            if (game.getScreen() instanceof TalkScreen) return true;
            if (frame % 4 != 0) return false;
            Scenery reeve = reeve();
            if (reeve == null) return true;
            steerToward(new GridPoint2((int) reeve.getPosition().x, (int) reeve.getPosition().y));
            return false;
        }));
        once("town: talking", () -> log("Talking: " + (game.getScreen() instanceof TalkScreen)));
        shot("pt08_talk");
        once("town: ask for work", () -> talk("Quest"));
        wait("town: read", 5);
        shot("pt09_quest");
        once("town: rumours", () -> talk("Rumours"));
        wait("town: read", 5);
        shot("pt10_rumours");
        once("town: leave", () -> talk("Leave"));
        wait("done", 10);
        once("finish", () -> {
            out.child("report.txt").writeString(report.toString(), false);
            Gdx.app.log("Playtest", "Report written to " + out.child("report.txt").path());
            Gdx.app.exit();
        });
    }

    // ------------------------------------------------------------------ step kinds

    private void add(Step s) {
        steps.add(s);
    }

    private void wait(String name, int frames) {
        add(new Step(name, frames, () -> false));
    }

    private void once(String name, Runnable r) {
        add(new Step(name, 1, () -> {
            r.run();
            return true;
        }));
    }

    private void key(String name, int keycode) {
        once(name, () -> press(keycode));
        wait(name + " (settle)", 8);
    }

    private void repeat(String name, int times, int keycode) {
        int[] done = {0};
        add(new Step(name, times * 8 + 20, () -> {
            if (frame % 6 != 0) return false;
            press(Input.Keys.END);
            press(keycode);
            return ++done[0] >= times;
        }));
    }

    private void shot(String name) {
        once("screenshot " + name, () -> capture(name));
    }

    // ------------------------------------------------------------------ the loop

    private void tick() {
        try {
            if (stepIndex < steps.size()) {
                Step s = steps.get(stepIndex);
                boolean done = s.tick.getAsBoolean();
                stepFrames++;
                frame++;
                if (done || stepFrames >= s.maxFrames) {
                    if (!done && s.maxFrames > 1 && !s.name.contains("settle") && !s.name.startsWith("town: read")
                            && !s.name.equals("settle") && !s.name.endsWith("arrive") && !s.name.equals("done")) {
                        log("(step '" + s.name + "' ran out of frames)");
                    }
                    stepIndex++;
                    stepFrames = 0;
                }
            }
        } catch (Exception e) {
            log("EXCEPTION in step " + stepIndex + ": " + e);
            Gdx.app.error("Playtest", "step failed", e);
            stepIndex++;
        }
        if (stepIndex <= steps.size()) Gdx.app.postRunnable(this::tick);
    }

    // ------------------------------------------------------------------ hands and eyes

    /** A key, delivered the way a real key is: to whatever is listening for input now. */
    private void press(int keycode) {
        com.badlogic.gdx.InputProcessor in = Gdx.input.getInputProcessor();
        if (in != null) {
            in.keyDown(keycode);
            in.keyUp(keycode);
        }
    }

    private void talk(String action) {
        if (game.getScreen() instanceof TalkScreen) {
            TalkScreen talk = (TalkScreen) game.getScreen();
            boolean ok = talk.perform(action);
            log("Talk -> " + action + (ok ? "" : " (no such action)") + ": " + talk.lastLine());
        } else {
            log("Talk -> " + action + ": not in a conversation");
        }
    }

    /** One key toward {@code target}: turn to face the next tile on the path, or step (or strike) into it. */
    private void steerToward(GridPoint2 target) {
        Player p = screen.getPlayer();
        Maze maze = screen.getMaze();
        GridPoint2 here = new GridPoint2((int) p.getPosition().x, (int) p.getPosition().y);
        GridPoint2 next = null;
        if (Math.abs(target.x - here.x) + Math.abs(target.y - here.y) == 1) {
            next = target;
        } else {
            // Path to the target, or, if it stands where no one can step (townsfolk), to beside it.
            List<GridPoint2> best = Pathfinder.findPath(maze, p, here, target, true);
            int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : around) {
                GridPoint2 beside = new GridPoint2(target.x + d[0], target.y + d[1]);
                if (!maze.isPassable(beside.x, beside.y)) continue;
                List<GridPoint2> path = beside.equals(here) ? null : Pathfinder.findPath(maze, p, here, beside, true);
                if (path != null && !path.isEmpty() && (best == null || best.isEmpty() || path.size() < best.size())) best = path;
            }
            for (GridPoint2 step : best) {
                if (!step.equals(here)) {
                    next = step;
                    break;
                }
            }
        }
        if (next == null) {
            // No path: open the door in front if there is one, else turn and look for a way.
            com.badlogic.gdx.math.Vector2 f = p.getFacing().getVector();
            Object front = maze.getGameObjectAt((int) (here.x + f.x), (int) (here.y + f.y));
            if (front instanceof com.bpm.minotaur.gamedata.Door
                    && ((com.bpm.minotaur.gamedata.Door) front).getState() != com.bpm.minotaur.gamedata.Door.DoorState.OPEN) {
                press(Input.Keys.O);
            } else {
                press(stuck++ % 4 == 3 ? Input.Keys.UP : Input.Keys.RIGHT);
            }
            return;
        }
        Direction want = next.x > here.x ? Direction.EAST : next.x < here.x ? Direction.WEST
                : next.y > here.y ? Direction.NORTH : Direction.SOUTH;
        Direction facing = p.getFacing();
        Object ahead = maze.getGameObjectAt(next.x, next.y);
        boolean closedDoor = ahead instanceof com.bpm.minotaur.gamedata.Door
                && ((com.bpm.minotaur.gamedata.Door) ahead).getState() != com.bpm.minotaur.gamedata.Door.DoorState.OPEN;
        if (facing == want && closedDoor) press(Input.Keys.O);
        else if (facing == want) press(Input.Keys.UP);
        else if (facing.getRight() == want) press(Input.Keys.RIGHT);
        else press(Input.Keys.LEFT);
    }

    private void capture(String name) {
        Screen s = game.getScreen();
        ScreenUtils.clear(0, 0, 0, 1);
        s.render(0.016f);
        int w = Gdx.graphics.getBackBufferWidth();
        int h = Gdx.graphics.getBackBufferHeight();
        Pixmap raw = ScreenUtils.getFrameBufferPixmap(0, 0, w, h);
        Pixmap flipped = new Pixmap(w, h, raw.getFormat());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) flipped.drawPixel(x, h - 1 - y, raw.getPixel(x, y));
        }
        PixmapIO.writePNG(out.child(name + ".png"), flipped);
        raw.dispose();
        flipped.dispose();
        log("[screenshot " + name + ".png]");
    }

    private void log(String line) {
        report.append(line).append('\n');
        Gdx.app.log("Playtest", line);
    }

    // ------------------------------------------------------------------ reading the world

    private String phase() {
        com.bpm.minotaur.managers.BattleDirector d = screen.getWarManager().active();
        return d == null ? "no battle" : d.phase().name();
    }

    private int countWarBand() {
        int n = 0;
        for (Monster m : screen.getMaze().getMonsters().values()) if (m != null && m.isWarBand()) n++;
        return n;
    }

    private int guardsAtGates() {
        int n = 0;
        for (GridPoint2 g : screen.getMaze().getGates().keySet()) {
            for (Monster m : screen.getMaze().getMonsters().values()) {
                if (m != null && m.isWarBand() && Math.abs(m.getPosition().x - g.x) + Math.abs(m.getPosition().y - g.y) <= 3) n++;
            }
        }
        return n;
    }

    private int countItems(com.bpm.minotaur.gamedata.item.Item.ItemType type) {
        int n = 0;
        for (com.bpm.minotaur.gamedata.item.Item i : screen.getMaze().getItems().values()) if (i.getType() == type) n++;
        return n;
    }

    private Monster sealLord() {
        for (Monster m : screen.getMaze().getMonsters().values()) {
            if (m != null && m.getSealRole() == Monster.SEAL_LORD && m.isAlive()) return m;
        }
        return null;
    }

    private String retinue() {
        List<String> names = new ArrayList<>();
        for (Monster m : screen.getMaze().getMonsters().values()) {
            if (m != null && m.getSealRole() == Monster.SEAL_RETAINER) names.add(m.getDisplayName());
        }
        return names.toString();
    }

    private List<Scenery> folkHere() {
        List<Scenery> out = new ArrayList<>();
        for (Scenery s : screen.getMaze().getScenery().values()) if (s.isTownsfolk()) out.add(s);
        return out;
    }

    private Scenery reeve() {
        com.bpm.minotaur.gamedata.history.town.Town t = screen.getWorldManager().townHere();
        if (t == null) return null;
        for (Scenery s : folkHere()) {
            if (t.folk.get(s.getFolkIndex()).role == com.bpm.minotaur.gamedata.history.town.Town.Role.QUESTGIVER) return s;
        }
        return null;
    }

    private int guards() {
        int n = 0;
        for (Monster m : screen.getMaze().getMonsters().values()) if (m != null && m.getTownKey() != null) n++;
        return n;
    }

    private com.bpm.minotaur.gamedata.history.HistoryEvent lastEvent() {
        List<com.bpm.minotaur.gamedata.history.HistoryEvent> e = screen.getWorldManager().getHistory().world().events();
        return e.isEmpty() ? null : e.get(e.size() - 1);
    }
}
