package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Ladder;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** One chunk's explored layout, resolved for the map's chunk view. */
final class ChunkTiles {

    int width;
    int height;
    int[][] wallData;
    byte[][] explorationState;
    final Map<GridPoint2, Ladder.LadderType> ladders = new HashMap<>();
    final Map<GridPoint2, TextureRegion> itemIcons = new HashMap<>();
    final Map<GridPoint2, String> events = new HashMap<>();
    final Set<GridPoint2> homeTiles = new HashSet<>();

    boolean isSeen(int x, int y) {
        if (explorationState == null || x < 0 || x >= width || y < 0 || y >= height) return false;
        return explorationState[y][x] == Maze.VISIBILITY_SEEN;
    }

    int wallAt(int x, int y) {
        if (wallData == null || x < 0 || x >= width || y < 0 || y >= height) return 0;
        return wallData[y][x];
    }

    /** A solid block rather than a floor with walls on some sides; the same test as {@code Maze.isWall}. */
    boolean isSolid(int x, int y) {
        int w = wallAt(x, y);
        return w == 1 || (w & 0b1111) == 0b1111 || (w & 0b01010101) == 0b01010101;
    }

    /** The live chunk the player stands in: its exploration is newer than its save. */
    static ChunkTiles fromMaze(Maze maze) {
        ChunkTiles v = new ChunkTiles();
        v.width = maze.getWidth();
        v.height = maze.getHeight();
        v.wallData = maze.getWallData();
        v.explorationState = maze.getExplorationState();
        v.homeTiles.addAll(maze.getHomeTiles());
        for (Map.Entry<GridPoint2, Ladder> e : maze.getLadders().entrySet()) {
            v.ladders.put(e.getKey(), e.getValue().getType());
        }
        for (Map.Entry<GridPoint2, Item> e : maze.getItems().entrySet()) {
            TextureRegion region = e.getValue().getTextureRegion();
            if (region != null) v.itemIcons.put(e.getKey(), region);
        }
        v.events.putAll(maze.getEventTriggers());
        return v;
    }

    /** A saved chunk, or null if it has no layout. */
    static ChunkTiles fromChunkData(ChunkData data, ItemDataManager itemDataManager, AssetManager assetManager) {
        if (data == null || data.wallData == null) return null;
        ChunkTiles v = new ChunkTiles();
        v.wallData = data.wallData;
        v.height = v.wallData.length;
        v.width = v.height > 0 ? v.wallData[0].length : 0;
        v.explorationState = data.explorationState;
        if (data.homeTiles != null) v.homeTiles.addAll(data.homeTiles);
        if (data.ladders != null) {
            for (ChunkData.LadderData ld : data.ladders) v.ladders.put(new GridPoint2(ld.x, ld.y), ld.type);
        }
        if (data.events != null) {
            for (ChunkData.EventData ed : data.events) v.events.put(new GridPoint2(ed.x, ed.y), ed.eventId);
        }
        if (data.items != null && itemDataManager != null && assetManager != null) {
            for (ChunkData.ItemData id : data.items) {
                try {
                    Item item = new Item(id.type, id.x, id.y, id.color, itemDataManager, assetManager);
                    TextureRegion region = item.getTextureRegion();
                    if (region != null) v.itemIcons.put(new GridPoint2(id.x, id.y), region);
                } catch (Exception ignored) {
                    // An unresolvable icon: the tile still draws, just without its item.
                }
            }
        }
        return v;
    }
}
