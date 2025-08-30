package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Player;

public class FirstPersonRenderer {

    private final Color floorColor = new Color(0.2f, 0.4f, 0.2f, 1);
    private final Color ceilingColor = new Color(0.3f, 0.5f, 0.3f, 1);
    private final Color wallColor = new Color(0.5f, 0.8f, 0.5f, 1);
    private final Color wallDarkColor = new Color(0.4f, 0.7f, 0.4f, 1);
    private final Color doorColor = new Color(0.6f, 0.4f, 0.2f, 1);
    private final Color doorFrameColor = new Color(0.4f, 0.2f, 0.1f, 1);

    // Wall direction constants matching the bitmask format
    private static final int WALL_LEFT = 0b00000001;
    private static final int WALL_RIGHT = 0b00000100;
    private static final int WALL_BOTTOM = 0b00010000;
    private static final int WALL_TOP = 0b01000000;
    private static final int DOOR_LEFT = 0b00000010;
    private static final int DOOR_RIGHT = 0b00001000;
    private static final int DOOR_BOTTOM = 0b00100000;
    private static final int DOOR_TOP = 0b10000000;

    public void render(ShapeRenderer shapeRenderer, Player player, Maze maze, Viewport viewport) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Draw floor and ceiling
        shapeRenderer.setColor(ceilingColor);
        shapeRenderer.rect(0, viewport.getWorldHeight() / 2, viewport.getWorldWidth(), viewport.getWorldHeight() / 2);
        shapeRenderer.setColor(floorColor);
        shapeRenderer.rect(0, 0, viewport.getWorldWidth(), viewport.getWorldHeight() / 2);

        // Raycasting loop
        for (int x = 0; x < viewport.getScreenWidth(); x++) {
            RaycastResult result = castRay(player, maze, x, viewport);
            renderWallSlice(shapeRenderer, result, x, viewport);
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
        int side = 0; // 0 for x-side, 1 for y-side

        // Calculate step direction and initial sideDist
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

        // DDA algorithm
        boolean hit = false;
        WallType wallType = WallType.WALL;
        Door hitDoor = null;

        while (!hit) {
            // Jump to next map square, either in x-direction, or in y-direction
            if (sideDist.x < sideDist.y) {
                sideDist.x += deltaDist.x;
                mapX += stepX;
                side = 0;
            } else {
                sideDist.y += deltaDist.y;
                mapY += stepY;
                side = 1;
            }

            // Check if we hit a wall or door
            if (isOutOfBounds(mapX, mapY, maze)) {
                hit = true;
                wallType = WallType.WALL;
            } else {
                int wallData = maze.getWallDataAt(mapX, mapY);
                Object gameObject = maze.getGameObjectAt(mapX, mapY);

                // Check for door objects first
                if (gameObject instanceof Door) {
                    Door door = (Door) gameObject;
                    if (door.getState() != Door.DoorState.OPEN) {
                        hit = true;
                        wallType = WallType.DOOR;
                        hitDoor = door;
                    }
                }
                // Check for walls using the bitmask
                else if (hasWallInDirection(wallData, side, stepX, stepY)) {
                    hit = true;
                    wallType = hasDoorInDirection(wallData, side, stepX, stepY) ? WallType.DOOR_FRAME : WallType.WALL;
                }
            }
        }

        // Calculate distance
        float perpWallDist;
        if (side == 0) {
            perpWallDist = (sideDist.x - deltaDist.x);
        } else {
            perpWallDist = (sideDist.y - deltaDist.y);
        }

        return new RaycastResult(perpWallDist, side, wallType, hitDoor, mapX, mapY);
    }

    private boolean hasWallInDirection(int wallData, int side, int stepX, int stepY) {
        if (side == 0) { // Hit x-side
            if (stepX > 0) {
                return (wallData & WALL_RIGHT) != 0;
            } else {
                return (wallData & WALL_LEFT) != 0;
            }
        } else { // Hit y-side
            if (stepY > 0) {
                return (wallData & WALL_TOP) != 0;
            } else {
                return (wallData & WALL_BOTTOM) != 0;
            }
        }
    }

    private boolean hasDoorInDirection(int wallData, int side, int stepX, int stepY) {
        if (side == 0) { // Hit x-side
            if (stepX > 0) {
                return (wallData & DOOR_RIGHT) != 0;
            } else {
                return (wallData & DOOR_LEFT) != 0;
            }
        } else { // Hit y-side
            if (stepY > 0) {
                return (wallData & DOOR_TOP) != 0;
            } else {
                return (wallData & DOOR_BOTTOM) != 0;
            }
        }
    }

    private boolean isOutOfBounds(int x, int y, Maze maze) {
        return x < 0 || x >= maze.getWidth() || y < 0 || y >= maze.getHeight();
    }

    private void renderWallSlice(ShapeRenderer shapeRenderer, RaycastResult result, int screenX, Viewport viewport) {
        // Calculate wall height on screen
        int lineHeight = (int) (viewport.getWorldHeight() / result.distance);
        float drawStart = Math.max(0, -lineHeight / 2f + viewport.getWorldHeight() / 2f);
        float drawEnd = Math.min(viewport.getWorldHeight() - 1, lineHeight / 2f + viewport.getWorldHeight() / 2f);

        // Choose color based on wall type and lighting
        Color renderColor;
        switch (result.wallType) {
            case DOOR:
                renderColor = doorColor;
                // Apply door animation if present
                if (result.door != null) {
                    float animationProgress = result.door.getAnimationProgress();
                    if (result.door.getState() == Door.DoorState.OPENING) {
                        // Reduce wall height as door opens
                        float heightReduction = lineHeight * animationProgress;
                        drawEnd -= heightReduction;
                    }
                }
                break;
            case DOOR_FRAME:
                renderColor = doorFrameColor;
                break;
            case WALL:
            default:
                // Apply lighting based on which side of the wall we hit
                renderColor = (result.side == 1) ? wallDarkColor : wallColor;
                break;
        }

        shapeRenderer.setColor(renderColor);

        // Render the wall slice
        if (drawEnd > drawStart) {
            shapeRenderer.rect(screenX, drawStart, 1, drawEnd - drawStart);
        }
    }

    public void dispose() {
        // No resources to dispose in this renderer
    }

    // Result class to hold raycast information
    private static class RaycastResult {
        final float distance;
        final int side; // 0 for x-side, 1 for y-side
        final WallType wallType;
        final Door door; // null if not a door
        final int hitX, hitY; // Grid coordinates of hit

        RaycastResult(float distance, int side, WallType wallType, Door door, int hitX, int hitY) {
            this.distance = distance;
            this.side = side;
            this.wallType = wallType;
            this.door = door;
            this.hitX = hitX;
            this.hitY = hitY;
        }
    }

    // Enum for different wall types
    private enum WallType {
        WALL,       // Regular wall
        DOOR,       // Door object (closed/opening/closing)
        DOOR_FRAME  // Wall with door bits set (door frame)
    }
}
