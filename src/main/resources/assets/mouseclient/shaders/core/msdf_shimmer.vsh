#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in float LineWidth;

out vec2 TexCoord;
out vec4 VertexColor;
out float LinePos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    TexCoord    = UV0;
    VertexColor = Color;
    LinePos     = LineWidth;
}
