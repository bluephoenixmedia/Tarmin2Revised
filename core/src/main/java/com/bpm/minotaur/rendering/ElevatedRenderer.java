package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Debug elevated renderer: activates when player.getPosZ() > ACTIVATE_THRESHOLD.
 * Renders the maze as true 3D geometry using LibGDX ModelBatch so the player can
 * inspect maze layout from above. Completely replaces the raycaster draw pass while
 * active; the HUD is still drawn on top as normal.
 *
 * Coordinate mapping:  maze(x, y)  →  world(x + 0.5,  worldY,  -(y + 0.5))
 *   - Maze X  →  World +X  (east)
 *   - Maze Y  →  World -Z  (north, matching the commented-out 3D item renderer convention)
 *   - posZ    →  World +Y  (up)
 */
public class ElevatedRenderer {

    /** posZ above which this renderer activates instead of the raycaster. */
    public static final float ACTIVATE_THRESHOLD = 1.15f;

    // Wall-face bit masks (identical to FirstPersonRenderer constants)
    private static final int WALL_WEST  = 0b00000001;
    private static final int WALL_EAST  = 0b00000100;
    private static final int WALL_SOUTH = 0b00010000;
    private static final int WALL_NORTH = 0b01000000;
    private static final int ALL_WALLS  = WALL_WEST | WALL_EAST | WALL_SOUTH | WALL_NORTH; // 0x55 = 85

    // Door-face bit masks
    private static final int DOOR_WEST = 0b00000010;
    private static final int DOOR_EAST = 0b00001000;

    // -------------------------------------------------------------------------
    // LibGDX 3D resources
    // -------------------------------------------------------------------------
    private final PerspectiveCamera camera;
    private final ModelBatch        modelBatch;
    private final Environment       environment;
    private final ModelBuilder      modelBuilder = new ModelBuilder();

    // Shared prototype models — one model per visual type, many instances share it
    private Model wallBoxModel;
    private Model playerMarkerModel;

    // Per-maze instance lists (rebuilt when maze reference changes)
    private final List<ModelInstance> wallInstances   = new ArrayList<>();
    private final List<ModelInstance> doorInstances   = new ArrayList<>();
    private final List<ModelInstance> floorInstances  = new ArrayList<>();
    // Per-maze owned models that must be disposed when geometry is rebuilt
    private final List<Model> ownedModels = new ArrayList<>();

    private Maze cachedMaze = null;

    // -------------------------------------------------------------------------
    // Materials
    // -------------------------------------------------------------------------
    private static final long ATTRIBS =
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;

    private final Material wallMat   = mat(0.62f, 0.57f, 0.52f);
    private final Material floorMat  = mat(0.22f, 0.20f, 0.18f);
    private final Material doorMat   = mat(0.60f, 0.42f, 0.22f);
    private final Material gateMat   = mat(0.40f, 0.38f, 0.35f);
    private final Material playerMat = mat(1.00f, 0.85f, 0.10f);

    // -------------------------------------------------------------------------

    public ElevatedRenderer() {
        // Match GameScreen's virtual resolution so the perspective is correct
        camera = new PerspectiveCamera(70f, 1920f, 900f);
        camera.near = 0.05f;
        camera.far  = 300f;

        modelBatch = new ModelBatch();

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.45f, 0.45f, 0.45f, 1f));
        // Key light from slightly above-left
        environment.add(new DirectionalLight().set(0.85f, 0.82f, 0.78f, -0.4f, -1.0f, -0.3f));
        // Soft fill from the opposite side
        environment.add(new DirectionalLight().set(0.20f, 0.22f, 0.28f,  0.4f, -0.5f,  0.3f));

        // Build the shared prototype models (geometry only — no maze-specific data)
        wallBoxModel    = modelBuilder.createBox(1f, 1f, 1f,  wallMat,   ATTRIBS);
        playerMarkerModel = modelBuilder.createBox(0.3f, 0.6f, 0.3f, playerMat, ATTRIBS);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Called instead of FirstPersonRenderer.render() when posZ > ACTIVATE_THRESHOLD.
     * Clears the colour+depth buffer and draws the full 3D maze view.
     */
    public void render(Player player, Maze maze) {
        // Rebuild static geometry if the maze changed
        if (maze != cachedMaze) {
            buildStaticGeometry(maze);
            cachedMaze = maze;
        }

        updateCamera(player);

        // Clear depth so 3D scene draws cleanly over whatever was previously rendered
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        modelBatch.begin(camera);

        // Static geometry
        for (ModelInstance inst : floorInstances)  modelBatch.render(inst, environment);
        for (ModelInstance inst : wallInstances)   modelBatch.render(inst, environment);
        for (ModelInstance inst : doorInstances)   modelBatch.render(inst, environment);

        // Player marker
        ModelInstance marker = buildPlayerMarker(player);
        modelBatch.render(marker, environment);

        modelBatch.end();
    }

    public void dispose() {
        modelBatch.dispose();
        if (wallBoxModel    != null) wallBoxModel.dispose();
        if (playerMarkerModel != null) playerMarkerModel.dispose();
        disposeOwnedModels();
    }

    // -------------------------------------------------------------------------
    // Camera
    // -------------------------------------------------------------------------

    private void updateCamera(Player player) {
        float px   = player.getPosition().x;
        float py   = player.getPosition().y;
        float posZ = 0.5f; // player.getPosZ();

        // World position — note maze Y → -Z
        camera.position.set(px, posZ, -py);

        // Blend look direction: near threshold = mostly horizontal forward,
        // higher elevation = steeper downward angle
        float t = clamp((posZ - ACTIVATE_THRESHOLD) / 1.2f); // 0..1 over 1.2 units of height
        float fwdX = player.getDirectionVector().x;
        float fwdZ = -player.getDirectionVector().y; // maze Y → -Z

        // At t=0: look forward (horizontal). At t=1: look straight down.
        float lookX = fwdX * (1f - t);
        float lookY = -t;                       // downward
        float lookZ = fwdZ * (1f - t);

        // When nearly straight down the forward vector degenerates; keep 'up' stable
        // by using the player's facing as the up-hint so the maze stays oriented correctly.
        if (t > 0.95f) {
            camera.up.set(fwdX, 0f, fwdZ).nor();
        } else {
            camera.up.set(0f, 1f, 0f);
        }

        camera.direction.set(lookX, lookY, lookZ).nor();
        camera.update();
    }

    // -------------------------------------------------------------------------
    // Geometry builders
    // -------------------------------------------------------------------------

    private void buildStaticGeometry(Maze maze) {
        disposeOwnedModels();
        wallInstances.clear();
        doorInstances.clear();
        floorInstances.clear();

        int w = maze.getWidth();
        int h = maze.getHeight();

        // --- Floor plane ---
        // A thin slab covering the whole maze, sitting just below y=0
        Model floorModel = modelBuilder.createBox(w, 0.04f, h, floorMat, ATTRIBS);
        ownedModels.add(floorModel);
        ModelInstance floorInst = new ModelInstance(floorModel);
        floorInst.transform.setToTranslation(w * 0.5f, -0.02f, -(h * 0.5f));
        floorInstances.add(floorInst);

        // --- Walls and doors ---
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int data = maze.getWallDataAt(x, y);
                Object obj = maze.getGameObjectAt(x, y);

                boolean isSolidWall = (data & ALL_WALLS) == ALL_WALLS;
                boolean isDoor      = obj instanceof Door;
                boolean isGate      = obj instanceof Gate;

                if (isSolidWall) {
                    // Reuse shared wall box model — only the transform differs
                    ModelInstance inst = new ModelInstance(wallBoxModel);
                    inst.transform.setToTranslation(x + 0.5f, 0.5f, -(y + 0.5f));
                    wallInstances.add(inst);

                } else if (isDoor || isGate) {
                    Material mat = isGate ? gateMat : doorMat;
                    addDoorInstance(data, x, y, mat);
                }
            }
        }
    }

    /**
     * Creates a thin panel ModelInstance for a door or gate, oriented along the
     * correct wall face based on the door-direction bits in wallData.
     */
    private void addDoorInstance(int data, int x, int y, Material mat) {
        // Determine orientation from door face bits
        boolean ewFacing = (data & (DOOR_EAST | DOOR_WEST)) != 0;

        // Door panel: full height, full width in one direction, thin in the other
        float bW = ewFacing ? 0.10f : 0.92f; // X width
        float bH = 0.88f;                     // Y height
        float bD = ewFacing ? 0.92f : 0.10f; // Z depth

        Model doorModel = modelBuilder.createBox(bW, bH, bD, mat, ATTRIBS);
        ownedModels.add(doorModel);

        ModelInstance inst = new ModelInstance(doorModel);
        // Position at cell centre, slightly above floor (bottom of panel at y=0)
        inst.transform.setToTranslation(x + 0.5f, bH * 0.5f, -(y + 0.5f));
        doorInstances.add(inst);
    }

    /** Builds a fresh player-marker instance at the player's current position. */
    private ModelInstance buildPlayerMarker(Player player) {
        ModelInstance marker = new ModelInstance(playerMarkerModel);
        float px = player.getPosition().x;
        float py = player.getPosition().y;
        // Sit on the floor, centred at player position
        marker.transform.setToTranslation(px, 0.3f, -py);
        // Rotate to face the player's direction so the marker is oriented correctly
        float angle = (float) Math.toDegrees(
                Math.atan2(player.getDirectionVector().x, player.getDirectionVector().y));
        marker.transform.rotate(0f, 1f, 0f, -angle);
        return marker;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void disposeOwnedModels() {
        for (Model m : ownedModels) m.dispose();
        ownedModels.clear();
    }

    private static Material mat(float r, float g, float b) {
        return new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
