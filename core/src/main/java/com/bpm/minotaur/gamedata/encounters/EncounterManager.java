package com.bpm.minotaur.gamedata.encounters;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EncounterManager {

    private final ObjectMap<String, Encounter> encounters = new ObjectMap<>();
    private final List<String> allEncounterIds = new ArrayList<>();
    private final Random random = new Random();

    public void load() {
        Json json = new Json();
        FileHandle file = Gdx.files.internal("data/encounters.json");

        if (!file.exists()) {
            Gdx.app.error("EncounterManager", "encounters.json not found!");
            return;
        }

        JsonValue root = new JsonReader().parse(file);
        JsonValue list = root.get("encounters");

        for (JsonValue entry : list) {
            Encounter encounter = json.readValue(Encounter.class, entry);
            encounters.put(encounter.id, encounter);
            allEncounterIds.add(encounter.id);
        }

        Gdx.app.log("EncounterManager", "Loaded " + encounters.size + " encounters.");
    }

    public void queueAssets(AssetManager assetManager) {
        for (Encounter e : encounters.values()) {
            if (e.imagePath != null && !e.imagePath.isEmpty()) {
                assetManager.load(e.imagePath, Texture.class);
            }
        }
    }

    public Encounter getEncounter(String id) {
        return encounters.get(id);
    }

    public boolean checkRequirement(com.bpm.minotaur.gamedata.player.Player player, EncounterChoice.Requirement req) {
        if (req == null)
            return true;
        if ("PERCEPTION".equals(req.stat)) {
            // Player doesn't have perception stat yet in stats?
            // The JSON example said "PERCEPTION", but PlayerStats might not have it.
            // Assuming we use Wisdom/Spiritual or just return true for now if not impl.
            // Let's check stats.
            return true;
        }
        if ("SPIRITUAL_STRENGTH".equals(req.stat)) {
            return player.getCurrentMP() >= req.value;
        }
        if ("DEXTERITY".equals(req.stat)) {
            return player.getDexterity() >= req.value;
        }
        return true;
    }

    public boolean checkCost(com.bpm.minotaur.gamedata.player.Player player, EncounterChoice.Cost cost) {
        if (cost == null)
            return true;
        if ("MAX_HP".equals(cost.type)) {
            return player.getMaxHP() > cost.amount; // Ensure > 0 left?
        }
        if ("HP".equals(cost.type)) {
            return player.getCurrentHP() > cost.amount;
        }
        return true;
    }

    public void payCost(com.bpm.minotaur.gamedata.player.Player player, EncounterChoice.Cost cost) {
        if (cost == null)
            return;
        if ("MAX_HP".equals(cost.type)) {
            player.getStats().setMaxHP(player.getMaxHP() - cost.amount);
            // Clamp Life
            if (player.getCurrentHP() > player.getMaxHP())
                player.setCurrentHP(player.getMaxHP());
        }
        if ("HP".equals(cost.type)) {
            player.takeDamage(cost.amount, com.bpm.minotaur.gamedata.DamageType.PHYSICAL);
        }
    }

    public void resolveResult(EncounterResult result, com.bpm.minotaur.gamedata.player.Player player,
            com.bpm.minotaur.managers.GameEventManager eventManager,
            com.bpm.minotaur.gamedata.item.ItemDataManager itemDataManager,
            com.bpm.minotaur.gamedata.monster.MonsterDataManager monsterDataManager,
            AssetManager assetManager,
            com.bpm.minotaur.gamedata.Maze maze) {
        if (result == null)
            return;

        if (result.text != null) {
            eventManager.addEvent(new com.bpm.minotaur.gamedata.GameEvent(result.text, 3f));
        }

        switch (result.type) {
            case HEAL:
                player.setWarStrength(Math.min(player.getEffectiveMaxHP(), player.getCurrentHP() + result.amount));
                break;
            case DAMAGE:
                player.takeDamage(result.amount, com.bpm.minotaur.gamedata.DamageType.PHYSICAL);
                break;
            case POISON:
                player.takeStatusEffectDamage(result.amount, com.bpm.minotaur.gamedata.DamageType.POISON);
                player.getStatusManager().addEffect(
                        com.bpm.minotaur.gamedata.effects.StatusEffectType.POISONED, 10, 1, true);
                break;
            case GIVE_ITEM:
                if (result.itemId != null) {
                    try {
                        com.bpm.minotaur.gamedata.item.Item.ItemType type = com.bpm.minotaur.gamedata.item.Item.ItemType
                                .valueOf(result.itemId);
                        com.bpm.minotaur.gamedata.item.Item item = itemDataManager.createItem(type,
                                (int) player.getPosition().x, (int) player.getPosition().y, null, assetManager);
                        if (!player.getInventory().pickupToBackpack(item)) {
                            if (maze != null)
                                maze.addItem(item);
                            eventManager.addEvent(
                                    new com.bpm.minotaur.gamedata.GameEvent("Inventory full! Item dropped.", 2f));
                        } else {
                            eventManager.addEvent(
                                    new com.bpm.minotaur.gamedata.GameEvent("Obtained " + item.getDisplayName(), 2f));
                        }
                    } catch (Exception e) {
                        Gdx.app.error("EncounterManager", "Invalid Item ID in event: " + result.itemId);
                    }
                }
                break;
            case GIVE_XP:
                player.addExperience(result.amount, eventManager);
                break;
            case SPAWN_MONSTER:
                if (result.monsterId != null && maze != null) {
                    try {
                        com.bpm.minotaur.gamedata.monster.Monster.MonsterType mType = com.bpm.minotaur.gamedata.monster.Monster.MonsterType
                                .valueOf(result.monsterId);

                        int level = player.getStats().getLevel();
                        com.bpm.minotaur.gamedata.monster.MonsterVariant variant = monsterDataManager
                                .getRandomVariantForMonster(mType, level);

                        if (variant != null) {
                            com.bpm.minotaur.gamedata.monster.MonsterColor color = variant.color;

                            int px = (int) player.getPosition().x;
                            int py = (int) player.getPosition().y;

                            com.bpm.minotaur.gamedata.monster.Monster monster = new com.bpm.minotaur.gamedata.monster.Monster(
                                    mType, px, py, color, monsterDataManager, assetManager);

                            monster.scaleStats(level);

                            maze.addMonster(monster);
                            eventManager.addEvent(
                                    new com.bpm.minotaur.gamedata.GameEvent("A " + mType.name() + " appears!", 2f));
                        }
                    } catch (Exception e) {
                        Gdx.app.error("EncounterManager", "Failed to spawn monster: " + result.monsterId, e);
                    }
                }
                break;
            default:
                break;
        }
    }

    public String getRandomEncounterId() {
        if (allEncounterIds.isEmpty())
            return null;
        return allEncounterIds.get(random.nextInt(allEncounterIds.size()));
    }
}
