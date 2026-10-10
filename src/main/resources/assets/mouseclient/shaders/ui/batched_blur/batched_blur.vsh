#version 150

#moj_import <mouseclient:releon_common.glsl>

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in float LineWidth;

out vec2 FragCoord;
out vec4 FragColor;
flat out int QuadIndex;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    FragCoord = rvertexcoord(gl_VertexID);
    FragColor = Color;
    QuadIndex = max(int(LineWidth + 0.5) - 1, 0);
}
