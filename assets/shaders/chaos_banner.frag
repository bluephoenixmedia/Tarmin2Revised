#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_time;
uniform float u_chromaIntensity;

// The Laughing Void chromatic spectrum:
// Cyan:    #00FFFF (0.0, 1.0, 1.0)
// Violet:  #BF00FF (0.749, 0.0, 1.0)
// Magenta: #FF007F (1.0, 0.0, 0.498)

vec3 getChromatic(float t) {
    float phase = fract(t);
    vec3 c1 = vec3(0.0, 1.0, 1.0);
    vec3 c2 = vec3(0.749, 0.0, 1.0);
    vec3 c3 = vec3(1.0, 0.0, 0.498);

    if (phase < 0.33333) {
        return mix(c1, c2, phase * 3.0);
    } else if (phase < 0.66666) {
        return mix(c2, c3, (phase - 0.33333) * 3.0);
    } else {
        return mix(c3, c1, (phase - 0.66666) * 3.0);
    }
}

void main() {
    vec4 texColor = texture2D(u_texture, v_texCoords);
    
    // Horizontal sweep + subtle oscillating pulse
    float wave = v_texCoords.x * 2.0 - u_time * 0.75 + sin(u_time * 1.8 + v_texCoords.y * 4.0) * 0.15;
    vec3 chromatic = getChromatic(wave);

    // Modulate texture color with chromatic gradient
    float intensity = clamp(u_chromaIntensity, 0.0, 1.0);
    vec3 blended = mix(texColor.rgb, chromatic, intensity);

    gl_FragColor = vec4(blended * v_color.rgb, texColor.a * v_color.a);
}
