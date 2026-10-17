package com.bpm.minotaur.rendering.gate;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.paperdoll.calibration.AssetDataFiles;

/**
 * Ctrl+F11: lines up the Sector Passage Gate texture by hand, live, against the real gate.
 *
 * <p>Stand in front of a gate, open this and nudge: the three parts (frame, left door, right door)
 * are mapped separately. Edits show on the next frame; ctrl+S writes {@code data/gate_uv.json}.
 * While open it takes the arrows, so they tune instead of walking, and passes every other key on.
 */
public class GateUvTunerPanel extends InputAdapter {

    private static final float NUDGE = 0.005f;
    private static final float NUDGE_FAST = 0.05f;
    private static final float SCALE_STEP = 1.02f;
    private static final float SCALE_STEP_FAST = 1.15f;
    private static final float ROTATE_STEP = 1f;
    private static final float ROTATE_STEP_FAST = 90f;

    private final GateUv uv;
    private final Runnable onChange;

    private boolean open;
    private boolean dirty;
    private int selected;
    private String status = "";
    private Color statusColor = Color.WHITE;

    public GateUvTunerPanel(GateUv uv, Runnable onChange) {
        this.uv = uv;
        this.onChange = onChange;
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        open = true;
        status = "";
    }

    private GateUv.Part part() {
        return uv.part(GateUv.PARTS[selected]);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (!open) {
            return false;
        }
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        float step = shift ? NUDGE_FAST : NUDGE;
        GateUv.Part p = part();

        switch (keycode) {
            case Input.Keys.LEFT:   p.offsetU -= step; break;
            case Input.Keys.RIGHT:  p.offsetU += step; break;
            case Input.Keys.UP:     p.offsetV += step; break;
            case Input.Keys.DOWN:   p.offsetV -= step; break;
            case Input.Keys.EQUALS:
            case Input.Keys.PLUS: {
                float k = shift ? SCALE_STEP_FAST : SCALE_STEP;
                p.scaleU *= k;
                p.scaleV *= k;
                break;
            }
            case Input.Keys.MINUS: {
                float k = shift ? SCALE_STEP_FAST : SCALE_STEP;
                p.scaleU /= k;
                p.scaleV /= k;
                break;
            }
            case Input.Keys.U:      p.scaleU *= shift ? 1f / SCALE_STEP : SCALE_STEP; break;
            case Input.Keys.V:      p.scaleV *= shift ? 1f / SCALE_STEP : SCALE_STEP; break;
            case Input.Keys.COMMA:  p.rotation += shift ? ROTATE_STEP_FAST : ROTATE_STEP; break;
            case Input.Keys.PERIOD: p.rotation -= shift ? ROTATE_STEP_FAST : ROTATE_STEP; break;
            case Input.Keys.F:      p.flipV = !p.flipV; break;
            case Input.Keys.BACKSPACE:
            case Input.Keys.FORWARD_DEL:
                p.reset();
                break;
            case Input.Keys.LEFT_BRACKET:
                selected = (selected + GateUv.PARTS.length - 1) % GateUv.PARTS.length;
                return true;
            case Input.Keys.RIGHT_BRACKET:
                selected = (selected + 1) % GateUv.PARTS.length;
                return true;
            case Input.Keys.S:
                if (!ctrl) {
                    return false;
                }
                save();
                return true;
            case Input.Keys.F11:
                if (ctrl) {
                    open = false;
                    return true;
                }
                return false;
            default:
                return false;
        }
        dirty = true;
        onChange.run();
        return true;
    }

    private void save() {
        FileHandle handle = AssetDataFiles.writable(GateUv.PATH);
        if (handle == null) {
            status = "SAVE FAILED - no writable assets/data path";
            statusColor = Color.RED;
            return;
        }
        handle.writeString(uv.toJson(), false, "UTF-8");
        dirty = false;
        status = "saved " + GateUv.PATH;
        statusColor = Color.GREEN;
    }

    /** Draws the readout in the top-left. Expects the batch to be begun, in UI space. */
    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport) {
        if (!open) {
            return;
        }
        float oldScaleX = font.getData().scaleX;
        float oldScaleY = font.getData().scaleY;
        Color oldColor = new Color(font.getColor());
        font.getData().setScale(1.4f);

        float x = 24f;
        float y = viewport.getWorldHeight() - 110f;
        float line = font.getLineHeight() * 1.15f;

        font.setColor(Color.GOLD);
        font.draw(batch, "GATE UV TUNER" + (dirty ? "   * UNSAVED *" : ""), x, y);
        y -= line;

        GateUv.Part p = part();
        font.setColor(Color.WHITE);
        font.draw(batch, "Part: " + GateUv.PARTS[selected] + "  ([ ] to change)", x, y);
        y -= line;
        font.draw(batch, String.format("u %+.3f   v %+.3f   scale u %.3f v %.3f   rot %+.0f   flipV %s",
                p.offsetU, p.offsetV, p.scaleU, p.scaleV, p.rotation, p.flipV ? "ON" : "off"), x, y);
        y -= line;

        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "arrows move texture   +/- scale   U/V stretch (shift = shrink)", x, y);
        y -= line;
        font.draw(batch, ", . rotate (shift = 90)   F flip V   Bksp reset part   shift = big steps", x, y);
        y -= line;
        font.draw(batch, "ctrl+S save   ctrl+F11 close", x, y);
        y -= line;

        if (!status.isEmpty()) {
            font.setColor(statusColor);
            font.draw(batch, status, x, y);
        }
        font.getData().setScale(oldScaleX, oldScaleY);
        font.setColor(oldColor);
    }

    /** Reads the saved mapping, or the defaults when there is no file. */
    public static GateUv loadSaved() {
        try {
            FileHandle handle = AssetDataFiles.readable(GateUv.PATH);
            return handle == null ? new GateUv() : GateUv.parse(new JsonReader().parse(handle));
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("GateUv", "Cannot read " + GateUv.PATH + "; using defaults", e);
            }
            return new GateUv();
        }
    }
}
