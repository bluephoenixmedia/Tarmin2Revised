package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.ui.UiGlyphs;
import com.bpm.minotaur.ui.UiTheme;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Tarmin's Knell on screen (plan K5): his words, one line after another, in red over the middle of
 * the view, with a faint red bleed at its edges -- the voice of the Antlered Lord inside the seeker's
 * head. It never takes input and never pauses play. The gong is the caller's to sound.
 */
public final class KnellOverlay extends Table implements Disposable {

    static final float FADE_IN = 0.6f;
    static final float HOLD = 4.5f;
    static final float FADE_OUT = 0.9f;
    static final float GAP = 0.3f;

    private final Label line;
    /** The same words in near-black, a vu down and right: red over a dark scene must still read. */
    private final Label shadow;
    /** Both, faded together. */
    private final Table words;
    private final com.badlogic.gdx.scenes.scene2d.ui.Cell<Table> wordsCell;
    private final com.badlogic.gdx.graphics.g2d.BitmapFont font;
    private final Image vignette;
    private final Texture vignetteTexture;
    private final Deque<String> queue = new ArrayDeque<>();
    private boolean speaking;
    private Runnable onSilence;

    public KnellOverlay(HudSkin skin) {
        setFillParent(true);
        setTouchable(Touchable.disabled);

        vignetteTexture = new Texture(redBleed(256));
        vignetteTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        vignette = new Image(vignetteTexture);
        vignette.setFillParent(true);
        vignette.getColor().a = 0f;
        addActor(vignette);

        font = skin.getFontDisplay();
        line = new Label("", new Label.LabelStyle(font, UiTheme.KNELL_RED));
        line.setWrap(true);
        line.setAlignment(Align.center);
        shadow = new Label("", new Label.LabelStyle(font, UiTheme.KNELL_SHADOW));
        shadow.setWrap(true);
        shadow.setAlignment(Align.center);
        com.badlogic.gdx.scenes.scene2d.ui.Stack stack = new com.badlogic.gdx.scenes.scene2d.ui.Stack();
        Table back = new Table();
        back.add(shadow).width(UiTheme.KNELL_TEXT_W).padLeft(UiTheme.VU).padTop(UiTheme.VU);
        Table front = new Table();
        front.add(line).width(UiTheme.KNELL_TEXT_W).padRight(UiTheme.VU).padBottom(UiTheme.VU);
        stack.add(back);
        stack.add(front);
        words = new Table();
        words.add(stack);
        words.getColor().a = 0f;
        wordsCell = add(words).width(UiTheme.KNELL_TEXT_W + UiTheme.VU).expand().center();
    }

    /** The line on screen now, or empty. */
    public String currentLine() {
        return line.getText().toString();
    }

    /** Whether Tarmin is still speaking. */
    public boolean isSpeaking() {
        return speaking;
    }

    /**
     * Tarmin speaks {@code lines}, after anything he is still saying. {@code withVignette}: the red
     * bleed rides with the words (a setting). {@code onSilence} runs when he falls quiet.
     */
    public void toll(List<String> lines, boolean withVignette, Runnable onSilence) {
        if (lines == null || lines.isEmpty()) return;
        for (String s : lines) queue.add(UiGlyphs.sanitize(s));
        this.onSilence = onSilence;
        if (withVignette) {
            vignette.clearActions();
            vignette.addAction(Actions.fadeIn(FADE_IN));
        }
        if (!speaking) {
            speaking = true;
            next();
        }
    }

    private void next() {
        String s = queue.poll();
        if (s == null) {
            speaking = false;
            vignette.clearActions();
            vignette.addAction(Actions.fadeOut(FADE_OUT));
            if (onSilence != null) onSilence.run();
            return;
        }
        line.setText(s);
        shadow.setText(s);
        // Wrapped text is as tall as its lines at this width; measure it so none is cut off.
        com.badlogic.gdx.graphics.g2d.GlyphLayout measured = new com.badlogic.gdx.graphics.g2d.GlyphLayout(
                font, s, UiTheme.KNELL_RED, UiTheme.KNELL_TEXT_W, Align.center, true);
        wordsCell.height(measured.height + font.getLineHeight() + UiTheme.VU);
        invalidateHierarchy();
        words.clearActions();
        words.getColor().a = 0f;
        words.addAction(Actions.sequence(Actions.fadeIn(FADE_IN), Actions.delay(HOLD), Actions.fadeOut(FADE_OUT),
                Actions.delay(GAP), Actions.run(this::next)));
    }

    /** A square of red, clear in the middle and bleeding in from the edges. */
    private static Pixmap redBleed(int size) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        Color red = UiTheme.KNELL_RED;
        float half = size / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - half) / half;
                float dy = (y + 0.5f - half) / half;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / (float) Math.sqrt(2.0);
                float edge = Math.max(0f, (d - 0.35f) / 0.65f);
                float a = (float) Math.pow(edge, 1.6) * UiTheme.KNELL_VIGNETTE_ALPHA;
                p.setColor(red.r, red.g, red.b, a);
                p.drawPixel(x, y);
            }
        }
        return p;
    }

    @Override
    public void dispose() {
        vignetteTexture.dispose();
    }
}
