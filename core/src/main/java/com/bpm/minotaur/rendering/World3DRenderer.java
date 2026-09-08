package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.Ladder;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Renderable;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.gore.WallDecal;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.lighting.LightSource;
import com.bpm.minotaur.lighting.LightingManager;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.managers.DoomManager;
import com.bpm.minotaur.managers.WorldManager;
import com.bpm.minotaur.rendering.mesh.ChunkMeshBuilder;
import com.bpm.minotaur.rendering.mesh.ChunkSubMesh;
import com.bpm.minotaur.rendering.mesh.DynamicQuadBatcher;
import com.bpm.minotaur.rendering.mesh.WorldMeshCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Full 3D Planar World Rendering Pipeline.
 * Renders the maze world using true 3D planar geometry, LibGDX PerspectiveCamera,
 * OpenGL hardware depth buffer (GL_DEPTH_TEST), chunk sub-meshes, 3D billboards,
 * and a 16-point-light GLSL shader.
 */
public class World3DRenderer implements Disposable {

    private static final String TAG = "World3DRenderer";
    private static final int MAX_LIGHTS = 16;

    private final PerspectiveCamera camera;
    private final ShaderProgram shader;
    private final WorldMeshCache meshCache;
    private final DynamicQuadBatcher dynamicBatcher;

    // Textures
    private final Texture wallTexture;
    private final Texture doorTexture;
    private final Texture gateTexture;
    private final Texture floorTexture;
    private final Texture ceilingTexture;
    private final Texture blankTexture;

    // Optional 3D skybox integration
    private Skybox3DRenderer skybox3DRenderer;

    // Reusable scratch vectors & arrays for camera math and lights
    private final Vector3 camRight = new Vector3();
    private final Vector3 camUp = new Vector3();
    private final Vector3 camDir = new Vector3();
    private final Matrix4 identityMatrix = new Matrix4();

    private final float[] lightPosArray = new float[MAX_LIGHTS * 3];
    private final float[] lightColorArray = new float[MAX_LIGHTS * 3];
    private final float[] lightRadiusArray = new float[MAX_LIGHTS];
    private final float[] lightIntensityArray = new float[MAX_LIGHTS];

    private float totalTime = 0f;

    public World3DRenderer() {
        this.camera = new PerspectiveCamera(70f, 1920f, 1080f);
        this.camera.near = 0.05f;
        this.camera.far = 300f;

        // Compile 3D Shader
        ShaderProgram.pedantic = false;
        this.shader = new ShaderProgram(
                Gdx.files.internal("shaders/world3d.vert"),
                Gdx.files.internal("shaders/world3d.frag")
        );
        if (!shader.isCompiled()) {
            Gdx.app.error(TAG, "Failed to compile world3d shader: " + shader.getLog());
        }

        // Initialize Textures with hardware repeat
        this.wallTexture = new Texture(Gdx.files.internal("images/wall.png"));
        this.wallTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        this.doorTexture = new Texture(Gdx.files.internal("images/door.png"));
        this.gateTexture = new Texture(Gdx.files.internal("images/gate.png"));

        this.floorTexture = new Texture(Gdx.files.internal("images/floor.png"));
        this.floorTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        this.ceilingTexture = new Texture(Gdx.files.internal("images/floor.png"));
        this.ceilingTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pix.setColor(Color.WHITE);
        pix.fill();
        this.blankTexture = new Texture(pix);
        pix.dispose();

        this.meshCache = new WorldMeshCache();
        this.dynamicBatcher = new DynamicQuadBatcher();

        try {
            this.skybox3DRenderer = new Skybox3DRenderer();
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to initialize Skybox3DRenderer: " + e.getMessage());
        }
    }

    public PerspectiveCamera getCamera() {
        return camera;
    }

    /**
     * Main 3D render method called from GameScreen.
     */
    public void render(
            Player player,
            Maze maze,
            Viewport viewport,
            WorldManager worldManager,
            int currentLevel,
            GameMode gameMode,
            CombatManager combatManager
    ) {
        if (player == null || maze == null || !shader.isCompiled()) return;

        float delta = Gdx.graphics.getDeltaTime();
        totalTime += delta;

        // Synchronize player lighting
        LightingManager lm = (worldManager != null) ? worldManager.getLightingManager() : null;
        if (lm != null) {
            lm.update(delta, player, maze);
        }

        // Determine indoor / shelter state
        boolean isInsideHome = maze.isHomeTile((int) player.getPosition().x, (int) player.getPosition().y);
        boolean isIndoors = (currentLevel > 1) || isInsideHome || maze.isIndoors((int) player.getPosition().x, (int) player.getPosition().y);

        // Determine Atmosphere, Fog, and Doom
        Biome biome = (worldManager != null && worldManager.getBiomeManager() != null)
                ? worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId())
                : Biome.FOREST;

        float fogDistance = 1000f;
        Color fogColor = new Color(Color.WHITE);
        boolean fogEnabled = false;
        float lightIntensity = 1.0f;

        if (worldManager != null && worldManager.getWeatherManager() != null && currentLevel == 1) {
            com.bpm.minotaur.weather.WeatherManager wm = worldManager.getWeatherManager();
            fogEnabled = true;
            fogDistance = wm.getFogDistance();
            fogColor.set(wm.getFogColor());
            lightIntensity = wm.getLightIntensity();
        } else if (gameMode == GameMode.ADVANCED && biome != null && biome.hasFogOfWar()) {
            fogEnabled = true;
            fogDistance = (biome == Biome.FOREST) ? 2.0f : biome.getFogDistance();
            fogColor.set(biome.getFogColor());
        }

        float bridgeIntegrity = DoomManager.getInstance().getBridgeIntegrity();
        float doomFactor = 1.0f - ((bridgeIntegrity / 100f) * 0.6f);
        lightIntensity *= doomFactor;
        if (bridgeIntegrity > 50) {
            fogColor.lerp(Color.RED, ((bridgeIntegrity - 50) / 50f) * 0.3f);
        }

        // --- UPDATE 3D CAMERA ---
        updateCamera(player, viewport);

        // --- PASS 0: 3D SKYBOX & HORIZON LANDMARKS (Outdoors Level 1) ---
        boolean canRender3DSky = (currentLevel == 1) && !isIndoors;
        if (canRender3DSky && skybox3DRenderer != null && skybox3DRenderer.isInitialized()) {
            Gdx.gl.glDepthMask(false);
            skybox3DRenderer.render(null, player, viewport, worldManager, DebugManager.getInstance().getRenderMode());
            Gdx.gl.glDepthMask(true);
        } else {
            // In dungeons or indoors without skybox, clear color & depth cleanly
            Color clearCol = isIndoors ? new Color(0.04f, 0.04f, 0.06f, 1f) : fogColor;
            Gdx.gl.glClearColor(clearCol.r, clearCol.g, clearCol.b, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        }

        // --- BIND 3D SHADER & GLOBAL UNIFORMS ---
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);

        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);
        shader.setUniformMatrix("u_worldTrans", identityMatrix);
        shader.setUniformf("u_cameraPos", camera.position);

        boolean isRetro = (DebugManager.getInstance().getRenderMode() == DebugManager.RenderMode.RETRO);
        shader.setUniformi("u_retroMode", isRetro ? 1 : 0);
        shader.setUniformf("u_doomFactor", doomFactor);

        // Ambient color
        Color ambientColor = isIndoors
                ? (isInsideHome ? LightingManager.COLOR_SHELTER_AMBIENT : LightingManager.COLOR_COLD_VOID)
                : Color.WHITE;
        shader.setUniformf("u_ambientColor", ambientColor.r * lightIntensity, ambientColor.g * lightIntensity, ambientColor.b * lightIntensity);

        // Fog
        shader.setUniformf("u_fogEnabled", fogEnabled ? 1.0f : 0.0f);
        shader.setUniformf("u_fogDistance", fogDistance);
        shader.setUniformf("u_fogColor", fogColor.r, fogColor.g, fogColor.b);

        // Setup dynamic point lights
        setupDynamicLights(lm, player);

        // --- PASS 1: OPAQUE CHUNK SUB-MESHES & DYNAMIC SLIDING DOORS ---
        shader.setUniformf("u_alphaCutoff", 0.0f);

        RetroTheme.Theme theme = maze.getTheme();
        if (theme == null) theme = RetroTheme.STANDARD_THEME;

        List<ChunkSubMesh> subMeshes = meshCache.getVisibleSubMeshes(
                maze,
                player.getPosition().x,
                player.getPosition().y,
                currentLevel,
                isIndoors,
                wallTexture,
                floorTexture,
                ceilingTexture,
                worldManager
        );

        for (ChunkSubMesh subMesh : subMeshes) {
            if (isRetro) {
                shader.setUniformf("u_retroBorder", 1.0f);
                if (subMesh.getTexture() == wallTexture) {
                    shader.setUniformf("u_retroColor", theme.wall);
                } else if (subMesh.getTexture() == floorTexture) {
                    shader.setUniformf("u_retroColor", theme.floor);
                } else {
                    shader.setUniformf("u_retroColor", theme.ceiling);
                }
            } else {
                shader.setUniformf("u_retroBorder", 0.0f);
            }
            subMesh.render(shader);
        }

        // Dynamic Doors & Gates
        renderDynamicDoors(maze, player, isRetro, theme);

        // --- PASS 2: ALPHA CUTOUT (Entities, Decals, Floor Corpses) ---
        shader.setUniformf("u_alphaCutoff", 0.1f);
        shader.setUniformf("u_retroBorder", 0.0f);

        // A. Coplanar Wall Decals (Blood splatters)
        renderWallDecals(maze);

        // B. Entities: Monsters, Items, Ladders, Scenery
        renderEntities(maze, player, combatManager, isRetro, theme);

        // --- RESTORE OPENGL STATE ---
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    private void updateCamera(Player player, Viewport viewport) {
        float px = player.getPosition().x;
        float py = player.getPosition().y;
        float pz = 0.5f; // Eye height

        camera.position.set(px, pz, -py);

        // Direction: maze +X -> world +X (East), maze +Y -> world -Z (North)
        Vector2 dir = player.getDirectionVector();
        camDir.set(dir.x, 0f, -dir.y).nor();
        camera.direction.set(camDir);

        camera.up.set(0f, 1f, 0f);

        // Confused / Dizzy Effect Camera Roll
        if (player.getStatusManager().hasEffect(StatusEffectType.CONFUSED)) {
            float dizzyAngle = 2.0f * (float) Math.sin(totalTime * 2.0f);
            camera.up.rotate(camera.direction, dizzyAngle);
        }

        camera.viewportWidth = viewport.getWorldWidth();
        camera.viewportHeight = viewport.getWorldHeight();
        camera.update();

        // Calculate Right and Up unit vectors for billboards
        camRight.set(camera.direction).crs(camera.up).nor();
        camUp.set(camRight).crs(camera.direction).nor();
    }

    private void setupDynamicLights(LightingManager lm, Player player) {
        int count = 0;
        if (lm != null) {
            // Player Light at index 0
            LightSource pl = lm.getPlayerLight();
            if (pl != null) {
                lightPosArray[0] = pl.getPosition().x;
                lightPosArray[1] = 0.5f;
                lightPosArray[2] = -pl.getPosition().y;

                Color c = pl.getCurrentColor();
                lightColorArray[0] = c.r;
                lightColorArray[1] = c.g;
                lightColorArray[2] = c.b;

                lightRadiusArray[0] = pl.getCurrentRadius();
                lightIntensityArray[0] = pl.getCurrentIntensity();
                count = 1;
            }

            // Up to 15 nearby world lights
            Array<LightSource> worldLights = lm.getWorldLights();
            if (worldLights != null && worldLights.size > 0) {
                // Sort by distance to player
                worldLights.sort((a, b) -> Float.compare(
                        player.getPosition().dst2(a.getPosition()),
                        player.getPosition().dst2(b.getPosition())
                ));

                for (int i = 0; i < worldLights.size && count < MAX_LIGHTS; i++) {
                    LightSource wl = worldLights.get(i);
                    int idx = count * 3;
                    lightPosArray[idx]     = wl.getPosition().x;
                    lightPosArray[idx + 1] = 0.5f;
                    lightPosArray[idx + 2] = -wl.getPosition().y;

                    Color c = wl.getCurrentColor();
                    lightColorArray[idx]     = c.r;
                    lightColorArray[idx + 1] = c.g;
                    lightColorArray[idx + 2] = c.b;

                    lightRadiusArray[count]     = wl.getCurrentRadius();
                    lightIntensityArray[count] = wl.getCurrentIntensity();
                    count++;
                }
            }
        }

        shader.setUniform3fv("u_lightPos", lightPosArray, 0, count * 3);
        shader.setUniform3fv("u_lightColor", lightColorArray, 0, count * 3);
        shader.setUniform1fv("u_lightRadius", lightRadiusArray, 0, count);
        shader.setUniform1fv("u_lightIntensity", lightIntensityArray, 0, count);
        shader.setUniformi("u_numLights", count);
    }

    private void renderDynamicDoors(Maze maze, Player player, boolean isRetro, RetroTheme.Theme theme) {
        int px = (int) player.getPosition().x;
        int py = (int) player.getPosition().y;
        int radius = 12;

        int minX = Math.max(0, px - radius);
        int maxX = Math.min(maze.getWidth() - 1, px + radius);
        int minY = Math.max(0, py - radius);
        int maxY = Math.min(maze.getHeight() - 1, py + radius);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                Object obj = maze.getGameObjectAt(x, y);
                if (obj instanceof Door) {
                    Door door = (Door) obj;
                    int wallData = maze.getWallDataAt(x, y);
                    boolean ewFacing = (wallData & (ChunkMeshBuilder.DOOR_EAST | ChunkMeshBuilder.DOOR_WEST)) != 0;

                    Color col = isRetro ? theme.door : Color.WHITE;
                    if (isRetro) {
                        shader.setUniformf("u_retroColor", theme.door);
                        shader.setUniformf("u_retroBorder", 1.0f);
                    }
                    dynamicBatcher.addSlidingDoor(x, y, ewFacing, door.getAnimationProgress(), col);
                    dynamicBatcher.flush(shader, doorTexture);
                } else if (obj instanceof Gate) {
                    Gate gate = (Gate) obj;
                    int wallData = maze.getWallDataAt(x, y);
                    boolean ewFacing = (wallData & (ChunkMeshBuilder.DOOR_EAST | ChunkMeshBuilder.DOOR_WEST)) != 0;

                    Color col = isRetro ? theme.doorDark : Color.WHITE;
                    if (isRetro) {
                        shader.setUniformf("u_retroColor", theme.doorDark);
                        shader.setUniformf("u_retroBorder", 1.0f);
                    }
                    dynamicBatcher.addSlidingDoor(x, y, ewFacing, gate.getAnimationProgress(), col);
                    dynamicBatcher.flush(shader, gateTexture);
                }
            }
        }
    }

    private void renderWallDecals(Maze maze) {
        if (maze.getGoreManager() == null) return;
        java.util.Map<Integer, Array<WallDecal>> allDecals = maze.getGoreManager().getAllWallDecals();
        if (allDecals == null || allDecals.isEmpty()) return;

        Texture currentTex = null;
        for (Array<WallDecal> decals : allDecals.values()) {
            if (decals == null) continue;
            for (int i = 0; i < decals.size; i++) {
                WallDecal decal = decals.get(i);
                if (decal.textureRegion == null) continue;
                Texture tex = decal.textureRegion.getTexture();
                if (currentTex != null && currentTex != tex) {
                    dynamicBatcher.flush(shader, currentTex);
                }
                currentTex = tex;
                dynamicBatcher.addWallDecal(decal, decal.color);
            }
        }
        if (currentTex != null) {
            dynamicBatcher.flush(shader, currentTex);
        }
    }

    private void renderEntities(Maze maze, Player player, CombatManager combatManager, boolean isRetro, RetroTheme.Theme theme) {
        List<Renderable> entities = new ArrayList<>();
        entities.addAll(maze.getItems().values());

        if (combatManager != null && combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE && combatManager.getMonster() != null) {
            entities.add(combatManager.getMonster());
        } else {
            entities.addAll(maze.getMonsters().values());
        }
        entities.addAll(maze.getLadders().values());
        entities.addAll(maze.getScenery().values());

        // Sort entities back-to-front by distance to player
        entities.sort((a, b) -> Float.compare(
                player.getPosition().dst2(b.getPosition()),
                player.getPosition().dst2(a.getPosition())
        ));

        for (Renderable r : entities) {
            float ex = r.getPosition().x;
            float ey = r.getPosition().y;
            float wz = -ey;

            if (r instanceof Monster) {
                Monster m = (Monster) r;
                TextureRegion region = m.getTextureRegion();
                Texture tex = (region != null) ? region.getTexture() : m.getTexture();

                if (tex != null) {
                    if (region == null) {
                        region = new TextureRegion(tex);
                    }
                    float w = m.getScale().x;
                    float h = m.getScale().y;
                    dynamicBatcher.addBillboard(ex, 0.0f, wz, w, h, region, Color.WHITE, camRight, camUp, camDir);
                    dynamicBatcher.flush(shader, tex);
                }
            } else if (r instanceof Item) {
                Item it = (Item) r;
                TextureRegion region = it.getTextureRegion();
                Texture tex = (region != null) ? region.getTexture() : it.getTexture();

                if (tex != null) {
                    if (region == null) {
                        region = new TextureRegion(tex);
                    }
                    float w = 0.5f;
                    float h = 0.5f;
                    ItemTemplate t = it.getTemplate();
                    if (t != null && t.modelScale > 0) {
                        w *= t.modelScale;
                        h *= t.modelScale;
                    }
                    dynamicBatcher.addBillboard(ex, 0.05f, wz, w, h, region, it.getColor(), camRight, camUp, camDir);
                    dynamicBatcher.flush(shader, tex);
                }
            } else if (r instanceof Scenery) {
                Scenery sc = (Scenery) r;
                Texture tex = sc.getTexture();
                if (tex != null) {
                    TextureRegion reg = new TextureRegion(tex);
                    dynamicBatcher.addBillboard(ex, 0.0f, wz, 1.0f, 1.0f, reg, Color.WHITE, camRight, camUp, camDir);
                    dynamicBatcher.flush(shader, tex);
                }
            } else if (r instanceof Ladder) {
                Ladder ld = (Ladder) r;
                // Render ladder as a 3D billboard
                dynamicBatcher.addBillboard(ex, 0.0f, wz, 0.8f, 1.0f, new TextureRegion(doorTexture), Color.WHITE, camRight, camUp, camDir);
                dynamicBatcher.flush(shader, doorTexture);
            }
        }
    }

    public void invalidateMeshCache() {
        meshCache.invalidate();
    }

    @Override
    public void dispose() {
        shader.dispose();
        meshCache.dispose();
        dynamicBatcher.dispose();

        wallTexture.dispose();
        doorTexture.dispose();
        gateTexture.dispose();
        floorTexture.dispose();
        ceilingTexture.dispose();
        blankTexture.dispose();

        if (skybox3DRenderer != null) {
            skybox3DRenderer.dispose();
        }
    }
}
