package com.bpm.minotaur.generation.theme;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.managers.GameEventManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Runtime driver for themed chunk objectives.
 *
 * <p>Owns the four moments an objective can advance: a monster dies, the player
 * interacts with a marker, a turn elapses, and the objective resolves. Keeping
 * them here rather than scattered through TurnManager and Player means the seal
 * and the reward have exactly one place that can open them.
 */
public class ThemeObjectiveManager {

    /** Upper bound on brambles so regrowth cannot fill the chunk. */
    private static final int MAX_BRAMBLES = 40;

    private static final Random regrowthRng = new Random();

    private ThemeObjectiveManager() {
    }

    // ------------------------------------------------------------------
    // Kill hook
    // ------------------------------------------------------------------

    /**
     * Called when any monster in a themed chunk dies.
     */
    public static void onMonsterKilled(Maze maze, Monster victim, GameEventManager eventManager) {
        ThemeObjectiveState state = stateOf(maze);
        if (state == null || state.isResolved()) return;

        if (state.getKind() == ThemeObjectiveKind.SLAY_CHAMPION
                && victim != null && victim.isThemeChampion()) {
            state.complete();
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("The champion falls. The seal weakens!", 3.0f));
            }
            routRemainingLegion(maze);
        }

        if (state.getKind() == ThemeObjectiveKind.BREACH_SEALED_TOMB
                && victim != null && victim.isThemeChampion()) {
            state.advance();
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("The Crypt Warden turns to ash! You claim the Crypt Key.", 3.0f));
            }
        }

        if (state.getKind() == ThemeObjectiveKind.LAST_COMBATANT_STANDING) {
            if (!anyHostileAlive(maze)) {
                state.complete();
            }
        }

        resolveIfComplete(maze, eventManager);
    }

    /**
     * The Battalion loses cohesion when its Commander dies: survivors drop their
     * hunt and scatter. This is the difference between "kill the commander" and
     * "kill everything", and it is why the two themes read differently.
     *
     * <p>The AI has no dedicated FLEEING state, so a rout is expressed as
     * WANDERING with the target cleared. Survivors disperse and stop
     * beelining, but they will still fight if the player closes. A true flee
     * behaviour would need pathfinding-away support in MonsterAiManager.
     */
    private static void routRemainingLegion(Maze maze) {
        for (Monster m : maze.getMonsters().values()) {
            if (m == null || !m.isAlive()) continue;
            if (m.getFaction() == Faction.TARMIN_LEGION && !m.isThemeChampion()) {
                m.setState(Monster.MonsterState.WANDERING);
                m.setTargetMonster(null);
            }
        }
    }

    private static boolean anyHostileAlive(Maze maze) {
        for (Monster m : maze.getMonsters().values()) {
            if (m == null || !m.isAlive()) continue;
            if (m.getFaction() == Faction.NEUTRAL) continue;
            if (m.isInvulnerable()) continue;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Interaction hook
    // ------------------------------------------------------------------

    /**
     * Offers an interaction at (x, y) to the themed objective.
     *
     * @return true when the objective consumed the interaction, so the caller
     *         should stop looking for something else to interact with.
     */
    public static boolean tryInteract(Maze maze, Player player, int x, int y,
                                      GameEventManager eventManager) {
        ThemeObjectiveState state = stateOf(maze);
        if (state == null || state.isResolved()) return false;

        Scenery marker = maze.getScenery().get(new GridPoint2(x, y));
        if (marker == null || !marker.isObjectiveMarker()) return false;

        switch (state.getKind()) {
            case RECONSECRATE_GRAVES:
                return reconsecrate(maze, marker, state, eventManager);

            case OPEN_DROWNED_CACHE:
                marker.consumeObjective();
                state.complete();
                announce(eventManager, "You haul the drowned cache open. The waters recede from the gates.");
                resolveIfComplete(maze, eventManager);
                return true;

            case DESTROY_HEART_BLOOM:
                return burnHeartBloom(player, marker, state, eventManager, maze);

            case BREACH_SEALED_TOMB:
                if (state.getProgress() >= 1) {
                    marker.consumeObjective();
                    state.complete();
                    announce(eventManager, "You turn the Crypt Key. The ancient seal breaks open!");
                    resolveIfComplete(maze, eventManager);
                    return true;
                } else {
                    announce(eventManager, "The crypt door is sealed with heavy iron. The Crypt Warden carries the key.");
                    return true;
                }

            case ACTIVATE_SUNKEN_SHRINE:
                marker.consumeObjective();
                state.complete();
                announce(eventManager, "You activate the sunken shrine. The rising tide calms!");
                resolveIfComplete(maze, eventManager);
                return true;

            default:
                return false;
        }
    }

    private static boolean reconsecrate(Maze maze, Scenery grave, ThemeObjectiveState state,
                                        GameEventManager eventManager) {
        grave.consumeObjective();
        state.advance();

        int remaining = Math.max(0, state.getRequired() - state.getProgress());
        if (remaining > 0) {
            announce(eventManager, "You reconsecrate the grave. " + remaining + " remain. Something stirs.");
        } else {
            announce(eventManager, "The last grave is reconsecrated. The graveyard settles.");
        }

        // Desecration has a price: each grave wakes what was buried in it.
        raiseGraveGuardian(maze, grave);
        resolveIfComplete(maze, eventManager);
        return true;
    }

    private static void raiseGraveGuardian(Maze maze, Scenery grave) {
        int gx = (int) grave.getPosition().x;
        int gy = (int) grave.getPosition().y;

        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] off : offsets) {
            int sx = gx + off[0];
            int sy = gy + off[1];
            GridPoint2 pt = new GridPoint2(sx, sy);
            if (maze.isWall(sx, sy)) continue;
            if (maze.getMonsters().containsKey(pt)) continue;

            Monster risen = new Monster(Monster.MonsterType.SKELETON, 28, 12, sx, sy);
            risen.setFaction(Faction.UNDEAD);
            maze.addMonster(risen);
            return;
        }
    }

    private static boolean burnHeartBloom(Player player, Scenery bloom, ThemeObjectiveState state,
                                          GameEventManager eventManager, Maze maze) {
        if (!hasFireSource(player)) {
            announce(eventManager, "The heart-bloom recoils but will not burn. You need a flame.");
            return true;
        }
        bloom.consumeObjective();
        maze.getScenery().remove(new GridPoint2((int) bloom.getPosition().x, (int) bloom.getPosition().y));
        state.complete();
        announce(eventManager, "The heart-bloom shrivels and burns. The brambles wither back.");
        clearBrambles(maze);
        resolveIfComplete(maze, eventManager);
        return true;
    }

    private static boolean hasFireSource(Player player) {
        if (player == null || player.getInventory() == null) return false;
        List<Item> carried = new ArrayList<>();
        if (player.getInventory().getRightHand() != null) carried.add(player.getInventory().getRightHand());
        if (player.getInventory().getLeftHand() != null) carried.add(player.getInventory().getLeftHand());
        if (player.getInventory().getBackpack() != null) {
            java.util.Collections.addAll(carried, player.getInventory().getBackpack());
        }
        if (player.getInventory().getMainInventory() != null) carried.addAll(player.getInventory().getMainInventory());

        for (Item item : carried) {
            if (item == null) continue;
            if (item.getType() == Item.ItemType.LAMP
                    || item.getType() == Item.ItemType.SMALL_FIREBALL
                    || item.getType() == Item.ItemType.LARGE_FIREBALL) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Turn hook
    // ------------------------------------------------------------------

    /**
     * Ticks the per-turn parts of a themed objective: bramble regrowth and the
     * Rune of Surrender channel.
     */
    public static void onTurn(Maze maze, GameEventManager eventManager) {
        ThemeObjectiveState state = stateOf(maze);
        if (state == null) return;

        if (state.isChannellingSurrender()) {
            if (state.tickSurrenderChannel()) {
                announce(eventManager, "The Rune of Surrender flares. The gates grind open. The prize is forfeit.");
                unsealGates(maze);
            } else {
                announce(eventManager, "The Rune of Surrender glows... ("
                        + state.getSurrenderChannelRemaining() + ")");
            }
            return;
        }

        if (state.isResolved()) return;

        if (maze.getChunkTheme() == ChunkTheme.OVERGROWN_THICKET
                && state.getKind() == ThemeObjectiveKind.DESTROY_HEART_BLOOM) {
            regrowBrambles(maze);
        }
    }

    /**
     * Brambles creep back each turn, re-blocking the paths behind the player.
     * Choke points are skipped so regrowth can never sever the chunk.
     */
    private static void regrowBrambles(Maze maze) {
        int existing = 0;
        for (Scenery s : maze.getScenery().values()) {
            if (s != null && "bramble".equals(s.getPropId())) existing++;
        }
        if (existing >= MAX_BRAMBLES) return;
        if (regrowthRng.nextFloat() > 0.35f) return;

        List<GridPoint2> candidates = new ArrayList<>();
        for (int y = 1; y < maze.getHeight() - 1; y++) {
            for (int x = 1; x < maze.getWidth() - 1; x++) {
                if (maze.isWall(x, y)) continue;
                GridPoint2 pt = new GridPoint2(x, y);
                if (maze.getScenery().containsKey(pt)) continue;
                if (maze.getMonsters().containsKey(pt)) continue;
                if (maze.getItems().containsKey(pt)) continue;
                if (maze.getGates().containsKey(pt)) continue;
                if (maze.getLadders().containsKey(pt)) continue;
                if (openNeighbourCount(maze, x, y) <= 2) continue; // never block a corridor
                candidates.add(pt);
            }
        }
        if (candidates.isEmpty()) return;

        GridPoint2 pt = candidates.get(regrowthRng.nextInt(candidates.size()));
        Scenery bramble = Scenery.fromProp("bramble", pt.x, pt.y);
        if (bramble != null) {
            maze.addScenery(bramble);
        }
    }

    private static int openNeighbourCount(Maze maze, int x, int y) {
        int open = 0;
        if (!maze.isWall(x + 1, y)) open++;
        if (!maze.isWall(x - 1, y)) open++;
        if (!maze.isWall(x, y + 1)) open++;
        if (!maze.isWall(x, y - 1)) open++;
        return open;
    }

    private static void clearBrambles(Maze maze) {
        List<GridPoint2> doomed = new ArrayList<>();
        for (java.util.Map.Entry<GridPoint2, Scenery> e : maze.getScenery().entrySet()) {
            if (e.getValue() != null && "bramble".equals(e.getValue().getPropId())) {
                doomed.add(e.getKey());
            }
        }
        for (GridPoint2 pt : doomed) {
            maze.getScenery().remove(pt);
        }
    }

    // ------------------------------------------------------------------
    // Surrender
    // ------------------------------------------------------------------

    /**
     * Starts a Rune of Surrender channel on a sealed themed gate.
     *
     * @return true when a channel was started or is already running.
     */
    public static boolean tryBeginSurrender(Maze maze, GameEventManager eventManager) {
        ThemeObjectiveState state = stateOf(maze);
        if (state == null || state.isResolved()) return false;

        if (state.isChannellingSurrender()) {
            announce(eventManager, "You are already channelling the Rune of Surrender.");
            return true;
        }

        state.beginSurrenderChannel();
        announce(eventManager, "You press your palm to the Rune of Surrender. Hold for "
                + ThemeObjectiveState.SURRENDER_CHANNEL_TURNS + " turns to forfeit and escape.");
        return true;
    }

    /** Interrupts a surrender channel, e.g. when the player moves or is struck. */
    public static void interruptSurrender(Maze maze, GameEventManager eventManager) {
        ThemeObjectiveState state = stateOf(maze);
        if (state == null || !state.isChannellingSurrender()) return;
        state.cancelSurrenderChannel();
        announce(eventManager, "The Rune of Surrender dims. The channel is broken.");
    }

    // ------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------

    /**
     * Unseals the gates and banks the Crest once the objective completes.
     * Idempotent: the reward is granted exactly once.
     */
    public static void resolveIfComplete(Maze maze, GameEventManager eventManager) {
        ThemeObjectiveState state = stateOf(maze);
        if (state == null || !state.isCompleted() || state.isRewardGranted()) return;

        unsealGates(maze);
        state.markRewardGranted();

        ThemeDefinition def = ThemeDataManager.getInstance().get(maze.getChunkTheme());
        int crests = def != null ? def.getCrestAward() : 1;

        ShelterAltar.getInstance().addCrestsOfValor(crests);
        announce(eventManager, "The seal breaks! You claim " + crests
                + (crests == 1 ? " Crest of Valor." : " Crests of Valor."));

        if (Gdx.app != null) {
            Gdx.app.log("ThemeObjectiveManager", "Theme " + maze.getChunkTheme()
                    + " cleared; awarded " + crests + " crest(s). Total: "
                    + ShelterAltar.getInstance().getCrestsOfValor());
        }
    }

    private static void unsealGates(Maze maze) {
        for (Gate gate : maze.getGates().values()) {
            gate.setLocked(false);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static ThemeObjectiveState stateOf(Maze maze) {
        if (maze == null || maze.getChunkTheme() == null) return null;
        ThemeObjectiveState state = maze.getThemeObjective();
        // A non-viable objective never sealed the chunk, so it must never pay
        // out either: "no champion alive" would otherwise read as "the champion
        // is dead" for a champion that was never placed.
        if (state != null && !state.isViable()) return null;
        return state;
    }

    private static void announce(GameEventManager eventManager, String message) {
        if (eventManager != null) {
            eventManager.addEvent(new GameEvent(message, 3.5f));
        }
    }
}
