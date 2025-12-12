package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A Singleton logger dedicated to tracking gameplay balance metrics,
 * economy flow, and combat fairness.
 */
public class BalanceLogger {

    private static BalanceLogger instance;
    private FileHandle logFile;
    private static final String LOG_PATH = "logs/game_balance_session.log";

    private BalanceLogger() {
        // Create or overwrite the session log
        logFile = Gdx.files.local(LOG_PATH);
        // Only write header if we are creating it fresh in this session instance
    }

    public static BalanceLogger getInstance() {
        if (instance == null) {
            instance = new BalanceLogger();
            String header = "--- NEW SESSION: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " ---\n";
            instance.writeRaw(header);
        }
        return instance;
    }

    private void writeRaw(String str) {
        try {
            logFile.writeString(str, true);
        } catch (Exception e) {
            Gdx.app.error("BalanceLogger", "Failed to write to log file", e);
        }
    }

    public void log(String category, String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String entry = String.format("[%s] [%s] %s\n", timestamp, category, message);
        writeRaw(entry);
        Gdx.app.log("BALANCE_LOG", category + ": " + message);
    }

    // --- Analytics Helpers: Player ---

    public void logPlayerState(Player player) {
        if (player == null) return;
        int pps = calculatePlayerPowerScore(player);
        String state = String.format("Lvl: %d | XP: %d | WarStr: %d/%d | SpirStr: %d/%d | Food: %d | Arrows: %d | Est. Power Score: %d",
            player.getLevel(),
            player.getExperience(),
            player.getWarStrength(), player.getEffectiveMaxWarStrength(),
            player.getSpiritualStrength(), player.getEffectiveMaxSpiritualStrength(),
            player.getFood(),
            player.getArrows(),
            pps
        );
        log("PLAYER_STATE", state);
    }

    // --- Analytics Helpers: Combat ---

    public void logCombatStart(Player player, Monster monster) {
        int pps = calculatePlayerPowerScore(player);
        int mcr = calculateMonsterChallengeRating(monster);
        int delta = pps - mcr;

        String prediction = "Even Match";
        if (delta > 20) prediction = "Player Advantage";
        if (delta > 50) prediction = "STOMP";
        if (delta < -10) prediction = "Hard Fight";
        if (delta < -30) prediction = "DEADLY";

        log("COMBAT_START", String.format("Vs %s (MCR: %d) | Player PPS: %d | Delta: %d (%s)",
            monster.getMonsterType(), mcr, pps, delta, prediction));
    }

    public void logCombatRound(String attacker, String action, int roll, int damage, int targetHp) {
        log("COMBAT_ROUND", String.format("%s %s | Roll: %d | Dmg: %d | Target HP: %d",
            attacker, action, roll, damage, targetHp));
    }

    public void logCombatEnd(String result, int turns, int damageTaken) {
        log("COMBAT_END", String.format("Result: %s | Turns: %d | Total Dmg Taken: %d",
            result, turns, damageTaken));
    }

    // --- Analytics Helpers: Economy & Loot ---

    public void logItemSpawn(Item item, int level) {
        if (item == null) return;
        String rarity = "Common/Tan";

        // Comprehensive Rarity Check
        if (item.getItemColor() == ItemColor.GRAY) rarity = "Medium/Gray";
        if (item.getItemColor() == ItemColor.GREEN) rarity = "Regular/Green";
        if (item.getItemColor() == ItemColor.BLUE) rarity = "Rare/Blue";
        if (item.getItemColor() == ItemColor.BLUE_STEEL) rarity = "Fair/BlueSteel";
        if (item.getItemColor() == ItemColor.PURPLE) rarity = "Epic/Purple";
        if (item.getItemColor() == ItemColor.YELLOW) rarity = "Legendary/Yellow";
        if (item.getItemColor() == ItemColor.ORANGE) rarity = "Greater/Orange";
        if (item.getItemColor() == ItemColor.WHITE) rarity = "Super/White";

        // Filter out debris/junk from logs to keep it readable
        if (item.getBaseValue() < 5 && !item.isKey()) return;

        log("LOOT_SPAWN", String.format("Lvl %d | Item: %s (%s) | Val: %d | Rarity: %s",
            level, item.getDisplayName(), item.getCategory(), item.getBaseValue(), rarity));
    }

    public void logEconomy(String event, String detail, int value) {
        log("ECONOMY", String.format("%s: %s (Val/Amt: %d)", event, detail, value));
    }

    /**
     * A heuristic to determine how strong the player is.
     */
    private int calculatePlayerPowerScore(Player player) {
        int score = 0;
        // Base Stats
        score += player.getEffectiveMaxWarStrength();
        score += player.getEffectiveMaxSpiritualStrength();

        // Equipment
        if (player.getEquipment().getWornHelmet() != null) score += player.getEquipment().getWornHelmet().getArmorDefense() * 2;
        if (player.getEquipment().getWornChest() != null) score += player.getEquipment().getWornChest().getArmorDefense() * 2;
        if (player.getEquipment().getWornLegs() != null) score += player.getEquipment().getWornLegs().getArmorDefense() * 2;
        if (player.getEquipment().getWornBoots() != null) score += player.getEquipment().getWornBoots().getArmorDefense() * 2;
        if (player.getEquipment().getWornArms() != null) score += player.getEquipment().getWornArms().getArmorDefense() * 2;
        if (player.getEquipment().getWornShield() != null) score += player.getEquipment().getWornShield().getArmorDefense() * 2;

        // Weapon
        if (player.getInventory().getRightHand() != null) {
            score += player.getInventory().getRightHand().getWarDamage() * 3;
            score += player.getInventory().getRightHand().getSpiritDamage() * 3;
        }

        return score;
    }

    private int calculateMonsterChallengeRating(Monster monster) {
        int score = 0;
        score += monster.getWarStrength();
        score += monster.getSpiritualStrength();
        score += monster.getArmor() * 3;
        score += monster.getDexterity();

        if (monster.hasRangedAttack()) score += 15;

        return score;
    }
}
