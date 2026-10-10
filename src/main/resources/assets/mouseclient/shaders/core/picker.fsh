#version 150

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform PickerParamsArray {
    vec4 params[768];
};

out vec4 OutColor;

vec3 hueRamp(float h) {
    vec3 k = mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0);
    vec3 tri = clamp(abs(k - 3.0) - 1.0, 0.0, 1.0);
    return tri * tri * (3.0 - 2.0 * tri);
}

float roundedAlpha(vec2 coord, vec2 size, float radius, float smoothness) {
    vec2 p = coord * size;
    vec2 center = size * 0.5;
    float r = min(radius, min(center.x, center.y));
    vec2 q = abs(p - center) - (center - vec2(r));
    float dist = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;
    return 1.0 - smoothstep(-smoothness, smoothness, dist);
}

void main() {
    int base = QuadIndex * 3;
    vec4 cfg = params[base];
    vec4 sz = params[base + 1];
    vec4 solid = params[base + 2];

    int mode = int(cfg.x + 0.5);
    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 size = max(sz.xy, vec2(1.0));

    float shapeAlpha = roundedAlpha(coord, size, max(cfg.y, 0.0), max(cfg.z, 0.5));

    vec3 rgb;
    if (mode == 0) {
        rgb = hueRamp(coord.x);
    } else {

        float cell = max(sz.z, 1.0);
        vec2 c = floor((coord * size) / cell);
        float square = mod(c.x + c.y, 2.0) < 0.5 ? 0.95 : 0.42;
        rgb = mix(vec3(square), solid.rgb, coord.x);
    }

    float a = shapeAlpha * cfg.w;
    if (a <= 0.001) {
        discard;
    }
    OutColor = vec4(rgb, a);
}
