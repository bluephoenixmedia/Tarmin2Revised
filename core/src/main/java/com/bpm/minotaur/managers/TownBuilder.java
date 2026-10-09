package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.ShopInventory;
import com.bpm.minotaur.gamedata.ShopkeeperNpc;
import com.bpm.minotaur.gamedata.history.town.Town;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.generation.ShelterBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Raises a town in its stratum chunk (plan D37, T4.2). The plaza is carved once, when the chunk
 * is made, and cleared of whatever prowled there. Its folk stand at their places around the
 * plaza; they are placed again on every load and never saved, so they are always who the town
 * says they are. The merchant is the travelling merchant, seated here for good.
 */
public final class TownBuilder {

    /** Half the plaza's width: a nine-by-nine square. */
    public static final int PLAZA_HALF = 4;
    /** Where folk stand around the plaza's centre, in order. */
    static final int[][] STANDS = {{-3, 3}, {3, 3}, {-3, -3}, {3, -3}, {0, 3}, {-3, 0}, {3, 0}, {0, -3}};

    private TownBuilder() {
    }

    /** Carves the plaza and seats the merchant; only for a freshly made chunk. */
    public static void raise(Maze maze, Town town, ItemDataManager items, AssetManager assets) {
        int cx = maze.getWidth() / 2;
        int cy = maze.getHeight() / 2;
        ShelterBuilder.clearPlaza(maze, cx, cy, PLAZA_HALF);
        for (Monster m : new ArrayList<>(maze.getMonsters().values())) {
            if (m != null && Math.abs(m.getPosition().x - cx) <= PLAZA_HALF + 2 && Math.abs(m.getPosition().y - cy) <= PLAZA_HALF + 2) {
                maze.removeMonster(m);
            }
        }
    }

    /** Stands the town's folk at their places, and seats a merchant if none is here. Every load. */
    public static List<Scenery> populate(Maze maze, Town town, MonsterDataManager monsters, ItemDataManager items,
            AssetManager assets) {
        return populate(maze, town, monsters, items, assets, false, null);
    }

    /** Where a town's guards stand: either side of the plaza's south approach. */
    static final int[][] GUARD_POSTS = {{-1, -PLAZA_HALF}, {1, -PLAZA_HALF}};

    /**
     * As above, and posts the town's guards: at peace with the player unless {@code hostile}.
     * {@code recruit} makes a monster of a type at a tile; null posts no guards.
     */
    public static List<Scenery> populate(Maze maze, Town town, MonsterDataManager monsters, ItemDataManager items,
            AssetManager assets, boolean hostile, com.bpm.minotaur.managers.BattleDirector.Recruiter recruit) {
        if (recruit != null) postGuards(maze, town, hostile, recruit);
        int cx = maze.getWidth() / 2;
        int cy = maze.getHeight() / 2;
        List<Scenery> placed = new ArrayList<>();
        int stand = 0;
        for (Town.Folk f : town.folk) {
            if (f.role == Town.Role.MERCHANT) continue;
            int[] off = STANDS[stand++ % STANDS.length];
            GridPoint2 at = new GridPoint2(cx + off[0], cy + off[1]);
            Scenery old = maze.getScenery().get(at);
            if (old != null && old.isTownsfolk()) continue;
            Scenery s = new Scenery(Scenery.SceneryType.STATUE, at.x, at.y, spritePath(f.sprite, monsters));
            s.setImpassable(true);
            s.setTownsfolk(town.key, f.index);
            bind(s, assets);
            maze.addScenery(s);
            placed.add(s);
        }
        if (maze.getShopkeeper() == null && items != null) {
            ShopkeeperNpc merchant = new ShopkeeperNpc(cx, cy - 1, assets);
            new ShopInventory().stock(merchant, items, assets, maze.getLevel());
            maze.setShopkeeper(merchant);
        }
        return placed;
    }

    private static void postGuards(Maze maze, Town town, boolean hostile,
            com.bpm.minotaur.managers.BattleDirector.Recruiter recruit) {
        int cx = maze.getWidth() / 2;
        int cy = maze.getHeight() / 2;
        for (Monster m : maze.getMonsters().values()) {
            if (m != null && town.key.equals(m.getTownKey())) return;
        }
        String type = guardType(town);
        for (int[] post : GUARD_POSTS) {
            GridPoint2 at = com.bpm.minotaur.managers.WorldManager.findSafeArrivalTile(maze, cx + post[0], cy + post[1]);
            if (at == null) continue;
            Monster guard = recruit.recruit(type, at.x, at.y);
            if (guard == null) continue;
            guard.setTownKey(town.key);
            guard.setFaction(com.bpm.minotaur.gamedata.monster.Faction.NEUTRAL);
            guard.setDisplayName("A guard of " + town.name);
            guard.setPeaceful(!hostile);
            guard.setState(hostile ? Monster.MonsterState.HUNTING : Monster.MonsterState.IDLE);
            maze.addMonster(guard);
        }
    }

    static String guardType(Town town) {
        switch (town.allegiance) {
            case GOBLIN_CLANS: return "HOBGOBLIN";
            case OUTCAST_COVENANT: return "CLOAKED_SKELETON";
            default: return "DWARF";
        }
    }

    private static String spritePath(String monsterType, MonsterDataManager monsters) {
        if (monsters == null) return null;
        try {
            MonsterTemplate t = monsters.getTemplate(Monster.MonsterType.valueOf(monsterType));
            return t != null ? t.texturePath : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void bind(Scenery s, AssetManager assets) {
        String path = s.getTexturePath();
        if (path == null || assets == null || Gdx.files == null || !Gdx.files.internal(path).exists()) return;
        if (!assets.isLoaded(path)) {
            assets.load(path, Texture.class);
            assets.finishLoadingAsset(path);
        }
        s.setTexture(assets.get(path, Texture.class));
    }
}
