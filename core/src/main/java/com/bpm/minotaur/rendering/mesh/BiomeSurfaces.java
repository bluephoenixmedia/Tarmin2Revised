package com.bpm.minotaur.rendering.mesh;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.generation.Biome;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Each biome's own wall and floor art, as {@link Biome} names it: the forest's
 * mossy cliffs and needle floor, the desert's sandstone and sand. A biome that
 * names none, or whose art is missing, takes the maze's.
 */
public final class BiomeSurfaces implements Disposable {

    private final Map<Biome, Texture> walls = new EnumMap<>(Biome.class);
    private final Map<Biome, Texture> floors = new EnumMap<>(Biome.class);
    private final Map<Biome, Texture> shelterWalls = new EnumMap<>(Biome.class);
    private final Map<String, Texture> loaded = new HashMap<>();

    /** Loads every biome's surface art that exists on disk. Needs a GL context. */
    public static BiomeSurfaces load() {
        BiomeSurfaces surfaces = new BiomeSurfaces();
        for (Biome biome : Biome.values()) {
            Texture wall = surfaces.texture(biome.getWallTexturePath());
            if (wall != null) surfaces.walls.put(biome, wall);
            Texture floor = surfaces.texture(biome.getFloorTexturePath());
            if (floor != null) surfaces.floors.put(biome, floor);
            Texture shelter = surfaces.texture(biome.getShelterWallTexturePath());
            if (shelter != null) surfaces.shelterWalls.put(biome, shelter);
        }
        return surfaces;
    }

    public Texture wallFor(Biome biome, Texture fallback) {
        Texture t = (biome != null) ? walls.get(biome) : null;
        return (t != null) ? t : fallback;
    }

    /** An outpost shelter's walls in this biome, or null where shelters wear the chunk's own walls. */
    public Texture shelterWallFor(Biome biome) {
        return biome != null ? shelterWalls.get(biome) : null;
    }

    public Texture floorFor(Biome biome, Texture fallback) {
        Texture t = (biome != null) ? floors.get(biome) : null;
        return (t != null) ? t : fallback;
    }

    private Texture texture(String path) {
        if (path == null) return null;
        Texture t = loaded.get(path);
        if (t == null && Gdx.files.internal(path).exists()) {
            t = new Texture(Gdx.files.internal(path));
            t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            loaded.put(path, t);
        }
        return t;
    }

    @Override
    public void dispose() {
        for (Texture t : loaded.values()) t.dispose();
        loaded.clear();
        walls.clear();
        floors.clear();
        shelterWalls.clear();
    }
}
