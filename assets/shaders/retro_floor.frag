#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform vec3 u_floorColor; // Solid color for the floor

uniform vec2 u_playerPos;
uniform vec2 u_dir;
uniform vec2 u_plane;
uniform float u_screenWidth;
uniform float u_screenHeight;
uniform float u_fogDist;
uniform vec3 u_fogColor;
uniform float u_lightIntensity;
uniform float u_fogEnabled; // 1.0 for true, 0.0 for false

void main() {
    // Current pixel coordinates
    // gl_FragCoord.y is 0 at bottom, increasing upwards.
    float y = gl_FragCoord.y;
    float horizon = u_screenHeight * 0.5;
    
    // If we are above the horizon, discard 
    if (y > horizon) {
        discard;
    }

    // Calculate row distance
    float p_down = horizon - y;
    if (p_down <= 0.0) p_down = 0.001; // Avoid divide by zero
    
    float posZ = 0.5 * u_screenHeight;
    float rowDistance = posZ / p_down;

    // Base color
    vec3 finalColor = u_floorColor;

    // Apply Fog
    if (u_fogEnabled > 0.5) {
        float FOG_FADE_RATIO = 0.5;
        float start = u_fogDist * (1.0 - FOG_FADE_RATIO);
        
        float fogAmount = (rowDistance - start) / (u_fogDist * FOG_FADE_RATIO);
        fogAmount = clamp(fogAmount, 0.0, 1.0);
        
        finalColor = mix(finalColor, u_fogColor, fogAmount);
    }

    // Apply Light Intensity + Distance Fade (Torch)
    // Consistent with MODERN shader logic
    float distBrightness = 1.0 - (rowDistance / 20.0);
    distBrightness = clamp(distBrightness, 0.2, 1.0);
    
    finalColor = finalColor * distBrightness * u_lightIntensity;

    gl_FragColor = vec4(finalColor, 1.0);
}
