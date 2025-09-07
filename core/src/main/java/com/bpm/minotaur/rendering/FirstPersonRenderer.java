package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Player;

/**
 * Renders the 3D first-person view of the maze using a raycasting algorithm.
 * This class is responsible for drawing the floor, ceiling, walls, and doors.
 * It also generates a depth buffer to allow other renderers (like ItemRenderer)
 * to correctly occlude objects behind walls.
 */
public class FirstPersonRenderer {

    // --- Colors ---
    private final Color floorColor = new Color(0.2f, 0.4f, 0.2f, 1);
    private final Color ceilingColor = new Color(0.3f, 0.5f, 0.3f, 1);
    private final Color wallColor = new Color(0.5f, 0.8f, 0.5f, 1);
    private final Color wallDarkColor = new Color(0.4f, 0.7f, 0.4f, 1); // Shaded for vertical walls
    /** A blue color for doors, inspired by the original game. */
    private final Color doorBlueColor = new Color(0.1f, 0.2f, 0.7f, 1);
    /** A darker, shaded version of the blue door color for vertical surfaces. */
    private final Color doorBlueDarkColor = new Color(0.1f, 0.15f, 0.6f, 1);

    // --- Rendering Constants ---
    /** The visual width of the door, making it appear as one-third of the tile width. */
    private static final float DOOR_WIDTH = 1.0f / 3.0f;
    /** The visual height of the door, making it appear shorter than the wall. */
    private static final float DOOR_HEIGHT_RATIO = 0.8f;

    // --- Bitmasks (matching DebugRenderer and GameScreen) ---
    private static final int WALL_WEST   = 0b00000001;
    private static final int WALL_EAST   = 0b00000100;
    private static final int WALL_SOUTH  = 0b00010000;
    private static final int WALL_NORTH  = 0b01000000;
    private static final int DOOR_WEST   = 0b00000010;
    private static final int DOOR_EAST   = 0b00001000;
    private static final int DOOR_SOUTH  = 0b00100000;
    private static final int DOOR_NORTH  = 0b10000000;

    /** A 1D array holding the distance to the nearest wall for each vertical column of the screen. */
    private float[] depthBuffer;

    /**
     * Returns the depth buffer calculated during the last render pass.
     * This can be used by other renderers to determine occlusion.
     * @return The depth buffer array.
     */
    public float[] getDepthBuffer() {
        return depthBuffer;
    }

    /**
     * The main render method for the first-person view.
     * It draws the ceiling and floor, then casts a ray for each vertical
     * column of the screen to draw the walls and doors.
     * @param shapeRenderer The ShapeRenderer to use for drawing.
     * @param player The player object, for position and direction.
     * @param maze The maze object, for wall and object data.
     * @param viewport The game's viewport, for screen dimensions.
     */
    public void render(ShapeRenderer shapeRenderer, Player player, Maze maze, Viewport viewport) {
        // Initialize or resize the depth buffer to match the screen width.
        if (depthBuffer == null || depthBuffer.length != viewport.getScreenWidth()) {
            depthBuffer = new float[(int)viewport.getScreenWidth()];
        }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Draw the ceiling and floor.
        shapeRenderer.setColor(ceilingColor);
        shapeRenderer.rect(0, viewport.getWorldHeight() / 2, viewport.getWorldWidth(), viewport.getWorldHeight() / 2);
        shapeRenderer.setColor(floorColor);
        shapeRenderer.rect(0, 0, viewport.getWorldWidth(), viewport.getWorldHeight() / 2);

        // --- Raycasting Loop ---
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

    /**
     * Casts a single ray from the player's position using the DDA algorithm.
     * @return A RaycastResult object with hit information, or null if no hit occurred.
     */
    private RaycastResult castRay(Player player, Maze maze, int screenX, Viewport viewport) {
        // This calculation was inverted to fix a mirrored/flipped view.
        // It now correctly maps screen coordinates so that the left side of the view corresponds
        // to rays cast to the left, and the right side to rays cast to the right.
        float cameraX = 1 - 2 * screenX / (float) viewport.getScreenWidth();
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

        float wallX;
        if (side == 0) {
            wallX = player.getPosition().y + perpWallDist * rayDir.y;
        } else {
            wallX = player.getPosition().x + perpWallDist * rayDir.x;
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

    /**
     * Renders a single vertical slice of a wall or door, handling door frames and animations.
     * @param shapeRenderer The ShapeRenderer to use.
     * @param result The result from the raycast containing hit details.
     * @param screenX The screen column to draw on.
     * @param viewport The game viewport for dimension information.
     */
    private void renderWallSlice(ShapeRenderer shapeRenderer, RaycastResult result, int screenX, Viewport viewport) {
        // Calculate the full height of the wall slice based on its distance.
        // The result.distance is the perpendicular distance, which correctly prevents fisheye distortion.
        int lineHeight = (result.distance <= 0) ? Integer.MAX_VALUE : (int) (viewport.getWorldHeight() / result.distance);

        // Calculate the bottom (drawStart) and top (drawEnd) Y coordinates for the slice on the screen.
        float drawStart = Math.max(0, -lineHeight / 2f + viewport.getWorldHeight() / 2f);
        float drawEnd = Math.min(viewport.getWorldHeight(), lineHeight / 2f + viewport.getWorldHeight() / 2f);

        if (result.wallType == WallType.DOOR) {
            // Calculate the horizontal boundaries of the door within its tile.
            float doorEdgeMargin = (1.0f - DOOR_WIDTH) / 2.0f;
            boolean isFramePart = result.wallX < doorEdgeMargin || result.wallX > 1.0f - doorEdgeMargin;

            if (isFramePart) {
                // The ray hit the side frame of the door. Render it as a full-height wall slice.
                Color frameColor = (result.side == 1) ? wallDarkColor : wallColor;
                shapeRenderer.setColor(frameColor);
                shapeRenderer.rect(screenX, drawStart, 1, drawEnd - drawStart);
            } else {
                // The ray hit the central part of the tile where the door is.
                // This requires drawing the lintel (top frame) and the door itself.

                // 1. Calculate door's vertical geometry. It sits on the floor but is shorter than the wall.
                float doorHeight = (drawEnd - drawStart) * DOOR_HEIGHT_RATIO;
                float doorDrawStart = drawStart; // Sits on the floor.
                float doorDrawEnd = drawStart + doorHeight; // Top of the door.

                // 2. Draw the lintel (the frame part above the door).
                Color frameColor = (result.side == 1) ? wallDarkColor : wallColor;
                shapeRenderer.setColor(frameColor);
                if (drawEnd > doorDrawEnd) { // Ensure there is space to draw the lintel
                    shapeRenderer.rect(screenX, doorDrawEnd, 1, drawEnd - doorDrawEnd);
                }

                // 3. Handle the opening animation: the door slides up into the ceiling.
                if (result.door != null && result.door.getState() == Door.DoorState.OPENING) {
                    float openingOffset = (doorDrawEnd - doorDrawStart) * result.door.getAnimationProgress();
                    doorDrawStart += openingOffset; // Move the bottom of the door up.
                }

                // 4. Draw the visible part of the door itself.
                if (doorDrawEnd > doorDrawStart) {
                    Color doorRenderColor = (result.side == 1) ? doorBlueDarkColor : doorBlueColor;
                    shapeRenderer.setColor(doorRenderColor);
                    shapeRenderer.rect(screenX, doorDrawStart, 1, doorDrawEnd - doorDrawStart);
                }
            }
        } else {
            // It's a regular wall. Draw a single, full-height slice.
            Color renderColor = (result.side == 1) ? wallDarkColor : wallColor;
            shapeRenderer.setColor(renderColor);
            shapeRenderer.rect(screenX, drawStart, 1, drawEnd - drawStart);
        }
    }

    /** Enum to differentiate between wall types for rendering. */
    private enum WallType { WALL, DOOR }

    /** A data class to hold all the necessary information from a raycast hit. */
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

