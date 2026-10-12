package com.bpm.minotaur.action;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.rendering.mesh.ChunkMeshBuilder;
import com.bpm.minotaur.rendering.mesh.ChunkSubMesh;
import com.bpm.minotaur.screens.MainMenuScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Master Screen for the Fast-Paced 3D Action Combat Prototype:
 * - 3D Perspective camera running continuous WASD, mouse look, dash, jump, slide
 * - "The Crucible" 32x32 combat arena with wall spikes, pillars, and hazards
 * - Visceral melee traces, hitstop micro-pauses, active parries, and F-key kick
 * - Decal monster mobs with telegraphed attack tells and dynamic dismemberment
 * - 3D ballistic blood particles, flying gib chunks, and visor blood splatters
 * - First-person animated viewmodels with weapon aspect preservation and swing arcs
 */
public class ActionPrototypeScreen implements Screen, com.badlogic.gdx.InputProcessor {

    private final Tarmin2 game;
    private final Screen previousScreen;

    // 3D Rendering & Camera
    private PerspectiveCamera camera3d;
    private DecalBatch decalBatch;
    private List<ChunkSubMesh> arenaSubMeshes;
    private com.badlogic.gdx.graphics.glutils.ShaderProgram worldShader;
    private final Matrix4 identityMatrix = new Matrix4();

    // Textures - Arena & Weapons
    private Texture wallTexture;
    private Texture floorTexture;
    private Texture swordTexture;
    private Texture axeTexture;
    private Texture pistolTexture;
    private Texture crossbowTexture;
    private Texture bookTexture;
    private Texture shieldTexture;
    private Texture bootTexture;
    private Texture shardTexture;

    // Textures - Monsters
    private Texture skeletonTex;
    private Texture ghoulTex;
    private Texture hobgoblinTex;
    private Texture minotaurTex;

    // Textures - Gore & Blood
    private Texture bloodDropTex;
    private Texture bloodSpatterTex;
    private Texture bloodSmearTex;
    private Texture[] gibTextures;

    // Controllers & Data
    private ActionArenaBuilder.ArenaData arenaData;
    private ActionPlayerController playerController;
    private ActionCombatEngine combatEngine;
    private final List<ActionMonsterEntity> monsters = new ArrayList<>();

    // 3D & 2D Gore Particle Systems
    public static class BloodParticle3D {
        public final Vector3 position = new Vector3();
        public final Vector3 velocity = new Vector3();
        public float life;
        public final float maxLife;
        public final float size;
        public boolean onFloor = false;

        public BloodParticle3D(Vector3 pos, Vector3 vel, float life, float size) {
            this.position.set(pos);
            this.velocity.set(vel);
            this.life = life;
            this.maxLife = life;
            this.size = size;
        }

        public void update(float delta) {
            life -= delta;
            if (!onFloor) {
                velocity.y -= 14.0f * delta; // Gravity
                position.add(velocity.x * delta, velocity.y * delta, velocity.z * delta);
                if (position.y <= 0.03f) {
                    position.y = 0.03f;
                    velocity.setZero();
                    onFloor = true;
                }
            }
        }
    }

    public static class Gib3D {
        public final Vector3 position = new Vector3();
        public final Vector3 velocity = new Vector3();
        public float rotation = 0f;
        public float rotSpeed;
        public float life;
        public final float maxLife;
        public final float size;
        public final Texture texture;
        public boolean onFloor = false;

        public Gib3D(Vector3 pos, Vector3 vel, float life, float size, Texture tex) {
            this.position.set(pos);
            this.velocity.set(vel);
            this.life = life;
            this.maxLife = life;
            this.size = size;
            this.texture = tex;
            this.rotSpeed = MathUtils.random(-360f, 360f);
        }

        public void update(float delta) {
            life -= delta;
            rotation += rotSpeed * delta;
            if (!onFloor) {
                velocity.y -= 16.0f * delta;
                position.add(velocity.x * delta, velocity.y * delta, velocity.z * delta);
                if (position.y <= 0.07f) {
                    position.y = 0.07f;
                    if (velocity.y < -3.0f) {
                        velocity.y = -velocity.y * 0.35f; // Bounce
                        velocity.scl(0.55f);
                    } else {
                        velocity.setZero();
                        onFloor = true;
                    }
                }
            }
        }
    }

    public static class VisorBlood {
        public float x, y, size, rotation, alpha;
        public final Texture texture;

        public VisorBlood(float x, float y, float size, float rotation, Texture texture) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.rotation = rotation;
            this.alpha = 1.0f;
            this.texture = texture;
        }
    }

    private final List<BloodParticle3D> bloodParticles = new ArrayList<>();
    private final List<Gib3D> gibParticles = new ArrayList<>();
    private final List<VisorBlood> visorBloodList = new ArrayList<>();

    // 2D Overlay & UI
    private SpriteBatch uiBatch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;

    // State, Waves & Death
    private int currentWave = 1;
    private boolean godMode = false;
    private boolean mouseCaptured = true;
    private float hitMarkerTimer = 0f;
    private float respawnTimer = 0f;

    public ActionPrototypeScreen(Tarmin2 game, Screen previousScreen) {
        this.game = game;
        this.previousScreen = previousScreen;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(this);
        Gdx.input.setCursorCatched(true);
        mouseCaptured = true;

        // 1. Initialize 3D Camera (high-action 78 FOV)
        camera3d = new PerspectiveCamera(78f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera3d.near = 0.05f;
        camera3d.far = 300f;

        // 2. Build Crucible Arena
        arenaData = ActionArenaBuilder.buildCrucibleArena();
        playerController = new ActionPlayerController(arenaData.playerSpawn);
        combatEngine = new ActionCombatEngine();

        // Wire Combat Event Listener for Blood & Gore
        combatEngine.eventListener = new ActionCombatEngine.CombatEventListener() {
            @Override
            public void onMonsterHit(Vector3 pos, Vector3 hitDirection, boolean isLethal) {
                hitMarkerTimer = 0.14f;
                spawnBloodBurst(pos, hitDirection, isLethal ? 35 : 18);
                if (isLethal) {
                    spawnGibExplosion(pos);
                }
                if (pos.dst(playerController.position) < 2.5f) {
                    spawnVisorBlood(isLethal ? 3 : 1);
                }
            }

            @Override
            public void onPlayerHit() {
                spawnVisorBlood(2);
            }
        };

        // 3. Initialize Textures (Real Assets)
        wallTexture = loadOrFallback("images/wall.png", Color.DARK_GRAY);
        floorTexture = loadOrFallback("images/floor.png", Color.GRAY);
        skeletonTex = loadOrFallback("images/monsters/skeleton.png", Color.WHITE);
        ghoulTex = loadOrFallback("images/monsters/ghoul.png", Color.LIGHT_GRAY);
        hobgoblinTex = loadOrFallback("images/monsters/hobgoblin.png", Color.ORANGE);
        minotaurTex = loadOrFallback("images/monsters/minotaur.png", Color.RED);

        shardTexture = loadOrFallback("images/items/ruby.png", Color.RED);
        swordTexture = loadOrFallback("images/weapons/sword_broad.png", Color.LIGHT_GRAY);
        axeTexture = loadOrFallback("images/weapons/battle_axe.png", Color.FIREBRICK);
        pistolTexture = loadOrFallback("images/weapons/musket.png", Color.DARK_GRAY);
        crossbowTexture = loadOrFallback("images/weapons/crossbow.png", Color.BROWN);
        bookTexture = loadOrFallback("images/weapons/book.png", Color.PURPLE);
        shieldTexture = loadOrFallback("images/weapons/buckler_spiked.png", Color.LIGHT_GRAY);
        bootTexture = loadOrFallback("images/armor/boots.png", Color.BROWN);

        bloodDropTex = loadOrFallback("images/gore/blood_drop1.png", Color.RED);
        bloodSpatterTex = loadOrFallback("images/gore/blood_spatter.png", Color.RED);
        bloodSmearTex = loadOrFallback("images/gore/blood_smear1.png", Color.RED);

        gibTextures = new Texture[6];
        for (int i = 0; i < 6; i++) {
            gibTextures[i] = loadOrFallback("images/gore/gib" + (i + 1) + ".png", Color.RED);
        }

        // 4. Decal Batch & 3D Mesh
        decalBatch = new DecalBatch(new CameraGroupStrategy(camera3d));
        try {
            arenaSubMeshes = ChunkMeshBuilder.buildChunk(
                    arenaData.maze,
                    0, 0, ActionArenaBuilder.ARENA_SIZE, ActionArenaBuilder.ARENA_SIZE,
                    wallTexture, floorTexture, floorTexture, false);
            if (Gdx.files.internal("shaders/world3d.vert").exists() && Gdx.files.internal("shaders/world3d.frag").exists()) {
                worldShader = new com.badlogic.gdx.graphics.glutils.ShaderProgram(
                        Gdx.files.internal("shaders/world3d.vert"), Gdx.files.internal("shaders/world3d.frag"));
                if (!worldShader.isCompiled()) {
                    Gdx.app.error("ActionPrototype", "Shader error: " + worldShader.getLog());
                }
            }
        } catch (Exception e) {
            Gdx.app.error("ActionPrototype", "Could not build arena chunk mesh: " + e.getMessage());
        }

        // 5. 2D UI & Fonts
        uiBatch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();
        font.getData().setScale(1.3f);

        // Spawn Wave 1
        spawnWave(currentWave);
    }

    private Texture loadOrFallback(String path, Color fallbackColor) {
        if (Gdx.files.internal(path).exists()) {
            return new Texture(Gdx.files.internal(path));
        }
        com.badlogic.gdx.graphics.Pixmap px = new com.badlogic.gdx.graphics.Pixmap(32, 32, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        px.setColor(fallbackColor);
        px.fill();
        Texture tex = new Texture(px);
        px.dispose();
        return tex;
    }

    private void spawnWave(int wave) {
        monsters.clear();
        int count = 3 + wave * 2;
        int spawnPoints = arenaData.monsterSpawnPoints.size();

        for (int i = 0; i < count; i++) {
            Vector3 sp = arenaData.monsterSpawnPoints.get(i % spawnPoints);
            Vector3 jittered = new Vector3(sp.x + MathUtils.random(-1.5f, 1.5f), sp.y, sp.z + MathUtils.random(-1.5f, 1.5f));

            Monster.MonsterType mType = Monster.MonsterType.SKELETON;
            if (wave >= 2 && i % 3 == 0) mType = Monster.MonsterType.GHOUL;
            if (wave >= 3 && i % 4 == 0) mType = Monster.MonsterType.HOBGOBLIN;
            if (wave >= 4 && i == 0) mType = Monster.MonsterType.MINOTAUR;

            monsters.add(new ActionMonsterEntity(mType, jittered));
        }
    }

    private void spawnBloodBurst(Vector3 pos, Vector3 hitDirection, int count) {
        for (int i = 0; i < count; i++) {
            Vector3 vel = new Vector3(hitDirection).nor().scl(MathUtils.random(2.5f, 7.5f));
            vel.add(MathUtils.random(-2.2f, 2.2f), MathUtils.random(1.5f, 4.5f), MathUtils.random(-2.2f, 2.2f));
            bloodParticles.add(new BloodParticle3D(
                    new Vector3(pos).add(MathUtils.random(-0.15f, 0.15f), MathUtils.random(0.1f, 0.5f), MathUtils.random(-0.15f, 0.15f)),
                    vel,
                    MathUtils.random(2.0f, 4.0f),
                    MathUtils.random(0.14f, 0.32f)));
        }
        if (bloodParticles.size() > 250) {
            bloodParticles.subList(0, bloodParticles.size() - 250).clear();
        }
    }

    private void spawnGibExplosion(Vector3 pos) {
        if (gibTextures == null || gibTextures.length == 0) return;
        for (int i = 0; i < 7; i++) {
            Vector3 vel = new Vector3(
                    MathUtils.random(-4.0f, 4.0f),
                    MathUtils.random(3.5f, 8.0f),
                    MathUtils.random(-4.0f, 4.0f));
            Texture gibTex = gibTextures[i % gibTextures.length];
            gibParticles.add(new Gib3D(
                    new Vector3(pos).add(0f, 0.4f, 0f),
                    vel,
                    MathUtils.random(3.5f, 5.5f),
                    MathUtils.random(0.35f, 0.65f),
                    gibTex));
        }
        if (gibParticles.size() > 80) {
            gibParticles.subList(0, gibParticles.size() - 80).clear();
        }
    }

    private void spawnVisorBlood(int count) {
        float sw = Gdx.graphics.getWidth();
        float sh = Gdx.graphics.getHeight();
        for (int i = 0; i < count; i++) {
            float x = MathUtils.random(sw * 0.15f, sw * 0.85f);
            float y = MathUtils.random(sh * 0.15f, sh * 0.85f);
            float size = MathUtils.random(140f, 280f);
            float rot = MathUtils.random(0f, 360f);
            Texture tex = (MathUtils.randomBoolean()) ? bloodSpatterTex : bloodSmearTex;
            visorBloodList.add(new VisorBlood(x, y, size, rot, tex));
        }
        if (visorBloodList.size() > 16) {
            visorBloodList.subList(0, visorBloodList.size() - 16).clear();
        }
    }

    @Override
    public void render(float delta) {
        delta = Math.min(delta, 0.05f);

        handleContinuousInput();

        // 1. Process Mouse Look
        if (mouseCaptured) {
            playerController.processMouseLook(Gdx.input.getDeltaX(), Gdx.input.getDeltaY());
        }

        // 2. Handle Player Defeat & Respawn
        if (combatEngine.playerHealth <= 0f) {
            respawnTimer += delta;
            if (respawnTimer >= 1.8f) {
                combatEngine.playerHealth = combatEngine.maxPlayerHealth;
                playerController.position.set(arenaData.playerSpawn);
                playerController.velocity.setZero();
                combatEngine.playerInvulnTimer = 1.5f;
                respawnTimer = 0f;
            }
        }

        // 3. Update Player Controller
        playerController.update(delta, arenaData.solidGrid);

        // 4. Update Combat Engine
        combatEngine.update(delta, playerController, monsters);

        // 5. Update Monster Entities & AI
        boolean allDead = true;
        for (ActionMonsterEntity monster : monsters) {
            if (!monster.isDead()) allDead = false;

            boolean hitPlayer = monster.update(delta, playerController.position, monsters, arenaData.solidGrid, arenaData.spikeHazards);
            if (hitPlayer && !godMode) {
                combatEngine.onMonsterAttackHitsPlayer(monster, playerController);
            }
        }

        if (allDead && !monsters.isEmpty()) {
            currentWave++;
            spawnWave(currentWave);
        }

        // 6. Update 3D Gore & Blood Particles
        for (int i = bloodParticles.size() - 1; i >= 0; i--) {
            BloodParticle3D bp = bloodParticles.get(i);
            bp.update(delta);
            if (bp.life <= 0f) bloodParticles.remove(i);
        }
        for (int i = gibParticles.size() - 1; i >= 0; i--) {
            Gib3D gib = gibParticles.get(i);
            gib.update(delta);
            if (gib.life <= 0f) gibParticles.remove(i);
        }

        // 7. Sync 3D Camera to Player Transform
        camera3d.position.set(playerController.position.x, playerController.getEyeY(), playerController.position.z);
        camera3d.direction.set(playerController.direction);
        camera3d.up.set(Vector3.Y);
        camera3d.update();

        // 8. Clear Buffer & Begin 3D Rendering Pass
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.06f, 0.05f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);

        // 9. Render 3D Arena Mesh
        renderArenaMesh();

        // 10. Render 3D Decals (Monsters, Shards, 3D Blood & Gibs)
        renderDecalEntities();

        // 11. Render 2D Viewmodels & HUD
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        render2DViewmodelAndHud(delta);
    }

    private void handleContinuousInput() {
        if (mouseCaptured && Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            combatEngine.triggerAttack(playerController, monsters);
        }
    }

    private void renderArenaMesh() {
        if (arenaSubMeshes == null || worldShader == null) return;
        worldShader.bind();
        worldShader.setUniformMatrix("u_projViewTrans", camera3d.combined);
        worldShader.setUniformMatrix("u_worldTrans", identityMatrix);
        worldShader.setUniformf("u_cameraPos", camera3d.position);
        worldShader.setUniformi("u_retroMode", 0);
        worldShader.setUniformf("u_ambientColor", 0.94f, 0.91f, 0.88f);
        worldShader.setUniformf("u_dirLightDir", 0.35f, -0.85f, -0.4f);
        worldShader.setUniformf("u_dirLightColor", 0.78f, 0.74f, 0.70f);
        worldShader.setUniformi("u_numLights", 0);
        worldShader.setUniformf("u_fogEnabled", 0.0f);
        worldShader.setUniformf("u_doomFactor", 0.0f);

        for (ChunkSubMesh subMesh : arenaSubMeshes) {
            subMesh.render(worldShader);
        }
    }

    private void renderDecalEntities() {
        // 1. Monsters
        for (ActionMonsterEntity m : monsters) {
            Texture mTex = skeletonTex;
            if (m.type == Monster.MonsterType.GHOUL) mTex = ghoulTex;
            else if (m.type == Monster.MonsterType.HOBGOBLIN) mTex = hobgoblinTex;
            else if (m.type == Monster.MonsterType.MINOTAUR) mTex = minotaurTex;

            Decal d = Decal.newDecal(1.3f, 1.7f, new TextureRegion(mTex), true);
            d.setPosition(m.position.x, m.position.y + 0.35f, m.position.z);
            d.lookAt(camera3d.position, camera3d.up);

            if (m.isDead()) {
                d.setColor(0.55f, 0.12f, 0.12f, 0.70f);
                d.setDimensions(1.6f, 0.5f);
            } else if (m.attackTellGlow > 0f) {
                float pulse = 0.5f + 0.5f * MathUtils.sin(m.attackTellGlow * 20f);
                d.setColor(1.0f, 0.3f + 0.7f * pulse, 0.1f, 1.0f);
            } else if (m.state == ActionMonsterEntity.State.STAGGERED) {
                d.setColor(0.4f, 0.7f, 1.0f, 0.95f);
            } else {
                d.setColor(1f, 1f, 1f, 1f);
            }
            decalBatch.add(d);
        }

        // 2. 3D Blood Droplets & Splatters
        for (BloodParticle3D bp : bloodParticles) {
            Texture tex = bp.onFloor ? bloodSpatterTex : bloodDropTex;
            Decal bd = Decal.newDecal(bp.size, bp.size, new TextureRegion(tex), true);
            bd.setPosition(bp.position.x, bp.position.y, bp.position.z);
            if (bp.onFloor) {
                bd.setRotationX(90f);
            } else {
                bd.lookAt(camera3d.position, camera3d.up);
            }
            float alpha = Math.min(1.0f, bp.life / (bp.maxLife * 0.4f));
            bd.setColor(0.85f, 0.05f, 0.05f, alpha);
            decalBatch.add(bd);
        }

        // 3. 3D Flying Gib Chunks
        for (Gib3D gib : gibParticles) {
            Decal gd = Decal.newDecal(gib.size, gib.size, new TextureRegion(gib.texture), true);
            gd.setPosition(gib.position.x, gib.position.y, gib.position.z);
            gd.lookAt(camera3d.position, camera3d.up);
            gd.setRotationZ(gib.rotation);
            float alpha = Math.min(1.0f, gib.life / 1.0f);
            gd.setColor(0.9f, 0.2f, 0.2f, alpha);
            decalBatch.add(gd);
        }

        // 4. Projectiles
        for (ActionCombatEngine.Projectile p : combatEngine.projectiles) {
            Decal pd = Decal.newDecal(0.45f, 0.45f, new TextureRegion(shardTexture), true);
            pd.setPosition(p.position.x, p.position.y, p.position.z);
            pd.lookAt(camera3d.position, camera3d.up);
            if (p.isFlame) pd.setColor(1.0f, 0.55f, 0.1f, 0.95f);
            else pd.setColor(0.2f, 0.85f, 1.0f, 0.95f);
            decalBatch.add(pd);
        }

        // 5. Vacuum Shard Pickups
        for (ActionCombatEngine.ShardPickup s : combatEngine.shards) {
            Decal sd = Decal.newDecal(0.35f, 0.35f, new TextureRegion(shardTexture), true);
            sd.setPosition(s.position.x, s.position.y, s.position.z);
            sd.lookAt(camera3d.position, camera3d.up);
            if (s.type == ActionCombatEngine.ShardPickup.ShardType.HEALTH) {
                sd.setColor(1.0f, 0.15f, 0.15f, 0.95f);
            } else {
                sd.setColor(0.15f, 0.6f, 1.0f, 0.95f);
            }
            decalBatch.add(sd);
        }

        decalBatch.flush();
    }

    private void render2DViewmodelAndHud(float delta) {
        float sw = Gdx.graphics.getWidth();
        float sh = Gdx.graphics.getHeight();

        // Screen-space damage vignette if hurt
        if (combatEngine.playerHealth < 45f) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(1f, 0f, 0f, 0.25f * (1f - combatEngine.playerHealth / 45f));
            shapeRenderer.rect(0, 0, sw, sh);
            shapeRenderer.end();
        }

        // Draw First-Person Weapon Viewmodel
        uiBatch.begin();
        float bobX = MathUtils.sin(playerController.bobTimer * 0.5f) * 20f;
        float bobY = Math.abs(MathUtils.cos(playerController.bobTimer)) * 16f;

        // Weapon sprite selection
        Texture curWeaponTex = swordTexture;
        if (combatEngine.equippedWeapon == ActionCombatEngine.WeaponType.BATTLEAXE) curWeaponTex = axeTexture;
        else if (combatEngine.equippedWeapon == ActionCombatEngine.WeaponType.PISTOL) curWeaponTex = pistolTexture;
        else if (combatEngine.equippedWeapon == ActionCombatEngine.WeaponType.CROSSBOW) curWeaponTex = crossbowTexture;
        else if (combatEngine.equippedWeapon == ActionCombatEngine.WeaponType.SPELLBOOK) curWeaponTex = bookTexture;

        // Preserve aspect ratio
        float texW = curWeaponTex.getWidth();
        float texH = curWeaponTex.getHeight();
        float aspect = texW / Math.max(1f, texH);
        float vmHeight = sh * 0.50f;
        float vmWidth = vmHeight * aspect;

        float vmX = sw * 0.68f + bobX;
        float vmY = -sh * 0.05f + bobY;
        float vmRot = 0f;

        // Swing attack kinematics
        if (combatEngine.weaponCooldownTimer > 0f) {
            float t = 1.0f - (combatEngine.weaponCooldownTimer / combatEngine.equippedWeapon.cooldown);
            vmX -= MathUtils.sin(t * MathUtils.PI) * 160f;
            vmY += MathUtils.sin(t * MathUtils.PI) * 70f;
            vmRot = -MathUtils.sin(t * MathUtils.PI) * 45f;
        }

        // Draw weapon viewmodel with rotation around grip base
        uiBatch.draw(new TextureRegion(curWeaponTex),
                vmX, vmY,
                vmWidth * 0.5f, 0f,
                vmWidth, vmHeight,
                1f, 1f,
                vmRot);

        // Draw Parry Shield if active
        if (combatEngine.isParrying) {
            float shieldAspect = (float) shieldTexture.getWidth() / Math.max(1f, shieldTexture.getHeight());
            float shieldH = sh * 0.48f;
            float shieldW = shieldH * shieldAspect;
            uiBatch.setColor(0.5f, 0.85f, 1.0f, 0.85f);
            uiBatch.draw(shieldTexture, sw * 0.18f, sh * 0.02f, shieldW, shieldH);
            uiBatch.setColor(Color.WHITE);
        }

        // Draw Kick Boot if kicking
        if (combatEngine.isKicking) {
            float kickProgress = 1.0f - (combatEngine.kickTimer / ActionCombatEngine.KICK_DURATION);
            float kickY = sh * 0.05f + MathUtils.sin(kickProgress * MathUtils.PI) * (sh * 0.28f);
            float bootAspect = (float) bootTexture.getWidth() / Math.max(1f, bootTexture.getHeight());
            float bootH = sh * 0.44f;
            float bootW = bootH * bootAspect;
            uiBatch.draw(bootTexture, sw * 0.44f - bootW * 0.5f, kickY, bootW, bootH);
        }

        // Draw Visor Screen-Space Blood Spatters
        for (int i = visorBloodList.size() - 1; i >= 0; i--) {
            VisorBlood vb = visorBloodList.get(i);
            vb.alpha -= delta * 0.45f;
            if (vb.alpha <= 0f) {
                visorBloodList.remove(i);
                continue;
            }
            uiBatch.setColor(0.9f, 0.05f, 0.05f, MathUtils.clamp(vb.alpha, 0f, 0.9f));
            uiBatch.draw(new TextureRegion(vb.texture),
                    vb.x - vb.size * 0.5f, vb.y - vb.size * 0.5f,
                    vb.size * 0.5f, vb.size * 0.5f,
                    vb.size, vb.size,
                    1f, 1f,
                    vb.rotation);
        }
        uiBatch.setColor(Color.WHITE);

        uiBatch.end();

        // Reticle & Hit-Markers
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        float cx = sw * 0.5f;
        float cy = sh * 0.5f;

        if (hitMarkerTimer > 0f) {
            hitMarkerTimer -= delta;
            shapeRenderer.setColor(Color.RED);
            shapeRenderer.rect(cx - 14, cy - 14, 6, 6);
            shapeRenderer.rect(cx + 8, cy - 14, 6, 6);
            shapeRenderer.rect(cx - 14, cy + 8, 6, 6);
            shapeRenderer.rect(cx + 8, cy + 8, 6, 6);
        } else {
            shapeRenderer.setColor(Color.WHITE);
            shapeRenderer.circle(cx, cy, 3.5f);
        }

        // Health & Mana Bars (Bottom Center)
        float barW = 280f;
        float barH = 20f;
        float barX = (sw - barW) * 0.5f;
        float barY = 35f;

        // Health bar
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.8f);
        shapeRenderer.rect(barX, barY + 28, barW, barH);
        shapeRenderer.setColor(Color.FIREBRICK);
        shapeRenderer.rect(barX, barY + 28, barW * (combatEngine.playerHealth / combatEngine.maxPlayerHealth), barH);

        // Mana bar
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.8f);
        shapeRenderer.rect(barX, barY, barW, barH);
        shapeRenderer.setColor(Color.CYAN);
        shapeRenderer.rect(barX, barY, barW * (combatEngine.playerMana / combatEngine.maxPlayerMana), barH);

        // Dash Cooldown Pip
        shapeRenderer.setColor(playerController.dashCooldownTimer <= 0f ? Color.GOLD : Color.DARK_GRAY);
        shapeRenderer.circle(barX - 25, barY + 24, 14f);

        shapeRenderer.end();

        // Text HUD & Controls
        uiBatch.begin();
        font.setColor(Color.WHITE);
        font.draw(uiBatch, "CRUCIBLE ARENA | WAVE " + currentWave, 25, sh - 25);
        font.draw(uiBatch, "HP: " + (int) combatEngine.playerHealth + "/" + (int) combatEngine.maxPlayerHealth
                + " | MP: " + (int) combatEngine.playerMana + "/" + (int) combatEngine.maxPlayerMana, barX + 15, barY + 45);

        font.setColor(Color.YELLOW);
        font.draw(uiBatch, "WEAPON: [" + combatEngine.equippedWeapon.name + "] (1-5)", barX, barY + 75);

        font.setColor(Color.LIGHT_GRAY);
        font.draw(uiBatch, "WASD / Arrows: Run | Shift: Dash | Space: Jump | Ctrl: Slide", 25, 95);
        font.draw(uiBatch, "LMB: Attack | RMB: Parry | F: Kick | Q/E: Magic Spells", 25, 65);
        font.draw(uiBatch, "F6 / ESC: Exit Prototype | R: Next Wave | F1: God Mode [" + (godMode ? "ON" : "OFF") + "]", 25, 35);

        // Defeat / Resurrect banner
        if (combatEngine.playerHealth <= 0f) {
            font.setColor(Color.RED);
            font.draw(uiBatch, "YOU WERE SLAIN IN THE CRUCIBLE", sw * 0.5f - 180f, sh * 0.60f);
            font.setColor(Color.GOLD);
            font.draw(uiBatch, "RESURRECTING...", sw * 0.5f - 75f, sh * 0.54f);
        }

        uiBatch.end();
    }

    private void exitPrototype() {
        Gdx.input.setCursorCatched(false);
        game.setScreen(previousScreen != null ? previousScreen : new MainMenuScreen(game));
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.F6) {
            exitPrototype();
            return true;
        }
        if (keycode == Input.Keys.ESCAPE) {
            if (mouseCaptured) {
                Gdx.input.setCursorCatched(false);
                mouseCaptured = false;
            } else {
                exitPrototype();
            }
            return true;
        }
        if (keycode == Input.Keys.R) {
            currentWave++;
            spawnWave(currentWave);
            combatEngine.playerHealth = combatEngine.maxPlayerHealth;
            return true;
        }
        if (keycode == Input.Keys.F1) {
            godMode = !godMode;
            if (godMode) combatEngine.playerHealth = combatEngine.maxPlayerHealth;
            return true;
        }
        if (keycode == Input.Keys.NUM_1) { combatEngine.equippedWeapon = ActionCombatEngine.WeaponType.BROADSWORD; return true; }
        if (keycode == Input.Keys.NUM_2) { combatEngine.equippedWeapon = ActionCombatEngine.WeaponType.BATTLEAXE; return true; }
        if (keycode == Input.Keys.NUM_3) { combatEngine.equippedWeapon = ActionCombatEngine.WeaponType.CROSSBOW; return true; }
        if (keycode == Input.Keys.NUM_4) { combatEngine.equippedWeapon = ActionCombatEngine.WeaponType.PISTOL; return true; }
        if (keycode == Input.Keys.NUM_5) { combatEngine.equippedWeapon = ActionCombatEngine.WeaponType.SPELLBOOK; return true; }

        if (keycode == Input.Keys.Q) {
            combatEngine.castQuickSpellFlame(playerController);
            return true;
        }
        if (keycode == Input.Keys.E) {
            combatEngine.castQuickSpellFrost(playerController);
            return true;
        }
        if (keycode == Input.Keys.F) {
            combatEngine.triggerKick(playerController, monsters);
            return true;
        }
        if (keycode == Input.Keys.SPACE) {
            playerController.triggerJump();
            return true;
        }
        if (keycode == Input.Keys.SHIFT_LEFT || keycode == Input.Keys.SHIFT_RIGHT) {
            playerController.triggerDash();
            return true;
        }
        if (keycode == Input.Keys.CONTROL_LEFT || keycode == Input.Keys.CONTROL_RIGHT) {
            playerController.triggerSlide();
            return true;
        }
        return true;
    }

    @Override public boolean keyUp(int keycode) { return true; }
    @Override public boolean keyTyped(char character) { return true; }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!mouseCaptured) {
            Gdx.input.setCursorCatched(true);
            mouseCaptured = true;
            return true;
        }
        if (button == Input.Buttons.LEFT) {
            combatEngine.triggerAttack(playerController, monsters);
            return true;
        }
        if (button == Input.Buttons.RIGHT) {
            combatEngine.triggerParry();
            return true;
        }
        return true;
    }

    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return true; }
    @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }

    @Override
    public void resize(int width, int height) {
        if (camera3d != null) {
            camera3d.viewportWidth = width;
            camera3d.viewportHeight = height;
            camera3d.update();
        }
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {
        Gdx.input.setCursorCatched(false);
    }

    @Override
    public void dispose() {
        Gdx.input.setCursorCatched(false);
        if (decalBatch != null) decalBatch.dispose();
        if (uiBatch != null) uiBatch.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (font != null) font.dispose();
        if (wallTexture != null) wallTexture.dispose();
        if (floorTexture != null) floorTexture.dispose();
        if (swordTexture != null) swordTexture.dispose();
        if (axeTexture != null) axeTexture.dispose();
        if (pistolTexture != null) pistolTexture.dispose();
        if (crossbowTexture != null) crossbowTexture.dispose();
        if (bookTexture != null) bookTexture.dispose();
        if (shieldTexture != null) shieldTexture.dispose();
        if (bootTexture != null) bootTexture.dispose();
        if (shardTexture != null) shardTexture.dispose();
        if (bloodDropTex != null) bloodDropTex.dispose();
        if (bloodSpatterTex != null) bloodSpatterTex.dispose();
        if (bloodSmearTex != null) bloodSmearTex.dispose();
        if (skeletonTex != null) skeletonTex.dispose();
        if (ghoulTex != null) ghoulTex.dispose();
        if (hobgoblinTex != null) hobgoblinTex.dispose();
        if (minotaurTex != null) minotaurTex.dispose();
        if (gibTextures != null) {
            for (Texture gt : gibTextures) {
                if (gt != null) gt.dispose();
            }
        }
        if (arenaSubMeshes != null) {
            for (ChunkSubMesh sm : arenaSubMeshes) {
                sm.dispose();
            }
        }
        if (worldShader != null) worldShader.dispose();
    }
}
