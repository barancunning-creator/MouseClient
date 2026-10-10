#version 150

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform BracketParamsArray {
    vec4 params[512];
};

out vec4 OutColor;

float cornerDist(vec2 q, float r) {
    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    int base = QuadIndex * 2;
    vec4 shape = params[base];
    vec4 color = params[base + 1];

    float size = max(shape.x, 0.0);
    float radius = clamp(shape.y, 0.0, size);
    float halfW = clamp(shape.z * 0.5, 0.0, radius);
    float feather = max(shape.w, 0.05);

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 p = coord * (size + 2.0 * feather) - vec2(feather);

    float innerRadius = max(radius - halfW, 0.0);
    float dOuter = cornerDist(p - vec2(size - radius), radius);
    float dInner = cornerDist(p - vec2(size - radius - halfW), innerRadius);

    float d = max(dOuter, -dInner);
    d = max(d, max(halfW - p.x, halfW - p.y));
    d = min(d, length(p - vec2(halfW, size - halfW)) - halfW);
    d = min(d, length(p - vec2(size - halfW, halfW)) - halfW);

    float alpha = 1.0 - smoothstep(-feather, feather, d);
    color.a *= alpha;
    if (color.a <= 0.001) {
        discard;
    }
    OutColor = color;
}
