package com.bpm.minotaur.generation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.telemetry.TelemetryManager.DirectorPacingState;

import java.util.*;

/**
 * Generates a cyclic macro-mission graph across a 3x3 grid of modular chunk archetypes
 * (Start -> Hub -> Champion/Key -> Gate -> Descent), solving socket compatibility.
 */
public class MacroMissionGraphGenerator {

    public static class GeneratedMapResult {
        public final String[] layout;
        public final int width;
        public final int height;
        public final GridPoint2 hubPos;
        public final GridPoint2 championChunkPos;
        public final GridPoint2 descentChunkPos;
        public final ModularChunk[][] chunkGrid;

        public GeneratedMapResult(String[] layout, int width, int height,
                                  GridPoint2 hubPos, GridPoint2 championChunkPos, GridPoint2 descentChunkPos,
                                  ModularChunk[][] chunkGrid) {
            this.layout = layout;
            this.width = width;
            this.height = height;
            this.hubPos = hubPos;
            this.championChunkPos = championChunkPos;
            this.descentChunkPos = descentChunkPos;
            this.chunkGrid = chunkGrid;
        }
    }

    private final ModularChunkLoader loader;
    private final Random random;

    public MacroMissionGraphGenerator(ModularChunkLoader loader, long seed) {
        this.loader = loader;
        this.random = new Random(seed);
    }

    public GeneratedMapResult generate3x3Strata(int strata, DirectorPacingState pacingState,
                                                boolean isStartExpeditionChunk, String[] homeTile) {
        int rows = 3;
        int cols = 3;
        ModularChunk[][] placed = new ModularChunk[rows][cols];

        GridPoint2 hubGrid = new GridPoint2(1, 1);
        GridPoint2 championGrid = new GridPoint2(2, 0); // Bottom-left or chosen corner
        GridPoint2 descentGrid = new GridPoint2(0, 2);  // Top-right or chosen corner

        // 1. Assign archetype targets per cell
        String[][] archetypeTargets = new String[rows][cols];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                archetypeTargets[y][x] = selectWeightedArchetype(pacingState, strata);
            }
        }

        if (isStartExpeditionChunk) {
            archetypeTargets[1][1] = "HOME";
        } else {
            archetypeTargets[1][1] = "FLOODED_CROSSROADS";
        }
        archetypeTargets[championGrid.y][championGrid.x] = "PANOPTICON";
        archetypeTargets[descentGrid.y][descentGrid.x] = "DESCENT_SHAFT";

        // Place choke passages leading into champion and descent rooms
        archetypeTargets[championGrid.y][1] = "CHOKE";
        archetypeTargets[descentGrid.y][1] = "CHOKE";

        // 2. Solve socket matching on the 3x3 grid
        boolean success = solveChunkGrid(placed, archetypeTargets, 0, 0, rows, cols, strata, isStartExpeditionChunk, homeTile);
        if (!success) {
            if (Gdx.app != null) {
                Gdx.app.log("MacroMissionGraphGenerator", "WFC socket backtrack exhausted, using relaxed placement.");
            }
            relaxedPlacement(placed, archetypeTargets, rows, cols, strata, isStartExpeditionChunk, homeTile);
        }

        // 3. Stitch 3x3 12x12 sub-chunks into 36x36 map
        int tileH = 12;
        int tileW = 12;
        int totalH = rows * tileH;
        int totalW = cols * tileW;
        String[] finalLayout = new String[totalH];

        for (int gy = 0; gy < rows; gy++) {
            for (int ty = 0; ty < tileH; ty++) {
                StringBuilder sb = new StringBuilder();
                for (int gx = 0; gx < cols; gx++) {
                    ModularChunk chunk = placed[gy][gx];
                    String[] chunkLayout = chunk.getLayout();
                    sb.append(chunkLayout[ty]);
                }
                finalLayout[gy * tileH + ty] = sb.toString();
            }
        }

        return new GeneratedMapResult(finalLayout, totalW, totalH, hubGrid, championGrid, descentGrid, placed);
    }

    private boolean solveChunkGrid(ModularChunk[][] placed, String[][] targets, int x, int y,
                                  int rows, int cols, int strata, boolean isStart, String[] homeTile) {
        if (y >= rows) return true;
        int nextX = (x + 1) % cols;
        int nextY = (x + 1 == cols) ? y + 1 : y;

        String targetArch = targets[y][x];

        if (targetArch.equals("HOME") && isStart && homeTile != null) {
            Map<String, String> homeSockets = new HashMap<>();
            homeSockets.put("NORTH", "CORRIDOR_1");
            homeSockets.put("EAST", "CORRIDOR_1");
            homeSockets.put("SOUTH", "CORRIDOR_1");
            homeSockets.put("WEST", "CORRIDOR_1");
            ModularChunk homeChunk = new ModularChunk("home_shelter", "HOME", homeSockets, homeTile);
            placed[y][x] = homeChunk;
            if (solveChunkGrid(placed, targets, nextX, nextY, rows, cols, strata, isStart, homeTile)) {
                return true;
            }
            placed[y][x] = null;
            return false;
        }

        // Determine socket constraints from already placed neighbors
        String northReq = (y > 0 && placed[y - 1][x] != null) ? placed[y - 1][x].getSocket("SOUTH") : "*";
        String westReq = (x > 0 && placed[y][x - 1] != null) ? placed[y][x - 1].getSocket("EAST") : "*";

        List<ModularChunk> candidates = loader.findMatchingChunks(northReq, "*", "*", westReq, targetArch, strata);
        if (candidates.isEmpty()) {
            // Relax archetype filter if no candidates match
            candidates = loader.findMatchingChunks(northReq, "*", "*", westReq, null, strata);
        }

        Collections.shuffle(candidates, random);

        for (ModularChunk cand : candidates) {
            placed[y][x] = cand;
            if (solveChunkGrid(placed, targets, nextX, nextY, rows, cols, strata, isStart, homeTile)) {
                return true;
            }
            placed[y][x] = null;
        }

        return false;
    }

    private void relaxedPlacement(ModularChunk[][] placed, String[][] targets, int rows, int cols, int strata,
                                 boolean isStart, String[] homeTile) {
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (placed[y][x] != null) continue;

                if (targets[y][x].equals("HOME") && isStart && homeTile != null) {
                    Map<String, String> hs = new HashMap<>();
                    hs.put("NORTH", "CORRIDOR_1");
                    hs.put("EAST", "CORRIDOR_1");
                    hs.put("SOUTH", "CORRIDOR_1");
                    hs.put("WEST", "CORRIDOR_1");
                    placed[y][x] = new ModularChunk("home_shelter", "HOME", hs, homeTile);
                    continue;
                }

                String northReq = (y > 0 && placed[y - 1][x] != null) ? placed[y - 1][x].getSocket("SOUTH") : "*";
                String westReq = (x > 0 && placed[y][x - 1] != null) ? placed[y][x - 1].getSocket("EAST") : "*";

                List<ModularChunk> matches = loader.findMatchingChunks(northReq, "*", "*", westReq, targets[y][x], strata);
                if (matches.isEmpty()) {
                    matches = loader.findMatchingChunks(northReq, "*", "*", westReq, null, strata);
                }
                if (matches.isEmpty()) {
                    matches = loader.getAllRotatedChunks();
                }

                placed[y][x] = matches.get(random.nextInt(matches.size()));
            }
        }
    }

    private String selectWeightedArchetype(DirectorPacingState pacingState, int strata) {
        // Base weights
        int weightSanctuary = 40;
        int weightTrap = 60;
        int weightChasm = 50;
        int weightDebris = 80;
        int weightVault = 50;
        int weightFlank = 70;

        // Telemetry Director adjustments
        if (pacingState == DirectorPacingState.HIGH_STRAIN) {
            weightSanctuary *= 3; // +200%
            weightTrap = Math.max(15, weightTrap / 2); // -50%
            weightChasm = Math.max(15, weightChasm / 2);
        } else if (pacingState == DirectorPacingState.FLOW) {
            weightTrap = (int) (weightTrap * 1.5f);
            weightVault = (int) (weightVault * 1.3f);
            weightDebris = (int) (weightDebris * 1.4f);
            weightSanctuary = Math.max(10, weightSanctuary / 2);
        }

        int totalWeight = weightSanctuary + weightTrap + weightChasm + weightDebris + weightVault + weightFlank;
        int roll = random.nextInt(totalWeight);

        if ((roll -= weightSanctuary) < 0) return "SANCTUARY";
        if ((roll -= weightTrap) < 0) return "TRAP_ROOM";
        if ((roll -= weightChasm) < 0) return "CHASM_WALKWAY";
        if ((roll -= weightDebris) < 0) return "DEBRIS_FIELD";
        if ((roll -= weightVault) < 0) return "BLIND_VAULT";
        return "ASYMMETRIC_FLANK";
    }
}
