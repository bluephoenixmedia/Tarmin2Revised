package com.bpm.minotaur.managers;

/**
 * Contract for a wild magic Chaos Surge event invoked by The Laughing Void.
 */
public interface ChaosSurgeEvent {

    /** Unique identifier string (e.g. "acidic_stardust", "inverted_duality"). */
    String getId();

    /** Human-readable event title for banners and logs. */
    String getName();

    /** Brief description of the chaos effect. */
    String getDescription();

    /** Fired once when The Laughing Void descends and reality warps. */
    void onActivate(ChaosEventContext context);

    /** Fired each discrete game turn tick while the surge is active. */
    void onTurnTick(ChaosEventContext context);

    /** Fired when the surge concludes and normal reality is restored. */
    void onExpire(ChaosEventContext context);
}
