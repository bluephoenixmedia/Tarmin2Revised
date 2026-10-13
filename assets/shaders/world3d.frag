#ifdef GL_ES
precision highp float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;
varying vec3 v_worldPos;
varying vec3 v_normal;

uniform sampler2D u_diffuseTexture;
uniform vec3 u_cameraPos;

// Mode switches
uniform int u_retroMode;           // 1 = RETRO unlit mode, 0 = MODERN lit mode
uniform vec4 u_retroColor;          // Flat base color for RETRO mode
uniform float u_retroBorder;        // 1.0 = draw border outline, 0.0 = none
uniform float u_alphaCutoff;        // 0.1 for alpha test discard, 0.0 for opaque
uniform float u_vertexCoverage;     // 1.0 = vertex alpha scales the cutoff test (forest canopy), 0.0 = ignore it
uniform float u_unlit;              // 1.0 = draws at its own colour, ignoring scene light (glowing props, light shafts)

// Fog uniforms
uniform float u_fogEnabled;
uniform float u_fogDistance;
uniform vec3 u_fogColor;

// Ambient & Doom
uniform vec3 u_ambientColor;
uniform float u_doomFactor;

// Volcanic sky bounce: colour and strength of the light the burning sky throws down onto
// upward-facing surfaces. Zero indoors and underground, where there is no sky to bounce.
uniform vec3 u_skyRimColor;
uniform float u_skyRimStrength;

// Directional celestial lighting (Sun / Moon)
uniform vec3 u_dirLightDir;
uniform vec3 u_dirLightColor;

// Dynamic surface weather modulation
uniform float u_wetness;           // 0.0 to 1.0: stone darkening and specular sheen
uniform float u_snowAccumulation;  // 0.0 to 1.0: snow/frost dusting on horizontal stone

// 1D Radial Lantern Shadow Map
uniform sampler2D u_shadowMap1D;
uniform float u_shadowMapEnabled;

// Relief & Bump Mapping
uniform float u_bumpStrength;

// Surface clouds and time
uniform float u_time;
uniform float u_cloudShadows;

// Multi-point dynamic lights (up to 16)
const int MAX_LIGHTS = 16;
uniform int u_numLights;
uniform vec3 u_lightPos[16];
uniform vec3 u_lightColor[16];
uniform float u_lightRadius[16];
uniform float u_lightIntensity[16];

void main() {
    vec4 texColor = texture2D(u_diffuseTexture, v_texCoords);

    // Alpha cutout discard
    // The forest canopy carries per-vertex coverage in v_color.a: fronds thin out
    // and drop away where coverage falls, which is what opens the seam over a trail.
    float cutoutAlpha = texColor.a * mix(1.0, v_color.a, u_vertexCoverage);
    if (u_alphaCutoff > 0.0 && cutoutAlpha < u_alphaCutoff) {
        discard;
    }

    vec4 finalColor;

    if (u_retroMode == 1) {
        // --- RETRO MODE ---
        vec4 base = u_retroColor * v_color;
        
        // Crisp geometric border along quad UV edges
        if (u_retroBorder > 0.5) {
            vec2 borderUV = fract(v_texCoords);
            float edgeDist = min(min(borderUV.x, 1.0 - borderUV.x), min(borderUV.y, 1.0 - borderUV.y));
            if (edgeDist < 0.02) {
                base.rgb *= 0.15; // Dark contrasting border edge
            }
        }
        finalColor = base;
    } else {
        // --- MODERN MODE ---
        vec4 baseColor = texColor * v_color;

        // 0. In-Shader Sobel Bump Mapping for stone relief & mortar crevices
        vec3 normal = v_normal;
        if (u_bumpStrength > 0.001) {
            vec2 texStep = vec2(1.0 / 256.0, 1.0 / 256.0);
            float lumCenter = dot(texColor.rgb, vec3(0.299, 0.587, 0.114));
            float lumR = dot(texture2D(u_diffuseTexture, v_texCoords + vec2(texStep.x, 0.0)).rgb, vec3(0.299, 0.587, 0.114));
            float lumU = dot(texture2D(u_diffuseTexture, v_texCoords + vec2(0.0, texStep.y)).rgb, vec3(0.299, 0.587, 0.114));
            float dX = (lumR - lumCenter) * u_bumpStrength;
            float dY = (lumU - lumCenter) * u_bumpStrength;

            // Generate tangent frame according to surface orientation
            vec3 tangent = (abs(v_normal.y) > 0.7) ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
            vec3 bitangent = normalize(cross(v_normal, tangent));
            tangent = cross(bitangent, v_normal);
            normal = normalize(v_normal - tangent * dX - bitangent * dY);
        }

        // 1. Wetness effect (puddle pooling in mortar crevices and porous stone darkening)
        float wetnessFactor = u_wetness;
        if (wetnessFactor > 0.01) {
            float lum = dot(texColor.rgb, vec3(0.299, 0.587, 0.114));
            // In stone textures, crevices and mortar are darker than the stone faces
            float mortarMask = smoothstep(0.55, 0.15, lum);
            // Puddle accumulates heavily in mortar depressions
            float puddle = clamp(wetnessFactor * (0.35 + 0.65 * mortarMask), 0.0, 1.0);
            // Porous darkening: water soaks in and darkens albedo
            baseColor.rgb *= mix(1.0, 0.52, puddle);
        }

        // 2. Snow / Frost accumulation dusting on horizontal upward-facing stone
        if (u_snowAccumulation > 0.01) {
            float upwardSlope = smoothstep(0.55, 0.90, normal.y);
            float snowFactor = u_snowAccumulation * upwardSlope;
            vec3 snowTone = vec3(0.92, 0.95, 0.98);
            baseColor.rgb = mix(baseColor.rgb, snowTone, snowFactor * 0.85);
        }

        // 3. Hemispheric ambient bounce: sky from above, warm earth from below
        float hemiFactor = normal.y * 0.5 + 0.5;
        vec3 groundBounce = u_ambientColor * 0.45;
        vec3 hemiAmbient = mix(groundBounce, u_ambientColor, hemiFactor);
        vec3 accumulatedLight = hemiAmbient * u_doomFactor;

        // Cloud shadow drift outdoors
        if (u_cloudShadows > 0.001) {
            float cloudFBM = sin(v_worldPos.x * 0.07 + u_time * 0.04) * cos(v_worldPos.z * 0.07 + u_time * 0.03);
            float cloudAtten = smoothstep(-0.4, 0.7, cloudFBM);
            accumulatedLight *= mix(0.72, 1.0, cloudAtten);
        }

        vec3 viewDir = normalize(u_cameraPos - v_worldPos);
        float cosTheta = max(dot(normal, viewDir), 0.0);
        float fresnel = 0.04 + 0.96 * pow(1.0 - cosTheta, 5.0);

        // Directional celestial light (Sun/Moon outdoors, modulated by doom and surface normal)
        if (length(u_dirLightColor) > 0.001) {
            float nDotSun = max(dot(normal, u_dirLightDir), 0.0);
            accumulatedLight += u_dirLightColor * nDotSun * u_doomFactor;

            // Wetness specular highlight from sun / moon reflection
            if (wetnessFactor > 0.05) {
                vec3 specNormal = normalize(mix(v_normal, normal, 0.30));
                vec3 halfDir = normalize(u_dirLightDir + viewDir);
                float specPower = mix(24.0, 64.0, wetnessFactor);
                float spec = pow(max(dot(specNormal, halfDir), 0.0), specPower);
                accumulatedLight += u_dirLightColor * (spec * 0.95 * fresnel * wetnessFactor);
            }
        }

        // Dynamic point light iteration
        int count = min(u_numLights, MAX_LIGHTS);
        for (int i = 0; i < MAX_LIGHTS; i++) {
            if (i >= count) break;

            vec3 toLight = u_lightPos[i] - v_worldPos;
            float dist = length(toLight);
            float radius = u_lightRadius[i];

            if (dist < radius && radius > 0.001) {
                float normDist = clamp(dist / radius, 0.0, 1.0);
                float taper = 1.0 - normDist;
                taper = taper * taper; // Smooth outer edge falloff

                // Physical inverse-square attenuation with intense hot core near the flame
                float atten = (1.0 / (1.0 + 1.5 * dist * dist)) * taper * 2.2;

                vec3 lightDir = toLight / dist;
                float nDotL = max(dot(normal, lightDir), 0.10);

                float lightModifier = 1.0;

                // Light 0 is the carried lantern / player light
                if (i == 0) {
                    // 1D Radial Shadow Buffer Occlusion
                    if (u_shadowMapEnabled > 0.5) {
                        vec2 toFragMaze = vec2(v_worldPos.x - u_lightPos[0].x, -(v_worldPos.z - u_lightPos[0].z));
                        float fragDist = length(toFragMaze);
                        float angle = atan(toFragMaze.y, toFragMaze.x); // [-PI, PI]
                        float uCenter = (angle + 3.14159265) / 6.2831853;

                        // 5-tap PCF filtering across angular samples
                        float pcfStep = 1.0 / 256.0;
                        float shadowAccum = 0.0;
                        for (int s = -2; s <= 2; s++) {
                            float sampleU = fract(uCenter + float(s) * pcfStep);
                            vec4 shadowTex = texture2D(u_shadowMap1D, vec2(sampleU, 0.5));
                            float occludedDist = (shadowTex.r * 0.99609375 + shadowTex.g * 0.00389099) * 16.0;
                            if (fragDist <= occludedDist + 0.22) {
                                shadowAccum += 1.0;
                            }
                        }
                        float shadowFactor = shadowAccum / 5.0;
                        float footProximity = clamp(1.0 - dist / 1.2, 0.0, 1.0);
                        lightModifier = max(shadowFactor, footProximity * 0.12);
                    }
                }

                if (lightModifier > 0.005) {
                    accumulatedLight += u_lightColor[i] * (atten * nDotL * u_lightIntensity[i] * lightModifier);

                    // Specular highlight: only on wet flagstones/puddles, smoothed to prevent pixel-dither static
                    if (wetnessFactor > 0.05) {
                        vec3 specNormal = normalize(mix(v_normal, normal, 0.30));
                        vec3 halfDir = normalize(lightDir + viewDir);
                        float specPower = mix(24.0, 64.0, wetnessFactor);
                        float spec = pow(max(dot(specNormal, halfDir), 0.0), specPower);
                        float specIntensity = 1.10 * fresnel * wetnessFactor;
                        accumulatedLight += u_lightColor[i] * (spec * specIntensity * atten * u_lightIntensity[i] * lightModifier);
                    }
                }
            }
        }

        // 4. Volumetric Light Raymarching (Air Scatter & Shafts from Carried Lantern)
        // Fixed-step continuous raymarch without stochastic noise
        if (u_shadowMapEnabled > 0.5 && count > 0) {
            vec3 rayVec = v_worldPos - u_cameraPos;
            float rayDist = length(rayVec);
            if (rayDist > 0.01) {
                vec3 rayDir = rayVec / rayDist;
                float marchDist = min(rayDist, 9.0);
                const int V_STEPS = 12;
                float stepSize = marchDist / float(V_STEPS);

                // Fixed half-step offset for smooth, noise-free air volume
                vec3 curPos = u_cameraPos + rayDir * (stepSize * 0.5);

                vec3 volAccum = vec3(0.0);
                vec3 light0 = u_lightPos[0];

                for (int v = 0; v < V_STEPS; v++) {
                    vec3 toL0 = light0 - curPos;
                    float dL0 = length(toL0);
                    if (dL0 < 8.5) {
                        // Check 1D shadow buffer at this position in the air
                        vec2 airMaze = vec2(curPos.x - light0.x, -(curPos.z - light0.z));
                        float airDist = length(airMaze);
                        float airAngle = atan(airMaze.y, airMaze.x);
                        float airU = fract((airAngle + 3.14159265) / 6.2831853);

                        vec4 sTex = texture2D(u_shadowMap1D, vec2(airU, 0.5));
                        float occD = (sTex.r * 0.99609375 + sTex.g * 0.00389099) * 16.0;

                        // Continuous shadow fade: eliminates binary threshold stepping noise
                        float shadowAtten = smoothstep(occD + 0.35, occD + 0.15, airDist);

                        if (shadowAtten > 0.01) {
                            float airTaper = clamp(1.0 - dL0 / 8.5, 0.0, 1.0);
                            float airAtten = (1.0 / (1.0 + 1.4 * dL0 * dL0)) * airTaper * airTaper;

                            // Forward Mie scattering phase function
                            float cosScatter = dot(rayDir, -normalize(toL0));
                            float mie = 0.5 * (1.0 + cosScatter * 0.70);

                            volAccum += u_lightColor[0] * (airAtten * mie * u_lightIntensity[0] * shadowAtten);
                        }
                    }
                    curPos += rayDir * stepSize;
                }

                float airDustDensity = 0.038;
                accumulatedLight += volAccum * (stepSize * airDustDensity);
            }
        }

        // Clamp maximum light brightness
        accumulatedLight = min(accumulatedLight, vec3(3.2));
        finalColor = vec4(baseColor.rgb * accumulatedLight, baseColor.a);

        // Upward-facing surfaces catch the burning sky. Multiplying through baseColor tints the
        // surface rather than washing it out, so materials keep their identity under the glow.
        if (u_skyRimStrength > 0.001) {
            float skyFacing = max(normal.y, 0.0);
            finalColor.rgb += baseColor.rgb * u_skyRimColor * (skyFacing * u_skyRimStrength);
        }

        // Something that makes its own light is not darkened by the scene's. Fog still applies.
        if (u_unlit > 0.5) {
            finalColor = baseColor;
        }
    }

    // Distance Fog
    if (u_fogEnabled > 0.5 && u_fogDistance > 0.001) {
        float camDist = length(v_worldPos - u_cameraPos);
        float fogFactor = clamp(camDist / u_fogDistance, 0.0, 1.0);
        // Exponential feel
        fogFactor = fogFactor * fogFactor;
        // What glows by its own light burns through haze: molten fire, glowing props, light shafts.
        // Under the Blight's short pale fog a chasm of fire otherwise washed out to lavender.
        if (u_unlit > 0.5) fogFactor *= 0.35;
        finalColor.rgb = mix(finalColor.rgb, u_fogColor, fogFactor);
    }

    gl_FragColor = finalColor;
}
