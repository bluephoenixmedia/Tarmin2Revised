package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.paperdoll.PaperDoll2DWidget;
import com.bpm.minotaur.paperdoll.calibration.CalibrationEditModel;
import com.bpm.minotaur.paperdoll.calibration.LayerCalibration;

import java.util.List;

/**
 * Calibration editor for the 2D paperdoll.
 *
 * Rebuilt against {@link PaperDoll2DWidget} and paperdoll_calibration.json. The previous
 * version drove the legacy PaperDollWidget and wrote assets/data/skeleton.json, so it
 * edited a doll the inventory does not display -- you could calibrate all day and the
 * game would look exactly the same.
 *
 * The design brief was that calibrating a piece is easy and happens once, so this is
 * built for walking a list rather than for fiddling with one item. Every transform has
 * both a slider and a key: the keys are precise, the sliders are fast, and rotation in
 * particular was unusable on keys alone at a degree per press.
 *
 *   [ ]            previous / next layer
 *   arrows         nudge 1px   (+SHIFT 10px)
 *   + / -          scale both axes   (SHIFT: width only, CTRL: height only)
 *   , / .          rotate
 *   R              revert this layer
 *   D              reset to the slot's median placement
 *   CTRL+S         save
 *   F5             re-read the file from disk, so it can be hand-edited alongside
 *   ESC            back
 */
public class PaperdollEditorScreen extends BaseScreen {

    private static final float NUDGE = 1f;
    private static final float NUDGE_FAST = 10f;
    private static final float SCALE_STEP = 1.01f;
    private static final float ROTATE_STEP = 1f;

    // Slider ranges. Offsets reach beyond the canvas half-extents (512 x 768) because a
    // layer can legitimately be positioned partly outside it.
    private static final float OFFSET_RANGE_X = 700f;
    private static final float OFFSET_RANGE_Y = 900f;
    private static final float MIN_SCALE = 0.05f;
    private static final float MAX_SCALE = 3f;

    // Matches PaperDollPanel: where base_father.png actually sits on the inventory page.
    private static final float PORTRAIT_W = 388.2f;
    private static final float PORTRAIT_H = 559.3f;

    private Stage stage;
    private Skin skin;
    private Texture whitePixel;

    private PaperDoll2DWidget doll;
    private CalibrationEditModel model;

    private SelectBox<String> slotBox;
    private SelectBox<String> layerBox;
    private Label statusLabel;
    private Label valuesLabel;
    private Slider offsetXSlider, offsetYSlider, scaleXSlider, scaleYSlider, rotationSlider;
    private Label offsetXValue, offsetYValue, scaleXValue, scaleYValue, rotationValue;
    private CheckBox hidesHairBox;
    private CheckBox hidesBeardBox;
    private CheckBox needsArtRedoBox;

    private final com.badlogic.gdx.Screen previousScreen;
    private boolean syncing = false;
    private boolean confirmingExit = false;
    private boolean confirmingReload = false;

    public PaperdollEditorScreen(Tarmin2 game, com.badlogic.gdx.Screen previousScreen) {
        super(game);
        this.previousScreen = previousScreen;
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        Gdx.input.setInputProcessor(stage);

        buildSkin();

        doll = new PaperDoll2DWidget();
        model = new CalibrationEditModel(doll.getCalibration());

        Table root = new Table();
        root.setFillParent(true);
        root.top().left();
        stage.addActor(root);

        // Preview at 2x the in-game portrait size: calibration is judged by eye, and the
        // real portrait is only 388px wide on a 1920px stage.
        Table previewCell = new Table();
        doll.setSize(PORTRAIT_W * 2f, PORTRAIT_H * 2f);
        previewCell.add(doll).size(PORTRAIT_W * 2f, PORTRAIT_H * 2f);

        root.add(previewCell).pad(30f).top().left();
        root.add(buildControls()).pad(30f).top().left().expandX().fillX();

        stage.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                return handleKey(keycode);
            }
        });

        if (!model.allLayers().isEmpty()) {
            selectLayer(model.allLayers().get(0));
        }
        refreshStatus();
    }

    private void buildSkin() {
        skin = new Skin();
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();
        // Registered as a TextureRegion, not a TextureRegionDrawable: Skin.newDrawable()
        // resolves a name against Drawable/TextureRegion/Texture/NinePatch/Sprite, and a
        // TextureRegionDrawable entry matches none of those — it throws "No Drawable...
        // registered with name: white" at screen construction.
        skin.add("white", new TextureRegion(whitePixel));

        BitmapFont font = new BitmapFont();
        skin.add("default-font", font);

        Label.LabelStyle label = new Label.LabelStyle();
        label.font = font;
        label.fontColor = Color.WHITE;
        skin.add("default", label);

        TextButton.TextButtonStyle button = new TextButton.TextButtonStyle();
        button.font = font;
        button.up = skin.newDrawable("white", new Color(0.25f, 0.25f, 0.3f, 1f));
        button.down = skin.newDrawable("white", new Color(0.4f, 0.4f, 0.5f, 1f));
        button.over = skin.newDrawable("white", new Color(0.32f, 0.32f, 0.4f, 1f));
        skin.add("default", button);

        CheckBox.CheckBoxStyle check = new CheckBox.CheckBoxStyle();
        check.font = font;
        check.fontColor = Color.WHITE;
        check.checkboxOff = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 1f));
        check.checkboxOn = skin.newDrawable("white", new Color(0.3f, 0.8f, 0.3f, 1f));
        skin.add("default", check);

        Slider.SliderStyle slider = new Slider.SliderStyle();
        slider.background = skin.newDrawable("white", new Color(0.18f, 0.18f, 0.22f, 1f));
        slider.background.setMinHeight(10f);
        // The knob needs an explicit size or it renders as a 1x1 pixel and cannot be grabbed.
        slider.knob = skin.newDrawable("white", new Color(0.75f, 0.75f, 0.85f, 1f));
        slider.knob.setMinWidth(14f);
        slider.knob.setMinHeight(26f);
        slider.knobDown = skin.newDrawable("white", new Color(1f, 1f, 1f, 1f));
        slider.knobDown.setMinWidth(14f);
        slider.knobDown.setMinHeight(26f);
        skin.add("default-horizontal", slider);

        SelectBox.SelectBoxStyle select = new SelectBox.SelectBoxStyle();
        select.font = font;
        select.fontColor = Color.WHITE;
        select.background = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.25f, 1f));
        com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle listStyle =
                new com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle();
        listStyle.font = font;
        listStyle.fontColorSelected = Color.WHITE;
        listStyle.fontColorUnselected = Color.LIGHT_GRAY;
        listStyle.selection = skin.newDrawable("white", new Color(0.3f, 0.4f, 0.6f, 1f));
        select.listStyle = listStyle;
        select.scrollStyle = new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle();
        select.scrollStyle.background = skin.newDrawable("white", new Color(0.15f, 0.15f, 0.18f, 1f));
        skin.add("default", select);
    }

    private Table buildControls() {
        Table t = new Table();
        t.top().left().defaults().left().pad(4f);

        t.add(new Label("PAPERDOLL CALIBRATION", skin)).colspan(2).row();
        t.add(new Label("", skin)).row();

        slotBox = new SelectBox<String>(skin);
        Array<String> slots = new Array<String>();
        for (String s : model.slots()) {
            slots.add(s);
        }
        slotBox.setItems(slots);
        slotBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (syncing) return;
                populateLayerBox(slotBox.getSelected());
                if (layerBox.getItems().size > 0) {
                    selectLayer(layerBox.getItems().first());
                }
            }
        });
        t.add(new Label("Slot", skin));
        t.add(slotBox).width(320f).row();

        layerBox = new SelectBox<String>(skin);
        layerBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (syncing) return;
                selectLayer(layerBox.getSelected());
            }
        });
        t.add(new Label("Layer", skin));
        t.add(layerBox).width(320f).row();

        valuesLabel = new Label("", skin);
        t.add(valuesLabel).colspan(2).padTop(14f).row();

        // Sliders alongside the keys. Rotation in particular was unusable on keys alone:
        // a degree per press means 180 presses to turn a piece round.
        Table sliders = new Table();
        sliders.defaults().left().pad(3f);
        offsetXValue = new Label("", skin);
        offsetYValue = new Label("", skin);
        scaleXValue = new Label("", skin);
        scaleYValue = new Label("", skin);
        rotationValue = new Label("", skin);
        offsetXSlider = addSlider(sliders, "X", -OFFSET_RANGE_X, OFFSET_RANGE_X, 1f, offsetXValue);
        offsetYSlider = addSlider(sliders, "Y", -OFFSET_RANGE_Y, OFFSET_RANGE_Y, 1f, offsetYValue);
        scaleXSlider = addSlider(sliders, "Width", MIN_SCALE, MAX_SCALE, 0.001f, scaleXValue);
        scaleYSlider = addSlider(sliders, "Height", MIN_SCALE, MAX_SCALE, 0.001f, scaleYValue);
        rotationSlider = addSlider(sliders, "Rotation", -180f, 180f, 0.5f, rotationValue);
        t.add(sliders).colspan(2).padTop(10f).row();

        hidesHairBox = flag("Helmet hides hair");
        hidesBeardBox = flag("Helmet hides beard");
        needsArtRedoBox = flag("Art needs redoing (wrong perspective)");
        t.add(hidesHairBox).colspan(2).row();
        t.add(hidesBeardBox).colspan(2).row();
        t.add(needsArtRedoBox).colspan(2).row();

        Table buttons = new Table();
        buttons.defaults().pad(4f);
        buttons.add(button("< Prev  [", new Runnable() {
            public void run() { model.previous(); syncFromModel(); }
        }));
        buttons.add(button("Next >  ]", new Runnable() {
            public void run() { model.next(); syncFromModel(); }
        }));
        buttons.add(button("Revert  R", new Runnable() {
            public void run() { model.revert(); syncFromModel(); }
        }));
        buttons.add(button("Slot default  D", new Runnable() {
            public void run() { model.resetToSlotDefault(); syncFromModel(); }
        }));
        buttons.row();
        buttons.add(button("SAVE  ctrl+S", new Runnable() {
            public void run() { save(); }
        })).colspan(2);
        buttons.add(button("Reload  F5", new Runnable() {
            public void run() { reload(); }
        })).colspan(2);
        t.add(buttons).colspan(2).padTop(14f).row();

        statusLabel = new Label("", skin);
        t.add(statusLabel).colspan(2).padTop(14f).row();

        Label help = new Label(
                "arrows nudge 1px (shift 10)   + / - scale (shift faster)\n"
                + ", / . rotate     [ ] step layers     esc back\n"
                + "The file can be hand-edited while this is open; F5 re-reads it.",
                skin);
        t.add(help).colspan(2).padTop(20f).row();

        return t;
    }

    /**
     * One labelled slider row, returning the slider and leaving its value label as the
     * table's last child so the caller can keep a reference to it.
     *
     * Every slider writes straight through to the model and refreshes, so the preview
     * tracks the drag live -- which is the whole reason for having them.
     */
    private Slider addSlider(Table into, String label, float min, float max, float step, Label value) {
        Slider s = new Slider(min, max, step, false, skin);
        s.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (syncing) return;
                applySliders();
            }
        });
        into.add(new Label(label, skin)).width(80f);
        into.add(s).width(320f);
        into.add(value).width(90f);
        into.row();
        return s;
    }

    /** Pushes every slider position into the selected layer's calibration. */
    private void applySliders() {
        if (model.selectedLayerId() == null) {
            return;
        }
        LayerCalibration c = model.current();
        c.offsetX = offsetXSlider.getValue();
        c.offsetY = offsetYSlider.getValue();
        c.scaleX = Math.max(MIN_SCALE, scaleXSlider.getValue());
        c.scaleY = Math.max(MIN_SCALE, scaleYSlider.getValue());
        c.rotation = rotationSlider.getValue();
        model.commit();
        // A focused Slider consumes the arrow keys for itself, so after touching one the
        // arrows would drag that slider instead of nudging the layer. Dropping focus
        // keeps the keys meaning the same thing all the time.
        stage.setKeyboardFocus(null);
        refreshStatus();
    }

    private CheckBox flag(String text) {
        CheckBox cb = new CheckBox(" " + text, skin);
        cb.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (syncing) return;
                LayerCalibration c = model.current();
                c.hidesHair = hidesHairBox.isChecked();
                c.hidesBeard = hidesBeardBox.isChecked();
                c.needsArtRedo = needsArtRedoBox.isChecked();
                model.commit();
                refreshStatus();
            }
        });
        return cb;
    }

    private TextButton button(String text, final Runnable action) {
        TextButton b = new TextButton(text, skin);
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                action.run();
            }
        });
        return b;
    }

    private void populateLayerBox(String slot) {
        Array<String> items = new Array<String>();
        for (String id : model.layersInSlot(slot)) {
            items.add(id);
        }
        syncing = true;
        layerBox.setItems(items);
        syncing = false;
    }

    private void selectLayer(String layerId) {
        model.select(layerId);
        syncFromModel();
    }

    /** Pushes model state into the widget and the controls. */
    private void syncFromModel() {
        String id = model.selectedLayerId();
        if (id == null) {
            return;
        }
        int sep = id.indexOf('/');
        String folder = sep > 0 ? id.substring(0, sep) : "";
        PaperDoll2DWidget.PaperDollSlot slot = PaperDoll2DWidget.slotForFolder(folder);

        doll.clearEquipment();
        if (slot != null) {
            doll.showLayer(slot, id);
        }

        syncing = true;
        if (!folder.equals(slotBox.getSelected())) {
            slotBox.setSelected(folder);
            populateLayerBox(folder);
        }
        layerBox.setSelected(id);
        LayerCalibration c = model.current();
        hidesHairBox.setChecked(c.hidesHair);
        hidesBeardBox.setChecked(c.hidesBeard);
        needsArtRedoBox.setChecked(c.needsArtRedo);
        syncing = false;

        refreshStatus();
    }

    private void refreshStatus() {
        confirmingExit = false;
        confirmingReload = false;
        LayerCalibration c = model.current();

        // Sliders follow the keys and the layer selection, not just the other way round;
        // otherwise a nudge would leave the knob lying about where the layer is.
        syncing = true;
        offsetXSlider.setValue(c.offsetX);
        offsetYSlider.setValue(c.offsetY);
        scaleXSlider.setValue(c.scaleX);
        scaleYSlider.setValue(c.scaleY);
        rotationSlider.setValue(c.rotation);
        syncing = false;

        offsetXValue.setText(String.format("%+.0f", c.offsetX));
        offsetYValue.setText(String.format("%+.0f", c.offsetY));
        scaleXValue.setText(String.format("%.3f", c.scaleX));
        scaleYValue.setText(String.format("%.3f", c.scaleY));
        rotationValue.setText(String.format("%+.1f", c.rotation));
        valuesLabel.setText(String.format(
                "offset  %+.1f, %+.1f      scale  %.3f x %.3f      rotation  %+.1f",
                c.offsetX, c.offsetY, c.scaleX, c.scaleY, c.rotation));

        String id = model.selectedLayerId();
        statusLabel.setText(String.format("%s   (%d of %d)%s",
                id == null ? "-" : id,
                model.selectedIndex() + 1,
                model.layerCount(),
                model.isDirty() ? "    *** UNSAVED ***" : ""));
        statusLabel.setColor(model.isDirty() ? Color.ORANGE : Color.WHITE);
    }

    private void save() {
        model.commit();
        if (doll.saveCalibration()) {
            model.markSaved();
            statusLabel.setText("saved " + model.layerCount() + " layers");
            statusLabel.setColor(Color.GREEN);
        } else {
            statusLabel.setText("SAVE FAILED - no writable assets/data path");
            statusLabel.setColor(Color.RED);
        }
    }

    private void reload() {
        if (model.isDirty() && !confirmingReload) {
            confirmingReload = true;
            statusLabel.setText("UNSAVED CHANGES - reloading discards them. Press F5 again to confirm");
            statusLabel.setColor(Color.ORANGE);
            return;
        }
        confirmingReload = false;
        String selected = model.selectedLayerId();
        doll.reloadCalibration();
        model = new CalibrationEditModel(doll.getCalibration());
        if (selected != null) {
            model.select(selected);
        } else if (!model.allLayers().isEmpty()) {
            model.select(model.allLayers().get(0));
        }
        syncFromModel();
    }

    private boolean handleKey(int keycode) {
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        float step = shift ? NUDGE_FAST : NUDGE;

        switch (keycode) {
            case Input.Keys.LEFT:   model.nudge(-step, 0f); break;
            case Input.Keys.RIGHT:  model.nudge(step, 0f); break;
            case Input.Keys.UP:     model.nudge(0f, -step); break;
            case Input.Keys.DOWN:   model.nudge(0f, step); break;
            // Non-uniform scale is the specific tool for "right height, too narrow",
            // so width and height need to be reachable separately.
            case Input.Keys.EQUALS:
            case Input.Keys.PLUS:
                model.scaleBy(ctrl ? 1f : SCALE_STEP, shift ? 1f : SCALE_STEP);
                break;
            case Input.Keys.MINUS:
                model.scaleBy(ctrl ? 1f : 1f / SCALE_STEP, shift ? 1f : 1f / SCALE_STEP);
                break;
            case Input.Keys.COMMA:  model.rotateBy(-ROTATE_STEP); break;
            case Input.Keys.PERIOD: model.rotateBy(ROTATE_STEP); break;
            case Input.Keys.LEFT_BRACKET:  model.previous(); syncFromModel(); return true;
            case Input.Keys.RIGHT_BRACKET: model.next(); syncFromModel(); return true;
            case Input.Keys.R:      model.revert(); syncFromModel(); return true;
            case Input.Keys.D:      model.resetToSlotDefault(); syncFromModel(); return true;
            case Input.Keys.F5:     reload(); return true;
            case Input.Keys.S:
                if (ctrl) { save(); return true; }
                return false;
            case Input.Keys.ESCAPE:
                exit();
                return true;
            default:
                return false;
        }
        refreshStatus();
        return true;
    }

    /**
     * Leaves the editor, refusing the first time if there is unsaved work.
     *
     * The whole point of tracking dirtiness is to not lose a calibration pass; silently
     * discarding it on the way out would waste exactly the work this screen exists to
     * capture.
     */
    private void exit() {
        if (model.isDirty() && !confirmingExit) {
            confirmingExit = true;
            statusLabel.setText("UNSAVED CHANGES - ctrl+S to save, or press ESC again to discard");
            statusLabel.setColor(Color.ORANGE);
            return;
        }
        // previousScreen is null when booted straight here with --paperdoll, and
        // Game.setScreen(null) leaves a black window with an orphaned input processor.
        game.setScreen(previousScreen != null ? previousScreen : new MainMenuScreen(game));
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.10f, 0.11f, 0.13f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    /**
     * Releases everything on the way out.
     *
     * Game.setScreen() calls hide(), never dispose(), so releasing in dispose() alone
     * would leak the stage, the skin and its font, the white pixel, and one texture per
     * layer stepped through -- on every visit to the editor. LoadingScreen does the same
     * thing for the same reason.
     */
    @Override
    public void hide() {
        if (Gdx.input.getInputProcessor() == stage) {
            Gdx.input.setInputProcessor(null);
        }
        releaseResources();
    }

    @Override
    public void dispose() {
        releaseResources();
    }

    private void releaseResources() {
        if (stage != null) { stage.dispose(); stage = null; }
        if (doll != null) { doll.dispose(); doll = null; }
        if (skin != null) { skin.dispose(); skin = null; }
        if (whitePixel != null) { whitePixel.dispose(); whitePixel = null; }
    }
}
