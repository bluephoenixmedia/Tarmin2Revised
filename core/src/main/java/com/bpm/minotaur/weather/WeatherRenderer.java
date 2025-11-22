package com.bpm.minotaur.weather;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.Iterator;

public class WeatherRenderer {

    private final Array<WeatherParticle> particles = new Array<>();
    private final WeatherManager weatherManager;

    // --- CONFIGURATION ---
    private static final int MAX_PARTICLES = 1500; // Increased count for denser storms
    private static final float SPAWN_TIMER_RESET = 0.004f; // Faster spawn rate
    private static final float VELOCITY_MULTIPLIER = 1.5f; // [NEW] Global speed boost

    private float spawnTimer = 0;

    // Tornado State
    private float tornadoX = 0;
    private float tornadoSpeed = 30f;

    // --- RETRO SPRITE DATA ---
    public static final String[] TORNADO_SPRITE = new String[]{
        "########################",
        "########################",
        "########################",
        ".######################.",
        "..####################..",
        "...##################...",
        "....###############.....",
        ".....#############......",
        "......###########.......",
        ".......#########........",
        "........#######.........",
        "........#######.........",
        "........#######.........",
        ".......#######..........",
        "......#######...........",
        ".....#######............",
        "....#######.............",
        "...#######..............",
        "..#######...............",
        "..#######...............",
        "....#####...............",
        ".....####...............",
        "......###................",
        ".......##................"
    };

    public WeatherRenderer(WeatherManager weatherManager) {
        this.weatherManager = weatherManager;
        this.tornadoX = Gdx.graphics.getWidth() / 2f;
    }

    public void update(float delta) {
        WeatherType type = weatherManager.getCurrentWeather();
        WeatherIntensity intensity = weatherManager.getCurrentIntensity();

        // 1. Tornado Movement
        if (type == WeatherType.TORNADO) {
            tornadoX += tornadoSpeed * delta;
            if (tornadoX < 0 || tornadoX > Gdx.graphics.getWidth() - 200) {
                tornadoSpeed = -tornadoSpeed;
            }
        }

        // 2. Spawn new particles
        if (isPrecipitation(type)) {
            spawnTimer -= delta;
            while (spawnTimer <= 0) {
                spawnParticle(type, intensity);

                // Reset timer based on intensity
                float divisor = 1.0f;
                if (intensity == WeatherIntensity.MEDIUM) divisor = 2.0f;
                if (intensity == WeatherIntensity.HEAVY) divisor = 5.0f;
                if (intensity == WeatherIntensity.EXTREME) divisor = 15.0f;

                spawnTimer += (SPAWN_TIMER_RESET / divisor);
            }
        } else {
            if (particles.size > 0) particles.clear();
        }

        // 3. Update existing particles
        Iterator<WeatherParticle> iter = particles.iterator();
        while (iter.hasNext()) {
            WeatherParticle p = iter.next();
            p.update(delta);
            if (p.isDead) {
                iter.remove();
            }
        }
    }

    private boolean isPrecipitation(WeatherType type) {
        return type == WeatherType.RAIN || type == WeatherType.STORM ||
            type == WeatherType.SNOW || type == WeatherType.BLIZZARD ||
            type == WeatherType.TORNADO;
    }

    private void spawnParticle(WeatherType type, WeatherIntensity intensity) {
        if (particles.size >= MAX_PARTICLES) return;

        float x = MathUtils.random(0, Gdx.graphics.getWidth());
        float y = Gdx.graphics.getHeight() + 20; // Start well above screen

        // Base Speeds
        float speedBase = (type == WeatherType.SNOW || type == WeatherType.BLIZZARD) ? 150f : 800f;

        // Apply Global Multiplier
        speedBase *= VELOCITY_MULTIPLIER;

        float speedMod = MathUtils.random(0.8f, 1.5f); // More variance

        // Wind simulation
        float windX = 0;
        if (type == WeatherType.STORM || type == WeatherType.BLIZZARD) windX = -200f;
        if (intensity == WeatherIntensity.EXTREME) windX = -500f;
        if (type == WeatherType.TORNADO) windX = -1500f;

        particles.add(new WeatherParticle(x, y, windX, -speedBase * speedMod, type));
    }

    /**
     * Renders the Tornado sprite. Should be called BEFORE walls are drawn.
     */
    public void renderTornado(ShapeRenderer shapeRenderer, Viewport viewport) {
        if (weatherManager.getCurrentWeather() != WeatherType.TORNADO) return;

        Gdx.gl.glEnable(Gdx.gl.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        renderTornadoSprite(shapeRenderer, viewport);
        shapeRenderer.end();
    }

    /**
     * Renders rain/snow particles. Should be called AFTER walls are drawn.
     */
    public void renderPrecipitation(ShapeRenderer shapeRenderer, Viewport viewport) {
        if (particles.size == 0) return;

        Gdx.gl.glEnable(Gdx.gl.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (WeatherParticle p : particles) {
            if (p.type == WeatherType.SNOW || p.type == WeatherType.BLIZZARD) {
                // Snow (White)
                shapeRenderer.setColor(1f, 1f, 1f, p.lifeRatio);
                shapeRenderer.rect(p.x, p.y, 3, 3);
            } else {
                // Rain (Blue-Grey)
                shapeRenderer.setColor(0.6f, 0.7f, 0.85f, 0.7f);
                float lean = p.velocityX * 0.03f;
                // Longer rain streaks
                shapeRenderer.rectLine(p.x, p.y, p.x + lean, p.y - 25, 1.5f);
            }
        }

        shapeRenderer.end();
    }

    private void renderTornadoSprite(ShapeRenderer shapeRenderer, Viewport viewport) {
        float pixelSize = 8.0f;
        float startY = (viewport.getWorldHeight() / 2); // Horizon

        // Color: Dark Ominous Gray
        shapeRenderer.setColor(0.2f, 0.18f, 0.15f, 1.0f);

        for (int r = 0; r < TORNADO_SPRITE.length; r++) {
            String row = TORNADO_SPRITE[r];
            for (int c = 0; c < row.length(); c++) {
                if (row.charAt(c) == '#') {
                    float y = startY + ((TORNADO_SPRITE.length - 1 - r) * pixelSize);
                    float x = tornadoX + (c * pixelSize);
                    shapeRenderer.rect(x, y, pixelSize, pixelSize);
                }
            }
        }
    }

    // Internal Particle Class
    private static class WeatherParticle {
        float x, y;
        float velocityX, velocityY;
        WeatherType type;
        float maxLife; // Calculated dynamically
        float currentLife = 0f;
        boolean isDead = false;
        float lifeRatio = 1.0f;

        public WeatherParticle(float x, float y, float vx, float vy, WeatherType type) {
            this.x = x;
            this.y = y;
            this.velocityX = vx;
            this.velocityY = vy;
            this.type = type;

            // [FIX] Calculate maxLife based on screen height so it reaches the bottom
            // Time = Distance / Speed. We add buffer to ensure it clears the screen.
            float timeToFall = (y + 50) / Math.abs(vy);
            this.maxLife = timeToFall + 1.0f; // Add 1 second buffer
        }

        public void update(float delta) {
            x += velocityX * delta;
            y += velocityY * delta;
            currentLife += delta;

            // Screen Wrap X
            if (x < -50) x = Gdx.graphics.getWidth() + 50;
            if (x > Gdx.graphics.getWidth() + 50) x = -50;

            // Death condition
            if (y < -20 || currentLife > maxLife) {
                isDead = true;
            }

            // [FIX] Only fade out in the very last 10% of life, or if snow, maybe not at all?
            // Let's keep a slight fade but ensure it's visible at bottom.
            lifeRatio = 1.0f;
            if (currentLife > maxLife - 0.5f) {
                lifeRatio = (maxLife - currentLife) / 0.5f;
            }
        }
    }
}
