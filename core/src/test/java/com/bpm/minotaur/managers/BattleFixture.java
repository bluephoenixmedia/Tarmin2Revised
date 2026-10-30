package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.gamedata.history.war.army.ArmyBattle;
import com.bpm.minotaur.gamedata.history.war.army.ArmyLayer;
import com.bpm.minotaur.gamedata.history.war.army.Terrain;
import com.bpm.minotaur.gamedata.monster.Monster;

/** A real history, its armies, and a battle of them at chunk (4, 4), for the tests of a battle on the map. */
final class BattleFixture {

    static final int X = 4;
    static final int Y = 4;

    final HistoryManager history;
    final ArmyLayer layer;
    final ArmyBattle battle;

    BattleFixture(long seed, DoctrineCatalog catalog, int soldiersEach) {
        history = HistoryManager.create(seed, catalog);
        BiomeManager b = new BiomeManager(seed);
        layer = new ArmyLayer(history.world(), Terrain.of(b), () -> SeatMap.of(history.world(), b.getRoads(), b.getCastleSite(), seed, b::isOpenLand),
                history.roster(), history::recordArmyDeed);
        battle = layer.raiseBattleAt(X, Y);
        if (battle == null) throw new AssertionError("no war to raise a battle from in seed " + seed);
        for (int id : battle.armiesA) history.roster().army(id).soldiers = soldiersEach;
        for (int id : battle.armiesB) history.roster().army(id).soldiers = soldiersEach;
        battle.startA = soldiersEach;
        battle.startB = soldiersEach;
    }

    Army armyA() {
        return history.roster().army(battle.armiesA[0]);
    }

    Army armyB() {
        return history.roster().army(battle.armiesB[0]);
    }

    /** A plain soldier: every recruit alike, so nothing but the numbers differs. */
    static Monster soldier(String type, int x, int y) {
        Monster m = new Monster(Monster.MonsterType.valueOf(type), 20, 12);
        m.getPosition().set(x, y);
        return m;
    }
}
