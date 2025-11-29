package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.gore.WallDecal;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.WorldConstants;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.managers.WorldManager;
import com.bpm.minotaur.weather.WeatherRenderer;

/**
 * Renders the 3D first-person view of the maze using a raycasting algorithm.
 * This class is responsible for drawing the floor, ceiling, walls, and doors.
 * It also generates a depth buffer to allow other renderers (like EntityRenderer)
 * to correctly occlude objects behind walls.
 */
public class FirstPersonRenderer {

    // --- Colors ---
    private Color currentFloorColor;
    private Color currentCeilingColor;
    private Color currentWallColor;
    private Color currentWallDarkColor;
    private Color currentDoorColor;
    private Color currentDoorDarkColor;
    private final Color fogLerpColor = new Color();

    // --- Rendering Constants ---
    private static final float DOOR_WIDTH = 1.0f / 3.0f;
    private static final float DOOR_HEIGHT_RATIO = 0.8f;
    private static final float FOG_FADE_RATIO = 0.5f;

    // --- Bitmasks ---
    private static final int WALL_WEST   = 0b00000001;
    private static final int WALL_EAST   = 0b00000100;
    private static final int WALL_SOUTH  = 0b00010000;
    private static final int WALL_NORTH  = 0b01000000;
    private static final int DOOR_WEST   = 0b00000010;
    private static final int DOOR_EAST   = 0b00001000;
    private static final int DOOR_SOUTH  = 0b00100000;
    private static final int DOOR_NORTH  = 0b10000000;

    private enum WallType { WALL, DOOR, GATE }

    private float[] depthBuffer;
    private final DebugManager debugManager = DebugManager.getInstance();
    private final SpriteBatch spriteBatch;

    // Textures
    private final Texture wallTexture;
    private final Texture doorTexture;
    private final Texture floorTexture;
    private final Texture skyboxNorth;
    private final Texture skyboxEast;
    private final Texture skyboxSouth;
    private final Texture skyboxWest;
    private final Texture retroSkyboxNorth;
    private final Texture retroSkyboxEast;
    private final Texture retroSkyboxSouth;
    private final Texture retroSkyboxWest;
    private final Texture retroSkyboxNorthStorm;
    private final Texture retroSkyboxEastStorm;
    private final Texture retroSkyboxSouthStorm;
    private final Texture retroSkyboxWestStorm;

    // Blank texture for Retro rendering
    private final Texture blankTexture;

    private WeatherRenderer weatherRenderer;

    public FirstPersonRenderer() {
        spriteBatch = new SpriteBatch();
        wallTexture = new Texture(Gdx.files.internal("images/wall.png"));
        doorTexture = new Texture(Gdx.files.internal("images/door.png"));
        floorTexture = new Texture(Gdx.files.internal("images/floor.png"));
        skyboxNorth = new Texture(Gdx.files.internal("images/skybox_castle.png"));
        skyboxEast = new Texture(Gdx.files.internal("images/skybox_east.png"));
        skyboxSouth = new Texture(Gdx.files.internal("images/skybox_south.png"));
        skyboxWest = new Texture(Gdx.files.internal("images/skybox_west.png"));
        retroSkyboxNorth = new Texture(Gdx.files.internal("images/retro_skybox_castle.jpg"));
        retroSkyboxEast = new Texture(Gdx.files.internal("images/retro_skybox_east.jpg"));
        retroSkyboxSouth = new Texture(Gdx.files.internal("images/retro_skybox_south.jpg"));
        retroSkyboxWest = new Texture(Gdx.files.internal("images/retro_skybox_west.jpg"));
        retroSkyboxNorthStorm = new Texture(Gdx.files.internal("images/retro_skybox_castle_storm.jpg"));
        retroSkyboxEastStorm = new Texture(Gdx.files.internal("images/retro_skybox_east_storm.jpg"));
        retroSkyboxSouthStorm = new Texture(Gdx.files.internal("images/retro_skybox_south_storm.jpg"));
        retroSkyboxWestStorm = new Texture(Gdx.files.internal("images/retro_skybox_west_storm.jpg"));

        // Create 1x1 white texture
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        blankTexture = new Texture(pixmap);
        pixmap.dispose();

        setTheme(RetroTheme.STANDARD_THEME);
    }

    public void setTheme(RetroTheme.Theme theme) {
        this.currentFloorColor = theme.floor;
        this.currentCeilingColor = theme.ceiling;
        this.currentWallColor = theme.wall;
        this.currentWallDarkColor = theme.wallDark;
        this.currentDoorColor = theme.door;
        this.currentDoorDarkColor = theme.doorDark;
    }

    public float[] getDepthBuffer() {
        return depthBuffer;
    }

    public void render(ShapeRenderer shapeRenderer, Player player, Maze maze, Viewport viewport, WorldManager worldManager, int currentLevel, GameMode gameMode) {

        if (this.weatherRenderer == null && worldManager.getWeatherManager() != null) {
            this.weatherRenderer = new WeatherRenderer(worldManager.getWeatherManager());
        }

        Biome biome = worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId());

        float fogDistance = 1000f;
        Color fogColor = new Color(Color.WHITE);
        boolean fogEnabled = false;
        float lightIntensity = 1.0f;

        // --- NEW: Check if player is inside the "Home" ---
        // Being inside the home behaves like being "Underground" (Level > 1)
        boolean isInsideHome = maze.isHomeTile((int)player.getPosition().x, (int)player.getPosition().y);
        boolean isIndoors = (currentLevel > 1) || isInsideHome;

        if (worldManager.getWeatherManager() != null && currentLevel == 1) {
            com.bpm.minotaur.weather.WeatherManager wm = worldManager.getWeatherManager();
            fogEnabled = true;
            fogDistance = wm.getFogDistance();
            fogColor.set(wm.getFogColor());
            lightIntensity = wm.getLightIntensity();
        } else if (gameMode == GameMode.ADVANCED && biome.hasFogOfWar()) {
            fogEnabled = true;
            fogDistance = biome.getFogDistance();
            fogColor.set(biome.getFogColor());
        }

        setTheme(maze.getTheme());

        if (depthBuffer == null || depthBuffer.length != viewport.getScreenWidth()) {
            depthBuffer = new float[(int)viewport.getScreenWidth()];
        }



        // 1. RENDER FLOOR & CEILING (Background)
        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
            spriteBatch.setShader(null);
            spriteBatch.begin();

            // FIX: Only render Skybox if NOT indoors.
            // Previous logic: (!isIndoors || currentLevel > 1) caused Dungeons to have Skyboxes.
            if (!isIndoors) {
                renderSkyboxCeiling(spriteBatch, player, viewport, lightIntensity, worldManager);
            }
            renderTexturedFloor(spriteBatch, player, viewport, fogEnabled, fogDistance, fogColor, lightIntensity, maze);
        } else {
            // RETRO MODE
            spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
            spriteBatch.setShader(null);
            spriteBatch.begin();

            // A. Ceiling/Skybox
            // FIX: Only render Skybox if NOT indoors.
            if (!isIndoors) {
                renderSkyboxCeiling(spriteBatch, player, viewport, lightIntensity, worldManager);
            } else {
                // Render Flat Ceiling for Indoors (Home OR Dungeon)
                Color ceilColor = applyTorchLighting(currentCeilingColor, WorldConstants.TORCH_FADE_END, new Color());
                spriteBatch.setColor(ceilColor);
                spriteBatch.draw(blankTexture, 0, viewport.getWorldHeight() / 2, viewport.getWorldWidth(), viewport.getWorldHeight() / 2);
            }

            // B. Floor
            renderRetroFloor(spriteBatch, player, viewport, maze, fogEnabled, fogDistance, fogColor, lightIntensity);

            spriteBatch.end();
        }

        // --- RENDER TORNADO (Behind Walls) ---
        if (this.weatherRenderer != null && currentLevel == 1) {
            this.weatherRenderer.update(Gdx.graphics.getDeltaTime());
            this.weatherRenderer.renderTornado(shapeRenderer, viewport);
        }

        // 2. RENDER WALLS (Midground)
        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            spriteBatch.setShader(null);
            spriteBatch.begin();
            for (int x = 0; x < viewport.getScreenWidth(); x++) {
                RaycastResult result = castRay(player, maze, x, viewport, worldManager);
                if (result != null) {
                    maze.markVisited(result.mapX, result.mapY);
                    renderWallSliceTexture(spriteBatch, result, x, viewport, fogEnabled, fogDistance, fogColor, lightIntensity);                    depthBuffer[x] = result.distance;
                } else {
                    depthBuffer[x] = Float.MAX_VALUE;
                    if (fogEnabled) {
                        Color wallFog = new Color(fogColor).mul(lightIntensity, lightIntensity, lightIntensity, 1f);
                        spriteBatch.setColor(wallFog);
                        spriteBatch.draw(wallTexture, x, 0, 1, viewport.getWorldHeight(), 0, 0, 1, 1, false, false);
                        spriteBatch.setColor(Color.WHITE);
                    }
                }
            }
            spriteBatch.end();

        } else {
            // Retro Path
            spriteBatch.setShader(null);
            spriteBatch.begin();

            for (int x = 0; x < viewport.getScreenWidth(); x++) {
                RaycastResult result = castRay(player, maze, x, viewport, worldManager);

                if (result != null) {
                    maze.markVisited(result.mapX, result.mapY);
                    renderRetroWallSlice(spriteBatch, result, x, viewport, fogEnabled, fogDistance, fogColor, lightIntensity, maze);                    depthBuffer[x] = result.distance;
                } else {
                    depthBuffer[x] = Float.MAX_VALUE;
                    if (fogEnabled) {
                        Color wallFog = new Color(fogColor).mul(lightIntensity, lightIntensity, lightIntensity, 1f);
                        spriteBatch.setColor(wallFog);
                        spriteBatch.draw(blankTexture, x, 0, 1, viewport.getWorldHeight());
                    }
                }
            }
            spriteBatch.end();
        }

        // --- RENDER PRECIPITATION (Foreground) ---
        // ONLY render if NOT indoors
        if (!isIndoors && this.weatherRenderer != null && currentLevel == 1) {
            this.weatherRenderer.renderPrecipitation(shapeRenderer, viewport);
        }
    }

    private void renderSkyboxCeiling(SpriteBatch spriteBatch, Player player, Viewport viewport, float lightIntensity, WorldManager worldManager) {
        Texture skyboxTexture;
        boolean isStormy = false;
        if (worldManager.getWeatherManager() != null) {
            isStormy = worldManager.getWeatherManager().isStormy();
        }
        switch (player.getFacing()) {
            case EAST: skyboxTexture = isStormy ? retroSkyboxEastStorm : retroSkyboxEast; break;
            case WEST: skyboxTexture = isStormy ? retroSkyboxWestStorm : retroSkyboxWest; break;
            case SOUTH: skyboxTexture = isStormy ? retroSkyboxSouthStorm : retroSkyboxSouth; break;
            default: skyboxTexture = isStormy ? retroSkyboxNorthStorm : retroSkyboxNorth; break;
        }
        spriteBatch.setColor(lightIntensity, lightIntensity, lightIntensity, 1f);
        spriteBatch.draw(skyboxTexture, 0, (viewport.getWorldHeight() / 2 - 160), viewport.getWorldWidth(), (viewport.getWorldHeight() / 2)+ 160);
        spriteBatch.setColor(Color.WHITE);
    }

    // ADDED: Maze maze parameter
    private void renderTexturedFloor(SpriteBatch spriteBatch, Player player, Viewport viewport, boolean fogEnabled, float fogDistance, Color fogColor, float lightIntensity, Maze maze) {
        for (int y = (int)(viewport.getWorldHeight() / 2); y >= 0; y--) {
            float rayDirX0 = player.getDirectionVector().x - player.getCameraPlane().x;
            float rayDirY0 = player.getDirectionVector().y - player.getCameraPlane().y;
            float rayDirX1 = player.getDirectionVector().x + player.getCameraPlane().x;
            float rayDirY1 = player.getDirectionVector().y + player.getCameraPlane().y;

            int p_down = (int)(viewport.getWorldHeight() / 2) - y;
            float posZ = 0.5f * viewport.getWorldHeight();
            float rowDistance = (p_down == 0) ? Float.MAX_VALUE : posZ / p_down;

            if (rowDistance <= 0 || rowDistance == Float.MAX_VALUE) continue;

            float floorStepX = rowDistance * (rayDirX1 - rayDirX0) / viewport.getWorldWidth();
            float floorStepY = rowDistance * (rayDirY1 - rayDirY0) / viewport.getWorldWidth();

            float floorX = player.getPosition().x + rowDistance * rayDirX0;
            float floorY = player.getPosition().y + rowDistance * rayDirY0;

            Color rowColor = new Color(Color.WHITE);
            if (fogEnabled) {
                float fogAmount = Math.max(0, Math.min(1f, (rowDistance - (fogDistance * (1f - FOG_FADE_RATIO))) / (fogDistance * FOG_FADE_RATIO)));
                rowColor = fogLerpColor.set(Color.WHITE).lerp(fogColor, fogAmount);
            }
            rowColor.mul(lightIntensity, lightIntensity, lightIntensity, 1f);

            // OPTIMIZATION: Track the last marked tile to reduce method calls per scanline
            int lastMarkedX = -999;
            int lastMarkedY = -999;

            for(int x = 0; x < viewport.getWorldWidth(); ++x) {
                int cellX = (int)(floorX);
                int cellY = (int)(floorY);

                // --- NEW: Mark Floor as Visited ---
                // We assume if you can see the floor pixel, you can see the tile.
                // Check against lastMarked to avoid calling map.markVisited 1920 times per line

                // ----------------------------------

                int tx = (int)(floorTexture.getWidth() * (floorX - cellX)) & (floorTexture.getWidth() - 1);
                int ty = (int)(floorTexture.getHeight() * (floorY - cellY)) & (floorTexture.getHeight() - 1);

                floorX += floorStepX;
                floorY += floorStepY;

                float brightness = Math.max(0.2f, Math.min(1.0f, 1.0f - rowDistance / 20.0f));
                fogLerpColor.set(rowColor.r * brightness, rowColor.g * brightness, rowColor.b * brightness, 1.0f);
                spriteBatch.setColor(fogLerpColor);
                spriteBatch.draw(floorTexture, x, y, 1, 1, tx, ty, 1, 1, false, false);
            }
        }
        spriteBatch.setColor(Color.WHITE);
    }

    // Retro floor rendering (Removed Blood Support)
    private void renderRetroFloor(SpriteBatch spriteBatch, Player player, Viewport viewport, Maze maze, boolean fogEnabled, float fogDistance, Color fogColor, float lightIntensity) {
        for (int y = (int)(viewport.getWorldHeight() / 2); y >= 0; y--) {
            // ... (keep rayDir and math setup exactly as is) ...
            float rayDirX0 = player.getDirectionVector().x - player.getCameraPlane().x;
            float rayDirY0 = player.getDirectionVector().y - player.getCameraPlane().y;
            float rayDirX1 = player.getDirectionVector().x + player.getCameraPlane().x;
            float rayDirY1 = player.getDirectionVector().y + player.getCameraPlane().y;

            int p_down = (int)(viewport.getWorldHeight() / 2) - y;
            float posZ = 0.5f * viewport.getWorldHeight();
            float rowDistance = (p_down == 0) ? Float.MAX_VALUE : posZ / p_down;

            if (rowDistance <= 0 || rowDistance == Float.MAX_VALUE) continue;

            float floorStepX = rowDistance * (rayDirX1 - rayDirX0) / viewport.getWorldWidth();
            float floorStepY = rowDistance * (rayDirY1 - rayDirY0) / viewport.getWorldWidth();

            float floorX = player.getPosition().x + rowDistance * rayDirX0;
            float floorY = player.getPosition().y + rowDistance * rayDirY0;

            Color baseTint = new Color(Color.WHITE);
            // ... (keep fog logic) ...
            if (fogEnabled) {
                float fogAmount = Math.max(0, Math.min(1f, (rowDistance - (fogDistance * (1f - FOG_FADE_RATIO))) / (fogDistance * FOG_FADE_RATIO)));
                baseTint = fogLerpColor.set(Color.WHITE).lerp(fogColor, fogAmount);
            }
            baseTint.mul(lightIntensity, lightIntensity, lightIntensity, 1f);



            for(int x = 0; x < viewport.getWorldWidth(); ++x) {
                // --- NEW: Mark Floor Visited ---
                int cX = (int)floorX;
                int cY = (int)floorY;

                // -------------------------------

                // Apply torch brightness
                float brightness = calculateTorchBrightness(rowDistance);
                Color finalColor = new Color(currentFloorColor).mul(baseTint).mul(brightness, brightness, brightness, 1f);

                spriteBatch.setColor(finalColor);
                spriteBatch.draw(blankTexture, x, y, 1, 1);

                floorX += floorStepX;
                floorY += floorStepY;
            }
        }
        spriteBatch.setColor(Color.WHITE);
    }

    private void renderWallSliceTexture(SpriteBatch spriteBatch, RaycastResult result, int screenX, Viewport viewport, boolean fogEnabled, float fogDistance, Color fogColor, float lightIntensity) {
        int lineHeight = (result.distance <= 0) ? Integer.MAX_VALUE : (int) (viewport.getWorldHeight() / result.distance);
        float drawStart = Math.max(0, -lineHeight / 2f + viewport.getWorldHeight() / 2f);
        float drawEnd = Math.min(viewport.getWorldHeight(), lineHeight / 2f + viewport.getWorldHeight() / 2f);

        Texture texture = (result.wallType == WallType.DOOR) ? doorTexture : wallTexture;

        if (fogEnabled) {
            float fogAmount = Math.max(0, Math.min(1f, (result.distance - (fogDistance * (1f - FOG_FADE_RATIO))) / (fogDistance * FOG_FADE_RATIO)));
            fogLerpColor.set(Color.WHITE).lerp(fogColor, fogAmount);
            fogLerpColor.mul(lightIntensity, lightIntensity, lightIntensity, 1f);
            spriteBatch.setColor(fogLerpColor);
        } else {
            spriteBatch.setColor(lightIntensity, lightIntensity, lightIntensity, 1f);
        }

        int texX = (int)(result.wallX * texture.getWidth());
        spriteBatch.draw(texture, screenX, drawStart, 1, drawEnd - drawStart, texX, 0, 1, texture.getHeight(), false, false);
        spriteBatch.setColor(Color.WHITE);
    }

    private void renderRetroWallSlice(SpriteBatch spriteBatch, RaycastResult result, int screenX, Viewport viewport, boolean fogEnabled, float fogDistance, Color fogColor, float lightIntensity, Maze maze) {
        int lineHeight = (result.distance <= 0) ? Integer.MAX_VALUE : (int) (viewport.getWorldHeight() / result.distance);
        float drawStart = Math.max(0, -lineHeight / 2f + viewport.getWorldHeight() / 2f);
        float drawEnd = Math.min(viewport.getWorldHeight(), lineHeight / 2f + viewport.getWorldHeight() / 2f);
        float height = drawEnd - drawStart;

        Color colorModifier = new Color(1, 1, 1, 1);
        if (fogEnabled) {
            float fogAmount = Math.max(0, Math.min(1f, (result.distance - (fogDistance * (1f - FOG_FADE_RATIO))) / (fogDistance * FOG_FADE_RATIO)));
            colorModifier.lerp(fogColor, fogAmount);
        }
        colorModifier.mul(lightIntensity, lightIntensity, lightIntensity, 1f);

        if (result.wallType == WallType.DOOR) {
            float doorEdgeMargin = (1.0f - DOOR_WIDTH) / 2.0f;
            boolean isFramePart = result.wallX < doorEdgeMargin || result.wallX > 1.0f - doorEdgeMargin;

            if (isFramePart) {
                Color frameColor = (result.side == 1) ? currentWallDarkColor : currentWallColor;
                Color finalColor = new Color(frameColor).mul(colorModifier);
                spriteBatch.setColor(applyTorchLighting(finalColor, result.distance, fogLerpColor));
                spriteBatch.draw(blankTexture, screenX, drawStart, 1, height);
            } else {
                float doorHeight = height * DOOR_HEIGHT_RATIO;
                float doorDrawStart = drawStart;
                float doorDrawEnd = drawStart + doorHeight;

                if (drawEnd > doorDrawEnd) {
                    Color frameColor = (result.side == 1) ? currentWallDarkColor : currentWallColor;
                    Color finalFrameColor = new Color(frameColor).mul(colorModifier);
                    spriteBatch.setColor(applyTorchLighting(finalFrameColor, result.distance, fogLerpColor));
                    spriteBatch.draw(blankTexture, screenX, doorDrawEnd, 1, drawEnd - doorDrawEnd);
                }

                if (result.door != null && result.door.getState() == Door.DoorState.OPENING) {
                    float openingOffset = (doorDrawEnd - doorDrawStart) * result.door.getAnimationProgress();
                    doorDrawStart += openingOffset;
                }

                if (doorDrawEnd > doorDrawStart) {
                    Color doorRenderColor = (result.side == 1) ? currentDoorDarkColor : currentDoorColor;
                    Color finalDoorColor = new Color(doorRenderColor).mul(colorModifier);
                    spriteBatch.setColor(applyTorchLighting(finalDoorColor, result.distance, fogLerpColor));
                    spriteBatch.draw(blankTexture, screenX, doorDrawStart, 1, doorDrawEnd - doorDrawStart);
                }
            }
        } else if (result.wallType == WallType.GATE && result.gate != null) {
            float doorHeight = height * DOOR_HEIGHT_RATIO;
            float doorDrawStart = drawStart;
            float doorDrawEnd = drawStart + doorHeight;

            Color frameColor = (result.side == 1) ? currentWallDarkColor : currentWallColor;
            Color finalFrameColor = new Color(frameColor).mul(colorModifier);
            spriteBatch.setColor(applyTorchLighting(finalFrameColor, result.distance, fogLerpColor));

            if (drawEnd > doorDrawEnd) {
                spriteBatch.draw(blankTexture, screenX, doorDrawEnd, 1, drawEnd - doorDrawEnd);
            }

            if (result.gate.getState() == Gate.GateState.OPENING) {
                float openingOffset = (doorDrawEnd - doorDrawStart) * result.gate.getAnimationProgress();
                doorDrawStart += openingOffset;
            }

            if (result.gate.getState() != Gate.GateState.OPEN && doorDrawEnd > doorDrawStart) {
                Color gateColor = new Color(Color.CYAN).mul(colorModifier);
                spriteBatch.setColor(applyTorchLighting(gateColor, result.distance, fogLerpColor));
                spriteBatch.draw(blankTexture, screenX, doorDrawStart, 1, doorDrawEnd - doorDrawStart);
            }
        } else {
            Color renderColor = (result.side == 1) ? currentWallDarkColor : currentWallColor;
            Color finalColor = new Color(renderColor).mul(colorModifier);
            spriteBatch.setColor(applyTorchLighting(finalColor, result.distance, fogLerpColor));
            spriteBatch.draw(blankTexture, screenX, drawStart, 1, height);
        }

        // ============================================================
        // NEW: WALL SPLATTER LOGIC (GORE SYSTEM)
        // ============================================================
        if (result.wallType == WallType.WALL || result.wallType == WallType.DOOR) {
            // Access the gore manager from the maze
            Array<WallDecal> decals = maze.getGoreManager().getWallDecals(result.mapX, result.mapY, result.side);

            if (decals != null) {
                float centerY = viewport.getWorldHeight() / 2f;

                for (WallDecal decal : decals) {
                    // 1. Calculate horizontal distance from this pixel strip to the center of the blood splat
                    float distX = Math.abs(decal.wallX - result.wallX);

                    // 2. Check if this pixel strip is inside the blood splat radius
                    if (distX < decal.radius) {
                        // 3. Circle Math: Calculate the vertical height of the splat at this specific X slice
                        // Pythagorean theorem: dy = sqrt(r^2 - dx^2)
                        float halfSplatHeight = (float) Math.sqrt(decal.radius * decal.radius - distX * distX);

                        float splatTopWorldY = decal.height + halfSplatHeight;
                        float splatBottomWorldY = decal.height - halfSplatHeight;

                        // 4. Convert World Height (0.0 - 1.0) to Screen Coordinates
                        // Subtract 0.5f because camera eye level is at 0.5
                        float screenYTop = centerY + (splatTopWorldY - 0.5f) * lineHeight;
                        float screenYBottom = centerY + (splatBottomWorldY - 0.5f) * lineHeight;
                        float splatHeight = screenYTop - screenYBottom;

                        // 5. Draw the blood slice
                        // We apply torch lighting to the blood color so it fades in the dark
                        spriteBatch.setColor(applyTorchLighting(decal.color, result.distance, fogLerpColor));
                        spriteBatch.draw(blankTexture, screenX, screenYBottom, 1, splatHeight);
                    }
                }
                // Reset batch color to white for next draw call
                spriteBatch.setColor(Color.WHITE);
            }
        }
    }

    private RaycastResult castRay(Player player, Maze maze, int screenX, Viewport viewport, WorldManager worldManager) {
        Vector2 renderPosition;
        int playerX = (int) player.getPosition().x;
        int playerY = (int) player.getPosition().y;
        boolean isBehindBlocked = maze.isWallBlocking(playerX, playerY, player.getFacing().getOpposite());

        if (isBehindBlocked) {
            renderPosition = player.getPosition().cpy();
        } else {
            renderPosition = player.getPosition().cpy().sub(player.getDirectionVector());
        }

        int startTileX = (int) renderPosition.x;
        int startTileY = (int) renderPosition.y;
        Object startObject = maze.getGameObjectAt(startTileX, startTileY);

        Object objectToIgnore = null;
        if (startObject instanceof Door || startObject instanceof Gate) {
            objectToIgnore = startObject;
        }

        float cameraX = 1 - 2 * screenX / (float) viewport.getScreenWidth();
        Vector2 rayDir = new Vector2(player.getDirectionVector()).add(new Vector2(player.getCameraPlane()).scl(cameraX));

        int mapX = (int) renderPosition.x;
        int mapY = (int) renderPosition.y;

        Vector2 sideDist = new Vector2();
        Vector2 deltaDist = new Vector2(
            (rayDir.x == 0) ? Float.MAX_VALUE : Math.abs(1 / rayDir.x),
            (rayDir.y == 0) ? Float.MAX_VALUE : Math.abs(1 / rayDir.y)
        );

        int stepX, stepY;
        if (rayDir.x < 0) {
            stepX = -1;
            sideDist.x = (renderPosition.x - mapX) * deltaDist.x;
        } else {
            stepX = 1;
            sideDist.x = (mapX + 1.0f - renderPosition.x) * deltaDist.x;
        }
        if (rayDir.y < 0) {
            stepY = -1;
            sideDist.y = (renderPosition.y - mapY) * deltaDist.y;
        } else {
            stepY = 1;
            sideDist.y = (mapY + 1.0f - renderPosition.y) * deltaDist.y;
        }

        boolean hit = false;
        int side = 0;
        Biome biome = worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId());
        float maxDist = (biome.isSeamless()) ? biome.getFogDistance() + 2 : 50;
        int maxDistance = (int) maxDist;
        int distanceTraveled = 0;
        boolean hitDoorFrame = false;
        int doorFrameMapX = -1, doorFrameMapY = -1;
        int doorFrameSide = -1;
        Door doorFrameObject = null;

        while (!hit && distanceTraveled < maxDistance) {

            maze.markVisited(mapX, mapY);

            if (sideDist.x < sideDist.y) {
                sideDist.x += deltaDist.x;
                mapX += stepX;
                side = 0;
            } else {
                sideDist.y += deltaDist.y;
                mapY += stepY;
                side = 1;
            }

            if (isOutOfBounds(mapX, mapY, maze)) {
                int prevMapX = (side == 0) ? mapX - stepX : mapX;
                int prevMapY = (side == 1) ? mapY - stepY : mapY;
                Object prevObj = maze.getGameObjectAt(prevMapX, prevMapY);

                if (prevObj instanceof Gate && ((Gate) prevObj).getState() == Gate.GateState.OPEN) {
                    Gate gate = (Gate) prevObj;
                    Maze nextMaze = worldManager.getChunk(gate.getTargetChunkId());

                    if (nextMaze != null) {
                        maze = nextMaze;
                        if (side == 0) {
                            mapX = (stepX > 0) ? 0 : maze.getWidth() - 1;
                        } else {
                            mapY = (stepY > 0) ? 0 : maze.getHeight() - 1;
                        }
                        Object entryGate = maze.getGameObjectAt(mapX, mapY);
                        if (entryGate instanceof Gate || entryGate instanceof Door) {
                            objectToIgnore = entryGate;
                        } else {
                            objectToIgnore = null;
                        }
                        continue;
                    } else {
                        hit = true;
                    }
                } else if (biome.isSeamless()) {
                    break;
                } else {
                    hit = true;
                }

            } else {
                int wallData = maze.getWallDataAt(mapX, mapY);
                int prevMapX = (side == 0) ? mapX - stepX : mapX;
                int prevMapY = (side == 1) ? mapY - stepY : mapY;
                int prevWallData = maze.getWallDataAt(prevMapX, prevMapY);
                Object currentObj = maze.getGameObjectAt(mapX, mapY);
                Object prevObj = maze.getGameObjectAt(prevMapX, prevMapY);

                if (currentObj instanceof Gate && currentObj != objectToIgnore) {
                    Gate gate = (Gate) currentObj;
                    if (gate.getState() == Gate.GateState.CLOSED || gate.getState() == Gate.GateState.OPENING) {
                        hit = true;
                    }
                } else if ((currentObj instanceof Door && currentObj != objectToIgnore) ||
                    (prevObj instanceof Door && prevObj != objectToIgnore)) {
                    Door door = (currentObj instanceof Door) ? (Door)currentObj : (Door)prevObj;

                    if (door.getState() != Door.DoorState.OPEN) {
                        hit = true;
                    } else {
                        float wallHitX;
                        if (side == 0) {
                            wallHitX = renderPosition.y + (sideDist.x - deltaDist.x) * rayDir.y;
                        } else {
                            wallHitX = renderPosition.x + (sideDist.y - deltaDist.y) * rayDir.x;
                        }
                        wallHitX -= Math.floor(wallHitX);

                        float doorEdgeMargin = (1.0f - DOOR_WIDTH) / 2.0f;
                        boolean hitsFrame = wallHitX < doorEdgeMargin || wallHitX > 1.0f - doorEdgeMargin;

                        if (hitsFrame) {
                            hit = true;
                        } else {
                            objectToIgnore = door;
                        }
                    }
                } else {
                    if (hasWallCollision(wallData, prevWallData, side, stepX, stepY)) {
                        hit = true;
                    }
                    else if (hasDoorCollision(wallData, prevWallData, side, stepX, stepY)) {
                        Object obj = maze.getGameObjectAt(mapX, mapY);
                        if (obj == null) obj = maze.getGameObjectAt(prevMapX, prevMapY);
                        if (obj instanceof Door && obj != objectToIgnore) {
                            Door door = (Door) obj;
                            if (door.getState() != Door.DoorState.OPEN) {
                                hit = true;
                            }
                        }
                    }
                }
            }
            distanceTraveled++;
        }

        if (hit && hitDoorFrame) {
            float actualFrameDistance;
            if (doorFrameSide == 0) {
                float wallXPos = (stepX > 0) ? doorFrameMapX : doorFrameMapX + 1;
                actualFrameDistance = Math.abs((wallXPos - renderPosition.x) / rayDir.x);
            } else {
                float wallYPos = (stepY > 0) ? doorFrameMapY : doorFrameMapY + 1;
                actualFrameDistance = Math.abs((wallYPos - renderPosition.y) / rayDir.y);
            }

            float frameWallX;
            if (doorFrameSide == 0) {
                frameWallX = renderPosition.y + actualFrameDistance * rayDir.y;
            } else {
                frameWallX = renderPosition.x + actualFrameDistance * rayDir.x;
            }
            frameWallX -= Math.floor(frameWallX);

            float doorEdgeMargin = (1.0f - DOOR_WIDTH) / 2.0f;
            boolean hitsFrame = frameWallX < doorEdgeMargin || frameWallX > 1.0f - doorEdgeMargin;

            if (hitsFrame) {
                return new RaycastResult(actualFrameDistance, doorFrameSide, WallType.DOOR, doorFrameObject, null, frameWallX, doorFrameMapX, doorFrameMapY);
            }
        }

        if (!hit) return null;

        float perpWallDist;
        if (side == 0) {
            perpWallDist = (sideDist.x - deltaDist.x);
        } else {
            perpWallDist = (sideDist.y - deltaDist.y);
        }

        float wallX;
        if (side == 0) {
            wallX = renderPosition.y + perpWallDist * rayDir.y;
        } else {
            wallX = renderPosition.x + perpWallDist * rayDir.x;
        }
        wallX -= Math.floor(wallX);

        WallType wallType = WallType.WALL;
        Door hitDoor = null;
        Gate hitGate = null;
        Object hitObject = null;
        if (!isOutOfBounds(mapX, mapY, maze)) {
            hitObject = maze.getGameObjectAt(mapX, mapY);

            if (hitObject instanceof Gate) {
                wallType = WallType.GATE;
                hitGate = (Gate) hitObject;
            } else if (hitObject instanceof Door) {
                wallType = WallType.DOOR;
                hitDoor = (Door) hitObject;
            } else {
                int prevMapX = (side == 0) ? mapX - stepX : mapX;
                int prevMapY = (side == 1) ? mapY - stepY : mapY;
                Object prevObject = maze.getGameObjectAt(prevMapX, prevMapY);
                if (prevObject instanceof Door) {
                    wallType = WallType.DOOR;
                    hitDoor = (Door) prevObject;
                }
            }
        }
        return new RaycastResult(perpWallDist, side, wallType, hitDoor, hitGate, wallX, mapX, mapY);
    }

    private boolean isOutOfBounds(int x, int y, Maze maze) {
        return x < 0 || x >= maze.getWidth() || y < 0 || y >= maze.getHeight();
    }

    private boolean hasWallCollision(int currentData, int prevData, int side, int stepX, int stepY) {
        if (side == 0) {
            return (stepX > 0) ? (prevData & WALL_EAST) != 0 || (currentData & WALL_WEST) != 0
                : (prevData & WALL_WEST) != 0 || (currentData & WALL_EAST) != 0;
        } else {
            return (stepY > 0) ? (prevData & WALL_NORTH) != 0 || (currentData & WALL_SOUTH) != 0
                : (prevData & WALL_SOUTH) != 0 || (currentData & WALL_NORTH) != 0;
        }
    }

    private boolean hasDoorCollision(int currentData, int prevData, int side, int stepX, int stepY) {
        if (side == 0) {
            return (stepX > 0) ? (prevData & DOOR_EAST) != 0 || (currentData & DOOR_WEST) != 0
                : (prevData & DOOR_WEST) != 0 || (currentData & DOOR_EAST) != 0;
        } else {
            return (stepY > 0) ? (prevData & DOOR_NORTH) != 0 || (currentData & DOOR_SOUTH) != 0
                : (prevData & DOOR_SOUTH) != 0 || (currentData & DOOR_NORTH) != 0;
        }
    }

    private Color applyTorchLighting(Color originalColor, float distance, Color outputColor) {
        float brightness = calculateTorchBrightness(distance);
        outputColor.set(
            originalColor.r * brightness,
            originalColor.g * brightness,
            originalColor.b * brightness,
            originalColor.a
        );
        return outputColor;
    }

    private float calculateTorchBrightness(float distance) {
        if (distance <= WorldConstants.TORCH_FULL_BRIGHTNESS_RADIUS) {
            return 1.0f;
        } else if (distance <= WorldConstants.TORCH_FADE_START) {
            float fadeRatio = (distance - WorldConstants.TORCH_FULL_BRIGHTNESS_RADIUS) / (WorldConstants.TORCH_FADE_START - WorldConstants.TORCH_FULL_BRIGHTNESS_RADIUS);
            return 1.0f - (fadeRatio * (1.0f - 0.8f));
        } else if (distance <= WorldConstants.TORCH_FADE_END) {
            float fadeRatio = (distance - WorldConstants.TORCH_FADE_START) / (WorldConstants.TORCH_FADE_END - WorldConstants.TORCH_FADE_START);
            return Math.max(WorldConstants.TORCH_MIN_BRIGHTNESS, 0.8f - (fadeRatio * (0.8f - WorldConstants.TORCH_MIN_BRIGHTNESS)));
        } else {
            return WorldConstants.TORCH_MIN_BRIGHTNESS;
        }
    }

    public float checkLineOfSight(Player player, Maze maze, Vector2 targetPos) {
        Vector2 renderPosition;
        int playerX = (int) player.getPosition().x;
        int playerY = (int) player.getPosition().y;
        boolean isBehindBlocked = maze.isWallBlocking(playerX, playerY, player.getFacing().getOpposite());

        if (isBehindBlocked) {
            renderPosition = player.getPosition().cpy();
        } else {
            renderPosition = player.getPosition().cpy().sub(player.getDirectionVector());
        }

        Vector2 rayDir = targetPos.cpy().sub(renderPosition);
        float targetDistance = rayDir.len();
        rayDir.nor();

        int startTileX = (int) renderPosition.x;
        int startTileY = (int) renderPosition.y;
        Object startObject = maze.getGameObjectAt(startTileX, startTileY);
        Door doorToIgnore = null;
        if (startObject instanceof Door) {
            doorToIgnore = (Door) startObject;
        }

        int mapX = (int) renderPosition.x;
        int mapY = (int) renderPosition.y;

        Vector2 deltaDist = new Vector2(
            (rayDir.x == 0) ? Float.MAX_VALUE : Math.abs(1 / rayDir.x),
            (rayDir.y == 0) ? Float.MAX_VALUE : Math.abs(1 / rayDir.y)
        );

        Vector2 sideDist = new Vector2();
        int stepX, stepY;

        if (rayDir.x < 0) {
            stepX = -1;
            sideDist.x = (renderPosition.x - mapX) * deltaDist.x;
        } else {
            stepX = 1;
            sideDist.x = (mapX + 1.0f - renderPosition.x) * deltaDist.x;
        }

        if (rayDir.y < 0) {
            stepY = -1;
            sideDist.y = (renderPosition.y - mapY) * deltaDist.y;
        } else {
            stepY = 1;
            sideDist.y = (mapY + 1.0f - renderPosition.y) * deltaDist.y;
        }

        boolean hit = false;
        int side = 0;
        int maxDistance = 50;
        int distanceTraveled = 0;
        float hitDistance = Float.MAX_VALUE;

        while (!hit && distanceTraveled < maxDistance) {
            float currentDistance;
            if (sideDist.x < sideDist.y) {
                currentDistance = sideDist.x;
                sideDist.x += deltaDist.x;
                mapX += stepX;
                side = 0;
            } else {
                currentDistance = sideDist.y;
                sideDist.y += deltaDist.y;
                mapY += stepY;
                side = 1;
            }

            if (currentDistance > targetDistance) {
                return targetDistance;
            }

            if (isOutOfBounds(mapX, mapY, maze)) {
                hit = true;
                hitDistance = currentDistance;
            } else {
                int wallData = maze.getWallDataAt(mapX, mapY);
                int prevMapX = (side == 0) ? mapX - stepX : mapX;
                int prevMapY = (side == 1) ? mapY - stepY : mapY;
                int prevWallData = maze.getWallDataAt(prevMapX, prevMapY);

                Object currentObj = maze.getGameObjectAt(mapX, mapY);
                Object prevObj = maze.getGameObjectAt(prevMapX, prevMapY);

                if (currentObj instanceof Gate) {
                    Gate gate = (Gate) currentObj;
                    if (gate.getState() == Gate.GateState.CLOSED || gate.getState() == Gate.GateState.OPENING) {
                        hit = true;
                        hitDistance = currentDistance;
                    }
                } else {
                    boolean currentTileHasOpenDoor = (currentObj instanceof Door &&
                        ((Door) currentObj).getState() == Door.DoorState.OPEN &&
                        currentObj != doorToIgnore);
                    boolean prevTileHasOpenDoor = (prevObj instanceof Door &&
                        ((Door) prevObj).getState() == Door.DoorState.OPEN &&
                        prevObj != doorToIgnore);

                    if (hasWallCollision(wallData, prevWallData, side, stepX, stepY)) {
                        hit = true;
                        hitDistance = currentDistance;
                    }
                    else if (hasDoorCollision(wallData, prevWallData, side, stepX, stepY)) {
                        Object obj = maze.getGameObjectAt(mapX, mapY);
                        if (obj == null) obj = maze.getGameObjectAt(prevMapX, prevMapY);

                        if (obj instanceof Door && obj != doorToIgnore) {
                            Door door = (Door) obj;
                            if (door.getState() != Door.DoorState.OPEN) {
                                hit = true;
                                hitDistance = currentDistance;
                            }
                        }
                    }
                }
            }
            distanceTraveled++;
        }

        return hit ? hitDistance : targetDistance;
    }

    public void renderAsciiViewToConsole(Player player, Maze maze) {
        // ... (omitted for brevity as it's unchanged)
    }

    private static class RaycastResult {
        final float distance;
        final int side;
        final WallType wallType;
        final Door door;
        final Gate gate;
        final float wallX;
        final int mapX;
        final int mapY;

        RaycastResult(float distance, int side, WallType wallType, Door door, Gate gate, float wallX, int mapX, int mapY) {
            this.distance = distance;
            this.side = side;
            this.wallType = wallType;
            this.door = door;
            this.gate = gate;
            this.wallX = wallX;
            this.mapX = mapX;
            this.mapY = mapY;
        }
    }
}
