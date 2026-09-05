package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.monster.stitcher.CreaturePartLibrary;
import com.bpm.minotaur.gamedata.monster.stitcher.G3djWriter;
import com.bpm.minotaur.gamedata.monster.stitcher.ProceduralPartBuilder;
import com.bpm.minotaur.gamedata.monster.stitcher.StitchedCreature;
import com.bpm.minotaur.rendering.BakedMonsterSprite;
import com.bpm.minotaur.rendering.CreatureBaker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Dev tool — two modes, toggled with TAB:
 *
 *  PARTS VIEW   Grid of every baked individual part sprite.
 *               [←/→] cycle facing   [↑/↓] scroll   [E] export .g3dj   [ESC] back
 *
 *  CREATURE VIEW   Click [GENERATE] (or press G) to assemble a random creature
 *                  from the available part library.  Click [SEVER] buttons or
 *                  press number keys to remove limbs live.  Press [R] to reset.
 *                  [Q/E] cycle facing   [TAB] back to parts view   [ESC] back
 *
 * Open from anywhere via F9.
 */
public class CreatureDevScreen extends BaseScreen {

    // ── Virtual resolution ────────────────────────────────────────────────────
    private static final float VW = 1920f;
    private static final float VH = 1080f;

    // ── Parts-grid layout ─────────────────────────────────────────────────────
    private static final float CELL_W      = 160f;
    private static final float CELL_H      = 185f;
    private static final float SPRITE_SIZE = 128f;
    private static final int   COLS        = (int)(VW / CELL_W);

    // ── Creature-view layout ──────────────────────────────────────────────────
    private static final float SPLIT_X      = 820f;   // x where limb panel starts
    private static final float TOPBAR_H     = 70f;
    private static final float BOTBAR_H     = 110f;
    private static final float MAIN_TOP     = VH - TOPBAR_H;
    private static final float MAIN_BOT     = BOTBAR_H;
    private static final float SPRITE_CX    = SPLIT_X / 2f;
    private static final float SPRITE_CY    = (MAIN_TOP + MAIN_BOT) / 2f + 30f;
    private static final float SPRITE_DISP  = 400f;
    private static final float ROW_H        = 66f;
    private static final float SEVER_W      = 112f;
    private static final float SEVER_H      = 44f;

    private static final String[] FACING    = {"NORTH","EAST","SOUTH","WEST"};

    // Sockets assembled in order; ARM/LEG pairs share the same part-prefix bucket
    private static final String[][] SOCKET_DEFS = {
        // { socket,  part-prefix, optional }
        {"HEAD",  "head",  "false"},
        {"ARM_L", "arm",   "false"},
        {"ARM_R", "arm",   "false"},
        {"LEG_L", "leg",   "false"},
        {"LEG_R", "leg",   "false"},
        {"TAIL",  "tail",  "true" },
    };

    // Per-socket translation offsets so parts don't all pile at the origin
    private static final Map<String,float[]> SOCKET_OFFSET = new HashMap<>();
    static {
        SOCKET_OFFSET.put("HEAD",  new float[]{ 0.00f,  1.05f,  0.00f});
        SOCKET_OFFSET.put("ARM_L", new float[]{-0.55f,  0.40f,  0.00f});
        SOCKET_OFFSET.put("ARM_R", new float[]{ 0.55f,  0.40f,  0.00f});
        SOCKET_OFFSET.put("LEG_L", new float[]{-0.22f, -0.80f,  0.00f});
        SOCKET_OFFSET.put("LEG_R", new float[]{ 0.22f, -0.80f,  0.00f});
        SOCKET_OFFSET.put("TAIL",  new float[]{ 0.00f, -0.55f, -0.15f});
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private enum Mode { PARTS_GRID, CREATURE_VIEW }

    private static final class PartEntry {
        final String id;
        final BakedMonsterSprite sprite;
        PartEntry(String id, BakedMonsterSprite sprite) { this.id = id; this.sprite = sprite; }
    }

    private static final class LimbEntry {
        final String socket;
        final String partId;
        final boolean optional;
        boolean severed = false;
        // hit-test region for SEVER button, set during render
        final float[] btn = {0,0,0,0};

        LimbEntry(String socket, String partId, boolean optional) {
            this.socket   = socket;
            this.partId   = partId;
            this.optional = optional;
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen previousScreen;
    private boolean built = false;
    private Mode mode = Mode.PARTS_GRID;

    // Shared
    private CreaturePartLibrary library;
    private CreatureBaker baker;
    private BitmapFont font;
    private GlyphLayout layout;
    private Texture whitePixel;

    // Parts-grid
    private final List<PartEntry> partEntries = new ArrayList<>();
    private int partFacing = 2;     // SOUTH
    private float gridScroll = 0f;
    private String gridStatus = "";

    // Creature-view
    private Map<String, List<String>> partsByPrefix = new HashMap<>();
    private StitchedCreature currentCreature;
    private BakedMonsterSprite currentSprite;
    // base transforms (translation+scale) stored separately so setFacing can compose correctly
    private Map<String, Matrix4> limbBaseTransforms = new LinkedHashMap<>();
    private final List<LimbEntry> limbEntries = new ArrayList<>();
    private int creatureFacing = 2; // SOUTH
    private long lastSeed = 0;
    private final List<String> severLog = new ArrayList<>();

    // Buttons stored across render for hit-testing
    private final float[] genBtnRgn   = {0,0,0,0};
    private final float[] resetBtnRgn = {0,0,0,0};

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public CreatureDevScreen(Tarmin2 game, Screen previousScreen) {
        super(game);
        this.previousScreen = previousScreen;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(this);
        if (!built) build();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.05f, 0.03f, 0.09f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        SpriteBatch b = game.getBatch();
        b.setProjectionMatrix(game.getViewport().getCamera().combined);
        b.begin();
        if (mode == Mode.PARTS_GRID) renderPartsGrid(b);
        else                         renderCreatureView(b);
        b.end();
    }

    @Override
    public void dispose() {
        if (baker      != null) baker.dispose();
        if (library    != null) library.dispose();
        if (font       != null) font.dispose();
        if (whitePixel != null) whitePixel.dispose();
        for (PartEntry e : partEntries) e.sprite.dispose();
        partEntries.clear();
        if (currentSprite != null) currentSprite.dispose();
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACKSPACE) {
            game.setScreen(previousScreen);
            return true;
        }
        if (keycode == Input.Keys.TAB) {
            mode = (mode == Mode.PARTS_GRID) ? Mode.CREATURE_VIEW : Mode.PARTS_GRID;
            return true;
        }

        if (mode == Mode.PARTS_GRID) {
            switch (keycode) {
                case Input.Keys.LEFT:  partFacing = (partFacing + 3) % 4; return true;
                case Input.Keys.RIGHT: partFacing = (partFacing + 1) % 4; return true;
                case Input.Keys.UP:    gridScroll = Math.max(0, gridScroll - CELL_H); return true;
                case Input.Keys.DOWN:  gridScroll += CELL_H; return true;
                case Input.Keys.E:     exportAll(); return true;
            }
        } else {
            switch (keycode) {
                case Input.Keys.G:     generateCreature(new Random().nextLong()); return true;
                case Input.Keys.Q:     creatureFacing = (creatureFacing + 3) % 4; return true;
                case Input.Keys.E:     creatureFacing = (creatureFacing + 1) % 4; return true;
                case Input.Keys.R:     resetCreature(); return true;
            }
            // 1–9: sever limb by index
            int n = keycode - Input.Keys.NUM_1;
            if (n >= 0 && n < limbEntries.size()) { severLimb(n); return true; }
        }
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (mode == Mode.PARTS_GRID)
            gridScroll = Math.max(0, gridScroll + amountY * 30f);
        return true;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        Vector3 v = new Vector3(screenX, screenY, 0);
        game.getViewport().unproject(v);
        float vx = v.x, vy = v.y;

        if (mode == Mode.CREATURE_VIEW) {
            if (hit(genBtnRgn,   vx, vy)) { generateCreature(new Random().nextLong()); return true; }
            if (hit(resetBtnRgn, vx, vy)) { resetCreature(); return true; }
            for (int i = 0; i < limbEntries.size(); i++) {
                if (hit(limbEntries.get(i).btn, vx, vy)) { severLimb(i); return true; }
            }
        }
        return false;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void build() {
        built = true;

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE); pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();

        font   = new BitmapFont();
        layout = new GlyphLayout();

        library = new CreaturePartLibrary(game.getAssetManager());
        baker   = new CreatureBaker();

        // Queue and synchronously load any file-based parts (e.g. deer .g3db files)
        // that aren't already in the AssetManager from the main loading pass.
        library.queueAssets();
        game.getAssetManager().finishLoading();
        ProceduralPartBuilder.registerAllParts(library);

        // Parts grid always uses the default purple palette
        baker.setDefaultPalette();

        // Bake individual parts for the grid view
        for (String id : library.getPartIds()) {
            Model m = library.get(id);
            if (m == null) continue;
            try {
                float us = library.getUnitScale(id);

                com.badlogic.gdx.math.collision.BoundingBox box =
                        new com.badlogic.gdx.math.collision.BoundingBox();
                new ModelInstance(m).calculateBoundingBox(box);
                com.badlogic.gdx.math.Vector3 c = box.getCenter(new com.badlogic.gdx.math.Vector3());

                // Normalize to uniform 1.4m display height in the 2.2m bake viewport
                float worldHeight = (box.max.y - box.min.y) * us;
                if (worldHeight < 1e-4f) worldHeight = 1.0f;
                float finalScale = us * (1.4f / worldHeight);

                ModelInstance inst = new ModelInstance(m);
                inst.transform.setToTranslation(-finalScale * c.x, 0.8f - finalScale * c.y, -finalScale * c.z)
                              .scale(finalScale, finalScale, finalScale);

                BakedMonsterSprite s = baker.bakeAllFacings(new StitchedCreature(inst));
                partEntries.add(new PartEntry(id, s));
            } catch (Exception ex) {
                Gdx.app.error("CreatureDevScreen", "Bake failed: " + id);
            }
        }
        gridStatus = "Baked " + partEntries.size() + " parts  |  [E] export  [←/→] facing  [TAB] creature view  [ESC] back";
    }

    // ── Creature generation ───────────────────────────────────────────────────

    // All Blender-exported parts share the same world-space coordinate system,
    // so everything can be placed at the origin — no socket offsets needed.
    private static final String[][] SOCKETS = {
        {"HEAD",  "false"},
        {"ARM_L", "false"},
        {"ARM_R", "false"},
        {"LEG_L", "false"},
        {"LEG_R", "false"},
        {"TAIL",  "true" },
    };

    private void generateCreature(long seed) {
        lastSeed = seed;
        Random rng = new Random(seed);

        if (currentSprite != null) { currentSprite.dispose(); currentSprite = null; }
        limbEntries.clear();
        limbBaseTransforms.clear();

        String torsoId = library.hasPart("torso_oval") ? "torso_oval" : null;

        // Compute torso bounds with identity transform so we get raw model-space
        // (Blender/cm) coordinates — same pattern as build() which is known to work.
        float tMinY = 0.3f, tMaxY = 1.3f, tHeight = 1f, tHalfW = 0.15f, overlap = 0.06f;
        ModelInstance torso = null;
        if (torsoId != null) {
            Model torsoModel = library.get(torsoId);
            if (torsoModel != null) {
                float st = library.getUnitScale(torsoId);
                com.badlogic.gdx.math.collision.BoundingBox tBox =
                        new com.badlogic.gdx.math.collision.BoundingBox();
                new ModelInstance(torsoModel).calculateBoundingBox(tBox); // identity → model space
                Vector3 tc = tBox.getCenter(new Vector3());
                float tyOff = 0.8f - st * tc.y;
                tMinY   = st * tBox.min.y + tyOff;
                tMaxY   = st * tBox.max.y + tyOff;
                tHeight = tMaxY - tMinY;
                tHalfW  = st * (tBox.max.x - tBox.min.x) / 2f;
                overlap = tHeight * 0.06f;

                torso = new ModelInstance(torsoModel);
                torso.transform.setToTranslation(-st * tc.x, tyOff, -st * tc.z).scale(st, st, st);
            }
        }

        LinkedHashMap<String, ModelInstance> limbs = new LinkedHashMap<>();
        for (String[] def : SOCKETS) {
            String socket    = def[0];
            boolean optional = Boolean.parseBoolean(def[1]);
            List<String> candidates = library.getPartIdsForSocket(socket);
            if (candidates.isEmpty()) continue;
            String partId = candidates.get(rng.nextInt(candidates.size()));
            Model model = library.get(partId);
            if (model == null) continue;
            float sp = library.getUnitScale(partId);

            // Bounds in model space (identity transform) then scale manually
            com.badlogic.gdx.math.collision.BoundingBox pBox =
                    new com.badlogic.gdx.math.collision.BoundingBox();
            new ModelInstance(model).calculateBoundingBox(pBox);
            Vector3 pc = pBox.getCenter(new Vector3());
            float pMinX = sp * pBox.min.x, pMaxX = sp * pBox.max.x;
            float pMinY = sp * pBox.min.y, pMaxY = sp * pBox.max.y;
            float pCenX = sp * pc.x, pCenY = sp * pc.y, pCenZ = sp * pc.z;

            float tx, ty, tz;
            switch (socket) {
                case "HEAD":
                    tx = -pCenX;
                    ty = (tMaxY - overlap) - pMinY;
                    tz = -pCenZ;
                    break;
                case "LEG_L":
                    tx = -tHalfW * 0.4f - pCenX;
                    ty = (tMinY + overlap) - pMaxY;
                    tz = -pCenZ;
                    break;
                case "LEG_R":
                    tx = tHalfW * 0.4f - pCenX;
                    ty = (tMinY + overlap) - pMaxY;
                    tz = -pCenZ;
                    break;
                case "ARM_L":
                    tx = -tHalfW + overlap - pMaxX;
                    ty = (tMaxY - tHeight * 0.3f) - pCenY;
                    tz = -pCenZ;
                    break;
                case "ARM_R":
                    tx = tHalfW - overlap - pMinX;
                    ty = (tMaxY - tHeight * 0.3f) - pCenY;
                    tz = -pCenZ;
                    break;
                default: // TAIL
                    tx = -pCenX;
                    ty = (tMinY + tHeight * 0.3f) - pCenY;
                    tz = -0.3f - pCenZ;
                    break;
            }

            ModelInstance inst = new ModelInstance(model);
            inst.transform.setToTranslation(tx, ty, tz).scale(sp, sp, sp);
            limbs.put(socket, inst);
            limbBaseTransforms.put(socket, inst.transform.cpy());
            limbEntries.add(new LimbEntry(socket, partId, optional));
        }

        currentCreature = new StitchedCreature(torso, limbs);
        baker.randomizePalette(seed);
        rebake();
        log("torso=" + (torsoId != null ? torsoId : "none") + "  parts=" + limbEntries.size());
    }

    private ModelInstance makeInstance(String partId, float scale, float ox, float oy, float oz) {
        if (partId == null) return null;
        Model m = library.get(partId);
        if (m == null) return null;
        ModelInstance inst = new ModelInstance(m);
        float s = scale * library.getUnitScale(partId);
        inst.transform.idt().translate(ox, oy, oz).scale(s, s, s);
        return inst;
    }

    private void resetCreature() {
        if (lastSeed == 0) generateCreature(new Random().nextLong());
        else               generateCreature(lastSeed);
    }

    private void severLimb(int idx) {
        if (idx < 0 || idx >= limbEntries.size()) return;
        LimbEntry e = limbEntries.get(idx);
        if (e.severed) return;
        e.severed = true;
        currentCreature.removeLimb(e.socket);
        limbBaseTransforms.remove(e.socket);
        rebake();
        log("Severed  [" + e.socket + "]  " + e.partId);
    }

    private void rebake() {
        if (currentSprite != null) currentSprite.dispose();
        currentSprite = (currentCreature != null) ? baker.bakeAllFacings(currentCreature) : null;
    }

    private void log(String msg) {
        severLog.add(0, msg);
        if (severLog.size() > 5) severLog.remove(severLog.size() - 1);
    }

    // ── setFacing — composed with stored base transforms ──────────────────────

    private void applyFacing(float degrees) {
        if (currentCreature == null) return;
        // Rotate the base-transform position around world-Y, preserving local scale
        Matrix4 rot = new Matrix4().setToRotation(Vector3.Y, degrees);
        if (currentCreature.torso != null)
            currentCreature.torso.transform.setToRotation(Vector3.Y, degrees);

        for (LimbEntry e : limbEntries) {
            if (e.severed) continue;
            ModelInstance limb = currentCreature.getLimb(e.socket);
            if (limb == null) continue;
            Matrix4 base = limbBaseTransforms.get(e.socket);
            if (base == null) { limb.transform.setToRotation(Vector3.Y, degrees); continue; }
            limb.transform.set(rot).mul(base);
        }
    }

    // ── Render: PARTS GRID ────────────────────────────────────────────────────

    private void renderPartsGrid(SpriteBatch b) {
        font.setColor(Color.WHITE);
        font.getData().setScale(1.5f);
        font.draw(b, "CREATURE PART PREVIEWER", 20f, VH - 12f);
        font.getData().setScale(0.95f);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(b, "Facing: " + FACING[partFacing], 20f, VH - 42f);

        float top = VH - 65f;
        for (int i = 0; i < partEntries.size(); i++) {
            PartEntry e = partEntries.get(i);
            float x = (i % COLS) * CELL_W + 10f;
            float y = top - (i / COLS) * CELL_H - CELL_H + 40f - gridScroll;
            if (y + CELL_H < 0 || y > VH) continue;
            Texture tex = getFacing(e.sprite, partFacing);
            if (tex != null) { b.setColor(Color.WHITE); b.draw(tex, x + (CELL_W - SPRITE_SIZE) / 2f, y + 32f, SPRITE_SIZE, SPRITE_SIZE); }
            font.getData().setScale(0.7f);
            font.setColor(0.8f, 0.8f, 0.8f, 1f);
            layout.setText(font, e.id);
            font.draw(b, e.id, x + (CELL_W - layout.width) / 2f, y + 28f);
        }
        b.setColor(Color.WHITE);
        font.getData().setScale(0.82f);
        font.setColor(Color.YELLOW);
        font.draw(b, gridStatus, 20f, 34f);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    // ── Render: CREATURE VIEW ─────────────────────────────────────────────────

    private void renderCreatureView(SpriteBatch b) {
        // Top bar
        drawRect(b, 0, VH - TOPBAR_H, VW, TOPBAR_H, new Color(0.08f,0.05f,0.13f,1f));
        font.getData().setScale(1.45f); font.setColor(Color.WHITE);
        font.draw(b, "CREATURE ASSEMBLY VIEW", 20f, VH - 12f);
        font.getData().setScale(0.82f); font.setColor(Color.LIGHT_GRAY);
        font.draw(b, "[G] generate   [Q/E] facing   [1-9] sever limb   [R] reset same   [TAB] parts view   [ESC] back",
                20f, VH - 46f);

        // Divider
        drawRect(b, SPLIT_X - 2f, MAIN_BOT, 2f, MAIN_TOP - MAIN_BOT, new Color(0.25f,0.15f,0.4f,1f));

        // ── Left panel: sprite + generate button ──
        float genBtnW = 240f, genBtnH = 56f;
        float genBtnX = SPRITE_CX - genBtnW / 2f;
        float genBtnY = MAIN_TOP - genBtnH - 10f;
        drawButton(b, genBtnRgn, genBtnX, genBtnY, genBtnW, genBtnH,
                new Color(0.18f,0.38f,0.20f,1f), "GENERATE  [G]", 1.1f);

        if (currentSprite != null) {
            applyFacing(creatureFacing * 90f);
            Texture tex = getFacing(currentSprite, creatureFacing);
            float sx = SPRITE_CX - SPRITE_DISP / 2f;
            float sy = SPRITE_CY - SPRITE_DISP / 2f;
            drawRect(b, sx - 4, sy - 4, SPRITE_DISP + 8, SPRITE_DISP + 8, new Color(0.28f,0.18f,0.42f,1f));
            b.setColor(Color.WHITE);
            if (tex != null) b.draw(tex, sx, sy, SPRITE_DISP, SPRITE_DISP);

            font.getData().setScale(0.8f); font.setColor(0.55f,0.42f,0.75f,1f);
            String facingLabel = "Facing: " + FACING[creatureFacing];
            layout.setText(font, facingLabel);
            font.draw(b, facingLabel, SPRITE_CX - layout.width / 2f, sy - 10f);
        } else {
            // Empty state prompt
            float px = SPRITE_CX - 180f, py = SPRITE_CY;
            font.getData().setScale(1.0f); font.setColor(0.35f,0.25f,0.55f,1f);
            font.draw(b, "Click  GENERATE  to assemble", px, py + 18f);
            font.draw(b, "a random procedural creature.", px, py - 12f);
        }

        // ── Right panel: limb list ──
        float panelX = SPLIT_X + 10f;
        float panelW = VW - SPLIT_X - 10f;

        font.getData().setScale(1.05f); font.setColor(0.65f,0.48f,0.95f,1f);
        font.draw(b, "LIMBS", panelX, MAIN_TOP - 12f);

        if (limbEntries.isEmpty()) {
            font.getData().setScale(0.82f); font.setColor(0.3f,0.22f,0.45f,1f);
            font.draw(b, "(no creature assembled)", panelX + 20f, MAIN_TOP - 55f);
        }

        float rowY = MAIN_TOP - 52f;
        for (int i = 0; i < limbEntries.size(); i++) {
            LimbEntry e = limbEntries.get(i);
            if (rowY - ROW_H < MAIN_BOT) break;

            // Row bg
            Color rowBg = e.severed
                    ? new Color(0.05f,0.03f,0.07f,1f)
                    : new Color(0.11f,0.07f,0.17f,1f);
            drawRect(b, panelX - 4f, rowY - ROW_H + 6f, panelW - 4f, ROW_H - 2f, rowBg);

            // Index + socket
            font.getData().setScale(0.9f);
            font.setColor(e.severed ? Color.DARK_GRAY : new Color(0.55f,0.85f,1f,1f));
            font.draw(b, "[" + (i+1) + "]  " + e.socket, panelX + 4f, rowY - 8f);

            // Part ID
            font.getData().setScale(0.78f);
            font.setColor(e.severed ? Color.DARK_GRAY : Color.WHITE);
            String label = e.severed ? "(severed)" : e.partId;
            font.draw(b, label, panelX + 240f, rowY - 8f);

            // Optional badge
            if (e.optional) {
                font.getData().setScale(0.62f); font.setColor(0.6f,0.6f,0.35f,1f);
                font.draw(b, "opt", panelX + 240f, rowY - 28f);
            }

            // SEVER button
            float bx = SPLIT_X + panelW - SEVER_W - 14f;
            float by = rowY - ROW_H + (ROW_H - SEVER_H) / 2f + 4f;
            if (!e.severed) {
                drawButton(b, e.btn, bx, by, SEVER_W, SEVER_H,
                        new Color(0.52f,0.08f,0.08f,1f), "SEVER", 0.9f);
            } else {
                e.btn[2] = 0; e.btn[3] = 0;
            }

            rowY -= ROW_H;
        }

        // Reset button
        float rstX = panelX, rstY = MAIN_BOT + 10f;
        drawButton(b, resetBtnRgn, rstX, rstY, 190f, 46f,
                new Color(0.18f,0.22f,0.40f,1f), "RESET SAME  [R]", 0.82f);

        // ── Bottom bar: sever log ──
        drawRect(b, 0, 0, SPLIT_X, BOTBAR_H, new Color(0.04f,0.03f,0.07f,1f));
        font.getData().setScale(0.75f); font.setColor(0.45f,0.38f,0.65f,1f);
        font.draw(b, "LOG", 16f, BOTBAR_H - 10f);
        font.setColor(0.7f,0.68f,0.8f,1f);
        float ly = BOTBAR_H - 28f;
        for (String msg : severLog) {
            if (ly < 4f) break;
            font.draw(b, msg, 16f, ly);
            ly -= 18f;
        }

        b.setColor(Color.WHITE);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    // ── Export (parts view) ───────────────────────────────────────────────────

    private void exportAll() {
        int ok = 0, fail = 0;
        FileHandle dir = Gdx.files.local("assets/models/creatures/parts/");
        dir.mkdirs();
        for (String id : library.getPartIds()) {
            Model m = library.get(id);
            if (m == null) continue;
            try { G3djWriter.write(m, dir.child(id + ".g3dj")); ok++; }
            catch (Exception ex) { Gdx.app.error("CreatureDevScreen", "Export: " + id + " — " + ex.getMessage()); fail++; }
        }
        gridStatus = (fail == 0) ? "Exported " + ok + " parts → assets/models/creatures/parts/" : "Exported " + ok + ", " + fail + " errors";
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private void drawRect(SpriteBatch b, float x, float y, float w, float h, Color c) {
        b.setColor(c);
        b.draw(whitePixel, x, y, w, h);
        b.setColor(Color.WHITE);
    }

    private void drawButton(SpriteBatch b, float[] rgn, float x, float y, float w, float h,
                            Color bg, String label, float textScale) {
        rgn[0] = x; rgn[1] = y; rgn[2] = w; rgn[3] = h;
        drawRect(b, x, y, w, h, bg);
        drawRect(b, x, y + h - 2f, w, 2f,
                new Color(Math.min(1f, bg.r + 0.25f), Math.min(1f, bg.g + 0.25f), Math.min(1f, bg.b + 0.25f), 1f));
        font.getData().setScale(textScale);
        font.setColor(Color.WHITE);
        layout.setText(font, label);
        font.draw(b, label, x + (w - layout.width) / 2f, y + (h + layout.height) / 2f);
    }

    private static boolean hit(float[] r, float x, float y) {
        return r[2] > 0 && x >= r[0] && x <= r[0]+r[2] && y >= r[1] && y <= r[1]+r[3];
    }

    private Texture getFacing(BakedMonsterSprite s, int idx) {
        switch (idx) {
            case 0: return s.north();
            case 1: return s.east();
            case 2: return s.south();
            case 3: return s.west();
            default: return s.south();
        }
    }
}
