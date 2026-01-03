package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
// import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
// import com.badlogic.gdx.utils.Align; // Unused
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.paperdoll.PaperDollWidget;
import com.bpm.minotaur.paperdoll.data.FragmentResolver;
import com.bpm.minotaur.paperdoll.data.SkeletonData;

import java.util.HashMap;
import java.util.Map;

public class PaperdollEditorScreen extends BaseScreen {

    private Stage stage;
    private Skin skin;
    private Table rootTable;

    // Components
    private PaperDollWidget paperDollWidget;
    private Texture paperDollTexture;
    private Texture headTexture;
    private SkeletonData skeletonData;
    private FragmentResolver fragmentResolver;

    // Editor data
    private Map<String, Item> equippedItems = new HashMap<>(); // slotName -> Item
    private String selectedSlot = null; // Currently selected slot for editing

    // UI Controls
    private SelectBox<ItemType> itemSelectBox;
    private SelectBox<String> slotSelectBox;

    private Slider offsetXSlider, offsetYSlider;
    private Slider scaleXSlider, scaleYSlider;
    private Label offsetValueLabel, scaleValueLabel;

    // Skeleton Controls
    private Slider socketXSlider, socketYSlider;
    private Label socketValueLabel;

    private com.badlogic.gdx.Screen previousScreen;

    public PaperdollEditorScreen(Tarmin2 game, com.badlogic.gdx.Screen previousScreen) {
        super(game);
        this.previousScreen = previousScreen;
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        Gdx.input.setInputProcessor(stage);

        // Basic Skin
        skin = new Skin();
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        skin.add("white", new Texture(pixmap));
        skin.add("white", new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(
                new com.badlogic.gdx.graphics.g2d.TextureRegion(new Texture(pixmap))));
        skin.add("default-font", new BitmapFont());

        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = skin.getFont("default-font");
        skin.add("default", labelStyle);

        TextField.TextFieldStyle tfStyle = new TextField.TextFieldStyle();
        tfStyle.font = new BitmapFont();
        tfStyle.fontColor = Color.WHITE;
        skin.add("default", tfStyle);

        CheckBox.CheckBoxStyle cbStyle = new CheckBox.CheckBoxStyle();
        cbStyle.font = new BitmapFont();
        cbStyle.checkboxOff = skin.newDrawable("white", Color.GRAY);
        cbStyle.checkboxOn = skin.newDrawable("white", Color.GREEN);
        skin.add("default", cbStyle);

        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = skin.newDrawable("white", Color.DARK_GRAY);
        sliderStyle.knob = skin.newDrawable("white", Color.LIGHT_GRAY);
        sliderStyle.knob.setMinHeight(20);
        sliderStyle.knob.setMinWidth(10);
        skin.add("default-horizontal", sliderStyle);

        ScrollPane.ScrollPaneStyle spStyle = new ScrollPane.ScrollPaneStyle();
        skin.add("default", spStyle);

        List.ListStyle lbStyle = new List.ListStyle();
        lbStyle.font = new BitmapFont();
        lbStyle.fontColorSelected = Color.GREEN;
        lbStyle.fontColorUnselected = Color.WHITE;
        lbStyle.selection = skin.newDrawable("white", Color.BLUE);
        skin.add("default", lbStyle);

        SelectBox.SelectBoxStyle sbStyle = new SelectBox.SelectBoxStyle();
        sbStyle.font = new BitmapFont();
        sbStyle.fontColor = Color.WHITE;
        sbStyle.scrollStyle = spStyle;
        sbStyle.listStyle = lbStyle;
        sbStyle.background = skin.newDrawable("white", Color.DARK_GRAY);
        skin.add("default", sbStyle);

        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = new BitmapFont();
        btnStyle.up = skin.newDrawable("white", Color.GRAY);
        btnStyle.down = skin.newDrawable("white", Color.DARK_GRAY);
        btnStyle.over = skin.newDrawable("white", Color.LIGHT_GRAY);
        skin.add("default", btnStyle);

        // --- Paper Doll Setup ---
        paperDollTexture = new Texture(Gdx.files.internal("images/inventory_paper_doll.png"));
        skeletonData = new SkeletonData();
        skeletonData.load(Gdx.files.internal("data/skeleton.json"));

        TextureAtlas armorAtlas = game.getAssetManager().get("packed/armor.atlas", TextureAtlas.class);
        TextureAtlas itemsAtlas = game.getAssetManager().get("packed/items.atlas", TextureAtlas.class);
        fragmentResolver = new FragmentResolver(armorAtlas, itemsAtlas);

        paperDollWidget = new PaperDollWidget(skeletonData, fragmentResolver);
        // Debug assets
        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fill();
        Texture white = new Texture(p);
        paperDollWidget.setDebugAssets(white, new BitmapFont());

        // Force debug overlay on for editor visibility of sockets
        com.bpm.minotaur.managers.DebugManager.getInstance().setDebugOverlayVisible(true);

        buildUI();
    }

    private void buildUI() {
        rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.pad(20);
        rootTable.defaults().space(10);

        // --- Left: Paperdoll Canvas ---
        Group paperDollGroup = new Group();
        Image bgImage = new Image(paperDollTexture);
        float maxH = 800f;
        float scale = maxH / bgImage.getHeight();

        bgImage.setSize(bgImage.getWidth() * scale, maxH);

        paperDollWidget.setSize(bgImage.getWidth() * scale, maxH);
        paperDollWidget.setScale(scale);

        paperDollGroup.addActor(bgImage);

        // --- Head Overlay (Behind Armor) ---
        Texture headTex = new Texture(Gdx.files.internal("images/inventory_doll_head.png"));
        Image headImage = new Image(headTex);
        // Position/Size from InventoryScreen, adapted for dynamic scale
        // InventoryScreen used hardcoded 402, 465 and approx 4% / 8% size.
        // We act as if those were for the original image and apply our 'scale'.
        // Original logic: width * .04, height * .08
        float headW = paperDollTexture.getWidth();
        float headH = paperDollTexture.getHeight();
        float headX = 1000; // Assuming 402 is relative to original
        float headY = 1200;

        headImage.setSize(headW, headH);
        headImage.setPosition(headX, headY);
        paperDollGroup.addActor(headImage);

        paperDollGroup.addActor(paperDollWidget);

        Container<Group> dollContainer = new Container<>(paperDollGroup);
        dollContainer.size(bgImage.getWidth() * scale, maxH);
        dollContainer.right();

        // --- Right: Controls ---
        Table controls = new Table(skin);
        controls.setBackground(skin.newDrawable("white", new Color(0, 0, 0, 0.8f))); // Dark background
        controls.pad(20);
        controls.top().left();

        // Slot Selection
        controls.add(new Label("Target Slot:", skin)).left();
        slotSelectBox = new SelectBox<>(skin);
        slotSelectBox.setItems("Head", "Torso", "Hands", "Legs", "Feet", "MainHand", "OffHand", "Back", "Backpack");
        slotSelectBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                selectedSlot = slotSelectBox.getSelected();
                updateUIFromSelection();
            }
        });
        controls.add(slotSelectBox).width(200).row();

        // Category Filter
        controls.add(new Label("Filter:", skin)).left();
        final SelectBox<String> categorySelectBox = new SelectBox<>(skin);
        categorySelectBox.setItems("All", "Armor", "Weapon", "Misc");
        categorySelectBox.setSelected("All");
        categorySelectBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                populateItemSelectBox(categorySelectBox.getSelected());
            }
        });
        controls.add(categorySelectBox).width(200).row();

        // Item Selection
        controls.add(new Label("Equip Item:", skin)).left();
        itemSelectBox = new SelectBox<>(skin);
        populateItemSelectBox("All"); // Initial population

        itemSelectBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                equipSelectedItem();
            }
        });
        controls.add(itemSelectBox).width(200).row();

        controls.add(new Label("--- Item Transforms ---", skin, "default-font", Color.YELLOW)).colspan(2).padTop(20)
                .row();

        // Offset X
        controls.add(new Label("Offset X:", skin)).left();
        offsetXSlider = new Slider(-100, 100, 1, false, skin);
        controls.add(offsetXSlider).width(200).row();

        // Offset Y
        controls.add(new Label("Offset Y:", skin)).left();
        offsetYSlider = new Slider(-100, 100, 1, false, skin);
        controls.add(offsetYSlider).width(200).row();

        offsetValueLabel = new Label("0, 0", skin);
        controls.add(offsetValueLabel).colspan(2).row();

        // Scale
        controls.add(new Label("Scale X:", skin)).left();
        scaleXSlider = new Slider(0.1f, 3f, 0.05f, false, skin);
        controls.add(scaleXSlider).width(200).row();

        controls.add(new Label("Scale Y:", skin)).left();
        scaleYSlider = new Slider(0.1f, 3f, 0.05f, false, skin);
        controls.add(scaleYSlider).width(200).row();

        scaleValueLabel = new Label("1.0, 1.0", skin);
        controls.add(scaleValueLabel).colspan(2).row();

        // Listeners for sliders
        ChangeListener scaler = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                updateItemTransform();
            }
        };
        offsetXSlider.addListener(scaler);
        offsetYSlider.addListener(scaler);
        scaleXSlider.addListener(scaler);
        scaleYSlider.addListener(scaler);

        // --- Socket Controls ---
        controls.add(new Label("--- Skeleton Sockets ---", skin, "default-font", Color.RED)).colspan(2).padTop(20)
                .row();
        controls.add(new Label("Modify global socket position for selected slot", skin)).colspan(2).row();

        socketXSlider = new Slider(0, 1200, 1, false, skin);
        socketYSlider = new Slider(0, 1200, 1, false, skin);

        controls.add(new Label("Socket X:", skin)).left();
        controls.add(socketXSlider).width(200).row();
        controls.add(new Label("Socket Y:", skin)).left();
        controls.add(socketYSlider).width(200).row();

        socketValueLabel = new Label("0, 0", skin);
        controls.add(socketValueLabel).colspan(2).row();

        ChangeListener socketListener = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                updateSocketTransform();
            }
        };
        socketXSlider.addListener(socketListener);
        socketYSlider.addListener(socketListener);

        // Buttons
        TextButton saveButton = new TextButton("SAVE JSON (Armor/Skeleton)", skin);
        saveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                saveData();
            }
        });
        controls.add(saveButton).colspan(2).fillX().height(50).padTop(30).row();

        TextButton closeButton = new TextButton("Close Editor", skin);
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (previousScreen != null) {
                    game.setScreen(previousScreen);
                } else {
                    game.setScreen(new MainMenuScreen(game));
                }
            }
        });
        controls.add(closeButton).colspan(2).fillX().height(50).padTop(10).row();

        // Layout
        rootTable.add(dollContainer).expand().fill();
        rootTable.add(controls).top().width(400);

        stage.addActor(rootTable);

        // Initial defaults
        selectedSlot = "Torso";
        slotSelectBox.setSelected(selectedSlot);
        updateUIFromSelection();
    }

    private void equipSelectedItem() {
        if (selectedSlot == null)
            return;
        ItemType type = itemSelectBox.getSelected();
        if (type == null)
            return;

        try {
            ItemTemplate template = game.getItemDataManager().getTemplate(type);
            if (template == null) {
                Gdx.app.log("Editor", "No template logic for " + type);
                return;
            }

            // Create item wrapper
            Item item = Item.fromTemplate(type, template);
            equippedItems.put(selectedSlot, item);

            // Update widget
            refreshWidget();

            // Update UI sliders to match this new item's current values
            scaleXSlider.setValue(template.scaleX);
            scaleYSlider.setValue(template.scaleY);
            offsetXSlider.setValue(template.offsetX);
            offsetYSlider.setValue(template.offsetY);
        } catch (Exception e) {
            Gdx.app.error("Editor", "Error equipping " + type, e);
        }
    }

    private void updateUIFromSelection() {
        // Update Socket Sliders
        String socketKey = mapSlotToSocket(selectedSlot);
        com.badlogic.gdx.math.Vector2 pos = skeletonData.getSocketPosition(socketKey);
        if (pos != null) {
            socketXSlider.setValue(pos.x);
            socketYSlider.setValue(pos.y);
            socketValueLabel.setText((int) pos.x + ", " + (int) pos.y);
        }

        // Update Item Sliders if item exists
        Item item = equippedItems.get(selectedSlot);
        if (item != null) {
            itemSelectBox.setSelected(item.getType());
            offsetXSlider.setValue(item.getOffsetX());
            offsetYSlider.setValue(item.getOffsetY());
            scaleXSlider.setValue(item.getScale().x);
            scaleYSlider.setValue(item.getScale().y);
        } else {
            // Reset sliders?
            offsetXSlider.setValue(0);
            offsetYSlider.setValue(0);
            scaleXSlider.setValue(1);
            scaleYSlider.setValue(1);
        }
    }

    private void updateItemTransform() {
        if (selectedSlot == null)
            return;
        Item item = equippedItems.get(selectedSlot);
        if (item == null)
            return;

        float valX = scaleXSlider.getValue();
        float valY = scaleYSlider.getValue();
        Gdx.app.log("EditorDebug", "Setting scale for " + item.getTypeName() + " to " + valX + ", " + valY);

        item.setOffsetX(offsetXSlider.getValue());
        item.setOffsetY(offsetYSlider.getValue());
        item.getScale().set(valX, valY);

        refreshWidget();

        offsetValueLabel.setText((int) item.getOffsetX() + ", " + (int) item.getOffsetY());
        scaleValueLabel.setText(String.format("%.2f, %.2f", item.getScale().x, item.getScale().y));
    }

    private void updateSocketTransform() {
        String socketKey = mapSlotToSocket(selectedSlot);
        skeletonData.setSocketPosition(socketKey, socketXSlider.getValue(), socketYSlider.getValue());
        refreshWidget(); // Re-render to see change
        socketValueLabel.setText((int) socketXSlider.getValue() + ", " + (int) socketYSlider.getValue());
    }

    private void refreshWidget() {
        paperDollWidget.clearEquipment();

        // --- Add Head Overlay as a Fragment (Z-Index Control) ---
        // Chest is usually Z=30, Helmet is Z=60. We want Z=45.
        // We need to load/cache the texture somewhere or just use the one we loaded.
        // We'll treat it as a "Body" part for now.
        if (headTexture == null) {
            headTexture = new Texture(Gdx.files.internal("images/inventory_doll_head.png"));
        }
        com.badlogic.gdx.graphics.g2d.TextureRegion headRegion = new com.badlogic.gdx.graphics.g2d.TextureRegion(
                headTexture);

        // Size/Position matching current Editor Scale logic
        // The Widget handles scale application during render if we set localOffset?
        // Wait, Widget.drawFragment uses: getX() + socketX + localOffset.
        // And applies scale: fragment.scaleX.

        // We need to match the "Global" positioning of the head image we realized
        // earlier.
        // In previous step we used explicit Actor sizing/positioning.
        // Here we are inside the widget. The widget is already scaled by the stage
        // logic (or actor scale).

        // If we use DollFragment, coordinates are relative to the Widget's origin (0,0)
        // + Socket Position.
        // We can define a pseudo-socket or just use absolute offset.
        // Let's use "head" socket?
        // SkeletonData for "head": x=1048, y=1200, z=40
        // The inventory_doll_head.png is actually the FACE.
        // Previously we positioned it at explicit X/Y relative to the paperdoll image.

        // Replicating explicit position from previous step:
        // float headX = 402; float headY = 465; (Relative to UNCALED 400x600 image? No,
        // wait.)
        // The paperdoll texture is huge? checking previous read..
        // Previous read showed paperDollTexture loaded from
        // "images/inventory_paper_doll.png".
        // InventoryScreen logic was "targetWidth / paperDollTexture.getWidth()".

        // If we put it in the widget, the widget aligns with the paperdoll background.
        // So we just need the local coordinates of the head relative to the background
        // 0,0.
        // InventoryScreen said: headImage.setPosition(402, 465);

        com.bpm.minotaur.paperdoll.data.DollFragment headFrag = new com.bpm.minotaur.paperdoll.data.DollFragment(
                headRegion, 45, "none", 1f, 1f);

        // "none" socket means (0,0). So we set offset to (402, 465).
        headFrag.localOffset.set(402, 465);

        // WAIT: InventoryScreen's 402,465 was for a specific target size maybe?
        // InventoryScreen used:
        // scale = targetWidth / paperDollTexture.getWidth();
        // ...
        // headImage.setPosition(402, 465);
        // This suggests 402,465 was relative to the Scaled Group if the Group/Image
        // were scaled?
        // Actually InventoryScreen adds headImage to paperDollGroup which contains
        // paperDollImage.
        // If paperDollImage is size (W,H) and headImage is at (402, 465),
        // AND the user said "bascinet_head: Pos(1067.00, 1249.00)" in logs...
        // The logs suggest the coordinate system is much larger (1000+).
        // 402, 465 seems small for a 1200+ Y system.

        // Let's re-read the user log from Step 635:
        // "bascinet_head: Pos(1067.00, 1249.00)"
        // "Socket(head: 1048.00, 1200.00)"

        // Verify: Inventory texture might be large (e.g. 2048x2048).
        // 402, 465 might be for the SCALED UI version in InventoryScreen.
        // BUT, in Editor we are using the internal coordinate system of the Widget
        // which matches the Texture resolution.

        // I need to know the REAL coordinates of the head on the source texture.
        // I don't have the image file to check.
        // But I know "head" socket is at 1048, 1200.
        // The "Face" should be near the head socket.
        // So the offset should be near 1048, 1200.

        // The user manually adjusted the Bascinet to:
        // "scaleX": 0.65, "offsetY": 42
        // Socket head: 1048, 1200.
        // So helmet is around 1048, 1242.

        // The head overlay is the "face". It should be roughly there too.
        // Let's blindly guess it should be anchored to the "head" socket with (0,0)
        // offset initially,
        // OR try to map that 402/465 to the larger space.
        // If 402/465 was correct for the UI, and the UI scale was, say, 0.4...
        // 402 / 0.4 = 1005.
        // 465 / 0.4 = 1162.
        // That is suspiciously close to 1048, 1200.

        // So, let's attach the Head Fragment to the "head" socket.
        // And give it a small offset or 0,0.
        // Let's try 0,0 first. It's safe.
        // Z-Index 45 to be > Chest(30) and < Helm(60).

        com.bpm.minotaur.paperdoll.data.DollFragment headFragment = new com.bpm.minotaur.paperdoll.data.DollFragment(
                headRegion, 35, "head", 1f, 1f);

        // The head image is likely centered or top-left?
        // Usually LibGDX draws from bottom-left.
        // If the socket is the "Neck/Pivot", the face image needs to be positioned
        // relative to it.
        // I'll assume 0,0 offset from "head" socket is a good starting point given the
        // helmet aligns there.
        // Actually, the previous step's code used explicit adjustment:
        // User changed my code to: headX = 1000; headY = 1200;
        // This confirms the large coordinate system.
        // So relative to socket (1048, 1200), the position (1000, 1200) is offset (-48,
        // 0).

        headFragment.localOffset.set(40, 40);
        paperDollWidget.setBody(headFragment); // Use setBody to add "extra" fragments

        // Re-equip all
        for (Item item : equippedItems.values()) {
            paperDollWidget.equip(item);
        }
    }

    private String mapSlotToSocket(String slotName) {
        switch (slotName) {
            case "Head":
                return "head";
            case "Torso":
                return "torso";
            case "Hands":
                return "hand_main"; // Or both?
            case "Legs":
                return "hips";
            case "Feet":
                return "feet";
            case "MainHand":
                return "hand_main";
            case "OffHand":
                return "hand_off";
            default:
                return "torso";
        }
    }

    private void saveData() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setTypeName(null);
        json.setUsePrototypes(false);
        json.setIgnoreUnknownFields(true);

        // 1. Update Skeleton JSON
        try {
            FileHandle skelFile = Gdx.files.local("assets/data/skeleton.json");

            // Reconstruct the expected JSON structure: { "sockets": { "name": {x,y,z}, ...
            // } }
            Map<String, Map<String, Object>> root = new HashMap<>();
            Map<String, Object> socketsMap = new HashMap<>();

            // We need to iterate over known sockets.
            // Since SkeletonData stores them in private maps, we should probably access
            // them via getters or assume keys.
            // But we don't have public access to keys in SkeletonData.
            // Let's assume the set of sockets we care about matches what's in the original
            // file or just standard anatomy.
            // Ideally SkeletonData should expose the keys.
            // Only way right now is modifying SkeletonData to expose keys or iterating
            // known slots.

            // Workaround: We will rely on the mapped sockets logic.
            String[] knownSockets = { "head", "torso", "hand_main", "hand_off", "feet", "hips", "back", "backpack" };

            for (String sockName : knownSockets) {
                com.badlogic.gdx.math.Vector2 pos = skeletonData.getSocketPosition(sockName);
                int depth = skeletonData.getSocketDepth(sockName);
                if (pos != null) { // only write if it exists/returns valid
                    Map<String, Object> data = new HashMap<>();
                    data.put("x", pos.x);
                    data.put("y", pos.y);
                    data.put("z", depth);
                    socketsMap.put(sockName, data);
                }
            }

            root.put("sockets", socketsMap);

            skelFile.writeString(json.prettyPrint(root), false);
            Gdx.app.log("Editor", "Saved skeleton.json");

        } catch (Exception e) {
            Gdx.app.error("Editor", "Error saving skeleton", e);
        }

        // 2. Update Armor/Items JSON
        try {
            FileHandle armorFile = Gdx.files.local("assets/data/armor.json");
            @SuppressWarnings("unchecked")
            // Use OrderedMap to preserve order if possible, though HashMap is often default
            // for Json
            com.badlogic.gdx.utils.OrderedMap<String, ItemTemplate> armorMap = json
                    .fromJson(com.badlogic.gdx.utils.OrderedMap.class, ItemTemplate.class, armorFile);

            boolean changed = false;
            for (Item item : equippedItems.values()) {
                if (item.getType().name().equals("SCROLL") || item.getType().name().equals("POTION"))
                    continue;

                String key = item.getTypeName();
                ItemTemplate t = armorMap.get(key);
                if (t != null) {
                    // Update only if changed? checking first might be optimization, but assigning
                    // is fine.
                    // Important: The UI might have floats like 1.00001, maybe round?
                    t.offsetX = item.getOffsetX();
                    t.offsetY = item.getOffsetY();
                    t.scaleX = item.getScale().x;
                    t.scaleY = item.getScale().y;

                    // SYNC Vector2Wrapper scale to ensure World Rendering (EntityRenderer) picks it
                    // up
                    if (t.scale == null) {
                        t.scale = new ItemTemplate.Vector2Wrapper();
                    }
                    t.scale.x = t.scaleX;
                    t.scale.y = t.scaleY;

                    Gdx.app.log("Editor", String.format("Updated %s: Scale(%.2f, %.2f) Offset(%.2f, %.2f)",
                            key, t.scaleX, t.scaleY, t.offsetX, t.offsetY));
                    changed = true;
                }
            }

            if (changed) {
                armorFile.writeString(json.prettyPrint(armorMap), false);
                Gdx.app.log("Editor", "Saved armor.json");
            }

        } catch (Exception e) {
            Gdx.app.error("Editor", "Failed to save armor JSON", e);
        }

        // 3. Trigger Global Reload
        game.getItemDataManager().reloadAll();
        Gdx.app.log("Editor", "Triggered global ItemDataManager reload.");
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.2f, 0.2f, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();

        // Input handling for hotkeys if needed
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            // maybe close?
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    private void populateItemSelectBox(String category) {
        Array<ItemType> items = new Array<>();
        for (ItemType t : ItemType.values()) {
            String name = t.name();

            // Heuristic check for armor based on common keywords if isArmor() isn't
            // available on Enum
            ItemTemplate template = null;
            try {
                template = game.getItemDataManager().getTemplate(t);
            } catch (Exception e) {
                // Template missing, fall back to name-based heuristics
            }

            boolean isArmor = false;
            boolean isWeapon = false;
            boolean isMisc = false;

            if (template != null) {
                // Check varied template flags for "End User" categorization
                isArmor = template.isArmor || template.isHelmet || template.isBoots || template.isGauntlets
                        || template.isLegs || template.isShield || template.isCloak || template.isAmulet
                        || template.isRing;
                isWeapon = template.isWeapon;
            } else {
                // Fallback name check if template missing
                isWeapon = name.contains("SWORD") || name.contains("AXE") || name.contains("MACE")
                        || name.contains("DAGGER") || name.contains("SPEAR") || name.contains("BOW")
                        || name.contains("CROSSBOW") || name.contains("STAFF") || name.contains("WAND")
                        || name.contains("FLAIL") || name.contains("HALBERD") || name.contains("HAMMER")
                        || name.contains("PICK") || name.contains("MORNING_STAR") || name.contains("CLUB")
                        || name.contains("SCIMITAR") || name.contains("KATANA") || name.contains("GLAIVE")
                        || name.contains("LANCE") || name.contains("TRIDENT") || name.contains("WHIP");
            }

            if (!isArmor && !isWeapon)
                isMisc = true;

            if (category.equals("All")) {
                if (!isMisc || (template != null && (template.isPotion || template.isScrollAppearance)))
                    items.add(t);
            } else if (category.equals("Armor") && isArmor) {
                items.add(t);
            } else if (category.equals("Weapon") && isWeapon) {
                items.add(t);
            } else if (category.equals("Misc") && isMisc) {
                items.add(t);
            }
        }

        // Sort alphabetically
        items.sort(new java.util.Comparator<ItemType>() {
            @Override
            public int compare(ItemType o1, ItemType o2) {
                return o1.name().compareTo(o2.name());
            }
        });

        itemSelectBox.setItems(items);
    }
}
