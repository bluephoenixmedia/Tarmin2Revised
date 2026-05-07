package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen debug overlay for the inventory UI.  Press F3 to toggle.
 *
 * PANEL DRAGGING
 *   While the overlay is visible, click and drag any highlighted panel to
 *   reposition it.  The title label above it updates in real time to show
 *   the new (x, y).  Click [SAVE LAYOUT] in the bottom-right corner to
 *   write inventory_layout.txt — the file contains ready-to-paste
 *   setPosition() calls you can copy into ModernInventoryUI.java.
 *
 * TEXT RENDERING NOTES
 *   All text is rendered through scene2d Label actors (children of this
 *   WidgetGroup) so the SpriteBatch state is managed by the normal draw
 *   cycle.  ShapeRenderer is used only for the shapes pass.
 *
 * LAYOUT
 *   - Coloured bounding box + draggable title above each registered panel.
 *   - Crosshair (+) at every InventorySlot child.
 *   - Yellow guide lines at X=960 (spine) and Y=540 (midpoint).
 *   - Stage corner coordinate stamps.
 *   - Hint bar and [SAVE LAYOUT] button at the bottom.
 */
public class InventoryDebugOverlay extends WidgetGroup implements Disposable {

    // ── Internal data ─────────────────────────────────────────────────

    private static final class Entry {
        final Actor   actor;
        final String  name;
        final Color   color;
        /** false = skip bounding box / drag; show slot crosshairs only (e.g. PaperDollPanel). */
        final boolean showBounds;

        // Two Label actors per panel title: shadow (dark, offset) + main (colour-tinted).
        // Repositioned every frame in updateLabels() so they track the actor during drag.
        final Label shadowLabel;
        final Label mainLabel;

        Entry(Actor a, String n, Color c, boolean sb,
              Label.LabelStyle shadowStyle, Label.LabelStyle mainStyle) {
            actor       = a;
            name        = n;
            color       = new Color(c);
            showBounds  = sb;
            shadowLabel = new Label("", shadowStyle);
            mainLabel   = new Label("", mainStyle);
        }
    }

    private final List<Entry>     entries     = new ArrayList<>();
    private final ShapeRenderer   shapes      = new ShapeRenderer();
    private final Label.LabelStyle shadowStyle;
    private final Label.LabelStyle mainStyle;

    // ── Fixed labels ──────────────────────────────────────────────────
    private final Label cornerBL, cornerBR, cornerTL, cornerTR;
    private final Label spineLbl, midLbl, hintLbl, saveBtnLabel;

    // ── Drag state ────────────────────────────────────────────────────
    private Entry dragging;
    private float dragDX, dragDY; // cursor-to-actor-origin offset at drag start

    // ── Save callback + feedback ──────────────────────────────────────
    private Runnable saveCallback;
    private float    saveFeedbackTimer  = 0f;
    private static final float FEEDBACK = 2f; // seconds to show "SAVED!"

    // ── Construction ─────────────────────────────────────────────────

    /**
     * @param font  BitmapFont used for all labels (borrowed — not disposed here).
     */
    public InventoryDebugOverlay(BitmapFont font) {
        shadowStyle = new Label.LabelStyle(font, new Color(0f, 0f, 0f, 0.90f));
        mainStyle   = new Label.LabelStyle(font, Color.WHITE);

        Label.LabelStyle guideStyle = new Label.LabelStyle(font, new Color(1f, 1f, 0.3f, 0.75f));
        Label.LabelStyle hintStyle  = new Label.LabelStyle(font, new Color(1f, 1f, 1f, 0.65f));
        Label.LabelStyle saveStyle  = new Label.LabelStyle(font, Color.LIME);

        cornerBL     = fixed("(0, 0)",       guideStyle, 4f,    13f);
        cornerBR     = fixed("(1920, 0)",    guideStyle, 1820f, 13f);
        cornerTL     = fixed("(0, 1080)",    guideStyle, 4f,    1065f);
        cornerTR     = fixed("(1920, 1080)", guideStyle, 1760f, 1065f);
        spineLbl     = fixed("x=960",        guideStyle, 963f,  1065f);
        midLbl       = fixed("y=540",        guideStyle, 2f,    544f);
        hintLbl      = fixed("[ F3 ]  toggle  |  drag panel to move  |  click SAVE LAYOUT",
                             hintStyle, 10f, 24f);
        saveBtnLabel = fixed("[ SAVE LAYOUT ]", saveStyle, 1750f, 5f);

        setSize(1920f, 1080f);
        setPosition(0f, 0f);

        // Input: drag panels and handle save button clicks.
        // hit() returns 'this' for the whole screen when visible, so this
        // listener captures all mouse events (blocking the panels beneath).
        addListener(new InputListener() {

            @Override
            public boolean touchDown(InputEvent event, float x, float y,
                                     int pointer, int button) {
                if (button != Input.Buttons.LEFT) return false;

                // Save button — highest priority.
                if (over(saveBtnLabel, x, y)) {
                    triggerSave();
                    return true;
                }

                // Panel drag.
                for (Entry e : entries) {
                    if (!e.showBounds) continue;
                    if (over(e.actor, x, y)) {
                        dragging = e;
                        dragDX   = e.actor.getX() - x;
                        dragDY   = e.actor.getY() - y;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (dragging == null) return;
                dragging.actor.setPosition(x + dragDX, y + dragDY);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y,
                                int pointer, int button) {
                dragging = null;
            }
        });
    }

    /**
     * Sets the callback invoked when the user clicks [SAVE LAYOUT].
     * The callback should collect current panel positions and write them to disk.
     */
    public void setSaveCallback(Runnable cb) {
        this.saveCallback = cb;
    }

    // ── Registration API ─────────────────────────────────────────────

    /**
     * Register a panel to outline when the overlay is visible.
     *
     * @param actor      Panel actor (must already have size/position set).
     * @param label      Name shown above the bounding box.
     * @param color      Outline and slot-crosshair colour.
     * @param showBounds true  = draw bounding rectangle and allow dragging;
     *                   false = only show slot crosshairs (use for full-screen panels).
     */
    public void track(Actor actor, String label, Color color, boolean showBounds) {
        Label.LabelStyle tinted = new Label.LabelStyle(mainStyle.font, new Color(color));
        Entry e = new Entry(actor, label, color, showBounds, shadowStyle, tinted);
        entries.add(e);
        addActor(e.shadowLabel);
        addActor(e.mainLabel);
    }

    /** Convenience — always shows the bounding rectangle and allows dragging. */
    public void track(Actor actor, String label, Color color) {
        track(actor, label, color, true);
    }

    // ── hit() override ────────────────────────────────────────────────

    /**
     * Returns {@code this} for any cursor position when visible so the
     * InputListener above captures all mouse events (panels beneath are blocked,
     * which is intentional — debug mode is for layout, not gameplay).
     * Returns {@code null} when hidden so normal interaction is unaffected.
     */
    @Override
    public Actor hit(float x, float y, boolean touchable) {
        return isVisible() ? this : null;
    }

    // ── Rendering ─────────────────────────────────────────────────────

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (!isVisible()) return;

        // Tick save-feedback; revert button text when it expires.
        if (saveFeedbackTimer > 0f) {
            saveFeedbackTimer -= Gdx.graphics.getDeltaTime();
            if (saveFeedbackTimer <= 0f) {
                saveBtnLabel.setText("[ SAVE LAYOUT ]");
                saveBtnLabel.setStyle(new Label.LabelStyle(mainStyle.font, Color.LIME));
                saveBtnLabel.pack();
                saveBtnLabel.setPosition(1750f, 5f);
            }
        }

        // ── PASS 1: Shapes (ShapeRenderer) ───────────────────────────
        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(batch.getProjectionMatrix());
        shapes.setTransformMatrix(batch.getTransformMatrix());

        // Semi-transparent fill — brighter while a panel is being dragged.
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Entry e : entries) {
            if (!e.showBounds) continue;
            shapes.setColor(e.color.r, e.color.g, e.color.b, e == dragging ? 0.22f : 0.07f);
            shapes.rect(e.actor.getX(), e.actor.getY(),
                        e.actor.getWidth(), e.actor.getHeight());
        }
        shapes.setColor(1f, 1f, 1f, 0.03f);
        shapes.rect(0, 0, 960, 1080);
        shapes.end();

        // Outlines, guide lines, slot crosshairs.
        shapes.begin(ShapeRenderer.ShapeType.Line);

        shapes.setColor(1f, 1f, 0.2f, 0.45f);
        shapes.line(960, 0,    960, 1080);
        shapes.line(0,   540, 1920, 540);

        shapes.setColor(1f, 1f, 1f, 0.55f);
        cross(shapes, 0f,    0f,    12f);
        cross(shapes, 1920f, 0f,    12f);
        cross(shapes, 0f,    1080f, 12f);
        cross(shapes, 1920f, 1080f, 12f);

        for (Entry e : entries) {
            if (e.showBounds) {
                boolean active = (e == dragging);
                shapes.setColor(e.color.r, e.color.g, e.color.b, active ? 1f : 0.85f);
                if (active) {
                    // Extra outer rect to visually confirm the panel is selected.
                    shapes.rect(e.actor.getX() - 2, e.actor.getY() - 2,
                                e.actor.getWidth() + 4, e.actor.getHeight() + 4);
                }
                shapes.rect(e.actor.getX(), e.actor.getY(),
                            e.actor.getWidth(), e.actor.getHeight());
            }

            if (!(e.actor instanceof Group)) continue;
            Array<Actor> children = ((Group) e.actor).getChildren();
            for (int i = 0; i < children.size; i++) {
                Actor child = children.get(i);
                if (!(child instanceof InventorySlot)) continue;

                float cx = e.actor.getX() + child.getX() + child.getWidth()  / 2f;
                float cy = e.actor.getY() + child.getY() + child.getHeight() / 2f;

                shapes.setColor(e.color.r, e.color.g, e.color.b, 0.30f);
                shapes.rect(e.actor.getX() + child.getX(),
                            e.actor.getY() + child.getY(),
                            child.getWidth(), child.getHeight());

                shapes.setColor(e.color.r, e.color.g, e.color.b, 0.95f);
                cross(shapes, cx, cy, 8f);
            }
        }
        shapes.end();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();

        // ── PASS 2: Text (scene2d Labels) ────────────────────────────
        updateLabels();
        super.draw(batch, parentAlpha);
    }

    /**
     * Repositions and re-texts every dynamic label to match the current actor state.
     * Called once per frame from draw() before super.draw() renders the children.
     */
    private void updateLabels() {
        for (Entry e : entries) {
            if (!e.showBounds) {
                e.shadowLabel.setVisible(false);
                e.mainLabel.setVisible(false);
                continue;
            }

            String text = e.name
                    + "  (" + (int) e.actor.getX() + ", " + (int) e.actor.getY() + ")"
                    + "  " + (int) e.actor.getWidth() + "×" + (int) e.actor.getHeight();

            float lx = e.actor.getX() + 4f;
            float ly = e.actor.getY() + e.actor.getHeight() + 14f;

            e.shadowLabel.setText(text);
            e.shadowLabel.pack();
            e.shadowLabel.setPosition(lx + 1f, ly - 1f);
            e.shadowLabel.setVisible(true);

            e.mainLabel.setText(text);
            e.mainLabel.pack();
            e.mainLabel.setPosition(lx, ly);
            e.mainLabel.setVisible(true);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /** True if stage-space point (x, y) lies inside actor a's bounding box. */
    private static boolean over(Actor a, float x, float y) {
        return x >= a.getX() && x <= a.getX() + a.getWidth()
            && y >= a.getY() && y <= a.getY() + a.getHeight();
    }

    private void triggerSave() {
        if (saveCallback != null) saveCallback.run();
        saveBtnLabel.setText("[ SAVED! ]");
        saveBtnLabel.setStyle(new Label.LabelStyle(mainStyle.font, Color.WHITE));
        saveBtnLabel.pack();
        saveBtnLabel.setPosition(1750f, 5f);
        saveFeedbackTimer = FEEDBACK;
    }

    /** Creates a Label, adds it as a child, and returns it. */
    private Label fixed(String text, Label.LabelStyle style, float x, float y) {
        Label lbl = new Label(text, style);
        lbl.setPosition(x, y);
        lbl.pack();
        addActor(lbl);
        return lbl;
    }

    /** Draws a + crosshair centred at (x, y) with arm length {@code arm}. */
    private static void cross(ShapeRenderer sr, float x, float y, float arm) {
        sr.line(x - arm, y, x + arm, y);
        sr.line(x, y - arm, x, y + arm);
    }

    // ── Cleanup ───────────────────────────────────────────────────────

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
