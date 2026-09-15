#ifdef GL_ES
precision mediump float;
#endif

// Merchant Void chain laser.
//
// A beam reads as coherent light, not a sprite: a thin white-hot core inside a
// wider tinted bloom, with energy pulses racing away from the muzzle and a
// per-segment shimmer so it never looks like a flat painted stripe. Output is
// premultiplied for additive blending (GL_ONE, GL_ONE), so overlapping beams in
// a burst stack into a brighter knot near the muzzle, the way real beams do.

varying vec4 v_color;   // rgb = beam tint, a = remaining life
varying vec2 v_uv;      // x = distance along the beam in tiles, y = -1..1 across

uniform float u_time;
uniform float u_seed;   // per-beam offset so beams in one burst flicker out of phase

float hash(float n) {
    return fract(sin(n) * 43758.5453);
}

void main() {
    float across = abs(v_uv.y);

    // Gaussian falloffs: a razor core and a soft halo around it.
    float core = exp(-across * across * 55.0);
    float halo = exp(-across * across * 5.0);

    // Pulses travel downrange, away from the muzzle.
    float pulse = 0.72 + 0.28 * sin(v_uv.x * 9.0 - u_time * 55.0 + u_seed * 6.2831);

    // Coarse shimmer along the length, re-rolled every frame-ish.
    float cell = floor(v_uv.x * 20.0) + floor(u_time * 60.0) + u_seed * 97.0;
    float shimmer = 0.82 + 0.18 * hash(cell);

    // Life fades the whole beam; square it so the tail end snaps out rather than lingers.
    float life = v_color.a * v_color.a;

    float intensity = (core * 1.7 + halo * 0.65) * pulse * shimmer * life;

    // The core burns toward white regardless of the tint; the halo carries the colour.
    vec3 color = mix(v_color.rgb, vec3(1.0), clamp(core * 0.85, 0.0, 1.0));

    gl_FragColor = vec4(color * intensity, intensity);
}
