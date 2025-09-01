package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Item;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Player;

import java.util.ArrayList;
import java.util.List;

public class ItemRenderer {

    public void render(ShapeRenderer shapeRenderer, Player player, Maze maze, Viewport viewport) {
        // Create a list of items and sort them by distance from the player (far to near)
        List<Item> items = new ArrayList<>(maze.getItems().values());
        items.sort((a, b) -> Float.compare(
            player.getPosition().dst2(b.getPosition()),
            player.getPosition().dst2(a.getPosition())
        ));

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (Item item : items) {
            drawPotion(shapeRenderer, player, item, viewport.getCamera());
        }

        shapeRenderer.end();
    }

    private void drawPotion(ShapeRenderer shapeRenderer, Player player, Item item, Camera camera) {
        // Translate item position to be relative to the player
        float spriteX = item.getPosition().x - player.getPosition().x;
        float spriteY = item.getPosition().y - player.getPosition().y;

        // Required for matrix transformation
        float invDet = 1.0f / (player.getCameraPlane().x * player.getDirectionVector().y - player.getDirectionVector().x * player.getCameraPlane().y);

        // Transform sprite position with the inverse camera matrix
        float transformX = invDet * (player.getDirectionVector().y * spriteX - player.getDirectionVector().x * spriteY);
        float transformY = invDet * (-player.getCameraPlane().y * spriteX + player.getCameraPlane().x * spriteY);

        // Only render if the item is in front of the player
        if (transformY > 0) {
            int screenX = (int) ((camera.viewportWidth / 2) * (1 + transformX / transformY));

            // Y position on screen, adjusted to appear on the floor
            float floorOffset = 0.4f;
            int spriteScreenY = (int)(camera.viewportHeight / 2 * (1 + floorOffset / transformY));

            // Calculate height and width of the sprite based on its distance
            int spriteHeight = Math.abs((int) (camera.viewportHeight / transformY)) / 2;
            int spriteWidth = spriteHeight / 2;

            float drawX = screenX - spriteWidth / 2f;
            float drawY = spriteScreenY - spriteHeight;

            // Draw a simple potion shape inspired by classic games
            shapeRenderer.setColor(item.getColor());
            shapeRenderer.rect(drawX, drawY, spriteWidth, spriteHeight); // Body of the potion

            shapeRenderer.setColor(item.getLiquidColor());
            shapeRenderer.rect(drawX + spriteWidth * 0.1f, drawY + spriteHeight * 0.1f, spriteWidth * 0.8f, spriteHeight * 0.5f); // Liquid part

            shapeRenderer.setColor(item.getColor().cpy().mul(0.7f)); // Darker color for the cork
            shapeRenderer.rect(drawX + spriteWidth * 0.25f, drawY + spriteHeight, spriteWidth * 0.5f, spriteHeight * 0.2f); // Cork
        }
    }
}

