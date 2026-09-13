package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.bpm.minotaur.managers.SettingsManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Toggleable (TAB) full key-legend overlay, listing every action tracked by
 * SettingsManager alongside its currently bound key. Kept in sync with the
 * Controls settings screen since both read from the same source of truth;
 * key labels are re-read on every show in case the player just rebound one.
 */
public class ControlsLegendOverlay extends Table {

    private static final float VIRTUAL_WIDTH = 1920f;
    private static final float VIRTUAL_HEIGHT = 1080f;

    private final SettingsManager settingsManager;
    private final Map<String, Label> keyLabels = new LinkedHashMap<>();

    public ControlsLegendOverlay(HudSkin skin) {
        this.settingsManager = SettingsManager.getInstance();

        setBackground(skin.getDoubleBorderPanel());
        pad(20f, 28f, 20f, 28f);
        top();

        Label.LabelStyle titleStyle = new Label.LabelStyle(skin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT);
        Label.LabelStyle actionStyle = new Label.LabelStyle(skin.getFontSmall(), Color.WHITE);
        Label.LabelStyle keyStyle = new Label.LabelStyle(skin.getFontSmall(), HudSkin.COL_GOLD_MUTED);

        Label title = new Label("CONTROLS", titleStyle);
        add(title).colspan(2).padBottom(16f).row();

        for (Map.Entry<String, String> entry : settingsManager.getKeyBindingDescriptions().entrySet()) {
            Label actionLabel = new Label(entry.getValue(), actionStyle);
            Label keyLabel = new Label("", keyStyle);
            keyLabels.put(entry.getKey(), keyLabel);

            add(actionLabel).left().padRight(40f).padTop(4f);
            add(keyLabel).right().padTop(4f).row();
        }

        Label hint = new Label("[TAB] Close", new Label.LabelStyle(skin.getFontSmall(), Color.LIGHT_GRAY));
        add(hint).colspan(2).padTop(16f).center();

        refreshKeyLabels();
        pack();
        setPosition((VIRTUAL_WIDTH - getWidth()) / 2f, (VIRTUAL_HEIGHT - getHeight()) / 2f);
        setVisible(false);
    }

    private void refreshKeyLabels() {
        for (Map.Entry<String, Label> entry : keyLabels.entrySet()) {
            entry.getValue().setText(Input.Keys.toString(settingsManager.getKey(entry.getKey())));
        }
    }

    public void toggle() {
        boolean showing = !isVisible();
        setVisible(showing);
        if (showing) {
            refreshKeyLabels();
            toFront();
        }
    }
}
