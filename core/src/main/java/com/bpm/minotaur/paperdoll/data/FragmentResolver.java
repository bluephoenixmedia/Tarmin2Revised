package com.bpm.minotaur.paperdoll.data;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves an ItemType into a list of visual DollFragments.
 * Handles the "one item -> multiple layers" logic (e.g. Plate -> Mail + Plate).
 */
public class FragmentResolver {

    private final TextureAtlas armorAtlas;
    private final TextureAtlas weaponAtlas;

    // We can define mapping logic here.
    // In the future, this could be data-driven from a JSON,
    // but for now we implement the logic in code as requested/implied.

    public FragmentResolver(TextureAtlas armorAtlas, TextureAtlas weaponAtlas) {
        this.armorAtlas = armorAtlas;
        this.weaponAtlas = weaponAtlas;
    }

    private String getAtlasRegionName(String itemName) {
        // Handle Exceptions / Mismatches
        switch (itemName) {
            case "BANDED_MAIL":
                return "banded";
            case "SPLINT_MAIL":
                return "splint_mail_armor";
            case "STUDDED_LEATHER":
                return "studded_leather_armor";
            case "LEATHER_ARMOR":
                return "leather_armor"; // Explicit match
            case "PADDED_ARMOR":
                return "padded_armor"; // Explicit match
            case "HIDE_ARMOR":
                return "hide_armor"; // Explicit match
            default:
                return itemName.toLowerCase();
        }
    }

    public List<DollFragment> resolve(Item item) {
        List<DollFragment> fragments = new ArrayList<>();

        if (item == null)
            return fragments;

        ItemType itemType = item.getType();
        String name = itemType.name();
        String regionName = getAtlasRegionName(name);

        // Get Scale from Item
        float sX = 1f;
        float sY = 1f;
        if (item.getScale() != null) {
            sX = item.getScale().x;
            sY = item.getScale().y;
        }

        // --- Plate Armor Example ---
        if (name.contains("PLATE")) {
            // 1. Chainmail Underlayer (Z=40)
            AtlasRegion mailRegion = armorAtlas.findRegion("chain_torso");
            if (mailRegion != null) {
                fragments.add(new DollFragment(mailRegion, 40, "torso", sX, sY));
            }

            // 2. Plate Overlayer (Z=50)
            // Use resolved name logic or fallback
            AtlasRegion plateRegion = armorAtlas.findRegion(regionName + "_torso");
            if (plateRegion == null)
                plateRegion = armorAtlas.findRegion(regionName); // Check exact match if _torso fails

            if (plateRegion == null)
                plateRegion = armorAtlas.findRegion("plate_torso"); // Fallback

            if (plateRegion != null) {
                fragments.add(new DollFragment(plateRegion, 50, "torso", sX, sY));
            }
        }

        // --- Standard Item Logic ---
        else {
            // Try to find a region matching the item name
            // Weapons
            if (weaponAtlas != null) {
                AtlasRegion weaponRegion = weaponAtlas.findRegion(regionName);
                if (weaponRegion != null) {
                    String socket = "hand_main";
                    if (name.contains("SHIELD"))
                        socket = "hand_off";
                    fragments.add(new DollFragment(weaponRegion, 70, socket, sX, sY));
                    return fragments;
                }
            }

            // Armor
            if (armorAtlas != null) {
                AtlasRegion armorRegion = armorAtlas.findRegion(regionName);
                if (armorRegion != null) {
                    // Determine Z-Index based on type
                    int zIndex = 30; // Default
                    String socket = "torso";

                    if (name.contains("HELM")) {
                        zIndex = 60;
                        socket = "head";
                    } else if (name.contains("BOOT")) {
                        zIndex = 50;
                        socket = "feet";
                    } else if (name.contains("GAUNTLET")) {
                        zIndex = 55;
                        socket = "hand_main";
                    }

                    fragments.add(new DollFragment(armorRegion, zIndex, socket, sX, sY));
                }
            }
        }

        return fragments;
    }
}
