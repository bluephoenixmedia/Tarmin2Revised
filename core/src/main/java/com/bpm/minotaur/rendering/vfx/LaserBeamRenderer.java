package com.bpm.minotaur.rendering.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.laser.LaserBurst;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws the merchant's Void chain laser in the 3D world.
 *
 * <p>Every beam of a burst is already resolved by the time it reaches here; this
 * class only makes the resolution <em>legible</em>. Beams appear 60ms apart so
 * the player can watch the climb walk the last shots off target -- which is how
 * they learn why a stray found them -- and each beam lives 0.16s, so a whole
 * burst plays out in about 0.4s without ever taking input away.
 *
 * <p>Each beam is a camera-facing ribbon drawn additively with
 * {@code shaders/beam.frag}, depth-tested against world geometry (so walls
 * occlude it) without writing depth (so beams never hide each other).
 *
 * <p><b>Coordinates.</b> The ribbon lives in the planar 3D camera's space,
 * which maps grid (x, y) to world (x, height, -y). Impact flashes and floor
 * decals elsewhere in the game use (x, height, +y) instead, so the start
 * callback hands back the grid-space {@link LaserBurst.Beam} and leaves that
 * conversion to the caller rather than passing a camera-space point that
 * would land on the mirrored tile.
 */
public class LaserBeamRenderer implements Disposable {

    /** Notified the moment each beam appears, so impacts and scorch marks land in sync with it. */
    public interface BeamStartListener {
        /** Positions are in grid space: {@code beam.getOrigin()} and {@code beam.getEnd()}. */
        void onBeamStart(LaserBurst.Beam beam);
    }

    /** Delay between successive beams of a burst. */
    public static final float STAGGER_SECONDS = 0.06f;
    /** How long each beam stays on screen. */
    public static final float BEAM_LIFETIME_SECONDS = 0.16f;
    /** Chest height: the muzzle sits a little below the camera's 0.5 eye line. */
    public static final float BEAM_HEIGHT = 0.42f;
    /** Half the ribbon's width in world units; the shader's halo fades out at this edge. */
    public static final float BEAM_HALF_WIDTH = 0.07f;

    /** Void violet: reads as alien against torchlit stone and matches the VOID impact sheet. */
    private static final float TINT_R = 0.78f;
    private static final float TINT_G = 0.36f;
    private static final float TINT_B = 1.00f;

    private static final int MAX_BEAMS = 64;
    private static final int FLOATS_PER_VERTEX = 9; // position(3) + colour(4) + uv(2)

    private final ShaderProgram shader;
    private final Mesh mesh;
    private final float[] vertices = new float[MAX_BEAMS * 4 * FLOATS_PER_VERTEX];
    private final float[] seeds = new float[MAX_BEAMS];
    private final List<ActiveBeam> beams = new ArrayList<>();

    private final Vector3 tmpDir = new Vector3();
    private final Vector3 tmpMid = new Vector3();
    private final Vector3 tmpToCamera = new Vector3();
    private final Vector3 tmpSide = new Vector3();

    private float time;
    private BeamStartListener listener;

    private static final class ActiveBeam {
        final LaserBurst.Beam source;
        final Vector3 start;
        final Vector3 end;
        final float delay;
        final float seed;
        final float length;
        float age;
        boolean started;

        ActiveBeam(LaserBurst.Beam source, Vector3 start, Vector3 end, float delay, float seed) {
            this.source = source;
            this.start = start;
            this.end = end;
            this.delay = delay;
            this.seed = seed;
            this.length = start.dst(end);
        }
    }

    public LaserBeamRenderer() {
        ShaderProgram.pedantic = false;
        shader = new ShaderProgram(Gdx.files.internal("shaders/beam.vert"), Gdx.files.internal("shaders/beam.frag"));
        if (!shader.isCompiled()) {
            Gdx.app.error("LaserBeamRenderer", "Failed to compile beam shader: " + shader.getLog());
        }

        mesh = new Mesh(false, MAX_BEAMS * 4, MAX_BEAMS * 6,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"));

        short[] indices = new short[MAX_BEAMS * 6];
        for (int i = 0, v = 0; i < indices.length; i += 6, v += 4) {
            indices[i] = (short) v;
            indices[i + 1] = (short) (v + 1);
            indices[i + 2] = (short) (v + 2);
            indices[i + 3] = (short) (v + 2);
            indices[i + 4] = (short) (v + 3);
            indices[i + 5] = (short) v;
        }
        mesh.setIndices(indices);
    }

    public void setBeamStartListener(BeamStartListener listener) {
        this.listener = listener;
    }

    /** Queues every beam of a resolved burst, staggered by its position in the burst. */
    public void spawnBurst(LaserBurst.BurstResult result) {
        for (LaserBurst.Beam beam : result.getBeams()) {
            if (beams.size() >= MAX_BEAMS) {
                break;
            }
            Vector3 start = toCameraSpace(beam.getOrigin().x, beam.getOrigin().y);
            Vector3 end = toCameraSpace(beam.getEnd().x, beam.getEnd().y);
            beams.add(new ActiveBeam(beam, start, end, beam.getIndex() * STAGGER_SECONDS, MathUtils.random()));
        }
    }

    /** Grid (x, y) to the planar 3D camera's world (x, height, -y). */
    private static Vector3 toCameraSpace(float gridX, float gridY) {
        return new Vector3(gridX, BEAM_HEIGHT, -gridY);
    }

    public boolean hasActiveBeams() {
        return !beams.isEmpty();
    }

    public void update(float delta) {
        time += delta;
        Iterator<ActiveBeam> it = beams.iterator();
        while (it.hasNext()) {
            ActiveBeam beam = it.next();
            beam.age += delta;
            if (!beam.started && beam.age >= beam.delay) {
                beam.started = true;
                if (listener != null) {
                    listener.onBeamStart(beam.source);
                }
            }
            if (beam.age >= beam.delay + BEAM_LIFETIME_SECONDS) {
                it.remove();
            }
        }
    }

    /**
     * Draws every visible beam. Call from the world's transparent pass, after the
     * opaque geometry has written depth.
     */
    public void render(Camera camera) {
        if (beams.isEmpty() || !shader.isCompiled()) {
            return;
        }

        int count = 0;
        int offset = 0;
        for (ActiveBeam beam : beams) {
            if (!beam.started || count >= MAX_BEAMS) {
                continue;
            }
            int next = writeQuad(offset, beam, camera);
            if (next != offset) {
                seeds[count++] = beam.seed;
                offset = next;
            }
        }
        if (count == 0) {
            return;
        }
        mesh.setVertices(vertices, 0, offset);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE); // additive: overlapping beams stack brighter
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);
        shader.setUniformf("u_time", time);
        for (int i = 0; i < count; i++) {
            shader.setUniformf("u_seed", seeds[i]);
            mesh.render(shader, GL20.GL_TRIANGLES, i * 6, 6);
        }

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Writes one camera-facing ribbon; returns the new offset, unchanged if the beam was skipped. */
    private int writeQuad(int offset, ActiveBeam beam, Camera camera) {
        float life = MathUtils.clamp(1f - (beam.age - beam.delay) / BEAM_LIFETIME_SECONDS, 0f, 1f);

        tmpDir.set(beam.end).sub(beam.start);
        if (tmpDir.len2() < 1e-6f) {
            return offset;
        }
        tmpMid.set(beam.start).add(beam.end).scl(0.5f);
        tmpToCamera.set(camera.position).sub(tmpMid);
        tmpSide.set(tmpDir).crs(tmpToCamera);
        if (tmpSide.len2() < 1e-8f) {
            // Looking straight down the beam (it is coming at you): fall back to a vertical ribbon.
            tmpSide.set(tmpDir).crs(Vector3.Y);
            if (tmpSide.len2() < 1e-8f) {
                return offset;
            }
        }
        tmpSide.nor().scl(BEAM_HALF_WIDTH);

        Vector3 a = beam.start;
        Vector3 b = beam.end;
        offset = vertex(offset, a.x - tmpSide.x, a.y - tmpSide.y, a.z - tmpSide.z, life, 0f, -1f);
        offset = vertex(offset, a.x + tmpSide.x, a.y + tmpSide.y, a.z + tmpSide.z, life, 0f, 1f);
        offset = vertex(offset, b.x + tmpSide.x, b.y + tmpSide.y, b.z + tmpSide.z, life, beam.length, 1f);
        offset = vertex(offset, b.x - tmpSide.x, b.y - tmpSide.y, b.z - tmpSide.z, life, beam.length, -1f);
        return offset;
    }

    private int vertex(int offset, float x, float y, float z, float life, float u, float v) {
        vertices[offset++] = x;
        vertices[offset++] = y;
        vertices[offset++] = z;
        vertices[offset++] = TINT_R;
        vertices[offset++] = TINT_G;
        vertices[offset++] = TINT_B;
        vertices[offset++] = life;
        vertices[offset++] = u;
        vertices[offset++] = v;
        return offset;
    }

    @Override
    public void dispose() {
        shader.dispose();
        mesh.dispose();
    }
}
