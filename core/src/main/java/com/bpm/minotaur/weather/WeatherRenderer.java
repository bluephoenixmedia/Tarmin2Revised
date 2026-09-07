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
 * into camera space and occluded by dungeon geometry via vertical wall span depth buffer
 * checks, enabling realistic indoor-to-outdoor portal visibility, needle-thin rain streaks,
 * and delicate micro-splash droplets.
 */
public class WeatherRenderer {

    private static final String TAG = "WeatherRenderer";

    // Cylinder simulation bounds
    public static final float CYLINDER_RADIUS = 16.0f;
    public static final float CYLINDER_HEIGHT = 11.0f;

    private final WeatherManager weatherManager;
    private final Array<WeatherParticle> particles = new Array<>(false, 2500);
    private final Array<SplashDroplet> splashDroplets = new Array<>(false, 250);

    private final Vector3 windVector = new Vector3();
    private float spawnAccumulator = 0f;

    // Pre-calculated scratch colors to eliminate GC allocations
    private final Color rainColor = new Color(0.72f, 0.82f, 0.95f, 1f);
    private final Color snowColor = new Color(0.95f, 0.98f, 1.0f, 1f);
    private final Color splashColor = new Color(0.75f, 0.85f, 1.0f, 1f);

    public WeatherRenderer(WeatherManager weatherManager) {
        this.weatherManager = weatherManager;
    }

    /**
     * Updates 3D particles and micro-splash droplets centered around the player's world position.
     */
    public void update(float delta, Player player, Maze maze) {
        if (weatherManager == null || player == null) return;

        WeatherType type = weatherManager.getCurrentWeather();
        WeatherIntensity intensity = weatherManager.getCurrentIntensity();

        if (!weatherManager.isPrecipitation(type)) {
            if (particles.size > 0) particles.clear();
            if (splashDroplets.size > 0) splashDroplets.clear();
            return;
        }

        weatherManager.getWindVector(windVector);
        float playerX = player.getPosition().x;
        float playerY = player.getPosition().y;

        int maxParticles = getMaxParticles(type, intensity);

        // 1. Spawn new particles in the cylinder above outdoor tiles, biased in forward view
        spawnParticles(delta, type, intensity, player, maze, maxParticles);

        // 2. Update existing falling particles
        for (int i = particles.size - 1; i >= 0; i--) {
            WeatherParticle p = particles.get(i);
            p.update(delta);

            // Ground impact: spawn tiny splash droplets if hitting floor (z <= 0)
            if (p.z <= 0f) {
                p.isDead = true;
                if (p.type == WeatherType.RAIN || p.type == WeatherType.STORM) {
                    spawnSplash(p.x, p.y, playerX, playerY, maze);
                }
            }

            // Despawn if drifted beyond cylinder radius
            float dx = p.x - playerX;
            float dy = p.y - playerY;
            if (dx * dx + dy * dy > (CYLINDER_RADIUS + 3f) * (CYLINDER_RADIUS + 3f)) {
                p.isDead = true;
            }

            if (p.isDead) {
                particles.removeIndex(i);
            }
        }

        // 3. Update ground micro-splash droplets
        for (int i = splashDroplets.size - 1; i >= 0; i--) {
            SplashDroplet s = splashDroplets.get(i);
            s.update(delta);
            if (s.isDead) {
                splashDroplets.removeIndex(i);
            }
        }
    }

    private int getMaxParticles(WeatherType type, WeatherIntensity intensity) {
        if (type == WeatherType.BLIZZARD) return 2400;
        if (type == WeatherType.STORM) return 1800;
        if (type == WeatherType.SNOW) return (intensity == WeatherIntensity.HEAVY) ? 1400 : 800;
        return (intensity == WeatherIntensity.HEAVY) ? 1400 : (intensity == WeatherIntensity.MEDIUM) ? 950 : 550;
    }

    private void spawnParticles(float delta, WeatherType type, WeatherIntensity intensity,
                                Player player, Maze maze, int targetMax) {
        if (particles.size >= targetMax) return;

        float spawnRate = targetMax * 2.2f; // Particles per second
        spawnAccumulator += spawnRate * delta;

        int toSpawn = (int) spawnAccumulator;
        spawnAccumulator -= toSpawn;

        float playerX = player.getPosition().x;
        float playerY = player.getPosition().y;
        Vector2 pDir = player.getDirectionVector();
        float viewAngle = MathUtils.atan2(pDir.y, pDir.x);

        for (int i = 0; i < toSpawn && particles.size < targetMax; i++) {
            // Bias 70% of particles into the forward view frustum arc (+/- 65 deg)
            float angle;
            if (MathUtils.randomBoolean(0.70f)) {
                angle = viewAngle + MathUtils.random(-1.15f, 1.15f);
            } else {
                angle = MathUtils.random(0f, MathUtils.PI2);
            }

            float radius = (float) Math.sqrt(MathUtils.random()) * CYLINDER_RADIUS;
            float px = playerX + MathUtils.cos(angle) * radius;
            float py = playerY + MathUtils.sin(angle) * radius;

            // Never spawn precipitation inside roofed indoor tiles
            int tileX = MathUtils.floor(px);
            int tileY = MathUtils.floor(py);
            if (maze != null && maze.isIndoors(tileX, tileY)) {
                continue;
            }

            float pz = MathUtils.random(CYLINDER_HEIGHT * 0.35f, CYLINDER_HEIGHT);

            // Physical velocities
            float vx = windVector.x + MathUtils.random(-0.4f, 0.4f);
            float vy = windVector.z + MathUtils.random(-0.4f, 0.4f); // Maze Y is world Z
            float vz;
            float length;

            if (type == WeatherType.SNOW || type == WeatherType.BLIZZARD) {
                vz = (type == WeatherType.BLIZZARD) ? -MathUtils.random(6.0f, 9.5f) : -MathUtils.random(1.8f, 3.2f);
                length = (type == WeatherType.BLIZZARD) ? 0.30f : 0.12f;
            } else {
                // Rain / Storm
                vz = -MathUtils.random(15.0f, 19.5f);
                length = (type == WeatherType.STORM) ? 0.60f : 0.42f;
            }

            particles.add(new WeatherParticle(px, py, pz, vx, vy, vz, length, type));
        }
    }

    private void spawnSplash(float x, float y, float playerX, float playerY, Maze maze) {
        if (splashDroplets.size >= 240) return;

        // Only spawn splashes within visible distance
        float dx = x - playerX;
        float dy = y - playerY;
        if (dx * dx + dy * dy > 80f) return;

        int tileX = MathUtils.floor(x);
        int tileY = MathUtils.floor(y);
        if (maze != null && maze.isIndoors(tileX, tileY)) return;

        // Spawn 3 tiny spray droplets popping upward and outward from ground contact
        for (int i = 0; i < 3; i++) {
            float angle = MathUtils.random(0f, MathUtils.PI2);
            float speed = MathUtils.random(0.4f, 1.1f);
            float vx = MathUtils.cos(angle) * speed;
            float vy = MathUtils.sin(angle) * speed;
            float vz = MathUtils.random(1.2f, 2.2f);
            float maxLife = MathUtils.random(0.10f, 0.16f);
            splashDroplets.add(new SplashDroplet(x, y, vx, vy, vz, maxLife));
        }
    }

    /**
     * Renders precipitation and micro-splashes with camera projection and vertical wall occlusion.
     */
    public void renderPrecipitation(SpriteBatch spriteBatch, Texture blankTexture,
                                    Viewport viewport, float[] depthBuffer,
                                    Player player, Maze maze) {
        if (particles.size == 0 && splashDroplets.size == 0) return;
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

        // --- 1. RENDER GROUND MICRO-SPLASH DROPLETS ---
        for (int i = 0; i < splashDroplets.size; i++) {
            SplashDroplet s = splashDroplets.get(i);
            float relX = s.x - playerX;
            float relY = s.y - playerY;

            float transformX = invDet * (-dir.y * relX + dir.x * relY);
            float transformY = invDet * (-plane.y * relX + plane.x * relY);

            if (transformY <= 0.22f) continue;

            float screenX = halfW * (1.0f + transformX / transformY);
            int col = (int) ((screenX / worldW) * numCols);
            if (col < 0 || col >= numCols) continue;

            // Ground droplet depth occlusion: skip if behind wall
            float wallDist = depthBuffer[col];
            if (wallDist < Float.MAX_VALUE && transformY > wallDist) continue;

            float screenY = halfH + ((s.z - 0.5f) / transformY) * worldH;
            if (screenY < 0 || screenY > worldH) continue;

            // Tiny 1-to-2 pixel spray specks
            float size = MathUtils.clamp((0.022f / transformY) * worldH, 1.2f, 2.5f);
            float alpha = (1.0f - s.life / s.maxLife) * MathUtils.clamp(1.0f - transformY / 9f, 0.25f, 0.75f);

            splashColor.a = alpha;
            spriteBatch.setColor(splashColor);
            spriteBatch.draw(blankTexture, screenX - size * 0.5f, screenY - size * 0.5f, size, size);
        }

        // --- 2. RENDER 3D PRECIPITATION STREAKS / FLAKES ---
        for (int i = 0; i < particles.size; i++) {
            WeatherParticle p = particles.get(i);

            float relX = p.x - playerX;
            float relY = p.y - playerY;

            float transformX = invDet * (-dir.y * relX + dir.x * relY);
            float transformY = invDet * (-plane.y * relX + plane.x * relY);

            // Cull if behind camera plane or too close to lens
            if (transformY <= 0.18f) continue;

            float screenX = halfW * (1.0f + transformX / transformY);
            int col = (int) ((screenX / worldW) * numCols);
            if (col < 0 || col >= numCols) continue;

            // Screen altitude projection (eye height = 0.5)
            float screenY = halfH + ((p.z - 0.5f) / transformY) * worldH;
            if (screenY < -50 || screenY > worldH + 100) continue;

            // VERTICAL WALL OCCLUSION:
            // The wall only occupies the vertical span up to wallTopY.
            // Above wallTopY, it is OPEN SKY! Rain falling in the sky above walls is visible!
            float wallDist = depthBuffer[col];
            if (wallDist < Float.MAX_VALUE && transformY > wallDist) {
                float wallTopY = halfH + (0.5f / wallDist) * worldH;
                if (screenY <= wallTopY) {
                    continue; // Drop is behind the solid wall surface
                }
            }

            // Proximity alpha fade & atmospheric depth falloff
            float alpha = MathUtils.clamp(0.85f - (transformY / CYLINDER_RADIUS) * 0.55f, 0.20f, 0.75f);

            if (p.type == WeatherType.SNOW || p.type == WeatherType.BLIZZARD) {
                // SNOWFLAKE: Fluttering square quad
                float flakeSize = Math.max(1.2f, (0.07f / transformY) * worldH);
                snowColor.a = alpha * 0.85f;
                spriteBatch.setColor(snowColor);
                spriteBatch.draw(blankTexture, screenX - flakeSize * 0.5f, screenY - flakeSize * 0.5f, flakeSize, flakeSize);
            } else {
                // RAIN STREAK: Directional slender angled quad
                float streakLen = MathUtils.clamp((p.length / transformY) * worldH, 10f, 45f);
                float streakW   = MathUtils.clamp(1.2f + (0.4f / transformY), 1.0f, 1.6f);

                // Calculate visual slant angle from relative camera-space wind velocity
                float vCamX = invDet * (-dir.y * p.vx + dir.x * p.vy);
                float slantDeg = MathUtils.clamp((-vCamX / Math.abs(p.vz)) * 25f, -35f, 35f);

                rainColor.a = alpha;
                spriteBatch.setColor(rainColor);
                spriteBatch.draw(
                        blankTexture,
                        screenX - streakW * 0.5f,
                        screenY - streakLen,
                        streakW * 0.5f,
                        streakLen,
                        streakW,
                        streakLen,
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

    public static class SplashDroplet {
        public float x, y, z;
        public float vx, vy, vz;
        public float life;
        public float maxLife;
        public boolean isDead;

        public SplashDroplet(float x, float y, float vx, float vy, float vz, float maxLife) {
            this.x = x;
            this.y = y;
            this.z = 0.02f;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.life = 0f;
            this.maxLife = maxLife;
            this.isDead = false;
        }

        public void update(float delta) {
            life += delta;
            x += vx * delta;
            y += vy * delta;
            z += vz * delta;
            vz -= 14.0f * delta; // Quick downward gravity
            if (z < 0f) z = 0f;
            if (life >= maxLife) {
                isDead = true;
            }
        }
    }
}
