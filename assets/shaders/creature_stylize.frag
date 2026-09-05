#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_uv;

uniform sampler2D u_texture;
uniform vec2  u_resolution;
uniform float u_blockSize;

// ---------------------------------------------------------------------------
// Palette — 5 discrete tones, set per-creature from Java.
// Tone 0 = deepest shadow / hard outline
// Tone 4 = brightest highlight
// ---------------------------------------------------------------------------
uniform vec3 u_tone0;
uniform vec3 u_tone1;
uniform vec3 u_tone2;
uniform vec3 u_tone3;
uniform vec3 u_tone4;

// Luminance thresholds separating the 5 tones (must be ascending, all in [0,1]).
uniform vec4 u_thresholds;  // x=t01  y=t12  z=t23  w=t34

vec2 pixelSnap(vec2 uv) {
    vec2 artPixels = u_resolution / u_blockSize;
    return (floor(uv * artPixels) + 0.5) / artPixels;
}

vec3 paletteLookup(float lum) {
    if      (lum < u_thresholds.x) return u_tone0;
    else if (lum < u_thresholds.y) return u_tone1;
    else if (lum < u_thresholds.z) return u_tone2;
    else if (lum < u_thresholds.w) return u_tone3;
    else                           return u_tone4;
}

float alphaEdge(vec2 uv) {
    vec2 px = 1.0 / u_resolution;
    float tl = texture2D(u_texture, uv + vec2(-px.x,  px.y)).a;
    float tm = texture2D(u_texture, uv + vec2(  0.0,  px.y)).a;
    float tr = texture2D(u_texture, uv + vec2( px.x,  px.y)).a;
    float ml = texture2D(u_texture, uv + vec2(-px.x,   0.0)).a;
    float mr = texture2D(u_texture, uv + vec2( px.x,   0.0)).a;
    float bl = texture2D(u_texture, uv + vec2(-px.x, -px.y)).a;
    float bm = texture2D(u_texture, uv + vec2(  0.0, -px.y)).a;
    float br = texture2D(u_texture, uv + vec2( px.x, -px.y)).a;
    float gx = -tl + tr - 2.0*ml + 2.0*mr - bl + br;
    float gy = -tl - 2.0*tm - tr  + bl + 2.0*bm + br;
    return clamp(sqrt(gx*gx + gy*gy) * 2.5, 0.0, 1.0);
}

void main() {
    vec2 snappedUV = pixelSnap(v_uv);
    vec4 raw = texture2D(u_texture, snappedUV);

    if (raw.a < 0.05) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
        return;
    }

    float lum = dot(raw.rgb, vec3(0.299, 0.587, 0.114));
    vec3 paletteColor = paletteLookup(lum);
    float edge = alphaEdge(v_uv);
    vec3 finalColor = (edge > 0.5) ? u_tone0 : paletteColor;
    gl_FragColor = vec4(finalColor, 1.0);
}
