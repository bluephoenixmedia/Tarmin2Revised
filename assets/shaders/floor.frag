#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;

uniform vec2 u_playerPos;
uniform vec2 u_dir;
uniform vec2 u_plane;
uniform float u_screenWidth;
uniform float u_screenHeight;
uniform float u_fogDist;
uniform vec3 u_fogColor;
uniform float u_lightIntensity;
uniform float u_fogEnabled; // 1.0 for true, 0.0 for false

// Dynamic Multi-Point Lights
uniform vec2 u_lightPos[4];
uniform vec3 u_lightColor[4];
uniform float u_lightRadius[4];
uniform float u_lightIntensityArr[4];
uniform int u_lightCount;
uniform vec3 u_ambientColor;

// Pseudo-random function
float random(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898,78.233))) * 43758.5453123);
}

void main() {
    float y = gl_FragCoord.y;
    float horizon = u_screenHeight * 0.5;
    
    if (y > horizon) {
        discard;
    }

    float p_down = horizon - y;
    if (p_down <= 0.0) p_down = 0.001; // Avoid divide by zero
    
    float posZ = 0.5 * u_screenHeight;
    float rowDistance = posZ / p_down;

    // Calculate ray direction for this pixel's X
    float cameraX = 2.0 * (gl_FragCoord.x / u_screenWidth) - 1.0;
    
    float rayDirX = u_dir.x + u_plane.x * cameraX;
    float rayDirY = u_dir.y + u_plane.y * cameraX;

    // Calculate floor position
    float floorX = u_playerPos.x + rowDistance * rayDirX;
    float floorY = u_playerPos.y + rowDistance * rayDirY;

    // --- Random Rotation Logic ---
    vec2 tilePos = floor(vec2(floorX, floorY));
    vec2 uv = fract(vec2(floorX, floorY));
    
    float rnd = random(tilePos);
    float orientation = floor(rnd * 4.0);
    
    if (orientation == 1.0) {
        uv = vec2(uv.y, 1.0 - uv.x);
    } else if (orientation == 2.0) {
        uv = vec2(1.0 - uv.x, 1.0 - uv.y);
    } else if (orientation == 3.0) {
        uv = vec2(1.0 - uv.y, uv.x);
    }

    // Sample texture
    vec4 texColor = texture2D(u_texture, uv);

    // --- Dynamic Multi-Light Accumulation ---
    vec3 lightAccum = u_ambientColor;
    vec2 worldPos = vec2(floorX, floorY);

    for (int i = 0; i < 4; i++) {
        if (i >= u_lightCount) break;
        float d = distance(worldPos, u_lightPos[i]);
        float r = u_lightRadius[i];
        if (d < r) {
            float norm = d / r;
            float atten = (1.0 - norm) * (1.0 - norm) * u_lightIntensityArr[i];
            lightAccum += u_lightColor[i] * atten;
        }
    }
    lightAccum = min(lightAccum, vec3(1.3)) * u_lightIntensity;

    vec3 finalColor = texColor.rgb * lightAccum;

    // Apply Fog
    if (u_fogEnabled > 0.5) {
        float FOG_FADE_RATIO = 0.5;
        float start = u_fogDist * (1.0 - FOG_FADE_RATIO);
        float fogAmount = (rowDistance - start) / (u_fogDist * FOG_FADE_RATIO);
        fogAmount = clamp(fogAmount, 0.0, 1.0);
        finalColor = mix(finalColor, u_fogColor, fogAmount);
    }

    gl_FragColor = vec4(finalColor, 1.0);
}
