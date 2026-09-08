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

// Fog uniforms
uniform float u_fogEnabled;
uniform float u_fogDistance;
uniform vec3 u_fogColor;

// Ambient & Doom
uniform vec3 u_ambientColor;
uniform float u_doomFactor;

// Directional celestial lighting (Sun / Moon)
uniform vec3 u_dirLightDir;
uniform vec3 u_dirLightColor;

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
    if (u_alphaCutoff > 0.0 && texColor.a < u_alphaCutoff) {
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

        // Ambient contribution scaled by Tarmin Doom
        vec3 accumulatedLight = u_ambientColor * u_doomFactor;

        // Directional celestial light (Sun/Moon outdoors, modulated by doom and surface normal)
        if (length(u_dirLightColor) > 0.001) {
            float nDotSun = max(dot(v_normal, u_dirLightDir), 0.0);
            accumulatedLight += u_dirLightColor * nDotSun * u_doomFactor;
        }

        // Dynamic point light iteration
        int count = min(u_numLights, MAX_LIGHTS);
        for (int i = 0; i < MAX_LIGHTS; i++) {
            if (i >= count) break;

            vec3 toLight = u_lightPos[i] - v_worldPos;
            float dist = length(toLight);
            float radius = u_lightRadius[i];

            if (dist < radius && radius > 0.001) {
                float normDist = dist / radius;
                float atten = clamp(1.0 - normDist, 0.0, 1.0);
                atten = atten * atten; // Smooth quadratic falloff

                // Normal dot light with soft wrap for dungeon ambient bounce
                vec3 lightDir = toLight / dist;
                float nDotL = max(dot(v_normal, lightDir), 0.2);

                accumulatedLight += u_lightColor[i] * (atten * nDotL * u_lightIntensity[i]);
            }
        }

        // Clamp maximum light brightness
        accumulatedLight = min(accumulatedLight, vec3(1.5));
        finalColor = vec4(baseColor.rgb * accumulatedLight, baseColor.a);
    }

    // Distance Fog
    if (u_fogEnabled > 0.5 && u_fogDistance > 0.001) {
        float camDist = length(v_worldPos - u_cameraPos);
        float fogFactor = clamp(camDist / u_fogDistance, 0.0, 1.0);
        // Exponential feel
        fogFactor = fogFactor * fogFactor;
        finalColor.rgb = mix(finalColor.rgb, u_fogColor, fogFactor);
    }

    gl_FragColor = finalColor;
}
