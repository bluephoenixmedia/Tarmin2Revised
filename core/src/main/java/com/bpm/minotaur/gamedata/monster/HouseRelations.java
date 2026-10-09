package com.bpm.minotaur.gamedata.monster;

/**
 * Where {@link FactionMatrix} learns how Maze houses stand toward each other (ADR 0004).
 * The history supplies it; the matrix never stores or serialises it.
 */
public interface HouseRelations {

    /** Tarmin-Zul's house, which every {@link Faction#TARMIN_LEGION} monster belongs to; -1 if none. */
    int tarminHouseId();

    /** How house {@code a} stands toward house {@code b}. Never called with {@code a == b}. */
    FactionMatrix.Relation between(int a, int b);
}
