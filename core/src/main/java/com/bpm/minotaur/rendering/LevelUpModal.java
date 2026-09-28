package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.SoundManager;
import com.bpm.minotaur.ui.KeyHintLegend;
import com.bpm.minotaur.ui.UiContexts;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

import java.util.EnumMap;
import java.util.Map;

/**
 * The level-up overlay: spend the attribute points a new level granted.
 *
 * <p>Rebuilt for the UI pass. What changed and why:
 *
 * <ul>
 *   <li><b>LEVELUP-1</b> -- the panel was a 660x710 box centred on the whole 1080-unit stage, so
 *       its bottom edge sat inside the HUD dashboard and the attribute list ran behind it. It is
 *       now a full-stage actor: a scrim over everything, with the panel centred in the play area
 *       above the dashboard.</li>
 *   <li><b>LEVELUP-2</b> -- the milestone banner opened with a black-star character the font has
 *       no glyph for, and ran about half again as wide as the panel it sat in. It is a wrapped
 *       label in a sized cell now, and the reward is a chip rather than a sentence.</li>
 *   <li><b>LEVELUP-6</b> -- nothing said whether the world was paused. The scrim says it.</li>
 *   <li><b>LEVELUP-7</b> -- allocation was blind and final: press +, the point is gone, with no
 *       preview and no undo. Points are now <i>staged</i>. Nothing reaches the character sheet
 *       until Confirm, the row shows the value it will become, and Reset puts it all back.</li>
 *   <li><b>LEVELUP-9</b> -- the green [ + ] buttons were off-palette and had no counterpart. They
 *       are a proper minus/value/plus stepper.</li>
 * </ul>
 *
 * <p>The hotkeys stay what they were -- 1 to 6 allocate, Q and K leave for the spellbook and the
 * skill tree -- because players have them; 1 to 6 now stage rather than spend, and the world
 * underneath no longer hears them (SPEC 5.6, via {@link UiContexts}).
 */
public class LevelUpModal extends Table {

    /** This overlay's entry on the input-context stack. */
    private static final String CONTEXT = "LEVELUP";

    /** Height of the HUD dashboard the panel must stay clear of. */
    private static final float HUD_HEIGHT = 180f;

    private static final float PANEL_W = 720f;

    private final HudSkin hudSkin;
    private final Table panel;
    private final Label titleLabel;
    private final Label pointsLabel;
    private final Table milestoneRow;
    private final Label milestoneLabel;
    private final Table attributesTable;
    private final TextButton confirmBtn;
    private final TextButton resetBtn;
    private final TextButton laterBtn;
    private final TextButton openSkillTreeBtn;
    private final TextButton openSpellbookBtn;

    private final Map<ShelterAltar.StatType, Label> statValueLabels = new EnumMap<>(ShelterAltar.StatType.class);
    private final Map<ShelterAltar.StatType, TextButton> plusButtons = new EnumMap<>(ShelterAltar.StatType.class);
    private final Map<ShelterAltar.StatType, TextButton> minusButtons = new EnumMap<>(ShelterAltar.StatType.class);

    /** Points the player has put against each attribute but not yet committed. */
    private final Map<ShelterAltar.StatType, Integer> staged = new EnumMap<>(ShelterAltar.StatType.class);

    private Player activePlayer;
    private GameEventManager activeEventManager;
    private SoundManager activeSoundManager;
    private Runnable onOpenSkillTree;
    private Runnable onOpenSpellbook;

    /** The six attributes, in the order their hotkeys run. */
    private static final ShelterAltar.StatType[] STATS = {
            ShelterAltar.StatType.STRENGTH,
            ShelterAltar.StatType.DEXTERITY,
            ShelterAltar.StatType.CONSTITUTION,
            ShelterAltar.StatType.INTELLIGENCE,
            ShelterAltar.StatType.WISDOM,
            ShelterAltar.StatType.AGILITY
    };

    public LevelUpModal(HudSkin hudSkin) {
        this.hudSkin = hudSkin;

        // The scrim is the modal's own background, so there is no second actor to keep in sync
        // and nothing can slip between the two.
        setFillParent(true);
        setBackground(hudSkin.getScrim());
        top();

        panel = new Table();
        panel.setBackground(hudSkin.getDoubleBorderPanel());
        panel.pad(UiTheme.PAD_XL, UiTheme.PAD_XXL, UiTheme.PAD_XL, UiTheme.PAD_XXL);
        panel.top();

        titleLabel = new Label("LEVEL UP", UiStyles.display(hudSkin));
        titleLabel.setAlignment(Align.center);
        panel.add(titleLabel).growX().padBottom(UiTheme.PAD_SM).row();

        // A reward is a chip, not a sentence that has to fit on one line.
        milestoneRow = new Table();
        milestoneRow.setBackground(hudSkin.getCardBg());
        milestoneLabel = UiLabels.wrapping("", UiStyles.body(hudSkin, UiTheme.GOLD));
        milestoneLabel.setAlignment(Align.center);
        milestoneRow.add(milestoneLabel).width(PANEL_W - 4 * UiTheme.PAD_XXL).pad(UiTheme.PAD_SM);
        milestoneRow.setVisible(false);
        panel.add(milestoneRow).growX().padBottom(UiTheme.PAD_SM).row();

        pointsLabel = new Label("", UiStyles.body(hudSkin));
        pointsLabel.setAlignment(Align.center);
        panel.add(pointsLabel).growX().padBottom(UiTheme.PAD_MD).row();

        attributesTable = new Table();
        panel.add(attributesTable).growX().padBottom(UiTheme.PAD_MD).row();

        openSkillTreeBtn = new TextButton("SKILL TREE", UiStyles.secondary(hudSkin));
        openSkillTreeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                openSkillTree();
            }
        });

        openSpellbookBtn = new TextButton("SPELLBOOK", UiStyles.secondary(hudSkin));
        openSpellbookBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                openSpellbook();
            }
        });

        resetBtn = new TextButton("RESET", UiStyles.secondary(hudSkin));
        resetBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                resetStaged();
            }
        });

        laterBtn = new TextButton("DECIDE LATER", UiStyles.secondary(hudSkin));
        laterBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                close();
            }
        });

        confirmBtn = new TextButton("CONFIRM", UiStyles.primary(hudSkin));
        confirmBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                confirm();
            }
        });

        Table btnRow = new Table();
        btnRow.add(openSkillTreeBtn).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).padRight(UiTheme.PAD_SM);
        btnRow.add(openSpellbookBtn).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).padRight(UiTheme.PAD_XL);
        btnRow.add(resetBtn).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).padRight(UiTheme.PAD_SM);
        btnRow.add(laterBtn).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).padRight(UiTheme.PAD_SM);
        btnRow.add(confirmBtn).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H);
        panel.add(btnRow).center().padBottom(UiTheme.PAD_SM).row();

        panel.add(new KeyHintLegend(hudSkin)
                .hint("1-6", "Add")
                .hint("-", "Remove")
                .hint("ENTER", "Confirm")
                .escapeHint("Decide later")).center().row();

        // The play area is everything above the dashboard, so that is what the panel centres in.
        add(panel).width(PANEL_W).padTop(UiTheme.SAFE).padBottom(HUD_HEIGHT).expand().center();

        setVisible(false);
    }

    public void setOnOpenSkillTree(Runnable onOpenSkillTree) {
        this.onOpenSkillTree = onOpenSkillTree;
    }

    public void setOnOpenSpellbook(Runnable onOpenSpellbook) {
        this.onOpenSpellbook = onOpenSpellbook;
    }

    public void openSkillTree() {
        confirm();
        close();
        if (onOpenSkillTree != null) {
            onOpenSkillTree.run();
        }
    }

    public void openSpellbook() {
        confirm();
        if (activePlayer != null && activePlayer.getStats().getUnallocatedAttributePoints() > 0) {
            activePlayer.setPendingLevelUpModal(true);
        }
        close();
        if (onOpenSpellbook != null) {
            onOpenSpellbook.run();
        }
    }

    public void show(Player player, GameEventManager eventManager, SoundManager soundManager) {
        this.activePlayer = player;
        this.activeEventManager = eventManager;
        this.activeSoundManager = soundManager;

        if (player == null) {
            close();
            return;
        }

        staged.clear();

        int lvl = activePlayer.getLevel();
        int slotUnlocked = 0;
        if (lvl == 2) slotUnlocked = 2;
        else if (lvl == 5) slotUnlocked = 3;
        else if (lvl == 8) slotUnlocked = 4;
        else if (lvl == 11) slotUnlocked = 5;

        if (slotUnlocked > 0) {
            // LEVELUP-2: this line used to start with a star the font cannot draw, so it opened
            // with a missing-glyph box, and it was written as one long sentence in a panel too
            // narrow for it.
            milestoneLabel.setText("Spell slot " + slotUnlocked + " unlocked");
            milestoneRow.setVisible(true);
        } else {
            milestoneLabel.setText("");
            milestoneRow.setVisible(false);
        }

        rebuildAttributeRows();
        refreshValues();

        if (getStage() != null) {
            getStage().setKeyboardFocus(this);
        }

        UiContexts.push(CONTEXT, UiContexts.Kind.MODAL);
        setVisible(true);
        toFront();
    }

    private void rebuildAttributeRows() {
        attributesTable.clear();
        statValueLabels.clear();
        plusButtons.clear();
        minusButtons.clear();

        if (activePlayer == null) {
            return;
        }

        for (int i = 0; i < STATS.length; i++) {
            final ShelterAltar.StatType stat = STATS[i];

            Table row = new Table();
            row.setBackground(hudSkin.getCardBg());
            row.pad(UiTheme.PAD_SM, UiTheme.PAD_MD, UiTheme.PAD_SM, UiTheme.PAD_MD);

            Table keycap = new Table();
            keycap.setBackground(hudSkin.getKeycap());
            keycap.add(new Label(String.valueOf(i + 1), UiStyles.caption(hudSkin, UiTheme.TEXT)));
            row.add(keycap).size(UiTheme.ICON_MD).padRight(UiTheme.PAD_MD);

            Label valLabel = UiLabels.ellipsized("", UiStyles.body(hudSkin, UiTheme.GOLD));
            statValueLabels.put(stat, valLabel);
            row.add(valLabel).left().growX();

            // LEVELUP-9: a lone green [ + ] with no way back. A stepper has both halves.
            TextButton minusBtn = new TextButton("-", UiStyles.secondary(hudSkin));
            minusBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    unstage(stat);
                }
            });
            minusButtons.put(stat, minusBtn);
            row.add(minusBtn).size(UiTheme.ICON_LG, UiTheme.ICON_MD).padRight(UiTheme.PAD_XS);

            TextButton plusBtn = new TextButton("+", UiStyles.secondary(hudSkin));
            plusBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    stage(stat);
                }
            });
            plusButtons.put(stat, plusBtn);
            row.add(plusBtn).size(UiTheme.ICON_LG, UiTheme.ICON_MD).row();

            Label desc = UiLabels.wrapping(stat.getDescription(), UiStyles.caption(hudSkin));
            row.add(desc).colspan(4).growX().left().padTop(UiTheme.PAD_XS);

            attributesTable.add(row).growX().padBottom(UiTheme.PAD_XS).row();
        }
    }

    /** Points staged across all six attributes. */
    private int stagedTotal() {
        int total = 0;
        for (Integer n : staged.values()) {
            total += n;
        }
        return total;
    }

    private int stagedFor(ShelterAltar.StatType stat) {
        Integer n = staged.get(stat);
        return n == null ? 0 : n;
    }

    private int remaining() {
        if (activePlayer == null) {
            return 0;
        }
        return activePlayer.getStats().getUnallocatedAttributePoints() - stagedTotal();
    }

    private void stage(ShelterAltar.StatType stat) {
        if (activePlayer == null || stat == null || remaining() <= 0) {
            return;
        }
        if (activePlayer.getStats().getEffectiveStat(stat) + stagedFor(stat) >= 20) {
            return;
        }
        staged.put(stat, stagedFor(stat) + 1);
        if (activeSoundManager != null) {
            activeSoundManager.playPickupItemSound();
        }
        refreshValues();
    }

    private void unstage(ShelterAltar.StatType stat) {
        if (stat == null || stagedFor(stat) <= 0) {
            return;
        }
        staged.put(stat, stagedFor(stat) - 1);
        refreshValues();
    }

    private void resetStaged() {
        staged.clear();
        refreshValues();
    }

    /**
     * Commits the staged points to the character sheet.
     *
     * <p>Safe to call with nothing staged, which is what makes it safe to call on the way out
     * through the skill tree or the spellbook.
     */
    public void confirm() {
        if (activePlayer == null || staged.isEmpty()) {
            return;
        }
        int applied = 0;
        for (Map.Entry<ShelterAltar.StatType, Integer> entry : staged.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                if (activePlayer.allocateAttribute(entry.getKey())) {
                    applied++;
                }
            }
        }
        staged.clear();
        if (applied > 0 && activeEventManager != null) {
            activeEventManager.addEvent(new GameEvent(
                    "Spent " + applied + (applied == 1 ? " attribute point." : " attribute points."), 2f));
        }
        refreshValues();
    }

    private void refreshValues() {
        if (activePlayer == null) {
            return;
        }

        titleLabel.setText("LEVEL " + activePlayer.getLevel());

        int unspent = remaining();
        int skills = activePlayer.getStats().getUnallocatedSkillPoints();
        pointsLabel.setText(unspent + " attribute " + (unspent == 1 ? "point" : "points")
                + " to spend   |   " + skills + " skill " + (skills == 1 ? "point" : "points"));
        pointsLabel.setColor(unspent > 0 || skills > 0 ? UiTheme.GOLD : UiTheme.TEXT_DIM);

        for (ShelterAltar.StatType stat : STATS) {
            Label label = statValueLabels.get(stat);
            if (label == null) {
                continue;
            }
            int current = activePlayer.getStats().getEffectiveStat(stat);
            int pending = stagedFor(stat);
            int preview = current + pending;
            int mod = (preview - 10) / 2;
            String modStr = (mod >= 0 ? "+" : "") + mod;

            // LEVELUP-7: the row shows what the attribute will be if the player confirms, and
            // by how much it moved, rather than asking them to hold the arithmetic.
            String text = stat.getDisplayName().toUpperCase() + "   " + preview + " (" + modStr + ")";
            if (pending > 0) {
                text += "   +" + pending + " staged";
            }
            label.setText(text);
            label.setColor(pending > 0 ? UiTheme.SUCCESS : UiTheme.GOLD);

            TextButton plus = plusButtons.get(stat);
            if (plus != null) {
                UiStyles.setEnabled(plus, unspent > 0 && preview < 20);
            }
            TextButton minus = minusButtons.get(stat);
            if (minus != null) {
                UiStyles.setEnabled(minus, pending > 0);
            }
        }

        UiStyles.setEnabled(confirmBtn, stagedTotal() > 0);
        UiStyles.setEnabled(resetBtn, stagedTotal() > 0);
    }

    public void close() {
        setVisible(false);
        UiContexts.pop(CONTEXT);
        if (getStage() != null) {
            getStage().setKeyboardFocus(null);
        }
    }

    public boolean handleInput(int keycode) {
        if (!isVisible()) {
            return false;
        }

        switch (keycode) {
            case Input.Keys.NUM_1:
            case Input.Keys.NUMPAD_1:
                stage(ShelterAltar.StatType.STRENGTH);
                return true;
            case Input.Keys.NUM_2:
            case Input.Keys.NUMPAD_2:
                stage(ShelterAltar.StatType.DEXTERITY);
                return true;
            case Input.Keys.NUM_3:
            case Input.Keys.NUMPAD_3:
                stage(ShelterAltar.StatType.CONSTITUTION);
                return true;
            case Input.Keys.NUM_4:
            case Input.Keys.NUMPAD_4:
                stage(ShelterAltar.StatType.INTELLIGENCE);
                return true;
            case Input.Keys.NUM_5:
            case Input.Keys.NUMPAD_5:
                stage(ShelterAltar.StatType.WISDOM);
                return true;
            case Input.Keys.NUM_6:
            case Input.Keys.NUMPAD_6:
                stage(ShelterAltar.StatType.AGILITY);
                return true;
            case Input.Keys.MINUS:
            case Input.Keys.BACKSPACE:
                unstageLast();
                return true;
            case Input.Keys.R:
                resetStaged();
                return true;
            case Input.Keys.K:
                openSkillTree();
                return true;
            case Input.Keys.Q:
                openSpellbook();
                return true;
            case Input.Keys.ENTER:
            case Input.Keys.SPACE:
                confirm();
                close();
                return true;
            case Input.Keys.ESCAPE:
                close();
                return true;
            default:
                // Everything else is swallowed: the world underneath must not act on a key the
                // player aimed at this panel.
                return true;
        }
    }

    /** Takes a point back off whichever attribute still has one staged, last in the row order. */
    private void unstageLast() {
        for (int i = STATS.length - 1; i >= 0; i--) {
            if (stagedFor(STATS[i]) > 0) {
                unstage(STATS[i]);
                return;
            }
        }
    }
}
