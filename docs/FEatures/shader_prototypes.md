# Disciplined Flow Engineering Spec: LibGDX Retro Shader Pipeline

## 1. Objective
Implement a retro 3D post-processing pipeline in Java using the LibGDX framework. The pipeline will apply four distinct visual effects—Distance Fog, Ordered Dithering, Color Quantization, and CRT Emulation—using OpenGL ES 2.0/3.0 compatible GLSL shaders over a rendered `FrameBuffer` (FBO).

## 2. Architecture & Environment Requirements
*   **Framework:** Java with LibGDX.
*   **Rendering Target:** The primary game scene (raycaster or 3D view) must be rendered to a low-resolution `FrameBuffer` (e.g., 320x240 or 640x480).
*   **Post-Processing:** The resulting FBO texture will be rendered to the screen via a full-screen quad using an `OrthographicCamera`, a `SpriteBatch`, and a custom `ShaderProgram`.
*   **Texture Filtering:** Set base FBO texture filtering to `TextureFilter.Nearest` to preserve hard pixel edges.

## 3. Shader Specifications (GLSL)

### 3.1. Vertex Shader (`retro.vert`)
A standard passthrough vertex shader that correctly passes the texture coordinates and position data from LibGDX's `SpriteBatch` to the fragment shader.

### 3.2. Fragment Shader (`retro.frag`)
The fragment shader must execute the following effects in order. 

**Required Uniforms:**
*   `sampler2D u_texture`: The rendered scene from the FBO.
*   `sampler2D u_depthBuffer`: The depth buffer from the scene (for fog calculation).
*   `vec2 u_resolution`: The screen resolution (for CRT coordinate distortion and dithering grid alignment).
*   `float u_time`: Elapsed game time (for subtle CRT pulse or noise).

**Effect A: Distance-Based Fog**
*   Sample the depth buffer.
*   Linearize the depth value.
*   Use `mix()` to interpolate between the sampled texture color and a defined fog color (e.g., `vec3(0.05, 0.05, 0.1)`) based on the linearized depth.

**Effect B: Ordered Dithering (Bayer Matrix)**
*   Define a 4x4 or 8x8 Bayer matrix array in the shader.
*   Calculate the screen-space pixel coordinate using `gl_FragCoord.xy`.
*   Map the pixel coordinate to the Bayer matrix using modulo math (`mod(x, 4.0)`).
*   Apply the matrix threshold to the fog-adjusted color luminance to determine if the pixel should be clamped to a lighter or darker value.

**Effect C: Color Quantization**
*   Restrict the output color channels to a limited palette simulating 8-bit or 16-bit color depth.
*   Multiply the RGB values by a stepping factor (e.g., 8.0), apply `floor()`, and divide by the stepping factor.

**Effect D: CRT Emulation (Final Output)**
*   **Barrel Distortion:** Warp the UV coordinates slightly outward from the center `vec2(0.5, 0.5)` using a cubic function.
*   **Scanlines:** Use `sin()` based on `gl_FragCoord.y` to darken alternating horizontal rows of pixels.
*   **Chromatic Aberration:** Sample the distorted UV coordinates three separate times with slight horizontal offsets for the R, G, and B channels.

## 4. LibGDX Implementation Steps for Antigravity

Instruct the AI to generate the Java implementation using the following flow:

1.  **Initialization:**
    *   Create a `FrameBuffer` matching the desired internal retro resolution.
    *   Load `retro.vert` and `retro.frag` into a `ShaderProgram`.
    *   Set `ShaderProgram.pedantic = false;` to avoid crashes if a uniform is optimized out during testing.
2.  **Render Loop (Game Scene):**
    *   Call `fbo.begin()`.
    *   Clear the screen with `GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT`.
    *   Render the 3D scene/raycaster.
    *   Call `fbo.end()`.
3.  **Render Loop (Post-Processing):**
    *   Call `spriteBatch.setShader(retroShader)`.
    *   Pass the required uniforms: `retroShader.setUniformf("u_resolution", width, height);`, etc.
    *   Begin the batch, draw the `fbo.getColorBufferTexture()` stretched to the actual screen dimensions, and end the batch.

## 5. Success Criteria
*   The code must compile and run in a standard LibGDX desktop launcher.
*   The shader must compile without GLSL syntax errors.
*   The final output should clearly display the hard pixel edges of the original FBO, distorted by the CRT effect, with visible banding from the quantization and dithering.