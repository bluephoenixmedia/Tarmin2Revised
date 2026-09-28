package com.bpm.minotaur.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.bpm.minotaur.rendering.HudSkin;

/**
 * A destructive action that has to be held down, with a fill that shows how far along the hold
 * is (SPEC section 4, 5.7).
 *
 * <p>For actions that cannot be taken back and that a single mis-click would otherwise complete.
 * The altar's sacrifice tab is the case that named this: eleven identical gold buttons, one of
 * which fed your equipped sword to the altar, in one click, with no confirmation.
 *
 * <p>Releasing early cancels and the fill resets -- there is no partial state. A dialog would do
 * the same job, but a dialog for every row turns a list into a sequence of interruptions, and the
 * hold is its own explanation.
 */
public class HoldButton extends TextButton {

    private final HudSkin skin;
    private final Runnable action;
    private final float holdSeconds;

    private float held;
    private boolean pressing;
    private boolean fired;

    public HoldButton(String text, HudSkin skin, Runnable action) {
        this(text, skin, action, UiTheme.T_HOLD_CONFIRM);
    }

    public HoldButton(String text, HudSkin skin, Runnable action, float holdSeconds) {
        super(UiGlyphs.sanitize(text), UiStyles.danger(skin));
        this.skin = skin;
        this.action = action;
        this.holdSeconds = holdSeconds;

        addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                beginHold();
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                cancelHold();
            }
        });
    }

    /** Starts the hold. Also called by the screen when the confirm key goes down. */
    public void beginHold() {
        if (isDisabled()) {
            return;
        }
        pressing = true;
        fired = false;
    }

    /** Abandons the hold. Also called by the screen when the confirm key comes up. */
    public void cancelHold() {
        pressing = false;
        held = 0f;
    }

    /** 0 at rest, 1 at the moment the action fires. */
    public float progress() {
        return holdSeconds <= 0f ? 1f : Math.min(1f, held / holdSeconds);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (!pressing || fired || isDisabled()) {
            return;
        }
        held += delta;
        if (held >= holdSeconds) {
            fired = true;
            pressing = false;
            held = 0f;
            if (action != null) {
                action.run();
            }
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);

        float p = progress();
        if (p <= 0f || !pressing) {
            return;
        }
        // The fill sits under the label rather than over it, so the verb stays readable all the
        // way to the end of the hold.
        batch.setColor(UiTheme.DANGER.r, UiTheme.DANGER.g, UiTheme.DANGER.b, 0.45f * parentAlpha);
        skin.getWhitePixelDrawable().draw(batch, getX(), getY(), getWidth() * p, getHeight());
        batch.setColor(1f, 1f, 1f, 1f);
    }
}
