package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.spells.SpellDataManager;
import com.bpm.minotaur.gamedata.spells.SpellTemplate;

import java.util.List;

/**
 * Shows the player's known spells and allows assigning them to the 5 Quick-Cast slots.
 */
public class SpellbookPanel extends Table implements InventoryEventBus.Listener {

    private final Player player;
    private final InventorySkin skin;
    private final Table preparedTable;
    private final Table list;
    private InventoryEventBus bus;

    public SpellbookPanel(Player player, InventorySkin skin) {
        this.player = player;
        this.skin   = skin;

        top().left();
        padTop(12);

        preparedTable = new Table();
        preparedTable.left();
        add(preparedTable).expandX().fillX().left().padBottom(6).row();

        list = new Table();
        list.top().left();

        ScrollPane.ScrollPaneStyle spStyle = new ScrollPane.ScrollPaneStyle();
        ScrollPane scrollPane = new ScrollPane(list, spStyle);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);

        add(scrollPane).size(340, 160).left().row();

        refresh();
    }

    public void setBus(InventoryEventBus bus) {
        this.bus = bus;
    }

    public void refresh() {
        preparedTable.clear();
        list.clear();

        // ── 1. Prepared Slots (Quick-Cast 1-5) ─────────────
        Label prepHeader = new Label("PREPARED [Shift+1..5 / Z,X,V,B,N]:",
                new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_HEADER));
        preparedTable.add(prepHeader).colspan(5).left().padBottom(2).row();

        String[] prepared = player.getPreparedSpells();
        Table prepRow = new Table();
        prepRow.left();
        for (int i = 0; i < 5; i++) {
            final int slotIdx = i;
            String spellId = (prepared != null && i < prepared.length) ? prepared[i] : null;
            String display = (i + 1) + ": ";
            Color textColor = InventorySkin.COL_TEXT_MUTED;

            if (spellId != null && !spellId.isEmpty()) {
                SpellTemplate tmpl = SpellDataManager.getSpell(spellId);
                String name = tmpl != null ? tmpl.name : spellId;
                if (name.length() > 8) name = name.substring(0, 7) + ".";
                display += name;
                textColor = InventorySkin.COL_PAGE_LIGHT;
            } else {
                display += "-";
            }

            Label slotLabel = new Label("[" + display + "]",
                    new Label.LabelStyle(skin.getFontSmall(), textColor));
            slotLabel.setTouchable(Touchable.enabled);
            slotLabel.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    // Click to clear slot
                    player.prepareSpell(slotIdx, null);
                    refresh();
                    if (bus != null) bus.fireSpellbookChanged();
                }
            });
            prepRow.add(slotLabel).padRight(4);
        }
        preparedTable.add(prepRow).left().padBottom(4).row();

        // ── 2. Known Spells ───────────────────────────────
        Label knownHeader = new Label("KNOWN SPELLS (Click 1-5 to assign):",
                new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_HEADER));
        list.add(knownHeader).colspan(6).left().padBottom(4).row();

        List<String> knownIds = player.getKnownSpellIds();
        if (knownIds.isEmpty()) {
            list.add(new Label("No spells inscribed yet.",
                    new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_MUTED))).left().row();
        } else {
            for (String spellId : knownIds) {
                SpellTemplate tmpl = SpellDataManager.getSpell(spellId);
                String name = (tmpl != null) ? tmpl.name : spellId;
                int mp = (tmpl != null) ? tmpl.mpCost : 0;
                String text = name + " (" + mp + "MP)";

                Table rowTable = new Table();
                rowTable.left();

                Label nameLabel = new Label(text,
                        new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_BODY));
                rowTable.add(nameLabel).width(180).left();

                // Slot buttons [1] [2] [3] [4] [5]
                for (int s = 0; s < 5; s++) {
                    final int targetSlot = s;
                    final String sId = spellId;
                    boolean isAssigned = (prepared != null && s < prepared.length
                            && sId.equalsIgnoreCase(prepared[s]));

                    String btnText = isAssigned ? "[" + (s + 1) + "]" : " " + (s + 1) + " ";
                    Color btnColor = isAssigned ? InventorySkin.COL_GEM_BLUE : InventorySkin.COL_TEXT_MUTED;

                    Label btn = new Label(btnText,
                            new Label.LabelStyle(skin.getFontSmall(), btnColor));
                    btn.setTouchable(Touchable.enabled);
                    btn.addListener(new ClickListener() {
                        @Override
                        public void clicked(InputEvent event, float x, float y) {
                            if (isAssigned) {
                                player.prepareSpell(targetSlot, null);
                            } else {
                                player.prepareSpell(targetSlot, sId);
                            }
                            refresh();
                            if (bus != null) bus.fireSpellbookChanged();
                        }
                    });
                    rowTable.add(btn).padRight(2);
                }

                list.add(rowTable).left().padBottom(2).row();
            }
        }
    }

    @Override
    public void onSpellbookChanged() {
        refresh();
    }
}
