#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in float LineWidth;

out vec2 FragCoord;
flat out int QuadIndex;

layout(std140) uniform GlassOutlineParamsArray {
    vec4 params[3072];
};

void main() {
    int index = max(int(LineWidth + 0.5) - 1, 0);
    int base = index * 7;
    vec4 flagsDistortZSquirt = params[base + 4];

    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, flagsDistortZSquirt.z, 1.0);
    FragCoord = Color.rg;
    QuadIndex = index;
}
