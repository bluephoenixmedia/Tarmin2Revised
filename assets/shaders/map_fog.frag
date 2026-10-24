#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texBody;
uniform sampler2D u_texShade;
uniform sampler2D u_texHi;
uniform sampler2D u_texWisp;
uniform sampler2D u_mask;

uniform float u_time;
uniform vec2 u_camPos;
uniform float u_camZoom;
uniform float u_camRot;
uniform vec2 u_origin;
uniform float u_fogStrength;
uniform float u_isCave;
uniform vec2 u_resolution;

vec4 blendOver(vec4 under, vec4 over) {
    float outA = over.a + under.a * (1.0 - over.a);
    if (outA < 0.0001) {
        return vec4(0.0);
    }
    vec3 outRgb = (over.rgb * over.a + under.rgb * under.a * (1.0 - over.a)) / outA;
    return vec4(outRgb, outA);
}

void main() {
    float sx = v_texCoords.x * u_resolution.x;
    float sy = (1.0 - v_texCoords.y) * u_resolution.y;

    float s = u_camZoom;
    float sc = sqrt(s);
    float t = u_time;

    // 1. Base fog fill
    vec4 fog = (u_isCave > 0.5) 
        ? vec4(28.0 / 255.0, 32.0 / 255.0, 38.0 / 255.0, 0.84)
        : vec4(60.0 / 255.0, 70.0 / 255.0, 82.0 / 255.0, 0.60);

    // 2. Body noise layer
    float k1 = 2.4 * sc;
    vec2 o1 = vec2(u_origin.x * 0.85 + t * 7.0, u_origin.y * 0.85 + t * 2.5);
    vec2 uvBody = (vec2(sx, sy) - o1) / (k1 * 128.0);
    vec4 colBody = texture2D(u_texBody, uvBody);
    float a1 = colBody.a * ((u_isCave > 0.5) ? 0.6 : 1.0);
    fog = blendOver(fog, vec4(colBody.rgb, a1));

    // 3. Shade noise layer
    float k2 = 1.7 * sc;
    vec2 o2 = vec2(u_origin.x * 0.90 - t * 4.0, u_origin.y * 0.90 + t * 1.5);
    vec2 uvShade = (vec2(sx, sy) - o2) / (k2 * 128.0);
    vec4 colShade = texture2D(u_texShade, uvShade);
    float a2 = colShade.a * 0.90;
    fog = blendOver(fog, vec4(colShade.rgb, a2));

    // 4. Highlight noise layer
    float k3 = 1.9 * sc;
    vec2 o3 = vec2(u_origin.x * 0.95 + t * 3.0 + 12.0 * sin(t * 0.13), u_origin.y * 0.95 - t * 1.2);
    vec2 uvHi = (vec2(sx, sy) - o3) / (k3 * 128.0);
    vec4 colHi = texture2D(u_texHi, uvHi);
    float a3 = colHi.a * ((u_isCave > 0.5) ? 0.3 : 0.9);
    fog = blendOver(fog, vec4(colHi.rgb, a3));

    // 5. Unproject screen coord to world chunks and sample knowledge mask
    float hw = 28.0 * s;
    float hh = 14.0 * s;
    float cs = cos(u_camRot);
    float sn = sin(u_camRot);

    float X = sx - u_resolution.x * 0.5;
    float Y = sy - u_resolution.y * 0.5 + 5.0 * s; // zf = 5
    float a = X / hw;
    float b = Y / hh;
    float rx = (a + b) * 0.5;
    float ry = (b - a) * 0.5;

    float wx = u_camPos.x + rx * cs + ry * sn;
    float wy = u_camPos.y - rx * sn + ry * cs;

    // Mask UV mapping: PAD = 7, N = 10, total chunks = 24
    float maskU = (wx + 7.0) / 24.0;
    float maskV = (wy + 7.0) / 24.0;

    float maskVal = 0.0;
    float frontVal = 0.0;
    if (maskU >= 0.0 && maskU <= 1.0 && maskV >= 0.0 && maskV <= 1.0) {
        vec4 maskSample = texture2D(u_mask, vec2(maskU, maskV));
        maskVal = maskSample.r;
        frontVal = maskSample.g;
    }

    // Clear holes over visited/glimpsed chunks
    fog.a *= (1.0 - maskVal);

    // 6. Frontier wisps layer
    float kw = 1.3 * sc;
    vec2 ow = vec2(u_origin.x + t * 10.0 + 18.0 * cos(t * 0.21), u_origin.y + 10.0 * sin(t * 0.17) - t * 2.0);
    vec2 uvWisp = (vec2(sx, sy) - ow) / (kw * 128.0);
    vec4 colWisp = texture2D(u_texWisp, uvWisp);

    float wispA = colWisp.a * frontVal * ((u_isCave > 0.5) ? 0.35 : 0.80);
    fog = blendOver(fog, vec4(colWisp.rgb, wispA));

    // 7. Global fog strength multiplier
    fog.a *= (0.3 + 0.7 * u_fogStrength);

    gl_FragColor = fog * v_color;
}
