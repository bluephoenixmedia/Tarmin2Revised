# Implementing a Dynamic Blood System in LibGDX

This guide outlines the architecture and step-by-step process for replicating a "Project Brutality" style dynamic blood system—complete with flying particles, environmental decals, and object pooling—using Java and the LibGDX game development framework.

## 1. Core Architecture

To achieve high performance without triggering Java's Garbage Collector (GC) stutter, the system must rely on **Object Pooling**. You will need three primary managers:
1.  **Particle Manager:** Handles the flying blood drops (physics, velocity, gravity).
2.  **Decal Manager:** Handles the static blood splatters on the walls, floors, and ceilings.
3.  **Collision System:** Bridges the two by detecting when a particle hits a surface and triggering a decal.

## 2. Step-by-Step Implementation

### Step 1: Create a `Poolable` Blood Particle
LibGDX has a built-in `Pool` interface. Your blood particle class must implement `Pool.Poolable` so it can be reset and reused.

*   **Properties:** `Vector2 position`, `Vector2 velocity`, `float lifespan`, `boolean active`, `Color color` (for varying shades of red).
*   **Methods:** 
    *   `init(x, y, velocityX, velocityY)`: Sets starting state.
    *   `update(deltaTime)`: Applies gravity to velocity, and velocity to position.
    *   `reset()`: Required by the `Poolable` interface. Clears all data when returned to the pool.

### Step 2: Build the Object Pool & Emitter
Create a manager class that holds a LibGDX `Pool<BloodParticle>`. 
*   **The "Gore Setting":** Implement a variable (e.g., `goreMultiplier`). When an enemy is hit, calculate `baseParticles * goreMultiplier`. 
*   **Spawning:** Loop through the required number, call `pool.obtain()`, initialize them with randomized spread/velocity vectors, and add them to an active array.
*   **Despawning:** When a particle hits a wall or its lifespan ends, remove it from the active array and call `pool.free(particle)`.

### Step 3: Implement Collision Detection
LibGDX's Box2D is perfect for this, but you can also use simple AABB (Axis-Aligned Bounding Box) logic or raycasting if you are using custom physics.
*   In the `update()` loop, project the particle's next position. 
*   If the next position intersects with a floor or wall rectangle, mark the particle for deactivation.
*   Pass the exact point of intersection and the surface normal (is it a wall or a floor?) to the **Decal Manager**.

### Step 4: The Decal Manager (Splatters and Pools)
When the Particle Manager reports a collision, spawn a static sprite.
*   **Floor Collisions (Pools):** Check if a decal already exists near this coordinate. If yes, simply scale up the existing sprite (`sprite.setScale()`) to simulate a growing puddle. If no, spawn a new flat puddle sprite.
*   **Wall Collisions (Splatters):** Spawn a splatter sprite rotated to match the wall's orientation.

### Step 5: Advanced State Machines (Drips)
To make the system dynamic, wall and ceiling decals need their own simple `update()` logic.
*   **Wall Drip:** Give wall decals a slowly decreasing Y-coordinate over time. Stretch the sprite along the Y-axis to leave a "trail." Once it reaches the floor's Y-coordinate, convert it to a floor pool.
*   **Ceiling Drop:** Give ceiling decals a timer. Every `X` seconds, trigger the Particle Manager to spawn a single blood drop straight down with low initial velocity.

## 3. Performance Checklist

*   **Zero Allocations:** During gameplay, the `new` keyword should **never** be used for blood particles or decals. Pre-allocate arrays and pools during the loading screen.
*   **SpriteBatching:** Ensure all blood particles share the same `TextureAtlas`. Draw them all within a single `SpriteBatch.begin()` and `end()` block to minimize OpenGL draw calls.
*   **Culling:** Do not update or draw particles/decals that are currently outside the camera's viewport (Frustum Culling). LibGDX's `Camera.frustum.boundsInFrustum()` is highly efficient for this.