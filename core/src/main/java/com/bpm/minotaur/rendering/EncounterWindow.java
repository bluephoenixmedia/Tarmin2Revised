package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.bpm.minotaur.gamedata.encounters.Encounter;
import com.bpm.minotaur.gamedata.encounters.EncounterChoice;
import com.bpm.minotaur.gamedata.encounters.EncounterManager;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.gamedata.Maze;
import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;

import java.util.ArrayList;
import java.util.List;

public class EncounterWindow extends Table {
    private final Label titleLabel;
    private final Image eventImage;
    private final Label bodyLabel;
    private final Table choicesTable;
    private final TextButton.TextButtonStyle buttonStyle;

    private Player player;
    private EncounterManager encounterManager;
    private GameEventManager eventManager;
    private ItemDataManager itemDataManager;
    private MonsterDataManager monsterDataManager;
    private AssetManager assetManager;
    private Maze currentMaze;
    private Runnable onClose;

    private List<TextButton> choiceButtons = new ArrayList<>();
    private List<EncounterChoice> choiceData = new ArrayList<>(); // Keep track of data
    private int selectedIndex = -1;

    // Drawables for button states
    private TextureRegionDrawable normalDrawable;
    private TextureRegionDrawable selectedDrawable;
    private TextureRegionDrawable disabledDrawable;

    public EncounterWindow(Label.LabelStyle titleStyle, Label.LabelStyle bodyStyle,
            TextButton.TextButtonStyle buttonStyle) {

        // --- Generate Custom Drawables for Borders ---
        normalDrawable = createBorderedDrawable(new com.badlogic.gdx.graphics.Color(0.1f, 0.1f, 0.1f, 0.9f),
                com.badlogic.gdx.graphics.Color.GRAY);
        selectedDrawable = createBorderedDrawable(new com.badlogic.gdx.graphics.Color(0.2f, 0.2f, 0.2f, 0.9f),
                com.badlogic.gdx.graphics.Color.GOLD);
        disabledDrawable = createBorderedDrawable(new com.badlogic.gdx.graphics.Color(0.05f, 0.05f, 0.05f, 0.5f),
                com.badlogic.gdx.graphics.Color.DARK_GRAY);

        // Copy style to override backgrounds
        this.buttonStyle = new TextButton.TextButtonStyle(buttonStyle);
        this.buttonStyle.up = normalDrawable;
        this.buttonStyle.over = selectedDrawable;
        this.buttonStyle.disabled = disabledDrawable;
        this.buttonStyle.fontColor = com.badlogic.gdx.graphics.Color.WHITE;
        this.buttonStyle.overFontColor = com.badlogic.gdx.graphics.Color.GOLD;
        this.buttonStyle.disabledFontColor = com.badlogic.gdx.graphics.Color.GRAY;

        // Dark background with border (NinePatch)
        Texture bgTex = new Texture(Gdx.files.internal("images/hud_bg.png"));
        int split = 80;
        com.badlogic.gdx.graphics.g2d.NinePatch patch = new com.badlogic.gdx.graphics.g2d.NinePatch(bgTex, split, split,
                split, split);
        this.setBackground(new com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable(patch));

        this.pad(split); // Use same padding as split to effectively keep content inside inner area
        this.defaults().space(10);
        this.align(Align.top);

        titleLabel = new Label("", titleStyle);
        titleLabel.setAlignment(Align.center);
        this.add(titleLabel).growX().padBottom(20).row();

        eventImage = new Image();
        eventImage.setScaling(Scaling.fit);
        this.add(eventImage).size(900, 400).padBottom(20).row();

        bodyLabel = new Label("", bodyStyle);
        bodyLabel.setWrap(true);
        bodyLabel.setAlignment(Align.left);
        this.add(bodyLabel).growX().width(500).padBottom(20).row();

        choicesTable = new Table();
        this.add(choicesTable).growX().row();

        this.setVisible(false);

        // --- Keyboard Listener (Local Focus Fallback) ---
        this.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                return handleInput(keycode);
            }
        });
    }

    public boolean handleInput(int keycode) {
        if (!isVisible())
            return false;

        if (keycode == com.badlogic.gdx.Input.Keys.UP || keycode == com.badlogic.gdx.Input.Keys.W) {
            moveSelection(-1);
            return true;
        } else if (keycode == com.badlogic.gdx.Input.Keys.DOWN || keycode == com.badlogic.gdx.Input.Keys.S) {
            moveSelection(1);
            return true;
        } else if (keycode == com.badlogic.gdx.Input.Keys.ENTER
                || keycode == com.badlogic.gdx.Input.Keys.SPACE) {
            triggerSelection();
            return true;
        } else if (keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
            close(); // Emergency exit
            return true;
        }
        return false;
    }

    private TextureRegionDrawable createBorderedDrawable(com.badlogic.gdx.graphics.Color fillColor,
            com.badlogic.gdx.graphics.Color borderColor) {
        com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(100, 40,
                com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);

        // Fill
        pixmap.setColor(fillColor);
        pixmap.fill();

        // Border
        pixmap.setColor(borderColor);
        int w = pixmap.getWidth();
        int h = pixmap.getHeight();
        // Draw rectangle 2px thick
        pixmap.drawRectangle(0, 0, w, h);
        pixmap.drawRectangle(1, 1, w - 2, h - 2);

        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    public void configure(Player player, EncounterManager encounterManager, GameEventManager eventManager,
            ItemDataManager itemDataManager, MonsterDataManager monsterDataManager,
            AssetManager assetManager, Maze maze, Runnable onClose) {
        this.player = player;
        this.encounterManager = encounterManager;
        this.eventManager = eventManager;
        this.itemDataManager = itemDataManager;
        this.monsterDataManager = monsterDataManager;
        this.assetManager = assetManager;
        this.currentMaze = maze;
        this.onClose = onClose;
    }

    public void show(Encounter encounter) {
        if (encounter == null)
            return;

        titleLabel.setText(encounter.title);
        bodyLabel.setText(encounter.text);

        if (encounter.imagePath != null && assetManager.isLoaded(encounter.imagePath, Texture.class)) {
            eventImage.setDrawable(new TextureRegionDrawable(assetManager.get(encounter.imagePath, Texture.class)));
        } else {
            eventImage.setDrawable(null);
        }

        choicesTable.clearChildren();
        choiceButtons.clear();
        choiceData.clear();
        selectedIndex = -1;

        for (int i = 0; i < encounter.choices.size(); i++) {
            EncounterChoice choice = encounter.choices.get(i);
            // Create a UNIQUE style for each button so we can change its 'up' drawable
            // independently
            TextButton.TextButtonStyle uniqueStyle = new TextButton.TextButtonStyle(buttonStyle);
            TextButton btn = new TextButton(choice.text, uniqueStyle);

            boolean reqMet = encounterManager.checkRequirement(player, choice.requirement);
            boolean costMet = encounterManager.checkCost(player, choice.cost);

            btn.setDisabled(!reqMet || !costMet);

            if (!reqMet) {
                if (choice.requirement != null) {
                    btn.setText(choice.text + " [Req: " + choice.requirement.stat + "]");
                }
            } else if (!costMet) {
                if (choice.cost != null) {
                    btn.setText(choice.text + " [Cost: " + choice.cost.amount + " " + choice.cost.type + "]");
                }
            }

            Gdx.app.log("EncounterWindow", "Choice '" + choice.text + "' Disabled: " + btn.isDisabled() + " (Req: "
                    + reqMet + ", Cost: " + costMet + ")");

            final int index = i;
            if (!btn.isDisabled()) {
                // Determine initial selection
                if (selectedIndex == -1)
                    selectedIndex = index;

                btn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        resolveChoice(choiceData.get(index));
                    }

                    @Override
                    public void enter(InputEvent event, float x, float y, int pointer,
                            com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                        if (pointer == -1) { // Mouse move
                            updateSelectionIndex(index);
                        }
                        super.enter(event, x, y, pointer, fromActor);
                    }
                });
            }

            choiceButtons.add(btn);
            choiceData.add(choice);
            choicesTable.add(btn).growX().padBottom(10).row();
        }

        updateSelectionVisuals();

        // --- Fix Positioning: Center Strictly ---
        this.pack(); // Re-calculate size
        if (getStage() != null) {
            this.setPosition(getStage().getWidth() / 2f, getStage().getHeight() / 2f, Align.center);
        }

        this.setVisible(true);
        this.toFront();

        // Request Focus for Keyboard
        if (getStage() != null) {
            getStage().setKeyboardFocus(this);
            getStage().setScrollFocus(this);
        }

        // Unlock cursor for UI interaction
        Gdx.input.setCursorCatched(false);
    }

    private void moveSelection(int delta) {
        if (choiceButtons.isEmpty())
            return;

        int nextIndex = selectedIndex;
        int attempts = 0; // Prevent infinite loop if all disabled
        do {
            nextIndex += delta;
            if (nextIndex >= choiceButtons.size())
                nextIndex = 0;
            if (nextIndex < 0)
                nextIndex = choiceButtons.size() - 1;

            attempts++;
        } while (choiceButtons.get(nextIndex).isDisabled() && attempts < choiceButtons.size());

        if (!choiceButtons.get(nextIndex).isDisabled()) {
            updateSelectionIndex(nextIndex);
        }
    }

    private void updateSelectionIndex(int index) {
        if (index < 0 || index >= choiceButtons.size())
            return;
        if (choiceButtons.get(index).isDisabled())
            return; // Don't select disabled via mouse hover

        selectedIndex = index;
        updateSelectionVisuals();
    }

    private void updateSelectionVisuals() {
        for (int i = 0; i < choiceButtons.size(); i++) {
            TextButton btn = choiceButtons.get(i);
            if (btn.isDisabled()) {
                btn.getStyle().up = disabledDrawable;
            } else {
                if (i == selectedIndex) {
                    btn.getStyle().up = selectedDrawable;
                } else {
                    btn.getStyle().up = normalDrawable;
                }
            }
        }
    }

    private void triggerSelection() {
        if (selectedIndex >= 0 && selectedIndex < choiceButtons.size()) {
            TextButton btn = choiceButtons.get(selectedIndex);
            if (!btn.isDisabled()) {
                resolveChoice(choiceData.get(selectedIndex));
            }
        }
    }

    private void resolveChoice(EncounterChoice choice) {
        encounterManager.payCost(player, choice.cost);

        boolean success = true;
        if (choice.successChance < 1.0f) {
            success = Math.random() < choice.successChance;
        }

        if (success) {
            encounterManager.resolveResult(choice.successEffect, player, eventManager, itemDataManager,
                    monsterDataManager, assetManager, currentMaze);
        } else {
            encounterManager.resolveResult(choice.failEffect, player, eventManager, itemDataManager, monsterDataManager,
                    assetManager, currentMaze);
        }

        close();
    }

    private void close() {
        this.setVisible(false);
        if (getStage() != null) {
            getStage().setKeyboardFocus(null); // Release focus
        }
        if (onClose != null)
            onClose.run();

        // Lock cursor back for gameplay -> REMOVED because Tarmin2 uses
        // click-to-move/interact/buttons
        // and doesn't support mouse-look, so we need the cursor visible.
        // Gdx.input.setCursorCatched(true);
    }
}
