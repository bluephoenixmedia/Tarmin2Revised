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
import com.badlogic.gdx.utils.Align;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.ui.KeyHintLegend;
import com.bpm.minotaur.ui.UiContexts;
import com.bpm.minotaur.ui.UiGlyphs;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiNames;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

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

    /** This screen's entry on the input-context stack (SPEC 5.6). */
    private static final String CONTEXT = "SKILLTREE";

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
        UiContexts.push(CONTEXT, UiContexts.Kind.PANEL);

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

        // SKILL-1: this was the whole of it. Five labels in one unbroken row, the last of them
        // a 44-character bracketed sentence, came to more than the 1840 units the screen has.
        // The header is inside a fillParent root, so its preferred width made the *root* wider
        // than the stage -- which is why the attributes column was clipped off the left, Arcana
        // off the right, and the footer off the bottom. Nothing else on this screen was wrong
        // with its own width. SKILL-7: the counters are chips and the Training Grounds state
        // is an info strip on its own row, not a fifth item competing for the same line.
        Table statsRow = new Table();
        statsRow.padTop(6);

        int attrPts = player.getStats().getUnallocatedAttributePoints();
        int skillPts = player.getStats().getUnallocatedSkillPoints();
        boolean trainingActive = isTrainingGroundsUnlocked();

        statsRow.add(buildChip("LEVEL " + player.getLevel(), UiTheme.GOLD)).padRight(UiTheme.PAD_MD);
        statsRow.add(buildChip("XP " + player.getExperience() + " / "
                + player.getStats().getExperienceToNextLevel(), UiTheme.TEXT)).padRight(UiTheme.PAD_MD);
        statsRow.add(buildChip(UiNames.plural(attrPts, "attribute point"),
                attrPts > 0 ? UiTheme.SUCCESS : UiTheme.TEXT_DIM)).padRight(UiTheme.PAD_MD);
        statsRow.add(buildChip(UiNames.plural(skillPts, "skill point"),
                skillPts > 0 ? UiTheme.GOLD : UiTheme.TEXT_DIM));

        header.add(statsRow).center().row();

        Label trainingStatusLabel = UiLabels.wrapping(
                trainingActive
                        ? "Training Grounds active -- skills can be learned here."
                        : "Training Grounds not built. Raise it at the Shelter Altar to learn skills.",
                new Label.LabelStyle(hudSkin.getFontSmall(), trainingActive ? UiTheme.SUCCESS : UiTheme.INFO));
        trainingStatusLabel.setAlignment(Align.center);
        header.add(trainingStatusLabel).growX().padTop(UiTheme.PAD_SM).row();
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

        statusLabel = UiLabels.ellipsized("", new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_FOOD_GREEN));
        footer.add(statusLabel).left().expandX();

        footer.add(new KeyHintLegend(hudSkin)
                .hint("CLICK", "Skill details")
                .hint("K", "Close")
                .escapeHint("Return to game")).right();

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
        // SKILL-6: the three schools were red, green and blue -- red against green is the pair
        // most colour-blind players cannot separate, and the game used two greens elsewhere.
        // These three survive deuteranopia and protanopia, and each carries a glyph as well,
        // because colour is never allowed to be the only signal.
        Table warfareCol = buildDisciplineColumn(SkillId.Discipline.WARFARE,
                UiGlyphs.GLYPH_WARFARE + " WARFARE", "Might, heavy armor and dual-wielding", UiTheme.SCHOOL_WARFARE);
        Table finesseCol = buildDisciplineColumn(SkillId.Discipline.FINESSE,
                UiGlyphs.GLYPH_FINESSE + " FINESSE", "Agility, criticals and marksmanship", UiTheme.SCHOOL_FINESSE);
        Table arcanaCol = buildDisciplineColumn(SkillId.Discipline.ARCANA,
                UiGlyphs.GLYPH_ARCANA + " ARCANA", "Spellweaving, runic power and evocation", UiTheme.SCHOOL_ARCANA);

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

        // SKILL-2: neither of these had a width, and the subtitle is wider than a third of the
        // panel, so it ran out of the column and across the school next door.
        Label titleLabel = UiLabels.ellipsized(title, new Label.LabelStyle(hudSkin.getFontHeader(), themeColor));
        titleLabel.setAlignment(Align.center);
        col.add(titleLabel).growX().center().row();

        Label subLabel = UiLabels.wrapping(subtitle, UiStyles.caption(hudSkin));
        subLabel.setAlignment(Align.center);
        col.add(subLabel).growX().center().padBottom(12).row();

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

        Label nameLabel = UiLabels.ellipsized(skill.getName(), new Label.LabelStyle(hudSkin.getFontMain(),
                learned ? UiTheme.SUCCESS : (canLearn ? UiTheme.TEXT : UiTheme.TEXT_OFF)));
        card.add(nameLabel).left().growX();

        // SKILL-4: every node that was not learned printed "[ LOCKED ]", so the column read as
        // a wall of the same word and said nothing about which one the player could take next.
        // A learnable node is simply bright; a locked one is dimmed and carries a lock mark,
        // and the detail pane below names the single thing blocking it.
        String statusBadge;
        Color badgeColor;
        if (learned) {
            statusBadge = "+";
            badgeColor = UiTheme.SUCCESS;
        } else if (canLearn) {
            if (!isTrainingGroundsUnlocked()) {
                statusBadge = "x";
                badgeColor = UiTheme.INFO;
            } else if (player.getStats().getUnallocatedSkillPoints() > 0) {
                statusBadge = "1 SP";
                badgeColor = UiTheme.GOLD;
            } else {
                statusBadge = "1 SP";
                badgeColor = UiTheme.TEXT_OFF;
            }
        } else {
            statusBadge = "x";
            badgeColor = UiTheme.TEXT_OFF;
        }
        if (!learned && !canLearn) {
            card.setColor(1f, 1f, 1f, 0.55f);
        }

        Label badgeLabel = new Label(statusBadge, new Label.LabelStyle(hudSkin.getFontSmall(), badgeColor));
        badgeLabel.addListener(new com.badlogic.gdx.scenes.scene2d.ui.TextTooltip(
                learned ? "Learned" : (canLearn ? "Costs 1 skill point" : blockingRequirement(skill)),
                hudSkin.getTooltipManager(), hudSkin.getTooltipStyle()));
        card.add(badgeLabel).right().padLeft(UiTheme.PAD_SM).row();

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
        // SKILL-3: the name, the Open5e reference and the action button all shared this row
        // with no widths, so "TRAINING GROUNDS LOCKED" -- the longest label the button ever
        // takes -- was drawn over the header beside it. The two text cells truncate; the
        // button keeps its own cell and is a real disabled button with its reason below.
        Table headerRow = new Table();
        Label name = UiLabels.ellipsized(def.getName().toUpperCase(),
                new Label.LabelStyle(hudSkin.getFontHeader(), UiTheme.GOLD));
        headerRow.add(name).left();

        Label ref = UiLabels.ellipsized("  (" + def.getOpen5eReference() + " - Tier " + def.getTier()
                        + " " + UiNames.of(def.getDiscipline()) + ")",
                UiStyles.caption(hudSkin));
        headerRow.add(ref).left().growX();

        if (learned) {
            Label learnedBadge = new Label("MASTERED", new Label.LabelStyle(hudSkin.getFontHeader(), UiTheme.SUCCESS));
            headerRow.add(learnedBadge).right().padLeft(UiTheme.PAD_MD);
        } else {
            // The button says what it does; the reason it cannot is a caption under it.
            TextButton learnBtn = createActionButton("LEARN SKILL");
            boolean enabled = canLearn && trainingActive && (skillPoints > 0);
            UiStyles.setEnabled(learnBtn, enabled);
            if (!enabled) {
                learnBtn.setColor(0.6f, 0.6f, 0.6f, 0.6f);
            }

            learnBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (skillPoints <= 0) {
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

            Table learnCol = new Table();
            learnCol.add(learnBtn).minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H).row();
            if (!enabled) {
                String reason = !trainingActive ? "Needs the Training Grounds"
                        : (skillPoints <= 0 ? "Needs a skill point" : blockingRequirement(def));
                learnCol.add(UiLabels.ellipsized(reason, UiStyles.caption(hudSkin, UiTheme.DANGER)))
                        .right().padTop(UiTheme.PAD_XS);
            }
            headerRow.add(learnCol).right().padLeft(UiTheme.PAD_MD);
        }

        panel.add(headerRow).fillX().padBottom(10).row();

        // Description
        Label desc = UiLabels.wrapping(def.getDescription(), UiStyles.body(hudSkin));
        panel.add(desc).growX().padBottom(10).row();

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
                prereqStr.append(reqDef != null ? reqDef.getName() : UiNames.of(reqId))
                        .append(hasReq ? " (have)" : " (missing)");
                first = false;
            }
        }

        Label prereqLabel = UiLabels.wrapping(prereqStr.toString(), UiStyles.caption(hudSkin));
        panel.add(prereqLabel).growX().left().padBottom(4).row();

        if (!trainingActive && !learned) {
            Label trainingNotice = new Label("Shelter Training Grounds provides martial guidance and combat drills.",
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            panel.add(trainingNotice).left();
        }

        return panel;
    }

    /**
     * The one requirement standing between the player and this skill.
     *
     * <p>SKILL-4: the tree printed "[ LOCKED ]" on every unlearned node, which tells the player
     * nothing they did not already know. A node is blocked by one thing at a time, and that is
     * the thing worth saying.
     */
    private String blockingRequirement(SkillDefinition def) {
        if (def == null) {
            return "Locked";
        }
        if (def.getRequiredStat() != null && def.getRequiredStatValue() > 10) {
            int current = player.getStats().getEffectiveStat(def.getRequiredStat());
            if (current < def.getRequiredStatValue()) {
                return "Needs " + def.getRequiredStat().getDisplayName() + " "
                        + def.getRequiredStatValue() + " - have " + current;
            }
        }
        for (SkillId reqId : def.getPrerequisites()) {
            if (!player.hasSkill(reqId)) {
                SkillDefinition reqDef = SkillRegistry.getInstance().getDefinition(reqId);
                return "Needs " + (reqDef != null ? reqDef.getName() : UiNames.of(reqId));
            }
        }
        return "Locked";
    }

    /** A counter as a chip: a bordered pill sized to its own text (SPEC section 4). */
    private Table buildChip(String text, Color color) {
        Table chip = new Table();
        chip.setBackground(hudSkin.getCardBg());
        chip.add(UiLabels.of(text, new Label.LabelStyle(hudSkin.getFontSmall(), color)))
                .pad(UiTheme.PAD_XS, UiTheme.PAD_MD, UiTheme.PAD_XS, UiTheme.PAD_MD);
        return chip;
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
    public void hide() {
        UiContexts.pop(CONTEXT);
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
