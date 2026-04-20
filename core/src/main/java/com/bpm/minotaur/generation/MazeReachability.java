package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.GridPoint2;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class that computes which floor tiles are reachable from a given
 * starting position via BFS (breadth-first flood-fill).
 *
 * The maze layout is stored with row 0 at the TOP of the map, while game
 * coordinates have Y 0 at the BOTTOM.  This class handles the conversion
 * internally so callers always work with game coordinates.
 *
 * A tile is considered traversable if it is a floor ('.') or a door ('D').
 * Only '#' (wall) and out-of-bounds cells are barriers.
 */
public class MazeReachability {

    private MazeReachability() {
        // Static utility — not instantiable
    }

    /**
     * Returns the set of all tiles reachable from {@code startGameCoord} in
     * game coordinates (origin bottom-left).
     *
     * @param layout        The text layout array (row 0 = top of map).
     * @param startGameCoord Player start in game coords (x = column, y = row from bottom).
     * @return Immutable-ish set of reachable game-coordinate points.
     */
    public static Set<GridPoint2> findReachable(String[] layout, GridPoint2 startGameCoord) {
        Set<GridPoint2> reachable = new HashSet<>();

        if (layout == null || layout.length == 0 || layout[0] == null || layout[0].isEmpty()) {
            return reachable;
        }

        int height = layout.length;
        int width  = layout[0].length();

        // Validate start in layout bounds before queuing
        int startLayoutRow = height - 1 - startGameCoord.y;
        if (startGameCoord.x < 0 || startGameCoord.x >= width
                || startLayoutRow < 0 || startLayoutRow >= height) {
            // Start tile is out of bounds – fallback: scan for the first floor tile
            GridPoint2 fallback = findFirstFloorTile(layout, height, width);
            if (fallback == null) return reachable;
            startGameCoord = fallback;
            startLayoutRow = height - 1 - startGameCoord.y;
        }

        // If the start tile is a wall, widen the search
        char startChar = layout[startLayoutRow].charAt(startGameCoord.x);
        if (isWall(startChar)) {
            GridPoint2 fallback = findFirstFloorTile(layout, height, width);
            if (fallback == null) return reachable;
            startGameCoord = fallback;
        }

        // BFS
        Deque<GridPoint2> queue = new ArrayDeque<>();
        queue.add(new GridPoint2(startGameCoord.x, startGameCoord.y));
        reachable.add(new GridPoint2(startGameCoord.x, startGameCoord.y));

        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};

        while (!queue.isEmpty()) {
            GridPoint2 cur = queue.poll();

            for (int dir = 0; dir < 4; dir++) {
                int nx = cur.x + dx[dir];
                int ny = cur.y + dy[dir];

                GridPoint2 next = new GridPoint2(nx, ny);
                if (reachable.contains(next)) continue;

                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;

                int layoutRow = height - 1 - ny;
                char c = layout[layoutRow].charAt(nx);
                if (isWall(c)) continue;

                reachable.add(next);
                queue.add(next);
            }
        }

        return reachable;
    }

    /**
     * True when a layout character is impassable (wall or unknown).
     * Floors ('.') and doors ('D') are passable.
     */
    private static boolean isWall(char c) {
        return c != '.' && c != 'D';
    }

    /** Scans the layout for the first non-wall tile as an emergency fallback. */
    private static GridPoint2 findFirstFloorTile(String[] layout, int height, int width) {
        for (int layoutRow = 0; layoutRow < height; layoutRow++) {
            String row = layout[layoutRow];
            for (int col = 0; col < width && col < row.length(); col++) {
                if (!isWall(row.charAt(col))) {
                    int gameY = height - 1 - layoutRow;
                    return new GridPoint2(col, gameY);
                }
            }
        }
        return null;
    }
}
