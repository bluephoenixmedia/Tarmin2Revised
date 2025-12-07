package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.gore.BloodParticle;
import com.bpm.minotaur.gamedata.gore.Gib;
import com.bpm.minotaur.gamedata.gore.SurfaceDecal;
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

    // --- Tooltip Vars ---
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private Item hoveredItem = null;
    private float hoveredScreenX = 0;
    private float hoveredScreenY = 0;

    public EntityRenderer(ItemDataManager itemDataManager) {
        this.spriteBatch = new SpriteBatch();
        this.itemDataManager = itemDataManager;

        // Create a default font for tooltips
        this.font = new BitmapFont();
        this.font.getData().setScale(1.2f);
        this.font.setUseIntegerPositions(false);
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

        // Reset tooltip for this frame
        hoveredItem = null;

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

        // Determine the "active" item tile (Feet or Front)
        int playerX = (int) player.getPosition().x;
        int playerY = (int) player.getPosition().y;
        int frontX = (int) (player.getPosition().x + player.getFacing().getVector().x);
        int frontY = (int) (player.getPosition().y + player.getFacing().getVector().y);

        // Pre-pass: Identify which item (if any) is the interaction target
        // Priority: Feet > Front
        Item itemAtFeet = null;
        Item itemInFront = null;

        for (Renderable r : entities) {
            if (r instanceof Item) {
                Item it = (Item) r;
                int ix = (int) it.getPosition().x;
                int iy = (int) it.getPosition().y;
                if (ix == playerX && iy == playerY) itemAtFeet = it;
                else if (ix == frontX && iy == frontY) itemInFront = it;
            }
        }

        if (itemAtFeet != null) hoveredItem = itemAtFeet;
        else if (itemInFront != null) hoveredItem = itemInFront;


        boolean isShapeRendererActive = false;
        boolean isSpriteBatchActive = false;

        for (Renderable entity : entities) {

            // --- NEW: Skip 2D Rendering for 3D Items ---
            if (entity instanceof Item) {
                Item item = (Item) entity;
                // If the template has a model path, we assume GameScreen is rendering it in 3D.
                // Therefore, we skip the 2D render here.
                if (item.getTemplate() != null && item.getTemplate().modelPath != null) {
                    continue;
                }
            }

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

        renderCorpses(shapeRenderer, maze, viewport.getCamera(), player, depthBuffer);
        this.renderGore(shapeRenderer, maze, viewport.getCamera(), player, depthBuffer);

        if (spriteBatch.isDrawing()) spriteBatch.end();

        // --- Draw Tooltip Overlay ---
        if (hoveredItem != null) {
            renderTooltip(spriteBatch, shapeRenderer, viewport);
        }
    }

    private void renderTooltip(SpriteBatch batch, ShapeRenderer shape, Viewport viewport) {
        // 1. Prepare Text
        StringBuilder sb = new StringBuilder();
        sb.append(hoveredItem.getDisplayName()).append("\n");

        // Add stats
        if (hoveredItem.isWeapon()) {
            if (hoveredItem.getWarDamage() > 0) sb.append("War: ").append(hoveredItem.getWarDamage()).append(" ");
            if (hoveredItem.getSpiritDamage() > 0) sb.append("Spirit: ").append(hoveredItem.getSpiritDamage()).append(" ");
        } else if (hoveredItem.isArmor()) {
            sb.append("Def: ").append(hoveredItem.getArmorDefense());
        } else if (hoveredItem.isPotion() || hoveredItem.isFood()) {
            sb.append("Consumable");
        }

        layout.setText(font, sb.toString());

        float width = layout.width + 20;
        float height = layout.height + 20;

        // 2. Calculate Position (Right of sprite, clamped to screen)
        float x = hoveredScreenX + 40;
        float y = hoveredScreenY + 20;

        // Clamp to screen
        if (x + width > viewport.getWorldWidth()) x = hoveredScreenX - width - 40;
        if (y - height < 0) y = height + 10;
        if (y > viewport.getWorldHeight()) y = viewport.getWorldHeight() - 10;

        // 3. Draw Background Box
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shape.setProjectionMatrix(viewport.getCamera().combined);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0, 0, 0, 0.7f);
        shape.rect(x, y - height, width, height);

        // Border
        shape.setColor(Color.WHITE);
        shape.rect(x, y - height, width, 2); // Bottom
        shape.rect(x, y, width, 2); // Top
        shape.rect(x, y - height, 2, height); // Left
        shape.rect(x + width - 2, y - height, 2, height); // Right
        shape.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // 4. Draw Text
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        font.setColor(Color.YELLOW);
        font.draw(batch, sb.toString(), x + 10, y - 10);
        batch.end();
    }

    // ... [GORE RENDER METHODS UNCHANGED] ...
    public void renderGore(ShapeRenderer shapeRenderer, Maze maze, Camera camera, Player player, float[] depthBuffer) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.setTransformMatrix(new Matrix4());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        renderDecals(shapeRenderer, maze, camera, player, depthBuffer);
        renderGibs(shapeRenderer, maze, camera, player, depthBuffer);
        float halfWidth = camera.viewportWidth / 2.0f;
        float halfHeight = camera.viewportHeight / 2.0f;
        for (BloodParticle p : maze.getGoreManager().getActiveParticles()) {
            float dx = p.position.x - player.getPosition().x;
            float dy = p.position.z - player.getPosition().y;
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
                    float spriteScale = Math.abs(camera.viewportHeight / transformY);
                    float screenY = halfHeight + (p.position.y - 0.5f) * spriteScale;
                    float particleSize = spriteScale * p.size;
                    shapeRenderer.setColor(p.color);
                    shapeRenderer.rect(screenX - particleSize/2, screenY - particleSize/2, particleSize, particleSize);
                }
            }
        }
        shapeRenderer.end();
    }

    private void renderGibs(ShapeRenderer shapeRenderer, Maze maze, Camera camera, Player player, float[] depthBuffer) {
        float halfWidth = camera.viewportWidth / 2.0f;
        float halfHeight = camera.viewportHeight / 2.0f;
        for (Gib gib : maze.getGoreManager().getActiveGibs()) {
            float dx = gib.position.x - player.getPosition().x;
            float dy = gib.position.z - player.getPosition().y;
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
                    float spriteScale = Math.abs(camera.viewportHeight / transformY);
                    float screenY = halfHeight + (gib.position.y - 0.5f) * spriteScale;
                    Matrix4 originalMatrix = shapeRenderer.getTransformMatrix().cpy();
                    transformMatrix.set(originalMatrix);
                    transformMatrix.translate(screenX, screenY, 0);
                    transformMatrix.rotate(0, 0, 1, gib.rotation);
                    shapeRenderer.setTransformMatrix(transformMatrix);
                    float partPixelSize = spriteScale / 30.0f;
                    int rows = gib.spriteData.length;
                    int cols = gib.spriteData[0].length();
                    float startOffsetX = -(cols * partPixelSize) / 2.0f;
                    float startOffsetY = -(rows * partPixelSize) / 2.0f;
                    for (int r = 0; r < rows; r++) {
                        String row = gib.spriteData[r];
                        for (int c = 0; c < cols; c++) {
                            if (c < row.length() && row.charAt(c) == '#') {
                                shapeRenderer.setColor(gib.color);
                                shapeRenderer.rect(startOffsetX + c * partPixelSize, startOffsetY + (rows - 1 - r) * partPixelSize, partPixelSize, partPixelSize);
                            }
                        }
                    }
                    shapeRenderer.setTransformMatrix(originalMatrix);
                }
            }
        }
    }

    private void renderDecals(ShapeRenderer shapeRenderer, Maze maze, Camera camera, Player player, float[] depthBuffer) {
        float halfWidth = camera.viewportWidth / 2.0f;
        float halfHeight = camera.viewportHeight / 2.0f;
        for (SurfaceDecal d : maze.getGoreManager().getActiveDecals()) {
            float dx = d.position.x - player.getPosition().x;
            float dy = d.position.z - player.getPosition().y;
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
                    float spriteScale = Math.abs(camera.viewportHeight / transformY);
                    float screenY = halfHeight + (d.position.y - 0.5f) * spriteScale;
                    float width = spriteScale * d.size * 2.0f;
                    float height = spriteScale * d.size * 0.5f;
                    shapeRenderer.setColor(d.color);
                    shapeRenderer.rect(screenX - width/2, screenY - height/2, width, height);
                }
            }
        }
    }

    // ... [MONSTER AND PROJECTILE RENDERERS UNCHANGED] ...
    public void renderSingleMonster(ShapeRenderer shapeRenderer, Player player, Monster monster, Viewport viewport,
                                    float[] depthBuffer, FirstPersonRenderer firstPersonRenderer, Maze maze, WorldManager worldManager) {
        if (depthBuffer == null || monster == null) return;
        Biome biome = worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId());
        if (biome.hasFogOfWar()) {
            float distanceToMonster = player.getPosition().dst(monster.getPosition());
            if (distanceToMonster > biome.getFogDistance()) return;
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
        shapeRenderer.setTransformMatrix(new Matrix4());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        float halfWidth = camera.viewportWidth / 2.0f;
        float halfHeight = camera.viewportHeight / 2.0f;
        for (CorpsePart part : maze.getCorpses()) {
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
                    float spriteScale = Math.abs(camera.viewportHeight / transformY);
                    float screenY = halfHeight + (part.position.y - 0.5f) * spriteScale;
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

    // --- MODIFIED HELPER METHODS ---

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

            // --- CAPTURE COORDINATES IF HOVERED ---
            if (item == hoveredItem) {
                hoveredScreenX = screenX + spriteWidth / 2f;
                hoveredScreenY = drawY + spriteHeight;
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

    // ... [DRAW PROJECTILE UNCHANGED] ...
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

        // --- CAPTURE COORDINATES IF HOVERED ---
        if (item == hoveredItem) {
            hoveredScreenX = screenX + spriteWidth / 2f;
            hoveredScreenY = drawY + spriteHeight;
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
            // Pass the ladder type to the drawAsciiSprite or handle flipping here
            // To keep drawAsciiSprite generic, we can just reverse the data array if it's UP
            String[] dataToDraw = spriteData;

            if (ladder.getType() == Ladder.LadderType.UP) {
                // Flip Vertical: Reverse the array order
                String[] flipped = new String[spriteData.length];
                for(int i=0; i<spriteData.length; i++) {
                    flipped[i] = spriteData[spriteData.length - 1 - i];
                }
                dataToDraw = flipped;

                // Optional: Change color to Gold/Green to indicate "Good/Return"?
                // Using existing color for now.
            }

            drawAsciiSprite(shapeRenderer, ladder, dataToDraw, screenX, transformY, camera, viewport, depthBuffer, spriteWidth, spriteHeight, drawY);
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
        font.dispose();
    }
}
