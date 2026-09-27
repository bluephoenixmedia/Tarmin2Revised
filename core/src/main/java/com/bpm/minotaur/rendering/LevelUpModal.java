package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.SoundManager;

import java.util.HashMap;
import java.util.Map;

/**
 * In-game modal overlay presented upon leveling up (outside active combat),
 * allowing immediate tactical allocation of earned attribute points.
 * Supports both mouse clicks and hotkeys (1-6 for attributes, ESC/ENTER/SPACE to close).
 */
public class LevelUpModal extends Table {

    private final HudSkin hudSkin;
    private final Label titleLabel;
    private final Label pointsLabel;
    private final Label milestoneBannerLabel;
    private final Table attributesTable;
    private final TextButton doneBtn;
    private final TextButton openSkillTreeBtn;
    private final TextButton openSpellbookBtn;

    private final Map<ShelterAltar.StatType, Label> statValueLabels = new HashMap<>();
    private final Map<ShelterAltar.StatType, TextButton> statAddButtons = new HashMap<>();

    private Player activePlayer;
    private GameEventManager activeEventManager;
    private SoundManager activeSoundManager;
    private Runnable onOpenSkillTree;
    private Runnable onOpenSpellbook;

    private final Texture whitePixel;

    public LevelUpModal(HudSkin hudSkin) {
        this.hudSkin = hudSkin;

        Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pix.setColor(Color.WHITE);
        pix.fill();
        this.whitePixel = new Texture(pix);
        pix.dispose();

        this.setBackground(new TextureRegionDrawable(whitePixel).tint(new Color(0.04f, 0.06f, 0.08f, 0.96f)));
        this.pad(24);

        titleLabel = new Label("LEVEL UP!", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        titleLabel.setAlignment(Align.center);
        this.add(titleLabel).growX().padBottom(4).row();

        milestoneBannerLabel = new Label("", new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT));
        milestoneBannerLabel.setAlignment(Align.center);
        milestoneBannerLabel.setVisible(false);
        this.add(milestoneBannerLabel).growX().padBottom(6).row();

        pointsLabel = new Label("Available Attribute Points: 0", new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE));
        pointsLabel.setAlignment(Align.center);
        this.add(pointsLabel).growX().padBottom(16).row();

        attributesTable = new Table();
        this.add(attributesTable).width(580).padBottom(20).row();

        TextButton.TextButtonStyle skillStyle = new TextButton.TextButtonStyle();
        skillStyle.font = hudSkin.getFontMain();
        skillStyle.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        skillStyle.up = hudSkin.getPrimaryButtonUp();
        skillStyle.over = hudSkin.getPrimaryButtonDown();
        openSkillTreeBtn = new TextButton("SKILL TREE [K]", skillStyle);
        openSkillTreeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                openSkillTree();
            }
        });

        openSpellbookBtn = new TextButton("SPELLBOOK [Q]", skillStyle);
        openSpellbookBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                openSpellbook();
            }
        });

        TextButton.TextButtonStyle doneStyle = new TextButton.TextButtonStyle();
        doneStyle.font = hudSkin.getFontMain();
        doneStyle.fontColor = Color.WHITE;
        doneStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.20f, 0.25f, 0.30f, 0.95f));
        doneStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.30f, 0.38f, 0.45f, 1f));
        doneBtn = new TextButton("DONE [ENTER / ESC]", doneStyle);
        doneBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                close();
            }
        });

        Table btnRow = new Table();
        btnRow.add(openSkillTreeBtn).width(180).height(44).padRight(12);
        btnRow.add(openSpellbookBtn).width(180).height(44).padRight(12);
        btnRow.add(doneBtn).width(180).height(44);
        this.add(btnRow).center().row();

        this.setSize(660, 710);
        this.setVisible(false);
    }

    public void setOnOpenSkillTree(Runnable onOpenSkillTree) {
        this.onOpenSkillTree = onOpenSkillTree;
    }

    public void setOnOpenSpellbook(Runnable onOpenSpellbook) {
        this.onOpenSpellbook = onOpenSpellbook;
    }

    public void openSkillTree() {
        close();
        if (onOpenSkillTree != null) {
            onOpenSkillTree.run();
        }
    }

    public void openSpellbook() {
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

        int lvl = activePlayer.getLevel();
        int slotUnlocked = 0;
        if (lvl == 2) slotUnlocked = 2;
        else if (lvl == 5) slotUnlocked = 3;
        else if (lvl == 8) slotUnlocked = 4;
        else if (lvl == 11) slotUnlocked = 5;

        if (slotUnlocked > 0) {
            milestoneBannerLabel.setText("★ SPELL SLOT " + slotUnlocked + " UNLOCKED! (Press [Q] to prepare spells)");
            milestoneBannerLabel.setVisible(true);
        } else {
            milestoneBannerLabel.setText("");
            milestoneBannerLabel.setVisible(false);
        }

        rebuildAttributeRows();
        refreshValues();

        if (getStage() != null) {
            this.setPosition((getStage().getWidth() - getWidth()) / 2f,
                    (getStage().getHeight() - getHeight()) / 2f);
            getStage().setKeyboardFocus(this);
        }

        this.setVisible(true);
        this.toFront();
    }

    private void rebuildAttributeRows() {
        attributesTable.clear();
        statValueLabels.clear();
        statAddButtons.clear();

        if (activePlayer == null) return;

        ShelterAltar.StatType[] stats = {
                ShelterAltar.StatType.STRENGTH,
                ShelterAltar.StatType.DEXTERITY,
                ShelterAltar.StatType.CONSTITUTION,
                ShelterAltar.StatType.INTELLIGENCE,
                ShelterAltar.StatType.WISDOM,
                ShelterAltar.StatType.AGILITY
        };

        for (int i = 0; i < stats.length; i++) {
            final ShelterAltar.StatType stat = stats[i];
            int keyNum = i + 1;

            Table row = new Table();
            row.setBackground(new TextureRegionDrawable(whitePixel).tint(new Color(0.10f, 0.13f, 0.16f, 0.9f)));
            row.pad(6, 12, 6, 12);

            Label keyBadge = new Label("[" + keyNum + "]", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            row.add(keyBadge).left().padRight(8);

            Label valLabel = new Label(stat.getDisplayName().toUpperCase() + "  10 (+0)",
                    new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT));
            statValueLabels.put(stat, valLabel);
            row.add(valLabel).left().expandX();

            TextButton.TextButtonStyle addStyle = new TextButton.TextButtonStyle();
            addStyle.font = hudSkin.getFontMain();
            addStyle.fontColor = Color.WHITE;
            addStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.18f, 0.45f, 0.25f, 0.95f));
            addStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.25f, 0.60f, 0.35f, 1f));
            addStyle.disabled = new TextureRegionDrawable(whitePixel).tint(new Color(0.15f, 0.18f, 0.20f, 0.5f));

            TextButton addBtn = new TextButton("[ + ]", addStyle);
            addBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    allocate(stat);
                }
            });
            statAddButtons.put(stat, addBtn);
            row.add(addBtn).size(48, 28).right().row();

            Label desc = new Label(stat.getDescription(), new Label.LabelStyle(hudSkin.getFontSmall(), Color.LIGHT_GRAY));
            desc.setWrap(true);
            row.add(desc).colspan(3).fillX().padTop(2);

            attributesTable.add(row).growX().padBottom(6).row();
        }
    }

    private void refreshValues() {
        if (activePlayer == null) return;

        titleLabel.setText("LEVEL UP! REACHED LEVEL " + activePlayer.getLevel() + "!");
        int unallocated = activePlayer.getStats().getUnallocatedAttributePoints();
        int unallocatedSkills = activePlayer.getStats().getUnallocatedSkillPoints();
        pointsLabel.setText("Attribute Points: " + unallocated + "   |   Skill Points: " + unallocatedSkills);
        if (unallocated > 0 || unallocatedSkills > 0) {
            pointsLabel.setColor(HudSkin.COL_GOLD_BRIGHT);
        } else {
            pointsLabel.setColor(Color.LIGHT_GRAY);
        }

        for (Map.Entry<ShelterAltar.StatType, Label> entry : statValueLabels.entrySet()) {
            ShelterAltar.StatType stat = entry.getKey();
            int val = activePlayer.getStats().getEffectiveStat(stat);
            int mod = (val - 10) / 2;
            String modStr = (mod >= 0 ? "+" : "") + mod;
            entry.getValue().setText(stat.getDisplayName().toUpperCase() + "  " + val + " (" + modStr + ")");

            TextButton btn = statAddButtons.get(stat);
            if (btn != null) {
                btn.setDisabled(unallocated <= 0 || val >= 20);
            }
        }
    }

    public void allocate(ShelterAltar.StatType stat) {
        if (activePlayer == null || stat == null) return;
        if (activePlayer.getStats().getUnallocatedAttributePoints() <= 0) return;

        if (activePlayer.allocateAttribute(stat)) {
            if (activeSoundManager != null) {
                activeSoundManager.playPickupItemSound();
            }
            if (activeEventManager != null) {
                activeEventManager.addEvent(new GameEvent("Allocated +1 into " + stat.getDisplayName() + "!", 2f));
            }
            refreshValues();
        }
    }

    public void close() {
        this.setVisible(false);
        if (getStage() != null) {
            getStage().setKeyboardFocus(null);
        }
    }

    public boolean handleInput(int keycode) {
        if (!isVisible()) return false;

        switch (keycode) {
            case Input.Keys.NUM_1:
            case Input.Keys.NUMPAD_1:
                allocate(ShelterAltar.StatType.STRENGTH);
                return true;
            case Input.Keys.NUM_2:
            case Input.Keys.NUMPAD_2:
                allocate(ShelterAltar.StatType.DEXTERITY);
                return true;
            case Input.Keys.NUM_3:
            case Input.Keys.NUMPAD_3:
                allocate(ShelterAltar.StatType.CONSTITUTION);
                return true;
            case Input.Keys.NUM_4:
            case Input.Keys.NUMPAD_4:
                allocate(ShelterAltar.StatType.INTELLIGENCE);
                return true;
            case Input.Keys.NUM_5:
            case Input.Keys.NUMPAD_5:
                allocate(ShelterAltar.StatType.WISDOM);
                return true;
            case Input.Keys.NUM_6:
            case Input.Keys.NUMPAD_6:
                allocate(ShelterAltar.StatType.AGILITY);
                return true;
            case Input.Keys.K:
                openSkillTree();
                return true;
            case Input.Keys.Q:
                openSpellbook();
                return true;
            case Input.Keys.ESCAPE:
            case Input.Keys.ENTER:
            case Input.Keys.SPACE:
                close();
                return true;
            default:
                // Consume all keys while modal is active so gameplay input doesn't fire underneath
                return true;
        }
    }
}
