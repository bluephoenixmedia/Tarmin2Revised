package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Item;
import com.bpm.minotaur.gamedata.ItemSpriteData;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;

import java.util.ArrayList;
import java.util.List;

public class ItemRenderer {

    public void render(ShapeRenderer shapeRenderer, Player player, Maze maze, Viewport viewport, float[] depthBuffer) {
        if (depthBuffer == null) return;

        List<Item> items = new ArrayList<>(maze.getItems().values());
        items.sort((a, b) -> Float.compare(
            player.getPosition().dst2(b.getPosition()),
            player.getPosition().dst2(a.getPosition())
        ));

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (Item item : items) {
            drawItem(shapeRenderer, player, item, viewport, depthBuffer);
        }

        shapeRenderer.end();
    }

    private void drawItem(ShapeRenderer shapeRenderer, Player player, Item item, Viewport viewport, float[] depthBuffer) {
        float spriteX = item.getPosition().x - player.getPosition().x;
        float spriteY = item.getPosition().y - player.getPosition().y;

        float invDet = 1.0f / (player.getCameraPlane().x * player.getDirectionVector().y - player.getDirectionVector().x * player.getCameraPlane().y);

        float transformX = invDet * (player.getDirectionVector().y * spriteX - player.getDirectionVector().x * spriteY);
        float transformY = invDet * (-player.getCameraPlane().y * spriteX + player.getCameraPlane().x * spriteY);

        if (transformY > 0) {
            Camera camera = viewport.getCamera();
            int screenX = (int) ((camera.viewportWidth / 2) * (1 + transformX / transformY));

            int spriteHeight = Math.abs((int) (camera.viewportHeight / transformY)) / 2;
            int spriteWidth = spriteHeight;

            String[] spriteData = ItemSpriteData.getSpriteByType(item.getTypeName());

            if (spriteData == null) {
                return; // No sprite data found for this item
            }

            Vector2 itemScale = item.getScale();

            int spritePixelHeight = spriteData.length;
            int spritePixelWidth = spriteData[0].length();

            float pixelWidth = (float)spriteWidth / spritePixelWidth * itemScale.x;
            float pixelHeight = (float)spriteHeight / spritePixelHeight * itemScale.y;

            float totalWidth = spritePixelWidth * pixelWidth;
            float totalHeight = spritePixelHeight * pixelHeight;

            float drawStartX = screenX - totalWidth / 2;
            float drawStartY = (camera.viewportHeight / 2) - totalHeight;


            for (int px = 0; px < spritePixelWidth; px++) {
                float currentX = drawStartX + px * pixelWidth;
                int screenStripe = (int)currentX;

                if (screenStripe >= 0 && screenStripe < viewport.getScreenWidth() && transformY < depthBuffer[screenStripe]) {
                    for (int py = 0; py < spritePixelHeight; py++) {
                        if (spriteData[py].charAt(px) == '#') {
                            float currentY = drawStartY + (spritePixelHeight - 1 - py) * pixelHeight;
                            shapeRenderer.setColor(item.getColor());
                            shapeRenderer.rect(currentX, currentY, pixelWidth, pixelHeight);
                        }
                    }
                }
            }
        }

    }
}
