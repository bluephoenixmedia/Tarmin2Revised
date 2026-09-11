package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Ladder;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.WorldManager;

public class CastleMapScreen extends BaseScreen {

    private final Player player;
    private final Maze maze;
    private final GameScreen gameScreen;
    private final WorldManager worldManager;
    private final BitmapFont font;
    private final ShapeRenderer shapeRenderer;

    private int viewFloor = 1;
    private float animationTimer = 0f;

    private static final int MIN_FLOOR = 1;
    private static final int MAX_FLOOR = 6;

    public CastleMapScreen(Tarmin2 game, Player player, Maze maze, GameScreen gameScreen) {
        super(game);
        this.player = player;
        this.maze = maze;
        this.gameScreen = gameScreen;
        this.worldManager = (gameScreen != null) ? gameScreen.getWorldManager() : null;
        this.font = new BitmapFont();
        this.shapeRenderer = new ShapeRenderer();

        if (worldManager != null) {
            this.viewFloor = worldManager.getCurrentLevel();
        } else if (maze != null) {
            this.viewFloor = maze.getLevel();
        }
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.M || keycode == Input.Keys.ESCAPE) {
                    game.setScreen(gameScreen);
                    return true;
                }
                // Floor navigation
                if (keycode == Input.Keys.PAGE_UP || keycode == Input.Keys.RIGHT_BRACKET || keycode == Input.Keys.UP) {
                    viewFloor = Math.max(MIN_FLOOR, viewFloor - 1);
                    return true;
                }
                if (keycode == Input.Keys.PAGE_DOWN || keycode == Input.Keys.LEFT_BRACKET || keycode == Input.Keys.DOWN) {
                    viewFloor = Math.min(MAX_FLOOR, viewFloor + 1);
                    return true;
                }
                // Direct floor shortcuts (1-6)
                if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_6) {
                    viewFloor = keycode - Input.Keys.NUM_1 + 1;
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                // Handle tab clicks at top of screen
                float worldW = game.getViewport().getWorldWidth();
                float worldH = game.getViewport().getWorldHeight();
                float glY = Gdx.graphics.getHeight() - screenY;
                float normX = (screenX / (float) Gdx.graphics.getWidth()) * worldW;
                float normY = (glY / (float) Gdx.graphics.getHeight()) * worldH;

                if (normY >= worldH - 95 && normY <= worldH - 65) {
                    float tabStartX = 40f;
                    float tabW = 100f;
                    for (int f = 1; f <= MAX_FLOOR; f++) {
                        float tx = tabStartX + (f - 1) * (tabW + 10);
                        if (normX >= tx && normX <= tx + tabW) {
                            viewFloor = f;
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }

    @Override
    public void render(float delta) {
        animationTimer += delta;
        ScreenUtils.clear(0.04f, 0.05f, 0.07f, 1f);

        float worldW = game.getViewport().getWorldWidth();
        float worldH = game.getViewport().getWorldHeight();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);

        // 1. Draw Background Frame and Tabs
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Header Background
        shapeRenderer.setColor(0.08f, 0.10f, 0.14f, 0.95f);
        shapeRenderer.rect(20, worldH - 105, worldW - 40, 85);

        // Tabs
        float tabStartX = 40f;
        float tabW = 100f;
        for (int f = 1; f <= MAX_FLOOR; f++) {
            float tx = tabStartX + (f - 1) * (tabW + 10);
            if (f == viewFloor) {
                shapeRenderer.setColor(0.70f, 0.55f, 0.20f, 0.9f); // Gold highlight
            } else {
                shapeRenderer.setColor(0.15f, 0.18f, 0.24f, 0.8f);
            }
            shapeRenderer.rect(tx, worldH - 95, tabW, 25);
        }

        // Main Map Canvas Background
        shapeRenderer.setColor(0.06f, 0.08f, 0.11f, 0.95f);
        shapeRenderer.rect(20, 50, worldW - 40, worldH - 165);

        // Footer Background
        shapeRenderer.setColor(0.08f, 0.10f, 0.14f, 0.95f);
        shapeRenderer.rect(20, 15, worldW - 40, 30);

        shapeRenderer.end();

        // Outline Borders
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.35f, 0.40f, 0.48f, 0.6f);
        shapeRenderer.rect(20, worldH - 105, worldW - 40, 85);
        shapeRenderer.rect(20, 50, worldW - 40, worldH - 165);
        shapeRenderer.rect(20, 15, worldW - 40, 30);
        shapeRenderer.end();

        // 2. Render Map Content based on viewFloor
        if (viewFloor == 1) {
            renderOverlandMap(worldW, worldH);
        } else {
            renderStrataMap(worldW, worldH);
        }

        // 3. Text Overlay
        game.getBatch().setProjectionMatrix(game.getViewport().getCamera().combined);
        game.getBatch().begin();

        // Header Title
        font.getData().setScale(1.2f);
        font.setColor(Color.GOLD);
        font.draw(game.getBatch(), "TARMIN EXPEDITION CARTOGRAPHY", 40, worldH - 30);

        // Floor Subtitle
        font.getData().setScale(0.9f);
        font.setColor(Color.LIGHT_GRAY);
        String subTitle;
        if (viewFloor == 1) {
            subTitle = "OVERLAND WILDERNESS & CASTLE APPROACH (Depth 0)";
        } else if (viewFloor == 2) {
            subTitle = "SUBTERRANEAN STRATA I — THE ANCIENT CRYPTS (Depth 1)";
        } else if (viewFloor == 3) {
            subTitle = "SUBTERRANEAN STRATA II — THE LOWER CATACOMBS (Depth 2)";
        } else if (viewFloor == 4) {
            subTitle = "SUBTERRANEAN STRATA III — ROOT CAVERNS & GROTTO (Depth 3)";
        } else if (viewFloor == 5) {
            subTitle = "SUBTERRANEAN STRATA IV — FLOODED CHASM VAULTS (Depth 4)";
        } else {
            subTitle = "SUBTERRANEAN STRATA V+ — THE ABYSSAL UNDERHALLS (Depth 5+)";
        }
        font.draw(game.getBatch(), subTitle, 40, worldH - 50);

        // Tab Labels
        font.getData().setScale(0.8f);
        for (int f = 1; f <= MAX_FLOOR; f++) {
            float tx = tabStartX + (f - 1) * (tabW + 10);
            font.setColor(f == viewFloor ? Color.WHITE : Color.GRAY);
            String label = (f == 1) ? "SURFACE" : "STRATA " + (f - 1);
            font.draw(game.getBatch(), label, tx + 18, worldH - 77);
        }

        // Footer Text
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
        String controls = "[Page Up/Down or 1-6] Switch Layers   |   [M / ESC] Return";
        font.draw(game.getBatch(), controls, worldW - 480, 36);

        game.getBatch().end();
    }

    private void renderOverlandMap(float worldW, float worldH) {
        float canvasX = 40f;
        float canvasY = 65f;
        float canvasW = worldW - 80f;
        float canvasH = worldH - 185f;

        // 5 columns (X: -2 to +2), 6 rows (Y: 0 to 5)
        int cols = 5;
        int rows = 6;
        float chunkW = (canvasW - 40) / cols;
        float chunkH = (canvasH - 40) / rows;

        BiomeManager biomeManager = (worldManager != null) ? worldManager.getBiomeManager() : null;
        GridPoint2 playerChunk = (worldManager != null) ? worldManager.getCurrentPlayerChunkId() : new GridPoint2(0, 0);
        int currentLevel = (worldManager != null) ? worldManager.getCurrentLevel() : 1;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int c = 0; c < cols; c++) {
            int cx = c - 2; // -2 to +2
            for (int r = 0; r < rows; r++) {
                int cy = r; // 0 to 5
                float x = canvasX + 20 + c * chunkW;
                float y = canvasY + 20 + r * chunkH;

                GridPoint2 chId = new GridPoint2(cx, cy);
                Biome biome = (biomeManager != null) ? biomeManager.getBiome(chId) : Biome.PLAINS;

                // Color by biome
                if (biome == Biome.FOREST) {
                    shapeRenderer.setColor(0.12f, 0.22f, 0.12f, 0.9f);
                } else if (biome == Biome.PLAINS) {
                    shapeRenderer.setColor(0.18f, 0.22f, 0.14f, 0.9f);
                } else if (biome == Biome.OCEAN) {
                    shapeRenderer.setColor(0.08f, 0.12f, 0.24f, 0.9f);
                } else if (biome == Biome.MOUNTAINS) {
                    shapeRenderer.setColor(0.15f, 0.15f, 0.17f, 0.9f);
                } else {
                    shapeRenderer.setColor(0.18f, 0.17f, 0.22f, 0.9f);
                }

                // Special Landmarks
                if (cx == 0 && cy == 0) {
                    shapeRenderer.setColor(0.24f, 0.22f, 0.15f, 0.95f); // Shelter Amber
                } else if (cx == 0 && cy == 5) {
                    shapeRenderer.setColor(0.28f, 0.14f, 0.14f, 0.95f); // Castle Crimson
                }

                shapeRenderer.rect(x + 2, y + 2, chunkW - 4, chunkH - 4);
            }
        }

        // Active Player Beacon on Surface
        if (currentLevel == 1) {
            int colIdx = playerChunk.x + 2;
            int rowIdx = playerChunk.y;
            if (colIdx >= 0 && colIdx < cols && rowIdx >= 0 && rowIdx < rows) {
                float px = canvasX + 20 + colIdx * chunkW + (player.getPosition().x / 16f) * (chunkW - 4);
                float py = canvasY + 20 + rowIdx * chunkH + (player.getPosition().y / 16f) * (chunkH - 4);

                float pulse = 0.5f + 0.5f * (float) Math.sin(animationTimer * 6f);
                shapeRenderer.setColor(0.2f, 0.8f, 1.0f, 0.7f + 0.3f * pulse);
                shapeRenderer.circle(px, py, 6f + pulse * 2f);
            }
        }

        shapeRenderer.end();

        // Outlines & Grid Lines
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (int c = 0; c < cols; c++) {
            int cx = c - 2;
            for (int r = 0; r < rows; r++) {
                int cy = r;
                float x = canvasX + 20 + c * chunkW;
                float y = canvasY + 20 + r * chunkH;

                if (cx == playerChunk.x && cy == playerChunk.y && currentLevel == 1) {
                    shapeRenderer.setColor(Color.CYAN);
                } else if (cx == 0 && cy == 0) {
                    shapeRenderer.setColor(Color.GOLD);
                } else if (cx == 0 && cy == 5) {
                    shapeRenderer.setColor(Color.RED);
                } else {
                    shapeRenderer.setColor(0.25f, 0.30f, 0.38f, 0.5f);
                }
                shapeRenderer.rect(x + 2, y + 2, chunkW - 4, chunkH - 4);
            }
        }
        shapeRenderer.end();

        // Text labels inside chunks
        game.getBatch().begin();
        font.getData().setScale(0.75f);
        for (int c = 0; c < cols; c++) {
            int cx = c - 2;
            for (int r = 0; r < rows; r++) {
                int cy = r;
                float x = canvasX + 20 + c * chunkW;
                float y = canvasY + 20 + r * chunkH;

                if (cx == 0 && cy == 0) {
                    font.setColor(Color.GOLD);
                    font.draw(game.getBatch(), "[SHELTER]", x + 8, y + chunkH - 10);
                    font.draw(game.getBatch(), "Camp Sanctuary", x + 8, y + 20);
                } else if (cx == 0 && cy == 5) {
                    font.setColor(Color.CORAL);
                    font.draw(game.getBatch(), "[CASTLE CITADEL]", x + 8, y + chunkH - 10);
                    font.draw(game.getBatch(), "Sealed Tarmin Gate", x + 8, y + 20);
                } else {
                    font.setColor(Color.LIGHT_GRAY);
                    GridPoint2 chId = new GridPoint2(cx, cy);
                    Biome biome = (biomeManager != null) ? biomeManager.getBiome(chId) : Biome.PLAINS;
                    font.draw(game.getBatch(), biome.name(), x + 8, y + chunkH - 10);
                    font.draw(game.getBatch(), String.format("(%d, %d)", cx, cy), x + 8, y + 20);
                }
            }
        }
        game.getBatch().end();
    }

    private void renderStrataMap(float worldW, float worldH) {
        float canvasX = 40f;
        float canvasY = 65f;
        float canvasW = worldW - 80f;
        float canvasH = worldH - 185f;

        int currentLevel = (worldManager != null) ? worldManager.getCurrentLevel() : 1;

        if (maze == null || currentLevel != viewFloor) {
            // Unsurveyed Floor or Different Floor
            game.getBatch().begin();
            font.getData().setScale(1.1f);
            font.setColor(Color.GRAY);
            String msg = "UNSURVEYED SUBTERRANEAN STRATA DEPTH " + (viewFloor - 1);
            font.draw(game.getBatch(), msg, canvasX + 60, canvasY + canvasH / 2 + 20);
            font.getData().setScale(0.85f);
            font.draw(game.getBatch(), "Descend via surface sinkholes or catacomb stairs to map this stratum in real-time.",
                    canvasX + 60, canvasY + canvasH / 2 - 15);
            game.getBatch().end();
            return;
        }

        int width = maze.getWidth();
        int height = maze.getHeight();
        float tileSize = Math.min((canvasW - 40) / width, (canvasH - 40) / height);
        float startX = canvasX + (canvasW - width * tileSize) / 2f;
        float startY = canvasY + (canvasH - height * tileSize) / 2f;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float rx = startX + x * tileSize;
                float ry = startY + y * tileSize;

                int wall = maze.getWallDataAt(x, y);
                // Floor or Wall
                if (wall != 0) {
                    shapeRenderer.setColor(0.18f, 0.20f, 0.25f, 0.95f); // Wall
                } else {
                    shapeRenderer.setColor(0.08f, 0.09f, 0.12f, 0.95f); // Floor
                }
                shapeRenderer.rect(rx, ry, tileSize - 1, tileSize - 1);

                // Ladders / Entrances
                Ladder ladder = maze.getLadders().get(new GridPoint2(x, y));
                if (ladder != null) {
                    if (ladder.getType() == Ladder.LadderType.UP) {
                        shapeRenderer.setColor(Color.GOLD);
                    } else {
                        shapeRenderer.setColor(Color.ORANGE);
                    }
                    shapeRenderer.rect(rx + tileSize * 0.25f, ry + tileSize * 0.25f, tileSize * 0.5f, tileSize * 0.5f);
                }
            }
        }

        // Render Player Position Indicator
        float px = startX + player.getPosition().x * tileSize;
        float py = startY + player.getPosition().y * tileSize;
        float pulse = 0.5f + 0.5f * (float) Math.sin(animationTimer * 6f);
        shapeRenderer.setColor(0.2f, 0.9f, 1.0f, 0.8f + 0.2f * pulse);
        shapeRenderer.circle(px, py, tileSize * 0.45f);

        // Directional pointer
        Direction facing = player.getFacing();
        Vector2 dir = facing.getVector();
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.line(px, py, px + dir.x * (tileSize * 0.65f), py + dir.y * (tileSize * 0.65f));

        shapeRenderer.end();

        // Legend overlay
        game.getBatch().begin();
        font.getData().setScale(0.8f);
        font.setColor(Color.GOLD);
        font.draw(game.getBatch(), "[H] Ascent Transit", canvasX + 20, canvasY + canvasH - 15);
        font.setColor(Color.ORANGE);
        font.draw(game.getBatch(), "[V] Descent Transit", canvasX + 170, canvasY + canvasH - 15);
        font.setColor(Color.CYAN);
        font.draw(game.getBatch(), "(O) Player Position", canvasX + 320, canvasY + canvasH - 15);
        game.getBatch().end();
    }

    @Override
    public void dispose() {
        font.dispose();
        shapeRenderer.dispose();
    }
}
