#version 150

#moj_import <mouseclient:releon_common.glsl>

in vec3 Position;

out vec2 FragCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);

    vec2 uv = rvertexcoord(gl_VertexID);
    FragCoord = vec2(uv.x, 1.0 - uv.y);
}
