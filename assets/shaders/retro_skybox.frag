#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2 u_resolution;

// 4x4 Bayer matrix ordered dithering
float bayer4x4(vec2 coord) {
    int x = int(mod(coord.x, 4.0));
    int y = int(mod(coord.y, 4.0));
    int idx = x + y * 4;

    float m = 0.0;
    if (idx == 0)       m = 0.0;
    else if (idx == 1)  m = 8.0;
    else if (idx == 2)  m = 2.0;
    else if (idx == 3)  m = 10.0;
    else if (idx == 4)  m = 12.0;
    else if (idx == 5)  m = 4.0;
    else if (idx == 6)  m = 14.0;
    else if (idx == 7)  m = 6.0;
    else if (idx == 8)  m = 3.0;
    else if (idx == 9)  m = 11.0;
    else if (idx == 10) m = 1.0;
    else if (idx == 11) m = 9.0;
    else if (idx == 12) m = 15.0;
    else if (idx == 13) m = 7.0;
    else if (idx == 14) m = 13.0;
    else if (idx == 15) m = 5.0;

    return (m / 16.0) - 0.5;
}

void main() {
    vec4 texColor = texture2D(u_texture, v_texCoords);

    // Dither at retro pixel resolution
    vec2 pixelCoord = v_texCoords * u_resolution;
    float dither = bayer4x4(pixelCoord) * (1.0 / 8.0);

    vec3 dithered = texColor.rgb + vec3(dither);

    // Quantize color steps for authentic 90s retro aesthetic
    float levels = 10.0;
    vec3 quantized = floor(dithered * levels + 0.5) / levels;

    gl_FragColor = vec4(clamp(quantized, 0.0, 1.0), texColor.a) * v_color;
}
