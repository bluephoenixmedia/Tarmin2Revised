#ifdef GL_ES
precision highp float;
#endif

attribute vec4 a_position;
attribute vec3 a_normal;
attribute vec4 a_color;
attribute vec2 a_texCoord0;

uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;

varying vec4 v_color;
varying vec2 v_texCoords;
varying vec3 v_worldPos;
varying vec3 v_normal;

void main() {
    vec4 worldPos = u_worldTrans * a_position;
    v_worldPos = worldPos.xyz;
    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);
    v_color = a_color;
    v_texCoords = a_texCoord0;
    gl_Position = u_projViewTrans * worldPos;
}
