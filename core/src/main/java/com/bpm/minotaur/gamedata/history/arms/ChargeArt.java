package com.bpm.minotaur.gamedata.history.arms;

import java.util.HashMap;
import java.util.Map;

/** Which silhouette under {@code images/arms/} draws each charge a doctrine can bear (Living War A44). */
public final class ChargeArt {

    public static final String FOLDER = "images/arms/";

    private static final Map<String, String> FILES = new HashMap<>();
    static {
        FILES.put("a flayed hand", "flayed_hand");
        FILES.put("an open mouth singing", "open_mouth");
        FILES.put("a hymnal bound in skin", "hymnal");
        FILES.put("a hook through a crown", "hook_crown");
        FILES.put("three chain links", "chain_links");
        FILES.put("a closed manacle", "manacle");
        FILES.put("a lidless eye sewn shut", "lidless_eye");
        FILES.put("a white mask", "white_mask");
        FILES.put("a bell without a tongue", "tongueless_bell");
        FILES.put("a gilded fly", "gilded_fly");
        FILES.put("a rotting crown", "rotting_crown");
        FILES.put("a gold coin weeping", "weeping_coin");
        FILES.put("a scourge aflame", "scourge");
        FILES.put("a burning hand raised", "burning_hand");
        FILES.put("ash upon a field of ash", "ash_field");
        FILES.put("a tower of skulls", "skull_tower");
        FILES.put("a crossed femur and spade", "femur_spade");
        FILES.put("a crowned jawbone", "crowned_jawbone");
        FILES.put("a glass mouth", "glass_mouth");
        FILES.put("a starving hound", "starving_hound");
        FILES.put("an empty bowl cracked", "cracked_bowl");
        FILES.put("a black antlered skull", "antlered_skull");
        FILES.put("a horned crown over an open rift", "horned_crown");
    }

    private ChargeArt() {
    }

    /** The silhouette's path under the assets, or null for a charge with no art. */
    public static String pathOf(String charge) {
        String file = FILES.get(charge);
        return file == null ? null : FOLDER + file + ".png";
    }
}
