package com.bpm.minotaur.gamedata.monster;

/**
 * One monster's death, cut out of a hand-drawn sprite sheet.
 *
 * <p>The sheets are not uniform grids: frame counts, rows and spacing differ per
 * monster, so every frame carries its own rectangle (pixels, top-left origin).
 * Frame 0 is the monster standing; the last frame is the corpse that stays on
 * the floor.
 */
public final class DeathAnimation {

    private final String monsterTexture;
    private final String sheetPath;
    private final int[][] frames;
    private final float frameDuration;

    public DeathAnimation(String monsterTexture, String sheetPath, int[][] frames, float frameDuration) {
        this.monsterTexture = monsterTexture;
        this.sheetPath = sheetPath;
        this.frames = frames;
        this.frameDuration = frameDuration;
    }

    /** The living monster's texture path, which is how a template finds its death. */
    public String getMonsterTexture() {
        return monsterTexture;
    }

    public String getSheetPath() {
        return sheetPath;
    }

    public int getFrameCount() {
        return frames.length;
    }

    public int getFinalFrame() {
        return frames.length - 1;
    }

    public float getFrameDuration() {
        return frameDuration;
    }

    /** {x, y, w, h} of a frame within the sheet. */
    public int[] getFrame(int index) {
        return frames[Math.max(0, Math.min(index, frames.length - 1))];
    }

    /** The frame showing {@code seconds} after death began; clamps on the corpse. */
    public int frameAt(float seconds) {
        if (seconds <= 0f || frameDuration <= 0f) return 0;
        return Math.min(frames.length - 1, (int) (seconds / frameDuration));
    }

    public float getDuration() {
        return frames.length * frameDuration;
    }
}
