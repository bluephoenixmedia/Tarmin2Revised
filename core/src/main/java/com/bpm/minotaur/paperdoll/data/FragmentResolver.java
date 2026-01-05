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
            case "BASINET":
                return "bascinet";
            case "BRONZE_PLATE":
                return "bronze_breastplate";
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
                DollFragment fragment = new DollFragment(plateRegion, 50, "torso", sX, sY);
                fragment.localOffset.set(item.getOffsetX(), item.getOffsetY());
                fragments.add(fragment);
            }
        }

        // --- Standard Item Logic ---
        else {
            // Try to find a region matching the item name
            // Weapons
            if (weaponAtlas != null && (!item.isArmor() || item.isShield())) {
                AtlasRegion weaponRegion = weaponAtlas.findRegion(regionName);
                if (weaponRegion != null) {
                    String socket = "hand_main";
                    if (name.contains("SHIELD"))
                        socket = "hand_off";
                    DollFragment fragment = new DollFragment(weaponRegion, 70, socket, sX, sY);
                    fragment.localOffset.set(item.getOffsetX(), item.getOffsetY());
                    fragments.add(fragment);
                    return fragments;
                }
            }

            // Armor
            if (armorAtlas != null) {
                AtlasRegion armorRegion = armorAtlas.findRegion(regionName);
                if (armorRegion != null) {
                    com.badlogic.gdx.Gdx.app.log("FR_DEBUG",
                            item.getType() + " Boots=" + item.isBoots() + " Helm=" + item.isHelmet());
                    // Determine Z-Index based on type
                    int zIndex = 30; // Default
                    String socket = "torso";

                    if (item.isHelmet()) {
                        zIndex = 60;
                        socket = "head";
                    } else if (item.isBoots()) {
                        zIndex = 50;
                        socket = "feet";
                    } else if (item.isGauntlets()) {
                        zIndex = 55;
                        socket = "hand_main";
                    } else if (item.isLegs()) {
                        zIndex = 40;
                        socket = "hips"; // Mapping Legs to Hips for now
                    } else if (item.isShield()) {
                        zIndex = 55;
                        socket = "hand_off";
                    } else if (item.isArms()) {
                        zIndex = 52;
                        socket = "hand_main";
                    }

                    DollFragment fragment = new DollFragment(armorRegion, zIndex, socket, sX, sY);
                    fragment.localOffset.set(item.getOffsetX(), item.getOffsetY());
                    com.badlogic.gdx.Gdx.app.log("FragmentResolver",
                            "   -> Assigned Socket: " + socket + " Z: " + zIndex);
                    fragments.add(fragment);
                }
            }

        }

        return fragments;
    }
}
