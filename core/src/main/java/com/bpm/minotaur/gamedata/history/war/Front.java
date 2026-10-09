package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;

import java.util.ArrayList;
import java.util.List;

/** Where a war is being fought right now: a block of overland chunks (glossary: Front). */
public final class Front {

    /** Chunks either side of the centre the front reaches: 1 makes a 3x3 block. */
    public static final int RADIUS = 1;

    public final int warId;
    public final int attackerId;
    public final int defenderId;
    public final GridPoint2 center;

    public Front(int warId, int attackerId, int defenderId, GridPoint2 center) {
        this.warId = warId;
        this.attackerId = attackerId;
        this.defenderId = defenderId;
        this.center = new GridPoint2(center);
    }

    public boolean covers(GridPoint2 chunk) {
        return chunk != null && Math.abs(chunk.x - center.x) <= RADIUS && Math.abs(chunk.y - center.y) <= RADIUS;
    }

    public List<GridPoint2> chunks() {
        List<GridPoint2> out = new ArrayList<>();
        for (int dy = -RADIUS; dy <= RADIUS; dy++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) out.add(new GridPoint2(center.x + dx, center.y + dy));
        }
        return out;
    }
}
