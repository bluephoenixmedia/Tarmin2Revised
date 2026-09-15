// Merchant Void chain laser: one camera-facing ribbon per beam, built on the CPU
// by LaserBeamRenderer and drawn additively in the world's transparent pass.

attribute vec3 a_position;
attribute vec4 a_color;      // rgb = beam tint, a = remaining life (1 = fresh, 0 = gone)
attribute vec2 a_texCoord0;  // x = distance along the beam in tiles, y = -1..1 across its width

uniform mat4 u_projViewTrans;

varying vec4 v_color;
varying vec2 v_uv;

void main() {
    v_color = a_color;
    v_uv = a_texCoord0;
    gl_Position = u_projViewTrans * vec4(a_position, 1.0);
}
