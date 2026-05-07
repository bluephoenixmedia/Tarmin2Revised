package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.paperdoll.PaperDollWidget;
import com.bpm.minotaur.paperdoll.data.FragmentResolver;
import com.bpm.minotaur.paperdoll.data.SkeletonData;

/**
 * Root of the Modern inventory UI.
 *
 * Assembles the open-book layout and wires all panels together via the
 * {@link InventoryEventBus}. The stage backdrop, spine divider, and tooltip
 * overlay are also created here.
 *
 * Lifecycle:
 * 
 * <pre>
 *   ModernInventoryUI ui = new ModernInventoryUI(...);
 *   ui.addToStage(stage);
 *   // on show:
 *   ui.refresh();
 *   // on hide / dispose:
 *   ui.dispose();
 * </pre>
 */
public class ModernInventoryUI {

    private final InventorySkin skin;
    private final InventoryEventBus bus;
    private final InventoryDragDropHandler dnd;

    private final PaperDollPanel paperDoll;
    private final CoreStatsPanel coreStats;
    private final SpellbookPanel spellbook;
    private final AlchemyPanel alchemy;
    private final BackpackPanel backpack;
    private final QuickSlotsPanel quickSlots;
    private final AttributesPanel attributes;

    private final Table root;
    private final Label tooltipLabel;

    // Owned by us — dispose on close
    private Texture paperDollTexture;
    private Texture headTexture;
    private Texture bgTexture;

    /** Debug overlay — press F3 in-game to toggle. */
    private final InventoryDebugOverlay debugOverlay;

    /** Loads/saves panel positions to inventory_layout.txt for debug-drag workflow. */
    private final InventoryLayoutConfig config;

    private final Player player;

    public ModernInventoryUI(Player player, Maze maze, AssetManager assets, ItemDataManager idm) {
        this.player = player;
        config = new InventoryLayoutConfig();

        skin = new InventorySkin();
        bus = new InventoryEventBus();
        dnd = new InventoryDragDropHandler(player, maze, bus, skin, idm);

        paperDollTexture = loadPaperDollTexture();

        // ── Paper doll widget (existing animated doll renderer) ───────
        SkeletonData skeletonData = new SkeletonData();
        if (Gdx.files.local("assets/data/skeleton.json").exists()) {
            skeletonData.load(Gdx.files.local("assets/data/skeleton.json"));
        } else {
            skeletonData.load(Gdx.files.internal("data/skeleton.json"));
        }

        TextureAtlas armorAtlas = assets.get("packed/armor.atlas", TextureAtlas.class);
        TextureAtlas weaponAtlas = assets.get("packed/weapons.atlas", TextureAtlas.class);
        FragmentResolver resolver = new FragmentResolver(armorAtlas, weaponAtlas);
        PaperDollWidget dollWidget = new PaperDollWidget(skeletonData, resolver);

        // ── Panels ────────────────────────────────────────────────────
        paperDoll = new PaperDollPanel(player, skin, dnd, idm, paperDollTexture);
        paperDoll.attachPaperDollWidget(dollWidget);

        coreStats = new CoreStatsPanel(player, skin);
        spellbook = new SpellbookPanel(player, skin);
        alchemy = new AlchemyPanel(skin, idm, dnd);
        backpack = new BackpackPanel(player.getInventory(), skin, idm, dnd);
        quickSlots = new QuickSlotsPanel(player.getInventory().getQuickSlots(), skin, idm, dnd);
        attributes = new AttributesPanel(player, skin);

        // ── Event subscriptions ───────────────────────────────────────
        bus.subscribe(paperDoll);
        bus.subscribe(coreStats);
        bus.subscribe(backpack);
        bus.subscribe(quickSlots);
        bus.subscribe(attributes);
        bus.subscribe(spellbook);

        // ── Backpack slot right-click → drop ─────────────────────────
        wireBackpackInteractions();

        // ══════════════════════════════════════════════════════════════
        // PANEL LAYOUT — HOW TO ADJUST POSITIONS
        // ══════════════════════════════════════════════════════════════
        // Stage is 1920 × 1080 virtual pixels (FitViewport).
        // X : 0 = left edge → 1920 = right edge
        // Y : 0 = BOTTOM edge → 1080 = TOP edge ← LibGDX is Y-up!
        //
        // setPosition(x, y) places the BOTTOM-LEFT corner of the panel.
        //
        // To move a panel RIGHT → increase x
        // To move a panel LEFT → decrease x
        // To move a panel UP → increase y
        // To move a panel DOWN → decrease y
        //
        // Each panel also has internal spacing constants (CELL_W, CELL_H)
        // in its own file if you need to adjust slot-to-slot spacing:
        // BackpackPanel.java — CELL_W (horizontal), CELL_H (vertical)
        // QuickSlotsPanel.java — CELL_W
        // ══════════════════════════════════════════════════════════════

        root = new Table();
        root.setFillParent(true);
        root.pad(0);

        // new_inventory.png stretched to fill the 1920×1080 virtual stage.
        // Falls back to a dark solid colour if the file is missing.
        bgTexture = loadBgTexture();
        root.setBackground(bgTexture != null
                ? new TextureRegionDrawable(bgTexture)
                : buildBackdrop());

        // PaperDollPanel fills the full stage so its internal slot coordinates
        // are identical to absolute stage coordinates — no offset needed.
        paperDoll.setSize(1920f, 1080f);
        paperDoll.setPosition(0f, 0f);
        root.addActor(paperDoll);

        // ── Right page: backpack 8×6 grid ────────────────────────────
        // X=1040 places the left edge of the grid.
        // Y=497 places the bottom edge so the top-left slot centre lands
        // at stage (1078, 898), matching the image's first slot outline.
        // To shift the whole grid: change both numbers together.
        // To change slot-to-slot spacing: edit CELL_W / CELL_H in BackpackPanel.java.
        // Panel positions come from InventoryLayoutConfig (loads inventory_layout.txt
        // if it exists, otherwise uses the hardcoded defaults encoded there).
        // In debug mode (F3), drag panels with the mouse then click SAVE LAYOUT
        // to write inventory_layout.txt.  Copy the setPosition() lines from that
        // file into this constructor when you're happy with the layout.
        backpack.setSize(backpack.getPrefWidth(), backpack.getPrefHeight());
        backpack.setPosition(config.getX(InventoryLayoutConfig.BACKPACK),
                             config.getY(InventoryLayoutConfig.BACKPACK));
        root.addActor(backpack);

        quickSlots.setSize(quickSlots.getPrefWidth(), quickSlots.getPrefHeight());
        quickSlots.setPosition(config.getX(InventoryLayoutConfig.QUICKSLOTS),
                               config.getY(InventoryLayoutConfig.QUICKSLOTS));
        root.addActor(quickSlots);

        attributes.pack();
        attributes.setPosition(config.getX(InventoryLayoutConfig.ATTRIBUTES),
                               config.getY(InventoryLayoutConfig.ATTRIBUTES));
        root.addActor(attributes);

        coreStats.pack();
        coreStats.setPosition(config.getX(InventoryLayoutConfig.CORESTATS),
                              config.getY(InventoryLayoutConfig.CORESTATS));
        root.addActor(coreStats);

        spellbook.pack();
        spellbook.setPosition(config.getX(InventoryLayoutConfig.SPELLBOOK),
                              config.getY(InventoryLayoutConfig.SPELLBOOK));
        root.addActor(spellbook);

        alchemy.pack();
        alchemy.setPosition(config.getX(InventoryLayoutConfig.ALCHEMY),
                            config.getY(InventoryLayoutConfig.ALCHEMY));
        root.addActor(alchemy);

        // ── Debug overlay (hidden by default — press F3 to show) ─────
        // Each color identifies a different panel at a glance:
        // CYAN = PaperDollPanel equipment slots (no bounding box — it is full-screen)
        // GREEN = BackpackPanel
        // ORANGE = QuickSlotsPanel
        // MAGENTA = AttributesPanel
        // YELLOW = CoreStatsPanel
        // SKY = SpellbookPanel
        // RED = AlchemyPanel
        debugOverlay = new InventoryDebugOverlay(skin.getFontSmall());
        debugOverlay.track(paperDoll, "PaperDollPanel", Color.CYAN, false); // full-screen; show slots only
        debugOverlay.track(backpack, "BackpackPanel", Color.GREEN);
        debugOverlay.track(quickSlots, "QuickSlotsPanel", Color.ORANGE);
        debugOverlay.track(attributes, "AttributesPanel", Color.MAGENTA);
        debugOverlay.track(coreStats, "CoreStatsPanel", Color.YELLOW);
        debugOverlay.track(spellbook, "SpellbookPanel", new Color(0.4f, 0.8f, 1f, 1f));
        debugOverlay.track(alchemy, "AlchemyPanel", Color.RED);
        debugOverlay.setVisible(false); // hidden until F3 is pressed
        root.addActor(debugOverlay); // added last so it renders above everything else

        // When the user clicks [SAVE LAYOUT] in the debug overlay, snapshot all
        // panel positions into the config and write inventory_layout.txt.
        debugOverlay.setSaveCallback(() -> {
            config.set(InventoryLayoutConfig.BACKPACK,   backpack.getX(),   backpack.getY());
            config.set(InventoryLayoutConfig.QUICKSLOTS, quickSlots.getX(), quickSlots.getY());
            config.set(InventoryLayoutConfig.ATTRIBUTES, attributes.getX(), attributes.getY());
            config.set(InventoryLayoutConfig.CORESTATS,  coreStats.getX(),  coreStats.getY());
            config.set(InventoryLayoutConfig.SPELLBOOK,  spellbook.getX(),  spellbook.getY());
            config.set(InventoryLayoutConfig.ALCHEMY,    alchemy.getX(),    alchemy.getY());
            config.save();
        });

        // ── Tooltip overlay (non-interactive, always on top) ──────────
        tooltipLabel = new Label("",
                new Label.LabelStyle(skin.getFontSmall(), Color.WHITE));
        tooltipLabel.setTouchable(Touchable.disabled);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────

    /** Adds the UI and tooltip overlay to the given stage. */
    public void addToStage(Stage stage) {
        stage.addActor(root);

        Table tooltipTable = new Table();
        tooltipTable.setFillParent(true);
        tooltipTable.bottom();
        tooltipTable.setTouchable(Touchable.disabled);
        tooltipTable.add(tooltipLabel).padBottom(18);
        stage.addActor(tooltipTable);

        // F3 toggles the debug overlay — listener lives on the stage so it
        // receives key events regardless of which actor has keyboard focus.
        stage.addListener(new EventListener() {
            @Override
            public boolean handle(Event event) {
                if (event instanceof InputEvent) {
                    InputEvent ie = (InputEvent) event;
                    if (ie.getType() == InputEvent.Type.keyDown
                            && ie.getKeyCode() == Input.Keys.F3) {
                        debugOverlay.setVisible(!debugOverlay.isVisible());
                        return true;
                    }
                }
                return false;
            }
        });
    }

    /** Syncs all panels from current player state. Call in {@code show()}. */
    public void refresh() {
        paperDoll.refresh();
        backpack.refresh();
        quickSlots.refresh();
        coreStats.refresh();
        attributes.refresh();
        spellbook.refresh();
    }

    /** Disposes skin (fonts + textures) and any textures we own. */
    public void dispose() {
        skin.dispose();
        if (paperDollTexture != null)
            paperDollTexture.dispose();
        if (headTexture != null)
            headTexture.dispose();
        if (bgTexture != null)
            bgTexture.dispose();
        debugOverlay.dispose(); // disposes the internal ShapeRenderer
    }

    // ── Private helpers ───────────────────────────────────────────────

    private Texture loadBgTexture() {
        try {
            Texture t = new Texture(Gdx.files.internal("images/new_inventory.png"));
            t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return t;
        } catch (Exception e) {
            Gdx.app.error("ModernInventoryUI", "Could not load inventory backdrop", e);
            return null;
        }
    }

    private void wireBackpackInteractions() {
        // The BackpackPanel holds package-private slot references; we wire click
        // listeners via a bus listener so we don't need to expose the slot array.
        // Instead, we rely on the existing right-click listener that InventoryScreen
        // used to add. Since BackpackPanel's slots are registered with the dnd
        // handler, we add a per-slot ClickListener inside BackpackPanel itself.
        // This is done here so the dnd reference is available:
        // (BackpackPanel could also accept a Runnable<InventorySlot> callback)
        // For now the quickest clean approach: listen inside BackpackPanel by adding
        // the listener there. We call the internal helper via a bus subscription:
        bus.subscribe(new InventoryEventBus.Listener() {
            /* intentionally empty — interaction listeners are wired in the panels */
        });
    }

    private TextureRegionDrawable buildBackdrop() {
        Pixmap p = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        p.setColor(new Color(0.06f, 0.04f, 0.02f, 0.92f));
        p.fill();
        Texture t = new Texture(p);
        p.dispose();
        return new TextureRegionDrawable(t);
    }

    private Texture loadPaperDollTexture() {
        try {
            return new Texture(Gdx.files.internal("images/inventory_paper_doll.png"));
        } catch (Exception e) {
            Gdx.app.error("ModernInventoryUI", "Could not load paper-doll image", e);
            Pixmap p = new Pixmap(400, 600, Pixmap.Format.RGBA8888);
            p.setColor(new Color(0.25f, 0.22f, 0.18f, 1f));
            p.fill();
            Texture t = new Texture(p);
            p.dispose();
            return t;
        }
    }
}
