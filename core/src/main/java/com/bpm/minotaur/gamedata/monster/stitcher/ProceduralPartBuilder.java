package com.bpm.minotaur.gamedata.monster.stitcher;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * Builds procedural placeholder Models for creature parts using libGDX ModelBuilder.
 *
 * Each factory method returns a new Model that the caller is responsible for disposing.
 * Parts are assembled from primitives (cylinder, sphere, cone, box) positioned with
 * MeshPartBuilder.setVertexTransform() so all primitives share a single MeshPart.
 *
 * Vertex attributes: POSITION | NORMAL (matches g3dj loader expectations).
 */
public final class ProceduralPartBuilder {

    private static final long ATTRS = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
    private static final int  DIVS  = 8; // low polygon count — baked to 128px sprites

    private ProceduralPartBuilder() {}

    // -------------------------------------------------------------------------
    // Torsos
    // -------------------------------------------------------------------------

    /**
     * Universal oval torso — the canonical chimera base.
     *
     * A smooth ellipsoid centred at the local origin:
     *   width  (X) = 0.60 m   height (Y) = 1.00 m   depth (Z) = 0.45 m
     *
     * After the stitcher centres it at Y = 0.8 the world range is Y ∈ [0.30, 1.30],
     * giving a 1.0 m anchor that all limb proportional-scale ratios are calibrated to.
     * unitScale = 1.0 (built in world metres, no Blender cm conversion needed).
     */
    public static Model buildTorsoOval() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("torso", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(new Color(0.50f, 0.40f, 0.60f, 1f))));
        SphereShapeBuilder.build(mpb, 0.60f, 1.00f, 0.45f, 16, 12);
        return mb.end();
    }

    /** Broad humanoid torso: squat box with shoulder columns. */
    public static Model buildTorsoHumanoid() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("torso", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(Color.GRAY)));

        setTranslate(mpb, 0, 0.5f, 0);
        BoxShapeBuilder.build(mpb, 0.7f, 0.9f, 0.4f);

        setTranslate(mpb, -0.38f, 0.95f, 0);
        CylinderShapeBuilder.build(mpb, 0.22f, 0.22f, 0.22f, DIVS);

        setTranslate(mpb, 0.38f, 0.95f, 0);
        CylinderShapeBuilder.build(mpb, 0.22f, 0.22f, 0.22f, DIVS);

        return mb.end();
    }

    /** Insectoid torso: oval abdomen with narrower thorax above. */
    public static Model buildTorsoInsectoid() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("torso", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(new Color(0.2f, 0.55f, 0.2f, 1f))));

        setTranslate(mpb, 0, 0.3f, 0);
        CylinderShapeBuilder.build(mpb, 0.55f, 0.6f, 0.45f, DIVS);

        setTranslate(mpb, 0, 0.85f, 0);
        CylinderShapeBuilder.build(mpb, 0.38f, 0.5f, 0.35f, DIVS);

        return mb.end();
    }

    /** Hulk torso: tall box — skeletons, undead, giants. */
    public static Model buildTorsoHulk() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("torso", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(Color.LIGHT_GRAY)));

        setTranslate(mpb, 0, 0.6f, 0);
        BoxShapeBuilder.build(mpb, 0.65f, 1.1f, 0.38f);

        return mb.end();
    }

    // -------------------------------------------------------------------------
    // Heads
    // -------------------------------------------------------------------------

    public static Model buildHeadRound() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("head", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(Color.TAN)));

        setTranslate(mpb, 0, 0, 0);
        SphereShapeBuilder.build(mpb, 0.5f, 0.5f, 0.45f, DIVS, DIVS);

        Matrix4 t = new Matrix4().setToTranslation(0, 0.14f, 0.2f).rotate(Vector3.X, 20f);
        mpb.setVertexTransform(t);
        BoxShapeBuilder.build(mpb, 0.36f, 0.08f, 0.12f);

        return mb.end();
    }

    public static Model buildHeadSkulled() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("head", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(new Color(0.88f, 0.85f, 0.75f, 1f))));

        setTranslate(mpb, 0, 0.05f, 0);
        SphereShapeBuilder.build(mpb, 0.46f, 0.46f, 0.40f, DIVS, DIVS);

        setTranslate(mpb, 0, -0.22f, 0.04f);
        BoxShapeBuilder.build(mpb, 0.28f, 0.18f, 0.22f);

        return mb.end();
    }

    public static Model buildHeadHorned() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("head", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(new Color(0.5f, 0.25f, 0.1f, 1f))));

        setTranslate(mpb, 0, 0, 0);
        SphereShapeBuilder.build(mpb, 0.48f, 0.48f, 0.42f, DIVS, DIVS);

        Matrix4 t = new Matrix4().setToTranslation(-0.15f, 0.3f, 0).rotate(Vector3.Z, 15f);
        mpb.setVertexTransform(t);
        CylinderShapeBuilder.build(mpb, 0.08f, 0.28f, 0.02f, DIVS);

        t = new Matrix4().setToTranslation(0.15f, 0.3f, 0).rotate(Vector3.Z, -15f);
        mpb.setVertexTransform(t);
        CylinderShapeBuilder.build(mpb, 0.08f, 0.28f, 0.02f, DIVS);

        return mb.end();
    }

    // -------------------------------------------------------------------------
    // Arms
    // -------------------------------------------------------------------------

    public static Model buildArmClawLight() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("arm", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(Color.GRAY)));

        setTranslate(mpb, 0, -0.2f, 0);
        CylinderShapeBuilder.build(mpb, 0.10f, 0.40f, 0.10f, DIVS);

        setTranslate(mpb, 0, -0.55f, 0);
        CylinderShapeBuilder.build(mpb, 0.08f, 0.30f, 0.08f, DIVS);

        Matrix4 t = new Matrix4().setToTranslation(0, -0.78f, 0).rotate(Vector3.X, 180f);
        mpb.setVertexTransform(t);
        CylinderShapeBuilder.build(mpb, 0.10f, 0.20f, 0.01f, DIVS);

        return mb.end();
    }

    public static Model buildArmClawHeavy() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("arm", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(new Color(0.3f, 0.5f, 0.2f, 1f))));

        setTranslate(mpb, 0, -0.2f, 0);
        CylinderShapeBuilder.build(mpb, 0.16f, 0.45f, 0.16f, DIVS);

        setTranslate(mpb, 0, -0.58f, 0);
        SphereShapeBuilder.build(mpb, 0.22f, 0.22f, 0.22f, DIVS, DIVS);

        Matrix4 t = new Matrix4().setToTranslation(0.08f, -0.76f, 0).rotate(Vector3.Z, 20f);
        mpb.setVertexTransform(t);
        CylinderShapeBuilder.build(mpb, 0.08f, 0.28f, 0.02f, DIVS);

        t = new Matrix4().setToTranslation(-0.08f, -0.76f, 0).rotate(Vector3.Z, -20f);
        mpb.setVertexTransform(t);
        CylinderShapeBuilder.build(mpb, 0.08f, 0.28f, 0.02f, DIVS);

        return mb.end();
    }

    public static Model buildArmClub() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("arm", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(new Color(0.55f, 0.35f, 0.15f, 1f))));

        setTranslate(mpb, 0, -0.25f, 0);
        CylinderShapeBuilder.build(mpb, 0.09f, 0.50f, 0.09f, DIVS);

        setTranslate(mpb, 0, -0.62f, 0);
        SphereShapeBuilder.build(mpb, 0.22f, 0.22f, 0.22f, DIVS, DIVS);

        return mb.end();
    }

    // -------------------------------------------------------------------------
    // Legs
    // -------------------------------------------------------------------------

    public static Model buildLegPlantigrade() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("leg", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(Color.DARK_GRAY)));

        setTranslate(mpb, 0, -0.22f, 0);
        CylinderShapeBuilder.build(mpb, 0.14f, 0.44f, 0.14f, DIVS);

        setTranslate(mpb, 0, -0.60f, 0);
        CylinderShapeBuilder.build(mpb, 0.10f, 0.38f, 0.10f, DIVS);

        Matrix4 t = new Matrix4().setToTranslation(0.05f, -0.84f, 0.06f).rotate(Vector3.X, -10f);
        mpb.setVertexTransform(t);
        BoxShapeBuilder.build(mpb, 0.14f, 0.08f, 0.28f);

        return mb.end();
    }

    public static Model buildLegDigitigrade() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("leg", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(new Color(0.2f, 0.45f, 0.15f, 1f))));

        Matrix4 t = new Matrix4().setToTranslation(0, -0.18f, 0.06f).rotate(Vector3.X, 15f);
        mpb.setVertexTransform(t);
        CylinderShapeBuilder.build(mpb, 0.11f, 0.38f, 0.11f, DIVS);

        t = new Matrix4().setToTranslation(0, -0.52f, -0.04f).rotate(Vector3.X, -20f);
        mpb.setVertexTransform(t);
        CylinderShapeBuilder.build(mpb, 0.09f, 0.35f, 0.09f, DIVS);

        setTranslate(mpb, 0, -0.78f, 0.04f);
        CylinderShapeBuilder.build(mpb, 0.06f, 0.14f, 0.01f, DIVS);

        return mb.end();
    }

    // -------------------------------------------------------------------------
    // Tails
    // -------------------------------------------------------------------------

    public static Model buildTailScorpion() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("tail", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(new Color(0.25f, 0.5f, 0.18f, 1f))));

        float[] radii  = {0.12f, 0.10f, 0.08f};
        float[] yStart = {0.10f, 0.40f, 0.66f};
        for (int i = 0; i < 3; i++) {
            setTranslate(mpb, 0, yStart[i], 0);
            CylinderShapeBuilder.build(mpb, radii[i], 0.28f, radii[i], DIVS);
        }

        Matrix4 t = new Matrix4().setToTranslation(0, 0.92f, 0).rotate(Vector3.X, 180f);
        mpb.setVertexTransform(t);
        CylinderShapeBuilder.build(mpb, 0.06f, 0.18f, 0.01f, DIVS);

        return mb.end();
    }

    public static Model buildTailSpike() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("tail", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(Color.DARK_GRAY)));

        setTranslate(mpb, 0, 0.25f, 0);
        CylinderShapeBuilder.build(mpb, 0.15f, 0.50f, 0.15f, DIVS);

        Matrix4 t = new Matrix4().setToTranslation(0, 0.60f, 0).rotate(Vector3.X, 180f);
        mpb.setVertexTransform(t);
        CylinderShapeBuilder.build(mpb, 0.15f, 0.22f, 0.01f, DIVS);

        return mb.end();
    }

    public static Model buildTailWhip() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("tail", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(new Color(0.4f, 0.2f, 0.08f, 1f))));

        float[] r = {0.08f, 0.06f, 0.04f, 0.02f};
        for (int i = 0; i < 4; i++) {
            setTranslate(mpb, 0, 0.1f + i * 0.22f, 0);
            CylinderShapeBuilder.build(mpb, r[i], 0.20f, r[i], DIVS);
        }
        return mb.end();
    }

    // -------------------------------------------------------------------------
    // Bulk registration
    // -------------------------------------------------------------------------

    /**
     * Builds all placeholder Models and registers them in the library (no file I/O).
     * Call once after the GL context is ready.
     */
    public static void registerAllParts(CreaturePartLibrary library) {
        // Universal oval torso — the canonical chimera base for all assembly
        library.registerModel("torso_oval", "TORSO", buildTorsoOval());

        // Old procedural torsos (no socket — visible in part grid, not used for chimera assembly)
        library.registerModel("torso_humanoid",  buildTorsoHumanoid());
        library.registerModel("torso_insectoid", buildTorsoInsectoid());
        library.registerModel("torso_hulk",      buildTorsoHulk());

        library.registerModel("head_round",   "HEAD", buildHeadRound());
        library.registerModel("head_skulled", "HEAD", buildHeadSkulled());
        library.registerModel("head_horned",  "HEAD", buildHeadHorned());

        // Arms registered for both sockets; symmetric cylinder geometry works for either side
        library.registerModel("arm_claw_light_l", "ARM_L", buildArmClawLight());
        library.registerModel("arm_claw_light_r", "ARM_R", buildArmClawLight());
        library.registerModel("arm_claw_heavy_l", "ARM_L", buildArmClawHeavy());
        library.registerModel("arm_claw_heavy_r", "ARM_R", buildArmClawHeavy());
        library.registerModel("arm_club_l",       "ARM_L", buildArmClub());
        library.registerModel("arm_club_r",       "ARM_R", buildArmClub());

        library.registerModel("leg_plantigrade_l", "LEG_L", buildLegPlantigrade());
        library.registerModel("leg_plantigrade_r", "LEG_R", buildLegPlantigrade());
        library.registerModel("leg_digitigrade_l", "LEG_L", buildLegDigitigrade());
        library.registerModel("leg_digitigrade_r", "LEG_R", buildLegDigitigrade());

        library.registerModel("tail_scorpion", "TAIL", buildTailScorpion());
        library.registerModel("tail_spike",    "TAIL", buildTailSpike());
        library.registerModel("tail_whip",     "TAIL", buildTailWhip());
    }

    // -------------------------------------------------------------------------

    private static void setTranslate(MeshPartBuilder mpb, float x, float y, float z) {
        mpb.setVertexTransform(new Matrix4().setToTranslation(x, y, z));
    }
}
