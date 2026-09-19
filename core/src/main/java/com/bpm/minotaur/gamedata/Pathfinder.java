package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.player.Player;

import java.util.*;

/**
 * A simple A* (A-star) pathfinding utility.
 * It finds the shortest path from a start to a goal, avoiding impassable obstacles.
 */
public class Pathfinder {

    /**
     * Internal class representing a node in the A* search.
     */
    private static class Node {
        int x, y;
        int gCost; // Cost from start
        int hCost; // Heuristic (estimated cost to goal)
        Node parent;

        Node(int x, int y) {
            this.x = x;
            this.y = y;
        }

        int fCost() {
            return gCost + hCost;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Node node = (Node) obj;
            return x == node.x && y == node.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    /**
     * Calculates the heuristic (Manhattan distance) between two nodes.
     */
    private static int getHeuristic(Node a, Node b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    /**
     * Finds a path from a start position to a goal position.
     *
     * @param maze   The current maze.
     * @param player The player (to avoid pathing through).
     * @param start  The starting grid coordinate (e.g., the monster).
     * @param goal   The goal grid coordinate (e.g., the player).
     * @return A List of GridPoint2 coordinates representing the path, or an empty list if no path is found.
     */
    public static List<GridPoint2> findPath(Maze maze, Player player, GridPoint2 start, GridPoint2 goal) {
        return findPath(maze, player, start, goal, false);
    }

    public static List<GridPoint2> findPath(Maze maze, Player player, GridPoint2 start, GridPoint2 goal, boolean canOpenDoors) {

        Node startNode = new Node(start.x, start.y);
        Node goalNode = new Node(goal.x, goal.y);

        // PriorityQueue to always get the node with the lowest fCost
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingInt(Node::fCost));
        // HashSet to quickly check if a node has been visited
        HashSet<Node> closedSet = new HashSet<>();

        startNode.gCost = 0;
        startNode.hCost = getHeuristic(startNode, goalNode);
        openSet.add(startNode);

        GridPoint2 tempPos = new GridPoint2(); // To avoid creating new objects in the loop

        while (!openSet.isEmpty()) {
            Node currentNode = openSet.poll(); // Get node with lowest fCost

            if (currentNode.equals(goalNode)) {
                return reconstructPath(currentNode);
            }

            closedSet.add(currentNode);

            // Check all 4 neighbors
            for (int i = 0; i < 4; i++) {
                int nx = currentNode.x;
                int ny = currentNode.y;
                Direction dir;

                if (i == 0) { ny += 1; dir = Direction.NORTH; } // North
                else if (i == 1) { ny -= 1; dir = Direction.SOUTH; } // South
                else if (i == 2) { nx += 1; dir = Direction.EAST; } // East
                else { nx -= 1; dir = Direction.WEST; } // West

                Node neighbor = new Node(nx, ny);

                if (closedSet.contains(neighbor)) {
                    continue; // Already processed this node
                }

                // --- Sanctuary Ward Check ---
                tempPos.set(nx, ny);
                if (maze.getHomeTiles() != null && maze.getHomeTiles().contains(tempPos)) {
                    continue; // Protected shelter sanctuary boundary
                }

                boolean isDoorEdge = false;
                int wallMask = dir.getWallMask();
                int doorMask = wallMask << 1;
                int cellData = maze.getWallDataAt(currentNode.x, currentNode.y);

                if ((cellData & wallMask) != 0) {
                    continue; // Solid wall blocks movement
                }

                if ((cellData & doorMask) != 0) {
                    Object obj1 = maze.getGameObjectAt(currentNode.x, currentNode.y);
                    Object obj2 = maze.getGameObjectAt(nx, ny);
                    Door door = (obj1 instanceof Door) ? (Door) obj1 : ((obj2 instanceof Door) ? (Door) obj2 : null);

                    if (door != null) {
                        if (door.getState() != Door.DoorState.OPEN && door.getState() != Door.DoorState.OPENING) {
                            if (!canOpenDoors) {
                                continue; // Closed door blocks non-door openers
                            }
                            isDoorEdge = true; // Can be opened, +2 cost penalty
                        }
                    } else if (maze.isWallBlocking(currentNode.x, currentNode.y, dir)) {
                        continue;
                    }
                } else if (maze.isWallBlocking(currentNode.x, currentNode.y, dir)) {
                    continue;
                }

                // 2. Check the NODE Scenery
                Scenery s = maze.getScenery().get(tempPos);
                if (s != null && s.isImpassable()) {
                    continue; // Tile is blocked by impassable scenery
                }

                // 3. Check if NODE is passable (ignoring closed door if canOpenDoors)
                if (!maze.isPassable(nx, ny)) {
                    Object targetObj = maze.getGameObjectAt(nx, ny);
                    if (!(canOpenDoors && targetObj instanceof Door)) {
                        continue;
                    }
                    isDoorEdge = true;
                }

                // 4. Check for the Player (unless it's the goal)
                boolean isPlayerTile = (player != null && player.getPosition() != null
                        && nx == (int) player.getPosition().x && ny == (int) player.getPosition().y);

                // The tile is valid if it's not the player OR it's the goal
                if (!isPlayerTile || neighbor.equals(goalNode)) {
                    int stepCost = isDoorEdge ? 3 : 1; // Door opening costs extra movement
                    int newGCost = currentNode.gCost + stepCost;

                    if (newGCost < neighbor.gCost || !openSet.contains(neighbor)) {
                        neighbor.gCost = newGCost;
                        neighbor.hCost = getHeuristic(neighbor, goalNode);
                        neighbor.parent = currentNode;

                        if (!openSet.contains(neighbor)) {
                            openSet.add(neighbor);
                        }
                    }
                }
            }
        }

        return Collections.emptyList(); // No path found
    }

    /**
     * Reconstructs the path from the goal node back to the start.
     */
    private static List<GridPoint2> reconstructPath(Node goalNode) {
        List<GridPoint2> path = new ArrayList<>();
        Node current = goalNode;
        while (current.parent != null) { // Stop before we add the start node itself
            path.add(new GridPoint2(current.x, current.y));
            current = current.parent;
        }
        Collections.reverse(path); // Reverse to get path from start to goal
        return path;
    }
}
