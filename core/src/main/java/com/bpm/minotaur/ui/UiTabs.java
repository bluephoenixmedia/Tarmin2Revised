package com.bpm.minotaur.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.bpm.minotaur.rendering.HudSkin;

import java.util.ArrayList;
import java.util.List;

/**
 * The one tab bar (RC5). Used by the altar, the chronicle, the stash, the hearth and the
 * workshop.
 *
 * <p>Each of those screens had built its own, and each had the same bug from a different
 * direction: a label wider than a fixed-width cell, overflowing into the next tab. The altar
 * rendered {@code SHELTER EXPANSI|2.}, the workshop rendered {@code ]][} and {@code ]D[}, the
 * hearth rendered {@code MEAL [2}. The number was baked into the label text, so the overflow ate
 * the tab name and left the next tab's number sitting in the middle of it.
 *
 * <p>Two things fix it and both are structural. The keycap number is its own actor in its own
 * cell, so it can never be confused with the label. And a tab sizes to its <i>preferred</i>
 * width, never a hand-picked one, so the text always fits. The bar as a whole is what has a
 * width, and it is the screen's job to give it one.
 *
 * <p>Selection is single-choice: clicking a tab, or pressing its number, selects it and runs the
 * screen's callback. Screens keep their own tab enum and their own switch; this replaces the
 * presentation only.
 */
public class UiTabs extends Table {

    /** Told which tab the player chose, by index. */
    public interface Listener {
        void onTabSelected(int index);
    }

    private final HudSkin skin;
    private final List<Button> tabs = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<Label> keycaps = new ArrayList<>();
    private Listener listener;
    private int selected = 0;

    public UiTabs(HudSkin skin) {
        this.skin = skin;
        left();
    }

    public UiTabs onSelect(Listener listener) {
        this.listener = listener;
        return this;
    }

    /**
     * Adds a tab whose hotkey is {@code number} (1-based, matching the keycap).
     *
     * <p>The text is the tab's name and nothing else -- no number, no brackets. Both of those
     * are the widget's job now.
     */
    public UiTabs addTab(int number, String text) {
        final int index = tabs.size();

        Table content = new Table();
        Label keycap = new Label(String.valueOf(number), UiStyles.caption(skin, UiTheme.TEXT_DIM));
        keycap.setAlignment(com.badlogic.gdx.utils.Align.center);
        Table cap = new Table();
        cap.setBackground(skin.getKeycap());
        cap.add(keycap);
        content.add(cap).size(UiTheme.ICON_MD, UiTheme.ICON_MD).padRight(UiTheme.PAD_SM);

        Label label = UiLabels.of(text.toUpperCase(java.util.Locale.ROOT), UiStyles.label(skin));
        content.add(label).left();

        Button button = new Button(new Button.ButtonStyle());
        button.add(content).pad(UiTheme.PAD_SM, UiTheme.PAD_MD, UiTheme.PAD_SM, UiTheme.PAD_MD);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                select(index);
            }
        });

        tabs.add(button);
        labels.add(label);
        keycaps.add(keycap);

        // Preferred width, never a fixed one: a tab is as wide as its name needs.
        add(button).height(UiTheme.BUTTON_H).padRight(UiTheme.PAD_SM);
        applyStyles();
        return this;
    }

    /**
     * Handles a number key, if it belongs to one of these tabs.
     *
     * @return true when the key selected a tab, so the caller can stop routing it
     */
    public boolean handleKey(int keycode) {
        int index = -1;
        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
            index = keycode - Input.Keys.NUM_1;
        } else if (keycode >= Input.Keys.NUMPAD_1 && keycode <= Input.Keys.NUMPAD_9) {
            index = keycode - Input.Keys.NUMPAD_1;
        }
        if (index < 0 || index >= tabs.size()) {
            return false;
        }
        select(index);
        return true;
    }

    /** Moves the selection by {@code delta}, wrapping. For left/right and shoulder buttons. */
    public void cycle(int delta) {
        if (tabs.isEmpty()) {
            return;
        }
        int next = (selected + delta) % tabs.size();
        if (next < 0) {
            next += tabs.size();
        }
        select(next);
    }

    /** Selects a tab and notifies the screen. Selecting the current tab still notifies, so a
     * screen can use it to refresh. */
    public void select(int index) {
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        selected = index;
        applyStyles();
        if (listener != null) {
            listener.onTabSelected(index);
        }
    }

    /** Selects a tab without notifying, for restoring state while building the screen. */
    public void setSelectedSilently(int index) {
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        selected = index;
        applyStyles();
    }

    public int getSelectedIndex() {
        return selected;
    }

    public int getTabCount() {
        return tabs.size();
    }

    private void applyStyles() {
        for (int i = 0; i < tabs.size(); i++) {
            boolean active = i == selected;
            Button button = tabs.get(i);
            Button.ButtonStyle style = button.getStyle();
            style.up = active ? skin.getTabActive() : skin.getTabInactive();
            style.over = active ? skin.getTabActive() : skin.getCardBg();
            style.down = skin.getTabActive();
            style.checked = skin.getTabActive();
            labels.get(i).getStyle().fontColor = active ? UiTheme.GOLD : UiTheme.TEXT_DIM;
            keycaps.get(i).getStyle().fontColor = active ? UiTheme.GOLD : UiTheme.TEXT_OFF;
        }
    }
}
