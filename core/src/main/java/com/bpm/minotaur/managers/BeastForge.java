package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.boss.SealLord;
import com.bpm.minotaur.gamedata.history.Megabeast;
import com.bpm.minotaur.gamedata.history.beast.MegabeastCatalog;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;

/**
 * Puts a megabeast of the history onto a monster of its archetype's body (plan D33, T3.3): its
 * name, its hit points and the wounds it carries, its material's hide, its breath's bite, and the
 * weakness the chronicle whispers about. A megabeast answers to no house: it attacks anything.
 */
public final class BeastForge {

    /** Billboard width at the renderer's clamp, as for the bridge guardian, so the height shows. */
    static final float SCALE_X = 0.82f;

    private BeastForge() {
    }

    /** {@code hp}: the beast's remaining hit points, or anything above its maximum for a whole one. */
    public static void dress(Monster m, Megabeast b, MegabeastCatalog catalog, int hp) {
        MegabeastCatalog.Archetype kind = catalog.archetype(b.archetypeId);
        MegabeastCatalog.Material material = catalog.material(b.materialId);
        MegabeastCatalog.Breath breath = catalog.breath(b.breathId);
        m.setFaction(Faction.CHAOS_BERSERK);
        m.setHouseId(-1);
        m.setMegabeastId(b.id);
        m.setDisplayName(b.name);
        int max = wholeHp(b, catalog);
        m.setMaxHP(max);
        m.setCurrentHP(Math.max(1, Math.min(hp, max)));
        if (material != null) m.setArmorClass(m.getArmorClass() + material.armor);
        int bite = (material != null ? material.damage : 0) + (breath != null ? breath.damage : 0) + depthOf(b) * DEPTH_BITE;
        m.setDamageDice(SealLord.withDamageBonus(m.getDamageDice(), bite));
        try {
            m.setExtraWeakness(DamageType.valueOf(b.weakness));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // A weakness the game no longer knows: the beast simply has none.
        }
        if (m.getScale() != null) m.getScale().set(SCALE_X, kind.scaleY);
        if (b.isPacified()) m.setPeaceful(true);
    }

    /** How near the beast a trophy must lie to be taken as an offering. */
    public static final int OFFERING_REACH = 3;

    /**
     * A trophy of the houses -- a signet, a banner -- lying within reach of a beast at {@code at}
     * is taken as an offering (plan T4.5): removed from the floor and returned. Null if there is none.
     */
    public static com.bpm.minotaur.gamedata.item.Item takeOffering(com.bpm.minotaur.gamedata.Maze maze,
            com.badlogic.gdx.math.GridPoint2 at) {
        for (java.util.Map.Entry<com.badlogic.gdx.math.GridPoint2, com.bpm.minotaur.gamedata.item.Item> e
                : new java.util.ArrayList<>(maze.getItems().entrySet())) {
            com.bpm.minotaur.gamedata.item.Item item = e.getValue();
            if (item == null || !isOffering(item)) continue;
            if (Math.abs(e.getKey().x - at.x) + Math.abs(e.getKey().y - at.y) > OFFERING_REACH) continue;
            maze.removeItem(item);
            return item;
        }
        return null;
    }

    /** What a beast will take: the houses' own trophies, a signet or a banner. */
    static boolean isOffering(com.bpm.minotaur.gamedata.item.Item item) {
        return item.getType() == com.bpm.minotaur.gamedata.item.Item.ItemType.SIGNET_RING
                || item.getType() == com.bpm.minotaur.gamedata.item.Item.ItemType.TORN_BANNER;
    }

    /** A beast's whole hit points, before any wound: the deeper its lair, the greater it is (D35). */
    public static int wholeHp(Megabeast b, MegabeastCatalog catalog) {
        MegabeastCatalog.Material material = catalog.material(b.materialId);
        float depth = 1f + depthOf(b) * DEPTH_HP;
        return Math.round(catalog.archetype(b.archetypeId).hp * (material != null ? material.hpMult : 1f) * depth);
    }

    /** Hit points gained per stratum below the first. */
    static final float DEPTH_HP = 0.15f;
    /** Damage gained per stratum below the first. */
    static final int DEPTH_BITE = 1;

    private static int depthOf(Megabeast b) {
        return Math.max(0, b.lairLevel - 2);
    }
}
