package com.bpm.minotaur.generation;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.managers.WorldManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The early weapon guarantee (#196): every new Incarnation finds one base-pool weapon averaging at
 * least 3.5 on the floor of the chunk it wakes in, a short walk from the shelter door.
 *
 * <p>The Father's Sword (1d3) is meant to be outgrown fast (#158). The world is wiped and reseeded
 * on every death, so the chunk the Incarnation wakes in is always freshly generated, and placing the
 * weapon at generation time gives exactly one per Incarnation.
 */
public final class EarlyWeapon {

    /** The weapon floor (#158, #195): the guaranteed weapon averages at least this much. */
    public static final double MIN_AVERAGE_DAMAGE = 3.5;

    /** Placed this many steps or more from the shelter, so it lies outside rather than at the door. */
    static final int MIN_STEPS = 2;
    /** And within this many, so it is found before the first real fight. */
    static final int MAX_STEPS = 10;

    private static final Pattern DICE = Pattern.compile("\\s*(\\d+)\\s*d\\s*(\\d+)\\s*([+-]\\s*\\d+)?\\s*");

    private EarlyWeapon() {
    }

    /** Average roll of an {@code NdS+K} die, or 0 if it does not parse. */
    public static double averageDamage(String dice) {
        if (dice == null) {
            return 0;
        }
        Matcher m = DICE.matcher(dice);
        if (!m.matches()) {
            return 0;
        }
        int n = Integer.parseInt(m.group(1));
        int s = Integer.parseInt(m.group(2));
        int k = (m.group(3) == null) ? 0 : Integer.parseInt(m.group(3).replace(" ", "").replace("+", ""));
        return n * (s + 1) / 2.0 + k;
    }

    /**
     * Base-pool melee weapons that meet the floor: tier 0, spawnable, not ranged or ammunition,
     * and not the Father's Sword.
     */
    public static List<ItemType> candidates(ItemDataManager itemDataManager) {
        List<ItemType> result = new ArrayList<>();
        for (ItemType type : itemDataManager.getLoadedTypes()) {
            ItemTemplate t = itemDataManager.getTemplate(type);
            if (t == null || !t.isWeapon || t.isRanged || t.isAmmunition || t.isShield) continue;
            if (t.unlockGated || t.lootTier != 0 || t.probability <= 0) continue;
            if (type == com.bpm.minotaur.gamedata.item.FathersSword.TYPE) continue;
            if (averageDamage(t.damageDice) < MIN_AVERAGE_DAMAGE) continue;
            result.add(type);
        }
        return result;
    }

    /** One candidate, weighted by spawn probability; null only if the base pool has none. */
    public static ItemType pick(ItemDataManager itemDataManager, Random random) {
        List<ItemType> pool = candidates(itemDataManager);
        int total = 0;
        for (ItemType type : pool) {
            total += itemDataManager.getTemplate(type).probability;
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (ItemType type : pool) {
            roll -= itemDataManager.getTemplate(type).probability;
            if (roll < 0) {
                return type;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /**
     * Places the weapon in {@code maze}.
     *
     * @return the placed item, or null if no weapon or tile could be found
     */
    public static Item place(Maze maze, ItemDataManager itemDataManager, AssetManager assetManager,
            Random random) {
        ItemType type = pick(itemDataManager, random);
        if (type == null) {
            return null;
        }
        GridPoint2 tile = findTile(maze, random);
        if (tile == null) {
            return null;
        }
        Item weapon = itemDataManager.createItem(type, tile.x, tile.y, ItemColor.GRAY, assetManager);
        if (weapon != null) {
            maze.addItem(weapon);
        }
        return weapon;
    }

    /**
     * A free floor tile outside the shelter, {@link #MIN_STEPS}-{@link #MAX_STEPS} steps from it
     * by a walk that walls do not block, so the player can always reach it. Falls back to the
     * nearest free tile at any distance, then to a tile near the middle of the chunk.
     */
    public static GridPoint2 findTile(Maze maze, Random random) {
        Set<GridPoint2> starts = new HashSet<>();
        starts.addAll(maze.getHomeTiles());
        starts.addAll(maze.getSanctuaryTiles());
        if (maze.getShelterEntry() != null) {
            starts.add(new GridPoint2(maze.getShelterEntry()));
        }
        if (starts.isEmpty()) {
            return WorldManager.findBonesPlacementTile(maze);
        }

        Map<GridPoint2, Integer> steps = new HashMap<>();
        ArrayDeque<GridPoint2> queue = new ArrayDeque<>();
        for (GridPoint2 s : starts) {
            steps.put(s, 0);
            queue.add(s);
        }
        List<GridPoint2> inBand = new ArrayList<>();
        GridPoint2 nearest = null;
        while (!queue.isEmpty()) {
            GridPoint2 at = queue.poll();
            int d = steps.get(at);
            if (d >= MIN_STEPS && isFree(maze, at)) {
                if (d <= MAX_STEPS) {
                    inBand.add(at);
                } else if (nearest == null) {
                    nearest = at;
                }
            }
            for (Direction dir : Direction.values()) {
                int nx = at.x + (int) dir.getVector().x;
                int ny = at.y + (int) dir.getVector().y;
                GridPoint2 next = new GridPoint2(nx, ny);
                if (steps.containsKey(next) || !canStep(maze, at, dir, nx, ny)) continue;
                steps.put(next, d + 1);
                queue.add(next);
            }
        }
        if (!inBand.isEmpty()) {
            return inBand.get(random.nextInt(inBand.size()));
        }
        return (nearest != null) ? nearest : WorldManager.findBonesPlacementTile(maze);
    }

    private static boolean canStep(Maze maze, GridPoint2 from, Direction dir, int nx, int ny) {
        if (nx < 0 || ny < 0 || nx >= maze.getWidth() || ny >= maze.getHeight()) {
            return false;
        }
        // A wall bit on the edge blocks; a door bit does not, since doors open.
        if ((maze.getWallDataAt(from.x, from.y) & dir.getWallMask()) != 0
                && !maze.hasGateAt(nx, ny) && !maze.hasGateAt(from.x, from.y)) {
            return false;
        }
        if (isSolid(maze, nx, ny)) {
            return false;
        }
        return maze.isPassable(nx, ny)
                || maze.getMonsters().containsKey(new GridPoint2(nx, ny))
                || maze.getGameObjectAt(nx, ny) instanceof Door;
    }

    /** All four edges walled: rock, not floor. */
    private static boolean isSolid(Maze maze, int x, int y) {
        return (maze.getWallDataAt(x, y) & 0b01010101) == 0b01010101;
    }

    private static boolean isFree(Maze maze, GridPoint2 p) {
        return !maze.isHomeTile(p.x, p.y)
                && !maze.isSanctuaryTile(p.x, p.y)
                && !isSolid(maze, p.x, p.y)
                && maze.isPassable(p.x, p.y)
                && maze.getGameObjectAt(p.x, p.y) == null
                && !maze.getItems().containsKey(p)
                && !maze.getScenery().containsKey(p)
                && !maze.getLadders().containsKey(p)
                && !maze.getGates().containsKey(p)
                && maze.getEventAt(p.x, p.y) == null;
    }
}
