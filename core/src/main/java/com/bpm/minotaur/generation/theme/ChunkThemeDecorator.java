package com.bpm.minotaur.generation.theme;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.monster.*;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Applies procedural thematic mutations to world chunks.
 */
public class ChunkThemeDecorator {

    public static void decorate(Maze maze, ChunkTheme theme, long seed,
                                MonsterDataManager monsterDataManager,
                                ItemDataManager itemDataManager,
                                AssetManager assetManager) {
        if (maze == null || theme == null) return;
        maze.setChunkTheme(theme);
        Random rng = new Random(seed ^ 0x9E3779B97F4A7C15L);

        // Stamp theme on all transition gates in this chunk
        for (Gate gate : maze.getGates().values()) {
            gate.setTheme(theme);
        }

        switch (theme) {
            case BLOOD_COLOSSEUM:
                decorateBloodColosseum(maze, rng, monsterDataManager, assetManager);
                break;
            case FLOODED_CAVERNS:
                decorateFloodedCaverns(maze, rng);
                break;
            case WANDERING_BATTALION:
                decorateWanderingBattalion(maze, rng, monsterDataManager, assetManager);
                break;
            case MAKESHIFT_GRAVEYARD:
                decorateMakeshiftGraveyard(maze, rng, monsterDataManager, assetManager);
                break;
            case RUINED_CASTLE:
                decorateRuinedCastle(maze, rng, monsterDataManager, assetManager);
                break;
            case OVERGROWN_THICKET:
                decorateOvergrownThicket(maze, rng);
                break;
        }
    }

    private static void decorateBloodColosseum(Maze maze, Random rng,
                                               MonsterDataManager monsterDataManager,
                                               AssetManager assetManager) {
        // Clear central 18x18 area for the arena
        int minX = 7, maxX = 24;
        int minY = 7, maxY = 24;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                // 4 stone pillars
                if ((x == 11 || x == 20) && (y == 11 || y == 20)) {
                    maze.setTile(x, y, 1);
                } else {
                    maze.setTile(x, y, 0); // Open floor
                    if (rng.nextFloat() < 0.40f) {
                        maze.addBlood(x, y, 0.06f + rng.nextFloat() * 0.12f);
                    }
                }
            }
        }

        // Lock all gates until cleared
        for (Gate gate : maze.getGates().values()) {
            gate.setLocked(true);
        }

        // Spawn 8-10 chaotic combatants in the arena
        MonsterType[] gladiatorTypes = {
                MonsterType.ORC, MonsterType.OGRE, MonsterType.TROLL,
                MonsterType.SKELETON, MonsterType.WEREWOLF, MonsterType.GOBLIN
        };

        int count = 8 + rng.nextInt(3);
        List<GridPoint2> spawnPoints = findOpenTiles(maze, minX + 1, minY + 1, maxX - 1, maxY - 1);
        for (int i = 0; i < count && !spawnPoints.isEmpty(); i++) {
            GridPoint2 pt = spawnPoints.remove(rng.nextInt(spawnPoints.size()));
            MonsterType type = gladiatorTypes[rng.nextInt(gladiatorTypes.length)];
            Monster m = createMonster(type, pt.x, pt.y, MonsterColor.RED, Faction.CHAOS_BERSERK, 15,
                    monsterDataManager, assetManager);
            maze.addMonster(m);
        }
    }

    private static void decorateFloodedCaverns(Maze maze, Random rng) {
        int width = maze.getWidth();
        int height = maze.getHeight();

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (maze.getWallDataAt(x, y) == 0) {
                    float roll = rng.nextFloat();
                    if (roll < 0.30f) {
                        maze.getLiquidManager().setLiquidAt(x, y, LiquidType.WATER);
                    } else if (roll < 0.40f) {
                        maze.getLiquidManager().setLiquidAt(x, y, LiquidType.BLOOD);
                    } else if (roll < 0.50f) {
                        maze.getLiquidManager().setLiquidAt(x, y, LiquidType.BLACK_MUCK);
                    }
                }
            }
        }
    }

    private static void decorateWanderingBattalion(Maze maze, Random rng,
                                                   MonsterDataManager monsterDataManager,
                                                   AssetManager assetManager) {
        List<GridPoint2> openTiles = findOpenTiles(maze, 8, 8, 23, 23);
        if (openTiles.isEmpty()) return;

        GridPoint2 center = openTiles.get(rng.nextInt(openTiles.size()));

        // Commander
        Monster commander = createMonster(MonsterType.OGRE, center.x, center.y, MonsterColor.ORANGE,
                Faction.TARMIN_LEGION, 50, monsterDataManager, assetManager);
        maze.addMonster(commander);

        // 4 Legionnaires
        int[][] offsets = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
        for (int[] off : offsets) {
            int sx = center.x + off[0];
            int sy = center.y + off[1];
            if (maze.getWallDataAt(sx, sy) == 0 && !maze.getMonsters().containsKey(new GridPoint2(sx, sy))) {
                Monster soldier = createMonster(MonsterType.ORC, sx, sy, MonsterColor.WHITE,
                        Faction.TARMIN_LEGION, 10, monsterDataManager, assetManager);
                maze.addMonster(soldier);
            }
        }
    }

    private static void decorateMakeshiftGraveyard(Maze maze, Random rng,
                                                   MonsterDataManager monsterDataManager,
                                                   AssetManager assetManager) {
        // Clear 12x12 clearing
        int minX = 10, maxX = 21;
        int minY = 10, maxY = 21;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                maze.setTile(x, y, 0);
            }
        }

        // Gravedigger NPC (Neutral)
        Monster gravedigger = createMonster(MonsterType.DWARF, 16, 16, MonsterColor.WHITE,
                Faction.NEUTRAL, 40, monsterDataManager, assetManager);
        maze.addMonster(gravedigger);

        // 4 Undead guardians in corners
        int[][] graves = { {12, 12}, {19, 12}, {12, 19}, {19, 19} };
        for (int[] g : graves) {
            Monster skeleton = createMonster(MonsterType.SKELETON, g[0], g[1], MonsterColor.WHITE,
                    Faction.UNDEAD, 5, monsterDataManager, assetManager);
            maze.addMonster(skeleton);
        }
    }

    private static void decorateRuinedCastle(Maze maze, Random rng,
                                             MonsterDataManager monsterDataManager,
                                             AssetManager assetManager) {
        // Clear outer courtyard
        for (int y = 9; y <= 22; y++) {
            for (int x = 9; x <= 22; x++) {
                maze.setTile(x, y, 0);
            }
        }

        // Inner keep throne room
        for (int x = 13; x <= 18; x++) {
            maze.setTile(x, 13, 1);
            maze.setTile(x, 18, 1);
        }
        for (int y = 13; y <= 18; y++) {
            maze.setTile(13, y, 1);
            maze.setTile(18, y, 1);
        }
        // Door into throne room
        maze.setTile(15, 13, 0);
        maze.setTile(16, 13, 0);
        for (int y = 14; y <= 17; y++) {
            for (int x = 14; x <= 17; x++) {
                maze.setTile(x, y, 0);
            }
        }

        // Castle Warden Boss in throne
        Monster warden = createMonster(MonsterType.MINOTAUR, 15, 16, MonsterColor.RED,
                Faction.TARMIN_LEGION, 80, monsterDataManager, assetManager);
        maze.addMonster(warden);
    }

    private static void decorateOvergrownThicket(Maze maze, Random rng) {
        int width = maze.getWidth();
        int height = maze.getHeight();

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (maze.getWallDataAt(x, y) == 0 && rng.nextFloat() < 0.20f) {
                    maze.getLiquidManager().setLiquidAt(x, y, LiquidType.WATER);
                }
            }
        }
    }

    private static List<GridPoint2> findOpenTiles(Maze maze, int minX, int minY, int maxX, int maxY) {
        List<GridPoint2> list = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (maze.getWallDataAt(x, y) == 0 && !maze.getMonsters().containsKey(new GridPoint2(x, y))) {
                    list.add(new GridPoint2(x, y));
                }
            }
        }
        return list;
    }

    private static Monster createMonster(MonsterType type, int x, int y, MonsterColor color,
                                         Faction faction, int hpBonus,
                                         MonsterDataManager dataManager, AssetManager assetManager) {
        Monster m;
        if (dataManager != null && assetManager != null) {
            m = new Monster(type, x, y, color, dataManager, assetManager);
        } else {
            m = new Monster(type, 30 + hpBonus, 12, x, y);
        }
        m.setFaction(faction);
        if (hpBonus > 0) {
            m.setCurrentHP(m.getCurrentHP() + hpBonus);
        }
        return m;
    }
}
