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
import com.bpm.minotaur.gamedata.gore.BloodParticle;
import com.bpm.minotaur.gamedata.gore.Gib;
import com.bpm.minotaur.gamedata.gore.GoreManager;
import com.bpm.minotaur.gamedata.gore.SurfaceDecal;
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
import com.bpm.minotaur.weather.WeatherManager;
import com.bpm.minotaur.weather.WeatherRenderer;

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

    // 3D Weather & Precipitation Renderer
    private WeatherRenderer weatherRenderer;

    // Reusable scratch vectors & arrays for camera math and lights
    private final Vector3 camRight = new Vector3();
    private final Vector3 camUp = new Vector3();
    private final Vector3 camDir = new Vector3();
    private final Matrix4 identityMatrix = new Matrix4();

    private final float[] lightPosArray = new float[MAX_LIGHTS * 3];
    private final float[] lightColorArray = new float[MAX_LIGHTS * 3];
    private final float[] lightRadiusArray = new float[MAX_LIGHTS];
    private final float[] lightIntensityArray = new float[MAX_LIGHTS];

    public static final float DEFAULT_FOV = DebugManager.DEFAULT_FOV_3D;
    private float totalTime = 0f;

    public World3DRenderer() {
        this.camera = new PerspectiveCamera(DebugManager.getInstance().getFov3d(), 1920f, 1080f);
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
        boolean canRender3DSky = (currentLevel == 1) && (!isIndoors || isInsideHome);
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

        // Ambient color with lightning flash dynamics
        Color ambientColor = isIndoors
                ? (isInsideHome ? LightingManager.COLOR_SHELTER_AMBIENT : LightingManager.COLOR_COLD_VOID)
                : Color.WHITE;

        if (currentLevel == 1 && worldManager != null && worldManager.getWeatherManager() != null) {
            float flash = worldManager.getWeatherManager().getFlashIntensity();
            if (flash > 0.05f) {
                if (isInsideHome) {
                    // Lightning illuminates through window
                    ambientColor = ambientColor.cpy().lerp(Color.WHITE, flash * 0.45f);
                } else if (!isIndoors) {
                    ambientColor = Color.WHITE;
                }
            }
        }
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

        // --- PASS 2: ALPHA CUTOUT & BLENDING (Entities, Decals, Floor Corpses) ---
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shader.setUniformf("u_alphaCutoff", 0.05f);
        shader.setUniformf("u_retroBorder", 0.0f);
        if (isRetro) {
            shader.setUniformf("u_retroColor", Color.WHITE);
        }

        // A. Gore System: Coplanar Wall Decals, Floor Decals, Particles, Gibs
        renderGore(maze);

        // B. Entities: Monsters, Items, Ladders, Scenery
        renderEntities(maze, player, combatManager, isRetro, theme);

        // --- PASS 3: 3D PRECIPITATION & WEATHER PARTICLES ---
        if (currentLevel == 1 && worldManager != null && worldManager.getWeatherManager() != null) {
            WeatherManager wm = worldManager.getWeatherManager();
            if (wm.isPrecipitation()) {
                if (this.weatherRenderer == null) {
                    this.weatherRenderer = new WeatherRenderer(wm);
                }
                this.weatherRenderer.update(Gdx.graphics.getDeltaTime(), player, maze);

                Gdx.gl.glEnable(GL20.GL_BLEND);
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
                Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
                Gdx.gl.glDepthMask(false); // Depth-test against world geometry without writing to depth buffer

                shader.setUniformf("u_alphaCutoff", 0.0f);
                shader.setUniformf("u_retroBorder", 0.0f);

                this.weatherRenderer.render3D(dynamicBatcher, blankTexture, shader, camera, player, maze, wm, isRetro);

                Gdx.gl.glDepthMask(true);
            }
        }

        // --- RESTORE OPENGL STATE ---
        Gdx.gl.glDisable(GL20.GL_BLEND);
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

        camera.fieldOfView = DebugManager.getInstance().getFov3d();
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
                    boolean ewFacing = door.getOrientation() == Door.Orientation.EAST_WEST;

                    Color col = isRetro ? theme.door : Color.WHITE;
                    if (isRetro) {
                        shader.setUniformf("u_retroColor", theme.door);
                        shader.setUniformf("u_retroBorder", 1.0f);
                    }
                    dynamicBatcher.addSlidingDoor(x, y, ewFacing, door.getAnimationProgress(), col);
                    dynamicBatcher.flush(shader, doorTexture);
                } else if (obj instanceof Gate) {
                    Gate gate = (Gate) obj;
                    boolean ewFacing = gate.getOrientation() == Door.Orientation.EAST_WEST;

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

    private void renderGore(Maze maze) {
        GoreManager gore = maze.getGoreManager();
        if (gore == null) return;

        Texture currentTex = null;

        // 1. Coplanar Wall Decals
        java.util.Map<Integer, Array<WallDecal>> allDecals = gore.getAllWallDecals();
        if (allDecals != null && !allDecals.isEmpty()) {
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
        }

        // 2. Floor Surface Decals (Blood Pools)
        Array<SurfaceDecal> surfaceDecals = gore.getActiveDecals();
        if (surfaceDecals != null && surfaceDecals.size > 0) {
            for (int i = 0; i < surfaceDecals.size; i++) {
                SurfaceDecal d = surfaceDecals.get(i);
                if (d.textureRegion == null) continue;
                Texture tex = d.textureRegion.getTexture();
                if (currentTex != null && currentTex != tex) {
                    dynamicBatcher.flush(shader, currentTex);
                }
                currentTex = tex;
                float splatSize = Math.max(0.12f, d.size);
                dynamicBatcher.addFloorQuad(
                        d.position.x, 0.002f, -d.position.z,
                        splatSize, splatSize,
                        d.textureRegion, d.color
                );
            }
        }

        // 3. Active Flying Blood Droplets
        Array<BloodParticle> particles = gore.getActiveParticles();
        if (particles != null && particles.size > 0) {
            for (int i = 0; i < particles.size; i++) {
                BloodParticle p = particles.get(i);
                if (p.textureRegion == null || p.onGround) continue;
                Texture tex = p.textureRegion.getTexture();
                if (currentTex != null && currentTex != tex) {
                    dynamicBatcher.flush(shader, currentTex);
                }
                currentTex = tex;
                float pSize = Math.max(0.04f, p.size * 2.0f);
                dynamicBatcher.addBillboard(
                        p.position.x, p.position.y, -p.position.z,
                        pSize, pSize,
                        p.textureRegion, p.color,
                        camRight, camUp, camDir
                );
            }
        }

        // 4. Active Gib Chunks (Using authentic gibs texture files from gore atlas)
        Array<Gib> gibs = gore.getActiveGibs();
        if (gibs != null && gibs.size > 0) {
            for (int i = 0; i < gibs.size; i++) {
                Gib g = gibs.get(i);
                // Strict check: only render authentic gib textures, ignore any monster texture shards
                if (g.textureRegion == null) continue;
                Texture tex = g.textureRegion.getTexture();
                if (currentTex != null && currentTex != tex) {
                    dynamicBatcher.flush(shader, currentTex);
                }
                currentTex = tex;
                float gibSize = 0.18f;
                if (g.onGround) {
                    dynamicBatcher.addFloorQuad(
                            g.position.x, 0.003f, -g.position.z,
                            gibSize * 0.5f, gibSize * 0.5f,
                            g.textureRegion, g.color
                    );
                } else {
                    dynamicBatcher.addBillboard(
                            g.position.x, Math.max(0.02f, g.position.y), -g.position.z,
                            gibSize, gibSize,
                            g.textureRegion, g.color,
                            camRight, camUp, camDir
                    );
                }
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
                    float baseHeight = 0.45f;
                    Vector2 scale = it.getScale();
                    float sx = (scale != null && scale.x > 0) ? scale.x : 0.5f;
                    float sy = (scale != null && scale.y > 0) ? scale.y : 0.5f;

                    float regW = region.getRegionWidth();
                    float regH = region.getRegionHeight();
                    float aspect = (regH > 0) ? (regW / regH) : 1.0f;

                    float h = baseHeight * sy;
                    float w = baseHeight * sx * aspect;

                    ItemTemplate t = it.getTemplate();
                    if (t != null && t.modelScale > 0 && t.modelScale != 1.0f) {
                        w *= t.modelScale;
                        h *= t.modelScale;
                    }

                    // Bounds safety for dungeon floor items
                    h = Math.max(0.06f, Math.min(0.6f, h));
                    w = Math.max(0.06f, Math.min(0.8f, w));

                    // Check if item is at player's feet (same tile as player)
                    float px = player.getPosition().x;
                    float py = player.getPosition().y;
                    float distSq = (ex - px) * (ex - px) + (ey - py) * (ey - py);
                    boolean atFeet = distSq < 0.15f;

                    float renderX = ex;
                    float renderZ = wz;
                    float renderFeetY = 0.01f;

                    if (atFeet) {
                        Vector2 dir = player.getDirectionVector();
                        // Anchor within the player's tile, avoiding wall clipping
                        int playerTileX = (int) px;
                        int playerTileY = (int) py;
                        boolean wallInFront = maze != null && maze.isWallBlocking(playerTileX, playerTileY, player.getFacing());
                        float zOffset = wallInFront ? 0.35f : 0.42f;

                        // Position vertically so the item sits comfortably in the lower view above the HUD banner
                        float halfFovRad = (float) Math.toRadians(camera.fieldOfView * 0.5f);
                        float targetAngle = halfFovRad * 0.76f;
                        float deltaY = zOffset * (float) Math.tan(targetAngle);
                        float targetCenterY = camera.position.y - deltaY;

                        renderFeetY = Math.max(0.01f, targetCenterY - h * 0.5f);
                        renderX = px + dir.x * zOffset;
                        renderZ = -py - dir.y * zOffset;

                        // Scale slightly for close-up presentation at player's feet
                        h = Math.min(0.32f, h * 0.85f);
                        w = Math.min(0.40f, w * 0.85f);
                    }

                    dynamicBatcher.addBillboard(renderX, renderFeetY, renderZ, w, h, region, it.getColor(), camRight, camUp, camDir);
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
