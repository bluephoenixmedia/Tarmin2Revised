package com.bpm.minotaur.gamedata.progression;

import com.bpm.minotaur.gamedata.item.Item;

/**
 * Starting weapons that the player can unlock with Divinities at the Ancient Stone Altar
 * and choose as their permanent respawn and new-game loadout.
 */
public enum StartingWeapon {
    RUSTY_SWORD(Item.ItemType.RUSTY_SWORD, "Rusty Sword", "1d3 Slashing", 0, 0,
            "Humble beaten iron blade. The tarnished standby of every doomed explorer."),
    KNIFE(Item.ItemType.KNIFE, "Hunting Knife", "1d4 Piercing", 15, 0,
            "Lightweight steel blade. Swift and nimble for close-quarters rogues."),
    AXE(Item.ItemType.AXE, "Hand Axe", "1d6 Slashing", 20, 0,
            "Hefty iron woodcutter's axe capable of cleaving timber and bone."),
    SPEAR(Item.ItemType.SPEAR, "Hunting Spear", "1d6 Piercing", 25, 0,
            "Iron-tipped hunting spear providing reach and defensive thrusts."),
    WARHAMMER(Item.ItemType.WARHAMMER, "Warhammer", "1d8 Bludgeoning", 25, 0,
            "Heavy flanged iron warhammer forged to shatter armor and skulls."),
    BOW(Item.ItemType.BOW, "Hunting Bow", "1d4 Ranged", 30, 15,
            "Sturdy recurve bow. Grants a quiver of 15 starter arrows upon setting out."),
    CROSSBOW(Item.ItemType.CROSSBOW, "Light Crossbow", "1d6 Ranged", 35, 15,
            "Lever-cocked light crossbow. Grants 15 starter quarrels upon setting out."),
    QUARTERSTAFF(Item.ItemType.QUARTERSTAFF, "Quarterstaff", "1d6 Bludgeoning", 20, 0,
            "Iron-shod oak stave resonant with balanced martial agility."),
    WOODEN_CROSS(Item.ItemType.WOODEN_CROSS, "Wooden Cross", "1d4 Spiritual", 20, 0,
            "Hallowed wooden icon channeling sacred light against the unholy.");

    private final Item.ItemType itemType;
    private final String displayName;
    private final String damageDice;
    private final int divinityCost;
    private final int startingAmmo;
    private final String description;

    StartingWeapon(Item.ItemType itemType, String displayName, String damageDice,
                   int divinityCost, int startingAmmo, String description) {
        this.itemType = itemType;
        this.displayName = displayName;
        this.damageDice = damageDice;
        this.divinityCost = divinityCost;
        this.startingAmmo = startingAmmo;
        this.description = description;
    }

    public Item.ItemType getItemType() {
        return itemType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDamageDice() {
        return damageDice;
    }

    public int getDivinityCost() {
        return divinityCost;
    }

    public int getStartingAmmo() {
        return startingAmmo;
    }

    public String getDescription() {
        return description;
    }

    public static StartingWeapon fromItemType(Item.ItemType type) {
        if (type == null) return RUSTY_SWORD;
        for (StartingWeapon w : values()) {
            if (w.itemType == type) return w;
        }
        return RUSTY_SWORD;
    }
}
