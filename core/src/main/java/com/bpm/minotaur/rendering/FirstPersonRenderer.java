package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Player;

/**
 * Renders the 3D first-person view of the maze using a raycasting algorithm.
 * This class is responsible for drawing the floor, ceiling, walls, and doors.
 * It also generates a depth buffer to allow other renderers (like EntityRenderer)
 * to correctly occlude objects behind walls.
 */
public class FirstPersonRenderer {

    // --- Colors ---
    private final Color floorColor = new Color(0.2f, 0.4f, 0.2f, 1);
    private final Color ceilingColor = new Color(0.3f, 0.5f, 0.3f, 1);
    private final Color wallColor = new Color(0.5f, 0.8f, 0.5f, 1);
    private final Color wallDarkColor = new Color(0.4f, 0.7f, 0.4f, 1); // Shaded for vertical walls
    private final Color doorBlueColor = new Color(0.1f, 0.2f, 0.7f, 1);
    private final Color doorBlueDarkColor = new Color(0.1f, 0.15f, 0.6f, 1);

    // --- Rendering Constants ---
    private static final float DOOR_WIDTH = 1.0f / 3.0f;
    private static final float DOOR_HEIGHT_RATIO = 0.8f;

    // --- Bitmasks ---
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
        Vector2 renderPosition;
        int playerX = (int) player.getPosition().x;
        int playerY = (int) player.getPosition().y;
        boolean isBehindBlocked = maze.isWallBlocking(playerX, playerY, player.getFacing().getOpposite());

        if (isBehindBlocked) {
            renderPosition = player.getPosition().cpy();
        } else {
            renderPosition = player.getPosition().cpy().sub(player.getDirectionVector());
        }

        // --- START OF NEW LOGIC ---
        // Check if the renderer is starting inside a door tile. If so, we need to ignore it during collision checks.
        int startTileX = (int) renderPosition.x;
        int startTileY = (int) renderPosition.y;
        Object startObject = maze.getGameObjectAt(startTileX, startTileY);
        Door doorToIgnore = null;
        if (startObject instanceof Door) {
            doorToIgnore = (Door) startObject;
        }
        // --- END OF NEW LOGIC ---

        float cameraX = 1 - 2 * screenX / (float) viewport.getScreenWidth();
        Vector2 rayDir = new Vector2(player.getDirectionVector()).add(new Vector2(player.getCameraPlane()).scl(cameraX));

        int mapX = (int) renderPosition.x;
        int mapY = (int) renderPosition.y;

        Vector2 sideDist = new Vector2();
        Vector2 deltaDist = new Vector2(
            (rayDir.x == 0) ? Float.MAX_VALUE : Math.abs(1 / rayDir.x),
            (rayDir.y == 0) ? Float.MAX_VALUE : Math.abs(1 / rayDir.y)
        );

        int stepX, stepY;
        if (rayDir.x < 0) {
            stepX = -1;
            sideDist.x = (renderPosition.x - mapX) * deltaDist.x;
        } else {
            stepX = 1;
            sideDist.x = (mapX + 1.0f - renderPosition.x) * deltaDist.x;
        }
        if (rayDir.y < 0) {
            stepY = -1;
            sideDist.y = (renderPosition.y - mapY) * deltaDist.y;
        } else {
            stepY = 1;
            sideDist.y = (mapY + 1.0f - renderPosition.y) * deltaDist.y;
        }

        boolean hit = false;
        int side = 0;
        int maxDistance = 50;
        int distanceTraveled = 0;

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

                    // --- MODIFIED COLLISION CHECK ---
                    // Hit is only true if the door is NOT the one we started in AND it is not open.
                    if (obj instanceof Door && obj != doorToIgnore && ((Door) obj).getState() != Door.DoorState.OPEN) {
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

        float wallX;
        if (side == 0) {
            wallX = renderPosition.y + perpWallDist * rayDir.y;
        } else {
            wallX = renderPosition.x + perpWallDist * rayDir.x;
        }
        wallX -= Math.floor(wallX);

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

        return new RaycastResult(perpWallDist, side, wallType, hitDoor, wallX);
    }

    private boolean isOutOfBounds(int x, int y, Maze maze) {
        return x < 0 || x >= maze.getWidth() || y < 0 || y >= maze.getHeight();
    }

    private boolean hasWallCollision(int currentData, int prevData, int side, int stepX, int stepY) {
        if (side == 0) {
            return (stepX > 0) ? (prevData & WALL_EAST) != 0 || (currentData & WALL_WEST) != 0
                : (prevData & WALL_WEST) != 0 || (currentData & WALL_EAST) != 0;
        } else {
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

        if (result.wallType == WallType.DOOR) {
            float doorEdgeMargin = (1.0f - DOOR_WIDTH) / 2.0f;
            boolean isFramePart = result.wallX < doorEdgeMargin || result.wallX > 1.0f - doorEdgeMargin;

            if (isFramePart) {
                Color frameColor = (result.side == 1) ? wallDarkColor : wallColor;
                shapeRenderer.setColor(frameColor);
                shapeRenderer.rect(screenX, drawStart, 1, drawEnd - drawStart);
            } else {
                float doorHeight = (drawEnd - drawStart) * DOOR_HEIGHT_RATIO;
                float doorDrawStart = drawStart;
                float doorDrawEnd = drawStart + doorHeight;

                Color frameColor = (result.side == 1) ? wallDarkColor : wallColor;
                shapeRenderer.setColor(frameColor);
                if (drawEnd > doorDrawEnd) {
                    shapeRenderer.rect(screenX, doorDrawEnd, 1, drawEnd - doorDrawEnd);
                }

                if (result.door != null && result.door.getState() == Door.DoorState.OPENING) {
                    float openingOffset = (doorDrawEnd - doorDrawStart) * result.door.getAnimationProgress();
                    doorDrawStart += openingOffset;
                }

                if (doorDrawEnd > doorDrawStart) {
                    Color doorRenderColor = (result.side == 1) ? doorBlueDarkColor : doorBlueColor;
                    shapeRenderer.setColor(doorRenderColor);
                    shapeRenderer.rect(screenX, doorDrawStart, 1, doorDrawEnd - doorDrawStart);
                }
            }
        } else {
            Color renderColor = (result.side == 1) ? wallDarkColor : wallColor;
            shapeRenderer.setColor(renderColor);
            shapeRenderer.rect(screenX, drawStart, 1, drawEnd - drawStart);
        }
    }

    public void renderAsciiViewToConsole(Player player, Maze maze) {
        int asciiWidth = 120;
        int asciiHeight = 30;
        char[][] screen = new char[asciiHeight][asciiWidth];

        // Initialize screen
        for (int r = 0; r < asciiHeight; r++) {
            for (int c = 0; c < asciiWidth; c++) {
                if (r < asciiHeight / 2) screen[r][c] = '`'; // Ceiling
                else screen[r][c] = '.'; // Floor
            }
        }

        for (int x = 0; x < asciiWidth; x++) {
            float cameraX = 2 * x / (float)asciiWidth - 1;
            Vector2 rayDir = new Vector2(player.getDirectionVector()).add(new Vector2(player.getCameraPlane()).scl(cameraX));

            int mapX = (int)player.getPosition().x;
            int mapY = (int)player.getPosition().y;

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

                if (!isOutOfBounds(mapX, mapY, maze)) {
                    Object obj = maze.getGameObjectAt(mapX, mapY);
                    if (obj instanceof Door) {
                        Door door = (Door) obj;
                        if (door.getState() != Door.DoorState.OPEN) {
                            hit = true;
                        }
                    } else if (maze.isWallBlocking(mapX, mapY, player.getFacing())) {
                        // A more robust check for walls
                        hit = true;
                    }
                } else {
                    hit = true;
                }
                distanceTraveled++;
            }

            if (hit) {
                float perpWallDist;
                if (side == 0) perpWallDist = (sideDist.x - deltaDist.x);
                else perpWallDist = (sideDist.y - deltaDist.y);

                if (perpWallDist <= 0) perpWallDist = 0.1f;

                int lineHeight = (int)(asciiHeight / perpWallDist);
                int drawStart = Math.max(0, -lineHeight / 2 + asciiHeight / 2);
                int drawEnd = Math.min(asciiHeight - 1, lineHeight / 2 + asciiHeight / 2);

                char wallChar = '#'; // Default wall
                if(side == 1) wallChar = '|';
                Object obj = maze.getGameObjectAt(mapX, mapY);
                if (obj instanceof Door) {
                    Door door = (Door) obj;
                    if (door.getState() == Door.DoorState.CLOSED) wallChar = 'D';
                    if (door.getState() == Door.DoorState.OPENING) wallChar = 'd';
                    if (door.getState() == Door.DoorState.OPEN) wallChar = 'O'; // Should not be seen if logic is correct
                }

                for (int y = drawStart; y <= drawEnd; y++) {
                    if (x >= 0 && x < asciiWidth && y >= 0 && y < asciiHeight) {
                        screen[y][x] = wallChar;
                    }
                }
            }
        }

        // Print the char array to console
        StringBuilder sb = new StringBuilder();
        sb.append("\n--- ASCII RENDERER --- Position: (").append(player.getPosition().x).append(", ").append(player.getPosition().y).append(") Facing: ").append(player.getFacing()).append("\n");
        for (int r = 0; r < asciiHeight; r++) {
            for (int c = 0; c < asciiWidth; c++) {
                sb.append(screen[r][c]);
            }
            sb.append('\n');
        }
        System.out.println(sb.toString());
    }

    private enum WallType { WALL, DOOR }

    private static class RaycastResult {
        final float distance;
        final int side;
        final WallType wallType;
        final Door door;
        final float wallX;

        RaycastResult(float distance, int side, WallType wallType, Door door, float wallX) {
            this.distance = distance;
            this.side = side;
            this.wallType = wallType;
            this.door = door;
            this.wallX = wallX;
        }
    }
}
