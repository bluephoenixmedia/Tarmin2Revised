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

    private final Texture texture;
    private final Mesh mesh;
    private final int numIndices;

    public ChunkSubMesh(Texture texture, Mesh mesh, int numIndices) {
        this.texture = texture;
        this.mesh = mesh;
        this.numIndices = numIndices;
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
