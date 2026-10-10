#version 150

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform CircleParamsArray {
    vec4 params[1024];
};

out vec4 OutColor;

void main() {
    int base = QuadIndex * 2;
    vec4 shape = params[base];
    vec4 color = params[base + 1];

    float radius = max(shape.x, 0.0);
    float feather = max(shape.y, 0.5);
    float thickness = max(shape.z, 0.0);
    float extent = radius + feather;

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 local = (coord - vec2(0.5)) * (extent * 2.0);
    float dist = length(local);
//    float aa = min(fwidth(dist) * 0.6, feather);
    float aa = min(fwidth(dist) * 0.5, feather);
    float outer = 1.0 - smoothstep(radius - aa, radius + aa, dist);
    float alpha = outer;

    if (thickness > 0.0) {
        float innerRadius = max(radius - thickness, 0.0);
        float inner = 1.0 - smoothstep(innerRadius - aa, innerRadius + aa, dist);
        alpha = clamp(outer - inner, 0.0, 1.0);
    }

    color.a *= alpha;
    if (color.a <= 0.001) {
        discard;
    }

    OutColor = color;
}
