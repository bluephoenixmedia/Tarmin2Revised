package com.bpm.minotaur.generation.theme;

/**
 * Per-chunk runtime progress toward a themed chunk's objective.
 *
 * <p>Lives on the {@code Maze} and persists through {@code ChunkData}. All
 * fields default to the "untouched" values so saves written before themed
 * objectives existed deserialise without migration.
 */
public class ThemeObjectiveState {

    private ThemeObjectiveKind kind;
    private int progress;
    private int required;
    private boolean completed;
    private boolean surrendered;
    private boolean rewardGranted;

    /**
     * False when the decorator could not actually place what the objective
     * needs (no room for the champion, no room for a single grave).
     *
     * <p>Without this, "no champion is alive" reads as "the champion is dead"
     * and pays out a Crest for a champion that was never placed; an arena that
     * spawned nothing completes on turn one. A non-viable objective also must
     * not seal the chunk, since there would be nothing to resolve it.
     */
    private boolean viable = true;

    /** Turns remaining in a Rune of Surrender channel; 0 means not channelling. */
    private int surrenderChannelRemaining;

    /** Turns a full surrender channel takes. */
    public static final int SURRENDER_CHANNEL_TURNS = 3;

    public ThemeObjectiveState() {
        this(null, 1);
    }

    public ThemeObjectiveState(ThemeObjectiveKind kind, int required) {
        this.kind = kind;
        this.required = Math.max(1, required);
        this.progress = 0;
        this.completed = false;
        this.surrendered = false;
        this.rewardGranted = false;
        this.surrenderChannelRemaining = 0;
    }

    public ThemeObjectiveKind getKind() {
        return kind;
    }

    public void setKind(ThemeObjectiveKind kind) {
        this.kind = kind;
    }

    public int getProgress() {
        return progress;
    }

    public int getRequired() {
        return required;
    }

    public void setRequired(int required) {
        this.required = Math.max(1, required);
    }

    /** Advances progress and completes the objective once the target is met. */
    public void advance() {
        if (completed || surrendered) return;
        progress++;
        if (progress >= required) {
            completed = true;
        }
    }

    public void complete() {
        if (surrendered) return;
        progress = required;
        completed = true;
    }

    public boolean isCompleted() {
        return completed;
    }

    /**
     * True once the player has channelled the Rune of Surrender. The gates open,
     * the Crest is forfeit, and the chunk stays failed for the rest of the run.
     */
    public boolean isSurrendered() {
        return surrendered;
    }

    /** True when the gates should be unsealed, by either route. */
    public boolean isResolved() {
        return completed || surrendered;
    }

    public boolean isViable() {
        return viable;
    }

    public void setViable(boolean viable) {
        this.viable = viable;
    }

    public boolean isRewardGranted() {
        return rewardGranted;
    }

    public void markRewardGranted() {
        this.rewardGranted = true;
    }

    // --- Surrender channel ---

    public boolean isChannellingSurrender() {
        return surrenderChannelRemaining > 0;
    }

    public int getSurrenderChannelRemaining() {
        return surrenderChannelRemaining;
    }

    /** Begins the channel. No-op once the objective is already resolved. */
    public void beginSurrenderChannel() {
        if (isResolved() || isChannellingSurrender()) return;
        surrenderChannelRemaining = SURRENDER_CHANNEL_TURNS;
    }

    /** Interrupts a channel in progress, e.g. when the player moves or is hit. */
    public void cancelSurrenderChannel() {
        surrenderChannelRemaining = 0;
    }

    /**
     * Ticks one turn of the channel.
     *
     * @return true on the turn the channel completes and the chunk is forfeited.
     */
    public boolean tickSurrenderChannel() {
        if (surrenderChannelRemaining <= 0) return false;
        surrenderChannelRemaining--;
        if (surrenderChannelRemaining <= 0) {
            surrendered = true;
            return true;
        }
        return false;
    }

    // --- Serialisation helpers (ChunkData) ---

    public void restore(ThemeObjectiveKind kind, int progress, int required,
                        boolean completed, boolean surrendered, boolean rewardGranted) {
        this.kind = kind;
        this.progress = progress;
        this.required = Math.max(1, required);
        this.completed = completed;
        this.surrendered = surrendered;
        this.rewardGranted = rewardGranted;
        this.surrenderChannelRemaining = 0;
    }
}
