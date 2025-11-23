package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Pool;

public class WallDecal implements Pool.Poolable {
    public int gridX, gridY;
    public int side; // 0 = East/West Face, 1 = North/South Face (Matches RaycastResult)
    public float wallX; // 0.0 to 1.0 (Horizontal position on the wall face)
    public float height; // 0.0 to 1.0 (Vertical position in world space)
    public float radius; // Size of the splat
    public Color color = new Color();

    public void init(int x, int y, int s, float wx, float h, float r, Color c) {
        this.gridX = x;
        this.gridY = y;
        this.side = s;
        this.wallX = wx;
        this.height = h;
        this.radius = r;
        this.color.set(c);
    }

    @Override
    public void reset() {
        // No heavy resources to dispose
    }
}
