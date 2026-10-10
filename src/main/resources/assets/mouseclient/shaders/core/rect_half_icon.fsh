#version 150

#moj_import <mouseclient:common.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 TexCoord;
in vec4 VertexColor;
in vec2 LocalPos;
flat in int RectIndex;

layout(std140) uniform RectHalfIconParamsArray {
    vec4 params[768];
};

out vec4 OutColor;

void main() {
    int base = RectIndex * 3;
    vec4 rect = params[base];
    vec4 radius = params[base + 1];
    vec4 smoothness = params[base + 2];
    vec2 size = max(rect.zw, vec2(1.0));

    if (LocalPos.x < 0.0 || LocalPos.y < 0.0 || LocalPos.x > size.x || LocalPos.y > size.y) {
        discard;
    }

    vec2 coord = LocalPos / size;
    float shapeAlpha = ralpha(size, coord, max(radius, vec4(0.0)), max(smoothness.x, 0.001));
    vec4 sampleColor = texture(Sampler0, TexCoord);
    float alpha = sampleColor.a * VertexColor.a * shapeAlpha;

    if (alpha <= 0.001) {
        discard;
    }

    OutColor = vec4(VertexColor.rgb, alpha) * ColorModulator;
}
