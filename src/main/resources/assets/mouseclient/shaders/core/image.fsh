#version 150

#moj_import <mouseclient:common.glsl>

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 TexCoord;
in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform ImageParamsArray {
    vec4 params[3072];
};

out vec4 OutColor;

vec4 imageColor(vec2 coord, vec4 topLeft, vec4 topRight, vec4 bottomRight, vec4 bottomLeft) {
    return mix(mix(topLeft, topRight, coord.x), mix(bottomLeft, bottomRight, coord.x), coord.y);
}

void main() {
    int base = QuadIndex * 6;
    vec4 radius = params[base];
    vec4 sizeSmooth = params[base + 1];
    vec4 topLeft = params[base + 2];
    vec4 topRight = params[base + 3];
    vec4 bottomRight = params[base + 4];
    vec4 bottomLeft = params[base + 5];

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    float shapeAlpha = ralpha(max(sizeSmooth.xy, vec2(1.0)), coord, radius, sizeSmooth.z);
    vec4 textureColor = texture(Sampler0, TexCoord);
    vec4 color = textureColor * imageColor(coord, topLeft, topRight, bottomRight, bottomLeft);
    color.a *= shapeAlpha;

    if (color.a <= 0.001) {
        discard;
    }

    OutColor = color * ColorModulator;
}
