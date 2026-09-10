package com.bpm.minotaur.gamedata.save;

import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerEquipment;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.gamedata.spells.SpellType;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable snapshot of the player's position, stats, equipment, inventory, and spells.
 */
public class PlayerSaveData {

    // Coordinates & orientation
    public float x;
    public float y;
    public String facing = "NORTH";

    // Stats
    public int currentHP = 20;
    public int maxHP = 20;
    public int currentMP = 10;
    public int maxMP = 10;

    public int strength = 10;
    public int dexterity = 10;
    public int constitution = 10;
    public int intelligence = 10;
    public int wisdom = 10;
    public int agility = 10;
    public int charisma = 10;
    public int stamina = 3;
    public int luck = 0;

    public int level = 1;
    public int experience = 0;
    public int experienceToNextLevel = 300;

    public float satiety = 80f;
    public float hydration = 80f;
    public float bodyTemperature = 37f;
    public int toxicity = 0;
    public int arrows = 0;
    public int treasureScore = 0;

    // Equipment
    public ItemSaveData wornHelmet;
    public ItemSaveData wornEyes;
    public ItemSaveData wornNeck;
    public ItemSaveData wornBack;
    public ItemSaveData wornChest;
    public ItemSaveData wornArms;
    public ItemSaveData wornGauntlets;
    public ItemSaveData wornLegs;
    public ItemSaveData wornBoots;
    public ItemSaveData wornRing;
    public ItemSaveData wornRing2;
    public ItemSaveData wornShield;

    // Hands
    public ItemSaveData rightHand;
    public ItemSaveData leftHand;

    // Quick slots (size 6)
    public List<ItemSaveData> quickSlots = new ArrayList<>();

    // Backpack (size up to 30)
    public List<ItemSaveData> backpack = new ArrayList<>();

    // Spells
    public List<String> knownSpells = new ArrayList<>();

    public PlayerSaveData() {
    }

    public PlayerSaveData(Player player) {
        if (player == null) {
            return;
        }

        // Coordinates & direction
        this.x = player.getPosition().x;
        this.y = player.getPosition().y;
        if (player.getFacing() != null) {
            this.facing = player.getFacing().name();
        }

        // Stats
        PlayerStats stats = player.getStats();
        if (stats != null) {
            this.currentHP = stats.getCurrentHP();
            this.maxHP = stats.getMaxHP();
            this.currentMP = stats.getCurrentMP();
            this.maxMP = stats.getMaxMP();

            this.strength = stats.getStrength();
            this.dexterity = stats.getDexterity();
            this.constitution = stats.getConstitution();
            this.intelligence = stats.getIntelligence();
            this.wisdom = stats.getWisdom();
            this.agility = stats.getAgility();
            this.charisma = stats.getCharisma();
            this.stamina = stats.getStamina();
            this.luck = stats.getLuck();

            this.level = stats.getLevel();
            this.experience = stats.getExperience();
            this.experienceToNextLevel = stats.getExperienceToNextLevel();

            this.satiety = stats.getSatietyFloat();
            this.hydration = stats.getHydrationFloat();
            this.bodyTemperature = stats.getBodyTemperature();
            this.toxicity = stats.getToxicity();
            this.arrows = stats.getArrows();
            this.treasureScore = stats.getTreasureScore();
        }

        // Equipment
        PlayerEquipment eq = player.getEquipment();
        if (eq != null) {
            this.wornHelmet = eq.getWornHelmet() != null ? new ItemSaveData(eq.getWornHelmet()) : null;
            this.wornEyes = eq.getWornEyes() != null ? new ItemSaveData(eq.getWornEyes()) : null;
            this.wornNeck = eq.getWornNeck() != null ? new ItemSaveData(eq.getWornNeck()) : null;
            this.wornBack = eq.getWornBack() != null ? new ItemSaveData(eq.getWornBack()) : null;
            this.wornChest = eq.getWornChest() != null ? new ItemSaveData(eq.getWornChest()) : null;
            this.wornArms = eq.getWornArms() != null ? new ItemSaveData(eq.getWornArms()) : null;
            this.wornGauntlets = eq.getWornGauntlets() != null ? new ItemSaveData(eq.getWornGauntlets()) : null;
            this.wornLegs = eq.getWornLegs() != null ? new ItemSaveData(eq.getWornLegs()) : null;
            this.wornBoots = eq.getWornBoots() != null ? new ItemSaveData(eq.getWornBoots()) : null;
            this.wornRing = eq.getWornRing() != null ? new ItemSaveData(eq.getWornRing()) : null;
            this.wornRing2 = eq.getWornRing2() != null ? new ItemSaveData(eq.getWornRing2()) : null;
            this.wornShield = eq.getWornShield() != null ? new ItemSaveData(eq.getWornShield()) : null;
        }

        // Inventory
        Inventory inv = player.getInventory();
        if (inv != null) {
            this.rightHand = inv.getRightHand() != null ? new ItemSaveData(inv.getRightHand()) : null;
            this.leftHand = inv.getLeftHand() != null ? new ItemSaveData(inv.getLeftHand()) : null;

            Item[] qs = inv.getQuickSlots();
            if (qs != null) {
                for (Item item : qs) {
                    this.quickSlots.add(item != null ? new ItemSaveData(item) : null);
                }
            }

            List<Item> bp = inv.getMainInventory();
            if (bp != null) {
                for (Item item : bp) {
                    if (item != null) {
                        this.backpack.add(new ItemSaveData(item));
                    }
                }
            }
        }

        // Spells
        List<SpellType> spells = player.getKnownSpells();
        if (spells != null) {
            for (SpellType sp : spells) {
                if (sp != null) {
                    this.knownSpells.add(sp.name());
                }
            }
        }
    }

    public void applyToPlayer(Player player, ItemDataManager itemDataManager, AssetManager assetManager) {
        if (player == null) {
            return;
        }

        // Position & facing
        player.setPosition(x, y);
        try {
            if (facing != null) {
                player.setFacing(Direction.valueOf(facing));
            }
        } catch (Exception ignored) {
        }

        // Stats
        PlayerStats stats = player.getStats();
        if (stats != null) {
            stats.setMaxHP(maxHP);
            stats.setCurrentHP(currentHP);
            stats.setMaxMP(maxMP);
            stats.setCurrentMP(currentMP);

            stats.setStrength(strength);
            stats.setDexterity(dexterity);
            stats.setConstitution(constitution);
            stats.setIntelligence(intelligence);
            stats.setWisdom(wisdom);
            stats.setAgility(agility);
            stats.setCharisma(charisma);
            stats.setStamina(stamina);
            stats.setLuck(luck);

            stats.setLevel(level);
            stats.setExperience(experience);
            stats.setExperienceToNextLevel(experienceToNextLevel);

            stats.setSatiety(satiety);
            stats.setHydration(hydration);
            stats.setBodyTemperature(bodyTemperature);
            stats.setToxicity(toxicity);
            stats.setArrows(arrows);
            stats.setTreasureScore(treasureScore);
        }

        // Equipment
        PlayerEquipment eq = player.getEquipment();
        if (eq != null) {
            eq.setWornHelmet(wornHelmet != null ? wornHelmet.toItem(itemDataManager, assetManager) : null);
            eq.setWornEyes(wornEyes != null ? wornEyes.toItem(itemDataManager, assetManager) : null);
            eq.setWornNeck(wornNeck != null ? wornNeck.toItem(itemDataManager, assetManager) : null);
            eq.setWornBack(wornBack != null ? wornBack.toItem(itemDataManager, assetManager) : null);
            eq.setWornChest(wornChest != null ? wornChest.toItem(itemDataManager, assetManager) : null);
            eq.setWornArms(wornArms != null ? wornArms.toItem(itemDataManager, assetManager) : null);
            eq.setWornGauntlets(wornGauntlets != null ? wornGauntlets.toItem(itemDataManager, assetManager) : null);
            eq.setWornLegs(wornLegs != null ? wornLegs.toItem(itemDataManager, assetManager) : null);
            eq.setWornBoots(wornBoots != null ? wornBoots.toItem(itemDataManager, assetManager) : null);
            eq.setWornRing(wornRing != null ? wornRing.toItem(itemDataManager, assetManager) : null);
            eq.setWornRing2(wornRing2 != null ? wornRing2.toItem(itemDataManager, assetManager) : null);
            eq.setWornShield(wornShield != null ? wornShield.toItem(itemDataManager, assetManager) : null);
        }

        // Inventory
        Inventory inv = player.getInventory();
        if (inv != null) {
            inv.clear();
            inv.setRightHand(rightHand != null ? rightHand.toItem(itemDataManager, assetManager) : null);
            inv.setLeftHand(leftHand != null ? leftHand.toItem(itemDataManager, assetManager) : null);

            Item[] qs = inv.getQuickSlots();
            if (qs != null) {
                for (int i = 0; i < qs.length; i++) {
                    if (i < quickSlots.size() && quickSlots.get(i) != null) {
                        qs[i] = quickSlots.get(i).toItem(itemDataManager, assetManager);
                    } else {
                        qs[i] = null;
                    }
                }
            }

            List<Item> bp = inv.getMainInventory();
            if (bp != null) {
                bp.clear();
                for (ItemSaveData isd : backpack) {
                    if (isd != null) {
                        Item item = isd.toItem(itemDataManager, assetManager);
                        if (item != null) {
                            bp.add(item);
                        }
                    }
                }
            }
        }

        // Spells
        if (knownSpells != null) {
            for (String spName : knownSpells) {
                try {
                    SpellType sp = SpellType.valueOf(spName);
                    player.learnSpell(sp);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
