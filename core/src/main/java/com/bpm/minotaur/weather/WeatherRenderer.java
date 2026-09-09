package com.bpm.minotaur.weather;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.rendering.mesh.DynamicQuadBatcher;

/**
 * 3D World-Space Precipitation & Surface Impact Renderer.
 *
 * <p>Simulates rain, snow, and blizzards as full 3D particles in a cylindrical
 * volume around the player in maze world coordinates. Supports both true 3D OpenGL
 * pipeline rendering (with hardware depth testing and velocity-aligned streaks)
 * and legacy 2.5D raycaster projection.
 */
public class WeatherRenderer {

    private static final String TAG = "WeatherRenderer";

    // Cylinder simulation bounds
    public static final float CYLINDER_RADIUS = 13.0f;
    public static final float CYLINDER_HEIGHT = 11.0f;

    private final WeatherManager weatherManager;
    private final Array<WeatherParticle> particles = new Array<>(false, 2500);
    private final Array<SplashDroplet> splashDroplets = new Array<>(false, 250);

    private final Vector3 windVector = new Vector3();

    // Pre-calculated scratch colors to eliminate GC allocations
    private final Color rainColor = new Color(0.72f, 0.82f, 0.95f, 1f);
    private final Color snowColor = new Color(0.95f, 0.98f, 1.0f, 1f);
    private final Color splashColor = new Color(0.75f, 0.85f, 1.0f, 1f);

    // 3D rendering scratch vectors and colors
    private final Vector3 scratchCamRight = new Vector3();
    private final Vector3 scratchCamUp = new Vector3();
    private final Color retroCyan = new Color(0.0f, 0.90f, 1.0f, 1.0f);
    private final Color modernRainStreak = new Color(0.78f, 0.88f, 1.0f, 0.65f);
    private final Color modernStormStreak = new Color(0.85f, 0.93f, 1.0f, 0.72f);
    private final Color modernSnowCrystalline = new Color(0.98f, 0.99f, 1.0f, 0.92f);
    private final Color modernSnowFluffy = new Color(0.92f, 0.95f, 1.0f, 0.78f);
    private final Color modernBlizzardStreak = new Color(0.96f, 0.98f, 1.0f, 0.88f);
    private final Color tornadoDebrisColor = new Color(0.28f, 0.24f, 0.18f, 0.85f);
    private final Color modernSplash = new Color(0.80f, 0.90f, 1.0f, 0.70f);

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
        Vector2 pDir = player.getDirectionVector();
        float viewAngle = MathUtils.atan2(pDir.y, pDir.x);

        int maxParticles = getMaxParticles(type, intensity);

        // 1. Maintain target particle count with instant fill if empty
        int toSpawn = maxParticles - particles.size;
        if (toSpawn > 0) {
            int batchSpawn = (particles.size == 0) ? toSpawn : Math.min(toSpawn, 140);
            for (int i = 0; i < batchSpawn; i++) {
                WeatherParticle p = new WeatherParticle(0, 0, 0, 0, 0, 0, 0, type, false, false);
                initParticle(p, playerX, playerY, viewAngle, maze, type, particles.size == 0);
                if (!p.isDead) {
                    particles.add(p);
                }
            }
        }

        // 2. Update existing falling particles and recycle in-place
        for (int i = particles.size - 1; i >= 0; i--) {
            WeatherParticle p = particles.get(i);
            p.update(delta);

            // Ground impact: spawn tiny splash droplets if hitting floor (z <= 0)
            if (p.z <= 0f) {
                if (p.type == WeatherType.RAIN || p.type == WeatherType.STORM) {
                    spawnSplash(p.x, p.y, playerX, playerY, maze);
                }
                // Recycle drop immediately to maintain dense continuous downpour
                initParticle(p, playerX, playerY, viewAngle, maze, type, false);
                if (p.isDead) {
                    particles.removeIndex(i);
                }
                continue;
            }

            // Recycle if drifted beyond cylinder radius
            float dx = p.x - playerX;
            float dy = p.y - playerY;
            if (dx * dx + dy * dy > (CYLINDER_RADIUS + 2.5f) * (CYLINDER_RADIUS + 2.5f)) {
                initParticle(p, playerX, playerY, viewAngle, maze, type, false);
                if (p.isDead) {
                    particles.removeIndex(i);
                }
                continue;
            }

            // Check if particle drifted into an indoor roofed tile
            int curTileX = MathUtils.floor(p.x);
            int curTileY = MathUtils.floor(p.y);
            if (maze != null && maze.isIndoors(curTileX, curTileY)) {
                initParticle(p, playerX, playerY, viewAngle, maze, type, false);
                if (p.isDead) {
                    particles.removeIndex(i);
                }
                continue;
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
        if (!weatherManager.isPrecipitation(type)) return 0;
        switch (type) {
            case BLIZZARD:
                switch (intensity) {
                    case LIGHT: return 200;
                    case MEDIUM: return 260;
                    case HEAVY: return 320;
                    case EXTREME: default: return 380;
                }
            case STORM:
                switch (intensity) {
                    case LIGHT: return 180;
                    case MEDIUM: return 240;
                    case HEAVY: return 300;
                    case EXTREME: default: return 360;
                }
            case SNOW:
                switch (intensity) {
                    case LIGHT: return 70;
                    case MEDIUM: return 130;
                    case HEAVY: return 200;
                    case EXTREME: default: return 280;
                }
            case TORNADO:
                return 260;
            case RAIN:
            default:
                switch (intensity) {
                    case LIGHT: return 75;
                    case MEDIUM: return 140;
                    case HEAVY: return 220;
                    case EXTREME: default: return 300;
                }
        }
    }

    /**
     * Initializes or recycles a particle with tiered near/mid/far distance distribution
     * and frustum-aligned altitude so precipitation fills the screen uniformly from top to bottom
     * across the entire player view, including the immediate foreground.
     */
    private void initParticle(WeatherParticle p, float playerX, float playerY, float viewAngle,
                              Maze maze, WeatherType type, boolean initialScatter) {
        for (int attempt = 0; attempt < 5; attempt++) {
            // Uniform visual distribution across player's field of view:
            // Counteracts foreshortening and shorter travel time of near particles by weighting near/mid spawns
            float tierRoll = MathUtils.random();
            float radius;
            if (tierRoll < 0.36f) {
                // Immediate Foreground: 0.4m - 1.8m (directly in front of player's face and corridor)
                radius = MathUtils.random(0.4f, 1.8f);
            } else if (tierRoll < 0.68f) {
                // Mid-Near: 1.8m - 4.2m (corridor ahead, doorways, immediate outdoor spaces)
                radius = MathUtils.random(1.8f, 4.2f);
            } else if (tierRoll < 0.86f) {
                // Mid-Far: 4.2m - 7.5m (courtyard, open grounds)
                radius = MathUtils.random(4.2f, 7.5f);
            } else {
                // Far: 7.5m - 12.0m (distant landscape, castle silhouette, sky)
                radius = MathUtils.random(7.5f, CYLINDER_RADIUS);
            }

            // Bias 92% across player's horizontal view cone (+/- 0.85 rad, ~49 deg matching camera FOV)
            float angle;
            if (MathUtils.randomBoolean(0.92f)) {
                angle = viewAngle + MathUtils.random(-0.85f, 0.85f);
            } else {
                angle = MathUtils.random(0f, MathUtils.PI2);
            }

            float px = playerX + MathUtils.cos(angle) * radius;
            float py = playerY + MathUtils.sin(angle) * radius;

            // Never spawn inside roofed indoor tiles
            int tileX = MathUtils.floor(px);
            int tileY = MathUtils.floor(py);
            if (maze != null && maze.isIndoors(tileX, tileY)) {
                continue;
            }

            // Frustum-aligned altitude with adequate height so near particles fall smoothly through the view
            float zTop = 0.5f + 0.55f * radius;
            float pz;
            if (initialScatter) {
                pz = MathUtils.random(0.05f, Math.max(zTop + 0.5f, 3.5f));
            } else {
                // Spawn above frustum top, with a minimum height (2.4m) so near particles don't instantly vanish
                pz = Math.max(zTop + MathUtils.random(0.15f, 0.75f), MathUtils.random(2.4f, 4.0f));
            }

            // Physical velocities
            float vx = windVector.x + MathUtils.random(-0.4f, 0.4f);
            float vy = windVector.z + MathUtils.random(-0.4f, 0.4f);
            float vz;
            float length;
            boolean isFluffy = (type == WeatherType.SNOW) && (MathUtils.random() < 0.32f);
            boolean isDebris = (type == WeatherType.TORNADO) && (MathUtils.random() < 0.45f);

            if (type == WeatherType.SNOW) {
                // Gentle fluttering descent
                vz = isFluffy ? -MathUtils.random(1.2f, 1.8f) : -MathUtils.random(1.8f, 2.6f);
                length = isFluffy ? 0.024f : 0.012f;
            } else if (type == WeatherType.BLIZZARD) {
                // High-velocity driving squall
                vz = -MathUtils.random(5.5f, 9.0f);
                length = MathUtils.random(0.16f, 0.26f);
            } else if (type == WeatherType.TORNADO) {
                if (isDebris) {
                    vz = MathUtils.random(-3.0f, 1.5f);
                    length = MathUtils.random(0.020f, 0.038f);
                } else {
                    vz = -MathUtils.random(12.0f, 16.0f);
                    length = 0.25f;
                }
            } else {
                vz = -MathUtils.random(14.0f, 18.0f);
                length = (type == WeatherType.STORM) ? 0.28f : 0.18f;
            }

            p.reset(px, py, pz, vx, vy, vz, length, type, isFluffy, isDebris);
            return;
        }
        p.isDead = true;
    }

    private void spawnSplash(float x, float y, float playerX, float playerY, Maze maze) {
        if (splashDroplets.size >= 96) return;

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
     * Renders precipitation and ground micro-splashes in true 3D world space
     * using the DynamicQuadBatcher and camera orientation.
     */
    public void render3D(
            DynamicQuadBatcher batcher,
            Texture blankTexture,
            ShaderProgram shader,
            Camera camera,
            Player player,
            Maze maze,
            WeatherManager wm,
            boolean isRetro
    ) {
        if (particles.size == 0 && splashDroplets.size == 0) return;
        if (batcher == null || camera == null || player == null || wm == null) return;

        WeatherType type = wm.getCurrentWeather();
        Vector3 camPos = camera.position;
        Vector3 camDir = camera.direction;
        Vector3 camUp = camera.up;

        // Calculate Right unit vector for billboards
        scratchCamRight.set(camDir).crs(camUp).nor();

        // Shading parameters
        Color streakColor;
        Color splashCol;
        float streakHalfWidth;

        if (isRetro) {
            streakColor = (type == WeatherType.SNOW || type == WeatherType.BLIZZARD)
                    ? Color.WHITE
                    : retroCyan;
            splashCol = streakColor;
            streakHalfWidth = 0.0018f; // Crisp retro pixel streak (~3.6mm wide)
        } else {
            if (type == WeatherType.SNOW) {
                streakColor = modernSnowCrystalline;
                splashCol = modernSnowCrystalline;
                streakHalfWidth = 0.010f;
            } else if (type == WeatherType.BLIZZARD) {
                streakColor = modernBlizzardStreak;
                splashCol = modernBlizzardStreak;
                streakHalfWidth = 0.0022f; // Driving white streak (~4.4mm wide)
            } else if (type == WeatherType.STORM) {
                streakColor = modernStormStreak;
                splashCol = modernSplash;
                streakHalfWidth = 0.0020f;
            } else if (type == WeatherType.TORNADO) {
                streakColor = modernStormStreak;
                splashCol = modernSplash;
                streakHalfWidth = 0.0022f;
            } else {
                streakColor = modernRainStreak;
                splashCol = modernSplash;
                streakHalfWidth = 0.0016f;
            }
        }

        // --- 1. RENDER GROUND MICRO-SPLASHES (Outdoors Only) ---
        float playerX = player.getPosition().x;
        float playerY = player.getPosition().y;

        for (int i = 0; i < splashDroplets.size; i++) {
            SplashDroplet s = splashDroplets.get(i);
            if (s.isDead) continue;

            int tileX = MathUtils.floor(s.x);
            int tileY = MathUtils.floor(s.y);
            if (maze != null && maze.isIndoors(tileX, tileY)) continue;

            float dx = s.x - playerX;
            float dy = s.y - playerY;
            if (dx * dx + dy * dy > 144f) continue; // within 12 tiles

            float progress = s.life / s.maxLife;
            float halfS = Math.max(0.003f, 0.012f * (1.0f - progress));

            // World coords: X = s.x, Y = s.z, Z = -s.y
            float worldX = s.x;
            float worldY = Math.max(0.01f, s.z);
            float worldZ = -s.y;

            // Camera-facing billboard quad
            batcher.addParticleQuad(
                    worldX - scratchCamRight.x * halfS - camUp.x * halfS,
                    worldY - scratchCamRight.y * halfS - camUp.y * halfS,
                    worldZ - scratchCamRight.z * halfS - camUp.z * halfS,

                    worldX + scratchCamRight.x * halfS - camUp.x * halfS,
                    worldY + scratchCamRight.y * halfS - camUp.y * halfS,
                    worldZ + scratchCamRight.z * halfS - camUp.z * halfS,

                    worldX + scratchCamRight.x * halfS + camUp.x * halfS,
                    worldY + scratchCamRight.y * halfS + camUp.y * halfS,
                    worldZ + scratchCamRight.z * halfS + camUp.z * halfS,

                    worldX - scratchCamRight.x * halfS + camUp.x * halfS,
                    worldY - scratchCamRight.y * halfS + camUp.y * halfS,
                    worldZ - scratchCamRight.z * halfS + camUp.z * halfS,

                    -camDir.x, -camDir.y, -camDir.z,
                    splashCol
            );
        }

        // --- 2. RENDER PRECIPITATION PARTICLES ---
        for (int i = 0; i < particles.size; i++) {
            WeatherParticle p = particles.get(i);
            if (p.isDead) continue;

            // World coords: X = p.x, Y = p.z, Z = -p.y
            float worldX = p.x;
            float worldY = p.z;
            float worldZ = -p.y;

            float dx = worldX - camPos.x;
            float dy = worldY - camPos.y;
            float dz = worldZ - camPos.z;
            float distSq = dx * dx + dy * dy + dz * dz;

            // Distance scaling compensation so near particles remain needle-crisp without perspective bloating
            float scale = 1.0f;
            if (distSq < 2.25f) { // Within 1.5m of camera
                float dist = (float) Math.sqrt(distSq);
                scale = Math.max(0.40f, dist / 1.5f);
            }

            if (p.type == WeatherType.SNOW) {
                // Square snowflake billboard quad with dual-layer variety
                float baseSize = p.isFluffy ? 0.016f : 0.008f;
                float halfS = baseSize * scale;
                Color flakeColor = isRetro ? Color.WHITE : (p.isFluffy ? modernSnowFluffy : modernSnowCrystalline);
                batcher.addParticleQuad(
                        worldX - scratchCamRight.x * halfS - camUp.x * halfS,
                        worldY - scratchCamRight.y * halfS - camUp.y * halfS,
                        worldZ - scratchCamRight.z * halfS - camUp.z * halfS,

                        worldX + scratchCamRight.x * halfS - camUp.x * halfS,
                        worldY + scratchCamRight.y * halfS - camUp.y * halfS,
                        worldZ + scratchCamRight.z * halfS - camUp.z * halfS,

                        worldX + scratchCamRight.x * halfS + camUp.x * halfS,
                        worldY + scratchCamRight.y * halfS + camUp.y * halfS,
                        worldZ + scratchCamRight.z * halfS + camUp.z * halfS,

                        worldX - scratchCamRight.x * halfS + camUp.x * halfS,
                        worldY - scratchCamRight.y * halfS + camUp.y * halfS,
                        worldZ - scratchCamRight.z * halfS + camUp.z * halfS,

                        -camDir.x, -camDir.y, -camDir.z,
                        flakeColor
                );
            } else if (p.type == WeatherType.TORNADO && p.isDebris) {
                // Flying debris quad
                float halfS = 0.014f * scale;
                Color debrisCol = isRetro ? Color.GRAY : tornadoDebrisColor;
                batcher.addParticleQuad(
                        worldX - scratchCamRight.x * halfS - camUp.x * halfS,
                        worldY - scratchCamRight.y * halfS - camUp.y * halfS,
                        worldZ - scratchCamRight.z * halfS - camUp.z * halfS,

                        worldX + scratchCamRight.x * halfS - camUp.x * halfS,
                        worldY + scratchCamRight.y * halfS - camUp.y * halfS,
                        worldZ + scratchCamRight.z * halfS - camUp.z * halfS,

                        worldX + scratchCamRight.x * halfS + camUp.x * halfS,
                        worldY + scratchCamRight.y * halfS + camUp.y * halfS,
                        worldZ + scratchCamRight.z * halfS + camUp.z * halfS,

                        worldX - scratchCamRight.x * halfS + camUp.x * halfS,
                        worldY - scratchCamRight.y * halfS + camUp.y * halfS,
                        worldZ - scratchCamRight.z * halfS + camUp.z * halfS,

                        -camDir.x, -camDir.y, -camDir.z,
                        debrisCol
                );
            } else {
                // Driving streak for Rain, Storm, Blizzard, or Tornado mist
                float vx = p.vx;
                float vy = p.vz; // vertical downward velocity
                float vz = -p.vy; // maze Y velocity -> world -Z

                float speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (speed < 1e-4f) continue;

                float len = p.length * Math.max(0.60f, scale);
                float invSpeed = 1.0f / speed;
                float dirX = vx * invSpeed;
                float dirY = vy * invSpeed;
                float dirZ = vz * invSpeed;

                float tailX = worldX - dirX * len;
                float tailY = worldY - dirY * len;
                float tailZ = worldZ - dirZ * len;

                float effectiveHalfWidth = streakHalfWidth * scale;

                batcher.addRainStreak(
                        worldX, worldY, worldZ,
                        tailX, tailY, tailZ,
                        effectiveHalfWidth,
                        camPos,
                        camUp,
                        streakColor
                );
            }
        }

        // Flush precipitation buffer
        batcher.flush(shader, blankTexture);
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

        boolean playerIndoors = (maze != null && maze.isIndoors((int) playerX, (int) playerY));

        // --- 1. RENDER GROUND MICRO-SPLASH DROPLETS ---
        for (int i = 0; i < splashDroplets.size; i++) {
            SplashDroplet s = splashDroplets.get(i);
            if (maze != null && maze.isIndoors(MathUtils.floor(s.x), MathUtils.floor(s.y))) continue;

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
            if (maze != null && maze.isIndoors(MathUtils.floor(p.x), MathUtils.floor(p.y))) continue;

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

            // VERTICAL WALL & CEILING OCCLUSION:
            // When indoors under a roof, walls meet the ceiling and block drops behind them.
            // When outdoors, open sky exists above wallTopY so rain falling in the sky above walls is visible.
            float wallDist = depthBuffer[col];
            if (wallDist < Float.MAX_VALUE && transformY > wallDist) {
                if (playerIndoors) {
                    continue; // Drop is behind indoor wall surface / under roof
                }
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
        public boolean isFluffy;
        public boolean isDebris;
        public float wobble;
        public float wobbleSpeed;
        public boolean isDead = false;

        public WeatherParticle(float x, float y, float z, float vx, float vy, float vz,
                               float length, WeatherType type) {
            this(x, y, z, vx, vy, vz, length, type, false, false);
        }

        public WeatherParticle(float x, float y, float z, float vx, float vy, float vz,
                               float length, WeatherType type, boolean isFluffy, boolean isDebris) {
            reset(x, y, z, vx, vy, vz, length, type, isFluffy, isDebris);
        }

        public void reset(float x, float y, float z, float vx, float vy, float vz,
                          float length, WeatherType type) {
            reset(x, y, z, vx, vy, vz, length, type, false, false);
        }

        public void reset(float x, float y, float z, float vx, float vy, float vz,
                          float length, WeatherType type, boolean isFluffy, boolean isDebris) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.length = length;
            this.type = type;
            this.isFluffy = isFluffy;
            this.isDebris = isDebris;
            this.isDead = false;
            this.wobble = MathUtils.random(0f, MathUtils.PI2);
            this.wobbleSpeed = isFluffy ? MathUtils.random(1.8f, 3.2f) : MathUtils.random(3.5f, 6.5f);
        }

        public void update(float delta) {
            if (type == WeatherType.SNOW) {
                wobble += wobbleSpeed * delta;
                float drift = MathUtils.sin(wobble) * (isFluffy ? 0.60f : 0.28f);
                x += (vx + drift) * delta;
                y += (vy + MathUtils.cos(wobble) * 0.20f) * delta;
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
