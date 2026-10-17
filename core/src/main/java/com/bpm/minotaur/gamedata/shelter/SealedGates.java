package com.bpm.minotaur.gamedata.shelter;

import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.blight.CastleGate;
import com.bpm.minotaur.generation.ShelterBuilder;

/**
 * What the sealed gates at the ends of the roads say when the player knocks.
 *
 * <p>Castle Tarmin's gate counts the seals held; a seal site's gate is the way
 * down to the deep boss who holds a seal, and a later feature opens it. Both are
 * hooks: kept here so the player's movement only relays the answer.
 */
public final class SealedGates {

    public static final String SEAL_SITE_MESSAGE =
            "A sealed way down, older than the Legion. One of the ancient seals lies far beneath it.";

    private static java.util.function.Supplier<String> sealSiteVoice;
    /** Opens a conversation with one of a town's folk (Houses of the Maze T4.3). */
    private static java.util.function.Consumer<Scenery> talker;

    /** Castle Tarmin's prop ids beyond the gate (Living War W35, W36). */
    public static final String BREACH_PROP = "castle_breach";
    public static final String THRONE_DOOR_PROP = "throne_door";
    public static final String THRONE_DOOR_MESSAGE =
            "The throne of Tarmin-Zul lies beyond. This inner door is sealed, and nothing you hold will open it yet.";

    /** Takes the player into the castle's halls, by its breach or its opened gate; true if they went. */
    private static java.util.function.Predicate<Scenery> castleWay;

    public static void setCastleWay(java.util.function.Predicate<Scenery> way) {
        castleWay = way;
    }

    /** Whether bumping {@code s} took the player into the castle (the breach, or the gate once open). */
    public static boolean enterCastle(Scenery s) {
        if (s == null || castleWay == null) return false;
        boolean way = BREACH_PROP.equals(s.getPropId()) || (CastleGate.isCastleGate(s) && CastleGate.isOpen());
        return way && castleWay.test(s);
    }

    public static void setTalker(java.util.function.Consumer<Scenery> listener) {
        talker = listener;
    }

    /** True if bumping {@code s} is speaking to someone, and the conversation was opened. */
    public static boolean talk(Scenery s) {
        if (s == null || !s.isTownsfolk() || talker == null) return false;
        talker.accept(s);
        return true;
    }

    private SealedGates() {
    }

    /** What the seal site says once the game knows who holds the gash below; null to reset. */
    public static void setSealSiteVoice(java.util.function.Supplier<String> voice) {
        sealSiteVoice = voice;
    }

    /** The message for knocking at this scenery, or null if it is no sealed gate. */
    public static String knock(Scenery scenery) {
        if (scenery != null && THRONE_DOOR_PROP.equals(scenery.getPropId())) return THRONE_DOOR_MESSAGE;
        if (CastleGate.isCastleGate(scenery)) {
            return CastleGate.knockMessage(CastleGate.sealsHeld());
        }
        if (ShelterBuilder.isSealGate(scenery)) {
            String voiced = sealSiteVoice != null ? sealSiteVoice.get() : null;
            return voiced != null ? voiced : SEAL_SITE_MESSAGE;
        }
        return null;
    }
}
