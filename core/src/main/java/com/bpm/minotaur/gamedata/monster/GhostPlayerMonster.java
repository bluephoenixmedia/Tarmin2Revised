package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.bones.BonesData;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.save.ItemSaveData;
import com.bpm.minotaur.gamedata.save.PlayerSaveData;
import com.bpm.minotaur.managers.BalanceLogger;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.SoundManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirror-match boss monster representing the ghost of a player from a previous death.
 * Inherits all stats, AC, equipment dice pool, memorized spells, and spectral traits.
 */
public class GhostPlayerMonster extends Monster {

    private final BonesData bonesData;
    private final String ghostPlayerName;
    private final List<String> knownSpells;

    public GhostPlayerMonster(BonesData bonesData, float startX, float startY, AssetManager assetManager) {
        super(MonsterType.PLAYER_GHOST, 20, 10, startX, startY);
        this.bonesData = bonesData;
        this.ghostPlayerName = (bonesData != null && bonesData.playerName != null) ? bonesData.playerName : "Fallen Hero";
        this.knownSpells = new ArrayList<>();

        PlayerSaveData pd = (bonesData != null) ? bonesData.playerData : null;
        if (pd != null) {
            // Stats transfer
            int hp = Math.max(20, pd.maxHP);
            setMaxHP(hp);
            setCurrentHP(hp);

            int mp = Math.max(10, pd.maxMP);
            setMaxMP(mp);
            setCurrentMP(mp);

            setIntelligence(pd.intelligence);
            setDexterity(pd.dexterity);
            setLevel(Math.max(1, pd.level));
            setBaseExperience(pd.level * 80);

            // Compute armor class
            int ac = 10 + Math.max(0, (pd.dexterity - 10) / 2);
            ac += getArmorBonus(pd.wornHelmet);
            ac += getArmorBonus(pd.wornChest);
            ac += getArmorBonus(pd.wornArms);
            ac += getArmorBonus(pd.wornGauntlets);
            ac += getArmorBonus(pd.wornLegs);
            ac += getArmorBonus(pd.wornBoots);
            ac += getArmorBonus(pd.wornShield);
            setArmorClass(Math.max(10, ac));

            // Weapon damage dice
            if (pd.rightHand != null && pd.rightHand.type != null) {
                setDamageDice(inferWeaponDice(pd.rightHand.type.name()));
            } else {
                setDamageDice("1d4+2");
            }

            // Spells
            if (pd.knownSpells != null) {
                this.knownSpells.addAll(pd.knownSpells);
            }
            if (!this.knownSpells.isEmpty()) {
                setAiType(MonsterTemplate.AiType.TACTICAL);
                setSpellChance(35);
                setHealThreshold(0.40f);
            } else {
                setAiType(MonsterTemplate.AiType.AGGRESSIVE);
            }

            // Ranged weapon check
            if (pd.arrows > 0 && isRangedWeapon(pd.rightHand)) {
                setHasRangedAttack(true);
                setAttackRange(4);
            }
        }

        // Texture fallback: try wraith
        if (assetManager != null) {
            String wraithPath = "images/monsters/wraith.png";
            if (assetManager.isLoaded(wraithPath, Texture.class)) {
                setTexture(assetManager.get(wraithPath, Texture.class));
            }
        }
    }

    private int getArmorBonus(ItemSaveData item) {
        if (item == null || item.type == null) return 0;
        String name = item.type.name().toUpperCase();
        if (name.contains("PLATE") || name.contains("HAUBERK")) return 4;
        if (name.contains("CHAIN") || name.contains("SCALE")) return 3;
        if (name.contains("LEATHER") || name.contains("PADDED")) return 2;
        if (name.contains("SHIELD")) return 2;
        if (name.contains("HELMET") || name.contains("BOOTS") || name.contains("GAUNTLET")) return 1;
        return 1;
    }

    private String inferWeaponDice(String itemType) {
        if (itemType == null) return "1d4";
        String t = itemType.toUpperCase();
        if (t.contains("TWO_HANDED") || t.contains("GREATSWORD") || t.contains("HALBERD")) return "2d6+2";
        if (t.contains("WARHAMMER") || t.contains("BATTLEAXE") || t.contains("BROADSWORD")) return "1d8+2";
        if (t.contains("SWORD") || t.contains("AXE") || t.contains("MACE") || t.contains("SPEAR")) return "1d6+2";
        if (t.contains("DAGGER") || t.contains("KNIFE")) return "1d4+1";
        return "1d6";
    }

    private boolean isRangedWeapon(ItemSaveData item) {
        if (item == null || item.type == null) return false;
        String t = item.type.name().toUpperCase();
        return t.contains("BOW") || t.contains("CROSSBOW");
    }

    public boolean performGhostSpell(Player target, GameEventManager eventManager, SoundManager soundManager) {
        if (getCurrentMP() <= 0 || knownSpells.isEmpty()) {
            return false;
        }

        // 1. Heal if below threshold
        if (getCurrentHP() < getMaxHP() * 0.40f && knownSpells.contains("HEAL") && getCurrentMP() >= 8) {
            setCurrentMP(getCurrentMP() - 8);
            int healAmount = 14 + getIntelligence() / 2;
            setCurrentHP(Math.min(getMaxHP(), getCurrentHP() + healAmount));
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("The ghost of " + ghostPlayerName + " casts Heal! (+" + healAmount + " HP)", 2f));
            }
            if (soundManager != null) soundManager.playSound("player_spiritual_attack");
            return true;
        }

        // 2. Iron Skin
        if (knownSpells.contains("IRON_SKIN") && getCurrentMP() >= 10 && getArmorClass() < 18) {
            setCurrentMP(getCurrentMP() - 10);
            setArmorClass(getArmorClass() + 3);
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("The ghost of " + ghostPlayerName + " hardens with Iron Skin! (+3 AC)", 2f));
            }
            return true;
        }

        // 3. Magic Arrow
        if (knownSpells.contains("MAGIC_ARROW") && getCurrentMP() >= 4) {
            setCurrentMP(getCurrentMP() - 4);
            int spellDmg = 7 + getIntelligence() / 2;
            target.takeSpiritualDamage(spellDmg, DamageType.SORCERY);
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("The ghost of " + ghostPlayerName + " casts Magic Arrow! (" + spellDmg + " dmg)", 2f));
            }
            BalanceLogger.getInstance().logCombatRound("GHOST", "Magic Arrow", -1, spellDmg, target.getWarStrength());
            return true;
        }

        // Fallback default spell if other spells were stored
        setCurrentMP(Math.max(0, getCurrentMP() - 3));
        int spellDmg = 5 + getIntelligence() / 3;
        target.takeSpiritualDamage(spellDmg, DamageType.SORCERY);
        if (eventManager != null) {
            eventManager.addEvent(new GameEvent("The ghost of " + ghostPlayerName + " casts a phantom bolt! (" + spellDmg + " dmg)", 2f));
        }
        return true;
    }

    public BonesData getBonesData() {
        return bonesData;
    }

    public String getGhostPlayerName() {
        return ghostPlayerName;
    }
}
