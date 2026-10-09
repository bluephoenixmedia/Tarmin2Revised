package com.bpm.minotaur.playtest;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Pathfinder;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.town.Town;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.BattleDirector;
import com.bpm.minotaur.managers.WorldManager;
import com.bpm.minotaur.screens.GameScreen;
import com.bpm.minotaur.screens.TalkScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PlaytestContext {

    private final Tarmin2 game;
    private GameScreen screen;
    private final PlaytestConfig config;
    private final PlaytestReport.ScenarioResult scenarioResult;
    private final FileHandle outDir;
    private int frame;
    private int stuck;

    public PlaytestContext(Tarmin2 game, GameScreen screen, PlaytestConfig config,
                           PlaytestReport.ScenarioResult scenarioResult, FileHandle outDir) {
        this.game = game;
        this.screen = screen;
        this.config = config;
        this.scenarioResult = scenarioResult;
        this.outDir = outDir;
    }

    public Tarmin2 getGame() {
        return game;
    }

    public GameScreen getScreen() {
        return screen;
    }

    public void setScreen(GameScreen screen) {
        this.screen = screen;
    }

    public PlaytestConfig getConfig() {
        return config;
    }

    public PlaytestReport.ScenarioResult getScenarioResult() {
        return scenarioResult;
    }

    public FileHandle getOutDir() {
        return outDir;
    }

    public int getFrame() {
        return frame;
    }

    public void incrementFrame() {
        frame++;
    }

    public void log(String message) {
        scenarioResult.log(message);
        Gdx.app.log("Playtest", message);
    }

    public void dismissModals() {
        Player p = screen.getPlayer();
        if (p != null) {
            p.setPendingLevelUpModal(false);
        }
        if (screen.getHud() != null && screen.getHud().getLevelUpModal() != null
                && screen.getHud().getLevelUpModal().isVisible()) {
            screen.getHud().getLevelUpModal().close();
        }
    }

    public void press(int keycode) {
        dismissModals();
        com.badlogic.gdx.InputProcessor in = Gdx.input.getInputProcessor();
        if (in != null) {
            in.keyDown(keycode);
            in.keyUp(keycode);
        }
    }

    public boolean talk(String action) {
        if (game.getScreen() instanceof TalkScreen) {
            TalkScreen talk = (TalkScreen) game.getScreen();
            boolean ok = talk.perform(action);
            log("Talk -> " + action + (ok ? "" : " (no such action)") + ": " + talk.lastLine());
            return ok;
        } else {
            log("Talk -> " + action + ": not in a conversation (screen: "
                    + (game.getScreen() == null ? "null" : game.getScreen().getClass().getSimpleName()) + ")");
            return false;
        }
    }

    public boolean talkTo(Scenery s) {
        return com.bpm.minotaur.gamedata.shelter.SealedGates.talk(s);
    }

    public void capture(String name) {
        if ("errors-only".equalsIgnoreCase(config.getScreenshotMode()) && !name.startsWith("failure_")) {
            return;
        }
        Screen s = game.getScreen();
        if (s == null) return;
        try {
            ScreenUtils.clear(0, 0, 0, 1);
            s.render(0.016f);
            int w = Gdx.graphics.getBackBufferWidth();
            int h = Gdx.graphics.getBackBufferHeight();
            Pixmap raw = ScreenUtils.getFrameBufferPixmap(0, 0, w, h);
            Pixmap flipped = new Pixmap(w, h, raw.getFormat());
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) flipped.drawPixel(x, h - 1 - y, raw.getPixel(x, y));
            }
            PixmapIO.writePNG(outDir.child(name + ".png"), flipped);
            raw.dispose();
            flipped.dispose();
            log("[screenshot " + name + ".png]");
        } catch (Exception e) {
            log("[screenshot failed for " + name + ": " + e.getMessage() + "]");
        }
    }

    public void steerToward(GridPoint2 target) {
        Player p = screen.getPlayer();
        Maze maze = screen.getMaze();
        if (p == null || maze == null || target == null) return;

        GridPoint2 here = new GridPoint2((int) p.getPosition().x, (int) p.getPosition().y);
        GridPoint2 next = null;

        if (Math.abs(target.x - here.x) + Math.abs(target.y - here.y) == 1) {
            next = target;
        } else {
            List<GridPoint2> best = Pathfinder.findPath(maze, p, here, target, true);
            int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : around) {
                GridPoint2 beside = new GridPoint2(target.x + d[0], target.y + d[1]);
                if (!maze.isPassable(beside.x, beside.y)) continue;
                List<GridPoint2> path = beside.equals(here) ? null : Pathfinder.findPath(maze, p, here, beside, true);
                if (path != null && !path.isEmpty() && (best == null || best.isEmpty() || path.size() < best.size())) {
                    best = path;
                }
            }
            if (best != null) {
                for (GridPoint2 step : best) {
                    if (!step.equals(here)) {
                        next = step;
                        break;
                    }
                }
            }
        }

        if (next == null) {
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

    public void settleTraits() {
        Player p = screen.getPlayer();
        if (p != null) {
            if (p.needsTraitOffer()) {
                p.offerTraits();
            }
            if (!p.getPendingTraitOffer().isEmpty()) {
                String traitId = p.getPendingTraitOffer().get(0);
                p.chooseTrait(traitId);
                log("Settle trait: chosen " + traitId);
            }
            if (game.getScreen() instanceof com.bpm.minotaur.screens.TraitChoiceScreen) {
                game.setScreen(screen);
            }
        }
    }

    public void outfitHero(int levels) {
        Player p = screen.getPlayer();
        if (p == null) return;
        for (int i = 0; i < levels; i++) press(Input.Keys.PAGE_UP);
        dismissModals();
        p.getStats().setMaxHP(9999);
        p.getStats().setCurrentHP(9999);
        com.bpm.minotaur.gamedata.item.ItemDataManager items = game.getItemDataManager();
        p.getInventory().setRightHand(items.createItem(Item.ItemType.SWORD_BROAD, 0, 0,
                ItemColor.PURPLE, game.getAssetManager()));
        p.getInventory().setLeftHand(items.createItem(Item.ItemType.LARGE_SHIELD, 0, 0,
                ItemColor.GRAY, game.getAssetManager()));
        p.getEquipment().setWornChest(items.createItem(Item.ItemType.CHAIN_MAIL, 0, 0,
                ItemColor.GRAY, game.getAssetManager()));
        press(Input.Keys.END);
        dismissModals();
        log("Hero outfitted: Level " + p.getLevel() + ", HP " + p.getStats().getCurrentHP() + "/" + p.getStats().getMaxHP());
    }

    public void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("QA ASSERTION FAILED: " + message);
        }
    }

    public void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("QA ASSERTION FAILED: " + message + " (Expected: " + expected + ", Got: " + actual + ")");
        }
    }

    // World inspection helpers
    public Maze getMaze() {
        return screen.getMaze();
    }

    public Player getPlayer() {
        return screen.getPlayer();
    }

    public GameScreen getGameScreen() {
        return screen;
    }

    public com.bpm.minotaur.managers.CombatManager getCombatManager() {
        return screen != null ? screen.getCombatManager() : null;
    }

    public com.bpm.minotaur.managers.GameEventManager getEventManager() {
        return screen != null ? screen.getEventManager() : null;
    }

    public WorldManager getWorldManager() {
        return screen.getWorldManager();
    }

    public com.bpm.minotaur.managers.WarManager getWarManager() {
        return screen.getWarManager();
    }

    public String battlePhase() {
        BattleDirector d = screen.getWarManager().active();
        return d == null ? "no battle" : d.phase().name();
    }

    public int countWarBand() {
        int n = 0;
        for (Monster m : screen.getMaze().getMonsters().values()) {
            if (m != null && m.isWarBand()) n++;
        }
        return n;
    }

    public Monster sealLord() {
        for (Monster m : screen.getMaze().getMonsters().values()) {
            if (m != null && m.getSealRole() == Monster.SEAL_LORD && m.isAlive()) return m;
        }
        return null;
    }

    public List<String> retinue() {
        List<String> names = new ArrayList<>();
        for (Monster m : screen.getMaze().getMonsters().values()) {
            if (m != null && m.getSealRole() == Monster.SEAL_RETAINER) names.add(m.getDisplayName());
        }
        return names;
    }

    public List<Scenery> folkHere() {
        List<Scenery> out = new ArrayList<>();
        for (Scenery s : screen.getMaze().getScenery().values()) {
            if (s.isTownsfolk()) out.add(s);
        }
        return out;
    }

    public Scenery reeve() {
        Town t = screen.getWorldManager().townHere();
        if (t == null) return null;
        for (Scenery s : folkHere()) {
            if (t.folk.get(s.getFolkIndex()).role == Town.Role.QUESTGIVER) return s;
        }
        return null;
    }

    public int guards() {
        int n = 0;
        for (Monster m : screen.getMaze().getMonsters().values()) {
            if (m != null && m.getTownKey() != null) n++;
        }
        return n;
    }

    public HistoryEvent lastHistoryEvent() {
        List<HistoryEvent> events = screen.getWorldManager().getHistory().world().events();
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }
}
