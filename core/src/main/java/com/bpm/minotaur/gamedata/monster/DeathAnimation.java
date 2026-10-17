package com.bpm.minotaur.gamedata.monster;

/**
 * One monster's death, as a grid atlas baked by {@code tools/build_death_animations.py}.
 *
 * <p>Every cell is the same size, read left to right then top to bottom, with
 * the body centred horizontally and the floor on the cell's bottom edge -- so
 * drawing a cell centred on the tile, feet on the floor, keeps the monster in
 * place however it falls apart. Frame 0 is the monster standing; the last frame
 * is the corpse that stays.
 */
public final class DeathAnimation {

    private final String monsterTexture;
    private final String sheetPath;
    private final int columns;
    private final int frameCount;
    private final int frameWidth;
    private final int frameHeight;
    private final int bodyWidth;
    private final int bodyHeight;
    private final int widestFrame;
    private final float frameDuration;

    public DeathAnimation(String monsterTexture, String sheetPath, int columns, int frameCount,
                          int frameWidth, int frameHeight, int bodyWidth, int bodyHeight,
                          int widestFrame, float frameDuration) {
        this.monsterTexture = monsterTexture;
        this.sheetPath = sheetPath;
        this.columns = Math.max(1, columns);
        this.frameCount = Math.max(1, frameCount);
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.bodyWidth = Math.max(1, bodyWidth);
        this.bodyHeight = Math.max(1, bodyHeight);
        this.widestFrame = Math.max(1, widestFrame);
        this.frameDuration = frameDuration;
    }

    /** The living monster's texture path, which is how a template finds its death. */
    public String getMonsterTexture() {
        return monsterTexture;
    }

    public String getSheetPath() {
        return sheetPath;
    }

    public int getColumns() {
        return columns;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public int getFinalFrame() {
        return frameCount - 1;
    }

    public int getFrameWidth() {
        return frameWidth;
    }

    public int getFrameHeight() {
        return frameHeight;
    }

    /** Visible width of frame 0, in sheet pixels: what lines up with the living sprite. */
    public int getBodyWidth() {
        return bodyWidth;
    }

    /** Visible height of frame 0, in sheet pixels. */
    public int getBodyHeight() {
        return bodyHeight;
    }

    /** Visible width of the widest frame, in sheet pixels. */
    public int getWidestFrame() {
        return widestFrame;
    }

    public float getFrameDuration() {
        return frameDuration;
    }

    /** Left edge of a frame's cell in the sheet, in pixels. */
    public int frameX(int index) {
        return (clamp(index) % columns) * frameWidth;
    }

    /** Top edge of a frame's cell in the sheet, in pixels. */
    public int frameY(int index) {
        return (clamp(index) / columns) * frameHeight;
    }

    /** The frame showing {@code seconds} after death began; clamps on the corpse. */
    public int frameAt(float seconds) {
        if (seconds <= 0f || frameDuration <= 0f) return 0;
        return clamp((int) (seconds / frameDuration));
    }

    public float getDuration() {
        return frameCount * frameDuration;
    }

    private int clamp(int index) {
        return Math.max(0, Math.min(index, frameCount - 1));
    }
}
