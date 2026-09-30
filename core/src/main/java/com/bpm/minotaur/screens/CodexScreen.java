package com.bpm.minotaur.screens;

import com.bpm.minotaur.ui.UiStyles;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemName;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.managers.SoundManager;
import com.bpm.minotaur.managers.UnlockManager;
import com.bpm.minotaur.rendering.DiscoveryCard;
import com.bpm.minotaur.rendering.HudSkin;
import com.bpm.minotaur.ui.KeyHintLegend;
import com.bpm.minotaur.ui.UiContexts;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiNames;
import com.bpm.minotaur.ui.UiTabs;
import com.bpm.minotaur.ui.UiTheme;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Full-screen compendium opened from the Shelter's Archive Lectern.
 *
 * <p>Presents three comprehensive ledgers:
 * <ul>
 *   <li><b>Armory & Relics:</b> The full progression item pool (items gated at score ≥ 310),
 *       redacted with hints until unlocked via runs or milestones.</li>
 *   <li><b>Arcane Mysteries:</b> The three circles of magic unsealed through the Shelter Altar's
 *       Arcane Attunement tree.</li>
 *   <li><b>Camp Renovations:</b> Built shelter stations and active tier benefits across all
 *       meta-progression trees.</li>
 * </ul>
 */
public class CodexScreen extends BaseScreen {

    public enum Tab {
        ARMORY, ARCANE, CAMP
    }

    public enum CategoryFilter {
        ALL("ALL CATEGORIES"),
        WEAPONS("WEAPONS"),
        ARMOR("ARMOR"),
        ITEMS("ITEMS & TRINKETS");

        public final String label;
        CategoryFilter(String label) { this.label = label; }
    }

    public enum StatusFilter {
        ALL("ALL STATUS"),
        UNLOCKED("UNLOCKED"),
        LOCKED("LOCKED");

        public final String label;
        StatusFilter(String label) { this.label = label; }
    }

    private final GameScreen parentScreen;
    private final Player player;
    private final HudSkin hudSkin;

    private Stage stage;
    /** This screen's entry on the input-context stack (SPEC 5.6). */
    private static final String CONTEXT = "CHRONICLE";

    private Tab activeTab = Tab.ARMORY;
    private CategoryFilter categoryFilter = CategoryFilter.ALL;
    private StatusFilter statusFilter = StatusFilter.ALL;

    private Table bodyContainer;
    private UiTabs tabs;
    private Texture mysteryTexture;

    public CodexScreen(Tarmin2 game, GameScreen parentScreen, Player player) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.hudSkin = new HudSkin();
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    returnToShelter();
                    return true;
                }
                return tabs != null && tabs.handleKey(keycode);
            }
        });
        Gdx.input.setInputProcessor(multiplexer);

        UiContexts.push(CONTEXT, UiContexts.Kind.PANEL);
        createMysteryTexture();
        buildUI();
    }

    private void createMysteryTexture() {
        if (mysteryTexture == null) {
            Pixmap p = new Pixmap(48, 48, Pixmap.Format.RGBA8888);
            p.setColor(new Color(0.15f, 0.12f, 0.08f, 0.85f));
            p.fill();
            p.setColor(HudSkin.COL_GOLD_MUTED);
            p.drawRectangle(0, 0, 48, 48);
            mysteryTexture = new Texture(p);
            p.dispose();
        }
    }

    private void buildUI() {
        stage.clear();

        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(30, 80, 30, 80);

        // --- HEADER ---
        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(16, 24, 16, 24);

        Label title = new Label("CHRONICLE OF TARMIN",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        header.add(title).center().row();

        Label subtitle = new Label("Compendium of Unearthed Relics, Arcane Mysteries, and Haven Renovations",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        header.add(subtitle).center().padTop(4).row();

        root.add(header).fillX().padBottom(14).row();

        // --- TOP TAB BAR & RETURN BUTTON ---
        Table navBar = new Table();

        // CHRON-1/CHRON-10 (RC5): three tabs at a hard 280 each, with "1." baked into the
        // label text so the number sat outside the tab once the name overflowed. The shared
        // widget puts the number in a keycap of its own and lets each tab be as wide as its
        // name. The Return button was pinned to 290, which its own label overflowed, and the
        // "(ESC)" in that label is what the legend is for.
        tabs = new UiTabs(hudSkin)
                .addTab(1, "Armory & Relics")
                .addTab(2, "Arcane Mysteries")
                .addTab(3, "Camp Renovations");
        tabs.onSelect(index -> {
            Tab picked = Tab.values()[index];
            if (activeTab != picked) {
                activeTab = picked;
                playPageSound();
            }
            refreshView();
        });
        tabs.setSelectedSilently(activeTab.ordinal());
        navBar.add(tabs).left().expandX();

        TextButton backBtn = createActionButton("RETURN TO SHELTER", false);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                returnToShelter();
            }
        });
        navBar.add(backBtn).right().minWidth(UiTheme.BUTTON_MIN_W).height(UiTheme.BUTTON_H);

        root.add(navBar).fillX().padBottom(12).row();

        // --- BODY CONTAINER ---
        bodyContainer = new Table();
        root.add(bodyContainer).expand().fill().padBottom(8).row();

        root.add(new KeyHintLegend(hudSkin)
                .hint("1-3", "Tab")
                .escapeHint("Return to shelter")).right().row();

        stage.addActor(root);

        refreshView();
    }

    private TextButton createActionButton(String text, boolean gold) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = hudSkin.getFontMain();
        style.up = gold ? hudSkin.getPrimaryButtonUp() : hudSkin.getPanelBg();
        style.down = hudSkin.getPrimaryButtonDown();
        style.over = gold ? hudSkin.getPrimaryButtonUp() : hudSkin.getSlotRecessed();
        style.fontColor = gold ? HudSkin.COL_TEXT_ON_GOLD : HudSkin.COL_GOLD_BRIGHT;
        return new TextButton(text, style);
    }

    private TextButton createFilterPill(String text, boolean selected, Runnable action) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = hudSkin.getFontSmall();
        style.up = selected ? hudSkin.getPrimaryButtonDown() : hudSkin.getSlotRecessed();
        style.down = hudSkin.getPrimaryButtonDown();
        style.over = hudSkin.getPrimaryButtonDown();
        style.fontColor = selected ? HudSkin.COL_GOLD_BRIGHT : HudSkin.COL_GOLD_MUTED;

        TextButton btn = new TextButton(text, style);
        btn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                action.run();
            }
        });
        return btn;
    }

    private void refreshView() {
        bodyContainer.clear();

        switch (activeTab) {
            case ARMORY:
                buildArmoryTab();
                break;
            case ARCANE:
                buildArcaneTab();
                break;
            case CAMP:
                buildCampTab();
                break;
        }
    }

    // =========================================================================
    // TAB 1: ARMORY & RELICS (PROGRESSION UNLOCKS)
    // =========================================================================

    private void buildArmoryTab() {
        ItemDataManager itemData = game.getItemDataManager();
        UnlockManager unlockManager = UnlockManager.getInstance();

        // Synchronize any items currently carried by the player or stored in the shelter chest
        if (player != null && player.getInventory() != null) {
            for (Item item : player.getInventory().getAllItems()) {
                if (item != null) {
                    unlockManager.recordItemEncountered(item);
                }
            }
        }
        for (Item item : com.bpm.minotaur.gamedata.item.ShelterChest.getInstance().getItems()) {
            if (item != null) {
                unlockManager.recordItemEncountered(item);
            }
        }

        // 1. Collect and classify items (weapons, armor, relics, and progression-gated gear)
        List<Item.ItemType> allGated = new ArrayList<>();
        int unlockedCount = 0;

        for (Item.ItemType type : Item.ItemType.values()) {
            try {
                ItemTemplate t = (itemData != null) ? itemData.getTemplate(type) : null;
                if (t != null && t.friendlyName != null && !t.friendlyName.isEmpty()
                        && (t.unlockGated || t.isWeapon || t.isArmor || unlockManager.isUnlocked(type.name()))) {
                    allGated.add(type);
                    if (unlockManager.isUnlocked(type.name())) {
                        unlockedCount++;
                    }
                }
            } catch (Exception ignored) {}
        }

        // Sort ascending by calculated score
        allGated.sort(Comparator.comparingInt(t -> {
            ItemTemplate tmpl = itemData != null ? itemData.getTemplate(t) : null;
            return UnlockManager.calculateItemScore(tmpl);
        }));

        // 2. Filter collection
        List<Item.ItemType> filtered = new ArrayList<>();
        for (Item.ItemType type : allGated) {
            ItemTemplate t = itemData != null ? itemData.getTemplate(type) : null;
            if (t == null) continue;

            boolean isUnlocked = unlockManager.isUnlocked(type.name());

            // Status filter
            if (statusFilter == StatusFilter.UNLOCKED && !isUnlocked) continue;
            if (statusFilter == StatusFilter.LOCKED && isUnlocked) continue;

            // Category filter
            if (categoryFilter == CategoryFilter.WEAPONS && !t.isWeapon) continue;
            if (categoryFilter == CategoryFilter.ARMOR && !t.isArmor) continue;
            if (categoryFilter == CategoryFilter.ITEMS && (t.isWeapon || t.isArmor)) continue;

            filtered.add(type);
        }

        // 3. Top filter & progress panel
        Table filterBar = new Table();
        filterBar.setBackground(hudSkin.getPanelBg());
        filterBar.pad(8, 16, 8, 16);

        // Progress bar text
        int total = allGated.size();
        int pct = total > 0 ? (unlockedCount * 100 / total) : 0;
        Label progressLbl = new Label(String.format("UNLOCKED: %d / %d (%d%%)", unlockedCount, total, pct),
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        filterBar.add(progressLbl).left().padRight(24);

        // Category filters
        for (CategoryFilter cf : CategoryFilter.values()) {
            TextButton pill = createFilterPill(cf.label, categoryFilter == cf, () -> {
                categoryFilter = cf;
                playPageSound();
                refreshView();
            });
            filterBar.add(pill).height(36).padRight(6);
        }

        filterBar.add().expandX(); // spacer

        // Status filters
        for (StatusFilter sf : StatusFilter.values()) {
            TextButton pill = createFilterPill(sf.label, statusFilter == sf, () -> {
                statusFilter = sf;
                playPageSound();
                refreshView();
            });
            filterBar.add(pill).height(36).padRight(6);
        }

        bodyContainer.add(filterBar).fillX().padBottom(10).row();

        // 4. Uniform Card Grid inside ScrollPane
        Table grid = new Table();
        grid.top().left();

        int col = 0;
        final int COLS = 4;
        final float CARD_WIDTH = 415f;
        final float CARD_HEIGHT = 185f;

        for (Item.ItemType type : filtered) {
            ItemTemplate t = itemData != null ? itemData.getTemplate(type) : null;
            boolean isUnlocked = unlockManager.isUnlocked(type.name());
            int score = UnlockManager.calculateItemScore(t);

            Table card = buildItemCard(type, t, isUnlocked, score, CARD_WIDTH, CARD_HEIGHT);
            grid.add(card).size(CARD_WIDTH, CARD_HEIGHT).pad(6);

            col++;
            if (col >= COLS) {
                grid.row();
                col = 0;
            }
        }

        if (filtered.isEmpty()) {
            Label emptyLbl = new Label("No items match the selected category and status filters.",
                    new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_MUTED));
            emptyLbl.setAlignment(Align.center);
            grid.add(emptyLbl).padTop(80).expandX().center();
        }

        ScrollPane scroll = new ScrollPane(grid, UiStyles.scrollPane(hudSkin));
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        bodyContainer.add(scroll).expand().fill().row();
    }

    private Table buildItemCard(Item.ItemType type, ItemTemplate t, boolean isUnlocked, int score,
                               float width, float height) {
        Table card = new Table();
        card.setBackground(isUnlocked ? hudSkin.getDoubleBorderPanel() : hudSkin.getSlotRecessed());
        card.top().left().pad(10, 12, 10, 12);

        // Header Row: Icon + Title/Score
        Table headerRow = new Table();
        headerRow.top().left();

        // Item Sprite / Unknown Icon
        TextureRegion icon = isUnlocked ? DiscoveryCard.resolveIcon(type, game.getItemDataManager(), game.getAssetManager()) : null;
        if (icon != null) {
            Image img = new Image(icon);
            headerRow.add(img).size(42, 42).padRight(10).left();
        } else {
            Image img = new Image(mysteryTexture);
            if (!isUnlocked) {
                img.setColor(new Color(0.6f, 0.6f, 0.6f, 0.6f));
            }
            headerRow.add(img).size(42, 42).padRight(10).left();
        }

        Table nameScoreCol = new Table();
        nameScoreCol.top().left();

        // CHRON-2: three things shared this row -- the name, a "[RELIC] Score: n" subtitle and
        // an UNLOCKED badge -- and all three were sized by hand. The name was pinned to
        // width-155, which left the badge no room, so it crossed the card's own border. The
        // name column now takes whatever is left after the icon and the badge, and truncates
        // with a tooltip rather than silently: two different rings both truncating to "Ring of
        // Resistance" made them indistinguishable in the grid.
        String displayName = isUnlocked ? ItemName.natural(t.friendlyName) : "???";
        Color titleCol = isUnlocked ? HudSkin.COL_GOLD_BRIGHT : Color.GRAY;
        Label nameLbl = UiLabels.ellipsized(displayName, new Label.LabelStyle(hudSkin.getFontMain(), titleCol));
        if (isUnlocked) {
            nameLbl.addListener(new com.badlogic.gdx.scenes.scene2d.ui.TextTooltip(
                    displayName, hudSkin.getTooltipManager(), hudSkin.getTooltipStyle()));
        }
        nameScoreCol.add(nameLbl).growX().left().row();

        // Subtitle row: Category & Score
        String catName;
        if (t.isWeapon) catName = "Weapon";
        else if (t.isArmor) catName = "Armor";
        else catName = "Relic";

        String subText = isUnlocked
                ? catName + "  -  Score " + score
                : catName + "  -  Score " + score + "+";
        Label subLbl = UiLabels.ellipsized(subText, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        nameScoreCol.add(subLbl).growX().left().row();

        headerRow.add(nameScoreCol).growX().left();

        // Status as a compact chip, sized to itself, so it can never push past the card edge.
        Label statusBadge = new Label(isUnlocked ? "+" : "-",
                new Label.LabelStyle(hudSkin.getFontMain(), isUnlocked ? HudSkin.COL_FOOD_GREEN : HudSkin.COL_TEXT_MUTED));
        statusBadge.addListener(new com.badlogic.gdx.scenes.scene2d.ui.TextTooltip(
                isUnlocked ? "Unlocked" : "Not yet discovered",
                hudSkin.getTooltipManager(), hudSkin.getTooltipStyle()));
        headerRow.add(statusBadge).top().right().padLeft(UiTheme.PAD_SM).width(UiTheme.ICON_SM);

        card.add(headerRow).fillX().padBottom(6).row();

        // Divider
        Image divider = new Image(hudSkin.getWhitePixel());
        divider.setColor(HudSkin.COL_STONE_MID);
        card.add(divider).fillX().height(2).padBottom(6).row();

        // Stats & Lore / Hint
        if (isUnlocked) {
            // Stats line
            StringBuilder sb = new StringBuilder();
            if (t.isWeapon && t.damageDice != null) {
                sb.append("Dmg: ").append(t.damageDice).append("  ");
            }
            if (t.isArmor && t.armorClassBonus != 0) {
                sb.append("AC: +").append(t.armorClassBonus).append("  ");
            }
            if (t.baseValue > 0) {
                sb.append("Val: ").append(t.baseValue).append("g");
            }
            if (sb.length() > 0) {
                Label statsLbl = new Label(sb.toString(), new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE));
                card.add(statsLbl).left().padBottom(4).row();
            }

            // Description / Lore
            String desc = (t.description != null && !t.description.isEmpty())
                    ? t.description
                    : "A masterwork relic unearthed from Castle Tarmin.";
            Label descLbl = new Label(desc, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            descLbl.setWrap(true);
            card.add(descLbl).width(width - 24).left().expand().top().row();
        } else {
            int reqStrata = Math.max(1, (score - UnlockManager.UNLOCK_SCORE_THRESHOLD) / 250 + 1);
            Label hintLbl = new Label(String.format("Requires Strata %d+ delve to discover.", reqStrata),
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE));
            card.add(hintLbl).left().padBottom(4).row();

            Label concealedLbl = new Label("Properties and lore remain veiled until granted by expedition milestones.",
                    new Label.LabelStyle(hudSkin.getFontSmall(), Color.DARK_GRAY));
            concealedLbl.setWrap(true);
            card.add(concealedLbl).width(width - 24).left().expand().top().row();
        }

        return card;
    }

    // =========================================================================
    // TAB 2: ARCANE MYSTERIES (SPELL ATTUNEMENT CIRCLES)
    // =========================================================================

    private void buildArcaneTab() {
        ShelterAltar altar = ShelterAltar.getInstance();

        Table content = new Table();
        content.top().left();

        String[][] spellsByCircle = {
                { "MAGIC_MISSILE", "SHIELD", "BURNING_HANDS" },
                { "MISTY_STEP", "ACID_ARROW", "SCORCHING_RAY" },
                { "FIREBALL", "LIGHTNING_BOLT" }
        };

        String[] circleNames = {
                "CIRCLE I - NOVICE INVOCATIONS (ARCANE ATTUNEMENT TIER 1)",
                "CIRCLE II - ADEPT EVOCATIONS (ARCANE ATTUNEMENT TIER 2)",
                "CIRCLE III - MASTER CONVOCATIONS (ARCANE ATTUNEMENT TIER 3)"
        };

        String[] circlePerks = {
                "Unseals Circle I spells for Tome Choice rolls; Tomes present 4 choices.",
                "Unseals Circle II spells for Tome Choice rolls; grants 1 free Tome reroll per shrine.",
                "Unseals Circle III spells; Tomes present 5 choices and can yield Level 8 magic."
        };

        for (int circle = 0; circle < spellsByCircle.length; circle++) {
            int circleTier = circle + 1;
            boolean circleUnlocked = altar.getTier(ShelterAltar.Tree.ARCANE_ATTUNEMENT) >= circleTier;

            Table circleSection = new Table();
            circleSection.setBackground(hudSkin.getDoubleBorderPanel());
            circleSection.top().left().pad(16);

            // Circle Header
            Table headRow = new Table();
            Label cTitle = UiLabels.ellipsized(circleNames[circle],
                    new Label.LabelStyle(hudSkin.getFontHeader(), circleUnlocked ? HudSkin.COL_GOLD_BRIGHT : Color.GRAY));
            headRow.add(cTitle).left().growX();

            Label cStatus = UiLabels.of(circleUnlocked ? "Unsealed" : "Sealed",
                    new Label.LabelStyle(hudSkin.getFontMain(), circleUnlocked ? HudSkin.COL_FOOD_GREEN : HudSkin.COL_HP_RED));
            headRow.add(cStatus).right().padLeft(UiTheme.PAD_MD);
            circleSection.add(headRow).fillX().padBottom(6).row();

            Label perkLbl = UiLabels.wrapping(circlePerks[circle],
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            circleSection.add(perkLbl).growX().left().padBottom(12).row();

            // CHRON-3: the spell cards were laid out in one unbounded row, so three 540-wide
            // cards made this section wider than the screen. Everything anchored to the
            // section's right edge -- the circle's own status included -- was dragged off with
            // them, which is where "3][ SEALED ]" came from. Two per row keeps the section
            // inside the viewport.
            Table spellsRow = new Table();
            int spellCol = 0;
            for (String spellId : spellsByCircle[circle]) {
                // CHRON-6: a spell inside a sealed circle used to advertise itself as
                // AVAILABLE, because its own seal was checked without asking whether the
                // circle holding it was open.
                boolean spellUnsealed = circleUnlocked && !altar.isSpellSealed(spellId);
                Table spellCard = buildSpellCard(spellId, circleTier, spellUnsealed);
                spellsRow.add(spellCard).width(540).height(120).padRight(12).padBottom(8);
                if (++spellCol % 2 == 0) {
                    spellsRow.row();
                }
            }
            circleSection.add(spellsRow).left().row();

            content.add(circleSection).fillX().padBottom(16).row();
        }

        ScrollPane scroll = new ScrollPane(content, UiStyles.scrollPane(hudSkin));
        scroll.setFadeScrollBars(false);
        bodyContainer.add(scroll).expand().fill().row();
    }

    private Table buildSpellCard(String spellId, int tier, boolean unsealed) {
        Table card = new Table();
        card.setBackground(unsealed ? hudSkin.getPanelBg() : hudSkin.getSlotRecessed());
        card.top().left().pad(10, 14, 10, 14);

        Table topRow = new Table();
        // CHRON-5: the card showed the spell id with its underscores swapped for spaces, when
        // the spell data already carries a name and a description written for a player.
        com.bpm.minotaur.gamedata.spells.SpellTemplate template =
                com.bpm.minotaur.gamedata.spells.SpellDataManager.getSpell(spellId);
        String formattedName = (template != null && template.getName() != null && !template.getName().isEmpty())
                ? template.getName()
                : UiNames.fromConstant(spellId);
        Label nameLbl = UiLabels.ellipsized(formattedName,
                new Label.LabelStyle(hudSkin.getFontMain(), unsealed ? HudSkin.COL_GOLD_BRIGHT : Color.GRAY));
        topRow.add(nameLbl).left().growX();

        Label badge = UiLabels.of(unsealed ? "Available" : ("Tier " + tier + " lock"),
                new Label.LabelStyle(hudSkin.getFontSmall(), unsealed ? HudSkin.COL_FOOD_GREEN : HudSkin.COL_HP_RED));
        topRow.add(badge).right().padLeft(UiTheme.PAD_SM);
        card.add(topRow).fillX().padBottom(6).row();

        String desc = (template != null && template.getDescription() != null && !template.getDescription().isEmpty())
                ? template.getDescription()
                : getSpellDescription(spellId);
        Label descLbl = UiLabels.wrapping(desc,
                new Label.LabelStyle(hudSkin.getFontSmall(), unsealed ? HudSkin.COL_GOLD_MUTED : Color.DARK_GRAY));
        card.add(descLbl).growX().left().expand().top().row();

        return card;
    }

    private String getSpellDescription(String spellId) {
        switch (spellId) {
            case "MAGIC_MISSILE": return "Fires unerring darts of arcane force that strike targets instantly.";
            case "SHIELD": return "An invisible barrier of magical energy that bolsters armor and deflects strikes.";
            case "BURNING_HANDS": return "A cone of searing flame erupts from your fingertips, burning near enemies.";
            case "MISTY_STEP": return "Briefly dissolve into silvery mist, instantly teleporting across distances.";
            case "ACID_ARROW": return "A shimmering green dart of corrosive acid that dissolves defenses over time.";
            case "SCORCHING_RAY": return "Hurls multiple beams of searing fire toward foes with deadly accuracy.";
            case "FIREBALL": return "A blinding streak that blooms into an incandescent explosion of roaring flame.";
            case "LIGHTNING_BOLT": return "A stroke of crackling lightning that pierces through dungeon hallways.";
            default: return "An ancient sorcery deciphered from the lost arcana of Minos.";
        }
    }

    // =========================================================================
    // TAB 3: CAMP RENOVATIONS (STATIONS & ALTAR UPGRADES)
    // =========================================================================

    private void buildCampTab() {
        ShelterAltar altar = ShelterAltar.getInstance();

        Table content = new Table();
        content.top().left();

        // 1. Shelter Stations Section
        Label stationHeader = new Label("HAVEN ARCHITECTURE & STATIONS",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        content.add(stationHeader).left().padBottom(10).row();

        Table stationGrid = new Table();
        stationGrid.top().left();
        int sCol = 0;
        for (ShelterAltar.Station station : ShelterAltar.Station.values()) {
            // A station gated behind a dungeon depth the player has not reached
            // stays out of the Codex too. Listing it here with its name, cost
            // and description would leak exactly what the Altar is hiding.
            if (!station.isRevealed()) continue;

            boolean built = altar.hasStation(station);
            Table sCard = new Table();
            sCard.setBackground(built ? hudSkin.getDoubleBorderPanel() : hudSkin.getSlotRecessed());
            sCard.top().left().pad(12);

            // CHRON-3: the name took every unit it wanted and the cost tag was anchored right,
            // so "BED / SLEEPING BAG" and "[ 15 DIVINITIES ]" were drawn over each other. The
            // cost keeps its own cell; the name takes what is left of the card.
            Table topRow = new Table();
            Label sName = UiLabels.ellipsized(station.getDisplayName().toUpperCase(),
                    new Label.LabelStyle(hudSkin.getFontMain(), built ? HudSkin.COL_GOLD_BRIGHT : Color.GRAY));
            topRow.add(sName).left().growX();

            Label sBadge = UiLabels.of(built ? "Constructed"
                            : UiNames.plural(station.getCost(), "Divinity", "Divinities"),
                    new Label.LabelStyle(hudSkin.getFontSmall(), built ? HudSkin.COL_FOOD_GREEN : HudSkin.COL_GOLD_MUTED));
            topRow.add(sBadge).right().padLeft(UiTheme.PAD_MD);
            sCard.add(topRow).fillX().padBottom(6).row();

            Label sDesc = UiLabels.wrapping(station.getDescription(),
                    new Label.LabelStyle(hudSkin.getFontSmall(), built ? HudSkin.COL_GOLD_MUTED : Color.DARK_GRAY));
            sCard.add(sDesc).growX().left().expand().top().row();

            stationGrid.add(sCard).size(550, 140).pad(6);
            sCol++;
            if (sCol >= 3) {
                stationGrid.row();
                sCol = 0;
            }
        }
        content.add(stationGrid).fillX().padBottom(24).row();

        // 2. Altar Blessings Trees
        Label treesHeader = new Label("ALTAR META-PROGRESSION BLESSINGS",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        content.add(treesHeader).left().padBottom(10).row();

        Table treesGrid = new Table();
        treesGrid.top().left();
        // Four 415-wide cards in one unbroken row came to more than the panel had, so the
        // fourth sat off the right edge. Three per row, like the stations above.

        // Provisions
        int provTier = altar.getProvisionsTier();
        treesGrid.add(buildTreeCard("PROVISIONS", provTier, 3,
                "Extra survival bread, waterskin, and field bandages stocked at the start of each expedition.",
                String.format("Current Benefit: +%d extra rations & bandages packed.", provTier)))
                .size(415, 150).pad(6);

        // Repertoire
        int repTier = altar.getRepertoireTier();
        treesGrid.add(buildTreeCard("REPERTOIRE", repTier, 3,
                "Mastery of martial stances, active stamina techniques, and combat maneuvers.",
                String.format("Current Benefit: Tier %d martial readiness.", repTier)))
                .size(415, 150).pad(6);

        // Monument
        int monTier = altar.getMonumentTier();
        treesGrid.add(buildTreeCard("MONUMENT", monTier, 3,
                "Consecrated stone pillars increasing frequency of divine statue encounter shrines in the strata.",
                String.format("Current Benefit: %.0f%% shrine event frequency.", altar.getStatueEventFrequency() * 100)))
                .size(415, 150).pad(6);
        treesGrid.row();

        // Arcane Attunement
        int arcTier = altar.getTier(ShelterAltar.Tree.ARCANE_ATTUNEMENT);
        treesGrid.add(buildTreeCard("ARCANE ATTUNEMENT", arcTier, 3,
                "Unseals circles of magic and empowers Tome Choice choices and shrine rerolls.",
                String.format("Current Benefit: Tier %d unsealed (%d options, %d reroll).", arcTier,
                        altar.getTomeChoicePerks().options(), altar.getTomeChoicePerks().rerolls())))
                .size(415, 150).pad(6);

        content.add(treesGrid).fillX().padBottom(20).row();

        // 3. Ascension Stats
        Label ascHeader = new Label("ALTAR OF ASCENSION (CREST STATS)",
                new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        content.add(ascHeader).left().padBottom(10).row();

        Table ascGrid = new Table();
        ascGrid.top().left();
        int aCol = 0;
        for (ShelterAltar.StatType stat : ShelterAltar.StatType.values()) {
            int tier = altar.getAscensionTier(stat);
            Table card = new Table();
            card.setBackground(tier > 0 ? hudSkin.getDoubleBorderPanel() : hudSkin.getSlotRecessed());
            card.top().left().pad(10, 14, 10, 14);

            Table top = new Table();
            Label statName = new Label(stat.getDisplayName(),
                    new Label.LabelStyle(hudSkin.getFontMain(), tier > 0 ? HudSkin.COL_GOLD_BRIGHT : Color.GRAY));
            top.add(statName).left().expandX();

            Label tierBadge = new Label(String.format("TIER %d / %d (+%d)", tier, ShelterAltar.MAX_ASCENSION_TIER, tier),
                    new Label.LabelStyle(hudSkin.getFontSmall(), tier > 0 ? HudSkin.COL_GOLD_ANTIQUE : Color.DARK_GRAY));
            top.add(tierBadge).right();
            card.add(top).fillX().padBottom(4).row();

            Label desc = new Label(stat.getDescription(),
                    new Label.LabelStyle(hudSkin.getFontSmall(), tier > 0 ? HudSkin.COL_GOLD_MUTED : Color.DARK_GRAY));
            desc.setWrap(true);
            card.add(desc).width(520).left().expand().top().row();

            ascGrid.add(card).size(550, 100).pad(6);
            aCol++;
            if (aCol >= 3) {
                ascGrid.row();
                aCol = 0;
            }
        }
        content.add(ascGrid).fillX().row();

        ScrollPane scroll = new ScrollPane(content, UiStyles.scrollPane(hudSkin));
        scroll.setFadeScrollBars(false);
        bodyContainer.add(scroll).expand().fill().row();
    }

    private Table buildTreeCard(String title, int tier, int maxTier, String desc, String benefit) {
        Table card = new Table();
        card.setBackground(tier > 0 ? hudSkin.getDoubleBorderPanel() : hudSkin.getSlotRecessed());
        card.top().left().pad(10, 12, 10, 12);

        Table top = new Table();
        Label nameLbl = new Label(title,
                new Label.LabelStyle(hudSkin.getFontMain(), tier > 0 ? HudSkin.COL_GOLD_BRIGHT : Color.GRAY));
        top.add(nameLbl).left().expandX();

        Label badge = new Label(String.format("TIER %d / %d", tier, maxTier),
                new Label.LabelStyle(hudSkin.getFontSmall(), tier > 0 ? HudSkin.COL_GOLD_ANTIQUE : Color.DARK_GRAY));
        top.add(badge).right();
        card.add(top).fillX().padBottom(4).row();

        Label benefitLbl = new Label(benefit,
                new Label.LabelStyle(hudSkin.getFontSmall(), tier > 0 ? HudSkin.COL_FOOD_GREEN : Color.GRAY));
        card.add(benefitLbl).left().padBottom(4).row();

        Label descLbl = new Label(desc,
                new Label.LabelStyle(hudSkin.getFontSmall(), tier > 0 ? HudSkin.COL_GOLD_MUTED : Color.DARK_GRAY));
        descLbl.setWrap(true);
        card.add(descLbl).width(390).left().expand().top().row();

        return card;
    }

    private void playPageSound() {
        SoundManager sm = SoundManager.getInstance();
        if (sm != null) {
            sm.playBookFlipSound();
        }
    }

    private void returnToShelter() {
        playPageSound();
        game.setScreen(parentScreen);
        dispose();
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.04f, 0.03f, 0.02f, 1f);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        UiContexts.pop(CONTEXT);
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (hudSkin != null) hudSkin.dispose();
        if (mysteryTexture != null) {
            mysteryTexture.dispose();
            mysteryTexture = null;
        }
    }
}
