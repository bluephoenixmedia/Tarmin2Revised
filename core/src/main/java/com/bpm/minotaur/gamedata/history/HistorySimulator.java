package com.bpm.minotaur.gamedata.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Runs the politics of the Maze (plan D8, D22-D25).
 *
 * <p>{@link #prehistory} founds the houses and simulates {@link #PREHISTORY_YEARS} years of
 * seasons. {@link #tickSeason} advances one live season, which the game does on every shelter
 * sleep. Every season draws from its own RNG derived from the world seed and the season number,
 * so the same seed and the same player deeds always produce the same history.
 */
public final class HistorySimulator {

    public static final int PREHISTORY_YEARS = 300;

    private static final long SALT = 0x6A09E667F3BCC909L;
    private static final int ADULT_AGE = 16;
    private static final int MIN_LESSER = 4;
    private static final int MAX_LESSER = 8;
    private static final int MAX_WARS_PER_HOUSE = 2;

    private final HistoryWorld world;
    private final DoctrineCatalog catalog;
    private Random rng;

    private HistorySimulator(HistoryWorld world, DoctrineCatalog catalog) {
        this.world = world;
        this.catalog = catalog;
    }

    /** Founds the houses and simulates the past. Same seed, same history. */
    public static HistoryWorld prehistory(long seed, DoctrineCatalog catalog) {
        HistoryWorld world = new HistoryWorld(seed);
        HistorySimulator sim = new HistorySimulator(world, catalog);
        sim.rng = new Random(seed ^ SALT);
        sim.found();
        int seasons = PREHISTORY_YEARS * HistoryWorld.SEASONS_PER_YEAR;
        while (world.season < seasons) {
            sim.step();
        }
        return world;
    }

    /** Advances one live season (one shelter sleep). */
    public static void tickSeason(HistoryWorld world, DoctrineCatalog catalog) {
        new HistorySimulator(world, catalog).step();
        world.liveSeasons++;
    }

    /** Applies a player deed immediately; its consequences resolve at the next season. */
    public static void applyDeed(HistoryWorld world, PlayerDeed deed) {
        new HistorySimulator(world, null).deed(deed);
    }

    // ------------------------------------------------------------------ founding

    private void found() {
        for (int g = 0; g < HistoryWorld.GASH_COUNT; g++) {
            world.gashNames[g] = NameForge.gash(world, rng);
        }
        List<Doctrine> doctrines = catalog.selectable();
        for (int g = 0; g < HistoryWorld.GASH_COUNT; g++) {
            House h = newHouse(doctrines.get(rng.nextInt(doctrines.size())), 80f);
            h.gashIndex = g;
        }
        int lesser = MIN_LESSER + rng.nextInt(MAX_LESSER - MIN_LESSER + 1);
        for (int i = 0; i < lesser; i++) {
            newHouse(doctrines.get(rng.nextInt(doctrines.size())), 40f);
        }
        world.tarminRisesSeason = (180 + rng.nextInt(81)) * HistoryWorld.SEASONS_PER_YEAR;
    }

    private House newHouse(Doctrine d, float strength) {
        int id = world.houses.size();
        String name = NameForge.house(d, world, rng);
        House h = new House(id, name, d.id, NameForge.sigil(d, rng), NameForge.pick(d.mottos, rng),
                world.season, NameForge.place(rng));
        h.strength = strength;
        world.houses.add(h);

        Figure lord = newFigure(h, d, world.season - (20 + rng.nextInt(25)) * HistoryWorld.SEASONS_PER_YEAR, -1);
        h.lordId = lord.id;
        int children = 1 + rng.nextInt(3);
        for (int i = 0; i < children; i++) {
            newFigure(h, d, world.season - rng.nextInt(18) * HistoryWorld.SEASONS_PER_YEAR, lord.id);
        }
        if (rng.nextBoolean()) {
            newFigure(h, d, lord.birthSeason + rng.nextInt(8) * HistoryWorld.SEASONS_PER_YEAR, -1);
        }
        newSwornSword(h, d);

        HistoryEvent e = record(EventType.HOUSE_FOUNDED);
        e.houseA = h.id;
        e.figureA = lord.id;
        e.gashIndex = h.gashIndex;
        return h;
    }

    private Figure newFigure(House h, Doctrine d, int birthSeason, int parentId) {
        Figure f = new Figure(world.figures.size(), NameForge.given(d, rng), h.id, birthSeason, Figure.Role.KIN, parentId);
        rollTraits(f, d);
        world.figures.add(f);
        return f;
    }

    private Figure newSwornSword(House h, Doctrine d) {
        int born = world.season - (18 + rng.nextInt(20)) * HistoryWorld.SEASONS_PER_YEAR;
        Figure f = new Figure(world.figures.size(), NameForge.given(d, rng), h.id, born, Figure.Role.SWORN_SWORD, -1);
        rollTraits(f, d);
        world.figures.add(f);
        return f;
    }

    private void rollTraits(Figure f, Doctrine d) {
        int count = 1 + (rng.nextInt(3) == 0 ? 1 : 0);
        int total = 0;
        for (int w : d.traitWeights.values()) total += w;
        Trait[] all = Trait.values();
        while (f.traits.size() < count) {
            Trait t;
            if (rng.nextInt(4) == 0) {
                t = all[rng.nextInt(all.length)];
            } else {
                int roll = rng.nextInt(total);
                t = null;
                for (Map.Entry<String, Integer> e : d.traitWeights.entrySet()) {
                    roll -= e.getValue();
                    if (roll < 0) {
                        t = Trait.valueOf(e.getKey());
                        break;
                    }
                }
            }
            if (!f.traits.contains(t)) f.traits.add(t);
        }
    }

    private void riseTarminZul() {
        Doctrine d = catalog.get(DoctrineCatalog.TARMIN_ZUL_DOCTRINE);
        int id = world.houses.size();
        House h = new House(id, "The Antlered Host", d.id, NameForge.sigil(d, rng), NameForge.pick(d.mottos, rng),
                world.season, "Castle Tarmin");
        h.holdsCastle = true;
        h.strength = 90f;
        world.houses.add(h);
        world.usedNames.add(h.name);
        world.tarminHouseId = id;

        Figure lord = new Figure(world.figures.size(), "Tarmin-Zul", id, world.season - 900 * HistoryWorld.SEASONS_PER_YEAR,
                Figure.Role.KIN, -1);
        lord.ageless = true;
        lord.traits.add(Trait.AMBITIOUS);
        lord.traits.add(Trait.CRUEL);
        world.figures.add(lord);
        h.lordId = lord.id;
        newSwornSword(h, d);
        newSwornSword(h, d);

        HistoryEvent e = record(EventType.TARMIN_ZUL_RISES);
        e.houseA = id;
        e.figureA = lord.id;
        e.place = "the Obsidian Rift";
    }

    // ------------------------------------------------------------------ the season

    private void step() {
        if (rng == null) {
            rng = new Random(world.seed ^ SALT ^ ((long) world.season * 0x9E3779B97F4A7C15L));
        }
        if (world.tarminHouseId < 0 && world.season >= world.tarminRisesSeason) {
            riseTarminZul();
        }
        lifeAndDeath();
        successions();
        hostages();
        for (House h : new ArrayList<>(world.houses)) {
            if (!h.isExtinct()) decide(h);
        }
        wars();
        // Lords who fell this season are succeeded before it closes, so a world at rest never
        // has a headless house; deaths the player causes between seasons wait for the next one.
        successions();
        recover();
        refillLesserHouses();
        world.season++;
    }

    private void lifeAndDeath() {
        for (Figure f : new ArrayList<>(world.figures)) {
            if (!f.isAlive() || f.ageless) continue;
            int age = f.ageAt(world.season);
            float p = age > 40 ? (age - 40) * 0.0016f : 0.0008f;
            if (rng.nextFloat() < p) {
                kill(f, Figure.Fate.NATURAL);
                House h = world.house(f.houseId);
                if (h != null && h.lordId == f.id) {
                    HistoryEvent e = record(EventType.NATURAL_DEATH);
                    e.houseA = h.id;
                    e.figureA = f.id;
                }
            }
        }
        for (House h : world.houses) {
            if (h.isExtinct()) continue;
            Figure lord = world.lordOf(h);
            if (lord == null || !lord.isAlive() || lord.ageless) continue;
            int age = lord.ageAt(world.season);
            if (age >= 18 && age <= 50 && childrenOf(lord) < 4 && rng.nextFloat() < 0.035f) {
                newFigure(h, doctrine(h), world.season, lord.id);
            }
            if (world.swornSwords(h).isEmpty() && rng.nextFloat() < 0.05f) {
                newSwornSword(h, doctrine(h));
            }
        }
    }

    private void successions() {
        for (House h : new ArrayList<>(world.houses)) {
            if (h.isExtinct()) continue;
            Figure lord = world.lordOf(h);
            if (lord != null && lord.isAlive()) continue;
            succeed(h, lord);
        }
    }

    private void succeed(House h, Figure dead) {
        List<Figure> members = world.livingMembers(h);
        members.removeIf(f -> f.hostageOf >= 0);
        if (members.isEmpty()) {
            extinguish(h);
            return;
        }
        Figure heir = null;
        if (dead != null) {
            for (Figure f : members) {
                if (f.parentId == dead.id && (heir == null || f.birthSeason < heir.birthSeason)) heir = f;
            }
        }
        if (heir == null) {
            for (Figure f : members) {
                if (heir == null || f.birthSeason < heir.birthSeason) heir = f;
            }
        }
        Figure rival = null;
        for (Figure f : members) {
            if (f == heir || f.ageAt(world.season) < ADULT_AGE) continue;
            if ((f.has(Trait.AMBITIOUS) || f.has(Trait.CUNNING) || f.has(Trait.WRATHFUL))
                    && (rival == null || f.birthSeason < rival.birthSeason)) {
                rival = f;
            }
        }
        boolean heirIsChild = heir.ageAt(world.season) < ADULT_AGE;
        HistoryEvent e;
        if (rival != null && heirIsChild && rng.nextFloat() < 0.6f) {
            e = record(EventType.USURPATION);
            e.figureA = rival.id;
            e.figureB = heir.id;
            h.lordId = rival.id;
            if (rng.nextBoolean()) kill(heir, Figure.Fate.DISPUTE);
        } else if (rival != null && rng.nextFloat() < (heirIsChild ? 0.5f : 0.35f)) {
            e = record(EventType.DISPUTED_SUCCESSION);
            boolean rivalWins = rng.nextFloat() < 0.4f;
            Figure winner = rivalWins ? rival : heir;
            Figure loser = rivalWins ? heir : rival;
            e.figureA = winner.id;
            e.figureB = loser.id;
            h.lordId = winner.id;
            if (rng.nextBoolean()) kill(loser, Figure.Fate.DISPUTE);
            h.strength *= 0.8f;
        } else {
            e = record(EventType.SUCCESSION);
            e.figureA = heir.id;
            e.figureB = dead != null ? dead.id : -1;
            h.lordId = heir.id;
        }
        e.houseA = h.id;
    }

    private void extinguish(House h) {
        h.extinctSeason = world.season;
        HistoryEvent e = record(EventType.HOUSE_EXTINGUISHED);
        e.houseA = h.id;
        for (War w : world.wars) {
            if (w.isActive() && w.involves(h.id)) w.endSeason = world.season;
        }
        for (House other : world.houses) {
            if (other.liegeId == h.id) other.liegeId = -1;
        }
        world.alliances.removeIf(k -> (int) (k >>> 32) == h.id || (int) (long) k == h.id);
        if (h.isGreat()) {
            int gash = h.gashIndex;
            h.gashIndex = -1;
            House heir = strongestClaimant(h);
            heir.gashIndex = gash;
            heir.liegeId = -1;
            HistoryEvent s = record(EventType.SEAT_SEIZED);
            s.houseA = heir.id;
            s.houseB = h.id;
            s.gashIndex = gash;
            s.causeEventId = e.id;
        }
    }

    /** Who takes an empty gash: a vassal of the dead house if any, else the strongest lesser house. */
    private House strongestClaimant(House fallen) {
        House best = null;
        for (House h : world.houses) {
            if (h.isExtinct() || h.isGreat() || h.holdsCastle || h == fallen) continue;
            boolean vassal = h.liegeId == fallen.id;
            boolean bestVassal = best != null && best.liegeId == fallen.id;
            if (best == null || (vassal && !bestVassal) || (vassal == bestVassal && h.strength > best.strength)) best = h;
        }
        return best;
    }

    private void hostages() {
        for (Figure f : world.figures) {
            if (!f.isAlive() || f.hostageOf < 0) continue;
            House captor = world.house(f.hostageOf);
            if (captor == null || captor.isExtinct()) {
                f.hostageOf = -1;
                continue;
            }
            if (world.activeWarBetween(captor.id, f.houseId) == null) {
                f.hostageOf = -1;
                continue;
            }
            Figure lord = world.lordOf(captor);
            boolean harsh = lord != null && (lord.has(Trait.CRUEL) || lord.has(Trait.WRATHFUL));
            if (harsh && rng.nextFloat() < 0.06f) {
                kill(f, Figure.Fate.EXECUTED);
                HistoryEvent e = record(EventType.HOSTAGE_EXECUTED);
                e.houseA = captor.id;
                e.houseB = f.houseId;
                e.figureA = lord.id;
                e.figureB = f.id;
                grudge(f.houseId, captor.id, CasusBelli.SLAIN_KIN, e.id, 5f);
            }
        }
    }

    // ------------------------------------------------------------------ a lord's choices

    private void decide(House h) {
        Figure lord = world.lordOf(h);
        if (lord == null || !lord.isAlive() || lord.ageAt(world.season) < ADULT_AGE) return;

        if (h.liegeId >= 0) {
            House liege = world.house(h.liegeId);
            boolean restless = lord.has(Trait.AMBITIOUS) || lord.has(Trait.WRATHFUL);
            if (restless && h.strength > 0.6f * liege.strength && rng.nextFloat() < 0.03f) {
                h.liegeId = -1;
                HistoryEvent e = record(EventType.VASSAL_REBELLION);
                e.houseA = h.id;
                e.houseB = liege.id;
                e.figureA = lord.id;
                e.casusBelli = CasusBelli.INDEPENDENCE;
                declareWar(h, liege, CasusBelli.INDEPENDENCE, e.id);
            }
            return;
        }

        if (tryBetrayal(h, lord)) return;
        if (tryAssassination(h, lord)) return;
        if (tryWar(h, lord)) return;
        if (tryOath(h, lord)) return;
        tryMarriage(h, lord);
    }

    private boolean tryBetrayal(House h, Figure lord) {
        if (!(lord.has(Trait.AMBITIOUS) || lord.has(Trait.CUNNING))) return false;
        if (world.activeWarCount(h.id) >= MAX_WARS_PER_HOUSE) return false;
        for (House ally : world.livingHouses()) {
            if (ally == h || world.stance(h.id, ally.id) != HistoryWorld.Stance.ALLIED) continue;
            boolean weak = world.activeWarCount(ally.id) > 0 || ally.strength < h.strength * 0.7f;
            if (weak && rng.nextFloat() < 0.04f) {
                world.alliances.remove(HistoryWorld.pairKey(h.id, ally.id));
                HistoryEvent e = record(EventType.BETRAYAL);
                e.houseA = h.id;
                e.houseB = ally.id;
                e.figureA = lord.id;
                e.casusBelli = CasusBelli.TREACHERY;
                grudge(ally.id, h.id, CasusBelli.BROKEN_PACT, e.id, 5f);
                declareWar(h, ally, CasusBelli.TREACHERY, e.id);
                return true;
            }
        }
        return false;
    }

    private boolean tryAssassination(House h, Figure lord) {
        if (!(lord.has(Trait.CUNNING) || lord.has(Trait.CRUEL))) return false;
        for (House target : world.livingHouses()) {
            if (target == h || world.grudgeWeight(h.id, target.id) < 2f) continue;
            Figure victim = world.lordOf(target);
            if (victim == null || !victim.isAlive() || victim.ageless) continue;
            if (rng.nextFloat() < 0.015f) {
                kill(victim, Figure.Fate.ASSASSINATED);
                HistoryEvent e = record(EventType.ASSASSINATION);
                e.houseA = h.id;
                e.houseB = target.id;
                e.figureA = lord.id;
                e.figureB = victim.id;
                grudge(target.id, h.id, CasusBelli.SLAIN_KIN, e.id, 4f);
                return true;
            }
        }
        return false;
    }

    private boolean tryWar(House h, Figure lord) {
        if (world.activeWarCount(h.id) >= MAX_WARS_PER_HOUSE) return false;
        House best = null;
        float bestUtility = 0f;
        for (House target : world.livingHouses()) {
            if (target == h || target.liegeId == h.id) continue;
            if (world.stance(h.id, target.id) != HistoryWorld.Stance.NEUTRAL) continue;
            float u = warUtility(h, lord, target);
            if (u > bestUtility) {
                bestUtility = u;
                best = target;
            }
        }
        if (best == null || rng.nextFloat() >= Math.min(0.25f, bestUtility * 0.08f)) return false;
        CasusBelli cb = casusBelli(h, best);
        Grudge g = world.strongestGrudge(h.id, best.id);
        declareWar(h, best, cb, g != null ? g.causeEventId : -1);
        return true;
    }

    /** Grudge, disposition and opportunity, less caution (plan D25). Positive means war is wanted. */
    private float warUtility(House h, Figure lord, House target) {
        float u = Math.min(6f, world.grudgeWeight(h.id, target.id)) - 1.6f;
        if (lord.has(Trait.AMBITIOUS)) u += 0.8f;
        if (lord.has(Trait.WRATHFUL) && world.grudgeWeight(h.id, target.id) > 0f) u += 0.8f;
        if (lord.has(Trait.ZEALOT)) u += 0.3f;
        if (lord.has(Trait.CRAVEN)) u -= 1.0f;
        if (lord.has(Trait.PATIENT)) u -= 0.4f;
        if (world.activeWarCount(target.id) > 0) u += 0.6f;
        if (h.strength > target.strength * 1.3f) u += 0.6f;
        if (h.strength < target.strength * 0.7f) u -= 0.8f;
        if (target.isGreat() && !h.isGreat() && !h.holdsCastle) u += 0.7f;
        if (target.holdsCastle) u += 0.3f;
        return u;
    }

    private CasusBelli casusBelli(House h, House target) {
        Grudge g = world.strongestGrudge(h.id, target.id);
        if (g != null && g.weight >= 1f) return g.cause;
        for (Figure f : world.figures) {
            if (f.isAlive() && f.houseId == h.id && f.hostageOf == target.id) return CasusBelli.HOSTAGE_HELD;
        }
        if (target.holdsCastle) return CasusBelli.CLAIM_TO_CASTLE;
        if (target.isGreat() && !h.isGreat()) return CasusBelli.CLAIM_TO_GASH;
        return CasusBelli.AMBITION;
    }

    private void declareWar(House attacker, House defender, CasusBelli cb, int causeEventId) {
        HistoryEvent e = record(EventType.WAR_DECLARED);
        e.houseA = attacker.id;
        e.houseB = defender.id;
        e.figureA = attacker.lordId;
        e.casusBelli = cb;
        e.causeEventId = causeEventId;
        world.wars.add(new War(world.wars.size(), attacker.id, defender.id, cb, e.id, world.season));
    }

    private boolean tryOath(House h, Figure lord) {
        if (h.isGreat() || h.holdsCastle || world.activeWarCount(h.id) > 0) return false;
        boolean meek = lord.has(Trait.CRAVEN) || lord.has(Trait.PATIENT) || h.strength < 25f;
        if (!meek || rng.nextFloat() >= 0.01f) return false;
        House liege = null;
        for (House g : world.livingHouses()) {
            if (!(g.isGreat() || g.holdsCastle) || world.activeWarBetween(h.id, g.id) != null) continue;
            if (world.grudgeWeight(h.id, g.id) >= 2f) continue;
            if (liege == null || g.strength > liege.strength) liege = g;
        }
        if (liege == null) return false;
        swear(h, liege, false, -1);
        return true;
    }

    private void swear(House vassal, House liege, boolean forced, int causeEventId) {
        vassal.liegeId = liege.id;
        world.alliances.remove(HistoryWorld.pairKey(vassal.id, liege.id));
        HistoryEvent e = record(EventType.VASSAL_OATH);
        e.houseA = vassal.id;
        e.houseB = liege.id;
        e.figureA = vassal.lordId;
        e.figureB = liege.lordId;
        e.detail = forced ? 1 : 0;
        e.causeEventId = causeEventId;
    }

    private void tryMarriage(House h, Figure lord) {
        if (lord.has(Trait.CRUEL) || rng.nextFloat() >= 0.012f) return;
        List<House> candidates = new ArrayList<>();
        for (House other : world.livingHouses()) {
            if (other == h || other.holdsCastle || h.holdsCastle) continue;
            if (world.stance(h.id, other.id) != HistoryWorld.Stance.NEUTRAL) continue;
            if (world.grudgeWeight(h.id, other.id) >= 1f || world.grudgeWeight(other.id, h.id) >= 1f) continue;
            candidates.add(other);
        }
        if (candidates.isEmpty()) return;
        House other = candidates.get(rng.nextInt(candidates.size()));
        Figure a = unwed(h);
        Figure b = unwed(other);
        if (a == null || b == null) return;
        a.spouseId = b.id;
        b.spouseId = a.id;
        world.alliances.add(HistoryWorld.pairKey(h.id, other.id));
        HistoryEvent e = record(EventType.MARRIAGE_PACT);
        e.houseA = h.id;
        e.houseB = other.id;
        e.figureA = a.id;
        e.figureB = b.id;
    }

    private Figure unwed(House h) {
        for (Figure f : world.livingMembers(h)) {
            if (f.spouseId < 0 && f.id != h.lordId && f.hostageOf < 0 && f.ageAt(world.season) >= ADULT_AGE) return f;
        }
        return null;
    }

    // ------------------------------------------------------------------ war

    private void wars() {
        for (War w : new ArrayList<>(world.wars)) {
            if (!w.isActive()) continue;
            House a = world.house(w.attackerId);
            House d = world.house(w.defenderId);
            if (rng.nextFloat() < 0.2f) battle(w, a, d);
            if (!w.isActive()) continue;
            int age = world.season - w.startSeason;
            boolean exhausted = a.strength < 30f && d.strength < 30f;
            if ((age > 16 && rng.nextFloat() < 0.05f) || (exhausted && rng.nextFloat() < 0.15f)) {
                w.endSeason = world.season;
                HistoryEvent e = record(EventType.PEACE);
                e.houseA = a.id;
                e.houseB = d.id;
                e.causeEventId = w.declaredEventId;
            }
        }
    }

    private void battle(War w, House a, House d) {
        float pa = power(a) * (0.6f + rng.nextFloat() * 0.8f);
        float pd = power(d) * 1.1f * (0.6f + rng.nextFloat() * 0.8f);
        House winner = pa >= pd ? a : d;
        House loser = winner == a ? d : a;
        HistoryEvent e = record(EventType.BATTLE);
        e.houseA = winner.id;
        e.houseB = loser.id;
        e.place = NameForge.place(rng);
        e.causeEventId = w.declaredEventId;
        loser.strength -= 6f + rng.nextFloat() * 8f;
        winner.strength -= 3f + rng.nextFloat() * 5f;
        winner.prestige += 2;

        Figure loserLord = world.lordOf(loser);
        if (loserLord != null && loserLord.isAlive() && !loserLord.ageless && rng.nextFloat() < 0.05f) {
            kill(loserLord, Figure.Fate.BATTLE);
            e.figureB = loserLord.id;
            grudge(loser.id, winner.id, CasusBelli.SLAIN_KIN, e.id, 4f);
        }
        List<Figure> swords = world.swornSwords(loser);
        if (!swords.isEmpty() && rng.nextFloat() < 0.12f) {
            kill(swords.get(rng.nextInt(swords.size())), Figure.Fate.BATTLE);
        }

        if (rng.nextFloat() < 0.15f) {
            Figure hostage = null;
            for (Figure f : world.livingMembers(loser)) {
                if (f.id != loser.lordId && f.hostageOf < 0) {
                    hostage = f;
                    break;
                }
            }
            if (hostage != null) {
                hostage.hostageOf = winner.id;
                HistoryEvent t = record(EventType.HOSTAGE_TAKEN);
                t.houseA = winner.id;
                t.houseB = loser.id;
                t.figureB = hostage.id;
                t.causeEventId = e.id;
                grudge(loser.id, winner.id, CasusBelli.HOSTAGE_HELD, t.id, 2f);
            }
        }

        if (loser.strength < 15f && rng.nextFloat() < 0.35f) seize(w, winner, loser, e);
        loser.strength = Math.max(5f, loser.strength);
        winner.strength = Math.max(5f, winner.strength);
    }

    private float power(House h) {
        float p = h.strength;
        for (House v : world.houses) {
            if (!v.isExtinct() && v.liegeId == h.id) p += 0.3f * v.strength;
        }
        return p;
    }

    /** A broken house loses its gash, or bends the knee. Either way the war is over. */
    private void seize(War w, House winner, House loser, HistoryEvent battle) {
        if (loser.holdsCastle) return;
        w.endSeason = world.season;
        if (loser.isGreat() && !winner.isGreat() && !winner.holdsCastle) {
            int gash = loser.gashIndex;
            loser.gashIndex = -1;
            winner.gashIndex = gash;
            winner.liegeId = -1;
            loser.strength = Math.min(loser.strength, 15f);
            winner.prestige += 5;
            HistoryEvent e = record(EventType.SEAT_SEIZED);
            e.houseA = winner.id;
            e.houseB = loser.id;
            e.gashIndex = gash;
            e.causeEventId = battle.id;
            e.place = world.gashNames[gash];
            grudge(loser.id, winner.id, CasusBelli.USURPED_SEAT, e.id, 4f);
        } else if (!loser.isGreat()) {
            swear(loser, winner, true, battle.id);
            grudge(loser.id, winner.id, CasusBelli.USURPED_SEAT, battle.id, 3f);
        } else {
            HistoryEvent e = record(EventType.PEACE);
            e.houseA = winner.id;
            e.houseB = loser.id;
            e.causeEventId = battle.id;
        }
    }

    // ------------------------------------------------------------------ upkeep

    private void recover() {
        for (House h : world.houses) {
            if (h.isExtinct()) continue;
            h.strength = Math.min(h.strengthCap(), h.strength + 1.2f);
        }
        for (Grudge g : world.grudges) g.weight *= 0.992f;
        world.grudges.removeIf(g -> g.weight < 0.25f);
    }

    private void refillLesserHouses() {
        int lesser = 0;
        for (House h : world.houses) {
            if (!h.isExtinct() && !h.isGreat() && !h.holdsCastle) lesser++;
        }
        if (lesser < MIN_LESSER) {
            List<Doctrine> doctrines = catalog.selectable();
            newHouse(doctrines.get(rng.nextInt(doctrines.size())), 30f);
        }
    }

    // ------------------------------------------------------------------ deeds

    private void deed(PlayerDeed deed) {
        if (deed.kind == PlayerDeed.Kind.SLEW_FIGURE) {
            Figure f = world.figure(deed.target);
            if (f == null || !f.isAlive()) return;
            kill(f, Figure.Fate.PLAYER);
            HistoryEvent e = record(EventType.SLAIN_BY_PLAYER);
            e.houseB = f.houseId;
            e.figureB = f.id;
        } else if (deed.kind == PlayerDeed.Kind.SEEKER_FELL) {
            world.seekersFallen++;
            HistoryEvent e = record(EventType.SEEKER_FELL);
            e.houseA = deed.target;
            e.detail = world.seekersFallen;
            House killer = world.house(deed.target);
            if (killer != null) killer.prestige += 3;
        } else if (deed.kind == PlayerDeed.Kind.DOOM_STAGE) {
            ascend(deed.target);
        }
    }

    /**
     * Tarmin's Hunger reaches a new stage, and the Antlered Lord feeds it from the houses (D42).
     * From the third stage the weakest free lesser house is made to kneel to him. The Doom
     * Clock's own numbers are never touched: the history only listens to it.
     */
    private void ascend(int stage) {
        House tarmin = world.tarminHouse();
        if (tarmin == null || tarmin.isExtinct()) return;
        HistoryEvent e = record(EventType.TARMIN_ASCENDANT);
        e.houseA = tarmin.id;
        e.figureA = tarmin.lordId;
        e.detail = stage;
        for (House h : world.livingHouses()) {
            if (h != tarmin && !h.holdsCastle) h.strength = Math.max(5f, h.strength - 3f * stage);
        }
        if (stage < 3) return;
        House weakest = null;
        for (House h : world.livingHouses()) {
            if (h.isGreat() || h.holdsCastle || h.liegeId >= 0) continue;
            if (weakest == null || h.strength < weakest.strength) weakest = h;
        }
        if (weakest != null) {
            for (War w : world.wars) {
                if (w.isActive() && w.involves(weakest.id) && w.involves(tarmin.id)) w.endSeason = world.season;
            }
            swear(weakest, tarmin, true, e.id);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void kill(Figure f, Figure.Fate fate) {
        f.deathSeason = world.season;
        f.fate = fate;
        f.hostageOf = -1;
    }

    private int childrenOf(Figure parent) {
        int n = 0;
        for (Figure f : world.figures) {
            if (f.parentId == parent.id && f.isAlive()) n++;
        }
        return n;
    }

    private Doctrine doctrine(House h) {
        return catalog.get(h.doctrineId);
    }

    private void grudge(int holder, int against, CasusBelli cause, int causeEventId, float weight) {
        world.grudges.add(new Grudge(holder, against, cause, causeEventId, weight));
    }

    private HistoryEvent record(EventType type) {
        HistoryEvent e = new HistoryEvent(world.events.size(), world.season, type);
        world.events.add(e);
        return e;
    }
}
