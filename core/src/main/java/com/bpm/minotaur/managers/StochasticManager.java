package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.physics.bullet.linearmath.btMotionState;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

public class StochasticManager implements Disposable {

    // --- Physics System ---
    private final btCollisionConfiguration collisionConfig;
    private final btCollisionDispatcher dispatcher;
    private final btBroadphaseInterface broadphase;
    private final btConstraintSolver solver;
    private final btDiscreteDynamicsWorld dynamicsWorld;

    // --- Rendering System ---
    private final ModelBatch modelBatch;
    private final Environment environment;
    private final PerspectiveCamera camera;

    // --- Simulation Objects ---
    private final Array<ModelInstance> instances = new Array<>();
    private final Array<Model> models = new Array<>();
    private final Array<btRigidBody> bodies = new Array<>();
    private final Array<btCollisionShape> shapes = new Array<>();

    // --- Dice Tracking ---
    private final Array<btRigidBody> activeDice = new Array<>();
    private final Array<ModelInstance> dieInstances = new Array<>();
    private final Array<Model> dieModels = new Array<>();
    private final Array<btCollisionShape> dieShapes = new Array<>();
    private final Array<btMotionState> dieMotionStates = new Array<>();

    // --- Textures ---
    private final Map<Integer, Texture> pipTextures = new HashMap<>();

    // --- Camera Control ---
    private final Vector3 camStartPos = new Vector3(0, 25f, 1f);
    private final Vector3 camTargetPos = new Vector3();
    private boolean isZooming = false;

    private static final float VIRTUAL_WIDTH = 1920f;
    private static final float VIRTUAL_HEIGHT = 1080f;

    public StochasticManager() {
        // 1. Initialize Physics
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);

        // --- HEAVY GRAVITY FOR SPEED ---
        dynamicsWorld.setGravity(new Vector3(0, -60f, 0));

        // 2. Initialize Rendering
        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        // 3. Setup Camera
        camera = new PerspectiveCamera(67, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        camera.position.set(camStartPos);
        camera.lookAt(0, 0, 0);
        camera.near = 1f;
        camera.far = 300f;
        camera.update();

        // 4. Generate Pip Textures (1-6)
        generatePipTextures();

        createTray();
    }

    private void generatePipTextures() {
        for (int i = 1; i <= 6; i++) {
            pipTextures.put(i, createPipTexture(i));
        }
    }

    private Texture createPipTexture(int number) {
        int size = 128;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // Background (Dice Color - Whiteish)
        pixmap.setColor(0.9f, 0.9f, 0.9f, 1f);
        pixmap.fill();

        // Border
        pixmap.setColor(0.7f, 0.7f, 0.7f, 1f);
        pixmap.drawRectangle(0, 0, size, size);

        // Pips (Black)
        pixmap.setColor(Color.BLACK);
        int r = 12; // Radius
        int c = size / 2;
        int q1 = size / 4;
        int q3 = size * 3 / 4;

        // Logic for standard dice pips
        if (number % 2 != 0) pixmap.fillCircle(c, c, r); // Center dot for 1, 3, 5
        if (number > 1) {
            pixmap.fillCircle(q3, q1, r); // Top Right
            pixmap.fillCircle(q1, q3, r); // Bottom Left
        }
        if (number > 3) {
            pixmap.fillCircle(q1, q1, r); // Top Left
            pixmap.fillCircle(q3, q3, r); // Bottom Right
        }
        if (number == 6) {
            pixmap.fillCircle(q1, c, r); // Middle Left
            pixmap.fillCircle(q3, c, r); // Middle Right
        }

        Texture tex = new Texture(pixmap);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return tex;
    }

    private void createTray() {
        ModelBuilder mb = new ModelBuilder();

        // Floor
        Model groundModel = mb.createBox(20f, 1f, 20f,
            new Material(ColorAttribute.createDiffuse(Color.DARK_GRAY)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        models.add(groundModel);
        ModelInstance groundInstance = new ModelInstance(groundModel);
        instances.add(groundInstance);

        btCollisionShape groundShape = new btBoxShape(new Vector3(10f, 0.5f, 10f));
        shapes.add(groundShape);

        btRigidBody.btRigidBodyConstructionInfo groundInfo = new btRigidBody.btRigidBodyConstructionInfo(0, null, groundShape, Vector3.Zero);
        btRigidBody groundBody = new btRigidBody(groundInfo);
        groundBody.setFriction(0.8f);
        bodies.add(groundBody);
        dynamicsWorld.addRigidBody(groundBody);
        groundInfo.dispose();

        // Walls
        float wallHeight = 4f;
        float wallThickness = 2f;
        float wallLength = 22f;
        float wallY = 0.5f + (wallHeight / 2f);
        Color wallColor = Color.GRAY;

        Model wallNSModel = mb.createBox(wallLength, wallHeight, wallThickness, new Material(ColorAttribute.createDiffuse(wallColor)), VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        Model wallEWModel = mb.createBox(wallThickness, wallHeight, wallLength, new Material(ColorAttribute.createDiffuse(wallColor)), VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        models.add(wallNSModel);
        models.add(wallEWModel);

        btBoxShape shapeNS = new btBoxShape(new Vector3(wallLength/2, wallHeight/2, wallThickness/2));
        btBoxShape shapeEW = new btBoxShape(new Vector3(wallThickness/2, wallHeight/2, wallLength/2));
        shapes.add(shapeNS);
        shapes.add(shapeEW);

        addStaticWall(wallNSModel, shapeNS, 0, wallY, -11);
        addStaticWall(wallNSModel, shapeNS, 0, wallY, 11);
        addStaticWall(wallEWModel, shapeEW, 11, wallY, 0);
        addStaticWall(wallEWModel, shapeEW, -11, wallY, 0);
    }

    private void addStaticWall(Model model, btCollisionShape shape, float x, float y, float z) {
        ModelInstance instance = new ModelInstance(model);
        instance.transform.setToTranslation(x, y, z);
        instances.add(instance);

        btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(0, null, shape, Vector3.Zero);
        btRigidBody body = new btRigidBody(info);
        body.setWorldTransform(instance.transform);
        body.setFriction(0.5f);
        body.setRestitution(0.5f);

        bodies.add(body);
        dynamicsWorld.addRigidBody(body);
        info.dispose();
    }

    public void spawnDie(int damagePotential, float impulsePower) {
        clearDice();
        isZooming = false;
        camera.position.set(camStartPos);
        camera.lookAt(0,0,0);
        camera.update();

        int sides = 6; // Focusing on D6 for stability and visuals

        // 1. Create Visual Model with Pips
        Model visualModel = createD6Model();
        dieModels.add(visualModel);

        ModelInstance dieInstance = new ModelInstance(visualModel);
        dieInstance.transform.setToTranslation(0, 12f, 0);

        instances.add(dieInstance);
        dieInstances.add(dieInstance);

        // 2. Physics Shape (Cube)
        btBoxShape boxShape = new btBoxShape(new Vector3(1.5f, 1.5f, 1.5f)); // Half-extents for 3x3x3 box
        dieShapes.add(boxShape);

        Vector3 localInertia = new Vector3();
        boxShape.calculateLocalInertia(1f, localInertia);

        MyMotionState motionState = new MyMotionState(dieInstance.transform);
        dieMotionStates.add(motionState);

        btRigidBody.btRigidBodyConstructionInfo dieInfo = new btRigidBody.btRigidBodyConstructionInfo(1f, motionState, boxShape, localInertia);
        btRigidBody dieBody = new btRigidBody(dieInfo);
        dieInfo.dispose();

        // Physics Tweaks
        dieBody.setFriction(0.6f);
        dieBody.setRestitution(0.3f);
        dieBody.setDamping(0.05f, 0.05f);
        dieBody.setCcdMotionThreshold(1e-7f);
        dieBody.setCcdSweptSphereRadius(0.5f);

        bodies.add(dieBody);
        activeDice.add(dieBody);
        dynamicsWorld.addRigidBody(dieBody);

        // Throw
        dieBody.setAngularVelocity(new Vector3((float)Math.random()*15, (float)Math.random()*15, (float)Math.random()*15));
        dieBody.applyCentralImpulse(new Vector3((float)(Math.random()-0.5)*impulsePower, -impulsePower * 1.2f, (float)(Math.random()-0.5)*impulsePower));
        dieBody.activate();
    }

    /**
     * Builds a Cube using 6 separate rectangles, each with a specific Pip Texture.
     */
    private Model createD6Model() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();

        long attr = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates;
        float s = 1.5f; // half size

        // Face 1 (Top, Y+)
        mp(mb, 1, attr).rect(-s, s, s, s, s, s, s, s, -s, -s, s, -s, 0, 1, 0);

        // Face 6 (Bottom, Y-)
        mp(mb, 6, attr).rect(-s, -s, -s, s, -s, -s, s, -s, s, -s, -s, s, 0, -1, 0);

        // Face 2 (Front, Z+)
        mp(mb, 2, attr).rect(-s, s, s, -s, -s, s, s, -s, s, s, s, s, 0, 0, 1);

        // Face 5 (Back, Z-)
        mp(mb, 5, attr).rect(s, s, -s, s, -s, -s, -s, -s, -s, -s, s, -s, 0, 0, -1);

        // Face 3 (Right, X+)
        mp(mb, 3, attr).rect(s, s, s, s, -s, s, s, -s, -s, s, s, -s, 1, 0, 0);

        // Face 4 (Left, X-)
        mp(mb, 4, attr).rect(-s, s, -s, -s, -s, -s, -s, -s, s, -s, s, s, -1, 0, 0);

        return mb.end();
    }

    private MeshPartBuilder mp(ModelBuilder mb, int number, long attr) {
        Material mat = new Material(TextureAttribute.createDiffuse(pipTextures.get(number)));
        return mb.part("face"+number, GL20.GL_TRIANGLES, attr, mat);
    }

    public int getRolledResult() {
        if (activeDice.size == 0) return 1;

        // Get Rotation Matrix
        btRigidBody die = activeDice.get(0);
        Matrix4 transform = die.getWorldTransform();

        // Local Normals for faces 1 to 6
        Vector3[] normals = {
            new Vector3(0, 1, 0),  // 1
            new Vector3(0, 0, 1),  // 2
            new Vector3(1, 0, 0),  // 3
            new Vector3(-1, 0, 0), // 4
            new Vector3(0, 0, -1), // 5
            new Vector3(0, -1, 0)  // 6
        };
        int[] values = { 1, 2, 3, 4, 5, 6 };

        // Find which local normal is pointing closest to World Up (0, 1, 0)
        float maxDot = -Float.MAX_VALUE;
        int result = 1;

        Vector3 worldUp = new Vector3(0, 1, 0);
        Vector3 rotatedNormal = new Vector3();

        for(int i=0; i<6; i++) {
            rotatedNormal.set(normals[i]).rot(transform);
            float dot = rotatedNormal.dot(worldUp);
            if (dot > maxDot) {
                maxDot = dot;
                result = values[i];
            }
        }

        return result;
    }

    public boolean areDiceSettled() {
        if (activeDice.size == 0) return false;
        boolean allSleeping = true;
        for (btRigidBody body : activeDice) {
            if (body.getLinearVelocity().len2() > 0.01f || body.getAngularVelocity().len2() > 0.01f) {
                allSleeping = false;
                break;
            }
        }
        return allSleeping;
    }

    public void update(float delta) {
        dynamicsWorld.stepSimulation(delta, 5, 1f/60f);

        // Emergency Reset
        for (btRigidBody body : activeDice) {
            Vector3 pos = body.getCenterOfMassPosition();
            if (pos.y < -5) {
                Gdx.app.error("Physics", "DIE FELL OUT OF WORLD! Resetting...");
                Matrix4 resetTrans = new Matrix4().setToTranslation(0, 10, 0);
                body.setWorldTransform(resetTrans);
                body.setLinearVelocity(Vector3.Zero);
                body.setAngularVelocity(Vector3.Zero);
                body.activate();
            }
        }

        // Camera Zoom Logic
        if (areDiceSettled() && activeDice.size > 0) {
            isZooming = true;
            Vector3 diePos = activeDice.get(0).getCenterOfMassPosition();
            // Target: Above the die, slightly offset
            camTargetPos.set(diePos.x, diePos.y + 8f, diePos.z + 1f);
        }

        if (isZooming && activeDice.size > 0) {
            // Speed up the zoom!
            camera.position.lerp(camTargetPos, 8f * delta);
            Vector3 diePos = activeDice.get(0).getCenterOfMassPosition();
            camera.lookAt(diePos); // Always look at die
            camera.update();
        }
    }

    public void render() {
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        modelBatch.begin(camera);
        modelBatch.render(instances, environment);
        modelBatch.end();
    }

    public void clearDice() {
        // Stop zooming so we don't access empty list
        isZooming = false;

        for (btRigidBody body : activeDice) {
            dynamicsWorld.removeRigidBody(body);
            bodies.removeValue(body, true);
            body.dispose();
        }
        activeDice.clear();
        instances.removeAll(dieInstances, true);
        dieInstances.clear();
        for (Model m : dieModels) m.dispose();
        dieModels.clear();
        for (btCollisionShape s : dieShapes) s.dispose();
        dieShapes.clear();
        for (btMotionState ms : dieMotionStates) ms.dispose();
        dieMotionStates.clear();
    }

    @Override
    public void dispose() {
        clearDice();
        for (btRigidBody body : bodies) {
            dynamicsWorld.removeRigidBody(body);
            body.dispose();
        }
        for (btCollisionShape shape : shapes) shape.dispose();
        for (Model model : models) model.dispose();
        for (Texture t : pipTextures.values()) t.dispose();

        dynamicsWorld.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfig.dispose();
        modelBatch.dispose();
    }

    static class MyMotionState extends btMotionState {
        private final com.badlogic.gdx.math.Matrix4 transform;
        public MyMotionState(com.badlogic.gdx.math.Matrix4 transform) {
            this.transform = transform;
        }
        @Override
        public void getWorldTransform(com.badlogic.gdx.math.Matrix4 worldTrans) {
            worldTrans.set(transform);
        }
        @Override
        public void setWorldTransform(com.badlogic.gdx.math.Matrix4 worldTrans) {
            transform.set(worldTrans);
        }
    }
}
