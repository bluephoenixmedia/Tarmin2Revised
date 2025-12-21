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

void main() {
    // Current pixel coordinates
    // gl_FragCoord.y is 0 at bottom, increasing upwards.
    // In our Java loop, y went from height/2 down to 0. 
    // So we are rendering the bottom half of the screen.
    
    float y = gl_FragCoord.y;
    float horizon = u_screenHeight * 0.5;
    
    // If we are above the horizon, discard (or handle skybox elsewhere)
    if (y > horizon) {
        discard;
    }

    // Calculate row distance
    // Matches: int p_down = (int) (viewport.getWorldHeight() / 2) - y;
    //          float posZ = 0.5f * viewport.getWorldHeight();
    //          float rowDistance = posZ / p_down;
    
    float p_down = horizon - y;
    if (p_down <= 0.0) p_down = 0.001; // Avoid divide by zero
    
    float posZ = 0.5 * u_screenHeight;
    float rowDistance = posZ / p_down;

    // Calculate ray direction for this pixel's X
    // cameraX goes from -1 (left) to 1 (right)
    float cameraX = 2.0 * (gl_FragCoord.x / u_screenWidth) - 1.0;
    
    float rayDirX = u_dir.x + u_plane.x * cameraX;
    float rayDirY = u_dir.y + u_plane.y * cameraX;

    // Calculate floor position
    float floorX = u_playerPos.x + rowDistance * rayDirX;
    float floorY = u_playerPos.y + rowDistance * rayDirY;

    // Texture mapping (wrapping)
    vec2 texCoord = vec2(floorX, floorY);
    
    // Sample texture
    vec4 texColor = texture2D(u_texture, texCoord);

    // Apply Fog
    if (u_fogEnabled > 0.5) {
        // float fogAmount = Math.max(0, Math.min(1f, (rowDistance - (fogDistance * (1f - FOG_FADE_RATIO))) / (fogDistance * FOG_FADE_RATIO)));
        float FOG_FADE_RATIO = 0.5;
        float start = u_fogDist * (1.0 - FOG_FADE_RATIO);
        float end = u_fogDist; // The calculation in Java slightly differs, adapting approximation
        
        // Java: (rowDistance - (fogDistance * 0.5)) / (fogDistance * 0.5)
        float fogAmount = (rowDistance - start) / (u_fogDist * FOG_FADE_RATIO);
        fogAmount = clamp(fogAmount, 0.0, 1.0);
        
        texColor.rgb = mix(texColor.rgb, u_fogColor, fogAmount);
    }

    // Apply Light Intensity + Distance Fade
    // float brightness = Math.max(0.2f, Math.min(1.0f, 1.0f - rowDistance / 20.0f));
    float distBrightness = 1.0 - (rowDistance / 20.0);
    distBrightness = clamp(distBrightness, 0.2, 1.0);
    
    vec3 finalColor = texColor.rgb * distBrightness * u_lightIntensity;

    gl_FragColor = vec4(finalColor, 1.0);
}
