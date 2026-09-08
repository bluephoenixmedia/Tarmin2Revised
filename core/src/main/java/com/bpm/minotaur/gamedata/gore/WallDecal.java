package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Pool;
import com.bpm.minotaur.gamedata.Direction;

public class WallDecal implements Pool.Poolable {
    public int gridX, gridY;
    public int side; // 0 = East/West Face, 1 = North/South Face (Matches RaycastResult)
    public Direction dir; // NORTH, SOUTH, EAST, WEST
    public float wallX; // 0.0 to 1.0 (Horizontal position on the wall face)
    public float height; // 0.0 to 1.0 (Vertical position in world space)
    public float radius; // Size of the splat
    public Color color = new Color();
    public com.badlogic.gdx.graphics.g2d.TextureRegion textureRegion; // For Modern Mode

    public void init(int x, int y, Direction dir, float wx, float h, float r, Color c,
            com.badlogic.gdx.graphics.g2d.TextureRegion texture) {
        this.gridX = x;
        this.gridY = y;
        this.dir = dir;
        this.side = (dir == Direction.EAST || dir == Direction.WEST) ? 0 : 1;
        this.radius = Math.max(0.05f, Math.min(0.35f, r));
        this.wallX = MathUtils.clamp(wx, this.radius + 0.01f, 1.0f - this.radius - 0.01f);
        this.height = MathUtils.clamp(h, this.radius + 0.01f, 1.0f - this.radius - 0.01f);
        this.color.set(c);
        this.textureRegion = texture;
    }

    public void init(int x, int y, int s, float wx, float h, float r, Color c,
            com.badlogic.gdx.graphics.g2d.TextureRegion texture) {
        Direction fallbackDir = (s == 0) ? Direction.WEST : Direction.NORTH;
        init(x, y, fallbackDir, wx, h, r, c, texture);
    }

    @Override
    public void reset() {
        this.dir = null;
        this.textureRegion = null;
    }
}

