#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_time;

// Simple Pseudo-Random Noise
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i + vec2(0.0, 0.0)), hash(i + vec2(1.0, 0.0)), u.x),
    mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

void main() {
    // 1. Generate dynamic liquid noise
    // Scale UVs up for detail
    vec2 noiseUV = v_texCoords * 5.0;

    // Animate the 'y' coordinate downwards to simulate gravity/falling
    noiseUV.y -= u_time * 2.0;

    float n = noise(noiseUV);

    // 2. Create a soft circle mask
    // This ensures the particle looks like a puff/blob, not a hard square
    float dist = distance(v_texCoords, vec2(0.5));
    float mask = 1.0 - smoothstep(0.3, 0.5, dist); // 1 at center, 0 at edges

    // 3. Combine Noise and Mask
    // Only show pixels where noise is dense enough and inside the circle
    float alpha = smoothstep(0.4, 0.6, n * mask);

    // 4. Color it Blood Red
    vec3 bloodColor = vec3(0.6, 0.0, 0.0);

    // 5. Output
    // v_color.a comes from our Java code (lifetime fade out)
    gl_FragColor = vec4(bloodColor, alpha * v_color.a);
}
