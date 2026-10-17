package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.bpm.minotaur.managers.FixedKeys;
import com.bpm.minotaur.managers.SettingsManager;
import com.bpm.minotaur.ui.UiGlyphs;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiTheme;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Toggleable (TAB) full key-legend overlay, listing every action tracked by
 * SettingsManager alongside its currently bound key, and the fixed keys from
 * {@link FixedKeys} beside them. Kept in sync with the
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
        Label.LabelStyle actionStyle = new Label.LabelStyle(skin.getFontSmall(), UiTheme.TEXT);
        Label.LabelStyle keyStyle = new Label.LabelStyle(skin.getFontSmall(), HudSkin.COL_GOLD_MUTED);

        Label title = new Label("CONTROLS", titleStyle);
        add(title).colspan(2).padBottom(16f).row();

        // Two columns: the rebindable actions (bound keys, re-read on show) beside the keys the
        // game reads as literal keycodes (#170), so the legend names every key that does something.
        Table rebindable = new Table();
        rebindable.top();
        for (Map.Entry<String, String> entry : settingsManager.getKeyBindingDescriptions().entrySet()) {
            Label actionLabel = UiLabels.of(entry.getValue(), actionStyle);
            Label keyLabel = UiLabels.of("", keyStyle);
            keyLabels.put(entry.getKey(), keyLabel);

            rebindable.add(actionLabel).left().padRight(40f).padTop(4f);
            rebindable.add(keyLabel).right().padTop(4f).row();
        }

        Table fixed = new Table();
        fixed.top();
        Label fixedHeader = UiLabels.of("FIXED KEYS", keyStyle);
        fixed.add(fixedHeader).colspan(2).left().padTop(4f).padBottom(4f).row();
        for (FixedKeys.Entry entry : FixedKeys.LEGEND) {
            fixed.add(UiLabels.of(entry.role, actionStyle)).left().padRight(40f).padTop(4f);
            fixed.add(UiLabels.of(entry.keys, keyStyle)).right().padTop(4f).row();
        }
        fixed.add(UiLabels.of(FixedKeys.BUMP_TO_ATTACK, actionStyle)).colspan(2).left().padTop(12f).row();

        add(rebindable).top().padRight(56f);
        add(fixed).top().row();

        Label hint = UiLabels.of("[TAB] Close", new Label.LabelStyle(skin.getFontSmall(), UiTheme.TEXT_DIM));
        add(hint).colspan(2).padTop(16f).center();

        refreshKeyLabels();
        pack();
        setPosition((VIRTUAL_WIDTH - getWidth()) / 2f, (VIRTUAL_HEIGHT - getHeight()) / 2f);
        setVisible(false);
    }

    private void refreshKeyLabels() {
        for (Map.Entry<String, Label> entry : keyLabels.entrySet()) {
            entry.getValue().setText(UiGlyphs.sanitize(Input.Keys.toString(settingsManager.getKey(entry.getKey()))));
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
