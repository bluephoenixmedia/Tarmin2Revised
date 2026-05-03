package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.paperdoll.PaperDollWidget;
import com.bpm.minotaur.paperdoll.data.FragmentResolver;
import com.bpm.minotaur.paperdoll.data.SkeletonData;

/**
 * Root of the Modern inventory UI.
 *
 * Assembles the open-book layout and wires all panels together via the
 * {@link InventoryEventBus}.  The stage backdrop, spine divider, and tooltip
 * overlay are also created here.
 *
 * Lifecycle:
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

    private final InventorySkin            skin;
    private final InventoryEventBus        bus;
    private final InventoryDragDropHandler dnd;

    private final PaperDollPanel  paperDoll;
    private final CoreStatsPanel  coreStats;
    private final SpellbookPanel  spellbook;
    private final AlchemyPanel    alchemy;
    private final BackpackPanel   backpack;
    private final QuickSlotsPanel quickSlots;
    private final AttributesPanel attributes;

    private final Table  root;
    private final Label  tooltipLabel;

    // Owned by us — dispose on close
    private Texture paperDollTexture;
    private Texture headTexture;

    private final Player player;

    public ModernInventoryUI(Player player, Maze maze, AssetManager assets, ItemDataManager idm) {
        this.player = player;

        skin = new InventorySkin();
        bus  = new InventoryEventBus();
        dnd  = new InventoryDragDropHandler(player, maze, bus, skin, idm);

        paperDollTexture = loadPaperDollTexture();

        // ── Paper doll widget (existing animated doll renderer) ───────
        SkeletonData skeletonData = new SkeletonData();
        if (Gdx.files.local("assets/data/skeleton.json").exists()) {
            skeletonData.load(Gdx.files.local("assets/data/skeleton.json"));
        } else {
            skeletonData.load(Gdx.files.internal("data/skeleton.json"));
        }

        TextureAtlas armorAtlas   = assets.get("packed/armor.atlas",   TextureAtlas.class);
        TextureAtlas weaponAtlas  = assets.get("packed/weapons.atlas",  TextureAtlas.class);
        FragmentResolver resolver  = new FragmentResolver(armorAtlas, weaponAtlas);
        PaperDollWidget dollWidget = new PaperDollWidget(skeletonData, resolver);

        // ── Panels ────────────────────────────────────────────────────
        paperDoll  = new PaperDollPanel(player, skin, dnd, idm, paperDollTexture);
        paperDoll.attachPaperDollWidget(dollWidget);

        coreStats  = new CoreStatsPanel(player, skin);
        spellbook  = new SpellbookPanel(player, skin);
        alchemy    = new AlchemyPanel(skin, idm, dnd);
        backpack   = new BackpackPanel(player.getInventory(), skin, idm, dnd);
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

        // ── Root table (fills stage) ──────────────────────────────────
        root = new Table();
        root.setFillParent(true);
        root.pad(30f);

        // Dark semi-transparent backdrop
        root.setBackground(buildBackdrop());

        LeftPageTable  leftPage  = new LeftPageTable(paperDoll, coreStats, spellbook, alchemy, skin);
        RightPageTable rightPage = new RightPageTable(backpack, quickSlots, attributes, skin);

        // Spine divider image
        Image spine = new Image(skin.getSpineDrawable());

        root.add(leftPage).expand().fill();
        root.add(spine).fillY().width(6f).padLeft(2f).padRight(2f);
        root.add(rightPage).expand().fill();

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
    }

    /** Syncs all panels from current player state.  Call in {@code show()}. */
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
        if (paperDollTexture != null) paperDollTexture.dispose();
        if (headTexture      != null) headTexture.dispose();
    }

    // ── Private helpers ───────────────────────────────────────────────

    private void wireBackpackInteractions() {
        // The BackpackPanel holds package-private slot references; we wire click
        // listeners via a bus listener so we don't need to expose the slot array.
        // Instead, we rely on the existing right-click listener that InventoryScreen
        // used to add.  Since BackpackPanel's slots are registered with the dnd
        // handler, we add a per-slot ClickListener inside BackpackPanel itself.
        // This is done here so the dnd reference is available:
        //   (BackpackPanel could also accept a Runnable<InventorySlot> callback)
        // For now the quickest clean approach: listen inside BackpackPanel by adding
        // the listener there.  We call the internal helper via a bus subscription:
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
