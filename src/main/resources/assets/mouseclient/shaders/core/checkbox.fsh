#version 150

#moj_import <mouseclient:common.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform CheckboxParamsArray {
    vec4 params[1536];
};

out vec4 OutColor;

float segmentDistance(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
    return length(pa - ba * h);
}

void main() {
    int base = QuadIndex * 3;
    vec4 shape = params[base];
    vec4 offColor = params[base + 1];
    vec4 onColor = params[base + 2];

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 size = max(shape.yz, vec2(1.0));
    float progress = clamp(shape.w, 0.0, 1.0);
    float alpha = ralpha(size, coord, vec4(max(shape.x, 0.0)), 1.0);
    vec4 color = mix(offColor, onColor, progress);

    vec2 p = coord * size;
    float stroke = max(1.0, min(size.x, size.y) * 0.12);
    float first = segmentDistance(p, size * vec2(0.27, 0.53), size * vec2(0.42, 0.68));
    float second = segmentDistance(p, size * vec2(0.42, 0.68), size * vec2(0.74, 0.34));
    float check = (1.0 - smoothstep(stroke, stroke + 0.85, min(first, second))) * smoothstep(0.08, 1.0, progress);

    color.a *= alpha * (1.0 - check);

    if (color.a <= 0.001) {
        discard;
    }

    OutColor = color;
}
