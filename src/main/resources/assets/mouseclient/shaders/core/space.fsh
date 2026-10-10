#version 150

in vec3 vRay;

layout(std140) uniform SkyParams {
    mat4 InvViewProj;
    vec4 Color;
    vec4 Params;
};

out vec4 OutColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash(p + vec3(0.0, 0.0, 0.0)), hash(p + vec3(1.0, 0.0, 0.0)), f.x),
            mix(hash(p + vec3(0.0, 1.0, 0.0)), hash(p + vec3(1.0, 1.0, 0.0)), f.x), f.y),
        mix(mix(hash(p + vec3(0.0, 0.0, 1.0)), hash(p + vec3(1.0, 0.0, 1.0)), f.x),
            mix(hash(p + vec3(0.0, 1.0, 1.0)), hash(p + vec3(1.0, 1.0, 1.0)), f.x), f.y),
        f.z);
}

float fbm(vec3 p) {
    float a = 0.0;
    float amp = 0.5;
    a += amp * noise(p); p *= 2.1; amp *= 0.5;
    a += amp * noise(p); p *= 2.1; amp *= 0.5;
    a += amp * noise(p); p *= 2.1; amp *= 0.5;
    a += amp * noise(p); p *= 2.1; amp *= 0.5;
    a += amp * noise(p);
    return a;
}

void main() {
    vec3 dir = normalize(vRay);

    float t = Params.x;

    vec3 th = Color.rgb;
    float thm = max(th.r, max(th.g, th.b));
    vec3 thN = thm > 0.001 ? th / thm : vec3(0.55, 0.52, 0.65);
    vec3 thDim = th * 0.22;

    vec3 col = mix(vec3(0.008, 0.01, 0.045), thDim, 0.78);
    float zen = dir.y * 0.5 + 0.5;
    vec3 zenAdd = mix(vec3(0.02, 0.03, 0.08), th * 0.14, 0.72);
    col += zenAdd * pow(max(1.0 - zen, 0.0), 2.5);

    vec3 pp = dir * 2.8;
    float n = fbm(pp);
    float n2 = fbm(pp.yzx * 1.38 + vec3(1.9, 2.1, 0.7));
    float nebMask = smoothstep(0.22, 0.95, n * n2);
    vec3 nebA = mix(vec3(0.45, 0.12, 0.55), thN * vec3(0.55, 0.35, 0.65) + th * 0.35, 0.58);
    vec3 nebB = mix(vec3(0.1, 0.35, 0.65), th * 0.42, 0.52);
    vec3 nebula = mix(nebA, nebB, n);
    nebula += mix(vec3(0.55, 0.15, 0.75), thN * 0.65 + th * 0.25, 0.58) * n2;
    float nebCore = pow(nebMask, 0.65);
    col += nebula * 0.85 * nebCore;
    vec3 glow = nebula * nebCore * nebCore * 1.4;
    col += glow;

    vec3 galAxis = normalize(vec3(0.1, 0.84, 0.16));
    float galW = abs(dot(dir, galAxis));
    float band = pow(max(1.0 - galW, 0.0), 6.0);
    float bandSoft = pow(max(1.0 - galW, 0.0), 14.0);
    vec3 galCol = mix(vec3(0.35, 0.32, 0.55), th * 0.55 + thN * 0.2, 0.55) * band * 0.75;
    galCol += mix(vec3(0.22, 0.28, 0.45), th * 0.42, 0.5) * bandSoft * 0.5;
    col += galCol;
    col += mix(vec3(0.15, 0.18, 0.35), thDim * 1.1, 0.6) * pow(max(1.0 - galW, 0.0), 22.0) * 0.45;

    vec3 sd = dir * 520.0;
    vec3 id = floor(sd);
    vec3 fr = fract(sd) - 0.5;
    float h = hash(id);
    float tw = 0.5 + 0.5 * sin(t * 2.5 + h * 50.0);
    float star = smoothstep(0.983, 0.998, h) * smoothstep(0.42, 0.0, length(fr));
    star *= mix(0.7, 1.0, tw);
    vec3 starTint = mix(vec3(0.95, 0.97, 1.0), mix(vec3(1.0), thN, 0.35), 0.4);
    vec3 starCol = starTint * star * 3.2;
    col += starCol;
    col += starCol * starCol * 0.35;

    vec3 sd2 = dir * 395.0 + vec3(19.0, 27.0, 13.0);
    vec3 id2 = floor(sd2);
    vec3 fr2 = fract(sd2) - 0.5;
    float h2 = hash(id2 + vec3(31.0, 17.0, 9.0));
    float star2 = smoothstep(0.992, 0.999, h2) * smoothstep(0.38, 0.0, length(fr2));
    col += mix(vec3(0.7, 0.88, 1.0), thN * 0.85 + th * 0.25, 0.45) * star2 * 2.0;

    float rim = max(1.0 - abs(dir.y), 0.0);
    vec3 rimCol = mix(vec3(0.06, 0.08, 0.18), th * 0.14, 0.62);
    col += rimCol * pow(rim, 2.8) * 0.4;

    col = col / (col + vec3(0.85));
    col = pow(col, vec3(0.92));

    col *= mix(vec3(1.0), thN, 0.28);
    col = mix(col, col * th, 0.12);

    OutColor = vec4(col, 1.0);
}
