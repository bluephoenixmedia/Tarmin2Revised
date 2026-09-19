package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.HostileSight;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.spells.SpellDataManager;
import com.bpm.minotaur.gamedata.spells.SpellTemplate;
import com.bpm.minotaur.gamedata.spells.Tome;
import com.bpm.minotaur.gamedata.spells.TomeChoice;
import com.bpm.minotaur.managers.SettingsManager;
import com.bpm.minotaur.rendering.HudSkin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * The Spellbook: every Known Spell with its full details, and the five Spell Slots
 * they are cast from. Assign a spell by clicking it and then a slot, or by dragging
 * it onto a slot; right-click a slot to clear it. Slots cannot be changed while a
 * hostile is in view. When a studied Tome is waiting on its Tome Choice, the
 * details side shows the offered spells instead, and the book cannot be closed
 * until one is learned.
 */
public class SpellbookScreen extends BaseScreen {

    /** Quick-cast keys of the five Spell Slots, in slot order. */
    public static final String[] SLOT_KEYS = { "Z", "X", "V", "B", "N" };

    private final GameScreen parentScreen;
    private final Player player;
    private final Maze maze;
    private final HudSkin hudSkin;

    private Stage stage;
    private Table slotRow;
    private Table filterBar;
    private Table spellList;
    private Table detailPanel;
    private Label mpLabel;
    private Label statusLabel;
    private DragAndDrop dragAndDrop;

    private boolean disposed;
    private String selectedSpellId;
    private String schoolFilter; // null = every school

    public SpellbookScreen(Tarmin2 game, GameScreen parentScreen, Player player, Maze maze) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.maze = maze;
        this.hudSkin = new HudSkin();
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        dragAndDrop = new DragAndDrop();

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        buildUI();
    }

    private void buildUI() {
        stage.clear();

        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(40, 100, 40, 100);

        // --- HEADER ---
        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(16, 24, 16, 24);
        Label title = new Label("SPELLBOOK", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        title.setFontScale(1.3f);
        header.add(title).center().row();
        mpLabel = new Label("", new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_MP_BLUE));
        header.add(mpLabel).center().padTop(4).row();
        root.add(header).fillX().padBottom(16).row();

        // --- SPELL SLOTS ---
        slotRow = new Table();
        root.add(slotRow).fillX().padBottom(16).row();

        // --- BODY: known spells (left), details (right) ---
        Table body = new Table();

        Table listColumn = new Table();
        listColumn.setBackground(hudSkin.getPanelBg());
        listColumn.pad(16);
        filterBar = new Table();
        listColumn.add(filterBar).left().padBottom(10).row();
        spellList = new Table();
        spellList.top().left();
        ScrollPane scroll = new ScrollPane(spellList);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        listColumn.add(scroll).expand().fill();
        body.add(listColumn).width(760).expandY().fillY().padRight(20);

        detailPanel = new Table();
        detailPanel.setBackground(hudSkin.getPanelBg());
        detailPanel.pad(24);
        detailPanel.top().left();
        body.add(detailPanel).expand().fill();

        root.add(body).expand().fill().padBottom(16).row();

        // --- FOOTER ---
        Table footer = new Table();
        footer.setBackground(hudSkin.getPanelBg());
        footer.pad(12, 20, 12, 20);
        Label keyHints = new Label("[ESC/" + keyName() + "] CLOSE   Click a spell, then a slot (or drag it) to assign   Right-click a slot to clear",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        footer.add(keyHints).left().expandX();
        statusLabel = new Label("", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_FOOD_GREEN));
        footer.add(statusLabel).right();
        root.add(footer).fillX();

        stage.addActor(root);

        if (hostileInView()) {
            setStatus("A hostile is in view -- you cannot change spell slots now.", false);
        }
        refresh();
    }

    private void refresh() {
        dragAndDrop.clear();
        mpLabel.setText("MP " + player.getStats().getCurrentMP() + " / " + player.getStats().getMaxMP()
                + "     Known spells: " + player.getKnownSpellIds().size()
                + "     Slots unlocked: " + player.getUnlockedSpellSlots() + " / 5");
        buildSlotRow();
        buildFilterBar();
        buildSpellList();
        buildDetails();
    }

    // =========================================================================
    // SPELL SLOTS
    // =========================================================================

    private void buildSlotRow() {
        slotRow.clear();
        for (int i = 0; i < 5; i++) {
            slotRow.add(buildSlotCell(i)).width(330).height(96).padRight(i < 4 ? 12 : 0);
        }
    }

    private Table buildSlotCell(int slot) {
        boolean unlocked = slot < player.getUnlockedSpellSlots();
        String spellId = unlocked ? player.getPreparedSpell(slot) : null;
        SpellTemplate spell = SpellDataManager.getSpell(spellId);

        Table cell = new Table();
        cell.setBackground(unlocked ? hudSkin.getSlotActive() : hudSkin.getSlotRecessed());
        cell.pad(8, 12, 8, 12);

        Label keyLabel = new Label("SLOT " + (slot + 1) + "  [" + SLOT_KEYS[slot] + "]",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        cell.add(keyLabel).left().row();

        String name;
        String sub;
        Color nameColor;
        if (!unlocked) {
            name = "LOCKED";
            sub = "Study the " + Tome.unlockingSlot(slot + 1).getDisplayName();
            nameColor = Color.DARK_GRAY;
        } else if (spell != null) {
            name = spell.getName();
            sub = costText(spell);
            nameColor = Color.CYAN;
        } else {
            name = "-- empty --";
            sub = "";
            nameColor = HudSkin.COL_GOLD_MUTED;
        }
        Label nameLabel = new Label(name, new Label.LabelStyle(hudSkin.getFontMain(), nameColor));
        nameLabel.setEllipsis(true);
        cell.add(nameLabel).width(300).left().row();
        cell.add(new Label(sub, new Label.LabelStyle(hudSkin.getFontSmall(), Color.LIGHT_GRAY))).left();

        if (!unlocked) {
            return cell;
        }

        cell.addListener(new ClickListener(Input.Buttons.LEFT) {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (selectedSpellId != null) {
                    assign(slot, selectedSpellId);
                } else if (spellId != null) {
                    selectedSpellId = spellId;
                    refresh();
                }
            }
        });
        cell.addListener(new ClickListener(Input.Buttons.RIGHT) {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assign(slot, null);
            }
        });
        dragAndDrop.addTarget(new DragAndDrop.Target(cell) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                return true;
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                assign(slot, (String) payload.getObject());
            }
        });
        return cell;
    }

    private void assign(int slot, String spellId) {
        switch (player.assignSpellSlot(slot, spellId, maze)) {
            case OK:
                SpellTemplate spell = SpellDataManager.getSpell(spellId);
                setStatus(spellId == null ? "Slot " + (slot + 1) + " cleared."
                        : (spell != null ? spell.getName() : spellId) + " prepared in slot " + (slot + 1) + ".", true);
                break;
            case HOSTILE_IN_VIEW:
                setStatus("A hostile is in view -- you cannot change spell slots now.", false);
                break;
            case SLOT_LOCKED:
                setStatus("That slot is locked. Study a Tome to unlock it.", false);
                break;
            case UNKNOWN_SPELL:
                setStatus("You do not know that spell.", false);
                break;
        }
        refresh();
    }

    // =========================================================================
    // KNOWN SPELLS
    // =========================================================================

    private List<SpellTemplate> knownSpells() {
        List<SpellTemplate> known = new ArrayList<>();
        for (String id : player.getKnownSpellIds()) {
            SpellTemplate spell = SpellDataManager.getSpell(id);
            if (spell != null) {
                known.add(spell);
            }
        }
        known.sort((a, b) -> a.getLevel() != b.getLevel()
                ? Integer.compare(a.getLevel(), b.getLevel())
                : a.getName().compareToIgnoreCase(b.getName()));
        return known;
    }

    private void buildFilterBar() {
        filterBar.clear();
        TreeSet<String> schools = new TreeSet<>();
        for (SpellTemplate spell : knownSpells()) {
            if (spell.getSchool() != null) {
                schools.add(spell.getSchool().toLowerCase(Locale.ROOT));
            }
        }
        filterBar.add(filterChip("ALL", null)).padRight(8);
        for (String school : schools) {
            filterBar.add(filterChip(school.toUpperCase(Locale.ROOT), school)).padRight(8);
        }
    }

    private Label filterChip(String text, String school) {
        boolean active = school == null ? schoolFilter == null : school.equals(schoolFilter);
        Label chip = new Label(active ? "[" + text + "]" : text,
                new Label.LabelStyle(hudSkin.getFontSmall(), active ? HudSkin.COL_GOLD_BRIGHT : HudSkin.COL_GOLD_MUTED));
        chip.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                schoolFilter = school;
                refresh();
            }
        });
        return chip;
    }

    private void buildSpellList() {
        spellList.clear();
        int currentLevel = -1;
        boolean any = false;
        for (SpellTemplate spell : knownSpells()) {
            if (schoolFilter != null && (spell.getSchool() == null
                    || !schoolFilter.equals(spell.getSchool().toLowerCase(Locale.ROOT)))) {
                continue;
            }
            any = true;
            if (spell.getLevel() != currentLevel) {
                currentLevel = spell.getLevel();
                Label levelHeader = new Label(currentLevel == 0 ? "CANTRIPS" : "LEVEL " + currentLevel,
                        new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE));
                spellList.add(levelHeader).left().padTop(spellList.hasChildren() ? 12 : 0).padBottom(4).row();
            }
            spellList.add(buildSpellRow(spell)).width(700).left().padBottom(2).row();
        }
        if (!any) {
            spellList.add(new Label("No spells inscribed yet.",
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED))).left();
        }
    }

    private Table buildSpellRow(SpellTemplate spell) {
        String id = spell.getId().toUpperCase(Locale.ROOT);
        boolean selected = id.equals(selectedSpellId);

        Table row = new Table();
        row.pad(4, 10, 4, 10);
        if (selected) {
            row.setBackground(hudSkin.getSlotActive());
        }
        Label name = new Label(spell.getName(), new Label.LabelStyle(hudSkin.getFontMain(),
                selected ? HudSkin.COL_GOLD_BRIGHT : Color.WHITE));
        row.add(name).expandX().left();
        int slot = slotOf(id);
        row.add(new Label(slot >= 0 ? "[" + SLOT_KEYS[slot] + "]" : "",
                new Label.LabelStyle(hudSkin.getFontSmall(), Color.CYAN))).width(50).right();
        row.add(new Label(costText(spell), new Label.LabelStyle(hudSkin.getFontSmall(), Color.LIGHT_GRAY)))
                .width(90).right();

        row.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedSpellId = selected ? null : id;
                refresh();
            }
        });
        dragAndDrop.addSource(new DragAndDrop.Source(row) {
            @Override
            public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
                DragAndDrop.Payload payload = new DragAndDrop.Payload();
                payload.setObject(id);
                Label ghost = new Label(spell.getName(), new Label.LabelStyle(hudSkin.getFontMain(), Color.CYAN));
                payload.setDragActor(ghost);
                return payload;
            }
        });
        return row;
    }

    private int slotOf(String spellId) {
        for (int i = 0; i < player.getUnlockedSpellSlots(); i++) {
            if (spellId.equals(player.getPreparedSpell(i))) {
                return i;
            }
        }
        return -1;
    }

    // =========================================================================
    // DETAILS
    // =========================================================================

    private void buildDetails() {
        detailPanel.clear();
        if (player.getPendingTomeChoice() != null) {
            buildTomeChoice(player.getPendingTomeChoice());
            return;
        }
        SpellTemplate spell = SpellDataManager.getSpell(selectedSpellId);
        if (spell == null) {
            detailPanel.add(new Label("Select a spell to read it.",
                    new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_MUTED))).left();
            return;
        }
        fillSpellDetails(detailPanel, spell, hudSkin, 820f);
    }

    // =========================================================================
    // TOME CHOICE
    // =========================================================================

    private void buildTomeChoice(TomeChoice choice) {
        detailPanel.add(new Label("THE " + choice.getTome().getDisplayName().toUpperCase(Locale.ROOT)
                        + " -- CHOOSE ONE SPELL TO LEARN",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT))).left().row();

        Table cards = new Table();
        cards.top().left();
        for (String id : choice.getOptions()) {
            SpellTemplate spell = SpellDataManager.getSpell(id);
            if (spell == null) {
                continue;
            }
            Table card = new Table();
            card.setBackground(hudSkin.getSlotRecessed());
            card.pad(14);
            card.top().left();
            fillSpellDetails(card, spell, hudSkin, 760f);
            TextButton learn = createActionButton("LEARN " + spell.getName().toUpperCase(Locale.ROOT));
            learn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    chooseTomeSpell(id);
                }
            });
            card.add(learn).colspan(2).left().width(420).height(44).padTop(10).row();
            cards.add(card).width(820).left().padBottom(12).row();
        }
        ScrollPane scroll = new ScrollPane(cards);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        detailPanel.add(scroll).expand().fill().padTop(12).row();

        if (choice.getRerollsLeft() > 0) {
            TextButton reroll = createActionButton("REROLL (" + choice.getRerollsLeft() + " LEFT)");
            reroll.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    player.rerollTomeChoice();
                    setStatus("The pages shift, revealing other spells.", true);
                    refresh();
                }
            });
            detailPanel.add(reroll).left().width(300).height(44).padTop(8).row();
        }
    }

    private void chooseTomeSpell(String spellId) {
        if (player.chooseTomeSpell(spellId, parentScreen.getEventManager())) {
            SpellTemplate spell = SpellDataManager.getSpell(spellId);
            selectedSpellId = spellId;
            setStatus("Learned " + (spell != null ? spell.getName() : spellId) + ".", true);
        }
        refresh();
    }

    private TextButton createActionButton(String text) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = hudSkin.getFontHeader();
        style.up = hudSkin.getPrimaryButtonUp();
        style.down = hudSkin.getPrimaryButtonDown();
        style.over = hudSkin.getPrimaryButtonDown();
        style.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        TextButton button = new TextButton(text, style);
        button.getLabel().setFontScale(0.72f);
        return button;
    }

    /** Writes a spell's full details into {@code panel}: the details side and each Tome Choice card. */
    private static void fillSpellDetails(Table panel, SpellTemplate spell, HudSkin hudSkin, float width) {
        Label name = new Label(spell.getName(), new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        panel.add(name).left().colspan(2).row();
        String levelText = spell.getLevel() == 0 ? "Cantrip" : "Level " + spell.getLevel();
        panel.add(new Label(levelText + "  -  " + capitalize(spell.getSchool()),
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE))).left().colspan(2).padBottom(12).row();

        detailRow(panel, hudSkin, "Cost", costText(spell));
        detailRow(panel, hudSkin, "Range", spell.getRange() + (spell.getRange() == 1 ? " tile" : " tiles"));
        detailRow(panel, hudSkin, "Target", capitalize(spell.getTargetType()));
        if (spell.getDamageDice() != null && !spell.getDamageDice().isEmpty()) {
            detailRow(panel, hudSkin, "Damage", spell.getDamageDice() + " " + capitalize(spell.getDamageType()));
        }
        if (spell.getStatusEffect() != null && !spell.getStatusEffect().isEmpty()) {
            detailRow(panel, hudSkin, "Effect", capitalize(spell.getStatusEffect().replace('_', ' ')));
        }
        if (spell.getDuration() != null && !spell.getDuration().isEmpty()) {
            detailRow(panel, hudSkin, "Duration", spell.getDuration());
        }

        String description = spell.getDescription() != null ? spell.getDescription().replace("**", "") : "";
        Label desc = new Label(description, new Label.LabelStyle(hudSkin.getFontSmall(), Color.LIGHT_GRAY));
        desc.setWrap(true);
        panel.add(desc).width(width).left().colspan(2).padTop(14).row();
    }

    private static void detailRow(Table panel, HudSkin hudSkin, String key, String value) {
        panel.add(new Label(key, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED))).width(140).left();
        panel.add(new Label(value, new Label.LabelStyle(hudSkin.getFontSmall(), Color.WHITE))).left().expandX().row();
    }

    private static String costText(SpellTemplate spell) {
        return spell.getMpCost() == 0 ? "Cantrip" : spell.getMpCost() + " MP";
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return "-";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    // =========================================================================
    // PLUMBING
    // =========================================================================

    private boolean hostileInView() {
        return HostileSight.anyInView(maze, player.getPosition());
    }

    private void setStatus(String text, boolean good) {
        statusLabel.setText(text);
        statusLabel.setColor(good ? HudSkin.COL_FOOD_GREEN : HudSkin.COL_HP_CRITICAL);
    }

    private static String keyName() {
        return Input.Keys.toString(SettingsManager.getInstance().getKey("SPELLBOOK"));
    }

    private void close() {
        game.setScreen(parentScreen);
        dispose();
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == SettingsManager.getInstance().getKey("SPELLBOOK")) {
            if (player.getPendingTomeChoice() != null) {
                setStatus("Choose a spell to learn first.", false);
            } else {
                close();
            }
            return true;
        }
        return false;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(10f / 255f, 8f / 255f, 6f / 255f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (stage != null) {
            stage.dispose();
        }
        hudSkin.dispose();
    }
}
