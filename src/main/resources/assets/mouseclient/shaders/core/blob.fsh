#version 150

#moj_import <mouseclient:common.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform BlobParamsArray {
    vec4 params[512];
};

out vec4 OutColor;

void main() {
    int base = QuadIndex * 4;
    vec4 geom = params[base];
    vec4 shape = params[base + 1];
    vec4 radius = params[base + 2];
    vec4 color = params[base + 3];

    vec2 size = max(geom.xy, vec2(0.001));
    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 p = coord * size;

    vec2 q = (p - geom.zw) / max(shape.xy, vec2(0.001));
    float glow = exp(-dot(q, q) * max(shape.z, 0.001));

    vec2 halfSize = size * 0.5;
    float maxRadius = min(halfSize.x, halfSize.y);
    vec4 safeRadius = clamp(max(radius, vec4(0.0)), vec4(0.0), vec4(maxRadius));
    vec2 pos = halfSize - p;
    float dist = rdist(pos, halfSize, safeRadius);
    float feather = max(shape.w, 0.001);
    float clip = 1.0 - smoothstep(-feather, feather, dist);

    float alpha = color.a * glow * clip;
    if (alpha <= 0.001) {
        discard;
    }
    OutColor = vec4(color.rgb, alpha);
}
