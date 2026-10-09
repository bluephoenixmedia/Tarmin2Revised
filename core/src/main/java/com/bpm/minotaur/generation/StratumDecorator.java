package com.bpm.minotaur.generation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.lighting.LightSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Dresses a stratum chunk (plan T4.1): props scattered through it once, when it is first made,
 * and its glow lit again every time it loads, since lights are not saved with a chunk. Only
 * passable props are scattered, so dressing can never wall a corridor shut.
 */
public final class StratumDecorator {

    static final int PROPS_MIN = 8;
    static final int PROPS_MAX = 16;
    static final int GLOWS = 4;
    static final String GLOW_ID = "stratum_glow_";

    private StratumDecorator() {
    }

    /** Scatters the stratum's props over a freshly generated chunk. */
    public static int decorate(Maze maze, Stratum stratum, long chunkSeed, AssetManager assets) {
        if (stratum == null || stratum.props.length == 0) return 0;
        Random rng = new Random(chunkSeed ^ 0x57A7A5L);
        List<GridPoint2> floor = freeFloor(maze);
        Collections.shuffle(floor, rng);
        int budget = PROPS_MIN + rng.nextInt(PROPS_MAX - PROPS_MIN + 1);
        int placed = 0;
        for (GridPoint2 pt : floor) {
            if (placed >= budget) break;
            Scenery prop = Scenery.fromProp(stratum.props[rng.nextInt(stratum.props.length)], pt.x, pt.y);
            if (prop == null || prop.isImpassable()) continue;
            bind(prop, assets);
            maze.addScenery(prop);
            placed++;
        }
        return placed;
    }

    /** Lights the stratum's glow; the same lights in the same places on every load. */
    public static void light(Maze maze, Stratum stratum, long chunkSeed) {
        if (stratum == null || stratum.glow == null) return;
        Random rng = new Random(chunkSeed ^ 0x6C0A1L);
        List<GridPoint2> floor = freeFloor(maze);
        if (floor.isEmpty()) return;
        for (int i = 0; i < GLOWS; i++) {
            GridPoint2 pt = floor.get(rng.nextInt(floor.size()));
            maze.removeLight(GLOW_ID + i);
            maze.addLight(new LightSource(GLOW_ID + i, pt.x + 0.5f, pt.y + 0.5f, stratum.glow, 4.0f, 0.9f,
                    LightSource.FlickerProfile.LANTERN_BREATH));
        }
    }

    private static List<GridPoint2> freeFloor(Maze maze) {
        List<GridPoint2> out = new ArrayList<>();
        for (int y = 1; y < maze.getHeight() - 1; y++) {
            for (int x = 1; x < maze.getWidth() - 1; x++) {
                if (!maze.isPassable(x, y)) continue;
                GridPoint2 pt = new GridPoint2(x, y);
                if (maze.getScenery().containsKey(pt) || maze.getItems().containsKey(pt)
                        || maze.getMonsters().containsKey(pt) || maze.getLadders().containsKey(pt)
                        || maze.getGates().containsKey(pt)) {
                    continue;
                }
                out.add(pt);
            }
        }
        return out;
    }

    private static void bind(Scenery s, AssetManager assets) {
        String path = s.getTexturePath();
        if (path == null || assets == null || Gdx.files == null || !Gdx.files.internal(path).exists()) return;
        if (!assets.isLoaded(path)) {
            assets.load(path, Texture.class);
            assets.finishLoadingAsset(path);
        }
        s.setTexture(assets.get(path, Texture.class));
    }
}
