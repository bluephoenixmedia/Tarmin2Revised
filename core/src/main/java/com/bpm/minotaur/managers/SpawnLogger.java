package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.bpm.minotaur.gamedata.item.Item;

import com.bpm.minotaur.gamedata.monster.Monster;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * Singleton logger for tracking item and monster usage spawns.
 * Tracks the "why" (reason) and "what" (stats).
 */
public class SpawnLogger {

    private static SpawnLogger instance;
    private FileHandle logFile;
    private static final String LOG_PATH = "logs/spawn_log.txt";

    private SpawnLogger() {
        // Create or overwrite the session log
        logFile = Gdx.files.local(LOG_PATH);
    }

    public static SpawnLogger getInstance() {
        if (instance == null) {
            instance = new SpawnLogger();
            String header = "--- SPAWN LOG SESSION: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                    + " ---\n";
            instance.writeRaw(header);
        }
        return instance;
    }

    private void writeRaw(String str) {
        System.err.print("SPAWNLOGGER: " + str);
        try {
            logFile.writeString(str, true);
        } catch (Exception e) {
            Gdx.app.error("SpawnLogger", "Failed to write to log file", e);
        }
    }

    private void log(String category, String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String entry = String.format("[%s] [%s] %s\n", timestamp, category, message);
        writeRaw(entry);
    }

    public void logMonsterSpawn(Monster monster, String reason) {
        if (monster == null)
            return;

        StringBuilder details = new StringBuilder();
        details.append(String.format("Type: %s", monster.getMonsterType()));

        details.append(String.format(" | HP: %d/%d | AC: %d | Dmg: %s",
                monster.getMaxHP(), monster.getMaxHP(), monster.getArmorClass(), monster.getDamageDice()));

        details.append(String.format(" | Pos: (%d, %d)",
                (int) monster.getPosition().x, (int) monster.getPosition().y));

        if (monster.getTemplate() != null
                && (monster.getTemplate().generationFlags
                        & com.bpm.minotaur.gamedata.monster.MonsterTemplate.G_UNIQ) != 0) {
            details.append(" | UNIQUE");
        }

        log("MONSTER_SPAWN", String.format("Reason: %s | %s", reason, details.toString()));
    }

    public void logItemSpawn(Item item, String reason) {
        if (item == null)
            return;

        StringBuilder details = new StringBuilder();
        details.append(String.format("Name: %s | Type: %s | Color: %s",
                item.getDisplayName(), item.getType(), item.getItemColor()));

        details.append(String.format(" | Val: %d | Beatitude: %s",
                item.getBaseValue(), item.getBeatitude()));

        if (item.getEnchantment() != 0) {
            details.append(" | Ench: " + item.getEnchantment());
        }

        if (!item.getModifiers().isEmpty()) {
            String mods = item.getModifiers().stream()
                    .map(m -> m.displayName)
                    .collect(Collectors.joining(", "));
            details.append(" | Mods: [" + mods + "]");
        }

        if (item.getPosition() != null) {
            details.append(String.format(" | Pos: (%d, %d)",
                    (int) item.getPosition().x, (int) item.getPosition().y));
        }

        log("ITEM_SPAWN", String.format("Reason: %s | %s", reason, details.toString()));
    }
}
