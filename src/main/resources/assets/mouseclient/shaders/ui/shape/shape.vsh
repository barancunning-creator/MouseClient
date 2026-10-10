#version 150

#moj_import <mouseclient:releon_common.glsl>

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in float LineWidth;

out vec2 FragCoord;
flat out int QuadIndex;

layout(std140) uniform ShapeParamsArray {
    vec4 params[1168];
};

void main() {
    int index = max(int(LineWidth + 0.5) - 1, 0);
    int base = index * 73;
    vec4 flagsDistortZ = params[base + 4];

    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, flagsDistortZ.z, 1.0);
    FragCoord = rvertexcoord(gl_VertexID);
    QuadIndex = index;
}
