package com.bpm.minotaur.rendering;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.managers.DayNightManager;
import com.bpm.minotaur.weather.WeatherType;

/**
 * Renders the volcanic sky across a fixed matrix of time-of-day, weather and heading states and
 * writes each one to a PNG.
 *
 * <p>A sky driven by time, weather and doom has a combinatorial space that manual play-testing
 * will miss -- "night is invisible" or "storm looks identical to clear" are exactly the defects
 * that only show up side by side. These captures make the palette reviewable as a contact sheet.
 *
 * <p>Deliberately standalone: it drives {@link Skybox3DRenderer} through its explicit-state seam
 * rather than booting a world, so it needs no save, no maze and no player.
 */
public class SkyCaptureHarness extends ApplicationAdapter {

    /** Rendered at the same dimensions as the in-game 3D viewport band. */
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 880;

    /** Frames to render before grabbing, so cloud noise and celestial transforms have settled. */
    private static final int WARMUP_FRAMES = 3;

    private static final class Shot {
        final String name;
        final float timeOfDay;
        final WeatherType weather;
        final float cloudCover;
        final boolean stormy;
        final float flash;
        final float headingRadians;
        final float northProgress;

        Shot(String name, float timeOfDay, WeatherType weather, float cloudCover,
             boolean stormy, float flash, float headingRadians, float northProgress) {
            this.name = name;
            this.timeOfDay = timeOfDay;
            this.weather = weather;
            this.cloudCover = cloudCover;
            this.stormy = stormy;
            this.flash = flash;
            this.headingRadians = headingRadians;
            this.northProgress = northProgress;
        }
    }

    private static final float NORTH = 0f;
    private static final float EAST = (float) (Math.PI / 2.0);
    private static final float SOUTH = (float) Math.PI;
    private static final float WEST = (float) (3.0 * Math.PI / 2.0);

    private static final Shot[] SHOTS = {
            // The default view: clear weather, morning. This is the shot the whole redesign is
            // judged against, and the one that used to render as flat grey.
            new Shot("01_morning_clear_north", 0.37f, WeatherType.CLEAR, 0f, false, 0f, NORTH, 0f),
            new Shot("02_dawn_clear_north", 0.24f, WeatherType.CLEAR, 0f, false, 0f, NORTH, 0f),
            new Shot("03_noon_clear_north", 0.50f, WeatherType.CLEAR, 0f, false, 0f, NORTH, 0f),
            new Shot("04_dusk_clear_north", 0.72f, WeatherType.CLEAR, 0f, false, 0f, NORTH, 0f),
            new Shot("05_night_clear_north", 0.95f, WeatherType.CLEAR, 0f, false, 0f, NORTH, 0f),

            // Weather must still read as an event on top of the permanent smoke ceiling.
            new Shot("06_noon_storm_north", 0.50f, WeatherType.STORM, 1f, true, 0f, NORTH, 0f),
            new Shot("07_noon_storm_flash", 0.50f, WeatherType.STORM, 1f, true, 0.9f, NORTH, 0f),
            new Shot("08_night_storm_north", 0.95f, WeatherType.STORM, 1f, true, 0f, NORTH, 0f),

            // Bearings: fire concentrates north, so the other three must read as smoke, not fire.
            new Shot("09_noon_clear_east", 0.50f, WeatherType.CLEAR, 0f, false, 0f, EAST, 0f),
            new Shot("10_noon_clear_south", 0.50f, WeatherType.CLEAR, 0f, false, 0f, SOUTH, 0f),
            new Shot("11_noon_clear_west", 0.50f, WeatherType.CLEAR, 0f, false, 0f, WEST, 0f),

            // Castle approach: the landmark should grow and close as the expedition pushes north.
            new Shot("12_noon_castle_near", 0.50f, WeatherType.CLEAR, 0f, false, 0f, NORTH, 1f),
    };

    /** Banner dimensions for the RETRO raycaster's static directional skyboxes. */
    private static final int BANNER_WIDTH = 1024;
    private static final int BANNER_HEIGHT = 320;

    /** Fraction of the captured frame, measured from the top, that is sky above the horizon. */
    private static final float BANNER_SKY_FRACTION = 0.64f;

    /**
     * The RETRO raycaster draws a flat directional PNG instead of the 3D dome. Rendering those
     * banners out of the dome itself is the only way the two engines agree on what the sky looks
     * like; hand-authoring them separately guarantees drift.
     */
    private static final Shot[] RETRO_BANNERS = {
            new Shot("retro_skybox_castle", 0.42f, WeatherType.CLEAR, 0f, false, 0f, NORTH, 0f),
            new Shot("retro_skybox_east", 0.42f, WeatherType.CLEAR, 0f, false, 0f, EAST, 0f),
            new Shot("retro_skybox_south", 0.42f, WeatherType.CLEAR, 0f, false, 0f, SOUTH, 0f),
            new Shot("retro_skybox_west", 0.42f, WeatherType.CLEAR, 0f, false, 0f, WEST, 0f),
            new Shot("retro_skybox_castle_storm", 0.42f, WeatherType.STORM, 1f, true, 0f, NORTH, 0f),
            // MODERN render mode on the raycaster engine draws these instead; regenerated from
            // the same dome so the two modes cannot drift apart either.
            new Shot("skybox_castle", 0.42f, WeatherType.CLEAR, 0f, false, 0f, NORTH, 0f),
            new Shot("skybox_east", 0.42f, WeatherType.CLEAR, 0f, false, 0f, EAST, 0f),
            new Shot("skybox_south", 0.42f, WeatherType.CLEAR, 0f, false, 0f, SOUTH, 0f),
            new Shot("skybox_west", 0.42f, WeatherType.CLEAR, 0f, false, 0f, WEST, 0f),
    };

    private final String outputDir;
    private final boolean retroBanners;
    private Skybox3DRenderer skybox;
    private Viewport viewport;
    private DayNightManager dayNight;

    private int shotIndex = 0;
    private int frameInShot = 0;

    public SkyCaptureHarness(String outputDir) {
        this(outputDir, false);
    }

    public SkyCaptureHarness(String outputDir, boolean retroBanners) {
        this.outputDir = outputDir;
        this.retroBanners = retroBanners;
    }

    private Shot[] shots() {
        return retroBanners ? RETRO_BANNERS : SHOTS;
    }

    @Override
    public void create() {
        skybox = new Skybox3DRenderer();
        viewport = new FitViewport(WIDTH, HEIGHT);
        viewport.update(WIDTH, HEIGHT, true);
        dayNight = new DayNightManager(0.5f);

        if (!skybox.isInitialized()) {
            Gdx.app.error("SkyCaptureHarness", "Skybox failed to initialise; no captures written.");
            Gdx.app.exit();
        }
    }

    @Override
    public void render() {
        if (shotIndex >= shots().length) {
            Gdx.app.exit();
            return;
        }

        Shot shot = shots()[shotIndex];
        dayNight.setTimeOfDay(shot.timeOfDay);

        skybox.renderDirect(viewport, dayNight, shot.weather, shot.cloudCover,
                shot.stormy, shot.flash, shot.headingRadians, shot.northProgress, 1f / 60f);

        frameInShot++;
        if (frameInShot > WARMUP_FRAMES) {
            writeShot(shot.name);
            shotIndex++;
            frameInShot = 0;
        }
    }

    private void writeShot(String name) {
        Pixmap raw = Pixmap.createFromFrameBuffer(0, 0, WIDTH, HEIGHT);
        // The framebuffer is bottom-up; flip it so the PNG reads the way the player sees it.
        Pixmap flipped = new Pixmap(WIDTH, HEIGHT, raw.getFormat());
        for (int y = 0; y < HEIGHT; y++) {
            flipped.drawPixmap(raw, 0, y, 0, HEIGHT - 1 - y, WIDTH, 1);
        }
        raw.dispose();

        Pixmap output = flipped;
        if (retroBanners) {
            // Keep only the sky above the horizon and squash it into a banner: the raycaster
            // stretches this across the top of the view, so the ground half is never seen.
            int skyHeight = (int) (HEIGHT * BANNER_SKY_FRACTION);
            output = new Pixmap(BANNER_WIDTH, BANNER_HEIGHT, flipped.getFormat());
            output.setFilter(Pixmap.Filter.BiLinear);
            output.drawPixmap(flipped, 0, 0, WIDTH, skyHeight, 0, 0, BANNER_WIDTH, BANNER_HEIGHT);
            flipped.dispose();
        }

        FileHandle out = Gdx.files.local(outputDir + "/" + name + ".png");
        PixmapIO.writePNG(out, output);
        output.dispose();
        Gdx.app.log("SkyCaptureHarness", "Wrote " + out.path());
    }

    @Override
    public void dispose() {
        if (skybox != null) {
            skybox.dispose();
        }
    }
}
