package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Pool;
import com.bpm.minotaur.gamedata.Direction;

public class WallDecal implements Pool.Poolable {
    public int gridX, gridY;
    public int side; // 0 = East/West Face, 1 = North/South Face
    public Direction dir; // NORTH, SOUTH, EAST, WEST
    public float wallX; // 0.0 to 1.0 (Horizontal position on the wall face)
    public float height; // 0.0 to 1.0 (Vertical position in world space)
    public float radius; // Size of the splat
    public Color color = new Color();
    private final Color freshColor = new Color();
    private final Color driedColor = new Color();

    public float lifeTimer;
    public float maxLife;
    public static final float MAX_WALL_DECAL_LIFE = 45.0f;
    public static final float FADE_DURATION = 5.0f;

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

        Color startColor = (c != null) ? c : Color.RED;
        this.freshColor.set(startColor);
        this.driedColor.set(startColor.r * 0.42f, startColor.g * 0.15f, startColor.b * 0.15f, startColor.a);
        this.color.set(freshColor);

        this.maxLife = MAX_WALL_DECAL_LIFE;
        this.lifeTimer = MAX_WALL_DECAL_LIFE;
        this.textureRegion = texture;
    }

    public void init(int x, int y, int s, float wx, float h, float r, Color c,
            com.badlogic.gdx.graphics.g2d.TextureRegion texture) {
        Direction fallbackDir = (s == 0) ? Direction.WEST : Direction.NORTH;
        init(x, y, fallbackDir, wx, h, r, c, texture);
    }

    public void update(float delta) {
        lifeTimer -= delta;

        // Oxidation color shift
        float age = maxLife - lifeTimer;
        float dryT = MathUtils.clamp(age / 30.0f, 0f, 1f);
        color.r = MathUtils.lerp(freshColor.r, driedColor.r, dryT);
        color.g = MathUtils.lerp(freshColor.g, driedColor.g, dryT);
        color.b = MathUtils.lerp(freshColor.b, driedColor.b, dryT);

        // Alpha fadeout
        if (lifeTimer <= FADE_DURATION) {
            color.a = MathUtils.clamp(lifeTimer / FADE_DURATION, 0f, 1f) * freshColor.a;
        } else {
            color.a = freshColor.a;
        }
    }

    @Override
    public void reset() {
        this.dir = null;
        this.textureRegion = null;
        this.lifeTimer = 0f;
        this.color.set(Color.WHITE);
        this.freshColor.set(Color.WHITE);
        this.driedColor.set(Color.WHITE);
    }
}
