package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.events.EventArt;
import com.bpm.minotaur.gamedata.events.EventCatalog;
import com.bpm.minotaur.gamedata.events.EventChoice;
import com.bpm.minotaur.gamedata.events.EventDefinition;
import com.bpm.minotaur.gamedata.events.EventOdds;
import com.bpm.minotaur.gamedata.events.EventResolver;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.ui.UiContexts;
import com.bpm.minotaur.ui.UiGlyphs;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The choice event scene: art, text, and a row per choice; then what happened and Continue.
 *
 * <p>Built to the UI overhaul rules rather than on {@link EncounterWindow}, which stays with the
 * statues. Full-stage: the event's backdrop dimmed under the scrim, with the panel centred in the
 * play area above the HUD dashboard. It holds the input context while open, so the world
 * underneath hears nothing (SPEC 5.6).
 */
public class EventWindow extends Table {

    private static final String CONTEXT = "CHOICE_EVENT";

    /** Height of the HUD dashboard the panel must stay clear of. */
    private static final float HUD_HEIGHT = 180f;

    private static final float PANEL_W = UiTheme.vu(300);
    private static final float TEXT_W = PANEL_W - 2 * UiTheme.PAD_XXL;
    /** The scene art is authored at 1408x752 (1.87:1); this keeps that shape. */
    private static final float ART_W = UiTheme.vu(188);
    private static final float ART_H = UiTheme.vu(100);
    /** The tag beside a choice ("WIS 55%", or why it is shut) may take at most this much of the row. */
    private static final float TAG_W = UiTheme.vu(90);
    /** How much of the backdrop art shows through under the scrim. */
    private static final float BACKDROP_ALPHA = 0.45f;

    private final HudSkin hudSkin;
    private final Image backdrop;
    private final Label titleLabel;
    private final Image sceneArt;
    private final Label bodyLabel;
    private final Table choicesTable;
    private final Table resultTable;

    private final List<Table> rows = new ArrayList<>();
    private final List<Label> rowLabels = new ArrayList<>();
    private final List<Boolean> rowEnabled = new ArrayList<>();
    private int selected = -1;

    private EventDefinition current;
    private String biome;
    private EventResolver resolver;
    private String pendingChain;

    private Player player;
    private Maze maze;
    private GameEventManager eventManager;
    private ItemDataManager itemDataManager;
    private MonsterDataManager monsterDataManager;
    private AssetManager assetManager;

    public EventWindow(HudSkin hudSkin) {
        this.hudSkin = hudSkin;
        setFillParent(true);

        backdrop = new Image();
        backdrop.setScaling(Scaling.fill);
        backdrop.setColor(1f, 1f, 1f, BACKDROP_ALPHA);

        Image scrim = new Image(hudSkin.getScrim());

        Table panel = new Table();
        panel.setBackground(hudSkin.getDoubleBorderPanel());
        panel.pad(UiTheme.PAD_XL, UiTheme.PAD_XXL, UiTheme.PAD_XL, UiTheme.PAD_XXL);
        panel.top();

        titleLabel = new Label("", UiStyles.display(hudSkin));
        titleLabel.setAlignment(Align.center);
        titleLabel.setEllipsis(true);
        panel.add(titleLabel).width(TEXT_W).padBottom(UiTheme.PAD_SM).row();

        sceneArt = new Image();
        sceneArt.setScaling(Scaling.fit);
        panel.add(sceneArt).size(ART_W, ART_H).padBottom(UiTheme.PAD_MD).row();

        bodyLabel = UiLabels.wrapping("", UiStyles.body(hudSkin));
        panel.add(bodyLabel).width(TEXT_W).padBottom(UiTheme.PAD_MD).row();

        choicesTable = new Table();
        panel.add(choicesTable).width(TEXT_W).row();

        resultTable = new Table();
        panel.add(resultTable).width(TEXT_W).row();

        // The panel sits in the play area: above the dashboard, centred in what is left.
        Table layer = new Table();
        layer.add(panel).width(PANEL_W).expand().center().padBottom(HUD_HEIGHT);

        Stack stack = new Stack();
        stack.add(backdrop);
        stack.add(scrim);
        stack.add(layer);
        add(stack).grow();

        setTouchable(Touchable.enabled);
        setVisible(false);
    }

    public void configure(Player player, Maze maze, GameEventManager eventManager, ItemDataManager itemDataManager,
                          MonsterDataManager monsterDataManager, AssetManager assetManager) {
        this.player = player;
        this.maze = maze;
        this.eventManager = eventManager;
        this.itemDataManager = itemDataManager;
        this.monsterDataManager = monsterDataManager;
        this.assetManager = assetManager;
    }

    /** Opens an event; {@code biome} picks the fallback art. */
    public void show(EventDefinition event, String biome) {
        if (event == null) {
            return;
        }
        this.current = event;
        this.biome = biome;
        this.pendingChain = null;
        this.resolver = new EventResolver(player, maze, eventManager, itemDataManager, monsterDataManager,
                assetManager, new Random());

        titleLabel.setText(UiGlyphs.sanitize(event.title));
        bodyLabel.setText(UiGlyphs.sanitize(event.text));
        sceneArt.setDrawable(drawable(EventArt.image(event, biome, this::exists)));
        backdrop.setDrawable(drawable(EventArt.background(event, biome, this::exists)));

        buildChoices();
        resultTable.clear();
        resultTable.setVisible(false);
        choicesTable.setVisible(true);

        if (!isVisible()) {
            UiContexts.push(CONTEXT, UiContexts.Kind.MODAL);
        }
        if (getStage() != null) {
            getStage().setKeyboardFocus(this);
        }
        setVisible(true);
        toFront();
        Gdx.input.setCursorCatched(false);
    }

    private void buildChoices() {
        choicesTable.clear();
        rows.clear();
        rowLabels.clear();
        rowEnabled.clear();
        selected = -1;

        for (int i = 0; i < current.choices.size(); i++) {
            final int index = i;
            EventChoice choice = current.choices.get(i);
            String unmet = resolver.unmetLabel(choice);
            boolean enabled = unmet == null;

            Table row = new Table();
            row.pad(UiTheme.PAD_SM, UiTheme.PAD_MD, UiTheme.PAD_SM, UiTheme.PAD_MD);

            Table keycap = new Table();
            keycap.setBackground(hudSkin.getKeycap());
            keycap.add(new Label(String.valueOf(i + 1), UiStyles.caption(hudSkin, UiTheme.TEXT)));
            row.add(keycap).size(UiTheme.ICON_MD).top().padRight(UiTheme.PAD_MD);

            Label text = UiLabels.wrapping(choice.text,
                    enabled ? UiStyles.body(hudSkin) : UiStyles.disabled(hudSkin));
            row.add(text).growX().left();

            String tag = enabled ? oddsTag(choice) : unmet;
            if (tag != null) {
                Label tagLabel = UiLabels.ellipsized("[ " + tag + " ]",
                        UiStyles.caption(hudSkin, enabled ? UiTheme.TEXT_DIM : UiTheme.DANGER));
                tagLabel.setAlignment(Align.right);
                row.add(tagLabel).width(TAG_W).right().padLeft(UiTheme.PAD_MD);
            }

            if (enabled) {
                row.setTouchable(Touchable.enabled);
                row.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        choose(index);
                    }

                    @Override
                    public void enter(InputEvent event, float x, float y, int pointer,
                                      com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                        if (pointer == -1) {
                            select(index);
                        }
                        super.enter(event, x, y, pointer, fromActor);
                    }
                });
                if (selected < 0) {
                    selected = index;
                }
            } else {
                row.setTouchable(Touchable.disabled);
            }

            rows.add(row);
            rowLabels.add(text);
            rowEnabled.add(enabled);
            choicesTable.add(row).growX().padBottom(UiTheme.PAD_XS).row();
        }
        refreshSelection();
    }

    /** "WIS 55%" for an attribute check, "Luck 30%" for a luck roll, nothing when certain. */
    private String oddsTag(EventChoice choice) {
        if (choice.check == null) {
            return null;
        }
        String name = choice.check.attribute != null ? choice.check.attribute : "Luck";
        return name + " " + EventOdds.percent(resolver.chance(choice)) + "%";
    }

    private void select(int index) {
        if (index >= 0 && index < rows.size() && rowEnabled.get(index)) {
            selected = index;
            refreshSelection();
        }
    }

    private void refreshSelection() {
        for (int i = 0; i < rows.size(); i++) {
            boolean enabled = rowEnabled.get(i);
            rows.get(i).setBackground(!enabled ? hudSkin.getInsetBg()
                    : i == selected ? hudSkin.getTabActive() : hudSkin.getCardBg());
            if (enabled) {
                rowLabels.get(i).setStyle(UiStyles.body(hudSkin, i == selected ? UiTheme.GOLD : UiTheme.TEXT));
            }
        }
    }

    private void moveSelection(int delta) {
        if (rows.isEmpty()) {
            return;
        }
        int next = selected < 0 ? 0 : selected;
        for (int tries = 0; tries < rows.size(); tries++) {
            next = (next + delta + rows.size()) % rows.size();
            if (rowEnabled.get(next)) {
                select(next);
                return;
            }
        }
    }

    private void choose(int index) {
        if (!choicesTable.isVisible() || index < 0 || index >= rows.size() || !rowEnabled.get(index)) {
            return;
        }
        EventResolver.Result result = resolver.resolve(current.choices.get(index));
        pendingChain = result.chainedEventId;
        showResult(result);
    }

    private void showResult(EventResolver.Result result) {
        choicesTable.setVisible(false);
        resultTable.clear();
        for (String line : result.messages) {
            resultTable.add(UiLabels.wrapping(line, UiStyles.body(hudSkin, UiTheme.GOLD)))
                    .width(TEXT_W).padBottom(UiTheme.PAD_SM).row();
        }
        TextButton cont = new TextButton("CONTINUE", UiStyles.primary(hudSkin));
        cont.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                proceed();
            }
        });
        resultTable.add(cont).height(UiTheme.BUTTON_H).minWidth(UiTheme.BUTTON_MIN_W).padTop(UiTheme.PAD_SM).row();
        resultTable.setVisible(true);
    }

    /** After the result: open a chained event if there is one, otherwise close. */
    private void proceed() {
        EventDefinition next = pendingChain != null ? EventCatalog.getInstance().get(pendingChain) : null;
        if (next != null) {
            show(next, biome);
        } else {
            close();
        }
    }

    public void close() {
        setVisible(false);
        UiContexts.pop(CONTEXT);
        if (getStage() != null) {
            getStage().setKeyboardFocus(null);
        }
        current = null;
    }

    public boolean handleInput(int keycode) {
        if (!isVisible()) {
            return false;
        }
        if (resultTable.isVisible()) {
            if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE || keycode == Input.Keys.E
                    || keycode == Input.Keys.ESCAPE) {
                proceed();
            }
            return true;
        }
        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
            choose(keycode - Input.Keys.NUM_1);
        } else if (keycode >= Input.Keys.NUMPAD_1 && keycode <= Input.Keys.NUMPAD_9) {
            choose(keycode - Input.Keys.NUMPAD_1);
        } else if (keycode == Input.Keys.UP || keycode == Input.Keys.W) {
            moveSelection(-1);
        } else if (keycode == Input.Keys.DOWN || keycode == Input.Keys.S) {
            moveSelection(1);
        } else if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE || keycode == Input.Keys.E) {
            choose(selected);
        }
        // Everything else is swallowed. There is no Escape out of a scene: every event has a
        // choice with no gate and no cost, and that is the way out.
        return true;
    }

    private boolean exists(String path) {
        return path != null && Gdx.files != null && Gdx.files.internal(path).exists();
    }

    private TextureRegionDrawable drawable(String path) {
        if (path == null || assetManager == null) {
            return null;
        }
        if (!assetManager.isLoaded(path, Texture.class)) {
            assetManager.load(path, Texture.class);
            assetManager.finishLoadingAsset(path);
        }
        return new TextureRegionDrawable(assetManager.get(path, Texture.class));
    }
}
