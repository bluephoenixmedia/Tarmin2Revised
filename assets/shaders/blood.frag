#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;
varying vec2 v_screenPos;

uniform sampler2D u_texture;
uniform float u_bloodIntensity;
uniform float u_time;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

void main() {
    vec4 texColor = texture2D(u_texture, v_texCoords);
    vec4 baseColor = v_color * texColor;

    if (u_bloodIntensity > 0.01) {
        // [NEW] Pixelate the coordinate for "Chunky" Retro Noise (4x4 pixel blocks)
        vec2 pixelCoord = floor(gl_FragCoord.xy / 4.0);
        float n = noise(pixelCoord * 0.2);

        // [NEW] Safety Cap: Limit visual coverage to 70% max
        float effectiveIntensity = min(u_bloodIntensity, 0.7);

        if (n < effectiveIntensity) {
            vec4 bloodColor = vec4(0.6, 0.05, 0.05, 1.0);
            baseColor = mix(baseColor, bloodColor, 0.6);
        }
    }

    gl_FragColor = baseColor;
}
