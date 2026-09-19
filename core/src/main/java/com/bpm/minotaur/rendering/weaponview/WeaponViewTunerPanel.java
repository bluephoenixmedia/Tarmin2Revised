package com.bpm.minotaur.rendering.weaponview;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.paperdoll.calibration.LayerCalibration;
import com.bpm.minotaur.rendering.FirstPersonWeaponOverlay;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * F11: tunes the first-person weapon live, in the game, against the real 3D view.
 *
 * Tuning happens here rather than in a separate editor screen because the question it
 * answers -- does this weapon sit right in the hand -- only has an answer against the
 * scene behind it, the bob, and the swing. Edits show on the next frame.
 *
 * Sits at the front of the input multiplexer while open and consumes only its own keys,
 * so the arrows tune instead of walking but everything else still reaches the game.
 * Keys follow the paperdoll editor where the two overlap, including [ and ] to walk
 * every weapon in turn. Walking only changes what is drawn, never the inventory, and
 * closing the tuner puts the real equipment back.
 */
public class WeaponViewTunerPanel extends InputAdapter {

    private static final float NUDGE = 0.005f;
    private static final float NUDGE_FAST = 0.025f;
    private static final float SCALE_STEP = 1.02f;
    private static final float SCALE_STEP_FAST = 1.10f;
    private static final float ROTATE_STEP = 1f;
    private static final float ROTATE_STEP_FAST = 10f;

    private final FirstPersonWeaponOverlay overlay;
    private final WeaponViewCalibration calibration;
    private final WeaponViewTuner tuner;
    private final ItemDataManager itemData;

    /** Built on first open, when item data is certain to be loaded. */
    private WeaponViewCycle mainCycle;
    private WeaponViewCycle offCycle;
    private final Map<String, Item> previewItems = new HashMap<String, Item>();

    private boolean open = false;
    /** Set by a first F11 or F5 over unsaved work; the second press goes through. */
    private boolean confirmingDiscard = false;
    private String status = "";
    private Color statusColor = Color.WHITE;

    public WeaponViewTunerPanel(FirstPersonWeaponOverlay overlay, ItemDataManager itemData) {
        this.overlay = overlay;
        this.itemData = itemData;
        this.calibration = overlay.getViewCalibration();
        this.tuner = new WeaponViewTuner(calibration.store());
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        buildCycles();
        open = true;
        confirmingDiscard = false;
        setStatus("", Color.WHITE);
    }

    /**
     * Closes the tuner, refusing the first time if there is unsaved work.
     *
     * @return true when the panel actually closed
     */
    public boolean requestClose() {
        if (tuner.isDirty() && !confirmingDiscard) {
            confirmingDiscard = true;
            setStatus("UNSAVED - ctrl+S to save, or F11 again to discard", Color.ORANGE);
            return false;
        }
        if (tuner.isDirty()) {
            // Edits live in the store the overlay draws from, so discarding has to put
            // the file's version back or they would stay on screen all session.
            reloadFromDisk();
        }
        open = false;
        confirmingDiscard = false;
        mainCycle.reset();
        offCycle.reset();
        applyPreview();
        return true;
    }

    /**
     * Main hand: every weapon, grouped by archetype so a pass tunes one family at a time
     * and the archetype default can be set on the first of each. Off hand: shields, then
     * every weapon again for dual-wielding. Only items with texture art are listed, as
     * tuning applies only to the textured view.
     */
    private void buildCycles() {
        if (mainCycle != null) {
            return;
        }
        List<Item> weapons = new ArrayList<Item>();
        List<Item> shields = new ArrayList<Item>();
        if (itemData != null) {
            for (Item.ItemType type : itemData.getLoadedTypes()) {
                ItemTemplate template = itemData.getTemplate(type);
                if (template == null || template.texturePath == null) {
                    continue;
                }
                Item item = Item.fromTemplate(type, template);
                if (template.isShield) {
                    shields.add(item);
                } else if (template.isWeapon) {
                    weapons.add(item);
                }
            }
        }
        Collections.sort(weapons, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                int byArchetype = AnimationArchetype.fromItem(a).compareTo(AnimationArchetype.fromItem(b));
                return byArchetype != 0 ? byArchetype : a.getTypeName().compareTo(b.getTypeName());
            }
        });

        List<String> mainKeys = new ArrayList<String>();
        for (Item item : weapons) {
            mainKeys.add(register(item));
        }
        List<String> offKeys = new ArrayList<String>();
        for (Item item : shields) {
            offKeys.add(register(item));
        }
        offKeys.addAll(mainKeys);
        mainCycle = new WeaponViewCycle(mainKeys);
        offCycle = new WeaponViewCycle(offKeys);
    }

    private String register(Item item) {
        String key = WeaponViewCalibration.itemKey(item);
        previewItems.put(key, item);
        return key;
    }

    private WeaponViewCycle selectedCycle() {
        return tuner.hand() == WeaponViewTuner.Hand.MAIN ? mainCycle : offCycle;
    }

    /**
     * What the selected hand shows. Only read when not yet previewing, when that is what
     * it really holds, so the first step lands next to the equipped weapon.
     */
    private String equippedKey() {
        return tuner.itemKey();
    }

    private void applyPreview() {
        overlay.setPreview(previewItems.get(mainCycle.current()), previewItems.get(offCycle.current()));
    }

    @Override
    public boolean keyDown(int keycode) {
        if (!open) {
            return false;
        }
        syncEquipment();

        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        float step = shift ? NUDGE_FAST : NUDGE;

        // F11 and F5 manage their own confirmation; anything else abandons one in progress.
        if (keycode != Input.Keys.F11 && keycode != Input.Keys.F5) {
            confirmingDiscard = false;
        }

        switch (keycode) {
            // Screen space: up raises the weapon, the same as it does in the paperdoll.
            case Input.Keys.LEFT:   tuner.nudge(-step, 0f); break;
            case Input.Keys.RIGHT:  tuner.nudge(step, 0f); break;
            case Input.Keys.UP:     tuner.nudge(0f, step); break;
            case Input.Keys.DOWN:   tuner.nudge(0f, -step); break;
            case Input.Keys.EQUALS:
            case Input.Keys.PLUS:
                tuner.scaleBy(shift ? SCALE_STEP_FAST : SCALE_STEP);
                break;
            case Input.Keys.MINUS:
                tuner.scaleBy(1f / (shift ? SCALE_STEP_FAST : SCALE_STEP));
                break;
            case Input.Keys.COMMA:  tuner.rotateBy(shift ? ROTATE_STEP_FAST : ROTATE_STEP); break;
            case Input.Keys.PERIOD: tuner.rotateBy(-(shift ? ROTATE_STEP_FAST : ROTATE_STEP)); break;
            case Input.Keys.F:      tuner.toggleFlip(); break;
            case Input.Keys.H:      tuner.toggleHand(); break;
            case Input.Keys.T:      tuner.toggleScope(); break;
            case Input.Keys.BACKSPACE:
            case Input.Keys.FORWARD_DEL:
                tuner.clear();
                break;
            case Input.Keys.P:      overlay.previewSwing(); break;
            case Input.Keys.LEFT_BRACKET:
                selectedCycle().previous(equippedKey());
                applyPreview();
                break;
            case Input.Keys.RIGHT_BRACKET:
                selectedCycle().next(equippedKey());
                applyPreview();
                break;
            case Input.Keys.E:
                selectedCycle().reset();
                applyPreview();
                break;
            case Input.Keys.S:
                if (!ctrl) {
                    return false;
                }
                save();
                break;
            case Input.Keys.F5:     reload(); break;
            case Input.Keys.F11:    requestClose(); break;
            default:
                return false;
        }
        return true;
    }

    private void syncEquipment() {
        tuner.setEquipment(overlay.getMainHandArchetype().name(),
                WeaponViewCalibration.itemKey(overlay.getMainHandItem()),
                WeaponViewCalibration.itemKey(overlay.getOffHandItem()));
    }

    private void save() {
        if (calibration.save()) {
            tuner.markSaved();
            setStatus("saved " + WeaponViewCalibration.PATH, Color.GREEN);
        } else {
            setStatus("SAVE FAILED - no writable assets/data path", Color.RED);
        }
    }

    private void reload() {
        if (tuner.isDirty() && !confirmingDiscard) {
            confirmingDiscard = true;
            setStatus("UNSAVED - F5 again to discard and re-read the file", Color.ORANGE);
            return;
        }
        confirmingDiscard = false;
        reloadFromDisk();
        setStatus("re-read " + WeaponViewCalibration.PATH, Color.WHITE);
    }

    private void reloadFromDisk() {
        calibration.load();
        tuner.markSaved();
    }

    private void setStatus(String text, Color color) {
        this.status = text;
        this.statusColor = color;
    }

    /** Draws the readout in the top-left. Expects the batch to be begun, in UI space. */
    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport) {
        if (!open) {
            return;
        }
        syncEquipment();

        float oldScaleX = font.getData().scaleX;
        float oldScaleY = font.getData().scaleY;
        Color oldColor = new Color(font.getColor());
        font.getData().setScale(1.4f);

        float x = 24f;
        float y = viewport.getWorldHeight() - 110f;
        float line = font.getLineHeight() * 1.15f;

        font.setColor(Color.GOLD);
        font.draw(batch, "WEAPON VIEW TUNER" + (tuner.isDirty() ? "   * UNSAVED *" : ""), x, y);
        y -= line;

        font.setColor(Color.WHITE);
        WeaponViewCycle cycle = selectedCycle();
        Item shown = tuner.hand() == WeaponViewTuner.Hand.MAIN
                ? overlay.getMainHandItem() : overlay.getOffHandItem();
        font.draw(batch, (cycle.isPreviewing()
                        ? "Previewing " + cycle.position() + " of " + cycle.size()
                        : "Equipped (" + cycle.size() + " to walk with [ ])")
                        + (shown != null ? ":  " + shown.getFriendlyName() : ""), x, y);
        y -= line;
        if (!tuner.hasTarget()) {
            font.draw(batch, (tuner.hand() == WeaponViewTuner.Hand.MAIN ? "Main" : "Off")
                    + " hand is empty - H to switch hands", x, y);
            y -= line;
        } else {
            String target = tuner.scope() == WeaponViewTuner.Scope.WEAPON
                    ? "this weapon (" + tuner.itemKey() + ")"
                    : "every " + tuner.archetype() + " without its own tuning";
            font.draw(batch, "Hand: " + tuner.hand() + "   Editing: " + target, x, y);
            y -= line;

            LayerCalibration cal = tuner.current();
            font.draw(batch, String.format("x %+.3f   y %+.3f   scale %.2f   rot %+.0f   %s%s",
                    cal.offsetX, cal.offsetY, cal.scaleY, cal.rotation,
                    cal.flipX ? "FLIPPED" : "",
                    tuner.targetHasEntry() ? "" : "   (inherited)"), x, y);
            y -= line;
        }

        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "arrows move   +/- scale   , . rotate   (shift = big steps)", x, y);
        y -= line;
        font.draw(batch, "[ ] prev/next weapon   E back to equipped   H hand   T weapon/archetype", x, y);
        y -= line;
        font.draw(batch, "F flip   P preview swing   Bksp clear", x, y);
        y -= line;
        font.draw(batch, "ctrl+S save   F5 re-read   F11 close", x, y);
        y -= line;

        if (!status.isEmpty()) {
            font.setColor(statusColor);
            font.draw(batch, status, x, y);
        }

        font.getData().setScale(oldScaleX, oldScaleY);
        font.setColor(oldColor);
    }
}
