package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.bpm.minotaur.gamedata.player.Player;

/**
 * Antique bronze compass dial displaying the player's cardinal orientation
 * with glowing cardinal heading text and orientation tick marks.
 */
public class CompassMedallion extends Actor {

    private final HudSkin skin;
    private final Player player;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();

    public CompassMedallion(HudSkin skin, Player player) {
        this.skin = skin;
        this.player = player;
        this.font = skin.getFontCompass();
        setSize(44f, 44f);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();

        // 1. Draw Dial Texture
        batch.setColor(1f, 1f, 1f, parentAlpha);
        if (skin.getCompassDial() != null) {
            batch.draw(skin.getCompassDial(), x, y, w, h);
        }

        // 2. Draw Cardinal Heading Letter in Center
        String heading = "N";
        if (player != null && player.getFacing() != null) {
            heading = player.getFacing().name().substring(0, 1);
        }

        layout.setText(font, heading);
        float textX = x + (w - layout.width) / 2f;
        float textY = y + (h + layout.height) / 2f - 1f;

        // Shadow
        font.setColor(0f, 0f, 0f, 0.9f * parentAlpha);
        font.draw(batch, heading, textX + 1f, textY - 1f);

        // Gold Foreground
        font.setColor(HudSkin.COL_GOLD_BRIGHT);
        font.draw(batch, heading, textX, textY);
    }
}
