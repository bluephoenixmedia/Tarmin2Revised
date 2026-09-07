attribute vec4 a_position;
attribute vec3 a_normal;
attribute vec2 a_texCoord0;

uniform mat4 u_projTrans;
uniform mat4 u_worldTrans;
uniform vec3 u_cameraPos;

varying vec3 v_worldPos;
varying vec3 v_dir;
varying vec2 v_texCoords;

void main() {
    vec4 worldPos = u_worldTrans * a_position;
    v_worldPos = worldPos.xyz;
    v_dir = normalize(worldPos.xyz - u_cameraPos);
    v_texCoords = a_texCoord0;
    gl_Position = u_projTrans * worldPos;
}
