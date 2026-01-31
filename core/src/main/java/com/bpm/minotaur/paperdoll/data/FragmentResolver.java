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
            case "CHAIN_HAUBERK": // Explicitly handling user mention if needed, or rely on default
                return "chain_hauberk"; // Default lower case works but being explicit helps debug
            default:
                // Generalized Mapping for Weapons (Fallback when Template is missing)
                String upper = itemName.toUpperCase();
                if (upper.contains("SWORD") || upper.contains("BLADE") || upper.contains("SABRE")
                        || upper.contains("SCIMITAR") || upper.contains("KATANA"))
                    return "sword";
                if (upper.contains("SPEAR") || upper.contains("LANCE") || upper.contains("PIKE")
                        || upper.contains("HALBERD") || upper.contains("TRIDENT"))
                    return "spear";
                if (upper.contains("AXE"))
                    return "axe"; // Or battle_axe
                if (upper.contains("BOW") && !upper.contains("CROSSBOW"))
                    return "bow";
                if (upper.contains("CROSSBOW"))
                    return "crossbow";
                if (upper.contains("DAGGER") || upper.contains("KNIFE") || upper.contains("DIRK"))
                    return "knife";
                if (upper.contains("DART"))
                    return "dart";
                if (upper.contains("SCROLL"))
                    return "scroll";
                if (upper.contains("BOOK") || upper.contains("TOME"))
                    return "book";

                return itemName.toLowerCase();
        }
    }

    public List<DollFragment> resolve(Item item) {
        List<DollFragment> fragments = new ArrayList<>();

        if (item == null)
            return fragments;

        ItemType itemType = item.getType();
        String name = itemType.name();

        // Try to derive region name from template texture path first
        String regionName = null;
        if (item.getTemplate() != null && item.getTemplate().texturePath != null) {
            String path = item.getTemplate().texturePath;
            int lastSlash = path.lastIndexOf('/');
            int lastDot = path.lastIndexOf('.');
            if (lastDot > lastSlash) {
                regionName = path.substring(lastSlash + 1, lastDot);
            }
        }

        if (regionName == null) {
            regionName = getAtlasRegionName(name);
        }

        // Get Scale from Item
        float sX = 1f;
        float sY = 1f;
        if (item.getScale() != null) {
            sX = item.getScale().x;
            sY = item.getScale().y;
        }

        // --- Plate Armor Example ---
        if (name.contains("PLATE")) {
            // 1. Chainmail Underlayer (Z=35) - Below standard Torso (40)
            AtlasRegion mailRegion = armorAtlas.findRegion("chain_torso");
            if (mailRegion != null) {
                fragments.add(new DollFragment(mailRegion, 35, "torso", sX, sY));
            }

            // 2. Plate Overlayer (Z=40) - Matches standard Torso
            // Use resolved name logic or fallback
            AtlasRegion plateRegion = armorAtlas.findRegion(regionName + "_torso");
            if (plateRegion == null)
                plateRegion = armorAtlas.findRegion(regionName); // Check exact match if _torso fails

            if (plateRegion == null)
                plateRegion = armorAtlas.findRegion("plate_torso"); // Fallback

            if (plateRegion != null) {
                DollFragment fragment = new DollFragment(plateRegion, 40, "torso", sX, sY);
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
                    fragment.rotation = item.getRotation(); // Pass rotation
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
                        zIndex = 90;
                        socket = "head";
                    } else if (item.isGauntlets()) {
                        zIndex = 60;
                        socket = "hand_main";
                    } else if (item.isBoots()) {
                        zIndex = 35; // Render over legs (30)
                        socket = "feet";
                    } else if (item.isLegs()) {
                        zIndex = 30;
                        socket = "hips"; // Mapping Legs to Hips for now
                    } else if (item.isShield()) {
                        zIndex = 70;
                        socket = "hand_off";
                    } else if (item.isArms()) {
                        zIndex = 50;
                        socket = "hand_main";
                        if (name.equals("CHAINMAIL_ARMS")) {
                            com.badlogic.gdx.Gdx.app.log("FragmentResolver", "Resolving CHAINMAIL_ARMS. Region found: "
                                    + (armorRegion != null) + " RegionName: " + regionName);
                        }
                    } else if (item.isCloak()) {
                        zIndex = 10; // Behind everything (Body is usually ~10-20, Plate is 50)
                        socket = "back";
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
