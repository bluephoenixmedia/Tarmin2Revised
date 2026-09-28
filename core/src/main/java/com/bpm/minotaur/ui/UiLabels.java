package com.bpm.minotaur.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

/**
 * Label constructors that cannot overflow their cell.
 *
 * <p>Nearly every P0 in the audit is the same defect: a Label with no maximum width in a cell
 * narrower than its text. Scene2D does not clip a Label by default -- it centres the glyphs and
 * lets them run out of both sides of the cell, over whatever is next to it. That is why the
 * altar's Commune button is "drawn over" the Divinity balance, why the modal title crosses its
 * own border, and why the hearth's back button reads {@code ESC / O : Back to Shelt...} with the
 * leading bracket missing too.
 *
 * <p>So: one Label per line, and every Label either wraps in a sized cell or ellipsizes in a
 * fixed one. {@link #wrapped} and {@link #ellipsized} are the two ways to say that.
 */
public final class UiLabels {

    private UiLabels() {
    }

    /**
     * A Label that wraps, added to {@code table} in a cell of {@code width} canvas units.
     *
     * <p>Wrapping needs a width to wrap at, and a Label reports a preferred width of its whole
     * unwrapped string, so a wrapped Label in an unsized cell silently does not wrap. Setting
     * both together here is the only reliable way to get it right.
     */
    public static Cell<Label> wrapped(Table table, String text, Label.LabelStyle style, float width) {
        Label label = new Label(UiGlyphs.sanitize(text), style);
        label.setWrap(true);
        return table.add(label).width(width).left().top();
    }

    /** A wrapping Label, for callers placing the cell themselves. */
    public static Label wrapping(String text, Label.LabelStyle style) {
        Label label = new Label(UiGlyphs.sanitize(text), style);
        label.setWrap(true);
        return label;
    }

    /**
     * A single-line Label that truncates with an ellipsis rather than overflowing.
     *
     * <p>Use it wherever the text comes from data and the cell is fixed. Pair it with a tooltip
     * when the full string matters -- a truncated relic name made two different rings look
     * identical in the chronicle (CHRON-2).
     */
    public static Label ellipsized(String text, Label.LabelStyle style) {
        Label label = new Label(UiGlyphs.sanitize(text), style);
        label.setEllipsis(true);
        // An ellipsized Label still reports its full text as its preferred width, so a cell that
        // sizes to preferred width will not truncate. Callers give it a width; this makes sure a
        // forgotten width cannot make the label push its row wider than the panel.
        label.setWrap(false);
        return label;
    }

    /** A plain single-line Label with the font's characters checked. */
    public static Label of(String text, Label.LabelStyle style) {
        return new Label(UiGlyphs.sanitize(text), style);
    }

    /**
     * A label-left / value-right stat row, added as one row of {@code table}.
     *
     * <p>SPEC section 3: stat columns are label-left in TEXT_DIM and value-right in TEXT. Doing
     * it by hand is how the HUD portrait block ended up with DIV, DOOM, ARR and SHOT printed on
     * top of each other (HUD-2), and how the spellbook's detail rows overlapped (SPELL-2).
     */
    public static void statRow(Table table, com.bpm.minotaur.rendering.HudSkin skin,
                               String name, String value, float width) {
        Label nameLabel = ellipsized(name, UiStyles.caption(skin));
        Label valueLabel = ellipsized(value, UiStyles.body(skin));
        table.add(nameLabel).left().growX();
        table.add(valueLabel).right().padLeft(UiTheme.PAD_SM);
        table.row().padTop(UiTheme.PAD_XS);
        if (width > 0f) {
            table.getCell(nameLabel).width(width * 0.55f);
            table.getCell(valueLabel).width(width * 0.45f);
        }
    }
}
