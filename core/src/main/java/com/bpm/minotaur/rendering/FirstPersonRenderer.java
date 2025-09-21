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

    /**
     * Casts a ray from the player's position in the direction determined by the screen X coordinate
     * and returns information about what the ray hits for rendering purposes.
     *
     * @param player The player object containing position and facing direction
     * @param maze The maze containing wall and door data
     * @param screenX The horizontal screen pixel coordinate (0 to screen width)
     * @param viewport The viewport for screen dimensions
     * @return RaycastResult containing hit information, or null if nothing is hit
     */
    private RaycastResult castRay(Player player, Maze maze, int screenX, Viewport viewport) {
        Vector2 renderPosition;
        // Get the player's current tile coordinates
        int playerX = (int) player.getPosition().x;
        int playerY = (int) player.getPosition().y;
        // Check if there's a wall directly behind the player (affects rendering position)
        boolean isBehindBlocked = maze.isWallBlocking(playerX, playerY, player.getFacing().getOpposite());

        // Determine the starting position for raycasting
        if (isBehindBlocked) {
            // If blocked behind, start from current position
            renderPosition = player.getPosition().cpy();
        } else {
            // If not blocked behind, start from one tile back (allows seeing walls behind player)
            renderPosition = player.getPosition().cpy().sub(player.getDirectionVector());
        }

        // Check if the renderer is starting inside a door tile. If so, we need to ignore it during collision checks.
        int startTileX = (int) renderPosition.x;
        int startTileY = (int) renderPosition.y;
        Object startObject = maze.getGameObjectAt(startTileX, startTileY);
        Door doorToIgnore = null;
        if (startObject instanceof Door) {
            // Store reference to starting door so we don't collide with it
            doorToIgnore = (Door) startObject;
        }

        // Calculate the camera plane offset for this screen column (-1 to +1 range)
        float cameraX = 1 - 2 * screenX / (float) viewport.getScreenWidth();
        // Calculate the ray direction by combining player direction with camera plane offset
        Vector2 rayDir = new Vector2(player.getDirectionVector()).add(new Vector2(player.getCameraPlane()).scl(cameraX));

        // Starting tile coordinates for the DDA algorithm
        int mapX = (int) renderPosition.x;
        int mapY = (int) renderPosition.y;

        // Calculate the distance the ray travels between grid lines
        Vector2 sideDist = new Vector2();
        Vector2 deltaDist = new Vector2(
            // Distance between vertical grid lines along the ray
            (rayDir.x == 0) ? Float.MAX_VALUE : Math.abs(1 / rayDir.x),
            // Distance between horizontal grid lines along the ray
            (rayDir.y == 0) ? Float.MAX_VALUE : Math.abs(1 / rayDir.y)
        );

        // Determine step direction and initial side distances
        int stepX, stepY;
        if (rayDir.x < 0) {
            // Ray pointing left
            stepX = -1;
            sideDist.x = (renderPosition.x - mapX) * deltaDist.x;
        } else {
            // Ray pointing right
            stepX = 1;
            sideDist.x = (mapX + 1.0f - renderPosition.x) * deltaDist.x;
        }
        if (rayDir.y < 0) {
            // Ray pointing down
            stepY = -1;
            sideDist.y = (renderPosition.y - mapY) * deltaDist.y;
        } else {
            // Ray pointing up
            stepY = 1;
            sideDist.y = (mapY + 1.0f - renderPosition.y) * deltaDist.y;
        }

        // DDA algorithm state variables
        boolean hit = false;  // Whether we've hit something that stops the ray
        int side = 0;         // Which side was hit (0=vertical wall, 1=horizontal wall)
        int maxDistance = 50; // Maximum ray distance to prevent infinite loops
        int distanceTraveled = 0; // Counter to enforce maximum distance

        // Track if we hit a door frame (for rendering purposes when door is open)
        boolean hitDoorFrame = false;    // Whether we encountered an open door frame
        int doorFrameMapX = -1, doorFrameMapY = -1;  // Map coordinates of the door frame
        int doorFrameSide = -1;          // Which side the door frame was hit on
        float doorFrameDistance = 0;     // Distance to the door frame
        Door doorFrameObject = null;     // Reference to the door object

        // DDA algorithm - step through the grid until we hit something
        while (!hit && distanceTraveled < maxDistance) {
            // Determine which grid line is closer and step to it
            if (sideDist.x < sideDist.y) {
                // Next vertical grid line is closer
                sideDist.x += deltaDist.x;  // Move to next vertical line
                mapX += stepX;              // Update map X coordinate
                side = 0;                   // Mark as vertical wall hit
            } else {
                // Next horizontal grid line is closer
                sideDist.y += deltaDist.y;  // Move to next horizontal line
                mapY += stepY;              // Update map Y coordinate
                side = 1;                   // Mark as horizontal wall hit
            }

            // Check if we've stepped outside the maze boundaries
            if (isOutOfBounds(mapX, mapY, maze)) {
                hit = true;  // Stop raycast at boundary
            } else {
                // Get wall data for current and previous tiles
                int wallData = maze.getWallDataAt(mapX, mapY);
                int prevMapX = (side == 0) ? mapX - stepX : mapX;  // Previous tile X
                int prevMapY = (side == 1) ? mapY - stepY : mapY;  // Previous tile Y
                int prevWallData = maze.getWallDataAt(prevMapX, prevMapY);

                // Check for wall collision first (solid walls always stop the ray)
                if (hasWallCollision(wallData, prevWallData, side, stepX, stepY)) {
                    hit = true;  // Wall blocks the ray
                }
                // Check for door collision (doors may or may not block depending on state)
                else if (hasDoorCollision(wallData, prevWallData, side, stepX, stepY)) {
                    // Find the door object in either current or previous tile
                    Object obj = maze.getGameObjectAt(mapX, mapY);
                    if (obj == null) obj = maze.getGameObjectAt(prevMapX, prevMapY);

                    // Process door collision if we found a door and it's not the starting door
                    if (obj instanceof Door && obj != doorToIgnore) {
                        Door door = (Door) obj;

                        if (door.getState() == Door.DoorState.OPEN) {
                            // Door is open - record the frame position but continue raycasting
                            if (!hitDoorFrame) { // Only record the first door frame we encounter
                                hitDoorFrame = true;
                                doorFrameMapX = mapX;           // Store frame map coordinates
                                doorFrameMapY = mapY;
                                doorFrameSide = side;           // Store which side was hit
                                // Calculate distance to door frame
                                doorFrameDistance = (side == 0) ? (sideDist.x - deltaDist.x) : (sideDist.y - deltaDist.y);
                                doorFrameObject = door;         // Store door reference
                            }
                            // Continue raycasting through the open door (don't set hit = true)
                        } else {
                            // Door is closed or opening - stop here
                            hit = true;
                        }
                    }
                }
            }
            distanceTraveled++;  // Increment distance counter
        }

        // If we hit something beyond an open door, we need to decide what to render
        if (hit && hitDoorFrame) {
            // We passed through an open door and hit something beyond it
            // We need to determine if the ray passes through the door opening or hits the door frame

            // Recalculate the door frame intersection from current render position
            // This ensures we get the correct perspective as the player moves
            float actualFrameDistance;
            if (doorFrameSide == 0) {
                // Vertical wall intersection - calculate distance to vertical wall line
                float wallXPos = (stepX > 0) ? doorFrameMapX : doorFrameMapX + 1;
                actualFrameDistance = Math.abs((wallXPos - renderPosition.x) / rayDir.x);
            } else {
                // Horizontal wall intersection - calculate distance to horizontal wall line
                float wallYPos = (stepY > 0) ? doorFrameMapY : doorFrameMapY + 1;
                actualFrameDistance = Math.abs((wallYPos - renderPosition.y) / rayDir.y);
            }

            // Calculate the wall position within the door frame using the recalculated distance
            float frameWallX;
            if (doorFrameSide == 0) {
                // For vertical walls, calculate Y position along the wall
                frameWallX = renderPosition.y + actualFrameDistance * rayDir.y;
            } else {
                // For horizontal walls, calculate X position along the wall
                frameWallX = renderPosition.x + actualFrameDistance * rayDir.x;
            }
            frameWallX -= Math.floor(frameWallX);  // Get fractional part (0-1 range)

            // Check if the ray hits the door frame (not the opening)
            float doorEdgeMargin = (1.0f - DOOR_WIDTH) / 2.0f;  // Calculate frame width on each side
            boolean hitsFrame = frameWallX < doorEdgeMargin || frameWallX > 1.0f - doorEdgeMargin;

            if (hitsFrame) {
                // Ray hits the door frame - render the frame with the correct distance
                return new RaycastResult(actualFrameDistance, doorFrameSide, WallType.DOOR, doorFrameObject, frameWallX);
            }
            // else: Ray passes through the door opening - continue with the original hit beyond the door
        }

        // If we didn't hit anything, return null
        if (!hit) return null;

        // Calculate perpendicular distance to the wall (prevents fisheye effect)
        float perpWallDist;
        if (side == 0) {
            // Vertical wall hit
            perpWallDist = (sideDist.x - deltaDist.x);
        } else {
            // Horizontal wall hit
            perpWallDist = (sideDist.y - deltaDist.y);
        }

        // Calculate the exact position where the ray hits the wall (0-1 range)
        float wallX;
        if (side == 0) {
            // For vertical walls, use Y coordinate
            wallX = renderPosition.y + perpWallDist * rayDir.y;
        } else {
            // For horizontal walls, use X coordinate
            wallX = renderPosition.x + perpWallDist * rayDir.x;
        }
        wallX -= Math.floor(wallX);  // Get fractional part

        // Determine what type of surface we hit and get door reference if applicable
        WallType wallType = WallType.WALL;  // Default to regular wall
        Door hitDoor = null;
        if (!isOutOfBounds(mapX, mapY, maze)) {
            // Check both current and previous tiles for door objects
            int prevMapX = (side == 0) ? mapX - stepX : mapX;
            int prevMapY = (side == 1) ? mapY - stepY : mapY;
            Object obj = maze.getGameObjectAt(mapX, mapY);
            if (obj == null) obj = maze.getGameObjectAt(prevMapX, prevMapY);
            if (obj instanceof Door) {
                // We hit a door surface
                wallType = WallType.DOOR;
                hitDoor = (Door) obj;
            }
        }

        // Return the raycast result with all calculated information
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
