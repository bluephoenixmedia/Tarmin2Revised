package com.bpm.minotaur.gamedata.bones;

import com.bpm.minotaur.gamedata.save.PlayerSaveData;

/**
 * Serialized snapshot of a fallen player for the NetHack-style Bones system.
 * Contains the player's full stats, equipment, inventory, and spellbook at death,
 * along with strata depth and runtime encounter state.
 */
public class BonesData {

    public String id;
    /** The name the bones were written under; all an older file has. Read through {@link #name()}. */
    public String playerName = NAMELESS;
    /** The prisoner this life was (#282); null in bones written before lives had names. */
    public String lifeName;
    /** Their home settlement: "the Ashen Ford". */
    public String lifeSettlement;
    /** What felled them, in words, or null. */
    public String killer;
    public String epitaph = "Fell in the depths";
    public int floorLevel = 1;
    public int strataDepth = 1;
    public long timestamp;
    public PlayerSaveData playerData;

    // Runtime state within the dungeon floor
    public int chunkX = -1;
    public int chunkY = -1;
    public int tileX = -1;
    public int tileY = -1;
    public boolean awakened = false;
    public boolean defeated = false;

    // Local file path on disk
    public String filePath;

    public BonesData() {
        this.timestamp = System.currentTimeMillis();
    }

    public BonesData(String id, String playerName, String epitaph, int floorLevel, int strataDepth,
                     PlayerSaveData playerData) {
        this.id = id;
        this.playerName = playerName;
        this.epitaph = epitaph;
        this.floorLevel = floorLevel;
        this.strataDepth = strataDepth;
        this.playerData = playerData;
        this.timestamp = System.currentTimeMillis();
    }

    /** Who the bones were when no life had been forged for them: never a Fallen Hero (#282). */
    public static final String NAMELESS = "a nameless prisoner";

    /** Whose bones these are: the prisoner's name, or the name an older file kept. */
    public String name() {
        if (lifeName != null) return lifeName;
        return playerName != null ? playerName : NAMELESS;
    }

    /** "Here lies Brannoc Thatch of the Ashen Ford, who fell to Gorvak the Gnawer" */
    public String graveText() {
        StringBuilder text = new StringBuilder("Here lies ").append(name());
        if (lifeName != null && lifeSettlement != null) text.append(" of ").append(lifeSettlement);
        if (killer != null) text.append(", who fell to ").append(killer);
        return text.toString();
    }

    /** "the shade of Brannoc Thatch": a ghost is one life, never a Fallen Hero. */
    public String shadeName() {
        return "the shade of " + name();
    }

    private transient java.util.List<com.bpm.minotaur.gamedata.item.Item> extractedItems = null;

    public java.util.List<com.bpm.minotaur.gamedata.item.Item> getOrExtractItems(
            com.bpm.minotaur.gamedata.item.ItemDataManager dataManager,
            com.badlogic.gdx.assets.AssetManager assetManager) {
        if (extractedItems != null) {
            return extractedItems;
        }
        extractedItems = new java.util.ArrayList<>();
        if (playerData == null) return extractedItems;

        addItemIfNotNull(extractedItems, playerData.wornHelmet, dataManager, assetManager);
        addItemIfNotNull(extractedItems, playerData.wornEyes, dataManager, assetManager);
        addItemIfNotNull(extractedItems, playerData.wornNeck, dataManager, assetManager);
        addItemIfNotNull(extractedItems, playerData.wornBack, dataManager, assetManager);
        addItemIfNotNull(extractedItems, playerData.wornChest, dataManager, assetManager);
        addItemIfNotNull(extractedItems, playerData.wornArms, dataManager, assetManager);
        addItemIfNotNull(extractedItems, playerData.wornGauntlets, dataManager, assetManager);
        addItemIfNotNull(extractedItems, playerData.wornLegs, dataManager, assetManager);
        addItemIfNotNull(extractedItems, playerData.wornBoots, dataManager, assetManager);
        addItemIfNotNull(extractedItems, playerData.wornRing, dataManager, assetManager);
        addItemIfNotNull(extractedItems, playerData.wornRing2, dataManager, assetManager);
        addItemIfNotNull(extractedItems, playerData.wornShield, dataManager, assetManager);
        addItemIfNotNull(extractedItems, playerData.wornBelt, dataManager, assetManager);
        addItemIfNotNull(extractedItems, playerData.rightHand, dataManager, assetManager);
        addItemIfNotNull(extractedItems, playerData.leftHand, dataManager, assetManager);

        if (playerData.quickSlots != null) {
            for (com.bpm.minotaur.gamedata.save.ItemSaveData isd : playerData.quickSlots) {
                addItemIfNotNull(extractedItems, isd, dataManager, assetManager);
            }
        }
        if (playerData.backpack != null) {
            for (com.bpm.minotaur.gamedata.save.ItemSaveData isd : playerData.backpack) {
                addItemIfNotNull(extractedItems, isd, dataManager, assetManager);
            }
        }
        return extractedItems;
    }

    private void addItemIfNotNull(java.util.List<com.bpm.minotaur.gamedata.item.Item> list,
                                  com.bpm.minotaur.gamedata.save.ItemSaveData isd,
                                  com.bpm.minotaur.gamedata.item.ItemDataManager dataManager,
                                  com.badlogic.gdx.assets.AssetManager assetManager) {
        if (isd != null && isd.type != null) {
            try {
                com.bpm.minotaur.gamedata.item.Item it = isd.toItem(dataManager, assetManager);
                if (it != null) {
                    list.add(it);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
