// Path: core/src/main/java/com/bpm/minotaur/rendering/EntityRenderer.java
package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.generation.Biome; // <-- ADD IMPORT
import com.bpm.minotaur.generation.WorldConstants;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.managers.WorldManager; // <-- ADD IMPORT

import java.util.ArrayList;
import java.util.List;

public class EntityRenderer {

    private final SpriteBatch spriteBatch;
    private static final float MONSTER_TEXTURE_SCALE = 1.0f;
    private static final float CLOSE_ITEM_Y_BOOST = 140.0f;
    private final DebugManager debugManager = DebugManager.getInstance();

    private static final Color GLOW_COLOR_MODERN = new Color(1.0f, 0.9f, 0.2f, 0.4f); // Translucent Gold
    private static final Color GLOW_COLOR_RETRO = Color.GOLD;

    public EntityRenderer() {
        this.spriteBatch = new SpriteBatch();
    }

    private static final int AT_FEET_SPRITE_HEIGHT = 200;



    /**
     * Applies torch lighting to a color by darkening it based on distance.
     *
     * @param originalColor The original color to darken
     * @param distance The distance from the player
     * @param outputColor Reusable Color object to store the result
     * @return The darkened color
     */
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

    /**
     * Calculates the brightness multiplier based on distance from player.
     * Creates a torch effect with full brightness close to player, fading to darkness at distance.
     *
     * @param distance The perpendicular distance from the player
     * @return A brightness value between TORCH_MIN_BRIGHTNESS and 1.0
     */
    private float calculateTorchBrightness(float distance) {
        if (distance <= WorldConstants.TORCH_FULL_BRIGHTNESS_RADIUS) {
            return 1.0f; // Full brightness close to player
        } else if (distance <= WorldConstants.TORCH_FADE_START) {
            // Gradual fade from full brightness to dimming
            float fadeRatio = (distance - WorldConstants.TORCH_FULL_BRIGHTNESS_RADIUS) / (WorldConstants.TORCH_FADE_START - WorldConstants.TORCH_FULL_BRIGHTNESS_RADIUS);
            return 1.0f - (fadeRatio * (1.0f - 0.8f)); // Fade from 100% to 80%
        } else if (distance <= WorldConstants.TORCH_FADE_END) {
            // Main dimming zone
            float fadeRatio = (distance - WorldConstants.TORCH_FADE_START) / (WorldConstants.TORCH_FADE_END - WorldConstants.TORCH_FADE_START);
            return Math.max(WorldConstants.TORCH_MIN_BRIGHTNESS, 0.8f - (fadeRatio * (0.8f - WorldConstants.TORCH_MIN_BRIGHTNESS)));
        } else {
            return WorldConstants.TORCH_MIN_BRIGHTNESS; // Minimum brightness at maximum distance
        }
    }

    // --- [FIX] Updated signature to accept WorldManager ---
    public void render(ShapeRenderer shapeRenderer, Player player, Maze maze, Viewport viewport,
                       float[] depthBuffer, FirstPersonRenderer firstPersonRenderer, WorldManager worldManager) {
        if (depthBuffer == null) return;

        // --- [NEW] Get Fog Info ---
        Biome biome = worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId());
        boolean fogEnabled = biome.hasFogOfWar();
        float fogDistance = biome.getFogDistance();
        // --- END NEW ---

        List<Renderable> entities = new ArrayList<>();
        entities.addAll(maze.getItems().values());
        entities.addAll(maze.getMonsters().values());
        entities.addAll(maze.getLadders().values());
        entities.addAll(maze.getProjectiles());
        entities.addAll(maze.getScenery().values());

        entities.sort((a, b) -> Float.compare(
            player.getPosition().dst2(b.getPosition()),
            player.getPosition().dst2(a.getPosition())
        ));

        boolean isShapeRendererActive = false;
        boolean isSpriteBatchActive = false;

        for (Renderable entity : entities) {

            // --- [NEW] FOG CULLING ---
            float distanceToEntity = player.getPosition().dst(entity.getPosition());
            if (fogEnabled && distanceToEntity > fogDistance) {
                continue; // Skip rendering this entity, it's in the fog
            }
            // --- END NEW ---

            boolean needsTexture = (entity instanceof Monster && ((Monster) entity).getTexture() != null) ||
                (entity instanceof Item && ((Item) entity).getTexture() != null);

            if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN && needsTexture) {
                if (isShapeRendererActive) {
                    shapeRenderer.end();
                    isShapeRendererActive = false;
                }
                if (!isSpriteBatchActive) {
                    spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
                    spriteBatch.begin();
                    isSpriteBatchActive = true;
                }

                if (entity instanceof Monster) {
                    drawMonsterTexture(spriteBatch, player, (Monster) entity, viewport, depthBuffer, firstPersonRenderer, maze);
                } else if (entity instanceof Item) {
                    drawItemTexture(spriteBatch, player, (Item) entity, viewport, depthBuffer, firstPersonRenderer, maze);
                }

            } else {
                if (isSpriteBatchActive) {
                    spriteBatch.end();
                    isSpriteBatchActive = false;
                }
                if (!isShapeRendererActive) {
                    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                    isShapeRendererActive = true;
                }
                drawEntityShape(shapeRenderer, player, entity, viewport, depthBuffer, firstPersonRenderer, maze);
            }
        }

        if (isShapeRendererActive) {
            shapeRenderer.end();
        }
        if (isSpriteBatchActive) {
            spriteBatch.end();
        }
    }

    // --- [FIX] Updated signature to accept WorldManager ---
    public void renderSingleMonster(ShapeRenderer shapeRenderer, Player player, Monster monster, Viewport viewport,
                                    float[] depthBuffer, FirstPersonRenderer firstPersonRenderer, Maze maze, WorldManager worldManager) {
        if (depthBuffer == null || monster == null) return;

        // --- [NEW] FOG CULLING ---
        Biome biome = worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId());
        if (biome.hasFogOfWar()) {
            float distanceToMonster = player.getPosition().dst(monster.getPosition());
            if (distanceToMonster > biome.getFogDistance()) {
                return; // Monster is in the fog, don't draw
            }
        }
        // --- END NEW ---

        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN && monster.getTexture() != null) {
            spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
            spriteBatch.begin();
            drawMonsterTexture(spriteBatch, player, monster, viewport, depthBuffer, firstPersonRenderer, maze);
            spriteBatch.end();
        } else {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            drawEntityShape(shapeRenderer, player, monster, viewport, depthBuffer, firstPersonRenderer, maze);
            shapeRenderer.end();
        }
    }

    // Update renderSingleProjectile to accept and use FirstPersonRenderer
    public void renderSingleProjectile(ShapeRenderer shapeRenderer, Player player, Projectile projectile, Viewport viewport,
                                       float[] depthBuffer, FirstPersonRenderer firstPersonRenderer, Maze maze) {
        if (depthBuffer == null || projectile == null) return;
        // Note: We don't fog check projectiles, they should be visible
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawEntityShape(shapeRenderer, player, projectile, viewport, depthBuffer, firstPersonRenderer, maze);
        shapeRenderer.end();
    }

    // Update drawMonsterTexture to use line of sight check
    private void drawMonsterTexture(SpriteBatch spriteBatch, Player player, Monster monster, Viewport viewport,
                                    float[] depthBuffer, FirstPersonRenderer firstPersonRenderer, Maze maze) {
        float spriteX = monster.getPosition().x - player.getPosition().x;
        float spriteY = monster.getPosition().y - player.getPosition().y;
        float invDet = 1.0f / (player.getCameraPlane().x * player.getDirectionVector().y - player.getDirectionVector().x * player.getCameraPlane().y);
        float transformX = invDet * (-player.getDirectionVector().y * spriteX + player.getDirectionVector().x * spriteY);
        float transformY = invDet * (-player.getCameraPlane().y * spriteX + player.getCameraPlane().x * spriteY);

        if (transformY > 0) {
            // Check line of sight to the monster
            float distanceToObstruction = firstPersonRenderer.checkLineOfSight(player, maze, monster.getPosition());
            float distanceToMonster = player.getPosition().dst(monster.getPosition());

            // If there's an obstruction closer than the monster, don't render
            if (distanceToObstruction < distanceToMonster - 0.1f) {
                return;
            }

            Camera camera = viewport.getCamera();
            int screenX = (int) ((camera.viewportWidth / 2) * (1 + transformX / transformY));

            int baseSpriteHeight = (int) Math.abs(camera.viewportHeight / transformY);
            int spriteHeight = (int) (baseSpriteHeight * monster.scale.y);
            int spriteWidth = (int) (baseSpriteHeight * monster.scale.x);

            float drawY = (camera.viewportHeight / 2) - spriteHeight / 2.0f;

            int drawStartX = Math.max(0, screenX - spriteWidth / 2);
            int drawEndX = Math.min((int) viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);

            for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
                // Use transformY (distance to monster) instead of checking depthBuffer
                // The line-of-sight check above already verified no walls block the view
                if (stripe >= 0 && stripe < depthBuffer.length) {
                    float u = (float) (stripe - drawStartX) / (float) spriteWidth;
                    spriteBatch.draw(monster.getTexture(), stripe, drawY, 1, spriteHeight, u, 1, u + (1.0f / spriteWidth), 0);
                }
            }
        }
    }

    // Update drawItemTexture similarly
    private void drawItemTexture(SpriteBatch spriteBatch, Player player, Item item, Viewport viewport,
                                 float[] depthBuffer, FirstPersonRenderer firstPersonRenderer, Maze maze) {
        float spriteX = item.getPosition().x - player.getPosition().x;
        float spriteY = item.getPosition().y - player.getPosition().y;
        float invDet = 1.0f / (player.getCameraPlane().x * player.getDirectionVector().y - player.getDirectionVector().x * player.getCameraPlane().y);
        float transformX = invDet * (-player.getDirectionVector().y * spriteX + player.getDirectionVector().x * spriteY);
        float transformY = invDet * (-player.getCameraPlane().y * spriteX + player.getCameraPlane().x * spriteY);

        // --- [FIX 1] ---
        float distance = (float)Math.sqrt(spriteX * spriteX + spriteY * spriteY);
        boolean atFeet = (distance < 0.1f);

        if (atFeet) {
            transformX = 0.0f; // Center it
            transformY = 0.2f; // Fake a close distance so (transformY > 0) passes
        }
        // --- [END FIX 1] ---

        if (transformY > 0) {
            // Line of sight check
            float distanceToObstruction = firstPersonRenderer.checkLineOfSight(player, maze, item.getPosition());
            float distanceToItem = player.getPosition().dst(item.getPosition());

            if (!atFeet && distanceToObstruction < distanceToItem - 0.1f) {
                return;
            }

            Camera camera = viewport.getCamera();
            int screenX = (int) ((camera.viewportWidth / 2) * (1 + transformX / transformY));

            // --- [FIX 2] Sizing and Y-Position Logic ---
            float drawY;
            int spriteHeight, spriteWidth;

            if (atFeet) {
                // --- THIS IS THE "UNIFORM" FIX ---
                // Hard-code the Y position to be low on the screen
                drawY = camera.viewportHeight / 8; // e.g., 1/8th from the bottom

                // Hard-code the size to be uniform
                spriteHeight = AT_FEET_SPRITE_HEIGHT;
                spriteWidth = AT_FEET_SPRITE_HEIGHT; // Make it square for simplicity
            } else {
                // This is the ORIGINAL logic for all other items
                int wallLineHeightAtSameDist = (int) (camera.viewportHeight / transformY);
                float floorY = (camera.viewportHeight / 2) - (wallLineHeightAtSameDist / 2f);

                int baseSpriteHeight = Math.abs((int) (camera.viewportHeight / transformY)) / 2;
                spriteHeight = (int)(baseSpriteHeight * item.scale.y);
                spriteWidth = (int)(baseSpriteHeight * item.scale.x);
                drawY = floorY;

                // Original boost logic for adjacent items
                int playerGridX = (int) player.getPosition().x;
                int playerGridY = (int) player.getPosition().y;
                int itemGridX = (int) item.getPosition().x;
                int itemGridY = (int) item.getPosition().y;
                int manhattanDistance = Math.abs(playerGridX - itemGridX) + Math.abs(playerGridY - itemGridY);
                if (manhattanDistance == 1) {
                    drawY += CLOSE_ITEM_Y_BOOST;
                }
            }
            // --- [END FIX 2] ---

            int drawStartX = Math.max(0, screenX - spriteWidth / 2);
            int drawEndX = Math.min((int) viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);

            for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
                if (stripe >= 0 && stripe < depthBuffer.length) {
                    float u = (float) (stripe - drawStartX) / (float) spriteWidth;
                    if (item.isModified()) {
                        spriteBatch.setColor(GLOW_COLOR_MODERN);
                        spriteBatch.draw(item.getTexture(), stripe - 1, drawY - 1, 1, spriteHeight + 2, u, 1, u + (1.0f / spriteWidth), 0);
                        spriteBatch.setColor(Color.WHITE);
                    }
                    spriteBatch.draw(item.getTexture(), stripe, drawY, 1, spriteHeight, u, 1, u + (1.0f / spriteWidth), 0);
                }
            }
        }
    }

    // Update drawEntityShape similarly
    private void drawEntityShape(ShapeRenderer shapeRenderer, Player player, Renderable entity, Viewport viewport,
                                 float[] depthBuffer, FirstPersonRenderer firstPersonRenderer, Maze maze) {
        float spriteX = entity.getPosition().x - player.getPosition().x;
        float spriteY = entity.getPosition().y - player.getPosition().y;
        float invDet = 1.0f / (player.getCameraPlane().x * player.getDirectionVector().y - player.getDirectionVector().x * player.getCameraPlane().y);
        float transformX = invDet * (-player.getDirectionVector().y * spriteX + player.getDirectionVector().x * spriteY);
        float transformY = invDet * (-player.getCameraPlane().y * spriteX + player.getCameraPlane().x * spriteY);

        // --- [FIX] ---
        // Check for "at-feet" case for items
        float distance = (float)Math.sqrt(spriteX * spriteX + spriteY * spriteY);
        boolean atFeet = ((entity instanceof Item || entity instanceof Ladder) && distance < 0.1f);
        if (atFeet) {
            transformX = 0.0f; // Center it
            transformY = 0.2f; // Fake a close distance
        }
        // --- [END FIX] ---

        if (transformY > 0) {
            // Check line of sight
            float distanceToObstruction = firstPersonRenderer.checkLineOfSight(player, maze, entity.getPosition());
            float distanceToEntity = player.getPosition().dst(entity.getPosition());

            // If there's an obstruction closer than the entity, don't render
            // Added 'atFeet' check to prevent culling item we're standing on
            if (!atFeet && distanceToObstruction < distanceToEntity - 0.1f) {
                return;
            }

            Camera camera = viewport.getCamera();
            int screenX = (int) ((camera.viewportWidth / 2) * (1 + transformX / transformY));

            if (entity instanceof Monster) {
                drawMonsterSprite(shapeRenderer, (Monster) entity, screenX, transformY, camera, viewport, depthBuffer);
            } else if (entity instanceof Item) {
                // --- [FIX] Pass the 'atFeet' boolean to drawItemSprite ---
                drawItemSprite(shapeRenderer, player, (Item) entity, screenX, transformY, camera, viewport, depthBuffer, atFeet);
            } else if (entity instanceof Ladder) {
                drawLadderSprite(shapeRenderer, (Ladder) entity, screenX, transformY, camera, viewport, depthBuffer, atFeet);
            } else if (entity instanceof Projectile) {
                drawProjectile(shapeRenderer, (Projectile) entity, screenX, transformY, camera, viewport, depthBuffer);
            }
            else if (entity instanceof Scenery) {
                drawScenerySprite(shapeRenderer, (Scenery) entity, screenX, transformY, camera, viewport, depthBuffer);
            }
        }
    }
    private void drawProjectile(ShapeRenderer shapeRenderer, Projectile projectile, int screenX, float transformY,
                                Camera camera, Viewport viewport, float[] depthBuffer) {
        float floorOffset = 0.5f;
        int spriteScreenY = (int) (camera.viewportHeight / 2 * (1 + floorOffset / transformY));

        int spriteHeight = Math.abs((int) (camera.viewportHeight / transformY)) / 4;
        int spriteWidth = spriteHeight;

        // Get sprite data from the projectile
        String[] spriteData = projectile.getSpriteData();

        if (spriteData == null) {
            Gdx.app.log("EntityRenderer", "Sprite data is null, using DART as fallback");
            spriteData = ItemSpriteData.DART;
        }

        Gdx.app.log("EntityRenderer", "Drawing projectile with sprite data");

        int spritePixelHeight = spriteData.length;
        int spritePixelWidth = spriteData[0].length();

        float pixelWidth = (float) spriteWidth / spritePixelWidth;
        float pixelHeight = (float) spriteHeight / spritePixelHeight;

        float totalWidth = spritePixelWidth * pixelWidth;
        float totalHeight = spritePixelHeight * pixelHeight;
        float drawStartX = screenX - totalWidth / 2;
        float drawStartY = spriteScreenY - totalHeight / 2;

        for (int px = 0; px < spritePixelWidth; px++) {
            float currentX = drawStartX + px * pixelWidth;
            int screenStripe = (int) currentX;

            if (screenStripe >= 0 && screenStripe < viewport.getScreenWidth() && transformY < depthBuffer[screenStripe]) {
                for (int py = 0; py < spritePixelHeight; py++) {
                    if (spriteData[py].charAt(px) == '#') {
                        float currentY = drawStartY + (spritePixelHeight - 1 - py) * pixelHeight;
                        shapeRenderer.setColor(projectile.getColor());
                        shapeRenderer.rect(currentX, currentY, pixelWidth, pixelHeight);
                    }
                }
            }
        }
    }

    private void drawMonsterSprite(ShapeRenderer shapeRenderer, Monster monster, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer) {
        int spriteScreenY = (int)(camera.viewportHeight / 2); // Center on horizon

        // Base size is the height of a wall at this distance
        int baseSpriteHeight = (int) Math.abs(camera.viewportHeight / transformY);

        // Apply the monster's specific x and y scale
        int spriteHeight = (int) (baseSpriteHeight * monster.scale.y);
        int spriteWidth = (int) (baseSpriteHeight * monster.scale.x);

        // Keep vertical centering logic
        float drawY = spriteScreenY - spriteHeight / 2.0f;

        if (monster.getSpriteData() != null) {
            drawAsciiSprite(shapeRenderer, monster, monster.getSpriteData(), screenX, transformY, camera, viewport, depthBuffer, spriteWidth, spriteHeight, drawY);
        } else {
            // Fallback for monsters without sprite data
            int drawStartX = Math.max(0, screenX - spriteWidth / 2);
            int drawEndX = Math.min((int)viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);
            for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
                if (transformY < depthBuffer[stripe]) {
                    shapeRenderer.setColor(applyTorchLighting(monster.getColor(), WorldConstants.TORCH_FADE_END, new Color()));

                    shapeRenderer.rect(stripe, drawY, 1, spriteHeight);
                }
            }
        }
    }

    /**
     * NEW: Draws a Scenery sprite using the retro ASCII method.
     */
    private void drawScenerySprite(ShapeRenderer shapeRenderer, Scenery scenery, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer) {
        // --- MODIFIED ---
        // Base size is the height of a wall at this distance
        int baseSpriteHeight = (int) (camera.viewportHeight / transformY);

        // Apply the scenery's specific x and y scale
        int spriteHeight = (int) (baseSpriteHeight * scenery.getScale().y);
        int spriteWidth = (int) (baseSpriteHeight * scenery.getScale().x);
        // --- END MODIFIED ---

        // For bushes, they should be on the floor. Trees/Rocks are full height.
        float drawY;
        if (scenery.getType() == Scenery.SceneryType.BUSH) {
            float floorY = (camera.viewportHeight / 2) - (baseSpriteHeight / 2f); // Use base height for floor calculation
            drawY = floorY;
            // The scale is already applied, so no need to divide here
        } else {
            // Center trees and rocks vertically
            drawY = (camera.viewportHeight / 2) - spriteHeight / 2.0f;
        }

        String[] spriteData = scenery.getSpriteData();
        if (spriteData != null) {
            drawAsciiSprite(shapeRenderer, scenery, spriteData, screenX, transformY, camera, viewport, depthBuffer, spriteWidth, spriteHeight, drawY);
        }
    }

    // --- [FIX] METHOD SIGNATURE CHANGED ---
    // Added 'boolean atFeet' parameter
    private void drawItemSprite(ShapeRenderer shapeRenderer, Player player, Item item, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer, boolean atFeet) {

        // --- [FIX 2] Sizing and Y-Position Logic ---
        float drawY;
        int spriteHeight, spriteWidth;

        if (atFeet) {
            // --- THIS IS THE "UNIFORM" FIX ---
            // Hard-code the Y position to be low on the screen
            drawY = camera.viewportHeight / 8; // e.g., 1/8th from the bottom

            // Hard-code the size to be uniform
            spriteHeight = AT_FEET_SPRITE_HEIGHT;
            // Respect the retro renderer's 2:1 width ratio
            spriteWidth = AT_FEET_SPRITE_HEIGHT / 2;
        } else {
            // This is the ORIGINAL logic for all other items
            int wallLineHeightAtSameDist = (int) (camera.viewportHeight / transformY);
            float floorY = (camera.viewportHeight / 2) - (wallLineHeightAtSameDist / 2f);

            int baseSpriteHeight = Math.abs((int) (camera.viewportHeight / transformY)) / 2;
            spriteHeight = (int)(baseSpriteHeight * item.scale.y);
            spriteWidth = (int)((baseSpriteHeight / 2) * item.scale.x);
            drawY = floorY;

            // Original boost logic for adjacent items
            int playerGridX = (int) player.getPosition().x;
            int playerGridY = (int) player.getPosition().y;
            int itemGridX = (int) item.getPosition().x;
            int itemGridY = (int) item.getPosition().y;

            if (Math.abs(playerGridX - itemGridX) + Math.abs(playerGridY - itemGridY) == 1) {
                drawY += CLOSE_ITEM_Y_BOOST;
            }
        }
        // --- [END FIX 2] ---

        if (item.getSpriteData() != null && item.getSpriteData().length == 0) {
            System.err.println("ERROR: Item type " + item.getType() + " has empty sprite data!");
        }

        if (item.getSpriteData() != null  && item.getSpriteData().length > 0)  {
            drawAsciiSprite(shapeRenderer, item, item.getSpriteData(), screenX, transformY, camera, viewport, depthBuffer, spriteWidth, spriteHeight, drawY);
        } else {
            // Fallback
            int drawStartX = Math.max(0, screenX - spriteWidth / 2);
            int drawEndX = Math.min((int)viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);
            for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
                if (transformY < depthBuffer[stripe]) {
                    if (item.isModified()) {
                        shapeRenderer.setColor(GLOW_COLOR_RETRO);
                        shapeRenderer.rect(stripe - 1, drawY - 1, 1, spriteHeight + 2);
                    }
                    shapeRenderer.setColor(item.getColor());
                    shapeRenderer.rect(stripe, drawY, 1, spriteHeight);
                }
            }
        }
    }

    private void drawLadderSprite(ShapeRenderer shapeRenderer, Ladder ladder, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer, boolean atFeet) {

        float drawY;
        int spriteHeight;
        int spriteWidth;

        if (atFeet) {
            // Use the same "at-feet" logic as items for consistency
            drawY = camera.viewportHeight / 8; // 1/8th from the bottom

            // Hard-code the size
            spriteHeight = AT_FEET_SPRITE_HEIGHT;
            spriteWidth = (int) (AT_FEET_SPRITE_HEIGHT * 0.5f); // Keep 0.5 ratio
        } else {
            // Original logic for ladders not at feet
            spriteHeight = (int) (camera.viewportHeight / transformY);
            float floorY = (camera.viewportHeight / 2) - (spriteHeight / 2f);
            drawY = floorY;
            spriteWidth = (int) (spriteHeight * 0.5f);
        }

        String[] spriteData = ItemSpriteData.getSpriteByType("LADDER");
        if (spriteData != null) {
            drawAsciiSprite(shapeRenderer, ladder, spriteData, screenX, transformY, camera, viewport, depthBuffer, spriteWidth, spriteHeight, drawY);
        }
    }

    private void drawAsciiSprite(ShapeRenderer shapeRenderer, Renderable entity, String[] spriteData, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer, int spriteWidth, int spriteHeight, float drawY) {
        int drawStartX = Math.max(0, screenX - spriteWidth / 2);
        int drawEndX = Math.min((int) viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);

        boolean isModifiedItem = (entity instanceof Item && ((Item) entity).isModified());
        if (isModifiedItem) {
            for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
                if (transformY > 0 && stripe > 0 && stripe < viewport.getScreenWidth() && transformY < depthBuffer[stripe]) {

                    int texX = (int)(((double)(stripe - (screenX - spriteWidth/2)) / spriteWidth) * 24.0);

                    for (int y = 0; y < spriteHeight; y++) {
                        int screenY = (int)drawY + y;
                        if(screenY < 0 || screenY >= viewport.getScreenHeight()) continue;

                        int texY = 23 - (int)(((double)y / spriteHeight) * 24.0);

                        if (texX >= 0 && texX < 24 && texY >= 0 && texY < 24) {
                            if (spriteData[texY].charAt(texX) == '#') {
                                shapeRenderer.setColor(GLOW_COLOR_RETRO);
                                // Draw 1px glow pixels around the main pixel
                                shapeRenderer.rect(stripe - 1, screenY, 1, 1);
                                shapeRenderer.rect(stripe + 1, screenY, 1, 1);
                                shapeRenderer.rect(stripe, screenY - 1, 1, 1);
                                shapeRenderer.rect(stripe, screenY + 1, 1, 1);
                            }
                        }
                    }
                }
            }
        }

        for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
            if (transformY > 0 && stripe > 0 && stripe < viewport.getScreenWidth() && transformY < depthBuffer[stripe]) {

                int texX = (int)(((double)(stripe - (screenX - spriteWidth/2)) / spriteWidth) * 24.0);

                for (int y = 0; y < spriteHeight; y++) {
                    int screenY = (int)drawY + y;
                    if(screenY < 0 || screenY >= viewport.getScreenHeight()) continue;

                    // --- VERTICAL FLIP LOGIC ---
                    int texY = 23 - (int)(((double)y / spriteHeight) * 24.0);

                    if (texX >= 0 && texX < 24 && texY >= 0 && texY < 24) {
                        if (spriteData[texY].charAt(texX) == '#') {
                            shapeRenderer.setColor(applyTorchLighting(entity.getColor(), WorldConstants.TORCH_FADE_END, new Color()));

                            shapeRenderer.rect(stripe, screenY, 1, 1);
                        }
                    }
                }
            }
        }
    }
    public void dispose() { spriteBatch.dispose(); }
}
