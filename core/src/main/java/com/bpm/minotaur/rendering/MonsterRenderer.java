package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterSpriteData;
import com.bpm.minotaur.gamedata.player.Player;

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

            int spriteHeight = Math.abs((int) (camera.viewportHeight / transformY));
            int spriteWidth = spriteHeight;

            String[] spriteData = MonsterSpriteData.getSpriteByType(monster.getMonsterType());

            if (spriteData == null) {
                return;
            }

            Vector2 monsterScale = monster.getScale();

            int spritePixelHeight = spriteData.length;
            int spritePixelWidth = spriteData[0].length();

            float pixelWidth = (float)spriteWidth / spritePixelWidth * monsterScale.x;
            float pixelHeight = (float)spriteHeight / spritePixelHeight * monsterScale.y;

            float totalWidth = spritePixelWidth * pixelWidth;
            float totalHeight = spritePixelHeight * pixelHeight;

            float drawStartX = screenX - totalWidth / 2;
            float drawStartY = (camera.viewportHeight / 2) - totalHeight / 2;


            for (int px = 0; px < spritePixelWidth; px++) {
                float currentX = drawStartX + px * pixelWidth;
                int screenStripe = (int) currentX;

                if (screenStripe >= 0 && screenStripe < viewport.getScreenWidth() && transformY < depthBuffer[screenStripe]) {
                    for (int py = 0; py < spritePixelHeight; py++) {
                        if (spriteData[py].charAt(px) == '#') {
                            float currentY = drawStartY + (spritePixelHeight - 1 - py) * pixelHeight;
                            shapeRenderer.setColor(monster.getColor());
                            shapeRenderer.rect(currentX, currentY, pixelWidth, pixelHeight);
                        }
                    }
                }
            }
        }

    }
}
