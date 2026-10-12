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
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.rendering.mesh.ChunkMeshBuilder;
import com.bpm.minotaur.screens.MainMenuScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Master Screen for the Fast-Paced 3D Action Combat Prototype:
 * - 3D Perspective camera running continuous WASD, mouse look, dash, jump, slide
 * - "The Crucible" 32x32 combat arena with wall spikes, pillars, and hazards
 * - Visceral melee traces, hitstop micro-pauses, active parries, and F-key kick
 * - Decal monster mobs with telegraphed attack tells and dynamic dismemberment
 * - First-person animated viewmodels, resource shard pickups, and HUD
 */
public class ActionPrototypeScreen implements Screen, com.badlogic.gdx.InputProcessor {

    private final Tarmin2 game;
    private final Screen previousScreen;

    // 3D Rendering & Camera
    private PerspectiveCamera camera3d;
    private DecalBatch decalBatch;
    private List<com.bpm.minotaur.rendering.mesh.ChunkSubMesh> arenaSubMeshes;
    private com.badlogic.gdx.graphics.glutils.ShaderProgram worldShader;
    private Texture wallTexture;
    private Texture floorTexture;
    private Texture monsterTexture;
    private Texture shardTexture;
    private Texture bootTexture;
    private Texture swordTexture;
    private Texture axeTexture;
    private Texture pistolTexture;

    // Controllers & Data
    private ActionArenaBuilder.ArenaData arenaData;
    private ActionPlayerController playerController;
    private ActionCombatEngine combatEngine;
    private final List<ActionMonsterEntity> monsters = new ArrayList<>();

    // 2D Overlay & UI
    private SpriteBatch uiBatch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;

    // State & Wave control
    private int currentWave = 1;
    private boolean godMode = false;
    private boolean mouseCaptured = true;
    private float hitMarkerTimer = 0f;

    public ActionPrototypeScreen(Tarmin2 game, Screen previousScreen) {
        this.game = game;
        this.previousScreen = previousScreen;
    }

    @Override
    public void show() {
        // Set ActionPrototypeScreen as the exclusive InputProcessor to override all other keybindings
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

        // 3. Initialize Textures
        wallTexture = loadOrFallback("images/wall.png", Color.DARK_GRAY);
        floorTexture = loadOrFallback("images/floor.png", Color.GRAY);
        monsterTexture = loadOrFallback("images/monsters/skeleton.png", Color.WHITE);
        shardTexture = loadOrFallback("images/items/ruby.png", Color.RED);
        swordTexture = loadOrFallback("images/items/broadsword.png", Color.LIGHT_GRAY);
        axeTexture = loadOrFallback("images/items/battle_axe.png", Color.FIREBRICK);
        pistolTexture = loadOrFallback("images/items/pistol.png", Color.DARK_GRAY);

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
        int count = 4 + wave * 2;
        int spawnPoints = arenaData.monsterSpawnPoints.size();

        for (int i = 0; i < count; i++) {
            Vector3 sp = arenaData.monsterSpawnPoints.get(i % spawnPoints);
            // Stagger slightly
            Vector3 jittered = new Vector3(sp.x + MathUtils.random(-1.5f, 1.5f), sp.y, sp.z + MathUtils.random(-1.5f, 1.5f));

            Monster.MonsterType mType = Monster.MonsterType.SKELETON;
            if (wave >= 2 && i % 3 == 0) mType = Monster.MonsterType.GHOUL;
            if (wave >= 3 && i % 4 == 0) mType = Monster.MonsterType.HOBGOBLIN;
            if (wave >= 4 && i == 0) mType = Monster.MonsterType.MINOTAUR;

            monsters.add(new ActionMonsterEntity(mType, jittered));
        }
    }

    @Override
    public void render(float delta) {
        // Clamp delta to prevent spiral of death during lag spikes
        delta = Math.min(delta, 0.05f);

        handleScreenHotkeys();

        // 1. Process Mouse Look
        if (mouseCaptured) {
            playerController.processMouseLook(Gdx.input.getDeltaX(), Gdx.input.getDeltaY());
        }

        // 2. Update Player Controller
        playerController.update(delta, arenaData.solidGrid);

        // 3. Update Combat Engine
        combatEngine.update(delta, playerController, monsters);
        if (combatEngine.triggeredHitSound) {
            hitMarkerTimer = 0.12f;
        }

        // 4. Update Monster Entities & AI
        boolean allDead = true;
        for (ActionMonsterEntity monster : monsters) {
            if (!monster.isDead()) allDead = false;

            boolean hitPlayer = monster.update(delta, playerController.position, monsters, arenaData.solidGrid, arenaData.spikeHazards);
            if (hitPlayer && !godMode) {
                combatEngine.onMonsterAttackHitsPlayer(monster, playerController);
            }
        }

        // Auto-advance wave when cleared
        if (allDead && !monsters.isEmpty()) {
            currentWave++;
            spawnWave(currentWave);
        }

        // 5. Sync 3D Camera to Player Transform
        camera3d.position.set(playerController.position.x, playerController.getEyeY(), playerController.position.z);
        camera3d.direction.set(playerController.direction);
        camera3d.up.set(Vector3.Y);
        camera3d.update();

        // 6. Clear Buffer & Begin 3D Rendering Pass
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.06f, 0.05f, 0.07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        // 7. Render 3D Arena Mesh
        renderArenaMesh();

        // 8. Render 3D Decals (Monsters, Shards, Hazard Indicators)
        renderDecalEntities();

        // 9. Render 2D Viewmodels & HUD
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        render2DViewmodelAndHud(delta);
    }

    private void renderArenaMesh() {
        if (arenaSubMeshes == null || worldShader == null) return;
        worldShader.bind();
        worldShader.setUniformMatrix("u_projTrans", camera3d.combined);
        worldShader.setUniformf("u_cameraPos", camera3d.position);
        worldShader.setUniformf("u_fogColor", 0.06f, 0.05f, 0.07f, 1f);
        worldShader.setUniformf("u_fogNear", 20f);
        worldShader.setUniformf("u_fogFar", 80f);
        worldShader.setUniformf("u_brightness", 1.0f);

        for (com.bpm.minotaur.rendering.mesh.ChunkSubMesh subMesh : arenaSubMeshes) {
            subMesh.render(worldShader);
        }
    }

    private void renderDecalEntities() {
        // 1. Monsters
        for (ActionMonsterEntity m : monsters) {
            Decal d = Decal.newDecal(1.2f, 1.6f, new TextureRegion(monsterTexture), true);
            d.setPosition(m.position.x, m.position.y + 0.35f, m.position.z);
            d.lookAt(camera3d.position, camera3d.up);

            if (m.isDead()) {
                // Splayed dead on ground
                d.setColor(0.5f, 0.1f, 0.1f, 0.65f);
                d.setDimensions(1.4f, 0.4f);
            } else if (m.attackTellGlow > 0f) {
                // Telegraphed windup tell glow (bright flashing yellow-red)
                float pulse = 0.5f + 0.5f * MathUtils.sin(m.attackTellGlow * 18f);
                d.setColor(1.0f, 0.35f + 0.65f * pulse, 0.2f, 1.0f);
            } else if (m.state == ActionMonsterEntity.State.STAGGERED) {
                // Staggered blue-grey
                d.setColor(0.4f, 0.6f, 1.0f, 0.9f);
            } else {
                d.setColor(1f, 1f, 1f, 1f);
            }
            decalBatch.add(d);
        }

        // 2. Flying Projectiles
        for (ActionCombatEngine.Projectile p : combatEngine.projectiles) {
            Decal pd = Decal.newDecal(0.45f, 0.45f, new TextureRegion(shardTexture), true);
            pd.setPosition(p.position.x, p.position.y, p.position.z);
            pd.lookAt(camera3d.position, camera3d.up);
            if (p.isFlame) pd.setColor(1.0f, 0.5f, 0.1f, 0.95f);
            else pd.setColor(0.2f, 0.85f, 1.0f, 0.95f);
            decalBatch.add(pd);
        }

        // 3. Shard Pickups (Health & Mana)
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
        if (combatEngine.playerHealth < 40f) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(1f, 0f, 0f, 0.28f * (1f - combatEngine.playerHealth / 40f));
            shapeRenderer.rect(0, 0, sw, sh);
            shapeRenderer.end();
        }

        // Draw First-Person Weapon Viewmodel
        uiBatch.begin();
        float bobX = MathUtils.sin(playerController.bobTimer * 0.5f) * 22f;
        float bobY = Math.abs(MathUtils.cos(playerController.bobTimer)) * 18f;

        float vmX = sw * 0.62f + bobX;
        float vmY = -sh * 0.08f + bobY;
        float vmSize = sh * 0.55f;

        // Weapon sprite selection
        Texture curWeaponTex = swordTexture;
        if (combatEngine.equippedWeapon == ActionCombatEngine.WeaponType.BATTLEAXE) curWeaponTex = axeTexture;
        else if (combatEngine.equippedWeapon == ActionCombatEngine.WeaponType.PISTOL) curWeaponTex = pistolTexture;

        // Swing recoil offset
        if (combatEngine.weaponCooldownTimer > 0f) {
            vmX -= 45f;
            vmY -= 35f;
        }

        // Draw Weapon
        uiBatch.draw(curWeaponTex, vmX, vmY, vmSize, vmSize);

        // Draw Parry Shield / Guard if active
        if (combatEngine.isParrying) {
            uiBatch.setColor(0.4f, 0.8f, 1.0f, 0.75f);
            uiBatch.draw(swordTexture, sw * 0.35f, sh * 0.05f, vmSize * 0.9f, vmSize * 0.9f);
            uiBatch.setColor(Color.WHITE);
        }

        // Draw Kick Boot if kicking
        if (combatEngine.isKicking) {
            uiBatch.setColor(0.85f, 0.7f, 0.5f, 1.0f);
            uiBatch.draw(wallTexture, sw * 0.42f, sh * 0.02f, sw * 0.16f, sh * 0.38f);
            uiBatch.setColor(Color.WHITE);
        }

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

        // Health bar (Red)
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.8f);
        shapeRenderer.rect(barX, barY + 28, barW, barH);
        shapeRenderer.setColor(Color.FIREBRICK);
        shapeRenderer.rect(barX, barY + 28, barW * (combatEngine.playerHealth / combatEngine.maxPlayerHealth), barH);

        // Mana bar (Cyan)
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

        uiBatch.end();
    }

    private void handleScreenHotkeys() {
        // Fallback polling for continuous left-click holding
        if (mouseCaptured && Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            combatEngine.triggerAttack(playerController, monsters);
        }
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
        if (monsterTexture != null) monsterTexture.dispose();
        if (shardTexture != null) shardTexture.dispose();
        if (swordTexture != null) swordTexture.dispose();
        if (axeTexture != null) axeTexture.dispose();
        if (pistolTexture != null) pistolTexture.dispose();
        if (arenaSubMeshes != null) {
            for (com.bpm.minotaur.rendering.mesh.ChunkSubMesh sm : arenaSubMeshes) {
                sm.dispose();
            }
        }
        if (worldShader != null) worldShader.dispose();
    }
}
