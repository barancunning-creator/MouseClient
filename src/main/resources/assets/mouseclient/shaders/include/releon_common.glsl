float rdist(vec2 pos, vec2 size, vec4 radius) {
    float cornerRadius;
    if (pos.x > 0.0) {
        cornerRadius = (pos.y > 0.0) ? radius.x : radius.w;
    } else {
        cornerRadius = (pos.y > 0.0) ? radius.y : radius.z;
    }

    float r = clamp(cornerRadius, 0.0, min(size.x, size.y));
    vec2 q = abs(pos) - max(size - vec2(r), vec2(0.0));
    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

float ralpha(vec2 size, vec2 coord, vec4 radius, float smoothness) {
    vec2 safeSize = max(size, vec2(1.0));
    vec2 halfSize = safeSize * 0.5;
    vec2 pos = halfSize - (coord * safeSize);
    float maxRadius = min(halfSize.x, halfSize.y);
    vec4 safeRadius = clamp(max(radius, vec4(0.0)), vec4(0.0), vec4(maxRadius));
    float dist = rdist(pos, halfSize, safeRadius);
    float feather = max(smoothness, 0.001);
    return 1.0 - smoothstep(-feather, feather, dist);
}

float rsmin(float a, float b, float k) {
    if (k <= 0.0) {
        return min(a, b);
    }
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

const vec2[4] RECT_VERTICES_COORDS = vec2[] (
    vec2(0.0, 0.0),
    vec2(0.0, 1.0),
    vec2(1.0, 1.0),
    vec2(1.0, 0.0)
);

vec2 rvertexcoord(int id) {
    return RECT_VERTICES_COORDS[id % 4];
}
