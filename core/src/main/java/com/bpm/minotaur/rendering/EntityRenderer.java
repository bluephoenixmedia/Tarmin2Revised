// Path: core/src/main/java/com/bpm/minotaur/rendering/EntityRenderer.java
package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.*;

import java.util.ArrayList;
import java.util.List;

public class EntityRenderer {

    private final SpriteBatch spriteBatch;

    public EntityRenderer() {
        this.spriteBatch = new SpriteBatch();
    }

    public void render(ShapeRenderer shapeRenderer, Player player, Maze maze, Viewport viewport, float[] depthBuffer) {
        if (depthBuffer == null) return;

        List<Renderable> entities = new ArrayList<>();
        entities.addAll(maze.getItems().values());
        entities.addAll(maze.getMonsters().values());
        entities.addAll(maze.getLadders().values());
        entities.addAll(maze.getProjectiles());

        entities.sort((a, b) -> Float.compare(
            player.getPosition().dst2(b.getPosition()),
            player.getPosition().dst2(a.getPosition())
        ));

        boolean isShapeRendererActive = false;
        boolean isSpriteBatchActive = false;

        for (Renderable entity : entities) {
            // Update this condition to check for textured items as well
            boolean needsTexture = (entity instanceof Monster && ((Monster) entity).getTexture() != null) ||
                (entity instanceof Item && ((Item) entity).getTexture() != null);

            if (needsTexture) {
                if (isShapeRendererActive) {
                    shapeRenderer.end();
                    isShapeRendererActive = false;
                }
                if (!isSpriteBatchActive) {
                    spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
                    spriteBatch.begin();
                    isSpriteBatchActive = true;
                }

                // Call the correct drawing method based on entity type
                if (entity instanceof Monster) {
                    drawEntityTexture(spriteBatch, player, (Monster) entity, viewport, depthBuffer);
                } else if (entity instanceof Item) {
                    drawEntityTexture(spriteBatch, player, (Item) entity, viewport, depthBuffer);
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
                drawEntityShape(shapeRenderer, player, entity, viewport, depthBuffer);
            }
        }

        if (isShapeRendererActive) {
            shapeRenderer.end();
        }
        if (isSpriteBatchActive) {
            spriteBatch.end();
        }
    }

    public void renderSingleMonster(ShapeRenderer shapeRenderer, Player player, Monster monster, Viewport viewport, float[] depthBuffer) {
        if (depthBuffer == null || monster == null) return;

        if (monster.getTexture() != null) {
            spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
            spriteBatch.begin();
            drawEntityTexture(spriteBatch, player, monster, viewport, depthBuffer);
            spriteBatch.end();
        } else {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            drawEntityShape(shapeRenderer, player, monster, viewport, depthBuffer);
            shapeRenderer.end();
        }
    }

    public void renderSingleProjectile(ShapeRenderer shapeRenderer, Player player, Projectile projectile, Viewport viewport, float[] depthBuffer) {
        if (depthBuffer == null || projectile == null) return;
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawEntityShape(shapeRenderer, player, projectile, viewport, depthBuffer);
        shapeRenderer.end();
    }

    private void drawEntityTexture(SpriteBatch spriteBatch, Player player, Monster monster, Viewport viewport, float[] depthBuffer) {
        float spriteX = monster.getPosition().x - player.getPosition().x;
        float spriteY = monster.getPosition().y - player.getPosition().y;
        float invDet = 1.0f / (player.getCameraPlane().x * player.getDirectionVector().y - player.getDirectionVector().x * player.getCameraPlane().y);
        float transformX = invDet * (player.getDirectionVector().y * spriteX - player.getDirectionVector().x * spriteY);
        float transformY = invDet * (-player.getCameraPlane().y * spriteX + player.getCameraPlane().x * spriteY);

        if (transformY > 0) {
            Camera camera = viewport.getCamera();
            int screenX = (int) ((camera.viewportWidth / 2) * (1 + transformX / transformY));

            int spriteHeight = (int) (Math.abs(camera.viewportHeight / transformY) * 0.8f);
            int spriteWidth = spriteHeight;
            float drawY = (camera.viewportHeight / 2) - spriteHeight / 2.0f;
            int drawStartX = Math.max(0, screenX - spriteWidth / 2);
            int drawEndX = Math.min((int) viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);

            for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
                if (transformY < depthBuffer[stripe]) {
                    float u = (float) (stripe - drawStartX) / (float) spriteWidth;
                    spriteBatch.draw(monster.getTexture(), stripe, drawY, 1, spriteHeight, u, 1, u + (1.0f / spriteWidth), 0);
                }
            }
        }
    }

    private void drawEntityShape(ShapeRenderer shapeRenderer, Player player, Renderable entity, Viewport viewport, float[] depthBuffer) {
        float spriteX = entity.getPosition().x - player.getPosition().x;
        float spriteY = entity.getPosition().y - player.getPosition().y;

        float invDet = 1.0f / (player.getCameraPlane().x * player.getDirectionVector().y - player.getDirectionVector().x * player.getCameraPlane().y);

        float transformX = invDet * (player.getDirectionVector().y * spriteX - player.getDirectionVector().x * spriteY);
        float transformY = invDet * (-player.getCameraPlane().y * spriteX + player.getCameraPlane().x * spriteY);

        if (transformY > 0) {
            Camera camera = viewport.getCamera();
            int screenX = (int) ((camera.viewportWidth / 2) * (1 + transformX / transformY));

            if (entity instanceof Monster) {
                drawMonsterSprite(shapeRenderer, (Monster) entity, screenX, transformY, camera, viewport, depthBuffer);
            } else if (entity instanceof Item) {
                Item item = (Item) entity;
                if (item.getCategory() == Item.ItemCategory.CONTAINER) {
                    drawContainerSprite(shapeRenderer, item, screenX, transformY, camera, viewport, depthBuffer);
                } else {
                    drawItemSprite(shapeRenderer, item, screenX, transformY, camera, viewport, depthBuffer);
                }
            }  else if (entity instanceof Ladder) {
                drawLadderSprite(shapeRenderer, (Ladder) entity, screenX, transformY, camera, viewport, depthBuffer);
            } else if (entity instanceof Projectile) {
                drawProjectile(shapeRenderer, (Projectile) entity, screenX, transformY, camera, viewport, depthBuffer);
            }
        }
    }

    private void drawProjectile(ShapeRenderer shapeRenderer, Projectile projectile, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer) {
        float floorOffset = 0.5f;
        int spriteScreenY = (int) (camera.viewportHeight / 2 * (1 + floorOffset / transformY));
        int spriteHeight = Math.abs((int) (camera.viewportHeight / transformY)) / 4;
        int spriteWidth = spriteHeight;
        float drawY = spriteScreenY - spriteHeight / 2.0f;
        int drawStartX = Math.max(0, screenX - spriteWidth / 2);
        int drawEndX = Math.min((int) viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);

        for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
            if (transformY < depthBuffer[stripe]) {
                shapeRenderer.setColor(projectile.getColor());
                shapeRenderer.rect(stripe, drawY, 1, spriteHeight);
            }
        }
    }

    private void drawEntityTexture(SpriteBatch spriteBatch, Player player, Item item, Viewport viewport, float[] depthBuffer) {
        float spriteX = item.getPosition().x - player.getPosition().x;
        float spriteY = item.getPosition().y - player.getPosition().y;
        float invDet = 1.0f / (player.getCameraPlane().x * player.getDirectionVector().y - player.getDirectionVector().x * player.getCameraPlane().y);
        float transformX = invDet * (player.getDirectionVector().y * spriteX - player.getDirectionVector().x * spriteY);
        float transformY = invDet * (-player.getCameraPlane().y * spriteX + player.getCameraPlane().x * spriteY);

        if (transformY > 0) {
            Camera camera = viewport.getCamera();
            int screenX = (int) ((camera.viewportWidth / 2) * (1 + transformX / transformY));

            // Item-specific positioning and scaling
            float floorOffset = 0.4f; // Sits on the floor
            int spriteScreenY = (int)(camera.viewportHeight / 2 * (1 + floorOffset / transformY));
            int spriteHeight = Math.abs((int) (camera.viewportHeight / transformY)) / 2; // Items are smaller than monsters
            int spriteWidth = spriteHeight; // Make it square
            float drawY = spriteScreenY - spriteHeight; // Position it on the floor

            int drawStartX = Math.max(0, screenX - spriteWidth / 2);
            int drawEndX = Math.min((int) viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);

            for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
                if (transformY < depthBuffer[stripe]) {
                    float u = (float) (stripe - drawStartX) / (float) spriteWidth;
                    spriteBatch.draw(item.getTexture(), stripe, drawY, 1, spriteHeight, u, 1, u + (1.0f / spriteWidth), 0);
                }
            }
        }
    }
    private void drawMonsterSprite(ShapeRenderer shapeRenderer, Monster monster, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer) {
        float floorOffset = 0.0f;
        int spriteScreenY = (int)(camera.viewportHeight / 2 * (1 + floorOffset / transformY));
        int spriteHeight = (int) (Math.abs((int) (camera.viewportHeight / transformY)) * 0.8f);
        int spriteWidth = spriteHeight;
        float drawY = spriteScreenY - spriteHeight / 2.0f;

        if (monster.getSpriteData() != null) {
            drawAsciiSprite(shapeRenderer, monster, monster.getSpriteData(), screenX, transformY, camera, viewport, depthBuffer, spriteWidth, spriteHeight, drawY);
        } else {
            // Fallback for monsters without sprite data
            int drawStartX = Math.max(0, screenX - spriteWidth / 2);
            int drawEndX = Math.min((int)viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);
            for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
                if (transformY < depthBuffer[stripe]) {
                    shapeRenderer.setColor(monster.getColor());
                    shapeRenderer.rect(stripe, drawY, 1, spriteHeight);
                }
            }
        }
    }

    private void drawItemSprite(ShapeRenderer shapeRenderer, Item item, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer) {
        float floorOffset = 0.4f;
        int spriteScreenY = (int)(camera.viewportHeight / 2 * (1 + floorOffset / transformY));
        int spriteHeight = Math.abs((int) (camera.viewportHeight / transformY)) / 2;
        int spriteWidth = spriteHeight / 2;
        float drawY = spriteScreenY - spriteHeight;

        if (item.getSpriteData() != null) {
            drawAsciiSprite(shapeRenderer, item, item.getSpriteData(), screenX, transformY, camera, viewport, depthBuffer, spriteWidth, spriteHeight, drawY);
        } else {
            // This is the original code for drawing potions, which we keep as a fallback
            int drawStartX = Math.max(0, screenX - spriteWidth / 2);
            int drawEndX = Math.min((int)viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);

            for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
                if (transformY < depthBuffer[stripe]) {
                    shapeRenderer.setColor(item.getColor());
                    shapeRenderer.rect(stripe, drawY, 1, spriteHeight);

                    if (item.getType() == Item.ItemType.POTION_HEALING || item.getType() == Item.ItemType.POTION_STRENGTH) {
                        if (item.getLiquidColor() != null) {
                            shapeRenderer.setColor(item.getLiquidColor());
                            shapeRenderer.rect(stripe, drawY + spriteHeight * 0.1f, 1, spriteHeight * 0.5f);
                        }
                        shapeRenderer.setColor(item.getColor().cpy().mul(0.7f));
                        shapeRenderer.rect(stripe, drawY + spriteHeight, 1, spriteHeight * 0.2f);
                    }
                }
            }
        }
    }

    private void drawContainerSprite(ShapeRenderer shapeRenderer, Item container, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer) {
        float floorOffset = 0.4f; // On the floor
        int spriteScreenY = (int)(camera.viewportHeight / 2 * (1 + floorOffset / transformY));
        int spriteHeight = Math.abs((int) (camera.viewportHeight / transformY)) / 2;
        int spriteWidth = spriteHeight; // Make it square like a box/chest
        float drawY = spriteScreenY - spriteHeight; // Sit it on the floor
        int drawStartX = Math.max(0, screenX - spriteWidth / 2);
        int drawEndX = Math.min((int)viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);

        for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
            if (transformY < depthBuffer[stripe]) {
                shapeRenderer.setColor(container.getColor());
                shapeRenderer.rect(stripe, drawY, 1, spriteHeight);
            }
        }
    }

    private void drawLadderSprite(ShapeRenderer shapeRenderer, Ladder ladder, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer) {
        float floorOffset = 0.5f;
        int spriteScreenY = (int)(camera.viewportHeight / 2 * (1 + floorOffset / transformY));
        int spriteHeight = (int) (camera.viewportHeight / transformY);
        int spriteWidth = (int) (spriteHeight * 0.5f);

        float drawY = spriteScreenY - spriteHeight;

        int drawStartX = Math.max(0, screenX - spriteWidth / 2);
        int drawEndX = Math.min((int)viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);

        for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
            if (transformY < depthBuffer[stripe]) {
                shapeRenderer.setColor(ladder.getColor());
                if (stripe == drawStartX || stripe == drawEndX - 1) {
                    shapeRenderer.rect(stripe, drawY, 1, spriteHeight);
                }
                for (int i = 1; i < 5; i++) {
                    shapeRenderer.rect(stripe, drawY + (spriteHeight * (i/5.0f)), 1, 3);
                }
            }
        }
    }
    private void drawAsciiSprite(ShapeRenderer shapeRenderer, Renderable entity, String[] spriteData, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer, int spriteWidth, int spriteHeight, float drawY) {
        int drawStartX = Math.max(0, screenX - spriteWidth / 2);
        int drawEndX = Math.min((int)viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);

        int spritePixelHeight = spriteData.length;
        int spritePixelWidth = spriteData[0].length();
        float pixelHeight = (float)spriteHeight / spritePixelHeight;
        float pixelWidth = (float)spriteWidth / spritePixelWidth;

        for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
            if (transformY < depthBuffer[stripe]) {
                int spriteColumn = (int)((stripe - (screenX - spriteWidth / 2.0f)) / pixelWidth);

                if (spriteColumn >= 0 && spriteColumn < spritePixelWidth) {
                    for (int y = 0; y < spritePixelHeight; y++) {
                        if (spriteData[y].charAt(spriteColumn) != ' ') {
                            shapeRenderer.setColor(entity.getColor());
                            float pixelY = drawY + (spritePixelHeight - 1 - y) * pixelHeight;
                            shapeRenderer.rect(stripe, pixelY, 1, pixelHeight);
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
