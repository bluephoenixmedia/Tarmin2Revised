#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2 u_texelSize;
uniform float u_sharpness;
uniform float u_grain;
uniform float u_time;
uniform float u_vignetteStrength;
uniform float u_contrast;

// Spell post-processing uniforms
uniform vec2 u_shockwaveCenter;
uniform float u_shockwaveProgress;
uniform float u_shockwaveStrength;
uniform vec4 u_vignetteColor;
uniform float u_vignetteIntensity;
uniform float u_glitchFactor;
uniform float u_spellChromatic;
uniform float u_sonarProgress;
uniform float u_wisdomIrisIntensity;

void main() {
    vec2 uv = v_texCoords;

    // 0. Spatial Glitch / Dimensional Tear
    if (u_glitchFactor > 0.001) {
        float slice = sin(uv.y * 50.0 + u_time * 35.0);
        if (slice > 0.6) {
            uv.x += sin(u_time * 65.0) * 0.04 * u_glitchFactor;
        }
    }

    // 1. Radial Shockwave Refraction
    if (u_shockwaveProgress > 0.001 && u_shockwaveProgress < 1.0) {
        vec2 diff = uv - u_shockwaveCenter;
        diff.x *= 1.777; // Aspect correction
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

    // 2. Subtle edge chromatic shift
    float chrom = 0.0006 + u_spellChromatic * 0.015;

    // 3. AMD FidelityFX CAS (Contrast-Adaptive Sharpening) 5-tap kernel
    vec3 c = texture2D(u_texture, uv).rgb;
    vec3 n = texture2D(u_texture, uv + vec2(0.0, -u_texelSize.y)).rgb;
    vec3 s = texture2D(u_texture, uv + vec2(0.0,  u_texelSize.y)).rgb;
    vec3 w = texture2D(u_texture, uv + vec2(-u_texelSize.x - chrom, 0.0)).rgb;
    vec3 e = texture2D(u_texture, uv + vec2( u_texelSize.x + chrom, 0.0)).rgb;

    // Local min and max luminance to prevent haloing/ringing
    vec3 minRGB = min(c, min(min(n, s), min(w, e)));
    vec3 maxRGB = max(c, max(max(n, s), max(w, e)));

    // Contrast-Adaptive Sharpening weight
    vec3 amp = clamp(min(minRGB, 1.0 - maxRGB) / (maxRGB + 0.0001), 0.0, 1.0);
    amp = sqrt(amp);

    float peak = -1.0 / mix(8.0, 4.5, clamp(u_sharpness, 0.0, 1.0));
    vec3 wRGB = amp * peak;
    vec3 rcpWeight = 1.0 / (1.0 + 4.0 * wRGB);

    vec3 sharpened = clamp((c + (n + s + w + e) * wRGB) * rcpWeight, 0.0, 1.0);

    // 4. Atmospheric Vignette (matches the deep, focused darkness of the CRT look)
    float vig = uv.x * uv.y * (1.0 - uv.x) * (1.0 - uv.y);
    vig = clamp(pow(vig * 15.0, 0.25), 0.0, 1.0);
    float vStrength = (u_vignetteStrength > 0.001) ? u_vignetteStrength : 0.85;
    float vigFactor = mix(1.0, vig, vStrength);

    // 5. Cinematic Shadow & Tone Calibration
    // Matches the deep, moody, atmospheric darkness of the CRT filter while preserving crisp CAS sharpness
    float gamma = (u_contrast > 0.001) ? u_contrast : 1.22;
    vec3 tonemapped = pow(sharpened, vec3(gamma)) * vigFactor;

    // 5. Micro-Dither Film Grain to dissolve 8-bit gradient banding in dark fog
    if (u_grain > 0.001) {
        float noise = fract(sin(dot(uv * 1876.3, vec2(12.9898, 78.233)) + u_time * 2.2) * 43758.5453) - 0.5;
        tonemapped += noise * u_grain;
    }

    // 6. Spell Vignette / Elemental Tint
    if (u_vignetteIntensity > 0.001) {
        vec2 vUv = uv - vec2(0.5);
        float edge = length(vUv) * 1.414;
        float vig = smoothstep(0.35, 1.0, edge) * u_vignetteIntensity;
        tonemapped = mix(tonemapped, u_vignetteColor.rgb, vig * u_vignetteColor.a);
    }

    // 7. Cartographic Sonar Pulse (Magic Mapping)
    if (u_sonarProgress > 0.001 && u_sonarProgress < 1.0) {
        vec2 sUv = uv - vec2(0.5);
        sUv.x *= 1.777;
        float dist = length(sUv);
        float waveDist = abs(dist - u_sonarProgress);
        if (waveDist < 0.035) {
            float ring = (1.0 - waveDist / 0.035) * (1.0 - u_sonarProgress);
            tonemapped += vec3(0.15, 0.95, 0.40) * ring * 0.75;
        }
        if (dist < u_sonarProgress) {
            float grid = (step(0.96, fract(uv.x * 24.0)) + step(0.96, fract(uv.y * 24.0))) * 0.12 * (1.0 - u_sonarProgress);
            tonemapped += vec3(0.1, 0.7, 0.3) * grid;
        }
    }

    // 8. Arcane Wisdom Iris (Identify)
    if (u_wisdomIrisIntensity > 0.001) {
        vec2 iUv = uv - vec2(0.5);
        iUv.x *= 1.777;
        float dist = length(iUv);
        float irisRing = smoothstep(0.32, 0.24, abs(dist - 0.28)) * u_wisdomIrisIntensity;
        tonemapped += vec3(1.0, 0.85, 0.30) * irisRing * 0.65;
    }

    gl_FragColor = v_color * vec4(clamp(tonemapped, 0.0, 1.0), 1.0);
}
