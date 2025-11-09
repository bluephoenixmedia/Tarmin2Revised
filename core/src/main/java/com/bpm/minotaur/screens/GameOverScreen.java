package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.managers.SoundManager;

public class GameOverScreen extends BaseScreen {

    private final BitmapFont font;
    private SoundManager soundManager;
    private DebugManager debugManager;

    /**
     * The texture for our reaper sprite, generated from REAPER_DATA.
     */
    private Texture reaperTexture;


    private Texture backgroundTexture;


    /**
     * ASCII-art data for the reaper sprite.
     * '#' represents a solid pixel, '.' is transparent.
     */
    private static final String[] REAPER_DATA = {
        //123456789012345678901234
        "........................",
        ".###............###.....",
        "..###..........###......",
        "...##.........##........",
        "...##.........##........",
        "...#####...#####........",
        ".....########...........",
        "......##.##.#...........",
        ".......##.##......####..",
        ".###########.....##..##.",
        ".#####.#...#....##..##..",
        "..#####.##.....##..##...",
        "..##.#######..##...#....",
        ".##.##.########.........",
        "##.##.#.#####...........",
        ".###.#.#####............",
        "###.#.########..........",
        ".###.#########..........",
        "..#.####.##.#...........",
        ".####.###.##.#..........",
        "..##.####.##.#..........",
        ".##.##########..........",
        "..##......##............",
        "..##......##............",
    };


    public GameOverScreen(Tarmin2 game) {
        super(game);
        this.font = new BitmapFont();
        this.font.setColor(Color.RED);
        this.font.getData().setScale(3);

        // Note: The debugManager is null here, which may cause an issue in your SoundManager constructor.
        // For this task, we will proceed, but you may need to address that.
        this.soundManager = new SoundManager(debugManager);
        this.soundManager.playPlayerDeathSound();

        try {
            this.backgroundTexture = new Texture(Gdx.files.internal("images/retro_skybox_castle_storm.png"));
        } catch (Exception e) {
            Gdx.app.error("GameOverScreen", "Failed to load background texture", e);
            this.backgroundTexture = null;
        }

        // Create the reaper texture from our data, using a ghostly white color
        this.reaperTexture = createTextureFromData(REAPER_DATA, Color.MAGENTA);

        clearWorldSaves();

    }

    /**
     * [NEW ROBUST VERSION]
     * Deletes all files within the 'saves/world/' directory, one by one,
     * with detailed logging.
     */
    private void clearWorldSaves() {
        Gdx.app.log("GameOverScreen", "Attempting to clear world saves...");
        FileHandle dirHandle = Gdx.files.local("saves/world/");

        // First, check if the directory handle is valid
        if (!dirHandle.exists()) {
            Gdx.app.error("GameOverScreen", "Directory does not exist: " + dirHandle.path());
            Gdx.app.error("GameOverScreen", "Check if game working directory is set to project root!");
            return;
        }

        if (!dirHandle.isDirectory()) {
            Gdx.app.error("GameOverScreen", "Handle is a file, not a directory: " + dirHandle.path());
            return;
        }

        // Get the list of files
        FileHandle[] files = dirHandle.list();
        if (files.length == 0) {
            Gdx.app.log("GameOverScreen", "'saves/world/' is already empty. Nothing to do.");
            return;
        }

        Gdx.app.log("GameOverScreen", "Found " + files.length + " files to delete...");

        int deleteCount = 0;
        try {
            // Iterate and delete each file
            for (FileHandle file : files) {
                // We only care about our json files, leave other things alone
                if (file.extension().equalsIgnoreCase("json")) {
                    if (file.delete()) {
                        Gdx.app.log("GameOverScreen", "  > DELETED: " + file.name());
                        deleteCount++;
                    } else {
                        // This is the key log message!
                        Gdx.app.error("GameOverScreen", "  > FAILED TO DELETE: " + file.name());
                    }
                } else {
                    Gdx.app.log("GameOverScreen", "  > Skipping non-json file: " + file.name());
                }
            }
        } catch (Exception e) {
            Gdx.app.error("GameOverScreen", "Failed to clear world saves with exception.", e);
        }

        Gdx.app.log("GameOverScreen", "Clear saves complete. Deleted " + deleteCount + "/" + files.length + " files.");
    }
    // --- [END NEW METHOD] ---

    /**
     * Generates a Texture from a String[] sprite data template.
     *
     * @param spriteData The String[] array, where '#' is a pixel and '.' is transparent.
     * @param color      The LibGDX Color to use for the pixels.
     * @return A new Texture.
     */
    private Texture createTextureFromData(String[] spriteData, Color color) {
        if (spriteData.length == 0) {
            return null;
        }

        int height = spriteData.length;
        int width = spriteData[0].length();

        // 1. Create a Pixmap. This is a pixel-buffer in memory.
        // We use RGBA8888 to support transparency.
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);

        // 2. Convert the LibGDX Color to an RGBA8888 integer.
        int drawColor = Color.rgba8888(color);
        int transparentColor = Color.rgba8888(0, 0, 0, 0);

        // 3. Iterate over the data and draw pixels to the Pixmap
        for (int y = 0; y < height; y++) {
            String row = spriteData[y];
            for (int x = 0; x < width; x++) {
                char c = row.charAt(x);
                if (c == '#') { // '#' is our solid pixel
                    pixmap.drawPixel(x, y, drawColor);
                } else { // '.' or anything else is transparent
                    pixmap.drawPixel(x, y, transparentColor);
                }
            }
        }

        // 4. Create the Texture from the Pixmap
        Texture texture = new Texture(pixmap);

        // 5. Dispose the Pixmap, as it's no longer needed (data is now on the GPU)
        pixmap.dispose();

        return texture;
    }

    @Override
    public void show() {
        // soundManager.playPlayerDeathSound(); // Already played in constructor
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                // Allow ANY key to return
                game.setScreen(new MainMenuScreen(game));
                return true;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                game.setScreen(new MainMenuScreen(game));
                return true;
            }
        });
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);

        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
        game.batch.begin();

        if (backgroundTexture != null) {
            // Set color to white to draw the texture without any tint
            game.batch.setColor(Color.WHITE);

            // Draw the texture to fill the entire viewport
            game.batch.draw(backgroundTexture,
                0, 0, // Draw at the bottom-left corner
                game.viewport.getWorldWidth(), game.viewport.getWorldHeight() // Scale to fit the screen
            );
        }

        // --- Draw the Reaper Sprite ---
        if (reaperTexture != null) {
            // The sprite is 24x24 pixels, which is tiny. We need to scale it up.
            // Let's make it 15x larger.
            float scale = 35.0f;
            float spriteWidth = reaperTexture.getWidth() * scale;
            float spriteHeight = reaperTexture.getHeight() * scale;

            // Calculate position to center it horizontally and place it above the text
            float x = (game.viewport.getWorldWidth() / 2) - (spriteWidth / 2) - 200;
            float y = (game.viewport.getWorldHeight() / 2) - 500; // Position above "GAME OVER"

            game.batch.draw(reaperTexture, x, y, spriteWidth, spriteHeight);
        }
        // --- End Sprite ---

        // Draw text
        font.draw(game.batch, "GAME OVER", game.viewport.getWorldWidth() / 2 - 150, game.viewport.getWorldHeight() / 2 + 50);
        font.draw(game.batch, "Press any key to return to the Main Menu", game.viewport.getWorldWidth() / 2 - 450, game.viewport.getWorldHeight() / 2 - 50);

        game.batch.end();
    }

    @Override
    public void dispose() {
        font.dispose();
        // IMPORTANT: Always dispose of textures you create
        if (reaperTexture != null) {
            reaperTexture.dispose();
        }
    }

    // ... (rest of the input adapter methods remain unchanged) ...
    @Override
    public boolean keyDown(int keycode) {
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
    }
}
