#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in float LineWidth;

out vec2 TexCoord;
out vec4 VertexColor;
out vec2 LocalPos;
flat out int RectIndex;

layout(std140) uniform RectHalfIconParamsArray {
    vec4 params[768];
};

void main() {
    RectIndex = max(int(LineWidth + 0.5) - 1, 0);
    int base = RectIndex * 3;
    vec4 rect = params[base];

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    TexCoord = UV0;
    VertexColor = Color;
    LocalPos = Position.xy - rect.xy;
}
