package com.bpm.minotaur.rendering;

/**
 * Vertical budgets for the HUD dashboard, worked out from font metrics rather than guessed (#108).
 *
 * <p>The dashboard is a fixed band. Its columns used to be sized with literals -- five chronicle
 * rows, 22-unit bars -- that were only right for one font size, so the chronicle and the vitals
 * column either overflowed the band or left it half empty. These functions take the real line
 * heights and return what fits.
 */
final class HudLayout {

    /** Height of the bottom dashboard band, in canvas units. */
    static final float DASHBOARD_HEIGHT = 200f;
    /** The dashboard table's own top + bottom padding. */
    static final float DASHBOARD_PAD_V = 20f;

    static final int CHRONICLE_MIN_ROWS = 3;
    static final int CHRONICLE_MAX_ROWS = 6;

    static final float BAR_MIN_HEIGHT = 16f;
    static final float BAR_MAX_HEIGHT = 22f;

    private HudLayout() {
    }

    /** Height left inside a dashboard zone once the band's and the zone's own padding are taken. */
    static float zoneInnerHeight(float zonePadV) {
        return DASHBOARD_HEIGHT - DASHBOARD_PAD_V - zonePadV;
    }

    /**
     * How many log rows the chronicle can show under its header without spilling out of the band.
     *
     * @param zonePadV   the chronicle zone's top + bottom padding
     * @param headerRow  the header row's height including the gap below it
     * @param rowHeight  one log row's height including the gap below it
     */
    static int chronicleRows(float zonePadV, float headerRow, float rowHeight) {
        if (rowHeight <= 0f) return CHRONICLE_MIN_ROWS;
        int rows = (int) Math.floor((zoneInnerHeight(zonePadV) - headerRow) / rowHeight);
        return Math.max(CHRONICLE_MIN_ROWS, Math.min(CHRONICLE_MAX_ROWS, rows));
    }

    /**
     * The tallest bar height that lets three stacked bars and the stat block beneath them fit the
     * vitals zone, kept between {@link #BAR_MIN_HEIGHT} and {@link #BAR_MAX_HEIGHT}. All three bars
     * share it, so EXP never reads as a thinner gauge than HP and MP.
     *
     * @param zonePadV    the vitals zone's top + bottom padding
     * @param barGaps     the total gap below the three bars
     * @param statBlock   the height of the block under the bars (its tallest cell)
     */
    static float vitalsBarHeight(float zonePadV, float barGaps, float statBlock) {
        float h = (float) Math.floor((zoneInnerHeight(zonePadV) - barGaps - statBlock) / 3f);
        return Math.max(BAR_MIN_HEIGHT, Math.min(BAR_MAX_HEIGHT, h));
    }

    /** The opacity of chronicle row {@code index} of {@code rows}, oldest dimmest, newest full. */
    static float chronicleRowAlpha(int index, int rows) {
        if (rows <= 1) return 1f;
        return 0.40f + 0.60f * index / (float) (rows - 1);
    }
}
