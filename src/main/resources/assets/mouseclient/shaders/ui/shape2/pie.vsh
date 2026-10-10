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
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
in float LineWidth;

out vec4 vertexColor;
out vec2 fanCoord;
out vec2 screenUv;
out float tintData;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    screenUv = gl_Position.xy * 0.5 + 0.5;
    vertexColor = Color;
    fanCoord = UV0;
    tintData = LineWidth;
}
