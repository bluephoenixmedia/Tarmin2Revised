// Path: core/src/main/java/com/bpm/minotaur/rendering/EntityRenderer.java
package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.managers.DebugManager;

import java.util.ArrayList;
import java.util.List;

public class EntityRenderer {

    private final SpriteBatch spriteBatch;
    private static final float MONSTER_TEXTURE_SCALE = 1.0f;
    private static final float CLOSE_ITEM_Y_BOOST = 140.0f;
    private final DebugManager debugManager = DebugManager.getInstance();

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
                    drawMonsterTexture(spriteBatch, player, (Monster) entity, viewport, depthBuffer);
                } else if (entity instanceof Item) {
                    drawItemTexture(spriteBatch, player, (Item) entity, viewport, depthBuffer);
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

        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN && monster.getTexture() != null) {
            spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
            spriteBatch.begin();
            drawMonsterTexture(spriteBatch, player, monster, viewport, depthBuffer);
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

    private void drawMonsterTexture(SpriteBatch spriteBatch, Player player, Monster monster, Viewport viewport, float[] depthBuffer) {
        float spriteX = monster.getPosition().x - player.getPosition().x;
        float spriteY = monster.getPosition().y - player.getPosition().y;
        float invDet = 1.0f / (player.getCameraPlane().x * player.getDirectionVector().y - player.getDirectionVector().x * player.getCameraPlane().y);
        float transformX = invDet * (player.getDirectionVector().y * spriteX - player.getDirectionVector().x * spriteY);
        float transformY = invDet * (-player.getCameraPlane().y * spriteX + player.getCameraPlane().x * spriteY);

        if (transformY > 0) {
            Camera camera = viewport.getCamera();
            int screenX = (int) ((camera.viewportWidth / 2) * (1 + transformX / transformY));

            // Base size is the height of a wall at this distance
            int baseSpriteHeight = (int) Math.abs(camera.viewportHeight / transformY);

            // Apply the monster's specific x and y scale
            int spriteHeight = (int) (baseSpriteHeight * monster.scale.y);
            int spriteWidth = (int) (baseSpriteHeight * monster.scale.x);

            // Keep vertical centering logic
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

    private void drawItemTexture(SpriteBatch spriteBatch, Player player, Item item, Viewport viewport, float[] depthBuffer) {
        float spriteX = item.getPosition().x - player.getPosition().x;
        float spriteY = item.getPosition().y - player.getPosition().y;
        float invDet = 1.0f / (player.getCameraPlane().x * player.getDirectionVector().y - player.getDirectionVector().x * player.getCameraPlane().y);
        float transformX = invDet * (player.getDirectionVector().y * spriteX - player.getDirectionVector().x * spriteY);
        float transformY = invDet * (-player.getCameraPlane().y * spriteX + player.getCameraPlane().x * spriteY);

        if (transformY > 0) {
            Camera camera = viewport.getCamera();
            int screenX = (int) ((camera.viewportWidth / 2) * (1 + transformX / transformY));

            int wallLineHeightAtSameDist = (int) (camera.viewportHeight / transformY);
            float floorY = (camera.viewportHeight / 2) - (wallLineHeightAtSameDist / 2f);

            int baseSpriteHeight = Math.abs((int) (camera.viewportHeight / transformY)) / 2;
            int spriteHeight = (int)(baseSpriteHeight * item.scale.y);
            int spriteWidth = (int)(baseSpriteHeight * item.scale.x);

            float drawY = floorY;

            int playerGridX = (int) player.getPosition().x;
            int playerGridY = (int) player.getPosition().y;
            int itemGridX = (int) item.getPosition().x;
            int itemGridY = (int) item.getPosition().y;

            if (Math.abs(playerGridX - itemGridX) + Math.abs(playerGridY - itemGridY) == 1) {
                // System.out.println("Triggered CLOSE ITEM BOOST for draw item texture");
                drawY += CLOSE_ITEM_Y_BOOST;
            }

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
                drawItemSprite(shapeRenderer, player, (Item) entity, screenX, transformY, camera, viewport, depthBuffer);
            }  else if (entity instanceof Ladder) {
                drawLadderSprite(shapeRenderer, (Ladder) entity, screenX, transformY, camera, viewport, depthBuffer);
            } else if (entity instanceof Projectile) {
                drawProjectile(shapeRenderer, (Projectile) entity, screenX, transformY, camera, viewport, depthBuffer);
            }
        }
    }

    private void drawProjectile(ShapeRenderer shapeRenderer, Projectile projectile, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer) {
        // ... (This method is unchanged)
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
                    shapeRenderer.setColor(monster.getColor());
                    shapeRenderer.rect(stripe, drawY, 1, spriteHeight);
                }
            }
        }
    }

    // --- METHOD SIGNATURE CORRECTED ---
    private void drawItemSprite(ShapeRenderer shapeRenderer, Player player, Item item, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer) {
        int wallLineHeightAtSameDist = (int) (camera.viewportHeight / transformY);
        float floorY = (camera.viewportHeight / 2) - (wallLineHeightAtSameDist / 2f);

        int baseSpriteHeight = Math.abs((int) (camera.viewportHeight / transformY)) / 2;
        int spriteHeight = (int)(baseSpriteHeight * item.scale.y);
        int spriteWidth = (int)((baseSpriteHeight / 2) * item.scale.x);

        float drawY = floorY;

        int playerGridX = (int) player.getPosition().x;
        int playerGridY = (int) player.getPosition().y;
        int itemGridX = (int) item.getPosition().x;
        int itemGridY = (int) item.getPosition().y;

        if (Math.abs(playerGridX - itemGridX) + Math.abs(playerGridY - itemGridY) == 1) {
            drawY += CLOSE_ITEM_Y_BOOST;
        }

        if (item.getSpriteData() != null) {
            drawAsciiSprite(shapeRenderer, item, item.getSpriteData(), screenX, transformY, camera, viewport, depthBuffer, spriteWidth, spriteHeight, drawY);
        } else {
            // Fallback
            int drawStartX = Math.max(0, screenX - spriteWidth / 2);
            int drawEndX = Math.min((int)viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);
            for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
                if (transformY < depthBuffer[stripe]) {
                    shapeRenderer.setColor(item.getColor());
                    shapeRenderer.rect(stripe, drawY, 1, spriteHeight);
                }
            }
        }
    }

    private void drawLadderSprite(ShapeRenderer shapeRenderer, Ladder ladder, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer) {
        int spriteHeight = (int) (camera.viewportHeight / transformY);
        float floorY = (camera.viewportHeight / 2) - (spriteHeight / 2f);
        float drawY = floorY;
        int spriteWidth = (int) (spriteHeight * 0.5f);

        String[] spriteData = ItemSpriteData.getSpriteByType("LADDER");
        if (spriteData != null) {
            drawAsciiSprite(shapeRenderer, ladder, spriteData, screenX, transformY, camera, viewport, depthBuffer, spriteWidth, spriteHeight, drawY);
        }
    }

    private void drawAsciiSprite(ShapeRenderer shapeRenderer, Renderable entity, String[] spriteData, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer, int spriteWidth, int spriteHeight, float drawY) {
        int drawStartX = Math.max(0, screenX - spriteWidth / 2);
        int drawEndX = Math.min((int) viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);

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
                            shapeRenderer.setColor(entity.getColor());
                            shapeRenderer.rect(stripe, screenY, 1, 1);
                        }
                    }
                }
            }
        }
    }
    public void dispose() { spriteBatch.dispose(); }
}
