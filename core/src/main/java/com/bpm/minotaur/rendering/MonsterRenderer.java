package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Monster;
import com.bpm.minotaur.gamedata.Player;

import java.util.ArrayList;
import java.util.List;

public class MonsterRenderer {

    public void render(ShapeRenderer shapeRenderer, Player player, Maze maze, Viewport viewport, float[] depthBuffer) {
        if (depthBuffer == null) return;

        List<Monster> monsters = new ArrayList<>(maze.getMonsters().values());
        monsters.sort((a, b) -> Float.compare(
            player.getPosition().dst2(b.getPosition()),
            player.getPosition().dst2(a.getPosition())
        ));

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (Monster monster : monsters) {
            drawMonster(shapeRenderer, player, monster, viewport, depthBuffer);
        }

        shapeRenderer.end();
    }

    private void drawMonster(ShapeRenderer shapeRenderer, Player player, Monster monster, Viewport viewport, float[] depthBuffer) {
        float spriteX = monster.getPosition().x - player.getPosition().x;
        float spriteY = monster.getPosition().y - player.getPosition().y;

        float invDet = 1.0f / (player.getCameraPlane().x * player.getDirectionVector().y - player.getDirectionVector().x * player.getCameraPlane().y);

        float transformX = invDet * (player.getDirectionVector().y * spriteX - player.getDirectionVector().x * spriteY);
        float transformY = invDet * (-player.getCameraPlane().y * spriteX + player.getCameraPlane().x * spriteY);

        if (transformY > 0) {
            Camera camera = viewport.getCamera();
            int screenX = (int) ((camera.viewportWidth / 2) * (1 + transformX / transformY));

            float floorOffset = 0.0f; // Adjust to make monsters appear at the correct height
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
    }
}
