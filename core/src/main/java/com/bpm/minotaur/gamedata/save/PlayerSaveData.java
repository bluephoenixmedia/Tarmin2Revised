package com.bpm.minotaur.gamedata.save;

import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerEquipment;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.gamedata.spells.SpellDataManager;

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
    public int unallocatedAttributePoints = 0;
    public int unallocatedSkillPoints = 0;
    public List<String> unlockedSkills = new ArrayList<>();

    public float satiety = 80f;
    public float hydration = 80f;
    public float bodyTemperature = 37f;
    public int toxicity = 0;
    public int arrows = 0;
    public int shot = 0;
    /** Carried powder dampness, 0..1; see PowderDampness. */
    public float powderDampness = 0f;
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
    public ItemSaveData wornBelt;
    /** Personality trait: the one held, an offer still waiting to be answered, and respawns since the last choice. */
    public String traitId;
    public java.util.List<String> pendingTraitOffer = new java.util.ArrayList<>();
    public int respawnsSinceChoice;

    // Hands
    public ItemSaveData rightHand;
    public ItemSaveData leftHand;

    // Quick slots (size 6)
    public List<ItemSaveData> quickSlots = new ArrayList<>();

    // Backpack (size up to 30)
    public List<ItemSaveData> backpack = new ArrayList<>();

    // Spells. knownSpellIds is null in saves written before the spellbook was persisted;
    // those carried only the legacy knownSpells name list.
    public List<String> knownSpellIds;
    public List<String> permanentSpellIds;
    public List<String> runSpellIds;
    public List<String> preparedSpells = new ArrayList<>();
    public int unlockedSpellSlots = 1;
    public List<String> knownSpells = new ArrayList<>();
    // A Tome Choice still waiting for its pick (null when none), saved as offered so a
    // reload cannot redraw it.
    public Item.ItemType pendingTomeType;
    public List<String> pendingTomeOptions = new ArrayList<>();
    public int pendingTomeRerolls;

    // Blood on the father's skin and splashes not yet settled onto the paperdoll. Blood
    // on worn items travels inside each ItemSaveData.
    public com.bpm.minotaur.gamedata.gore.PlayerBlood blood;

    // Run state that used to be lost on every reload. All of these are null in saves written
    // before they were persisted; null means "leave the character as it is", while an empty
    // list means the character really had none.
    public List<com.bpm.minotaur.gamedata.effects.ActiveStatusEffect> statusEffects;
    public List<com.bpm.minotaur.gamedata.effects.StatusEffectType> mealEffects;
    public List<InjurySaveData> injuries;
    public com.bpm.minotaur.gamedata.injury.IllnessStage illnessStage;
    public int illnessTimer;
    public int injuryStepCounter;
    public int bleedDamageThisRun;
    public Integer fieldRestCooldownTurns;
    public Integer temporaryHP;
    public Integer kindlingCount;
    public Integer cookingWaterCount;
    public Integer cookingSkill;

    /** One open wound, with the running state a fresh InjuryRecord would reset. */
    public static class InjurySaveData {
        public com.bpm.minotaur.gamedata.injury.BodyPart bodyPart;
        public com.bpm.minotaur.gamedata.injury.InjuryType injuryType;
        public int severity = 1;
        public boolean treated;
        public boolean infected;
        public int turnsUntreated;
        public int bleedTicksRemaining;
    }

    public PlayerSaveData() {
    }

    public PlayerSaveData(Player player) {
        if (player == null) {
            return;
        }

        this.blood = player.getBlood();

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
            this.maxHP = stats.getBaseMaxHP(); // not the trait-adjusted figure, or it would be applied twice on load
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
            this.shot = stats.getShot();
            this.powderDampness = stats.getPowderDampness();
            this.treasureScore = stats.getTreasureScore();

            this.temporaryHP = stats.getTemporaryHP();
            this.kindlingCount = stats.getKindlingCount();
            this.cookingWaterCount = stats.getCookingWaterCount();
            this.cookingSkill = stats.getCookingSkill();

            this.unallocatedAttributePoints = stats.getUnallocatedAttributePoints();
            this.unallocatedSkillPoints = stats.getUnallocatedSkillPoints();
            for (com.bpm.minotaur.gamedata.progression.SkillId s : stats.getUnlockedSkills()) {
                this.unlockedSkills.add(s.name());
            }
        }

        // Status effects, meal buffs and wounds
        if (player.getStatusManager() != null) {
            this.statusEffects = new ArrayList<>();
            for (com.bpm.minotaur.gamedata.effects.ActiveStatusEffect effect : player.getStatusManager().getActiveEffects()) {
                this.statusEffects.add(new com.bpm.minotaur.gamedata.effects.ActiveStatusEffect(
                        effect.getType(), effect.getDuration(), effect.getPotency()));
            }
        }
        this.mealEffects = new ArrayList<>(player.getActiveMealEffects());
        this.fieldRestCooldownTurns = player.getFieldRestCooldownTurns();
        com.bpm.minotaur.gamedata.injury.InjuryManager injuryManager = player.getInjuryManager();
        if (injuryManager != null) {
            this.injuries = new ArrayList<>();
            for (com.bpm.minotaur.gamedata.injury.InjuryRecord record : injuryManager.getInjuries().values()) {
                InjurySaveData d = new InjurySaveData();
                d.bodyPart = record.getBodyPart();
                d.injuryType = record.getInjuryType();
                d.severity = record.getSeverity();
                d.treated = record.isTreated();
                d.infected = record.isInfected();
                d.turnsUntreated = record.getTurnsUntreated();
                d.bleedTicksRemaining = record.getBleedTicksRemaining();
                this.injuries.add(d);
            }
            this.illnessStage = injuryManager.getIllnessStage();
            this.illnessTimer = injuryManager.getIllnessTimer();
            this.injuryStepCounter = injuryManager.getStepCounter();
            this.bleedDamageThisRun = injuryManager.getBleedDamageThisRun();
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
            this.wornBelt = eq.getWornBelt() != null ? new ItemSaveData(eq.getWornBelt()) : null;
        }

        this.traitId = player.getTraitId();
        this.pendingTraitOffer = new java.util.ArrayList<>(player.getPendingTraitOffer());
        this.respawnsSinceChoice = player.getRespawnsSinceChoice();

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
        this.knownSpellIds = new ArrayList<>(player.getKnownSpellIds());
        this.permanentSpellIds = new ArrayList<>(player.getPermanentSpellIds());
        this.runSpellIds = new ArrayList<>(player.getRunSpellIds());
        java.util.Collections.addAll(this.preparedSpells, player.getPreparedSpells());
        this.unlockedSpellSlots = player.getUnlockedSpellSlots();
        com.bpm.minotaur.gamedata.spells.TomeChoice pending = player.getPendingTomeChoice();
        if (pending != null && pending.getTomeItem() != null) {
            this.pendingTomeType = pending.getTomeItem().getType();
            this.pendingTomeOptions.addAll(pending.getOptions());
            this.pendingTomeRerolls = pending.getRerollsLeft();
        }
    }

    public void applyToPlayer(Player player, ItemDataManager itemDataManager, AssetManager assetManager) {
        if (player == null) {
            return;
        }

        player.restoreBlood(blood);

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
            stats.setShot(shot);
            stats.setPowderDampness(powderDampness);
            stats.setTreasureScore(treasureScore);

            if (temporaryHP != null) stats.setTemporaryHP(temporaryHP);
            if (kindlingCount != null) stats.setKindlingCount(kindlingCount);
            if (cookingWaterCount != null) stats.setCookingWaterCount(cookingWaterCount);
            if (cookingSkill != null) stats.setCookingSkill(cookingSkill);

            stats.setUnallocatedAttributePoints(unallocatedAttributePoints);
            stats.setUnallocatedSkillPoints(unallocatedSkillPoints);
            if (unlockedSkills != null) {
                for (String s : unlockedSkills) {
                    try {
                        stats.unlockSkill(com.bpm.minotaur.gamedata.progression.SkillId.valueOf(s));
                    } catch (Exception ignored) {}
                }
            }
        }

        // Status effects, meal buffs and wounds
        if (statusEffects != null && player.getStatusManager() != null) {
            player.getStatusManager().restoreEffects(statusEffects);
        }
        if (mealEffects != null) {
            player.restoreActiveMealEffects(mealEffects);
        }
        if (fieldRestCooldownTurns != null) {
            player.restoreFieldRestCooldownTurns(fieldRestCooldownTurns);
        }
        com.bpm.minotaur.gamedata.injury.InjuryManager injuryManager = player.getInjuryManager();
        if (injuries != null && injuryManager != null) {
            injuryManager.cureAll();
            for (InjurySaveData d : injuries) {
                if (d == null || d.bodyPart == null || d.injuryType == null) {
                    continue;
                }
                com.bpm.minotaur.gamedata.injury.InjuryRecord record =
                        new com.bpm.minotaur.gamedata.injury.InjuryRecord(d.bodyPart, d.injuryType, d.severity);
                record.restoreState(d.treated, d.infected, d.turnsUntreated, d.bleedTicksRemaining);
                injuryManager.restoreInjury(record);
            }
            injuryManager.restoreClocks(illnessStage, illnessTimer, injuryStepCounter, bleedDamageThisRun);
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
            eq.setWornBelt(wornBelt != null ? wornBelt.toItem(itemDataManager, assetManager) : null);
        }

        player.restoreTrait(traitId, pendingTraitOffer, respawnsSinceChoice);

        // Inventory
        Inventory inv = player.getInventory();
        if (inv != null) {
            inv.clear();
            inv.setRightHand(rightHand != null ? rightHand.toItem(itemDataManager, assetManager) : null);
            inv.setLeftHand(leftHand != null ? leftHand.toItem(itemDataManager, assetManager) : null);
            player.migrateLanternToBelt(); // older saves carried the lantern in the left hand

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
            // Saves from before rations stacked carry them loose, one per slot.
            inv.consolidateStacks();
        }

        // Spells
        if (permanentSpellIds != null || runSpellIds != null) {
            player.restoreSpellbook(permanentSpellIds, runSpellIds, preparedSpells, unlockedSpellSlots);
            player.restorePendingTomeChoice(pendingTomeType, pendingTomeOptions, pendingTomeRerolls);
        } else if (knownSpellIds != null) {
            player.restoreSpellbook(knownSpellIds, preparedSpells, unlockedSpellSlots);
            player.restorePendingTomeChoice(pendingTomeType, pendingTomeOptions, pendingTomeRerolls);
        } else if (knownSpells != null) {
            // Legacy save: keep the starting spellbook and add any legacy name that is a real spell id.
            for (String spName : knownSpells) {
                if (spName != null && SpellDataManager.getSpell(spName) != null) {
                    player.learnSpellId(spName);
                }
            }
        }
    }
}
