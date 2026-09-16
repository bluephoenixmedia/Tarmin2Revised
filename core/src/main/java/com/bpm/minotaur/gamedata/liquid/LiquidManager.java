package com.bpm.minotaur.gamedata.liquid;

import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.RingEffectType;
import com.bpm.minotaur.gamedata.player.Player;

/**
 * Manages chunk liquid hazard grids and player step-count exposure simulation.
 */
public class LiquidManager {

    public static final int CHUNK_SIZE = 32;
    public static final int MAX_EXPOSURE = 20;

    private final byte[][] grid = new byte[CHUNK_SIZE][CHUNK_SIZE];
    private int exposureSteps = 0;
    private LiquidType currentExposureType = LiquidType.NONE;

    public LiquidManager() {
    }

    public void setLiquidAt(int x, int y, LiquidType type) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_SIZE) {
            grid[x][y] = type != null ? type.getId() : 0;
        }
    }

    public LiquidType getLiquidAt(int x, int y) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_SIZE) {
            return LiquidType.fromId(grid[x][y]);
        }
        return LiquidType.NONE;
    }

    public boolean hasLiquidAt(int x, int y) {
        return getLiquidAt(x, y) != LiquidType.NONE;
    }

    public int getExposureSteps() {
        return exposureSteps;
    }

    public void setExposureSteps(int steps) {
        this.exposureSteps = Math.max(0, Math.min(MAX_EXPOSURE, steps));
        if (this.exposureSteps == 0) {
            this.currentExposureType = LiquidType.NONE;
        }
    }

    public LiquidType getCurrentExposureType() {
        return currentExposureType;
    }

    /**
     * Called whenever player steps onto a tile in the chunk.
     */
    public void onPlayerStep(int x, int y, Player player, GameEventManager eventManager) {
        if (player == null) return;
        LiquidType type = getLiquidAt(x, y);

        if (type != LiquidType.NONE) {
            boolean immune = isImmuneToLiquid(player, type);
            if (!immune) {
                exposureSteps = Math.min(MAX_EXPOSURE, exposureSteps + 1);
                currentExposureType = type;

                // Threshold Debuffs
                if (type == LiquidType.BLACK_MUCK) {
                    if (exposureSteps >= 5) {
                        int newTox = Math.min(player.getStats().getMaxToxicity(), player.getStats().getToxicity() + 3);
                        player.getStats().setToxicity(newTox);
                        if (eventManager != null && exposureSteps % 3 == 0) {
                            eventManager.addEvent(new GameEvent("Toxic muck seeps through! (+3 Toxicity)", 1.5f));
                        }
                    }
                    if (exposureSteps >= 18) {
                        player.takeDamage(2, DamageType.POISON);
                        if (eventManager != null) {
                            eventManager.addEvent(new GameEvent("Necrotic agony burns your flesh! (2 Toxic dmg)", 1.5f));
                        }
                    }
                } else if (type == LiquidType.WATER) {
                    if (exposureSteps == 10 && eventManager != null) {
                        eventManager.addEvent(new GameEvent("Waterlogged! Your boots squelch and cold seeps into your bones.", 2f));
                    }
                } else if (type == LiquidType.BLOOD) {
                    if (exposureSteps == 10 && eventManager != null) {
                        eventManager.addEvent(new GameEvent("Gore-soaked! Beasts can scent your blood trail across corridors.", 2.5f));
                    }
                }
            }
        } else {
            // Dry land drainage
            if (exposureSteps > 0) {
                exposureSteps--;
                if (exposureSteps == 0) {
                    currentExposureType = LiquidType.NONE;
                    if (eventManager != null) {
                        eventManager.addEvent(new GameEvent("You dry off on solid ground.", 1.2f));
                    }
                }
            }
        }
    }

    private boolean isImmuneToLiquid(Player player, LiquidType type) {
        if (player.getEquipment() == null) return false;

        // Ring of Levitation
        if (player.getEquipment().hasRingEffect(RingEffectType.LEVITATION)) {
            return true;
        }

        // Check boots & armor equipment
        Item boots = player.getEquipment().getWornBoots();
        if (boots != null && boots.getFriendlyName() != null) {
            String name = boots.getFriendlyName().toLowerCase();
            if (type == LiquidType.WATER && (name.contains("wading") || name.contains("waterproof") || name.contains("swiftness"))) {
                return true;
            }
            if (type == LiquidType.BLACK_MUCK && (name.contains("hazmat") || name.contains("sealed"))) {
                return true;
            }
        }

        Item chest = player.getEquipment().getWornChest();
        if (chest != null && chest.getFriendlyName() != null) {
            String name = chest.getFriendlyName().toLowerCase();
            if (type == LiquidType.BLACK_MUCK && (name.contains("hazmat") || name.contains("sealed") || name.contains("veil"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compact string serialization of non-empty liquid coordinates: "x,y,id;x,y,id;..."
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                if (grid[x][y] != 0) {
                    sb.append(x).append(",").append(y).append(",").append(grid[x][y]).append(";");
                }
            }
        }
        return sb.toString();
    }

    public static LiquidManager deserialize(String data) {
        LiquidManager lm = new LiquidManager();
        if (data == null || data.trim().isEmpty()) return lm;

        String[] tiles = data.split(";");
        for (String tile : tiles) {
            String[] parts = tile.split(",");
            if (parts.length == 3) {
                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    byte id = Byte.parseByte(parts[2]);
                    lm.setLiquidAt(x, y, LiquidType.fromId(id));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return lm;
    }
}
