package com.bpm.minotaur.gamedata.history.war;

/**
 * A battle's bookkeeping, apart from the map it is fought on (plan D17, D31, T2.4, T2.6).
 *
 * <p>Each side keeps a line on the field and a reserve behind it; every turn it is told how many
 * of its soldiers still stand and whether its war-captain fell, and it answers with how many to
 * march in. Losses and a fallen captain cost morale; a side whose morale breaks, or whose men
 * and reserve are both spent, routs. A battle that will not break ends at {@link #TURN_CAP}.
 */
public final class BattleModel {

    public enum Outcome { ONGOING, A_ROUTED, B_ROUTED, TURN_CAP }

    /** Turns before a stalemate is called for whichever side stands stronger. */
    public static final int TURN_CAP = 200;
    /** Soldiers each side keeps on the field while its reserve lasts: two lines make 32. */
    public static final int LINE = 16;
    /** Reinforcements a side can march in per turn. */
    static final int MARCH_PER_TURN = 3;
    static final int MORALE_START = 100;
    static final int MORALE_PER_LOSS = 4;
    /** A war-captain's death is most of a side's nerve. */
    static final int MORALE_CAPTAIN = 70;
    /** Turns after a captain's death within which the side is expected to break. */
    public static final int CAPTAIN_BREAK_TURNS = 8;

    /** One side's state. */
    public static final class Side {
        public final int houseId;
        public int reserve;
        public int morale = MORALE_START;
        public boolean captainAlive = true;
        int lastAlive;

        Side(int houseId, int reserve) {
            this.houseId = houseId;
            this.reserve = reserve;
        }
    }

    /** What to do this turn. */
    public static final class Orders {
        public int sendA;
        public int sendB;
        public Outcome outcome = Outcome.ONGOING;
    }

    public final Side a;
    public final Side b;
    private int turn;
    private Outcome outcome = Outcome.ONGOING;

    public BattleModel(int houseA, int reserveA, int houseB, int reserveB) {
        this.a = new Side(houseA, reserveA);
        this.b = new Side(houseB, reserveB);
    }

    /**
     * A house's reserve for one battle. Even a spent house fills its line (plan D31: 30 to 40 on
     * the field); its strength (5 to 120 in the history) decides how much stands behind it.
     */
    public static int reserveFor(float strength) {
        return LINE + Math.max(2, Math.min(20, Math.round(strength / 5f)));
    }

    /** The opening muster: each side fills its line from its reserve at once. */
    public Orders muster(int linedA, int linedB) {
        Orders o = new Orders();
        o.sendA = Math.min(a.reserve, Math.max(0, LINE - linedA));
        o.sendB = Math.min(b.reserve, Math.max(0, LINE - linedB));
        a.reserve -= o.sendA;
        b.reserve -= o.sendB;
        a.lastAlive = linedA + o.sendA;
        b.lastAlive = linedB + o.sendB;
        return o;
    }

    /**
     * One turn of the battle. {@code aliveA}/{@code aliveB} are the soldiers each side has on
     * the field now; the captain flags are true on the turn that side's war-captain falls.
     */
    public Orders step(int aliveA, int aliveB, boolean captainAFell, boolean captainBFell) {
        Orders o = new Orders();
        if (outcome != Outcome.ONGOING) {
            o.outcome = outcome;
            return o;
        }
        turn++;
        bleed(a, aliveA, captainAFell);
        bleed(b, aliveB, captainBFell);

        boolean aBroken = a.morale <= 0 || (turn > 1 && aliveA == 0 && a.reserve == 0);
        boolean bBroken = b.morale <= 0 || (turn > 1 && aliveB == 0 && b.reserve == 0);
        if (aBroken || bBroken) {
            outcome = aBroken && (!bBroken || a.morale <= b.morale) ? Outcome.A_ROUTED : Outcome.B_ROUTED;
        } else if (turn >= TURN_CAP) {
            outcome = Outcome.TURN_CAP;
        }
        o.outcome = outcome;
        if (outcome != Outcome.ONGOING) return o;

        o.sendA = march(a, aliveA);
        o.sendB = march(b, aliveB);
        a.lastAlive = aliveA + o.sendA;
        b.lastAlive = aliveB + o.sendB;
        return o;
    }

    private void bleed(Side s, int alive, boolean captainFell) {
        int losses = Math.max(0, s.lastAlive - alive);
        s.morale -= losses * MORALE_PER_LOSS;
        if (losses == 0 && s.morale < MORALE_START) s.morale++;
        if (captainFell && s.captainAlive) {
            s.captainAlive = false;
            s.morale -= MORALE_CAPTAIN;
        }
    }

    private static int march(Side s, int alive) {
        int send = Math.min(Math.min(MARCH_PER_TURN, s.reserve), Math.max(0, LINE - alive));
        s.reserve -= send;
        return send;
    }

    public Outcome outcome() {
        return outcome;
    }

    public int turn() {
        return turn;
    }

    /** The house that held the field, or -1 while the battle goes on. */
    public int winner() {
        switch (outcome) {
            case A_ROUTED: return b.houseId;
            case B_ROUTED: return a.houseId;
            case TURN_CAP: return strength(a) >= strength(b) ? a.houseId : b.houseId;
            default: return -1;
        }
    }

    public int loser() {
        int w = winner();
        return w < 0 ? -1 : (w == a.houseId ? b.houseId : a.houseId);
    }

    private static int strength(Side s) {
        return (s.lastAlive + s.reserve) * Math.max(1, s.morale);
    }
}
