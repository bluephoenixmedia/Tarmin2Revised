package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.WorldConstants;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.managers.WorldManager;

import java.util.ArrayList;
import java.util.List;

public class EntityRenderer {

    private final SpriteBatch spriteBatch;
    private final Matrix4 transformMatrix = new Matrix4();
    private static final float CLOSE_ITEM_Y_BOOST = 140.0f;
    private final DebugManager debugManager = DebugManager.getInstance();

    private static final Color GLOW_COLOR_MODERN = new Color(1.0f, 0.9f, 0.2f, 0.4f);
    private static final Color GLOW_COLOR_RETRO = Color.GOLD;

    private static final int AT_FEET_SPRITE_HEIGHT = 200;

    private final ItemDataManager itemDataManager;

    public EntityRenderer(ItemDataManager itemDataManager) {
        this.spriteBatch = new SpriteBatch();
        this.itemDataManager = itemDataManager;
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

    public void render(ShapeRenderer shapeRenderer, Player player, Maze maze, Viewport viewport,
                       float[] depthBuffer, FirstPersonRenderer firstPersonRenderer, WorldManager worldManager) {
        if (depthBuffer == null) return;

        Biome biome = worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId());
        boolean fogEnabled = biome.hasFogOfWar();
        float fogDistance = biome.getFogDistance();

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
            float distanceToEntity = player.getPosition().dst(entity.getPosition());
            if (fogEnabled && distanceToEntity > fogDistance) {
                continue;
            }

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
            isShapeRendererActive = false;
        }
        if (isSpriteBatchActive) {
            spriteBatch.end();
            isSpriteBatchActive = false;
        }

        // Draw Corpses (Debris)
        renderCorpses(shapeRenderer, maze, viewport.getCamera(), player, depthBuffer);

        // Removed: renderBlood call

        // Re-close
        if (spriteBatch.isDrawing()) spriteBatch.end();
    }

    public void renderSingleMonster(ShapeRenderer shapeRenderer, Player player, Monster monster, Viewport viewport,
                                    float[] depthBuffer, FirstPersonRenderer firstPersonRenderer, Maze maze, WorldManager worldManager) {
        if (depthBuffer == null || monster == null) return;

        Biome biome = worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId());
        if (biome.hasFogOfWar()) {
            float distanceToMonster = player.getPosition().dst(monster.getPosition());
            if (distanceToMonster > biome.getFogDistance()) {
                return;
            }
        }

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

    public void renderSingleProjectile(ShapeRenderer shapeRenderer, Player player, Projectile projectile, Viewport viewport,
                                       float[] depthBuffer, FirstPersonRenderer firstPersonRenderer, Maze maze) {
        if (depthBuffer == null || projectile == null) return;
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawEntityShape(shapeRenderer, player, projectile, viewport, depthBuffer, firstPersonRenderer, maze);
        shapeRenderer.end();
    }

    public void renderCorpses(ShapeRenderer shapeRenderer, Maze maze, Camera camera, Player player, float[] depthBuffer) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.setTransformMatrix(new Matrix4()); // Reset to Identity for Screen Space
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float halfWidth = camera.viewportWidth / 2.0f;
        float halfHeight = camera.viewportHeight / 2.0f;

        for (CorpsePart part : maze.getCorpses()) {
            // 1. Raycast Projection Math
            float dx = part.position.x - player.getPosition().x;
            float dy = part.position.z - player.getPosition().y;

            float planeX = player.getCameraPlane().x;
            float planeY = player.getCameraPlane().y;
            float dirX = player.getDirectionVector().x;
            float dirY = player.getDirectionVector().y;

            float invDet = 1.0f / (planeX * dirY - dirX * planeY);
            float transformX = invDet * (-dirY * dx + dirX * dy);
            float transformY = invDet * (-planeY * dx + planeX * dy);

            if (transformY > 0.1f) {
                int screenX = (int) (halfWidth * (1 + transformX / transformY));

                if (screenX >= 0 && screenX < depthBuffer.length && transformY < depthBuffer[screenX]) {
                    // 2. Calculate Scale & Position
                    float spriteScale = Math.abs(camera.viewportHeight / transformY);
                    float screenY = halfHeight + (part.position.y - 0.5f) * spriteScale;

                    // 3. Draw with Rotation
                    Matrix4 originalMatrix = shapeRenderer.getTransformMatrix().cpy();
                    transformMatrix.set(originalMatrix);
                    transformMatrix.translate(screenX, screenY, 0);
                    transformMatrix.rotate(0, 0, 1, part.rotation);
                    shapeRenderer.setTransformMatrix(transformMatrix);

                    float partPixelSize = spriteScale / 24.0f;
                    float startOffset = -(6 * partPixelSize);

                    for (int r = 0; r < 12; r++) {
                        String row = part.spriteQuadrant[r];
                        for (int c = 0; c < 12; c++) {
                            if (row.charAt(c) == '#') {
                                shapeRenderer.setColor(part.color);
                                shapeRenderer.rect(startOffset + c * partPixelSize, startOffset + (11-r) * partPixelSize, partPixelSize, partPixelSize);
                            }
                        }
                    }
                    shapeRenderer.setTransformMatrix(originalMatrix);
                }
            }
        }
        shapeRenderer.end();
    }

    // Removed: renderBlood method

    // --- Existing Helper Methods ---

    private void drawMonsterTexture(SpriteBatch spriteBatch, Player player, Monster monster, Viewport viewport,
                                    float[] depthBuffer, FirstPersonRenderer firstPersonRenderer, Maze maze) {
        float spriteX = monster.getPosition().x - player.getPosition().x;
        float spriteY = monster.getPosition().y - player.getPosition().y;
        float invDet = 1.0f / (player.getCameraPlane().x * player.getDirectionVector().y - player.getDirectionVector().x * player.getCameraPlane().y);
        float transformX = invDet * (-player.getDirectionVector().y * spriteX + player.getDirectionVector().x * spriteY);
        float transformY = invDet * (-player.getCameraPlane().y * spriteX + player.getCameraPlane().x * spriteY);

        if (transformY > 0) {
            float distanceToObstruction = firstPersonRenderer.checkLineOfSight(player, maze, monster.getPosition());
            float distanceToMonster = player.getPosition().dst(monster.getPosition());

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
                if (stripe >= 0 && stripe < depthBuffer.length) {
                    float u = (float) (stripe - drawStartX) / (float) spriteWidth;
                    spriteBatch.draw(monster.getTexture(), stripe, drawY, 1, spriteHeight, u, 1, u + (1.0f / spriteWidth), 0);
                }
            }
        }
    }

    private void drawItemTexture(SpriteBatch spriteBatch, Player player, Item item, Viewport viewport,
                                 float[] depthBuffer, FirstPersonRenderer firstPersonRenderer, Maze maze) {
        float spriteX = item.getPosition().x - player.getPosition().x;
        float spriteY = item.getPosition().y - player.getPosition().y;
        float invDet = 1.0f / (player.getCameraPlane().x * player.getDirectionVector().y - player.getDirectionVector().x * player.getCameraPlane().y);
        float transformX = invDet * (-player.getDirectionVector().y * spriteX + player.getDirectionVector().x * spriteY);
        float transformY = invDet * (-player.getCameraPlane().y * spriteX + player.getCameraPlane().x * spriteY);

        float distance = (float)Math.sqrt(spriteX * spriteX + spriteY * spriteY);
        boolean atFeet = (distance < 0.1f);

        if (atFeet) {
            transformX = 0.0f;
            transformY = 0.2f;
        }

        if (transformY > 0) {
            float distanceToObstruction = firstPersonRenderer.checkLineOfSight(player, maze, item.getPosition());
            float distanceToItem = player.getPosition().dst(item.getPosition());

            if (!atFeet && distanceToObstruction < distanceToItem - 0.1f) {
                return;
            }

            Camera camera = viewport.getCamera();
            int screenX = (int) ((camera.viewportWidth / 2) * (1 + transformX / transformY));

            float drawY;
            int spriteHeight, spriteWidth;

            if (atFeet) {
                drawY = camera.viewportHeight / 8;
                spriteHeight = AT_FEET_SPRITE_HEIGHT;
                spriteWidth = AT_FEET_SPRITE_HEIGHT;
            } else {
                int wallLineHeightAtSameDist = (int) (camera.viewportHeight / transformY);
                float floorY = (camera.viewportHeight / 2) - (wallLineHeightAtSameDist / 2f);

                int baseSpriteHeight = Math.abs((int) (camera.viewportHeight / transformY)) / 2;
                spriteHeight = (int)(baseSpriteHeight * item.getScale().y);
                spriteWidth = (int)(baseSpriteHeight * item.getScale().x);
                drawY = floorY;

                int playerGridX = (int) player.getPosition().x;
                int playerGridY = (int) player.getPosition().y;
                int itemGridX = (int) item.getPosition().x;
                int itemGridY = (int) item.getPosition().y;
                int manhattanDistance = Math.abs(playerGridX - itemGridX) + Math.abs(playerGridY - itemGridY);
                if (manhattanDistance == 1) {
                    drawY += CLOSE_ITEM_Y_BOOST;
                }
            }

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

    private void drawEntityShape(ShapeRenderer shapeRenderer, Player player, Renderable entity, Viewport viewport,
                                 float[] depthBuffer, FirstPersonRenderer firstPersonRenderer, Maze maze) {
        float spriteX = entity.getPosition().x - player.getPosition().x;
        float spriteY = entity.getPosition().y - player.getPosition().y;
        float invDet = 1.0f / (player.getCameraPlane().x * player.getDirectionVector().y - player.getDirectionVector().x * player.getCameraPlane().y);
        float transformX = invDet * (-player.getDirectionVector().y * spriteX + player.getDirectionVector().x * spriteY);
        float transformY = invDet * (-player.getCameraPlane().y * spriteX + player.getCameraPlane().x * spriteY);

        float distance = (float)Math.sqrt(spriteX * spriteX + spriteY * spriteY);
        boolean atFeet = ((entity instanceof Item || entity instanceof Ladder) && distance < 0.1f);
        if (atFeet) {
            transformX = 0.0f;
            transformY = 0.2f;
        }

        if (transformY > 0) {
            float distanceToObstruction = firstPersonRenderer.checkLineOfSight(player, maze, entity.getPosition());
            float distanceToEntity = player.getPosition().dst(entity.getPosition());

            if (!atFeet && distanceToObstruction < distanceToEntity - 0.1f) {
                return;
            }

            Camera camera = viewport.getCamera();
            int screenX = (int) ((camera.viewportWidth / 2) * (1 + transformX / transformY));

            if (entity instanceof Monster) {
                drawMonsterSprite(shapeRenderer, (Monster) entity, screenX, transformY, camera, viewport, depthBuffer);
            } else if (entity instanceof Item) {
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

        String[] spriteData = projectile.getSpriteData();
        if (spriteData == null) {
            spriteData = itemDataManager.getTemplate(Item.ItemType.DART).spriteData;
        }

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
        int spriteScreenY = (int)(camera.viewportHeight / 2);
        int baseSpriteHeight = (int) Math.abs(camera.viewportHeight / transformY);

        int spriteHeight = (int) (baseSpriteHeight * monster.scale.y);
        int spriteWidth = (int) (baseSpriteHeight * monster.scale.x);

        float drawY = spriteScreenY - spriteHeight / 2.0f;

        if (monster.getSpriteData() != null) {
            drawAsciiSprite(shapeRenderer, monster, monster.getSpriteData(), screenX, transformY, camera, viewport, depthBuffer, spriteWidth, spriteHeight, drawY);
        } else {
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

    private void drawScenerySprite(ShapeRenderer shapeRenderer, Scenery scenery, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer) {
        int baseSpriteHeight = (int) (camera.viewportHeight / transformY);
        int spriteHeight = (int) (baseSpriteHeight * scenery.getScale().y);
        int spriteWidth = (int) (baseSpriteHeight * scenery.getScale().x);

        float drawY;
        if (scenery.getType() == Scenery.SceneryType.BUSH) {
            float floorY = (camera.viewportHeight / 2) - (baseSpriteHeight / 2f);
            drawY = floorY;
        } else {
            drawY = (camera.viewportHeight / 2) - spriteHeight / 2.0f;
        }

        String[] spriteData = scenery.getSpriteData();
        if (spriteData != null) {
            drawAsciiSprite(shapeRenderer, scenery, spriteData, screenX, transformY, camera, viewport, depthBuffer, spriteWidth, spriteHeight, drawY);
        }
    }

    private void drawItemSprite(ShapeRenderer shapeRenderer, Player player, Item item, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer, boolean atFeet) {
        float drawY;
        int spriteHeight, spriteWidth;

        if (atFeet) {
            drawY = camera.viewportHeight / 8;
            spriteHeight = AT_FEET_SPRITE_HEIGHT;
            spriteWidth = AT_FEET_SPRITE_HEIGHT / 2;
        } else {
            int wallLineHeightAtSameDist = (int) (camera.viewportHeight / transformY);
            float floorY = (camera.viewportHeight / 2) - (wallLineHeightAtSameDist / 2f);

            int baseSpriteHeight = Math.abs((int) (camera.viewportHeight / transformY)) / 2;
            spriteHeight = (int)(baseSpriteHeight * item.getScale().y);
            spriteWidth = (int)((baseSpriteHeight / 2) * item.getScale().x);
            drawY = floorY;

            int playerGridX = (int) player.getPosition().x;
            int playerGridY = (int) player.getPosition().y;
            int itemGridX = (int) item.getPosition().x;
            int itemGridY = (int) item.getPosition().y;

            if (Math.abs(playerGridX - itemGridX) + Math.abs(playerGridY - itemGridY) == 1) {
                drawY += CLOSE_ITEM_Y_BOOST;
            }
        }

        if (item.getSpriteData() != null  && item.getSpriteData().length > 0)  {
            drawAsciiSprite(shapeRenderer, item, item.getSpriteData(), screenX, transformY, camera, viewport, depthBuffer, spriteWidth, spriteHeight, drawY);
        } else {
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
            drawY = camera.viewportHeight / 8;
            spriteHeight = AT_FEET_SPRITE_HEIGHT;
            spriteWidth = (int) (AT_FEET_SPRITE_HEIGHT * 0.5f);
        } else {
            spriteHeight = (int) (camera.viewportHeight / transformY);
            float floorY = (camera.viewportHeight / 2) - (spriteHeight / 2f);
            drawY = floorY;
            spriteWidth = (int) (spriteHeight * 0.5f);
        }

        String[] spriteData = itemDataManager.getTemplate(Item.ItemType.LADDER).spriteData;

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

    public void dispose() {
        spriteBatch.dispose();
    }
}
