package com.bpm.minotaur.weather;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;

/**
 * 3D World-Space Precipitation & Surface Impact Renderer.
 *
 * <p>Simulates rain, snow, and blizzards as full 3D particles in a cylindrical
 * volume around the player in maze world coordinates. Particles are projected
 * into camera space and occluded by dungeon geometry via per-column raycast depth buffer
 * checks, enabling realistic indoor-to-outdoor portal visibility and ground splashes.
 */
public class WeatherRenderer {

    private static final String TAG = "WeatherRenderer";

    // Cylinder simulation bounds
    public static final float CYLINDER_RADIUS = 16.0f;
    public static final float CYLINDER_HEIGHT = 10.5f;

    private final WeatherManager weatherManager;
    private final Array<WeatherParticle> particles = new Array<>(false, 2500);
    private final Array<SplashParticle> splashes = new Array<>(false, 150);

    private final Vector3 windVector = new Vector3();
    private float spawnAccumulator = 0f;

    // Pre-calculated scratch colors to eliminate GC allocations
    private final Color rainColor = new Color(0.70f, 0.80f, 0.95f, 1f);
    private final Color snowColor = new Color(0.95f, 0.98f, 1.0f, 1f);
    private final Color splashColor = new Color(0.75f, 0.85f, 1.0f, 1f);

    public WeatherRenderer(WeatherManager weatherManager) {
        this.weatherManager = weatherManager;
    }

    /**
     * Updates 3D particles and ground splashes centered around the player's world position.
     */
    public void update(float delta, Player player, Maze maze) {
        if (weatherManager == null || player == null) return;

        WeatherType type = weatherManager.getCurrentWeather();
        WeatherIntensity intensity = weatherManager.getCurrentIntensity();

        if (!weatherManager.isPrecipitation(type)) {
            if (particles.size > 0) particles.clear();
            if (splashes.size > 0) splashes.clear();
            return;
        }

        weatherManager.getWindVector(windVector);
        float playerX = player.getPosition().x;
        float playerY = player.getPosition().y;

        int maxParticles = getMaxParticles(type, intensity);

        // 1. Spawn new particles in the cylinder above outdoor tiles
        spawnParticles(delta, type, intensity, playerX, playerY, maze, maxParticles);

        // 2. Update existing falling particles
        for (int i = particles.size - 1; i >= 0; i--) {
            WeatherParticle p = particles.get(i);
            p.update(delta);

            // Ground impact: spawn splash if hitting floor (z <= 0)
            if (p.z <= 0f) {
                p.isDead = true;
                if (p.type == WeatherType.RAIN || p.type == WeatherType.STORM) {
                    spawnSplash(p.x, p.y, playerX, playerY, maze);
                }
            }

            // Despawn if drifted beyond cylinder radius
            float dx = p.x - playerX;
            float dy = p.y - playerY;
            if (dx * dx + dy * dy > (CYLINDER_RADIUS + 2f) * (CYLINDER_RADIUS + 2f)) {
                p.isDead = true;
            }

            if (p.isDead) {
                particles.removeIndex(i);
            }
        }

        // 3. Update ground splashes
        for (int i = splashes.size - 1; i >= 0; i--) {
            SplashParticle s = splashes.get(i);
            s.update(delta);
            if (s.isDead) {
                splashes.removeIndex(i);
            }
        }
    }

    private int getMaxParticles(WeatherType type, WeatherIntensity intensity) {
        if (type == WeatherType.BLIZZARD) return 2400;
        if (type == WeatherType.STORM) return 1800;
        if (type == WeatherType.SNOW) return (intensity == WeatherIntensity.HEAVY) ? 1400 : 800;
        return (intensity == WeatherIntensity.HEAVY) ? 1400 : (intensity == WeatherIntensity.MEDIUM) ? 900 : 500;
    }

    private void spawnParticles(float delta, WeatherType type, WeatherIntensity intensity,
                                float playerX, float playerY, Maze maze, int targetMax) {
        if (particles.size >= targetMax) return;

        float spawnRate = targetMax * 1.8f; // Particles per second
        spawnAccumulator += spawnRate * delta;

        int toSpawn = (int) spawnAccumulator;
        spawnAccumulator -= toSpawn;

        for (int i = 0; i < toSpawn && particles.size < targetMax; i++) {
            // Uniform disk distribution
            float angle = MathUtils.random(0f, MathUtils.PI2);
            float radius = (float) Math.sqrt(MathUtils.random()) * CYLINDER_RADIUS;
            float px = playerX + MathUtils.cos(angle) * radius;
            float py = playerY + MathUtils.sin(angle) * radius;

            // Never spawn precipitation inside roofed indoor tiles
            int tileX = MathUtils.floor(px);
            int tileY = MathUtils.floor(py);
            if (maze != null && maze.isIndoors(tileX, tileY)) {
                continue;
            }

            float pz = MathUtils.random(CYLINDER_HEIGHT * 0.4f, CYLINDER_HEIGHT);

            // Physical velocities
            float vx = windVector.x + MathUtils.random(-0.5f, 0.5f);
            float vy = windVector.z + MathUtils.random(-0.5f, 0.5f); // Maze Y is world Z
            float vz;
            float length;

            if (type == WeatherType.SNOW || type == WeatherType.BLIZZARD) {
                vz = (type == WeatherType.BLIZZARD) ? -MathUtils.random(6.0f, 9.5f) : -MathUtils.random(1.8f, 3.2f);
                length = (type == WeatherType.BLIZZARD) ? 0.35f : 0.12f;
            } else {
                // Rain / Storm
                vz = -MathUtils.random(14.0f, 18.5f);
                length = (type == WeatherType.STORM) ? 0.65f : 0.45f;
            }

            particles.add(new WeatherParticle(px, py, pz, vx, vy, vz, length, type));
        }
    }

    private void spawnSplash(float x, float y, float playerX, float playerY, Maze maze) {
        if (splashes.size >= 120) return;

        // Only spawn splashes in outdoor tiles within reasonable viewing radius
        float dx = x - playerX;
        float dy = y - playerY;
        if (dx * dx + dy * dy > 100f) return;

        int tileX = MathUtils.floor(x);
        int tileY = MathUtils.floor(y);
        if (maze != null && maze.isIndoors(tileX, tileY)) return;

        splashes.add(new SplashParticle(x, y));
    }

    /**
     * Renders precipitation and ground splashes with camera-space projection and depth occlusion.
     */
    public void renderPrecipitation(SpriteBatch spriteBatch, Texture blankTexture,
                                    Viewport viewport, float[] depthBuffer,
                                    Player player, Maze maze) {
        if (particles.size == 0 && splashes.size == 0) return;
        if (player == null || depthBuffer == null) return;

        float playerX = player.getPosition().x;
        float playerY = player.getPosition().y;
        Vector2 dir = player.getDirectionVector();
        Vector2 plane = player.getCameraPlane();

        float invDet = 1.0f / (plane.x * dir.y - dir.x * plane.y);
        float worldW = viewport.getWorldWidth();
        float worldH = viewport.getWorldHeight();
        float halfW  = worldW * 0.5f;
        float halfH  = worldH * 0.5f;
        int numCols  = depthBuffer.length;

        // --- 1. RENDER GROUND SPLASH RINGS ---
        for (int i = 0; i < splashes.size; i++) {
            SplashParticle s = splashes.get(i);
            float relX = s.x - playerX;
            float relY = s.y - playerY;

            float transformX = invDet * (dir.y * relX - dir.x * relY);
            float transformY = invDet * (-plane.y * relX + plane.x * relY);

            if (transformY <= 0.25f) continue;

            float screenX = halfW * (1.0f + transformX / transformY);
            int col = (int) screenX;
            if (col < 0 || col >= numCols) continue;

            // Depth occlusion: skip if splash is behind wall/door
            if (transformY > depthBuffer[col]) continue;

            // Floor altitude is at 0 (eye height is 0.5)
            float screenY = halfH - (0.5f / transformY) * worldH;
            if (screenY < 0 || screenY > worldH) continue;

            float ringW = (s.radius / transformY) * worldW;
            float ringH = ringW * 0.35f; // Flat ellipse on the floor plane
            float alpha = (1.0f - s.life / s.maxLife) * MathUtils.clamp(1.0f - transformY / 10f, 0.2f, 0.65f);

            splashColor.a = alpha;
            spriteBatch.setColor(splashColor);
            spriteBatch.draw(blankTexture, screenX - ringW * 0.5f, screenY - ringH * 0.5f, ringW, ringH);
        }

        // --- 2. RENDER 3D PRECIPITATION STREAKS / FLAKES ---
        for (int i = 0; i < particles.size; i++) {
            WeatherParticle p = particles.get(i);

            float relX = p.x - playerX;
            float relY = p.y - playerY;

            float transformX = invDet * (dir.y * relX - dir.x * relY);
            float transformY = invDet * (-plane.y * relX + plane.x * relY);

            // Cull if behind camera plane or too close to lens
            if (transformY <= 0.18f) continue;

            float screenX = halfW * (1.0f + transformX / transformY);
            int col = (int) screenX;
            if (col < 0 || col >= numCols) continue;

            // Depth occlusion against dungeon raycast walls & monsters
            if (transformY > depthBuffer[col]) continue;

            // Screen altitude projection (eye height = 0.5)
            float screenY = halfH + ((p.z - 0.5f) / transformY) * worldH;
            if (screenY < -50 || screenY > worldH + 80) continue;

            // Proximity alpha fade & atmospheric depth falloff
            float alpha = MathUtils.clamp(1.0f - (transformY / CYLINDER_RADIUS), 0.12f, 0.75f);

            if (p.type == WeatherType.SNOW || p.type == WeatherType.BLIZZARD) {
                // SNOWFLAKE: Fluttering square quad
                float flakeSize = Math.max(1.5f, (0.09f / transformY) * worldH);
                snowColor.a = alpha * 0.85f;
                spriteBatch.setColor(snowColor);
                spriteBatch.draw(blankTexture, screenX - flakeSize * 0.5f, screenY - flakeSize * 0.5f, flakeSize, flakeSize);
            } else {
                // RAIN STREAK: Directional angled quad
                float streakLen = MathUtils.clamp((p.length / transformY) * worldH, 4f, 100f);
                float streakW   = MathUtils.clamp((0.035f / transformY) * worldW, 1.2f, 3.2f);

                // Calculate visual slant angle from relative camera-space wind velocity
                float vCamX = invDet * (dir.y * p.vx - dir.x * p.vy);
                float slantDeg = MathUtils.clamp((vCamX / Math.abs(p.vz)) * 28f, -40f, 40f);

                rainColor.a = alpha;
                spriteBatch.setColor(rainColor);
                spriteBatch.draw(
                        blankTexture,
                        screenX, screenY - streakLen,
                        0f, 0f,
                        streakW, streakLen,
                        1f, 1f,
                        slantDeg,
                        0, 0, 1, 1,
                        false, false
                );
            }
        }

        spriteBatch.setColor(Color.WHITE);
    }

    // -------------------------------------------------------------------------
    // INTERNAL PARTICLE CLASSES
    // -------------------------------------------------------------------------

    public static class WeatherParticle {
        public float x, y, z;
        public float vx, vy, vz;
        public float length;
        public WeatherType type;
        public float wobble;
        public float wobbleSpeed;
        public boolean isDead = false;

        public WeatherParticle(float x, float y, float z, float vx, float vy, float vz,
                               float length, WeatherType type) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.length = length;
            this.type = type;
            this.wobble = MathUtils.random(0f, MathUtils.PI2);
            this.wobbleSpeed = MathUtils.random(2.5f, 6.0f);
        }

        public void update(float delta) {
            if (type == WeatherType.SNOW || type == WeatherType.BLIZZARD) {
                wobble += wobbleSpeed * delta;
                float drift = MathUtils.sin(wobble) * 0.4f;
                x += (vx + drift) * delta;
                y += vy * delta;
            } else {
                x += vx * delta;
                y += vy * delta;
            }
            z += vz * delta;
        }
    }

    public static class SplashParticle {
        public float x, y;
        public float radius = 0.05f;
        public float life = 0f;
        public float maxLife = 0.22f;
        public boolean isDead = false;

        public SplashParticle(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public void update(float delta) {
            life += delta;
            radius = MathUtils.lerp(0.05f, 0.30f, life / maxLife);
            if (life >= maxLife) {
                isDead = true;
            }
        }
    }
}
