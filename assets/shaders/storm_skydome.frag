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
// 0 = expedition start, 1 = fully doomed. The primary driver of how bad the sky looks.
uniform float u_doom;
// Colour of the top of the vault: kept dark so the fire stays a horizon band, not a flood.
uniform vec3 u_zenithColor;
// Tarmin's Knell (plan K5): seconds since the gong (negative when silent), and the castle's
// bearing on the dome, in world x/z.
uniform float u_knellAge;
uniform vec2 u_knellDir;

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

// 3-octave and 5-octave Fractal Brownian Motion for high-performance domain-warped clouds
float fbm3(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(0.87758, 0.47942, -0.47942, 0.87758);
    for (int i = 0; i < 3; ++i) {
        v += a * noise(p);
        p = rot * p * 2.02 + vec2(12.3, 24.7);
        a *= 0.5;
    }
    return v;
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(0.87758, 0.47942, -0.47942, 0.87758);
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
    float domeProjection = 1.0 / (up + 0.20);
    vec2 skyUV = v_dir.xz * domeProjection * 0.40;
    
    // Wind drift vectors (Layer 1 slow billowing overcast, Layer 2 fast scud)
    float speed = u_windSpeed * (1.0 + u_stormIntensity * 1.5);
    vec2 windUpper = vec2(0.012, 0.007) * u_time * speed;
    vec2 windLower = vec2(0.038, 0.020) * u_time * speed;

    // Smoke is geology, not weather: CLEAR now means "no precipitation", not "empty sky".
    float cover = clamp(max(u_cloudCover, u_smokeFloor), 0.0, 1.0);

    // --- BEARING ---
    // Castle Tarmin stands due north (world -Z) and the volcano burns behind it. Concentrating
    // the fire on that bearing turns the sky into a compass: you navigate toward the apocalypse.
    vec2 flatDir = v_dir.xz;
    float flatLen = max(length(flatDir), 0.0001);
    float northness = -flatDir.y / flatLen; // flatDir.y is v_dir.z; -Z is north
    float northWeight = mix(0.20, 1.0, smoothstep(-0.35, 0.95, northness));

    // --- 1. BASE TERRIFYING & MENACING SKY VAULT ---
    // Inky, suffocating dark black-plum at zenith; ominous dark blood-crimson at horizon
    vec3 zenithSky = mix(vec3(0.035, 0.010, 0.045), vec3(0.065, 0.012, 0.035), u_doom);
    vec3 horizonBlood = vec3(0.42, 0.045, 0.015); // Deep, ominous dark blood-red
    vec3 skyBase = mix(horizonBlood, zenithSky, pow(up, 0.35));

    // Volcanic horizon fire: tight, intense band right along the horizon ridge behind Castle Tarmin
    // Sharp exponential decay keeps the fire strictly behind the mountains/castle without washing out the sky
    float horizonGlow = exp(-up * 9.0);
    float plumePulse = 0.90 + 0.10 * sin(u_time * 0.50 + northness * 1.6);
    vec3 volcanicEmber = mix(vec3(0.55, 0.05, 0.015), vec3(0.85, 0.16, 0.025), horizonGlow);
    skyBase += volcanicEmber * (horizonGlow * 1.25) * plumePulse * northWeight;

    // --- 1D. ERUPTION PLUMES ---
    // Columns of fire and smoke rising off the northern ridge. Procedural rather than modelled:
    // these have to churn and rise continuously, which sprite or mesh plumes do badly.
    float plumeBand = smoothstep(0.48 - 0.28 * u_doom, 1.0, northness);
    if (plumeBand > 0.01) {
        float across = flatDir.x / flatLen;
        float colNoise = fbm(vec2(across * 4.5, up * 2.2 - u_time * 0.14));
        float column = smoothstep(0.42, 0.86, colNoise);
        float rise = exp(-up * 3.5);
        skyBase += vec3(0.85, 0.18, 0.03) * column * rise * plumeBand * (0.85 + 0.85 * u_doom);
    }

    // --- 1C. CELESTIAL SMEAR ---
    // Under a permanent smoke ceiling the sun and moon never resolve into discs. They remain a
    // diffuse stain that still tracks across the sky, so the hour stays readable without
    // contradicting the choked-sky premise.
    float sunDot = max(dot(v_dir, u_sunDir), 0.0);
    float moonDot = max(dot(v_dir, u_moonDir), 0.0);
    float smokeOcclusion = 0.45;
    if (u_sunDir.y > -0.15) {
        skyBase += vec3(0.85, 0.22, 0.05) * pow(sunDot, 2.8) * 0.30 * smokeOcclusion;
    }
    if (u_moonDir.y > -0.15) {
        skyBase += vec3(0.35, 0.15, 0.30) * pow(moonDot, 3.2) * 0.12 * smokeOcclusion;
    }

    // --- 2. HEAVY BILLOWING CUMULONIMBUS STORM CLOUDS (Menacing Canopy) ---
    // Domain warp creates organic curling plumes and cauliflower billows
    vec2 p1 = skyUV * 0.65 + windUpper;
    vec2 warp = vec2(fbm3(p1), fbm3(p1 + vec2(5.2, 1.3)));
    float n1 = fbm(p1 + 1.20 * warp);

    // Billow contouring: produces rounded 3D cloud lobes rather than flat noise
    float billow = 1.0 - abs(n1 * 2.0 - 1.0);
    float cloudDensity = smoothstep(0.12, 0.62, n1 + billow * 0.35);

    // Scud layer for high-speed wind turbulence
    vec2 p2 = skyUV * 1.40 + windLower;
    float scud = smoothstep(0.38, 0.75, fbm3(p2)) * 0.40;

    // The sky is heavily overcast with towering, menacing storm clouds across the vault
    float cloudCoverage = clamp(cloudDensity * 0.85 + scud * 0.35 + 0.15, 0.0, 1.0);

    // --- 3. INTIMIDATING & TERRIFYING CLOUD COLOR PALETTE ---
    // Heavy soot, dark charcoal-plum, and smoldering blood-red backlit underbellies
    vec3 cloudCore    = vec3(0.035, 0.010, 0.045); // Suffocating inky soot / black-plum core
    vec3 cloudBody    = vec3(0.10, 0.025, 0.12);   // Menacing dark charcoal-violet body
    vec3 cloudFold    = vec3(0.20, 0.040, 0.18);   // Bruised dark wine in internal folds
    vec3 cloudFireRim = vec3(0.70, 0.120, 0.02);   // Smoldering blood-orange volcanic backlight rim

    // Shading the cloud lobe:
    vec3 cloudColor = mix(cloudCore, cloudBody, smoothstep(0.18, 0.62, n1));
    float internalGlow = smoothstep(0.30, 0.80, billow);
    cloudColor = mix(cloudColor, cloudFold, internalGlow * 0.40);

    // Downward slope detection: lower rims of cloud lobes catch the volcanic fire from below
    float dBelow = n1 - fbm3(p1 + 1.20 * warp + vec2(0.0, 0.05));
    float underRim = smoothstep(0.0, 0.22, dBelow);

    // Fiery backlight on cloud underbellies facing the horizon inferno
    float fireUnderlight = exp(-up * 4.5) * (0.65 + 0.65 * underRim);
    cloudColor = mix(cloudColor, cloudFireRim, clamp(fireUnderlight * northWeight, 0.0, 0.85));

    // --- 4. LIGHTNING ILLUMINATION BURST ---
    if (u_flashIntensity > 0.02) {
        // Internal cloud volume scattering: backlit cloud lobes glow bright electric violet-white
        vec3 flashColor = vec3(0.85, 0.88, 1.0) * (u_flashIntensity * 1.5);
        float flashScattering = smoothstep(0.1, 0.7, n1) * 0.9 + 0.1;
        cloudColor = mix(cloudColor, flashColor, flashScattering * u_flashIntensity);
        skyBase = mix(skyBase, vec3(0.75, 0.78, 0.95), u_flashIntensity * 0.70);
    }

    // Blend clouds over atmospheric sky background (heavily dominated by dark storm clouds)
    vec3 finalColor = mix(skyBase, cloudColor, cloudCoverage * 0.95);

    // Smooth horizon blending: fade seamlessly into horizon atmospheric fog
    float horizonBlend = smoothstep(0.0, 0.04, up);
    finalColor = mix(horizonBlood, finalColor, horizonBlend);

    // --- 5. EMBER DRIFT ---
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

    // --- 6. DOOM HEAT-LIGHTNING ---
    // Weather owns storm lightning. This is the sky flickering with its own light, so a doomed
    // sky stays restless even in clear weather.
    if (u_doom > 0.05) {
        float tick = hash21(vec2(floor(u_time * 2.0), 3.0));
        float doomFlash = step(1.0 - 0.10 * u_doom, tick);
        finalColor += vec3(1.0, 0.45, 0.20) * doomFlash * u_doom * 0.40;
    }

    // --- 7. TARMIN'S KNELL ---
    // As the gong strikes, cloud-to-cloud lightning ripples out from the castle's bearing and
    // sweeps the whole sky in a breath: a crackling front of branching bolts, and behind it the
    // clouds left glowing, cooling from white to his red as the bell dies away.
    if (u_knellAge >= 0.0) {
        vec3 dir = normalize(v_dir);
        vec3 castle = normalize(vec3(u_knellDir.x, 0.12, u_knellDir.y));
        float theta = acos(clamp(dot(dir, castle), -1.0, 1.0));
        float front = u_knellAge * 2.4;
        float echo = (u_knellAge - 0.3) * 2.4;
        float band = exp(-pow((theta - front) / 0.16, 2.0)) + 0.55 * exp(-pow((theta - echo) / 0.13, 2.0)) * step(0.0, echo);
        float ridge = 1.0 - abs(2.0 * fbm(skyUV * 5.0 + vec2(u_knellAge * 0.7, 0.0)) - 1.0);
        float bolts = pow(ridge, 7.0);
        float flicker = 0.65 + 0.35 * hash21(vec2(floor(u_time * 28.0), theta * 13.0));
        float fade = 1.0 - smoothstep(1.4, 3.2, u_knellAge);
        float behind = step(theta, front) * exp(-(front - theta) * 0.7);
        vec3 strike = vec3(1.0, 0.86, 0.90) * band * (0.30 + bolts * 2.2) * flicker;
        vec3 glow = mix(vec3(1.0, 0.75, 0.70), vec3(0.85, 0.12, 0.08), smoothstep(0.0, 0.9, front - theta))
                * behind * (0.30 + 0.25 * smoothstep(0.35, 0.85, ridge));
        finalColor += (strike + glow) * fade * (0.35 + 0.65 * cloudCoverage) * (1.0 - below);
    }

    // --- 8. SUB-PERCEPTUAL DITHER (NO QUANTIZATION BANDING) ---
    // High-precision sub-least-significant-bit dither eliminates 8-bit monitor gradient banding,
    // ensuring silky smooth continuous-tone transitions across the entire celestial vault.
    float dither = (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    finalColor += dither;

    gl_FragColor = vec4(finalColor, 1.0);
}
