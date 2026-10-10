#version 150

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform SoftGlowParams {
    vec4 params[640];
};

out vec4 OutColor;

float pickRadius(vec2 p, vec4 r) {

    float top = (p.x < 0.0) ? r.x : r.y;
    float bot = (p.x < 0.0) ? r.w : r.z;
    return (p.y < 0.0) ? top : bot;
}

float sdRoundBox(vec2 p, vec2 b, vec4 r) {
    float rad = min(pickRadius(p, r), min(b.x, b.y));
    rad = max(rad, 0.0);
    vec2 q = abs(p) - b + rad;
    return min(max(q.x, q.y), 0.0) + length(max(q, vec2(0.0))) - rad;
}

void main() {
    int base = QuadIndex * 5;
    vec4 p0 = params[base];
    vec4 p1 = params[base + 1];
    vec4 col = params[base + 2];
    vec4 p3 = params[base + 3];
    vec4 p4 = params[base + 4];

    vec2 halfSize = vec2(p0.x, p0.y);
    float pad = max(p0.z, 0.0001);
    float sigma = max(p0.w, 0.0001);
    float mode = p3.w;
    float falloff = max(p4.x, 0.25);

    vec2 fullHalf = halfSize + vec2(pad);
    vec2 local = (clamp(FragCoord, vec2(0.0), vec2(1.0)) - vec2(0.5)) * (fullHalf * 2.0);

    float d = sdRoundBox(local, halfSize, p1);

    float dist;
    float core;
    if (mode > 0.5) {
        dist = abs(d);
        core = 0.0;
    } else {
        dist = max(d, 0.0);
        core = (d <= 0.0) ? 1.0 : 0.0;
    }

    float halo = exp(-pow(dist / sigma, falloff));
    float intensity = max(core, halo);

    float dirStrength = clamp(p3.z, 0.0, 1.0);
    if (dirStrength > 0.0) {
        vec2 dir = vec2(p3.x, p3.y);
        if (length(dir) > 0.0001) {
            dir = normalize(dir);
            vec2 n = (length(local) > 0.0001) ? normalize(local) : dir;
            float w = clamp(0.5 + 0.5 * dot(n, dir), 0.0, 1.0);
            intensity *= mix(1.0, w * w, dirStrength);
        }
    }

    float a = col.a * intensity;
    if (a <= 0.0015) {
        discard;
    }

    OutColor = vec4(col.rgb, a);
}
