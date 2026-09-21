#ifdef GL_ES
precision mediump float;
#endif

varying vec3 v_worldPos;
varying vec3 v_dir;
varying vec2 v_texCoords;

uniform float u_time;
uniform vec3 u_sunDir;
uniform vec3 u_moonDir;
uniform vec3 u_skyTint;
uniform vec3 u_horizonColor;
uniform float u_stormIntensity;
uniform float u_flashIntensity;
uniform float u_windSpeed;
uniform float u_cloudCover;
// Always-on volcanic smoke ceiling, independent of weather. Weather adds to it, never clears it.
uniform float u_smokeFloor;
// Colour of the top of the vault: kept dark so the fire stays a horizon band, not a flood.
uniform vec3 u_zenithColor;

// High-speed analytical hash & 2D smooth noise
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i + vec2(0.0, 0.0)), hash21(i + vec2(1.0, 0.0)), u.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), u.x), u.y);
}

// 5-octave Fractal Brownian Motion for rich billowy cloud structures
float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(0.87758, 0.47942, -0.47942, 0.87758); // Rotation matrix for organic turbulence
    for (int i = 0; i < 5; ++i) {
        v += a * noise(p);
        p = rot * p * 2.02 + vec2(12.3, 24.7);
        a *= 0.5;
    }
    return v;
}

void main() {
    // Upward angle: v_dir.y in [-1, 1]
    float up = clamp(v_dir.y, 0.0, 1.0);
    
    // Curved celestial dome projection coordinates
    float domeProjection = 1.0 / (up + 0.18);
    vec2 skyUV = v_dir.xz * domeProjection * 0.45;
    
    // Wind drift vectors (Layer 1 slow overcast, Layer 2 fast scud)
    float speed = u_windSpeed * (1.0 + u_stormIntensity * 1.5);
    vec2 windUpper = vec2(0.015, 0.008) * u_time * speed;
    vec2 windLower = vec2(0.042, 0.022) * u_time * speed;

    // Smoke is geology, not weather: CLEAR now means "no precipitation", not "empty sky".
    float cover = clamp(max(u_cloudCover, u_smokeFloor), 0.0, 1.0);

    // --- BEARING ---
    // Castle Tarmin stands due north (world -Z) and the volcano burns behind it. Concentrating
    // the fire on that bearing turns the sky into a compass: you navigate toward the apocalypse.
    vec2 flatDir = v_dir.xz;
    float flatLen = max(length(flatDir), 0.0001);
    float northness = -flatDir.y / flatLen; // flatDir.y is v_dir.z; -Z is north
    float northWeight = mix(0.16, 1.0, smoothstep(-0.35, 0.95, northness));

    // --- 1. BASE ATMOSPHERIC SKY GRADIENT ---
    // Hot at the horizon, choked purple-black at the zenith. The steeper falloff keeps the fire
    // a tight band above the walls rather than washing the whole view orange.
    vec3 zenithSky = u_zenithColor;
    vec3 horizonSky = mix(u_horizonColor, u_skyTint, 0.72) * 1.15;
    vec3 skyBase = mix(horizonSky, zenithSky, pow(up, 0.42));

    // --- 1B. ERUPTION GLOW ---
    // A tight band of fire hugging the horizon, hottest to the north, pulsing slowly.
    float horizonGlow = exp(-up * 6.5);
    float plumePulse = 0.82 + 0.18 * sin(u_time * 0.55 + northness * 2.0);
    skyBase += vec3(1.0, 0.34, 0.07) * horizonGlow * northWeight * 0.60 * plumePulse;

    // --- 1D. ERUPTION PLUMES ---
    // Columns of fire and smoke rising off the northern ridge. Procedural rather than modelled:
    // these have to churn and rise continuously, which sprite or mesh plumes do badly.
    float plumeBand = smoothstep(0.50, 1.0, northness);
    if (plumeBand > 0.01) {
        float across = flatDir.x / flatLen;
        float colNoise = fbm(vec2(across * 4.5, up * 2.2 - u_time * 0.14));
        float column = smoothstep(0.42, 0.86, colNoise);
        float rise = exp(-up * 3.0);
        skyBase += vec3(1.0, 0.30, 0.05) * column * rise * plumeBand * 0.95;
    }

    // --- 1C. CELESTIAL SMEAR ---
    // Under a permanent smoke ceiling the sun and moon never resolve into discs. They remain a
    // diffuse stain that still tracks across the sky, so the hour stays readable without
    // contradicting the choked-sky premise.
    float sunDot = max(dot(v_dir, u_sunDir), 0.0);
    float moonDot = max(dot(v_dir, u_moonDir), 0.0);
    float smokeOcclusion = 1.0 - cover * 0.55;
    if (u_sunDir.y > -0.15) {
        skyBase += vec3(1.0, 0.30, 0.10) * pow(sunDot, 2.2) * 0.42 * smokeOcclusion;
    }
    if (u_moonDir.y > -0.15) {
        skyBase += vec3(0.55, 0.32, 0.42) * pow(moonDot, 3.0) * 0.16 * smokeOcclusion;
    }

    // --- 2. LAYER 1: UPPER TURBULENT OVERCAST CANOPY ---
    vec2 p1 = skyUV * 0.85 + windUpper;
    float n1 = fbm(p1);
    // Density threshold dynamically scaled by cloud cover
    float minCutoff1 = mix(0.72, 0.10, cover);
    float cloud1 = smoothstep(minCutoff1, minCutoff1 + 0.45, n1);

    // --- 3. LAYER 2: LOW GALE-FORCE SCUD WISPS ---
    vec2 p2 = skyUV * 1.75 + windLower;
    float n2 = fbm(p2);
    float minCutoff2 = mix(0.80, 0.24, cover);
    float cloud2 = smoothstep(minCutoff2, minCutoff2 + 0.35, n2) * 0.75;

    // Combined multi-layer cloud coverage: drops cleanly to 0 in clear weather
    float cloudCoverage = clamp((cloud1 + cloud2 * (1.0 - cloud1 * 0.6)) * (0.55 + 0.75 * cover), 0.0, 1.0);

    // Starfield removed: nothing is visible through a permanent volcanic smoke ceiling.

    // --- 4. VOLUMETRIC CLOUD LIGHTING & COLOR PALETTE ---
    // Volcanic smoke: near-black bodies, not grey storm cloud. Colour comes from what is
    // burning underneath them, never from a sun above.
    vec3 smokeCore = vec3(0.05, 0.03, 0.06) * (1.0 - u_stormIntensity * 0.3);
    vec3 smokeMid  = vec3(0.17, 0.10, 0.17);
    vec3 cloudColor = mix(smokeCore, smokeMid, smoothstep(0.2, 0.8, n1));

    // Secondary scud wisps are darker and more ragged
    vec3 scudColor = vec3(0.04, 0.03, 0.05);
    cloudColor = mix(cloudColor, scudColor, cloud2 * 0.5);

    // Underlighting: the lower a cloud sits, the more of the burning horizon it catches.
    // This is what turns a flat grey overcast into a churning, lit-from-below ceiling.
    float underlight = pow(1.0 - up, 2.4);
    vec3 emberLight = mix(vec3(0.95, 0.26, 0.07), u_skyTint, 0.30);
    cloudColor += emberLight * underlight * (0.55 + 0.45 * smoothstep(0.15, 0.75, n1));

    // --- 4B. EMBER RIM ---
    // Cloud edges facing the eruption catch a hot rim: the silver lining of a burning world.
    float emberRim = pow(max(northness, 0.0), 3.0);
    cloudColor += vec3(1.0, 0.38, 0.10) * emberRim * smoothstep(0.35, 0.85, n1) * (1.0 - up) * 0.55;

    // --- 5. LIGHTNING ILLUMINATION BURST ---
    if (u_flashIntensity > 0.02) {
        // Internal cloud volume scattering: backlit cloud lobes glow bright electric violet-white
        vec3 flashColor = vec3(0.92, 0.95, 1.05) * (u_flashIntensity * 1.6);
        // Deep folds scatter light unevenly, giving rich 3D contour depth during flashes
        float flashScattering = smoothstep(0.1, 0.7, n1) * 0.9 + 0.1;
        cloudColor = mix(cloudColor, flashColor, flashScattering * u_flashIntensity);
        skyBase = mix(skyBase, vec3(0.85, 0.88, 1.0), u_flashIntensity * 0.75);
    }

    // Blend clouds over atmospheric sky background
    vec3 finalColor = mix(skyBase, cloudColor, cloudCoverage);

    // Smooth horizon blending: fade seamlessly into horizon atmospheric fog
    // Keep this tight: a wide blend washes the cloud ceiling away exactly where it is
    // most dramatic, just above the walls.
    float horizonBlend = smoothstep(0.0, 0.05, up);
    finalColor = mix(horizonSky, finalColor, horizonBlend);

    // --- 6. EMBER DRIFT ---
    // Sparse embers rising on the thermals, concentrated toward the eruption.
    vec2 emberUV = skyUV * 3.2 + vec2(0.02, -0.11) * u_time * (0.6 + u_windSpeed * 0.3);
    vec2 emberId = floor(emberUV);
    float emberHash = hash21(emberId);
    if (emberHash > 0.976) {
        vec2 emberF = fract(emberUV) - 0.5;
        float ember = smoothstep(0.22, 0.0, length(emberF));
        float flicker = 0.55 + 0.45 * sin(u_time * (3.0 + emberHash * 9.0) + emberHash * 30.0);
        finalColor += vec3(1.0, 0.45, 0.12) * ember * flicker * northWeight * exp(-up * 3.0) * 0.9;
    }

    // Below the horizon the dome must not glow: the ground is not on fire, the sky is.
    float below = clamp(-v_dir.y, 0.0, 1.0);
    finalColor *= 1.0 - below * 0.82;

    gl_FragColor = vec4(finalColor, 1.0);
}
