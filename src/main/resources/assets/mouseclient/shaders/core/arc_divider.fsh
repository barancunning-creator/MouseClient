#version 150

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform ArcParamsArray {
    vec4 params[1024];
};

out vec4 OutColor;

float parabolaDistance(vec2 p, float k, float halfW) {

    float best = 1e9;
    const int STEPS = 32;
    for (int i = 0; i <= STEPS; i++) {
        float t = float(i) / float(STEPS);
        float x = mix(-halfW, halfW, t);
        float y = k * x * x;
        best = min(best, distance(p, vec2(x, y)));
    }
    return best;
}

void main() {
    int base = QuadIndex * 2;
    vec4 geom = params[base];
    vec4 color = params[base + 1];

    float halfW = max(geom.x, 0.5);
    float lift = geom.y;
    float thickness = max(geom.z, 0.5);
    float feather = max(geom.w, 0.75);

    float padX = thickness + feather;
    float padY = thickness + feather;
    float spanX = halfW + padX;
    float spanY = lift + padY;
    vec2 local = (FragCoord - vec2(0.5)) * vec2(spanX * 2.0, spanY * 2.0);

    float k = halfW > 0.0 ? lift / (halfW * halfW) : 0.0;

    vec2 p = vec2(local.x, local.y + lift * 0.5);
    float dist = parabolaDistance(p, k, halfW);

    float halfThickness = thickness * 0.5;
    float alpha = 1.0 - smoothstep(halfThickness - feather, halfThickness + feather, dist);

    float edge = 1.0 - clamp((abs(local.x) - (halfW - feather * 4.0)) / (feather * 4.0), 0.0, 1.0);
    alpha *= edge;

    color.a *= alpha;
    if (color.a <= 0.001) {
        discard;
    }
    OutColor = color;
}
