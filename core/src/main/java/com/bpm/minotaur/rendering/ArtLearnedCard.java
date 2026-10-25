package com.bpm.minotaur.rendering;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.bpm.minotaur.gamedata.arts.CombatArt;
import com.bpm.minotaur.ui.MasteryText;
import com.bpm.minotaur.ui.UiLabels;
import com.bpm.minotaur.ui.UiNames;
import com.bpm.minotaur.ui.UiStyles;
import com.bpm.minotaur.ui.UiTheme;

/**
 * The card that announces a Combat Art the soul learns for the first time (#249; spec section 6),
 * in the style of the {@link DiscoveryCard}: the carved double border, the name in gold, then its
 * Family, rank and kind, and its effect in one line. A kept Art retrained by a later body gets a
 * log line instead ("Your arm remembers Ringing Blow."), not a card.
 */
public class ArtLearnedCard extends Table {

    public ArtLearnedCard(HudSkin skin, CombatArt art) {
        setBackground(skin.getDoubleBorderPanel());
        pad(UiTheme.PAD_MD, UiTheme.PAD_XL, UiTheme.PAD_MD, UiTheme.PAD_XL);
        float inner = UiTheme.ART_CARD_W - 2 * UiTheme.PAD_XL;

        Label kicker = UiLabels.ellipsized("COMBAT ART LEARNED", UiStyles.caption(skin, HudSkin.COL_GOLD_MUTED));
        kicker.setAlignment(Align.center);
        add(kicker).width(inner).row();

        Label name = UiLabels.wrapping(UiNames.of(art), new Label.LabelStyle(skin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        name.setAlignment(Align.center);
        add(name).width(inner).padTop(UiTheme.PAD_SM).row();

        Label subtitle = UiLabels.ellipsized(MasteryText.cardSubtitle(art), UiStyles.caption(skin, HudSkin.COL_GOLD_MUTED));
        subtitle.setAlignment(Align.center);
        add(subtitle).width(inner).padTop(UiTheme.PAD_XS).row();

        Label effect = UiLabels.wrapping(art.describe(), UiStyles.body(skin, UiTheme.TEXT));
        effect.setAlignment(Align.center);
        add(effect).width(inner).padTop(UiTheme.PAD_MD).row();
        pack();
    }
}
