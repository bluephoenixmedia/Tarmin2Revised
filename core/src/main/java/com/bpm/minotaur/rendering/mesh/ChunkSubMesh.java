package com.bpm.minotaur.rendering.mesh;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/**
 * Encapsulates a static 3D mesh buffer paired with its diffuse texture.
 */
public class ChunkSubMesh implements Disposable {

    /**
     * Which surface this mesh is.
     *
     * <p>RETRO mode needs to know, because it replaces the texture with a flat
     * theme colour. It used to work that out by comparing the texture against the
     * two known wall textures by reference, which silently mistook any third wall
     * texture for a ceiling. With six variants per palette that would have been
     * every wall but one.
     */
    public enum Surface { WALL, FLOOR, CEILING }

    private final Texture texture;
    private final Mesh mesh;
    private final int numIndices;
    private final Surface surface;

    public ChunkSubMesh(Texture texture, Mesh mesh, int numIndices, Surface surface) {
        this.texture = texture;
        this.mesh = mesh;
        this.numIndices = numIndices;
        this.surface = surface != null ? surface : Surface.WALL;
    }

    public Surface getSurface() {
        return surface;
    }

    public boolean isWall() {
        return surface == Surface.WALL;
    }

    public Texture getTexture() {
        return texture;
    }

    public Mesh getMesh() {
        return mesh;
    }

    public int getNumIndices() {
        return numIndices;
    }

    public void render(ShaderProgram shader) {
        if (numIndices <= 0 || mesh == null) return;
        if (texture != null) {
            texture.bind(0);
            shader.setUniformi("u_diffuseTexture", 0);
        }
        mesh.render(shader, GL20.GL_TRIANGLES, 0, numIndices);
    }

    @Override
    public void dispose() {
        if (mesh != null) {
            mesh.dispose();
        }
    }
}
