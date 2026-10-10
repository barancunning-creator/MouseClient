#version 150

#moj_import <mouseclient:common.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform OutlineParamsArray {
    vec4 params[4096];
};

layout(std140) uniform OutlineParamsArray2 {
    vec4 params2[4096];
};

vec4 outlineParam(int idx) {
    return idx < 4096 ? params[idx] : params2[idx - 4096];
}

out vec4 OutColor;

vec4 outlineColor(vec2 coord, vec4 topLeft, vec4 topRight, vec4 bottomRight, vec4 bottomLeft) {
    vec4 top = mix(topLeft, topRight, coord.x);
    vec4 bottom = mix(bottomLeft, bottomRight, coord.x);
    return mix(top, bottom, coord.y);
}

float outlineAlpha(vec2 coord, vec2 size, vec4 radius, float thickness, float smoothness) {

    vec2 safeSize = max(size, vec2(1.0));
    vec2 halfSize = safeSize * 0.5;
    vec2 pos = halfSize - (coord * safeSize);
    float maxRadius = min(halfSize.x, halfSize.y);
    vec4 safeRadius = clamp(max(radius, vec4(0.0)), vec4(0.0), vec4(maxRadius));

    float dist = rdist(pos, halfSize, safeRadius);
    float t = max(thickness, 0.0);
    float band = abs(dist + t * 0.5) - t * 0.5;

    float feather = fwidth(dist) * max(smoothness, 0.5);
    return 1.0 - smoothstep(-feather, feather, band);
}

void main() {
    int base = QuadIndex * 8;
    vec4 radius = outlineParam(base);
    vec4 sizeThicknessSmooth = outlineParam(base + 1);
    vec4 topLeft = outlineParam(base + 2);
    vec4 topRight = outlineParam(base + 3);
    vec4 bottomRight = outlineParam(base + 4);
    vec4 bottomLeft = outlineParam(base + 5);

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 size = max(sizeThicknessSmooth.xy, vec2(1.0));
    float thickness = max(sizeThicknessSmooth.z, 0.0);
    float alpha = outlineAlpha(coord, size, radius, thickness, sizeThicknessSmooth.w);

    vec4 color = outlineColor(coord, topLeft, topRight, bottomRight, bottomLeft);
    color.a *= alpha;

    vec4 scissorPosSize = outlineParam(base + 6);
    vec4 scissorRadius = outlineParam(base + 7);
    if (scissorPosSize.z > 0.0 && scissorPosSize.w > 0.0) {
        vec2 scissorCoord = (gl_FragCoord.xy - scissorPosSize.xy) / scissorPosSize.zw;
        color.a *= ralpha(scissorPosSize.zw, scissorCoord, max(scissorRadius, vec4(0.0)), 1.5);
    }

    if (color.a <= 0.001) {
        discard;
    }

    OutColor = color;
}
