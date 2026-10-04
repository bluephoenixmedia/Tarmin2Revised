#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_time;

// --- SPELL POST-PROCESSING UNIFORMS ---
uniform vec2 u_shockwaveCenter;
uniform float u_shockwaveProgress;
uniform float u_shockwaveStrength;
uniform vec4 u_vignetteColor;
uniform float u_vignetteIntensity;
uniform float u_glitchFactor;
uniform float u_spellChromatic;
uniform float u_sonarProgress;
uniform float u_wisdomIrisIntensity;
// Desert heat: the air over the sand wavers around the horizon at midday. 0 = off.
uniform float u_heatShimmer;
// Lakelands water mist: low rolling ground haze over the water and shallows. 0 = off.
uniform float u_waterMist;

// --- TWEAKED VALUES FOR SUBTLE CRT ---
const float curvature = 0.0;        // WAS 3.0. Now 0.3 for very slight curve. Set to 0.0 for perfectly flat.
const float scanlineIntensity = 0.12;
const float scanlineCount = 800.0;
const float vignetteStrength = 0.9; // WAS 1.2. Reduced dark corners.

void main() {
    vec2 uv = v_texCoords;

    // 0. Spatial Glitch / Dimensional Tear
    if (u_glitchFactor > 0.001) {
        float slice = sin(uv.y * 50.0 + u_time * 35.0);
        if (slice > 0.6) {
            uv.x += sin(u_time * 65.0) * 0.04 * u_glitchFactor;
        }
    }

    // 0b. Heat shimmer: a fine horizontal waver, strongest just below the horizon
    // where the hot sand meets the distance, fading out above and below.
    if (u_heatShimmer > 0.001) {
        float band = 1.0 - smoothstep(0.0, 0.22, abs(uv.y - 0.45));
        uv.x += sin(uv.y * 140.0 + u_time * 5.0) * 0.0016 * u_heatShimmer * band;
    }

    // 1. Radial Shockwave
    if (u_shockwaveProgress > 0.001 && u_shockwaveProgress < 1.0) {
        vec2 diff = uv - u_shockwaveCenter;
        diff.x *= 1.777; // Aspect ratio correction
        float dist = length(diff);
        float ringWidth = 0.14;
        if (dist > u_shockwaveProgress - ringWidth && dist < u_shockwaveProgress + ringWidth) {
            float diffDist = dist - u_shockwaveProgress;
            float factor = sin(diffDist / ringWidth * 3.14159265) * u_shockwaveStrength * (1.0 - u_shockwaveProgress);
            vec2 normDiff = normalize(diff);
            normDiff.x /= 1.777;
            uv += normDiff * factor;
        }
    }

    // 2. Curvature Distortion
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

    // 3. Chromatic Aberration (RGB Shift) with dynamic spell shift
    float chromOffset = 0.001 + u_spellChromatic * 0.018;
    float r = texture2D(u_texture, uv + vec2(chromOffset, 0.0)).r;
    float g = texture2D(u_texture, uv).g;
    float b = texture2D(u_texture, uv - vec2(chromOffset, 0.0)).b;
    vec3 color = vec3(r, g, b);

    // 4. Scanlines
    float scanline = sin(uv.y * scanlineCount) * 0.5 + 0.5;
    color -= scanline * scanlineIntensity;

    // 4b. Low rolling water mist over wetland water and shallows (subtle vapor at player's feet)
    if (u_waterMist > 0.001) {
        float groundHaze = smoothstep(0.24, 0.02, uv.y);
        float wave = sin(uv.x * 12.0 + u_time * 0.65) * cos(uv.x * 7.0 - u_time * 0.40);
        float mistDrift = (0.5 + 0.5 * wave) * groundHaze * u_waterMist;
        color = mix(color, color + vec3(0.04, 0.07, 0.08), mistDrift * 0.22);
    }

    // 5. CRT Base Vignette
    float vignette = uv.x * uv.y * (1.0 - uv.x) * (1.0 - uv.y);
    vignette = pow(vignette * 15.0, 0.25);
    vignette = mix(1.0, vignette, vignetteStrength);
    color *= vignette;

    // 6. Spell Vignette / Elemental Tint
    if (u_vignetteIntensity > 0.001) {
        vec2 vUv = uv - vec2(0.5);
        float edge = length(vUv) * 1.414;
        float vig = smoothstep(0.35, 1.0, edge) * u_vignetteIntensity;
        color = mix(color, u_vignetteColor.rgb, vig * u_vignetteColor.a);
    }

    // 7. Cartographic Sonar Pulse (Magic Mapping)
    if (u_sonarProgress > 0.001 && u_sonarProgress < 1.0) {
        vec2 sUv = uv - vec2(0.5);
        sUv.x *= 1.777;
        float dist = length(sUv);
        float waveDist = abs(dist - u_sonarProgress);
        if (waveDist < 0.035) {
            float ring = (1.0 - waveDist / 0.035) * (1.0 - u_sonarProgress);
            color += vec3(0.15, 0.95, 0.40) * ring * 0.75;
        }
        if (dist < u_sonarProgress) {
            float grid = (step(0.96, fract(uv.x * 24.0)) + step(0.96, fract(uv.y * 24.0))) * 0.12 * (1.0 - u_sonarProgress);
            color += vec3(0.1, 0.7, 0.3) * grid;
        }
    }

    // 8. Arcane Wisdom Iris (Identify)
    if (u_wisdomIrisIntensity > 0.001) {
        vec2 iUv = uv - vec2(0.5);
        iUv.x *= 1.777;
        float dist = length(iUv);
        float irisRing = smoothstep(0.32, 0.24, abs(dist - 0.28)) * u_wisdomIrisIntensity;
        color += vec3(1.0, 0.85, 0.30) * irisRing * 0.65;
    }

    gl_FragColor = v_color * vec4(color, 1.0);
}
