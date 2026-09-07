package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;

/**
 * Informational card and interaction modal displayed in the viewport when
 * facing interactive world props (Shelter Chest, Bedroll, Crafting Bench, Cooking Fire,
 * Doors, Gates, Containers, Corpses, Ladders, and Ground Items).
 * Supports both keyboard shortcuts and direct mouse clicks.
 */
public class WorldInteractionCard extends Table {

    private final HudSkin skin;
    private final Label tagLabel;
    private final Label subTagLabel;
    private final Label titleLabel;
    private final Label descLabel;
    private final Label keyBadgeLabel;
    private final Label actionTextLabel;
    private final Label hintLabel;

    private Runnable onAction;

    public WorldInteractionCard(HudSkin skin) {
        this.skin = skin;
        setBackground(skin.getTooltipBg());
        pad(10f, 20f, 10f, 20f);
        setTouchable(Touchable.enabled);

        Label.LabelStyle tagStyle = new Label.LabelStyle(skin.getFontSmall(), HudSkin.COL_EXP_AMBER);
        Label.LabelStyle subTagStyle = new Label.LabelStyle(skin.getFontSmall(), HudSkin.COL_GOLD_BRIGHT);
        Label.LabelStyle titleStyle = new Label.LabelStyle(skin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT);
        Label.LabelStyle descStyle = new Label.LabelStyle(skin.getFontSmall(), Color.valueOf("E0E8E8"));
        Label.LabelStyle keyStyle = new Label.LabelStyle(skin.getFontHeader(), Color.valueOf("FFE680"));
        Label.LabelStyle actionStyle = new Label.LabelStyle(skin.getFontMain(), Color.WHITE);
        Label.LabelStyle hintStyle = new Label.LabelStyle(skin.getFontSmall(), HudSkin.COL_GOLD_MUTED);

        tagLabel = new Label("", tagStyle);
        subTagLabel = new Label("", subTagStyle);
        titleLabel = new Label("", titleStyle);
        descLabel = new Label("", descStyle);
        descLabel.setAlignment(Align.left);
        descLabel.setWrap(true);

        keyBadgeLabel = new Label("", keyStyle);
        actionTextLabel = new Label("", actionStyle);
        hintLabel = new Label("(Click or Press Key)", hintStyle);

        // 1. Tag row
        Table tagTable = new Table();
        tagTable.add(tagLabel).left();
        tagTable.add(subTagLabel).left().padLeft(8f);
        tagTable.add().expandX();
        add(tagTable).fillX().left().row();

        // 2. Title row
        add(titleLabel).left().padTop(3f).row();

        // 3. Description row
        add(descLabel).width(580f).left().padTop(4f).row();

        // 4. Action footer row
        Table actionTable = new Table();
        actionTable.left();
        actionTable.add(keyBadgeLabel).left();
        actionTable.add(actionTextLabel).left().padLeft(8f);
        actionTable.add(hintLabel).left().padLeft(14f);
        actionTable.add().expandX();
        add(actionTable).fillX().left().padTop(8f).row();

        // Click listener for direct mouse interaction
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (onAction != null) {
                    onAction.run();
                }
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                actionTextLabel.setColor(Color.valueOf("FFE680"));
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                actionTextLabel.setColor(Color.WHITE);
            }
        });

        setVisible(false);
    }

    public void show(String tag, String subTag, String title, String description,
                     String keyBadge, String actionText, Runnable onAction) {
        this.tagLabel.setText(tag != null ? tag : "");
        this.subTagLabel.setText(subTag != null ? subTag : "");
        this.titleLabel.setText(title != null ? title : "");
        this.descLabel.setText(description != null ? description : "");
        this.keyBadgeLabel.setText(keyBadge != null ? keyBadge : "");
        this.actionTextLabel.setText(actionText != null ? actionText : "");
        this.onAction = onAction;

        pack();

        // Center horizontally in virtual 1920 viewport, sit at Y=208f (above 200px HUD)
        float posX = (1920f - getWidth()) / 2f;
        float posY = 208f;
        setPosition(posX, posY);
        toFront();
        setVisible(true);
    }

    public void hide() {
        if (isVisible()) {
            setVisible(false);
            onAction = null;
        }
    }
}
