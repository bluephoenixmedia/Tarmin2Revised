package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.gamedata.progression.SkillDefinition;
import com.bpm.minotaur.gamedata.progression.SkillId;
import com.bpm.minotaur.gamedata.progression.SkillRegistry;
import com.bpm.minotaur.rendering.HudSkin;

import java.util.List;

/**
 * Skill Tree and Character Progression Screen.
 * Displays character attributes with point allocation, and 3-discipline perk trees
 * (Warfare, Finesse, Arcana) grounded in Open5e / SRD feats.
 * Skill point spending is unlocked via the Shelter Training Grounds station.
 */
public class SkillTreeScreen extends BaseScreen {

    private final GameScreen parentScreen;
    private final Player player;
    private final Maze maze;
    private final HudSkin hudSkin;

    private Stage stage;
    private SkillId selectedSkillId = SkillId.DUAL_WIELDER;
    private Label statusLabel;
    private Table rootTable;

    public SkillTreeScreen(Tarmin2 game, GameScreen parentScreen, Player player, Maze maze) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.maze = maze;
        this.hudSkin = new HudSkin();
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        buildUI();
    }

    private boolean isTrainingGroundsUnlocked() {
        return ShelterAltar.getInstance().isSkillTreeUnlocked();
    }

    private void buildUI() {
        stage.clear();

        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.pad(24, 40, 24, 40);

        // --- 1. HEADER PANEL ---
        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(12, 24, 12, 24);

        Label title = new Label("CHARACTER PROGRESSION & SKILL TREE",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        header.add(title).center().row();

        Table statsRow = new Table();
        statsRow.padTop(6);

        Label lvlLabel = new Label("LEVEL " + player.getLevel(),
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_ANTIQUE));
        statsRow.add(lvlLabel).padRight(30);

        Label xpLabel = new Label("XP: " + player.getExperience() + " / " + player.getStats().getExperienceToNextLevel(),
                new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE));
        statsRow.add(xpLabel).padRight(40);

        int attrPts = player.getStats().getUnallocatedAttributePoints();
        Label attrPtsLabel = new Label("Attribute Points: " + attrPts,
                new Label.LabelStyle(hudSkin.getFontMain(), attrPts > 0 ? HudSkin.COL_FOOD_GREEN : HudSkin.COL_GOLD_MUTED));
        statsRow.add(attrPtsLabel).padRight(40);

        int skillPts = player.getStats().getUnallocatedSkillPoints();
        Label skillPtsLabel = new Label("Skill Points: " + skillPts,
                new Label.LabelStyle(hudSkin.getFontMain(), skillPts > 0 ? HudSkin.COL_EXP_AMBER : HudSkin.COL_GOLD_MUTED));
        statsRow.add(skillPtsLabel).padRight(40);

        boolean trainingActive = isTrainingGroundsUnlocked();
        Label trainingStatusLabel = new Label(
                trainingActive ? "[ TRAINING GROUNDS ACTIVE ]" : "[ TRAINING GROUNDS LOCKED: Build at Shelter Altar ]",
                new Label.LabelStyle(hudSkin.getFontSmall(), trainingActive ? HudSkin.COL_FOOD_GREEN : HudSkin.COL_TEMP_ORANGE));
        statsRow.add(trainingStatusLabel);

        header.add(statsRow).center().row();
        rootTable.add(header).fillX().padBottom(16).row();

        // --- 2. BODY (ATTRIBUTES LEFT, SKILL TREES RIGHT) ---
        Table body = new Table();

        // Left Panel: Attributes
        Table attrPanel = buildAttributesPanel();
        body.add(attrPanel).width(440).expandY().fillY().padRight(20);

        // Right Panel: Disciplines + Details
        Table rightPanel = new Table();

        Table disciplinesTable = buildDisciplinesTable();
        rightPanel.add(disciplinesTable).expand().fill().padBottom(14).row();

        Table detailPanel = buildSkillDetailPanel();
        rightPanel.add(detailPanel).height(230).fillX();

        body.add(rightPanel).expand().fill();
        rootTable.add(body).expand().fill().padBottom(12).row();

        // --- 3. FOOTER PANEL ---
        Table footer = new Table();
        footer.setBackground(hudSkin.getPanelBg());
        footer.pad(10, 20, 10, 20);

        Label hints = new Label("[ESC / K] Return to Game    [+] Allocate Attribute Points anywhere    [Skill Card] View details & learn perks",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        footer.add(hints).left().expandX();

        statusLabel = new Label("", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_FOOD_GREEN));
        footer.add(statusLabel).right();

        rootTable.add(footer).fillX();
        stage.addActor(rootTable);
    }

    private Table buildAttributesPanel() {
        Table panel = new Table();
        panel.setBackground(hudSkin.getDoubleBorderPanel());
        panel.pad(16);
        panel.top();

        Label attrTitle = new Label("ATTRIBUTES", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        panel.add(attrTitle).left().row();

        Label attrSub = new Label("Earned points can be assigned anytime (Cap: 20).",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        panel.add(attrSub).left().padBottom(16).row();

        int availablePts = player.getStats().getUnallocatedAttributePoints();

        for (ShelterAltar.StatType stat : ShelterAltar.StatType.values()) {
            Table row = new Table();
            row.setBackground(hudSkin.getPanelBg());
            row.pad(8, 12, 8, 12);

            int val = player.getStats().getEffectiveStat(stat);
            int mod = (val - 10) / 2;
            String modStr = (mod >= 0 ? "+" : "") + mod;

            Label nameVal = new Label(stat.getDisplayName().toUpperCase() + "  " + val + " (" + modStr + ")",
                    new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_ANTIQUE));
            row.add(nameVal).left().expandX();

            if (availablePts > 0 && val < 20) {
                TextButton addBtn = createMiniButton("+");
                addBtn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        if (player.allocateAttribute(stat)) {
                            setStatus("Allocated 1 point into " + stat.getDisplayName() + "!", true);
                            buildUI();
                        }
                    }
                });
                row.add(addBtn).size(36, 28).right();
            }

            row.row();
            Label desc = new Label(stat.getDescription(), new Label.LabelStyle(hudSkin.getFontSmall(), Color.LIGHT_GRAY));
            desc.setWrap(true);
            row.add(desc).colspan(2).fillX().padTop(4);

            panel.add(row).fillX().padBottom(8).row();
        }

        return panel;
    }

    private Table buildDisciplinesTable() {
        Table table = new Table();

        // 3 Columns: Warfare, Finesse, Arcana
        Table warfareCol = buildDisciplineColumn(SkillId.Discipline.WARFARE, "WARFARE", "Might, Heavy Armor & Dual-Wielding", HudSkin.COL_HP_RED);
        Table finesseCol = buildDisciplineColumn(SkillId.Discipline.FINESSE, "FINESSE", "Agility, Criticals & Marksmanship", HudSkin.COL_FOOD_GREEN);
        Table arcanaCol = buildDisciplineColumn(SkillId.Discipline.ARCANA, "ARCANA", "Spellweaving, Runic Power & Evocation", HudSkin.COL_MP_BLUE);

        table.add(warfareCol).expand().fill().padRight(12);
        table.add(finesseCol).expand().fill().padRight(12);
        table.add(arcanaCol).expand().fill();

        return table;
    }

    private Table buildDisciplineColumn(SkillId.Discipline discipline, String title, String subtitle, Color themeColor) {
        Table col = new Table();
        col.setBackground(hudSkin.getPanelBg());
        col.pad(12);
        col.top();

        Label titleLabel = new Label(title, new Label.LabelStyle(hudSkin.getFontHeader(), themeColor));
        col.add(titleLabel).center().row();

        Label subLabel = new Label(subtitle, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        col.add(subLabel).center().padBottom(12).row();

        // Group skills by Tier (1, 2, 3)
        for (int tier = 1; tier <= 3; tier++) {
            Label tierLabel = new Label("--- TIER " + tier + " ---",
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            col.add(tierLabel).center().padTop(tier > 1 ? 8 : 0).padBottom(6).row();

            List<SkillDefinition> skills = SkillRegistry.getInstance().getSkillsByDisciplineAndTier(discipline, tier);
            for (SkillDefinition skill : skills) {
                Table skillCard = createSkillCard(skill);
                col.add(skillCard).fillX().padBottom(6).row();
            }
        }

        return col;
    }

    private Table createSkillCard(SkillDefinition skill) {
        Table card = new Table();
        card.pad(8, 10, 8, 10);

        boolean learned = player.hasSkill(skill.getId());
        boolean canLearn = SkillRegistry.getInstance().canLearn(player.getStats(), skill.getId());
        boolean isSelected = (skill.getId() == selectedSkillId);

        if (isSelected) {
            card.setBackground(hudSkin.getDoubleBorderPanel());
        } else {
            card.setBackground(hudSkin.getPanelBg());
        }

        Label nameLabel = new Label(skill.getName(), new Label.LabelStyle(hudSkin.getFontMain(),
                learned ? HudSkin.COL_FOOD_GREEN : (canLearn ? HudSkin.COL_GOLD_BRIGHT : Color.GRAY)));
        card.add(nameLabel).left().expandX();

        String statusBadge;
        Color badgeColor;
        if (learned) {
            statusBadge = "[ LEARNED ]";
            badgeColor = HudSkin.COL_FOOD_GREEN;
        } else if (canLearn) {
            if (!isTrainingGroundsUnlocked()) {
                statusBadge = "[ DUMMY REQ ]";
                badgeColor = HudSkin.COL_TEMP_ORANGE;
            } else if (player.getStats().getUnallocatedSkillPoints() > 0) {
                statusBadge = "[ 1 SP ]";
                badgeColor = HudSkin.COL_GOLD_BRIGHT;
            } else {
                statusBadge = "[ 0 SP ]";
                badgeColor = HudSkin.COL_GOLD_MUTED;
            }
        } else {
            statusBadge = "[ LOCKED ]";
            badgeColor = Color.DARK_GRAY;
        }

        Label badgeLabel = new Label(statusBadge, new Label.LabelStyle(hudSkin.getFontSmall(), badgeColor));
        card.add(badgeLabel).right().row();

        card.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedSkillId = skill.getId();
                buildUI();
            }
        });

        return card;
    }

    private Table buildSkillDetailPanel() {
        Table panel = new Table();
        panel.setBackground(hudSkin.getDoubleBorderPanel());
        panel.pad(16, 20, 16, 20);
        panel.top().left();

        SkillDefinition def = SkillRegistry.getInstance().getDefinition(selectedSkillId);
        if (def == null) return panel;

        boolean learned = player.hasSkill(def.getId());
        boolean canLearn = SkillRegistry.getInstance().canLearn(player.getStats(), def.getId());
        boolean trainingActive = isTrainingGroundsUnlocked();
        int skillPoints = player.getStats().getUnallocatedSkillPoints();

        // Top line: Name + Open5e reference + Action Button
        Table headerRow = new Table();
        Label name = new Label(def.getName().toUpperCase(), new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        headerRow.add(name).left();

        Label ref = new Label("  (" + def.getOpen5eReference() + " - Tier " + def.getTier() + " " + def.getDiscipline().name() + ")",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        headerRow.add(ref).left().expandX();

        if (learned) {
            Label learnedBadge = new Label("[ MASTERED ]", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_FOOD_GREEN));
            headerRow.add(learnedBadge).right();
        } else {
            TextButton learnBtn = createActionButton(
                    !trainingActive ? "TRAINING GROUNDS LOCKED" :
                            (skillPoints <= 0 ? "NEED SKILL POINT" :
                                    (!canLearn ? "PREREQUISITES NOT MET" : "LEARN SKILL (1 SP)"))
            );

            boolean enabled = canLearn && trainingActive && (skillPoints > 0);
            learnBtn.setDisabled(!enabled);
            if (!enabled) {
                learnBtn.setColor(0.6f, 0.6f, 0.6f, 0.6f);
            }

            learnBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (!trainingActive) {
                        setStatus("Construct the Training Grounds at the Shelter Altar to train skills!", false);
                    } else if (skillPoints <= 0) {
                        setStatus("Level up to earn skill points!", false);
                    } else if (!canLearn) {
                        setStatus("You do not meet the stat or feat prerequisites for this skill!", false);
                    } else {
                        if (player.learnSkill(def.getId())) {
                            setStatus("Mastered " + def.getName() + "!", true);
                            buildUI();
                        }
                    }
                }
            });

            headerRow.add(learnBtn).right().height(38);
        }

        panel.add(headerRow).fillX().padBottom(10).row();

        // Description
        Label desc = new Label(def.getDescription(), new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE));
        desc.setWrap(true);
        panel.add(desc).fillX().padBottom(10).row();

        // Prerequisites Row
        StringBuilder prereqStr = new StringBuilder("Prerequisites: ");
        if (def.getPrerequisites().isEmpty() && (def.getRequiredStat() == null || def.getRequiredStatValue() <= 10)) {
            prereqStr.append("None (Entry Tier)");
        } else {
            boolean first = true;
            if (def.getRequiredStat() != null && def.getRequiredStatValue() > 10) {
                int curStat = player.getStats().getEffectiveStat(def.getRequiredStat());
                prereqStr.append(def.getRequiredStat().getDisplayName())
                        .append(" ").append(def.getRequiredStatValue())
                        .append(" (Current: ").append(curStat).append(")");
                first = false;
            }
            for (SkillId reqId : def.getPrerequisites()) {
                if (!first) prereqStr.append(", ");
                SkillDefinition reqDef = SkillRegistry.getInstance().getDefinition(reqId);
                boolean hasReq = player.hasSkill(reqId);
                prereqStr.append(reqDef != null ? reqDef.getName() : reqId.name())
                        .append(hasReq ? " [OK]" : " [MISSING]");
                first = false;
            }
        }

        Label prereqLabel = new Label(prereqStr.toString(), new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        panel.add(prereqLabel).left().padBottom(4).row();

        if (!trainingActive && !learned) {
            Label trainingNotice = new Label("Notice: Skill point training is locked until the Training Grounds station is constructed in the Shelter.",
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_TEMP_ORANGE));
            panel.add(trainingNotice).left();
        }

        return panel;
    }

    private TextButton createActionButton(String text) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = hudSkin.getFontMain();
        style.up = hudSkin.getPrimaryButtonUp();
        style.down = hudSkin.getPrimaryButtonDown();
        style.over = hudSkin.getPrimaryButtonDown();
        style.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        TextButton button = new TextButton(text, style);
        return button;
    }

    private TextButton createMiniButton(String text) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = hudSkin.getFontSmall();
        style.up = hudSkin.getPrimaryButtonUp();
        style.down = hudSkin.getPrimaryButtonDown();
        style.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        TextButton button = new TextButton(text, style);
        return button;
    }

    private void setStatus(String message, boolean positive) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setColor(positive ? HudSkin.COL_FOOD_GREEN : HudSkin.COL_HP_CRITICAL);
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.K) {
            game.setScreen(parentScreen);
            return true;
        }
        return super.keyDown(keycode);
    }

    @Override
    public void render(float delta) {
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
        hudSkin.dispose();
    }
}
