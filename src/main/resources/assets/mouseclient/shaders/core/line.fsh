#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform LineParamsArray {

    vec4 params[2048];
};

out vec4 OutColor;

void main() {
    int base = QuadIndex * 2;
    vec4 color = params[base];
    vec4 meta = params[base + 1];
    vec2 size = max(meta.xy, vec2(1.0));
    float fadeStart = clamp(meta.z, 0.0, 1.0);
    float fadeEnd = clamp(meta.w, 0.0, 1.0);

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 halfSize = size * 0.5;

    vec2 pos = halfSize - coord * size;
    vec2 q = abs(pos) - halfSize;
    float dist = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0);

    float feather = fwidth(dist) * 0.6;
    float alpha = 1.0 - smoothstep(-feather, feather, dist);

    alpha *= mix(fadeStart, fadeEnd, coord.x);

    color.a *= alpha;
    if (color.a <= 0.001) {
        discard;
    }
    OutColor = color * ColorModulator;
}
