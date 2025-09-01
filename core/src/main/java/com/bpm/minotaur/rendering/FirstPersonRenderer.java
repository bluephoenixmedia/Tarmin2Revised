package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Player;

public class FirstPersonRenderer {

    // --- Colors ---
    private final Color floorColor = new Color(0.2f, 0.4f, 0.2f, 1);
    private final Color ceilingColor = new Color(0.3f, 0.5f, 0.3f, 1);
    private final Color wallColor = new Color(0.5f, 0.8f, 0.5f, 1);
    private final Color wallDarkColor = new Color(0.4f, 0.7f, 0.4f, 1);
    private final Color doorColor = new Color(0.6f, 0.4f, 0.2f, 1);
    private final Color doorDarkColor = new Color(0.5f, 0.3f, 0.1f, 1);

    // --- Bitmasks (matching DebugRenderer and GameScreen) ---
    private static final int WALL_WEST   = 0b00000001;
    private static final int WALL_EAST   = 0b00000100;
    private static final int WALL_SOUTH  = 0b00010000;
    private static final int WALL_NORTH  = 0b01000000;
    private static final int DOOR_WEST   = 0b00000010;
    private static final int DOOR_EAST   = 0b00001000;
    private static final int DOOR_SOUTH  = 0b00100000;
    private static final int DOOR_NORTH  = 0b10000000;

    private float[] depthBuffer;

    public float[] getDepthBuffer() {
        return depthBuffer;
    }

    public void render(ShapeRenderer shapeRenderer, Player player, Maze maze, Viewport viewport) {
        // Initialize or resize the depth buffer if necessary
        if (depthBuffer == null || depthBuffer.length != viewport.getScreenWidth()) {
            depthBuffer = new float[(int)viewport.getScreenWidth()];
        }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(ceilingColor);
        shapeRenderer.rect(0, viewport.getWorldHeight() / 2, viewport.getWorldWidth(), viewport.getWorldHeight() / 2);
        shapeRenderer.setColor(floorColor);
        shapeRenderer.rect(0, 0, viewport.getWorldWidth(), viewport.getWorldHeight() / 2);

        for (int x = 0; x < viewport.getScreenWidth(); x++) {
            RaycastResult result = castRay(player, maze, x, viewport);
            if (result != null) {
                renderWallSlice(shapeRenderer, result, x, viewport);
                depthBuffer[x] = result.distance;
            } else {
                depthBuffer[x] = Float.MAX_VALUE;
            }
        }

        shapeRenderer.end();
    }

    private RaycastResult castRay(Player player, Maze maze, int screenX, Viewport viewport) {
        float cameraX = 2 * screenX / (float) viewport.getScreenWidth() - 1;
        Vector2 rayDir = new Vector2(player.getDirectionVector()).add(new Vector2(player.getCameraPlane()).scl(cameraX));

        int mapX = (int) player.getPosition().x;
        int mapY = (int) player.getPosition().y;

        Vector2 sideDist = new Vector2();
        Vector2 deltaDist = new Vector2(
            (rayDir.x == 0) ? Float.MAX_VALUE : Math.abs(1 / rayDir.x),
            (rayDir.y == 0) ? Float.MAX_VALUE : Math.abs(1 / rayDir.y)
        );

        int stepX, stepY;
        if (rayDir.x < 0) {
            stepX = -1;
            sideDist.x = (player.getPosition().x - mapX) * deltaDist.x;
        } else {
            stepX = 1;
            sideDist.x = (mapX + 1.0f - player.getPosition().x) * deltaDist.x;
        }
        if (rayDir.y < 0) {
            stepY = -1;
            sideDist.y = (player.getPosition().y - mapY) * deltaDist.y;
        } else {
            stepY = 1;
            sideDist.y = (mapY + 1.0f - player.getPosition().y) * deltaDist.y;
        }

        boolean hit = false;
        int side = 0;
        int maxDistance = 50;
        int distanceTraveled = 0;

        // DDA Algorithm
        while (!hit && distanceTraveled < maxDistance) {
            if (sideDist.x < sideDist.y) {
                sideDist.x += deltaDist.x;
                mapX += stepX;
                side = 0;
            } else {
                sideDist.y += deltaDist.y;
                mapY += stepY;
                side = 1;
            }

            if (isOutOfBounds(mapX, mapY, maze)) {
                hit = true;
            } else {
                int wallData = maze.getWallDataAt(mapX, mapY);
                int prevMapX = (side == 0) ? mapX - stepX : mapX;
                int prevMapY = (side == 1) ? mapY - stepY : mapY;
                int prevWallData = maze.getWallDataAt(prevMapX, prevMapY);

                if (hasWallCollision(wallData, prevWallData, side, stepX, stepY)) {
                    hit = true;
                } else if (hasDoorCollision(wallData, prevWallData, side, stepX, stepY)) {
                    Object obj = maze.getGameObjectAt(mapX, mapY);
                    if (obj == null) obj = maze.getGameObjectAt(prevMapX, prevMapY);

                    if (obj instanceof Door && ((Door) obj).getState() != Door.DoorState.OPEN) {
                        hit = true;
                    }
                }
            }
            distanceTraveled++;
        }

        if (!hit) return null;

        float perpWallDist;
        if (side == 0) {
            perpWallDist = (sideDist.x - deltaDist.x);
        } else {
            perpWallDist = (sideDist.y - deltaDist.y);
        }

        WallType wallType = WallType.WALL;
        Door hitDoor = null;
        if (!isOutOfBounds(mapX, mapY, maze)) {
            int prevMapX = (side == 0) ? mapX - stepX : mapX;
            int prevMapY = (side == 1) ? mapY - stepY : mapY;

            Object obj = maze.getGameObjectAt(mapX, mapY);
            if (obj == null) obj = maze.getGameObjectAt(prevMapX, prevMapY);

            if (obj instanceof Door) {
                wallType = WallType.DOOR;
                hitDoor = (Door) obj;
            }
        }

        return new RaycastResult(perpWallDist, side, wallType, hitDoor);
    }

    private boolean isOutOfBounds(int x, int y, Maze maze) {
        return x < 0 || x >= maze.getWidth() || y < 0 || y >= maze.getHeight();
    }

    private boolean hasWallCollision(int currentData, int prevData, int side, int stepX, int stepY) {
        if (side == 0) { // Vertical wall (moved East/West)
            return (stepX > 0) ? (prevData & WALL_EAST) != 0 || (currentData & WALL_WEST) != 0
                : (prevData & WALL_WEST) != 0 || (currentData & WALL_EAST) != 0;
        } else { // Horizontal wall (moved North/South)
            return (stepY > 0) ? (prevData & WALL_NORTH) != 0 || (currentData & WALL_SOUTH) != 0
                : (prevData & WALL_SOUTH) != 0 || (currentData & WALL_NORTH) != 0;
        }
    }

    private boolean hasDoorCollision(int currentData, int prevData, int side, int stepX, int stepY) {
        if (side == 0) {
            return (stepX > 0) ? (prevData & DOOR_EAST) != 0 || (currentData & DOOR_WEST) != 0
                : (prevData & DOOR_WEST) != 0 || (currentData & DOOR_EAST) != 0;
        } else {
            return (stepY > 0) ? (prevData & DOOR_NORTH) != 0 || (currentData & DOOR_SOUTH) != 0
                : (prevData & DOOR_SOUTH) != 0 || (currentData & DOOR_NORTH) != 0;
        }
    }

    private void renderWallSlice(ShapeRenderer shapeRenderer, RaycastResult result, int screenX, Viewport viewport) {
        int lineHeight = (result.distance <= 0) ? Integer.MAX_VALUE : (int) (viewport.getWorldHeight() / result.distance);
        float drawStart = Math.max(0, -lineHeight / 2f + viewport.getWorldHeight() / 2f);
        float drawEnd = Math.min(viewport.getWorldHeight(), lineHeight / 2f + viewport.getWorldHeight() / 2f);

        Color renderColor;
        switch (result.wallType) {
            case DOOR:
                renderColor = (result.side == 1) ? doorDarkColor : doorColor;
                if (result.door != null && result.door.getState() == Door.DoorState.OPENING) {
                    float openingOffset = (drawEnd - drawStart) * result.door.getAnimationProgress();
                    drawEnd -= openingOffset;
                }
                break;
            case WALL:
            default:
                renderColor = (result.side == 1) ? wallDarkColor : wallColor;
                break;
        }

        shapeRenderer.setColor(renderColor);
        if (drawEnd > drawStart) {
            shapeRenderer.rect(screenX, drawStart, 1, drawEnd - drawStart);
        }
    }

    private enum WallType { WALL, DOOR }

    private static class RaycastResult {
        final float distance;
        final int side;
        final WallType wallType;
        final Door door;

        RaycastResult(float distance, int side, WallType wallType, Door door) {
            this.distance = distance;
            this.side = side;
            this.wallType = wallType;
            this.door = door;
        }
    }
}

