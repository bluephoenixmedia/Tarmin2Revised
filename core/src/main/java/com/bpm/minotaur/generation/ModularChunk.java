package com.bpm.minotaur.generation;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a handcrafted 12x12 modular maze chunk prefab with typed sockets
 * for procedural WFC / grammar assembly.
 */
public class ModularChunk {

    public static final String SOCKET_CORRIDOR_1 = "CORRIDOR_1";
    public static final String SOCKET_CORRIDOR_2 = "CORRIDOR_2";
    public static final String SOCKET_WALL = "WALL";
    public static final String SOCKET_OPEN = "OPEN";

    private String id;
    private String archetype;
    private int minStrata = 1;
    private int maxStrata = 10;
    private int pacingWeight = 100;
    private Map<String, String> sockets = new HashMap<>(); // NORTH, EAST, SOUTH, WEST
    private String[] layout; // 12 strings, each 12 chars
    private Map<String, String> tileLegend = new HashMap<>();
    private int hazardSlots = 0;
    private int decalSlots = 0;
    private int rotation = 0; // 0, 1 (90 CW), 2 (180), 3 (270 CW)

    public ModularChunk() {
    }

    public ModularChunk(String id, String archetype, Map<String, String> sockets, String[] layout) {
        this.id = id;
        this.archetype = archetype;
        this.sockets = new HashMap<>(sockets);
        this.layout = layout;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getArchetype() {
        return archetype;
    }

    public void setArchetype(String archetype) {
        this.archetype = archetype;
    }

    public int getMinStrata() {
        return minStrata;
    }

    public void setMinStrata(int minStrata) {
        this.minStrata = minStrata;
    }

    public int getMaxStrata() {
        return maxStrata;
    }

    public void setMaxStrata(int maxStrata) {
        this.maxStrata = maxStrata;
    }

    public int getPacingWeight() {
        return pacingWeight;
    }

    public void setPacingWeight(int pacingWeight) {
        this.pacingWeight = pacingWeight;
    }

    public Map<String, String> getSockets() {
        return sockets;
    }

    public void setSockets(Map<String, String> sockets) {
        this.sockets = sockets;
    }

    public String getSocket(String direction) {
        return sockets.getOrDefault(direction.toUpperCase(), SOCKET_WALL);
    }

    public String[] getLayout() {
        return layout;
    }

    public void setLayout(String[] layout) {
        this.layout = layout;
    }

    public Map<String, String> getTileLegend() {
        return tileLegend;
    }

    public void setTileLegend(Map<String, String> tileLegend) {
        this.tileLegend = tileLegend;
    }

    public int getHazardSlots() {
        return hazardSlots;
    }

    public void setHazardSlots(int hazardSlots) {
        this.hazardSlots = hazardSlots;
    }

    public int getDecalSlots() {
        return decalSlots;
    }

    public void setDecalSlots(int decalSlots) {
        this.decalSlots = decalSlots;
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    /**
     * Produces a new ModularChunk rotated clockwise by 90 * steps (0..3).
     * Sockets and layout rotate synchronously.
     */
    public ModularChunk createRotated(int steps) {
        int r = (steps % 4 + 4) % 4;
        if (r == 0) {
            ModularChunk copy = new ModularChunk(this.id, this.archetype, this.sockets, this.layout);
            copy.minStrata = this.minStrata;
            copy.maxStrata = this.maxStrata;
            copy.pacingWeight = this.pacingWeight;
            copy.tileLegend = new HashMap<>(this.tileLegend);
            copy.hazardSlots = this.hazardSlots;
            copy.decalSlots = this.decalSlots;
            copy.rotation = 0;
            return copy;
        }

        // 1. Rotate layout 90 degrees clockwise r times
        String[] currentLayout = this.layout;
        for (int step = 0; step < r; step++) {
            int height = currentLayout.length;
            int width = currentLayout[0].length();
            char[][] temp = new char[width][height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    temp[x][height - 1 - y] = currentLayout[y].charAt(x);
                }
            }
            String[] rotated = new String[width];
            for (int x = 0; x < width; x++) {
                rotated[x] = new String(temp[x]);
            }
            currentLayout = rotated;
        }

        // 2. Rotate sockets clockwise
        // North -> East -> South -> West -> North
        // Under 1 CW rotation: New North was Old West, New East was Old North, New South was Old East, New West was Old South
        Map<String, String> newSockets = new HashMap<>();
        String[] dirs = {"NORTH", "EAST", "SOUTH", "WEST"};
        for (int i = 0; i < 4; i++) {
            int oldIndex = (i - r + 4) % 4;
            newSockets.put(dirs[i], this.sockets.getOrDefault(dirs[oldIndex], SOCKET_WALL));
        }

        ModularChunk rotatedChunk = new ModularChunk(this.id + "_r" + r, this.archetype, newSockets, currentLayout);
        rotatedChunk.minStrata = this.minStrata;
        rotatedChunk.maxStrata = this.maxStrata;
        rotatedChunk.pacingWeight = this.pacingWeight;
        rotatedChunk.tileLegend = new HashMap<>(this.tileLegend);
        rotatedChunk.hazardSlots = this.hazardSlots;
        rotatedChunk.decalSlots = this.decalSlots;
        rotatedChunk.rotation = r;
        return rotatedChunk;
    }

    public static boolean areSocketsCompatible(String socketA, String socketB) {
        if (socketA == null || socketB == null) return false;
        if (socketA.equalsIgnoreCase(socketB)) return true;
        // Corridor 1 and Corridor 2 can both connect if designated
        if ((socketA.equals(SOCKET_CORRIDOR_1) && socketB.equals(SOCKET_CORRIDOR_2)) ||
            (socketA.equals(SOCKET_CORRIDOR_2) && socketB.equals(SOCKET_CORRIDOR_1))) {
            return true;
        }
        return false;
    }
}
