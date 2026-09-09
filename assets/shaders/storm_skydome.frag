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

    // --- 1. BASE ATMOSPHERIC SKY GRADIENT ---
    vec3 zenithSky = u_skyTint * 0.65;
    vec3 horizonSky = mix(u_horizonColor, u_skyTint * 0.75, 0.5);
    vec3 skyBase = mix(horizonSky, zenithSky, pow(up, 0.6));

    // --- 2. LAYER 1: UPPER TURBULENT OVERCAST CANOPY ---
    vec2 p1 = skyUV * 0.85 + windUpper;
    float n1 = fbm(p1);
    // Density threshold dynamically scaled by cloud cover
    float minCutoff1 = mix(0.72, 0.22, u_cloudCover);
    float cloud1 = smoothstep(minCutoff1, minCutoff1 + 0.45, n1);

    // --- 3. LAYER 2: LOW GALE-FORCE SCUD WISPS ---
    vec2 p2 = skyUV * 1.75 + windLower;
    float n2 = fbm(p2);
    float minCutoff2 = mix(0.80, 0.35, u_cloudCover);
    float cloud2 = smoothstep(minCutoff2, minCutoff2 + 0.35, n2) * 0.75;

    // Combined multi-layer cloud coverage: drops cleanly to 0 in clear weather
    float cloudCoverage = clamp((cloud1 + cloud2 * (1.0 - cloud1 * 0.6)) * u_cloudCover, 0.0, 1.0);

    // --- 3B. PROCEDURAL NIGHT STARFIELD (Clear / Partly Cloudy Nights) ---
    float nightFactor = clamp(-u_sunDir.y * 3.5, 0.0, 1.0);
    float starVisibility = (1.0 - cloudCoverage) * nightFactor;
    if (starVisibility > 0.02 && up > 0.04) {
        vec2 starCoord = (v_dir.xz / (up + 0.12)) * 140.0;
        vec2 starId = floor(starCoord);
        vec2 starFract = fract(starCoord) - 0.5;
        float starHash = hash21(starId);

        if (starHash > 0.935) { // Top 6.5% cells contain a star
            float starDist = length(starFract);
            float twinkle = sin(u_time * (2.0 + starHash * 6.0) + starHash * 14.0) * 0.35 + 0.65;
            float starGlow = smoothstep(0.16, 0.0, starDist) * twinkle * (starHash - 0.935) * 18.0;
            // Warm-white to silver-blue stellar temperatures
            vec3 starColor = mix(vec3(0.80, 0.90, 1.0), vec3(1.0, 0.96, 0.85), fract(starHash * 43.0));
            skyBase += starColor * starGlow * starVisibility;
        }

        // Subtle galactic dust haze along tilted celestial plane
        float milkyWay = exp(-abs(v_dir.x * 0.85 + v_dir.z * 0.52) * 4.5) * pow(up, 0.75);
        skyBase += vec3(0.06, 0.08, 0.14) * milkyWay * starVisibility;
    }

    // --- 4. VOLUMETRIC CLOUD LIGHTING & COLOR PALETTE ---
    // Deep bruised storm core vs slate highlight
    vec3 darkStormCore = vec3(0.08, 0.08, 0.12) * (1.0 - u_stormIntensity * 0.3);
    vec3 midStormSlate = vec3(0.20, 0.18, 0.26);
    vec3 cloudColor = mix(darkStormCore, midStormSlate, smoothstep(0.2, 0.8, n1));

    // Secondary scud wisps are darker and more ragged
    vec3 scudColor = vec3(0.06, 0.06, 0.09);
    cloudColor = mix(cloudColor, scudColor, cloud2 * 0.5);

    // --- 4B. CELESTIAL CORONA THROUGH CLOUDS (Time-of-day solar/lunar position) ---
    float sunDot = max(dot(v_dir, u_sunDir), 0.0);
    float moonDot = max(dot(v_dir, u_moonDir), 0.0);

    if (u_sunDir.y > -0.10 && u_cloudCover > 0.05) {
        // Atmospheric solar corona: broad soft glow + brighter core
        float sunCorona = pow(sunDot, 3.5) * 0.55 + pow(sunDot, 22.0) * 0.75;
        // Warm gold/rose at dawn/dusk, radiant warm-white at midday
        vec3 sunColor = mix(vec3(1.0, 0.60, 0.25), vec3(1.0, 0.96, 0.90), clamp(u_sunDir.y * 3.0, 0.0, 1.0));
        vec3 sunGlow = sunColor * sunCorona * mix(0.95, 0.55, u_stormIntensity) * u_cloudCover;
        cloudColor += sunGlow;
    }

    if (u_moonDir.y > -0.10 && u_cloudCover > 0.05) {
        // Cool lunar silver halo
        float moonCorona = pow(moonDot, 4.5) * 0.30 + pow(moonDot, 28.0) * 0.45;
        vec3 moonColor = vec3(0.65, 0.75, 0.95);
        vec3 moonGlow = moonColor * moonCorona * mix(0.85, 0.40, u_stormIntensity) * u_cloudCover;
        cloudColor += moonGlow;
    }

    // Sun / Moon rim light scattering (silver lining)
    if (u_cloudCover > 0.10) {
        float celestialScatter = pow(sunDot, 6.0) * 0.4 + pow(moonDot, 4.0) * 0.25;
        vec3 rimLightColor = vec3(0.75, 0.70, 0.65) * celestialScatter * (1.0 - u_stormIntensity * 0.7);
        cloudColor += rimLightColor * smoothstep(0.3, 0.8, n1) * u_cloudCover;
    }

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
    float horizonBlend = smoothstep(0.0, 0.18, up);
    finalColor = mix(horizonSky, finalColor, horizonBlend);

    gl_FragColor = vec4(finalColor, 1.0);
}
