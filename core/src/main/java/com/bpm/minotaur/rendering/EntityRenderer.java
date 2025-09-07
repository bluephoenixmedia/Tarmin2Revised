package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Item;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Monster;
import com.bpm.minotaur.gamedata.Player;
import com.bpm.minotaur.gamedata.Renderable;

import java.util.ArrayList;
import java.util.List;

public class EntityRenderer {

    public void render(ShapeRenderer shapeRenderer, Player player, Maze maze, Viewport viewport, float[] depthBuffer) {
        if (depthBuffer == null) return;

        // 1. Combine all renderable entities into a single list
        List<Renderable> entities = new ArrayList<>();
        entities.addAll(maze.getItems().values());
        entities.addAll(maze.getMonsters().values());

        // 2. Sort the list from farthest to nearest
        entities.sort((a, b) -> Float.compare(
                player.getPosition().dst2(b.getPosition()),
                player.getPosition().dst2(a.getPosition())
        ));

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // 3. Render each entity in the sorted order
        for (Renderable entity : entities) {
            drawEntity(shapeRenderer, player, entity, viewport, depthBuffer);
        }

        shapeRenderer.end();
    }

    private void drawEntity(ShapeRenderer shapeRenderer, Player player, Renderable entity, Viewport viewport, float[] depthBuffer) {
        float spriteX = entity.getPosition().x - player.getPosition().x;
        float spriteY = entity.getPosition().y - player.getPosition().y;

        float invDet = 1.0f / (player.getCameraPlane().x * player.getDirectionVector().y - player.getDirectionVector().x * player.getCameraPlane().y);

        float transformX = invDet * (player.getDirectionVector().y * spriteX - player.getDirectionVector().x * spriteY);
        float transformY = invDet * (-player.getCameraPlane().y * spriteX + player.getCameraPlane().x * spriteY);

        if (transformY > 0) {
            Camera camera = viewport.getCamera();
            int screenX = (int) ((camera.viewportWidth / 2) * (1 + transformX / transformY));

            if (entity instanceof Monster) {
                drawMonsterSprite(shapeRenderer, entity, screenX, transformY, camera, viewport, depthBuffer);
            } else if (entity instanceof Item) {
                drawItemSprite(shapeRenderer, (Item) entity, screenX, transformY, camera, viewport, depthBuffer);
            }
        }
    }

    private void drawMonsterSprite(ShapeRenderer shapeRenderer, Renderable monster, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer) {
        float floorOffset = 0.0f;
        int spriteScreenY = (int)(camera.viewportHeight / 2 * (1 + floorOffset / transformY));
        int spriteHeight = Math.abs((int) (camera.viewportHeight / transformY));
        int spriteWidth = spriteHeight;
        float drawY = spriteScreenY - spriteHeight / 2.0f;
        int drawStartX = Math.max(0, screenX - spriteWidth / 2);
        int drawEndX = Math.min((int)viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);

        for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
            if (transformY < depthBuffer[stripe]) {
                shapeRenderer.setColor(monster.getColor());
                shapeRenderer.rect(stripe, drawY, 1, spriteHeight);
            }
        }
    }

    private void drawItemSprite(ShapeRenderer shapeRenderer, Item item, int screenX, float transformY, Camera camera, Viewport viewport, float[] depthBuffer) {
        float floorOffset = 0.4f;
        int spriteScreenY = (int)(camera.viewportHeight / 2 * (1 + floorOffset / transformY));
        int spriteHeight = Math.abs((int) (camera.viewportHeight / transformY)) / 2;
        int spriteWidth = spriteHeight / 2;
        float drawY = spriteScreenY - spriteHeight;
        int drawStartX = Math.max(0, screenX - spriteWidth / 2);
        int drawEndX = Math.min((int)viewport.getScreenWidth() - 1, screenX + spriteWidth / 2);

        for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
            if (transformY < depthBuffer[stripe]) {
                // Body of the potion
                shapeRenderer.setColor(item.getColor());
                shapeRenderer.rect(stripe, drawY, 1, spriteHeight);
                // Liquid part
                shapeRenderer.setColor(item.getLiquidColor());
                shapeRenderer.rect(stripe, drawY + spriteHeight * 0.1f, 1, spriteHeight * 0.5f);
                // Cork
                shapeRenderer.setColor(item.getColor().cpy().mul(0.7f));
                shapeRenderer.rect(stripe, drawY + spriteHeight, 1, spriteHeight * 0.2f);
            }
        }
    }
}
