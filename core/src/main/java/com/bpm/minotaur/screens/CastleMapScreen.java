package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Ladder;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.SettingsManager;
import com.bpm.minotaur.managers.WorldManager;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Full-screen expedition map. Every Z-level (surface and strata alike) is a two-tier
 * view: an overview grid of the chunks the player has actually visited on that level
 * (cursor-navigable, camera follows the cursor), which can be zoomed into for a
 * per-tile view of that chunk's unlocked layout (ladders, items, points of interest).
 */
public class CastleMapScreen extends BaseScreen {

    private enum Mode { OVERVIEW, ZOOM }

    /** Overview rendering style for one biome: fill color, single-letter code, and legend name. */
    private static class BiomeStyle {
        final Color color;
        final String letter;
        final String legendName;

        BiomeStyle(Color color, String letter, String legendName) {
            this.color = color;
            this.letter = letter;
            this.legendName = legendName;
        }
    }

    private static final Map<Biome, BiomeStyle> BIOME_STYLES = new EnumMap<>(Biome.class);
    static {
        BIOME_STYLES.put(Biome.MAZE, new BiomeStyle(new Color(0.18f, 0.17f, 0.22f, 0.9f), "M", "Maze / Castle Approach"));
        BIOME_STYLES.put(Biome.FOREST, new BiomeStyle(new Color(0.12f, 0.22f, 0.12f, 0.9f), "F", "Forest"));
        BIOME_STYLES.put(Biome.PLAINS, new BiomeStyle(new Color(0.18f, 0.22f, 0.14f, 0.9f), "P", "Plains"));
        BIOME_STYLES.put(Biome.DESERT, new BiomeStyle(new Color(0.30f, 0.26f, 0.14f, 0.9f), "D", "Desert"));
        BIOME_STYLES.put(Biome.LAKELANDS, new BiomeStyle(new Color(0.10f, 0.20f, 0.28f, 0.9f), "L", "Lakelands"));
        BIOME_STYLES.put(Biome.MOUNTAINS, new BiomeStyle(new Color(0.15f, 0.15f, 0.17f, 0.9f), "A", "Mountains (impassable)"));
        BIOME_STYLES.put(Biome.OCEAN, new BiomeStyle(new Color(0.08f, 0.12f, 0.24f, 0.9f), "O", "Ocean (impassable)"));
    }
    private static final BiomeStyle UNKNOWN_BIOME_STYLE =
            new BiomeStyle(new Color(0.18f, 0.17f, 0.22f, 0.9f), "?", "Unknown");

    private static BiomeStyle styleFor(Biome biome) {
        BiomeStyle style = BIOME_STYLES.get(biome);
        return style != null ? style : UNKNOWN_BIOME_STYLE;
    }

    /** The one hardcoded landmark left over from the original map: the sealed Tarmin gate. */
    private static final int CASTLE_CHUNK_X = 0;
    private static final int CASTLE_CHUNK_Y = 5;

    private static final int MIN_FLOOR = 1;
    /** Overview shows a (2*radius+1)^2 window of chunks centered on the cursor. */
    private static final int WINDOW_RADIUS = 3;

    private final Player player;
    private final Maze maze;
    private final GameScreen gameScreen;
    private final WorldManager worldManager;
    private final AssetManager assetManager;
    private final ItemDataManager itemDataManager;

    private final BitmapFont font;
    private final ShapeRenderer shapeRenderer;
    private final Texture ladderUpTexture;
    private final Texture ladderDownTexture;

    private int viewFloor = 1;
    private int maxFloor = 1;
    private Mode mode = Mode.OVERVIEW;
    private float animationTimer = 0f;

    private final Map<Integer, GridPoint2> cursorByFloor = new HashMap<>();
    private final Map<String, ChunkData> chunkDataCache = new HashMap<>();
    private Set<GridPoint2> visitedCacheSet = Collections.emptySet();
    private int visitedCacheFloor = Integer.MIN_VALUE;

    private GridPoint2 zoomedChunkId;
    private ZoomView zoomView;

    private OverviewLayout lastOverviewLayout;

    public CastleMapScreen(Tarmin2 game, Player player, Maze maze, GameScreen gameScreen) {
        super(game);
        this.player = player;
        this.maze = maze;
        this.gameScreen = gameScreen;
        this.worldManager = (gameScreen != null) ? gameScreen.getWorldManager() : null;
        this.assetManager = game.getAssetManager();
        this.itemDataManager = game.getItemDataManager();
        this.font = new BitmapFont();
        this.shapeRenderer = new ShapeRenderer();
        this.ladderUpTexture = new Texture(Gdx.files.internal("images/items/ladder_up.png"));
        this.ladderDownTexture = new Texture(Gdx.files.internal("images/items/ladder.png"));

        if (worldManager != null) {
            this.viewFloor = worldManager.getCurrentLevel();
            this.maxFloor = Math.max(worldManager.getCurrentLevel(), worldManager.getMaxVisitedLevel());
        } else if (maze != null) {
            this.viewFloor = maze.getLevel();
            this.maxFloor = this.viewFloor;
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                boolean isMapKey = keycode == SettingsManager.getInstance().getKey("MAP");
                if (isMapKey) {
                    game.setScreen(gameScreen);
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    if (mode == Mode.ZOOM) {
                        exitZoom();
                    } else {
                        game.setScreen(gameScreen);
                    }
                    return true;
                }

                if (mode == Mode.ZOOM) {
                    return false;
                }

                // --- Overview-only controls below ---
                switch (keycode) {
                    case Input.Keys.LEFT:
                        moveCursor(-1, 0);
                        return true;
                    case Input.Keys.RIGHT:
                        moveCursor(1, 0);
                        return true;
                    case Input.Keys.UP:
                        moveCursor(0, 1);
                        return true;
                    case Input.Keys.DOWN:
                        moveCursor(0, -1);
                        return true;
                    case Input.Keys.ENTER:
                    case Input.Keys.SPACE:
                        tryEnterZoom();
                        return true;
                }

                if (keycode == Input.Keys.PAGE_UP || keycode == Input.Keys.RIGHT_BRACKET) {
                    viewFloor = Math.min(maxFloor, viewFloor + 1);
                    return true;
                }
                if (keycode == Input.Keys.PAGE_DOWN || keycode == Input.Keys.LEFT_BRACKET) {
                    viewFloor = Math.max(MIN_FLOOR, viewFloor - 1);
                    return true;
                }
                if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
                    int floor = keycode - Input.Keys.NUM_1 + 1;
                    if (floor <= maxFloor) {
                        viewFloor = floor;
                    }
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                float worldW = game.getViewport().getWorldWidth();
                float worldH = game.getViewport().getWorldHeight();
                float glY = Gdx.graphics.getHeight() - screenY;
                float normX = (screenX / (float) Gdx.graphics.getWidth()) * worldW;
                float normY = (glY / (float) Gdx.graphics.getHeight()) * worldH;

                // Floor tabs, like PageUp/PageDown, only apply in the overview — while zoomed,
                // Esc/M are the only advertised controls, so a tab click here does nothing.
                if (mode == Mode.OVERVIEW && normY >= worldH - 95 && normY <= worldH - 65) {
                    float tabStartX = 40f;
                    float tabW = tabWidth(worldW);
                    for (int f = 1; f <= maxFloor; f++) {
                        float tx = tabStartX + (f - 1) * (tabW + 10);
                        if (normX >= tx && normX <= tx + tabW) {
                            viewFloor = f;
                            return true;
                        }
                    }
                    return false;
                }

                if (mode == Mode.OVERVIEW && lastOverviewLayout != null) {
                    GridPoint2 cell = lastOverviewLayout.chunkAt(normX, normY);
                    if (cell != null) {
                        GridPoint2 cursor = getCursor(viewFloor);
                        cursor.set(cell);
                        tryEnterZoom();
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void moveCursor(int dx, int dy) {
        getCursor(viewFloor).add(dx, dy);
    }

    private GridPoint2 getCursor(int floor) {
        GridPoint2 cursor = cursorByFloor.get(floor);
        if (cursor == null) {
            GridPoint2 playerChunk = (worldManager != null) ? worldManager.getCurrentPlayerChunkId() : null;
            cursor = (worldManager != null && floor == worldManager.getCurrentLevel() && playerChunk != null)
                    ? new GridPoint2(playerChunk)
                    : new GridPoint2(0, 0);
            cursorByFloor.put(floor, cursor);
        }
        return cursor;
    }

    private void tryEnterZoom() {
        GridPoint2 cursor = getCursor(viewFloor);
        if (!getVisitedForCurrentFloor().contains(cursor)) {
            return; // Blank/unvisited chunks can't be zoomed into.
        }
        this.zoomedChunkId = new GridPoint2(cursor);
        this.zoomView = buildZoomView(viewFloor, zoomedChunkId);
        if (this.zoomView != null) {
            this.mode = Mode.ZOOM;
        }
    }

    private void exitZoom() {
        this.mode = Mode.OVERVIEW;
        this.zoomedChunkId = null;
        this.zoomView = null;
    }

    private Set<GridPoint2> getVisitedForCurrentFloor() {
        if (viewFloor != visitedCacheFloor) {
            visitedCacheSet = (worldManager != null) ? worldManager.getVisitedChunkIds(viewFloor) : Collections.emptySet();
            visitedCacheFloor = viewFloor;
        }
        return visitedCacheSet;
    }

    private ChunkData getCachedChunkData(int floor, GridPoint2 chunkId) {
        String key = floor + "_" + chunkId.x + "_" + chunkId.y;
        ChunkData data = chunkDataCache.get(key);
        if (data == null && worldManager != null) {
            data = worldManager.loadChunkDataReadOnly(floor, chunkId);
            if (data != null) {
                chunkDataCache.put(key, data);
            }
        }
        return data;
    }

    private float tabWidth(float worldW) {
        float available = worldW - 80f;
        float gutter = 10f * (maxFloor - 1);
        return Math.min(100f, (available - gutter) / maxFloor);
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    @Override
    public void render(float delta) {
        animationTimer += delta;
        ScreenUtils.clear(0.04f, 0.05f, 0.07f, 1f);

        float worldW = game.getViewport().getWorldWidth();
        float worldH = game.getViewport().getWorldHeight();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);

        drawChrome(worldW, worldH);

        float canvasX = 40f;
        float canvasY = 65f;
        float canvasW = worldW - 80f;
        float canvasH = worldH - 185f;

        if (mode == Mode.OVERVIEW) {
            float legendW = 220f;
            renderOverview(canvasX, canvasY, canvasW - legendW - 15f, canvasH);
            drawLegendPanel(canvasX + canvasW - legendW, canvasY, legendW, canvasH);
        } else {
            renderZoom(canvasX, canvasY, canvasW, canvasH);
        }

        drawHeaderAndFooterText(worldW, worldH);
    }

    private void drawChrome(float worldW, float worldH) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(0.08f, 0.10f, 0.14f, 0.95f);
        shapeRenderer.rect(20, worldH - 105, worldW - 40, 85);

        float tabStartX = 40f;
        float tabW = tabWidth(worldW);
        for (int f = 1; f <= maxFloor; f++) {
            float tx = tabStartX + (f - 1) * (tabW + 10);
            if (f == viewFloor) {
                shapeRenderer.setColor(0.70f, 0.55f, 0.20f, 0.9f);
            } else {
                shapeRenderer.setColor(0.15f, 0.18f, 0.24f, 0.8f);
            }
            shapeRenderer.rect(tx, worldH - 95, tabW, 25);
        }

        shapeRenderer.setColor(0.06f, 0.08f, 0.11f, 0.95f);
        shapeRenderer.rect(20, 50, worldW - 40, worldH - 165);

        shapeRenderer.setColor(0.08f, 0.10f, 0.14f, 0.95f);
        shapeRenderer.rect(20, 15, worldW - 40, 30);

        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.35f, 0.40f, 0.48f, 0.6f);
        shapeRenderer.rect(20, worldH - 105, worldW - 40, 85);
        shapeRenderer.rect(20, 50, worldW - 40, worldH - 165);
        shapeRenderer.rect(20, 15, worldW - 40, 30);
        shapeRenderer.end();
    }

    private void drawHeaderAndFooterText(float worldW, float worldH) {
        game.getBatch().setProjectionMatrix(game.getViewport().getCamera().combined);
        game.getBatch().begin();

        font.getData().setScale(1.2f);
        font.setColor(Color.GOLD);
        font.draw(game.getBatch(), "TARMIN EXPEDITION CARTOGRAPHY", 40, worldH - 30);

        font.getData().setScale(0.9f);
        font.setColor(Color.LIGHT_GRAY);
        String subTitle = (viewFloor == 1)
                ? "OVERLAND WILDERNESS & CASTLE APPROACH (Depth 0)"
                : "SUBTERRANEAN STRATA " + (viewFloor - 1) + " (Depth " + (viewFloor - 1) + ")";
        if (mode == Mode.ZOOM && zoomedChunkId != null) {
            subTitle += String.format("  —  Chunk (%d, %d)", zoomedChunkId.x, zoomedChunkId.y);
        }
        font.draw(game.getBatch(), subTitle, 40, worldH - 50);

        font.getData().setScale(0.8f);
        float tabStartX = 40f;
        float tabW = tabWidth(worldW);
        for (int f = 1; f <= maxFloor; f++) {
            float tx = tabStartX + (f - 1) * (tabW + 10);
            font.setColor(f == viewFloor ? Color.WHITE : Color.GRAY);
            String label = (f == 1) ? "SURFACE" : "STRATA " + (f - 1);
            font.draw(game.getBatch(), label, tx + Math.max(4f, tabW * 0.15f), worldH - 77);
        }

        font.getData().setScale(0.85f);
        font.setColor(Color.WHITE);
        GridPoint2 pChunk = (worldManager != null) ? worldManager.getCurrentPlayerChunkId() : new GridPoint2(0, 0);
        int pLvl = (worldManager != null) ? worldManager.getCurrentLevel() : 1;
        String playerStatus = String.format("Current Location: Chunk (%d, %d) | Tile (%d, %d) | Depth: %s",
                pChunk.x, pChunk.y,
                (int) player.getPosition().x, (int) player.getPosition().y,
                (pLvl == 1 ? "Surface" : "Strata " + (pLvl - 1)));
        font.draw(game.getBatch(), playerStatus, 35, 36);

        font.setColor(Color.GOLD);
        String controls = (mode == Mode.OVERVIEW)
                ? "[Arrows] Move   [Enter] Zoom In   [PgUp/PgDn or 1-9] Switch Floor   [M] Close"
                : "[Esc] Back to Overview   [M] Close";
        font.draw(game.getBatch(), controls, worldW - 620, 36);

        game.getBatch().end();
    }

    // ------------------------------------------------------------------
    // Overview
    // ------------------------------------------------------------------

    private static class OverviewLayout {
        float canvasX, canvasY, chunkW, chunkH;
        int cols, rows, radius;
        GridPoint2 cursor;

        GridPoint2 chunkAt(float x, float y) {
            int c = (int) ((x - canvasX) / chunkW);
            int r = (int) ((y - canvasY) / chunkH);
            if (c < 0 || c >= cols || r < 0 || r >= rows) return null;
            return new GridPoint2(cursor.x + (c - radius), cursor.y + (r - radius));
        }
    }

    private void renderOverview(float canvasX, float canvasY, float canvasW, float canvasH) {
        int radius = WINDOW_RADIUS;
        int cols = radius * 2 + 1;
        int rows = radius * 2 + 1;
        float chunkW = canvasW / cols;
        float chunkH = canvasH / rows;

        GridPoint2 cursor = getCursor(viewFloor);
        OverviewLayout layout = new OverviewLayout();
        layout.canvasX = canvasX;
        layout.canvasY = canvasY;
        layout.chunkW = chunkW;
        layout.chunkH = chunkH;
        layout.cols = cols;
        layout.rows = rows;
        layout.radius = radius;
        layout.cursor = cursor;
        this.lastOverviewLayout = layout;

        Set<GridPoint2> visited = getVisitedForCurrentFloor();
        BiomeManager biomeManager = (worldManager != null) ? worldManager.getBiomeManager() : null;
        GridPoint2 playerChunk = (worldManager != null) ? worldManager.getCurrentPlayerChunkId() : null;
        int currentLevel = (worldManager != null) ? worldManager.getCurrentLevel() : 1;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                int cx = cursor.x + (c - radius);
                int cy = cursor.y + (r - radius);
                GridPoint2 chId = new GridPoint2(cx, cy);
                float x = canvasX + c * chunkW;
                float y = canvasY + r * chunkH;

                if (!visited.contains(chId)) {
                    continue; // Fully blank: unvisited chunks reveal nothing.
                }

                boolean isShelter = false;
                ChunkData data = getCachedChunkData(viewFloor, chId);
                if (data != null) {
                    isShelter = data.hasShelter();
                }

                if (viewFloor == 1) {
                    Biome biome = (biomeManager != null) ? biomeManager.getBiome(chId) : Biome.PLAINS;
                    shapeRenderer.setColor(styleFor(biome).color);
                } else {
                    shapeRenderer.setColor(0.16f, 0.18f, 0.22f, 0.9f); // Uniform "visited" strata color
                }

                if (isShelter) {
                    shapeRenderer.setColor(0.24f, 0.22f, 0.15f, 0.95f); // Shelter amber
                } else if (viewFloor == 1 && cx == CASTLE_CHUNK_X && cy == CASTLE_CHUNK_Y) {
                    shapeRenderer.setColor(0.28f, 0.14f, 0.14f, 0.95f); // Castle Citadel crimson
                }

                shapeRenderer.rect(x + 2, y + 2, chunkW - 4, chunkH - 4);
            }
        }

        // Player beacon, centered in their current chunk cell, on whichever floor they occupy.
        if (playerChunk != null && viewFloor == currentLevel) {
            int c = (playerChunk.x - cursor.x) + radius;
            int r = (playerChunk.y - cursor.y) + radius;
            if (c >= 0 && c < cols && r >= 0 && r < rows) {
                float px = canvasX + c * chunkW + chunkW / 2f;
                float py = canvasY + r * chunkH + chunkH / 2f;
                float pulse = 0.5f + 0.5f * (float) Math.sin(animationTimer * 6f);
                shapeRenderer.setColor(0.2f, 0.8f, 1.0f, 0.7f + 0.3f * pulse);
                shapeRenderer.circle(px, py, 6f + pulse * 2f);
            }
        }
        shapeRenderer.end();

        // Borders: visited (dim), player (cyan), shelter (gold), castle (red), cursor (white pulse).
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        float cursorPulse = 0.6f + 0.4f * (float) Math.sin(animationTimer * 5f);
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                int cx = cursor.x + (c - radius);
                int cy = cursor.y + (r - radius);
                GridPoint2 chId = new GridPoint2(cx, cy);
                float x = canvasX + c * chunkW;
                float y = canvasY + r * chunkH;
                boolean isVisited = visited.contains(chId);
                boolean isCursor = (c == radius && r == radius);

                if (isCursor) {
                    shapeRenderer.setColor(1f, 1f, 1f, cursorPulse);
                } else if (!isVisited) {
                    continue;
                } else if (playerChunk != null && cx == playerChunk.x && cy == playerChunk.y && viewFloor == currentLevel) {
                    shapeRenderer.setColor(Color.CYAN);
                } else {
                    ChunkData data = getCachedChunkData(viewFloor, chId);
                    if (data != null && data.hasShelter()) {
                        shapeRenderer.setColor(Color.GOLD);
                    } else if (viewFloor == 1 && cx == CASTLE_CHUNK_X && cy == CASTLE_CHUNK_Y) {
                        shapeRenderer.setColor(Color.RED);
                    } else {
                        shapeRenderer.setColor(0.25f, 0.30f, 0.38f, 0.5f);
                    }
                }
                shapeRenderer.rect(x + 2, y + 2, chunkW - 4, chunkH - 4);
            }
        }
        shapeRenderer.end();

        // Labels & strata ladder-connector icons
        game.getBatch().begin();
        font.getData().setScale(0.7f);
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                int cx = cursor.x + (c - radius);
                int cy = cursor.y + (r - radius);
                GridPoint2 chId = new GridPoint2(cx, cy);
                if (!visited.contains(chId)) continue;
                float x = canvasX + c * chunkW;
                float y = canvasY + r * chunkH;

                ChunkData data = getCachedChunkData(viewFloor, chId);
                boolean isShelter = data != null && data.hasShelter();

                if (isShelter) {
                    font.setColor(Color.GOLD);
                    font.draw(game.getBatch(), "HOME", x + 6, y + chunkH - 8);
                } else if (viewFloor == 1 && cx == CASTLE_CHUNK_X && cy == CASTLE_CHUNK_Y) {
                    font.setColor(Color.CORAL);
                    font.draw(game.getBatch(), "CASTLE", x + 6, y + chunkH - 8);
                } else if (viewFloor == 1) {
                    Biome biome = (biomeManager != null) ? biomeManager.getBiome(chId) : Biome.PLAINS;
                    font.setColor(Color.LIGHT_GRAY);
                    font.draw(game.getBatch(), styleFor(biome).letter, x + 6, y + chunkH - 8);
                }

                font.setColor(Color.GRAY);
                font.draw(game.getBatch(), String.format("(%d,%d)", cx, cy), x + 6, y + 14);

                if (viewFloor != 1 && data != null) {
                    if (data.hasUpLadder()) {
                        font.setColor(Color.GOLD);
                        font.draw(game.getBatch(), "↑", x + chunkW - 26, y + chunkH - 8);
                    }
                    if (data.hasDownLadder()) {
                        font.setColor(Color.ORANGE);
                        font.draw(game.getBatch(), "↓", x + chunkW - 14, y + chunkH - 8);
                    }
                }
            }
        }
        game.getBatch().end();
    }

    private void drawLegendPanel(float x, float y, float w, float h) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.07f, 0.09f, 0.13f, 0.9f);
        shapeRenderer.rect(x, y, w, h);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.3f, 0.34f, 0.42f, 0.6f);
        shapeRenderer.rect(x, y, w, h);
        shapeRenderer.end();

        game.getBatch().begin();
        font.getData().setScale(0.85f);
        font.setColor(Color.GOLD);
        font.draw(game.getBatch(), "LEGEND", x + 12, y + h - 14);

        font.getData().setScale(0.72f);
        float lineY = y + h - 40;
        float lineH = 22f;

        if (viewFloor == 1) {
            for (BiomeStyle style : BIOME_STYLES.values()) {
                lineY = drawLegendLine(x, lineY, lineH, style.letter, Color.LIGHT_GRAY, style.legendName);
            }
        } else {
            lineY = drawLegendLine(x, lineY, lineH, "↑", Color.GOLD, "Ascent ladder");
            lineY = drawLegendLine(x, lineY, lineH, "↓", Color.ORANGE, "Descent ladder");
        }
        lineY -= 6f;
        lineY = drawLegendLine(x, lineY, lineH, "■", Color.GOLD, "Your shelter");
        lineY = drawLegendLine(x, lineY, lineH, "●", Color.CYAN, "You are here");
        lineY = drawLegendLine(x, lineY, lineH, "□", Color.WHITE, "Cursor");
        lineY = drawLegendLine(x, lineY, lineH, " ", Color.GRAY, "Unvisited (blank)");

        game.getBatch().end();
    }

    private float drawLegendLine(float x, float y, float lineH, String glyph, Color glyphColor, String label) {
        font.setColor(glyphColor);
        font.draw(game.getBatch(), glyph, x + 12, y);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(game.getBatch(), label, x + 34, y);
        return y - lineH;
    }

    // ------------------------------------------------------------------
    // Zoom (single-chunk detail)
    // ------------------------------------------------------------------

    private ZoomView buildZoomView(int floor, GridPoint2 chunkId) {
        boolean isLiveCurrentChunk = worldManager != null && maze != null
                && floor == worldManager.getCurrentLevel()
                && chunkId.equals(worldManager.getCurrentPlayerChunkId());
        if (isLiveCurrentChunk) {
            return ZoomView.fromMaze(maze);
        }
        ChunkData data = getCachedChunkData(floor, chunkId);
        if (data == null || data.wallData == null) return null;
        return ZoomView.fromChunkData(data, itemDataManager, assetManager);
    }

    private void renderZoom(float canvasX, float canvasY, float canvasW, float canvasH) {
        if (zoomView == null) return;
        int width = zoomView.width;
        int height = zoomView.height;
        if (width <= 0 || height <= 0) return;

        float tileSize = Math.min((canvasW - 40) / width, (canvasH - 60) / height);
        float startX = canvasX + (canvasW - width * tileSize) / 2f;
        float startY = canvasY + (canvasH - height * tileSize) / 2f;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float rx = startX + x * tileSize;
                float ry = startY + y * tileSize;

                if (!zoomView.isVisited(x, y)) {
                    shapeRenderer.setColor(0.03f, 0.03f, 0.04f, 0.95f); // Fog: never actually explored
                } else if (zoomView.homeTiles.contains(new GridPoint2(x, y))) {
                    shapeRenderer.setColor(0.45f, 0.36f, 0.14f, 0.95f); // Shelter highlight
                } else if (zoomView.wallAt(x, y) != 0) {
                    shapeRenderer.setColor(0.18f, 0.20f, 0.25f, 0.95f); // Wall
                } else {
                    shapeRenderer.setColor(0.08f, 0.09f, 0.12f, 0.95f); // Floor
                }
                shapeRenderer.rect(rx, ry, tileSize - 1, tileSize - 1);

                if (zoomView.isVisited(x, y)) {
                    String eventId = zoomView.events.get(new GridPoint2(x, y));
                    if (eventId != null) {
                        shapeRenderer.setColor(eventId.contains("STATUE") ? Color.LIME : Color.ROYAL);
                        shapeRenderer.circle(rx + tileSize / 2f, ry + tileSize / 2f, tileSize * 0.2f);
                    }
                }
            }
        }
        shapeRenderer.end();

        // Sprite icons: ladders and items, only on tiles the player has actually seen.
        game.getBatch().begin();
        for (Map.Entry<GridPoint2, Ladder.LadderType> entry : zoomView.ladders.entrySet()) {
            GridPoint2 pos = entry.getKey();
            if (!zoomView.isVisited(pos.x, pos.y)) continue;
            float rx = startX + pos.x * tileSize;
            float ry = startY + pos.y * tileSize;
            Texture tex = (entry.getValue() == Ladder.LadderType.UP) ? ladderUpTexture : ladderDownTexture;
            float pad = tileSize * 0.1f;
            game.getBatch().draw(tex, rx + pad, ry + pad, tileSize - pad * 2, tileSize - pad * 2);
        }
        for (Map.Entry<GridPoint2, TextureRegion> entry : zoomView.itemIcons.entrySet()) {
            GridPoint2 pos = entry.getKey();
            if (!zoomView.isVisited(pos.x, pos.y)) continue;
            float rx = startX + pos.x * tileSize;
            float ry = startY + pos.y * tileSize;
            float pad = tileSize * 0.22f;
            game.getBatch().draw(entry.getValue(), rx + pad, ry + pad, tileSize - pad * 2, tileSize - pad * 2);
        }
        game.getBatch().end();

        // Player position, only meaningful when viewing the floor/chunk they're actually standing in.
        boolean showPlayer = worldManager != null && zoomedChunkId != null
                && viewFloor == worldManager.getCurrentLevel()
                && zoomedChunkId.equals(worldManager.getCurrentPlayerChunkId());
        if (showPlayer) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            float px = startX + player.getPosition().x * tileSize;
            float py = startY + player.getPosition().y * tileSize;
            float pulse = 0.5f + 0.5f * (float) Math.sin(animationTimer * 6f);
            shapeRenderer.setColor(0.2f, 0.9f, 1.0f, 0.8f + 0.2f * pulse);
            shapeRenderer.circle(px, py, tileSize * 0.45f);
            shapeRenderer.end();

            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            Direction facing = player.getFacing();
            Vector2 dir = facing.getVector();
            shapeRenderer.setColor(Color.WHITE);
            shapeRenderer.line(px, py, px + dir.x * (tileSize * 0.65f), py + dir.y * (tileSize * 0.65f));
            shapeRenderer.end();
        }

        game.getBatch().begin();
        font.getData().setScale(0.75f);
        font.setColor(Color.GOLD);
        font.draw(game.getBatch(), "↑ Ascent", canvasX + 20, canvasY + canvasH - 15);
        font.setColor(Color.ORANGE);
        font.draw(game.getBatch(), "↓ Descent", canvasX + 150, canvasY + canvasH - 15);
        font.setColor(Color.CYAN);
        font.draw(game.getBatch(), "● Player", canvasX + 290, canvasY + canvasH - 15);
        font.setColor(new Color(0.85f, 0.68f, 0.25f, 1f));
        font.draw(game.getBatch(), "■ Shelter", canvasX + 410, canvasY + canvasH - 15);
        font.setColor(Color.LIME);
        font.draw(game.getBatch(), "● Point of Interest", canvasX + 530, canvasY + canvasH - 15);
        game.getBatch().end();
    }

    /** Pre-resolved, render-ready snapshot of one chunk's unlocked detail. */
    private static class ZoomView {
        int width;
        int height;
        int[][] wallData;
        byte[][] explorationState;
        final Map<GridPoint2, Ladder.LadderType> ladders = new HashMap<>();
        final Map<GridPoint2, TextureRegion> itemIcons = new HashMap<>();
        final Map<GridPoint2, String> events = new HashMap<>();
        final Set<GridPoint2> homeTiles = new HashSet<>();

        boolean isVisited(int x, int y) {
            if (explorationState == null || x < 0 || x >= width || y < 0 || y >= height) return false;
            return explorationState[y][x] == Maze.VISIBILITY_SEEN;
        }

        int wallAt(int x, int y) {
            if (wallData == null || x < 0 || x >= width || y < 0 || y >= height) return 0;
            return wallData[y][x];
        }

        static ZoomView fromMaze(Maze maze) {
            ZoomView v = new ZoomView();
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

        static ZoomView fromChunkData(ChunkData data, ItemDataManager itemDataManager, AssetManager assetManager) {
            ZoomView v = new ZoomView();
            v.wallData = data.wallData;
            v.height = v.wallData.length;
            v.width = v.height > 0 ? v.wallData[0].length : 0;
            v.explorationState = data.explorationState;
            if (data.homeTiles != null) v.homeTiles.addAll(data.homeTiles);
            if (data.ladders != null) {
                for (ChunkData.LadderData ld : data.ladders) {
                    v.ladders.put(new GridPoint2(ld.x, ld.y), ld.type);
                }
            }
            if (data.events != null) {
                for (ChunkData.EventData ed : data.events) {
                    v.events.put(new GridPoint2(ed.x, ed.y), ed.eventId);
                }
            }
            if (data.items != null && itemDataManager != null && assetManager != null) {
                for (ChunkData.ItemData id : data.items) {
                    try {
                        Item item = new Item(id.type, id.x, id.y, id.color, itemDataManager, assetManager);
                        TextureRegion region = item.getTextureRegion();
                        if (region != null) {
                            v.itemIcons.put(new GridPoint2(id.x, id.y), region);
                        }
                    } catch (Exception ignored) {
                        // Missing/unresolvable icon: tile still renders, just without an item marker.
                    }
                }
            }
            return v;
        }
    }

    @Override
    public void dispose() {
        font.dispose();
        shapeRenderer.dispose();
        ladderUpTexture.dispose();
        ladderDownTexture.dispose();
    }
}
