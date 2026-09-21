package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;

/**
 * A single discovery, shown as a trophy on the death screen.
 *
 * <p>Drawn entirely from {@link HudSkin}'s procedural drawables and a couple of primitives rather
 * than from a painted background, matching the rest of the UI -- which means it re-tints for free
 * if the palette changes, and the border can actually move.
 *
 * <p>The blood drips once and settles rather than looping. There is no time limit on this screen,
 * so a permanent animation stops being a flourish and becomes a distraction.
 */
public class DiscoveryCard extends Table {

    public static final float CARD_WIDTH = 260f;
    public static final float CARD_HEIGHT = 300f;

    private static final int DRIP_COUNT = 4;
    private static final float DRIP_DURATION = 1.2f;
    private static final float DRIP_MAX_LENGTH = 46f;


    private final HudSkin skin;
    private final float[] dripX = new float[DRIP_COUNT];
    private final float[] dripDelay = new float[DRIP_COUNT];
    private final float[] dripLength = new float[DRIP_COUNT];
    private final float[] dripWidth = new float[DRIP_COUNT];

    private float age;

    /**
     * @param stagger seconds to hold this card's drips back, so a row of cards does not bleed in
     *                unison.
     */
    public DiscoveryCard(HudSkin skin, Item.ItemType type, String friendlyName,
                         ItemDataManager itemData, AssetManager assets, float stagger) {
        this.skin = skin;
        setBackground(skin.getDoubleBorderPanel());

        DiscoveryName name = DiscoveryName.of(friendlyName);

        TextureRegion icon = resolveIcon(type, itemData, assets);
        if (icon != null) {
            Image image = new Image(new TextureRegionDrawable(icon));
            image.setScaling(com.badlogic.gdx.utils.Scaling.fit);
            add(image).size(120f, 120f).padTop(22f).row();
        } else {
            add().size(120f, 120f).padTop(22f).row();
        }

        Label.LabelStyle headStyle = new Label.LabelStyle(skin.getFontMain(), HudSkin.COL_GOLD_BRIGHT);
        Label head = new Label(name.headline, headStyle);
        head.setWrap(true);
        head.setAlignment(com.badlogic.gdx.utils.Align.center);
        add(head).width(CARD_WIDTH - 40f).padTop(14f).row();

        if (!name.subtitle.isEmpty()) {
            Label.LabelStyle subStyle = new Label.LabelStyle(skin.getFontSmall(), HudSkin.COL_GOLD_MUTED);
            Label sub = new Label(name.subtitle, subStyle);
            sub.setWrap(true);
            sub.setAlignment(com.badlogic.gdx.utils.Align.center);
            add(sub).width(CARD_WIDTH - 40f).padTop(6f).row();
        }

        for (int i = 0; i < DRIP_COUNT; i++) {
            dripX[i] = MathUtils.random(0.12f, 0.88f) * CARD_WIDTH;
            dripDelay[i] = stagger + MathUtils.random(0f, 0.45f);
            dripLength[i] = MathUtils.random(0.45f, 1f) * DRIP_MAX_LENGTH;
            dripWidth[i] = MathUtils.random(2f, 4f);
        }
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        age += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);

        float a = getColor().a * parentAlpha;
        if (a <= 0.01f) return;

        com.badlogic.gdx.graphics.Texture pixel = skin.getWhitePixel();

        // Ember glow: a slow pulse along the border, so the card reads as lit rather than printed.
        float pulse = 0.55f + 0.45f * MathUtils.sin(age * 2.2f);
        batch.setColor(HudSkin.COL_EMBER_GLOW.r, HudSkin.COL_EMBER_GLOW.g,
                HudSkin.COL_EMBER_GLOW.b, a * 0.32f * pulse);
        float g = 2f;
        batch.draw(pixel, getX(), getY(), getWidth(), g);
        batch.draw(pixel, getX(), getY() + getHeight() - g, getWidth(), g);
        batch.draw(pixel, getX(), getY(), g, getHeight());
        batch.draw(pixel, getX() + getWidth() - g, getY(), g, getHeight());

        // Blood, running down from the top edge once and stopping.
        for (int i = 0; i < DRIP_COUNT; i++) {
            float t = (age - dripDelay[i]) / DRIP_DURATION;
            if (t <= 0f) continue;
            float eased = 1f - (1f - Math.min(t, 1f)) * (1f - Math.min(t, 1f));
            float len = dripLength[i] * eased;
            batch.setColor(HudSkin.COL_BLOOD.r, HudSkin.COL_BLOOD.g, HudSkin.COL_BLOOD.b, a * 0.9f);
            batch.draw(pixel,
                    getX() + dripX[i],
                    getY() + getHeight() - len,
                    dripWidth[i],
                    len);
            // The bead at the leading edge.
            batch.setColor(HudSkin.COL_BLOOD_BEAD.r, HudSkin.COL_BLOOD_BEAD.g,
                    HudSkin.COL_BLOOD_BEAD.b, a);
            batch.draw(pixel,
                    getX() + dripX[i] - 1f,
                    getY() + getHeight() - len,
                    dripWidth[i] + 2f,
                    3f);
        }

        batch.setColor(Color.WHITE);
    }

    /**
     * Finds an item's packed icon.
     *
     * <p>Mirrors the atlas convention used when instantiating a real item: the texture path names
     * both the atlas and, via its basename, the region.
     */
    private static TextureRegion resolveIcon(Item.ItemType type, ItemDataManager itemData,
                                             AssetManager assets) {
        if (type == null || itemData == null || assets == null) return null;
        if (Gdx.app != null && Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.HeadlessDesktop) {
            return null;
        }

        ItemTemplate template = itemData.getTemplate(type);
        if (template == null || template.texturePath == null || template.texturePath.isEmpty()) {
            return null;
        }

        String path = template.texturePath;
        String atlasPath = null;
        if (path.contains("images/weapons")) atlasPath = "packed/weapons.atlas";
        else if (path.contains("images/armor")) atlasPath = "packed/armor.atlas";
        else if (path.contains("images/items")) atlasPath = "packed/items.atlas";
        else if (path.contains("images/debris")) atlasPath = "packed/debris.atlas";

        if (atlasPath != null && assets.isLoaded(atlasPath)) {
            String region = path.substring(path.lastIndexOf('/') + 1);
            if (region.endsWith(".png")) {
                region = region.substring(0, region.length() - 4);
            }
            TextureAtlas atlas = assets.get(atlasPath, TextureAtlas.class);
            TextureRegion found = atlas.findRegion(region);
            if (found != null) {
                return found;
            }
        }

        // Most weapons are never packed -- weapons.atlas holds only 24 regions -- but
        // ItemDataManager force-loads every template's texture as a standalone Texture, so the
        // loose asset is the reliable source. Item resolves icons the same way.
        if (assets.isLoaded(path, com.badlogic.gdx.graphics.Texture.class)) {
            return new TextureRegion(assets.get(path, com.badlogic.gdx.graphics.Texture.class));
        }
        return null;
    }
}
