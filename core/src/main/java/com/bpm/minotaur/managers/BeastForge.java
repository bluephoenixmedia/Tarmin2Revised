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
        int max = Math.round(kind.hp * (material != null ? material.hpMult : 1f));
        m.setMaxHP(max);
        m.setCurrentHP(Math.max(1, Math.min(hp, max)));
        if (material != null) m.setArmorClass(m.getArmorClass() + material.armor);
        int bite = (material != null ? material.damage : 0) + (breath != null ? breath.damage : 0);
        m.setDamageDice(SealLord.withDamageBonus(m.getDamageDice(), bite));
        try {
            m.setExtraWeakness(DamageType.valueOf(b.weakness));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // A weakness the game no longer knows: the beast simply has none.
        }
        if (m.getScale() != null) m.getScale().set(SCALE_X, kind.scaleY);
    }

    /** A beast's whole hit points, before any wound. */
    public static int wholeHp(Megabeast b, MegabeastCatalog catalog) {
        MegabeastCatalog.Material material = catalog.material(b.materialId);
        return Math.round(catalog.archetype(b.archetypeId).hp * (material != null ? material.hpMult : 1f));
    }
}
