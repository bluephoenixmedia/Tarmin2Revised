package com.bpm.minotaur.ui;

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * A scroll pane built without a style draws no bar at all, so the list scrolls and nothing says it
 * can. The shared style must always carry all four bar drawables at the standard thickness.
 */
public class ScrollPaneStyleTest {

    @Test
    public void everyBarDrawableIsPresentAtTheStandardThickness() {
        BaseDrawable track = new BaseDrawable();
        BaseDrawable knob = new BaseDrawable();

        ScrollPane.ScrollPaneStyle style = UiStyles.scrollPane(track, knob);

        assertNotNull("vertical track", style.vScroll);
        assertNotNull("vertical knob", style.vScrollKnob);
        assertNotNull("horizontal track", style.hScroll);
        assertNotNull("horizontal knob", style.hScrollKnob);
        assertEquals(UiTheme.SCROLL_W, style.vScroll.getMinWidth(), 0.001f);
        assertEquals(UiTheme.SCROLL_W, style.vScrollKnob.getMinWidth(), 0.001f);
        assertEquals(UiTheme.SCROLL_W, style.hScroll.getMinHeight(), 0.001f);
        assertEquals(UiTheme.SCROLL_W, style.hScrollKnob.getMinHeight(), 0.001f);
    }

    @Test
    public void theBarIsASeparateCopySoTheSkinsOwnDrawableIsUntouched() {
        BaseDrawable shared = new BaseDrawable();
        shared.setMinWidth(99f);

        UiStyles.scrollPane(shared, shared);

        assertEquals("the skin's drawable must keep its own size", 99f, shared.getMinWidth(), 0.001f);
    }
}
