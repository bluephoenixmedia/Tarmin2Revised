#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_texCoords;

uniform sampler2D u_diffuseTexture;
uniform float u_time;
uniform vec4 u_diffuseColor;
uniform float u_flashIntensity;

// Hash & Noise functions for organic flame turbulence
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

float fbm(vec2 p) {
    float v = 0.0;
    v += 0.5000 * noise(p); p = p * 2.02 + vec2(1.7, 9.2);
    v += 0.2500 * noise(p); p = p * 2.03 + vec2(8.3, 2.8);
    v += 0.1250 * noise(p);
    return v;
}

// Flame evaluation for one of the four spires
float computeTowerFlame(vec2 uv, float uCenter, float vBase, float vPeak, float tw, float towerIdx) {
    float t = u_time * 3.5 + towerIdx * 1.73;
    float yNorm = clamp((vBase - uv.y) / max(0.001, vBase - vPeak), 0.0, 1.5);
    float flameWidth = tw * (1.1 - 0.55 * clamp(yNorm, 0.0, 1.0));
    float yFlow = (vBase - uv.y) * 35.0 - t * 4.5;
    
    vec2 pts = vec2(uv.x * 50.0, yFlow * 0.4);
    float xDisplace = (fbm(pts) - 0.48) * flameWidth * 1.5;
    float dist = abs(uv.x - uCenter + xDisplace);
    
    float radial = 1.0 - clamp(dist / max(0.0001, flameWidth), 0.0, 1.0);
    float vertFalloff = 1.0 - smoothstep(0.70, 1.45, yNorm);
    
    vec2 tonguePts = vec2(dist * 80.0, yFlow * 0.8);
    float tongues = fbm(tonguePts);
    float flameShape = radial * (0.6 + 0.7 * tongues) * vertFalloff;
    
    float flicker = 0.88 + 0.12 * sin(t * 8.0) + 0.08 * cos(t * 13.7 + towerIdx * 2.1);
    return clamp(flameShape * flicker, 0.0, 1.5);
}

vec3 getFireColor(float intensity) {
    vec3 c_core = vec3(1.0, 1.0, 0.85);
    vec3 c_mid  = vec3(1.0, 0.75, 0.15);
    vec3 c_outer = vec3(1.0, 0.30, 0.03);
    vec3 c_rim  = vec3(0.70, 0.06, 0.01);

    if (intensity > 0.85) {
        float f = clamp((intensity - 0.85) / 0.65, 0.0, 1.0);
        return mix(c_mid, c_core, f);
    } else if (intensity > 0.40) {
        float f = (intensity - 0.40) / 0.45;
        return mix(c_outer, c_mid, f);
    } else {
        float f = clamp(intensity / 0.40, 0.0, 1.0);
        return mix(c_rim, c_outer, f);
    }
}

void main() {
    vec4 baseCol = texture2D(u_diffuseTexture, v_texCoords);
    vec2 uv = v_texCoords;

    // Evaluate 4 flame spires
    float intensity0 = computeTowerFlame(uv, 0.3797, 0.241, 0.080, 0.011, 0.0);
    float intensity1 = computeTowerFlame(uv, 0.4517, 0.172, 0.045, 0.015, 1.0);
    float intensity2 = computeTowerFlame(uv, 0.5469, 0.185, 0.030, 0.015, 2.0);
    float intensity3 = computeTowerFlame(uv, 0.6210, 0.190, 0.070, 0.011, 3.0);

    float maxIntensity = max(max(intensity0, intensity1), max(intensity2, intensity3));

    // Base lighting & lightning flash
    vec3 castleCol = baseCol.rgb * u_diffuseColor.rgb;
    if (u_flashIntensity > 0.05) {
        castleCol = mix(castleCol, vec3(1.4, 1.4, 1.5), u_flashIntensity);
    }

    if (maxIntensity > 0.05) {
        vec3 fireCol = getFireColor(maxIntensity);
        bool isSpriteFlame = (baseCol.a > 0.1) && (baseCol.r > 0.50) && (baseCol.g > 0.12) && (baseCol.b < 0.55);

        if (isSpriteFlame) {
            // Overlay animated fiery furnace and licking tongues directly over the sprite
            float blendAlpha = clamp(maxIntensity * 1.1, 0.0, 1.0);
            vec3 blended = mix(castleCol, fireCol, blendAlpha);
            blended += fireCol * clamp(maxIntensity - 0.5, 0.0, 1.0) * 0.6; // Incandescent core bloom
            gl_FragColor = vec4(blended, baseCol.a);
            return;
        } else if (baseCol.a < 0.05) {
            // In the air above and dancing around the torches
            if (maxIntensity > 0.25) {
                float airAlpha = clamp((maxIntensity - 0.25) / 0.35, 0.0, 0.95);
                gl_FragColor = vec4(fireCol, airAlpha);
                return;
            }
        }
    }

    // Normal billboard geometry
    if (baseCol.a < 0.02) {
        discard;
    }

    gl_FragColor = vec4(castleCol, baseCol.a);
}
