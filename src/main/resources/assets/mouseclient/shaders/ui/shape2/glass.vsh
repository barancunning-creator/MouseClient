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
in vec4 Color;      // rgb = цвет-примесь стекла (tint), a = альфа панели (fade)
in vec2 UV0;        // локальная координата квада 0..1
in ivec2 UV1;       // x = радиус угла (знак = squircle), y = доля примеси tint * PARAM_SCALE
in ivec2 UV2;       // x = суммарная сила тени * PARAM_SCALE
in vec3 Normal;     // rgb цвета тени (нормализован в [0,1])
in float LineWidth; // spread тени в пикселях устройства

out vec4 tintColor;
out vec2 quadCoord;
out float radiusData;
out float tintAmount;
out vec3 shadowColor;
out float shadowStrength;
out float spreadPx;
out vec2 screenUv;

const float RADIUS_SCALE = 16.0;
const float PARAM_SCALE = 8192.0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    screenUv = gl_Position.xy * 0.5 + 0.5;
    tintColor = Color;
    quadCoord = UV0;
    radiusData = float(UV1.x) / RADIUS_SCALE;
    tintAmount = float(UV1.y) / PARAM_SCALE;
    shadowStrength = float(UV2.x) / PARAM_SCALE;
    shadowColor = Normal;
    spreadPx = LineWidth;
}
