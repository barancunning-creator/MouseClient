#version 150

#moj_import <mouseclient:releon_common.glsl>

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in float LineWidth;

out vec2 FragCoord;
flat out int QuadIndex;

layout(std140) uniform GlowParamsArray {
    vec4 params[1120];
};

void main() {
    int index = max(int(LineWidth + 0.5) - 1, 0);
    int base = index * 5;
    float z = params[base + 2].z;

    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, z, 1.0);
    FragCoord = rvertexcoord(gl_VertexID);
    QuadIndex = index;
}
