package com.bpm.minotaur.screens.firstaid;

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
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.injury.BodyPart;
import com.bpm.minotaur.gamedata.injury.IllnessStage;
import com.bpm.minotaur.gamedata.injury.InjuryManager;
import com.bpm.minotaur.gamedata.injury.InjuryManager.TreatmentResult;
import com.bpm.minotaur.gamedata.injury.InjuryRecord;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.SoundManager;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.rendering.SpellCastOverlay;
import com.bpm.minotaur.screens.BaseScreen;
import com.bpm.minotaur.screens.GameScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive Scene2D modal window for Field Surgery and Wound Care.
 * Features an interactive anatomical quadrant selector, comprehensive trauma diagnosis,
 * a filtered supply drawer supporting medicines, monster parts, and debris,
 * and realistic triage turn simulation.
 */
public class FirstAidModal extends BaseScreen {

    private final GameScreen parentScreen;
    private final Player player;
    private final Maze maze;
    private final SoundManager soundManager;
    private final SpellCastOverlay spellCastOverlay;

    private Stage stage;
    private HudSkin hudSkin;

    private BodyPart selectedPart = BodyPart.TORSO;
    private Item selectedItem = null;

    private Table limbColumnTable;
    private Table diagnosisPanel;
    private Table supplyListTable;
    private Label feedbackLabel;
    private Label prognosisPreviewLabel;
    private TextButton applyTreatmentBtn;
    private TextButton crudePressureBtn;

    public FirstAidModal(Tarmin2 game, GameScreen parentScreen, Player player, Maze maze,
                         SoundManager soundManager, SpellCastOverlay spellCastOverlay) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.maze = maze;
        this.soundManager = soundManager;
        this.spellCastOverlay = spellCastOverlay;
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());
        hudSkin = new HudSkin();

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        buildUI();
    }

    private void buildUI() {
        stage.clear();

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(hudSkin.getScreenBackdrop());
        root.pad(30f);
        stage.addActor(root);

        // --- 1. HEADER ---
        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(12f, 24f, 12f, 24f);

        Label title = new Label("FIELD SURGERY & WOUND CARE",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        Label subtitle = new Label("Triage physical trauma, bind broken bones, and arrest wound infection. Tending takes world turns.",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));

        header.add(title).center().row();
        header.add(subtitle).center().padTop(4f);
        root.add(header).fillX().padBottom(16f).row();

        // --- 2. MAIN 3-COLUMN CONTENT ---
        Table contentTable = new Table();

        // Left Column: Anatomical Zones (420px)
        Table leftCol = buildAnatomicalZonesColumn();
        contentTable.add(leftCol).width(420f).top().fillY().padRight(16f);

        // Center Column: Diagnosis & Action (680px)
        Table centerCol = buildDiagnosisColumn();
        contentTable.add(centerCol).width(680f).top().fillY().padRight(16f);

        // Right Column: Medical Supply Drawer (680px)
        Table rightCol = buildSupplyDrawerColumn();
        contentTable.add(rightCol).width(680f).top().fillY().row();

        root.add(contentTable).expand().fill().padBottom(16f).row();

        // --- 3. FOOTER & ACTIONS ---
        Table footer = buildFooterBar();
        root.add(footer).fillX();

        refreshDiagnosis();
        refreshSupplies();
    }

    private Table buildAnatomicalZonesColumn() {
        Table panel = new Table();
        panel.setBackground(hudSkin.getPanelBg());
        panel.pad(16f);
        panel.top();

        Label colHeader = new Label("ANATOMICAL ZONES",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_ANTIQUE));
        panel.add(colHeader).left().padBottom(14f).row();

        limbColumnTable = new Table();
        panel.add(limbColumnTable).expandX().fillX().row();

        // Systemic Illness Status Card
        Table illnessCard = new Table();
        illnessCard.setBackground(hudSkin.getSlotRecessed());
        illnessCard.pad(10f);

        InjuryManager im = player.getInjuryManager();
        IllnessStage stage = (im != null) ? im.getIllnessStage() : IllnessStage.HEALTHY;

        Label illTitle = new Label("SYSTEMIC BLOODSTREAM",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        Color illCol = stage.isIll() ? HudSkin.COL_HP_CRITICAL : HudSkin.COL_FOOD_GREEN;
        Label illDesc = new Label(stage.getDisplayName(),
                new Label.LabelStyle(hudSkin.getFontMain(), illCol));

        illnessCard.add(illTitle).left().row();
        illnessCard.add(illDesc).left().padTop(4f);

        panel.add(illnessCard).expandX().fillX().padTop(16f);

        refreshLimbButtons();
        return panel;
    }

    private void refreshLimbButtons() {
        limbColumnTable.clear();
        InjuryManager im = player.getInjuryManager();

        int partIdx = 1;
        for (BodyPart part : BodyPart.values()) {
            final BodyPart currentPart = part;
            final int keyNum = partIdx++;
            boolean isSelected = (currentPart == selectedPart);

            Table limbRow = new Table();
            limbRow.setBackground(isSelected ? hudSkin.getSlotActive() : hudSkin.getSlotRecessed());
            limbRow.pad(10f, 14f, 10f, 14f);

            InjuryRecord rec = (im != null) ? im.getInjury(part) : null;
            String statusText;
            Color statusColor;

            if (rec == null) {
                statusText = "Healthy & Sound";
                statusColor = HudSkin.COL_FOOD_GREEN;
            } else {
                statusText = rec.getInjuryType().getDisplayName();
                if (rec.isInfected()) {
                    statusText += " (Infected)";
                    statusColor = Color.valueOf("7CB342");
                } else if (rec.isTreated()) {
                    statusText += " [Stabilized]";
                    statusColor = Color.valueOf("80CBC4");
                } else {
                    statusText += " [ACTIVE]";
                    statusColor = HudSkin.COL_HP_CRITICAL;
                }
            }

            Table textCol = new Table();
            Label nameLbl = new Label("[" + keyNum + "] " + part.getDisplayName().toUpperCase(),
                    new Label.LabelStyle(hudSkin.getFontMain(), isSelected ? HudSkin.COL_GOLD_BRIGHT : Color.WHITE));
            Label statLbl = new Label(statusText,
                    new Label.LabelStyle(hudSkin.getFontSmall(), statusColor));

            textCol.add(nameLbl).left().row();
            textCol.add(statLbl).left().padTop(2f);

            limbRow.add(textCol).left().expandX();

            limbRow.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectedPart = currentPart;
                    refreshLimbButtons();
                    refreshDiagnosis();
                    refreshSupplies();
                }
            });

            limbColumnTable.add(limbRow).expandX().fillX().padBottom(8f).row();
        }
    }

    private Table buildDiagnosisColumn() {
        diagnosisPanel = new Table();
        diagnosisPanel.setBackground(hudSkin.getDoubleBorderPanel());
        diagnosisPanel.pad(20f);
        diagnosisPanel.top().left();
        return diagnosisPanel;
    }

    private void refreshDiagnosis() {
        diagnosisPanel.clear();

        InjuryManager im = player.getInjuryManager();
        InjuryRecord record = (im != null) ? im.getInjury(selectedPart) : null;

        Label header = new Label("DIAGNOSIS: " + selectedPart.getDisplayName().toUpperCase(),
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        diagnosisPanel.add(header).left().padBottom(14f).row();

        if (record == null) {
            Label healthyLbl = new Label("This anatomical quadrant exhibits intact musculature, bone density, and circulation. No active trauma detected.",
                    new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_FOOD_GREEN));
            healthyLbl.setWrap(true);
            diagnosisPanel.add(healthyLbl).expandX().fillX().padBottom(16f).row();
        } else {
            Table traumaCard = new Table();
            traumaCard.setBackground(hudSkin.getSlotRecessed());
            traumaCard.pad(12f);

            Label typeLbl = new Label("Condition: " + record.getInjuryType().getDisplayName(),
                    new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_HP_CRITICAL));
            Label sevLbl = new Label("Severity: Grade " + record.getSeverity() + " Trauma",
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            Label treatLbl = new Label("Status: " + (record.isTreated() ? "Dressed / Stabilized" : "UNTREATED OPEN WOUND"),
                    new Label.LabelStyle(hudSkin.getFontSmall(), record.isTreated() ? Color.valueOf("80CBC4") : HudSkin.COL_HP_CRITICAL));

            traumaCard.add(typeLbl).left().row();
            traumaCard.add(sevLbl).left().padTop(3f).row();
            traumaCard.add(treatLbl).left().padTop(3f).row();

            if (record.isInfected()) {
                Label infLbl = new Label("WARNING: Sepsis detected! Infection throbs hot in the tissue.",
                        new Label.LabelStyle(hudSkin.getFontSmall(), Color.valueOf("E57373")));
                traumaCard.add(infLbl).left().padTop(4f).row();
            }

            diagnosisPanel.add(traumaCard).expandX().fillX().padBottom(14f).row();

            // Tactical Debuff Explanation
            Label debuffHeader = new Label("ACTIVE FUNCTIONAL DEBUFF:",
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE));
            diagnosisPanel.add(debuffHeader).left().padBottom(4f).row();

            String debuffDesc = getDebuffDescription(selectedPart, record);
            Label debuffText = new Label(debuffDesc, new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE));
            debuffText.setWrap(true);
            diagnosisPanel.add(debuffText).expandX().fillX().padBottom(16f).row();
        }

        // Treatment Prognosis Box
        Table progBox = new Table();
        progBox.setBackground(hudSkin.getSlotRecessed());
        progBox.pad(12f);

        Label progHeader = new Label("SELECTED TREATMENT PROGNOSIS:",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        progBox.add(progHeader).left().row();

        String preview = (im != null) ? im.getItemSuitabilityPreview(selectedPart, selectedItem) : "No treatment selected.";
        prognosisPreviewLabel = new Label(preview, new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT));
        prognosisPreviewLabel.setWrap(true);
        progBox.add(prognosisPreviewLabel).expandX().fillX().padTop(4f).row();

        diagnosisPanel.add(progBox).expandX().fillX().padBottom(10f).row();

        updateButtonStates();
    }

    private String getDebuffDescription(BodyPart part, InjuryRecord rec) {
        if (rec == null) return "None. Operating at peak vitality.";
        switch (part) {
            case TORSO:
                return rec.isTreated()
                        ? "Hemorrhage sealed. Moderate soreness; no blood loss per step."
                        : "ACTIVE HEMORRHAGE: Leaks blood decals on floor and causes true damage every step moved!";
            case LEGS:
                return rec.isTreated()
                        ? "Fracture stabilized with rigid splint. Minor limp (80% standard move speed)."
                        : "CRIPPLING LIMB FRACTURE: Movement speed reduced by 50%! Each step costs double turns.";
            case ARMS:
                return rec.isTreated()
                        ? "Splinted arm. Slight tremor (-1 To-Hit penalty)."
                        : "BROKEN ARM: -4 To-Hit penalty on attacks! Cannot wield two-handed weapons or bows!";
            case HEAD:
                return rec.isTreated()
                        ? "Concussion bandaged. Lingering dizziness."
                        : "SEVERE CONCUSSION: Blurred vision, mana regeneration halted, spellcast disruption!";
            default:
                return "Physical bodily impairment.";
        }
    }

    private Table buildSupplyDrawerColumn() {
        Table panel = new Table();
        panel.setBackground(hudSkin.getPanelBg());
        panel.pad(16f);
        panel.top();

        Label header = new Label("MEDICAL SUPPLIES & DEBRIS",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_ANTIQUE));
        panel.add(header).left().padBottom(10f).row();

        supplyListTable = new Table();
        supplyListTable.top().left();

        ScrollPane scrollPane = new ScrollPane(supplyListTable);
        scrollPane.setFadeScrollBars(false);
        panel.add(scrollPane).expand().fill();

        return panel;
    }

    private void refreshSupplies() {
        supplyListTable.clear();
        InjuryManager im = player.getInjuryManager();

        List<Item> allItems = new ArrayList<>();
        if (player.getInventory() != null) {
            for (Item item : player.getInventory().getAllItems()) {
                if (item != null && im != null && im.isItemViableFirstAid(item)) {
                    allItems.add(item);
                }
            }
        }

        if (allItems.isEmpty()) {
            Table emptyRow = new Table();
            emptyRow.setBackground(hudSkin.getSlotRecessed());
            emptyRow.pad(16f);
            Label emptyLbl = new Label("No viable medical supplies, flora, or debris in your pack.\nScavenge monster webs, roots, bones, or moss from the dungeon.",
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            emptyLbl.setWrap(true);
            emptyRow.add(emptyLbl).expandX().fillX();
            supplyListTable.add(emptyRow).expandX().fillX().padTop(10f).row();
            selectedItem = null;
            updateButtonStates();
            return;
        }

        for (final Item item : allItems) {
            final boolean isSelected = (item == selectedItem);

            Table row = new Table();
            row.setBackground(isSelected ? hudSkin.getSlotActive() : hudSkin.getSlotRecessed());
            row.pad(8f, 12f, 8f, 12f);

            Table textCol = new Table();
            Label nameLbl = new Label(item.getDisplayName(),
                    new Label.LabelStyle(hudSkin.getFontMain(), isSelected ? HudSkin.COL_GOLD_BRIGHT : Color.WHITE));

            String subtext = getSupplySubtext(item);
            Color subColor = subtext.contains("INFECTION") ? Color.valueOf("E57373") : HudSkin.COL_GOLD_MUTED;
            Label descLbl = new Label(subtext, new Label.LabelStyle(hudSkin.getFontSmall(), subColor));

            textCol.add(nameLbl).left().row();
            textCol.add(descLbl).left().padTop(2f);

            row.add(textCol).left().expandX();

            row.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectedItem = item;
                    refreshSupplies();
                    refreshDiagnosis();
                }
            });

            supplyListTable.add(row).expandX().fillX().padBottom(6f).row();
        }

        updateButtonStates();
    }

    private String getSupplySubtext(Item item) {
        if (item == null || item.getType() == null) return "";
        String name = item.getType().name();
        if (name.startsWith("POTION_OF_HEALING")) return "Curative Elixir - 0% Risk, Knits tissue & stops bleed";
        if (name.equals("POTION_VITALITY") || name.equals("GLOWING_LICHEN")) return "Purifying Antidote - Breaks systemic fever";
        if (name.equals("SPIDER_SILK")) return "Sterile Suture - 0% Risk, Seals lacerations";
        if (name.equals("DIRTY_CLOTH")) return "Makeshift Rag - 35% INFECTION RISK! Stops bleed";
        if (name.contains("BONE") || name.contains("ROPE")) return "Rigid Debris - 0% Risk, Splints bone fractures";
        if (name.equals("SLIME_RESIDUE")) return "Cooling Gel - 0% Risk, Soothes severe burns";
        if (name.equals("MOSS_CLUMP")) return "Antiseptic Dressing - 0% Risk, Bandages flesh";
        if (name.equals("TWISTED_ROOT")) return "Astringent Poultice - 0% Risk, Hemostatic seal";
        if (name.contains("WATER")) return "Fresh Water - Flushes wound grime";
        return "Field medical utility";
    }

    private Table buildFooterBar() {
        Table footer = new Table();
        footer.setBackground(hudSkin.getPanelBg());
        footer.pad(12f, 20f, 12f, 20f);

        // Feedback message
        feedbackLabel = new Label("Select an anatomical quadrant and treatment item to proceed.",
                new Label.LabelStyle(hudSkin.getFontSmall(), Color.WHITE));
        feedbackLabel.setEllipsis(true);
        footer.add(feedbackLabel).left().width(740f).padRight(16f);

        // 1. Crude Direct Pressure Button (Emergency Bare Hands)
        TextButton.TextButtonStyle crudeStyle = new TextButton.TextButtonStyle();
        crudeStyle.font = hudSkin.getFontMain();
        crudeStyle.up = hudSkin.getSlotRecessed();
        crudeStyle.down = hudSkin.getSlotActive();
        crudeStyle.fontColor = HudSkin.COL_GOLD_ANTIQUE;

        crudePressureBtn = new TextButton("[P] Crude Pressure", crudeStyle);
        crudePressureBtn.pad(8f, 16f, 8f, 16f);
        crudePressureBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                applyCrudePressure();
            }
        });
        footer.add(crudePressureBtn).padRight(12f);

        // 2. Apply Treatment Button
        TextButton.TextButtonStyle treatStyle = new TextButton.TextButtonStyle();
        treatStyle.font = hudSkin.getFontHeader();
        treatStyle.up = hudSkin.getPrimaryButtonUp();
        treatStyle.down = hudSkin.getPrimaryButtonDown();
        treatStyle.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        treatStyle.disabledFontColor = HudSkin.COL_GOLD_MUTED;
        treatStyle.disabled = hudSkin.getSlotRecessed();

        applyTreatmentBtn = new TextButton("[ENTER] Apply Treatment", treatStyle);
        applyTreatmentBtn.pad(8f, 20f, 8f, 20f);
        applyTreatmentBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                applyTreatmentWithItem();
            }
        });
        footer.add(applyTreatmentBtn).padRight(12f);

        // 3. Close Button
        TextButton.TextButtonStyle closeStyle = new TextButton.TextButtonStyle();
        closeStyle.font = hudSkin.getFontMain();
        closeStyle.up = hudSkin.getSlotRecessed();
        closeStyle.down = hudSkin.getSlotActive();
        closeStyle.fontColor = Color.WHITE;

        TextButton closeBtn = new TextButton("[ESC] Return", closeStyle);
        closeBtn.pad(8f, 16f, 8f, 16f);
        closeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                closeModal();
            }
        });
        footer.add(closeBtn);

        return footer;
    }

    private void updateButtonStates() {
        if (applyTreatmentBtn == null) return;
        applyTreatmentBtn.setDisabled(selectedItem == null);
    }

    private void applyTreatmentWithItem() {
        if (selectedItem == null) {
            feedbackLabel.setText("Select a medical item or supply to apply.");
            feedbackLabel.setColor(HudSkin.COL_HP_CRITICAL);
            return;
        }

        InjuryManager im = player.getInjuryManager();
        TreatmentResult result = im.applyTreatment(selectedPart, selectedItem, false, player, parentScreen.getEventManager());

        if (result.success) {
            // Audio Feedback
            String iname = (selectedItem.getType() != null) ? selectedItem.getType().name() : "";
            if (iname.contains("CLOTH") || iname.contains("SILK")) {
                soundManager.playBandageTearSound();
            } else if (iname.contains("MOSS") || iname.contains("SLIME") || iname.contains("ROOT")) {
                soundManager.playSalveApplySound();
            }
            soundManager.playFirstAidSuccessSound();

            // First-person VFX
            spellCastOverlay.triggerBandageTend(1.6f);

            // Close modal and simulate world turns
            closeModal();
            parentScreen.simulateFirstAidTurnAdvance(result.turnsRequired);
        } else {
            feedbackLabel.setText(result.message);
            feedbackLabel.setColor(HudSkin.COL_HP_CRITICAL);
        }
    }

    private void applyCrudePressure() {
        InjuryManager im = player.getInjuryManager();
        TreatmentResult result = im.applyTreatment(selectedPart, null, true, player, parentScreen.getEventManager());
        // True only when pressure was actually attempted on a bleeding wound --
        // "bare hands cannot mend fractures" is a refusal, not a fumble, and
        // must not cost the player blood or time.
        boolean bareHandsAttempt = !result.success && result.turnsRequired > 0;

        if (result.success) {
            soundManager.playBandageTearSound();
            soundManager.playFirstAidSuccessSound();
            spellCastOverlay.triggerBandageTend(1.6f);

            closeModal();
            parentScreen.simulateFirstAidTurnAdvance(result.turnsRequired);
        } else {
            feedbackLabel.setText(result.message);
            feedbackLabel.setColor(HudSkin.COL_HP_CRITICAL);

            // A fumbled attempt costs time as well as blood, otherwise the modal
            // is a free retry loop: monsters never act while it is open, so the
            // player could roll until the attempt happened to succeed.
            //
            // The modal closes first, exactly as the success path does. Leaving
            // it open would resolve the monsters' turns -- and any death -- behind
            // a dialog the player is still looking at. And the cost is its own
            // small constant, not result.turnsRequired, which is the 25 turns a
            // successful binding takes.
            if (bareHandsAttempt) {
                closeModal();
                parentScreen.simulateFirstAidTurnAdvance(
                        com.bpm.minotaur.gamedata.injury.InjuryManager.FAILED_PRESSURE_TURN_COST);
            }
        }
    }

    private void closeModal() {
        game.setScreen(parentScreen);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            closeModal();
            return true;
        }
        if (keycode == Input.Keys.P) {
            applyCrudePressure();
            return true;
        }
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            if (selectedItem != null) {
                applyTreatmentWithItem();
            } else {
                applyCrudePressure();
            }
            return true;
        }
        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_4) {
            int idx = keycode - Input.Keys.NUM_1;
            BodyPart[] parts = BodyPart.values();
            if (idx < parts.length) {
                selectedPart = parts[idx];
                refreshLimbButtons();
                refreshDiagnosis();
                refreshSupplies();
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.04f, 0.03f, 0.02f, 1f);
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
        if (stage != null) stage.dispose();
        if (hudSkin != null) hudSkin.dispose();
    }
}
