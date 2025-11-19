#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_time;

// --- TWEAKED VALUES FOR SUBTLE CRT ---
const float curvature = 0.0;        // WAS 3.0. Now 0.3 for very slight curve. Set to 0.0 for perfectly flat.
const float scanlineIntensity = 0.12;
const float scanlineCount = 800.0;
const float vignetteStrength = 0.9; // WAS 1.2. Reduced dark corners.

void main() {
    vec2 uv = v_texCoords;

    // 1. Curvature Distortion
    // Transform UVs to -1.0 to 1.0 range
    vec2 dc = abs(0.5 - uv);
    // Apply slight curve based on distance from center
    dc *= dc;

    // Warp the UV coordinates
    uv.x -= 0.5; uv.x *= 1.0 + (dc.y * (0.3 * curvature)); uv.x += 0.5;
    uv.y -= 0.5; uv.y *= 1.0 + (dc.x * (0.4 * curvature)); uv.y += 0.5;

    // Cutoff pixels outside the curve (black borders)
    if (uv.y > 1.0 || uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // 2. Chromatic Aberration (RGB Shift)
    float r = texture2D(u_texture, uv + vec2(0.001, 0.0)).r;
    float g = texture2D(u_texture, uv).g;
    float b = texture2D(u_texture, uv + vec2(-0.001, 0.0)).b;
    vec3 color = vec3(r, g, b);

    // 3. Scanlines
    // Sine wave based on Y coordinate
    float scanline = sin(uv.y * scanlineCount) * 0.5 + 0.5;
    color -= scanline * scanlineIntensity;

    // 4. Vignette
    float vignette = uv.x * uv.y * (1.0 - uv.x) * (1.0 - uv.y);
    vignette = pow(vignette * 15.0, 0.25);
    // Apply strength
    vignette = mix(1.0, vignette, vignetteStrength);
    color *= vignette;

    gl_FragColor = v_color * vec4(color, 1.0);
}
