package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.boss.SealLord;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;

/**
 * A seal lord's court in the strata beneath its seal site (Houses of the Maze plan T1.12): who
 * stands there, how their traits play out turn by turn, and what the sealed gate above says of
 * them. {@link SealLord} composes the lord; this puts the composition on real monsters.
 */
public final class SealCourt {

    /**
     * The level the court stands on: two strata beneath the seal site, the "far beneath" its gate
     * speaks of. The gash interior (plan T1.13) will replace this single level.
     */
    public static final int COURT_LEVEL = 3;

    /** Extra damage a wrathful lord finds below half health. */
    static final int RAGE_DAMAGE = 4;
    static final int RAGE_SPEED = 2;
    /** Armour a craven lord gains when it shrinks behind its retinue. */
    static final int COWER_ARMOR = 3;

    private SealCourt() {
    }

    /** Whether this chunk at this level is where a seal road's lord still holds court. */
    public static boolean isCourt(int level, int sealRoad, boolean sealHeld) {
        return level == COURT_LEVEL && sealRoad >= 0 && SealLord.gashIndexForRoad(sealRoad) >= 0 && !sealHeld;
    }

    /** Makes {@code m} the lord: identity, faction, and the spec's stats over its own type's. */
    public static void dressLord(Monster m, SealLord.Spec spec, int road) {
        m.setFaction(Faction.MAZE_HOUSE);
        m.setHouseId(spec.houseId);
        m.setFigureId(spec.figureId);
        m.setSealRoad(road);
        m.setSealRole(Monster.SEAL_LORD);
        m.setMaxHP(Math.round(m.getMaxHP() * spec.hpMult));
        m.setCurrentHP(m.getMaxHP());
        applyCombat(m, spec);
        m.setState(Monster.MonsterState.HUNTING);
    }

    /** Makes {@code m} one of the lord's named sworn swords. */
    public static void dressRetainer(Monster m, SealLord.Retainer r, SealLord.Spec spec, int road) {
        m.setFaction(Faction.MAZE_HOUSE);
        m.setHouseId(spec.houseId);
        m.setFigureId(r.figureId);
        m.setSealRoad(road);
        m.setSealRole(Monster.SEAL_RETAINER);
        m.setDisplayName(r.name);
        boolean duel = spec.behaviours.contains(SealLord.Behaviour.DUELIST);
        m.setState(duel ? Monster.MonsterState.IDLE : Monster.MonsterState.HUNTING);
    }

    /**
     * Rebuilds what a saved court member does not carry -- name, damage, armour, speed, traits --
     * from the history. Health is saved and left alone. A member whose lord has since lost the
     * gash keeps only its name.
     */
    public static void reapply(Monster m, HistoryWorld world, DoctrineCatalog catalog) {
        int gash = SealLord.gashIndexForRoad(m.getSealRoad());
        if (gash < 0) return;
        SealLord.Spec spec = SealLord.compose(world, gash, catalog);
        if (m.getSealRole() == Monster.SEAL_LORD) {
            if (spec.figureId == m.getFigureId()) {
                applyCombat(m, spec);
            } else {
                m.setDisplayName(formerName(world, m.getFigureId()));
            }
            return;
        }
        for (SealLord.Retainer r : spec.retinue) {
            if (r.figureId == m.getFigureId()) {
                m.setDisplayName(r.name);
                return;
            }
        }
        m.setDisplayName(formerName(world, m.getFigureId()));
    }

    /**
     * Court members whose lord no longer holds the gash (plan D7: the boss is whoever holds it
     * now). Their seat goes to the current holder's lord when the court is re-seated.
     */
    public static java.util.List<Monster> deposed(Maze maze, HistoryWorld world, DoctrineCatalog catalog) {
        java.util.List<Monster> out = new java.util.ArrayList<>();
        if (maze == null) return out;
        for (Monster m : maze.getMonsters().values()) {
            if (m == null || m.getSealRole() != Monster.SEAL_LORD) continue;
            int gash = SealLord.gashIndexForRoad(m.getSealRoad());
            if (gash >= 0 && SealLord.compose(world, gash, catalog).figureId != m.getFigureId()) {
                out.add(m);
                out.addAll(retinueOf(m, maze));
            }
        }
        return out;
    }

    private static void applyCombat(Monster m, SealLord.Spec spec) {
        m.setDisplayName(spec.name);
        m.setDamageDice(SealLord.withDamageBonus(m.getDamageDice(), spec.damageBonus));
        m.setArmorClass(m.getArmorClass() + spec.armorBonus);
        m.setMoveSpeed(Math.max(1, m.getMoveSpeed() + spec.moveSpeedDelta));
        int bits = 0;
        for (SealLord.Behaviour b : spec.behaviours) bits |= 1 << b.ordinal();
        m.setSealBehaviours(bits);
    }

    private static String formerName(HistoryWorld world, int figureId) {
        Figure f = world.figure(figureId);
        if (f == null) return null;
        House h = world.house(f.houseId);
        return h == null ? f.name : f.name + " of " + h.name;
    }

    static boolean has(Monster m, SealLord.Behaviour b) {
        return (m.getSealBehaviours() & (1 << b.ordinal())) != 0;
    }

    /**
     * A court member's traits, played out at the start of its turn. Returns true when the
     * monster should do nothing else this turn: an honourable lord's retinue standing back.
     */
    public static boolean onTurn(Monster m, Maze maze) {
        if (m.getSealRole() == Monster.SEAL_LORD) {
            int hp = m.getCurrentHP();
            int max = m.getMaxHP();
            if (has(m, SealLord.Behaviour.REGENERATES) && hp < max) {
                m.setCurrentHP(Math.min(max, hp + Math.max(1, max / 60)));
            }
            if (has(m, SealLord.Behaviour.BERSERK_AT_HALF) && !m.isSealRageSpent() && hp * 2 < max) {
                m.setSealRageSpent(true);
                m.setDamageDice(SealLord.withDamageBonus(m.getDamageDice(), RAGE_DAMAGE));
                m.setMoveSpeed(m.getMoveSpeed() + RAGE_SPEED);
            }
            if (has(m, SealLord.Behaviour.CALLS_RETINUE) && !m.isSealCallSpent() && hp * 3 < max) {
                m.setSealCallSpent(true);
                m.setArmorClass(m.getArmorClass() + COWER_ARMOR);
                for (Monster r : retinueOf(m, maze)) r.setState(Monster.MonsterState.HUNTING);
            }
            return false;
        }
        if (m.getSealRole() == Monster.SEAL_RETAINER) {
            Monster lord = lordOf(m, maze);
            boolean lordStandsAlone = lord != null && has(lord, SealLord.Behaviour.DUELIST)
                    && lord.getCurrentHP() * 2 >= lord.getMaxHP();
            if (lordStandsAlone && m.getCurrentHP() >= m.getMaxHP()) {
                m.setState(Monster.MonsterState.IDLE);
                return true;
            }
        }
        return false;
    }

    static Monster lordOf(Monster retainer, Maze maze) {
        if (maze == null) return null;
        for (Monster m : maze.getMonsters().values()) {
            if (m != null && m.getSealRole() == Monster.SEAL_LORD && m.getSealRoad() == retainer.getSealRoad()) return m;
        }
        return null;
    }

    static java.util.List<Monster> retinueOf(Monster lord, Maze maze) {
        java.util.List<Monster> out = new java.util.ArrayList<>();
        if (maze == null) return out;
        for (Monster m : maze.getMonsters().values()) {
            if (m != null && m.getSealRole() == Monster.SEAL_RETAINER && m.getSealRoad() == lord.getSealRoad()) out.add(m);
        }
        return out;
    }

    /**
     * A court member, or any named figure, died at the player's hand: the history records it,
     * and a seal lord gives up its seal. Returns what the player is told, or null.
     */
    public static String onSlain(Monster m, HistoryManager history,
            com.bpm.minotaur.gamedata.shelter.ShelterNetwork network) {
        if (m == null || m.getFigureId() < 0) return null;
        history.recordKill(m.getFigureId());
        if (m.getSealRole() != Monster.SEAL_LORD || m.getSealRoad() < 0) return null;
        network.awardSeal(m.getSealRoad());
        int gash = SealLord.gashIndexForRoad(m.getSealRoad());
        return com.bpm.minotaur.ui.UiGlyphs.sanitize("You take the seal of " + history.world().gashName(gash)
                + " from the body. (" + network.getSealCount() + "/"
                + com.bpm.minotaur.gamedata.blight.CastleGate.SEALS_REQUIRED + " seals)");
    }

    /** What the sealed gate at a seal site says, now that its lord has a name. */
    public static String knockLine(HistoryWorld world, int road, DoctrineCatalog catalog, boolean sealHeld) {
        int gash = SealLord.gashIndexForRoad(road);
        if (gash < 0) return null;
        if (sealHeld) {
            return com.bpm.minotaur.ui.UiGlyphs.sanitize("The way down is quiet. You already hold the seal of "
                    + world.gashName(gash) + ".");
        }
        SealLord.Spec spec = SealLord.compose(world, gash, catalog);
        return com.bpm.minotaur.ui.UiGlyphs.sanitize("A sealed way down. Far beneath it, " + spec.name + " holds "
                + world.gashName(gash) + ", and one of the ancient seals with it.");
    }
}
