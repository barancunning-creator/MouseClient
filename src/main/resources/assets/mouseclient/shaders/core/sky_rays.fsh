#version 150

layout(std140) uniform SkyPostParams {
    mat4 InvViewProj;
    vec4 FirstColor;
    vec4 SecondColor;
    vec4 TimeIntensity;
    vec4 CameraPosition;
};

#define time (TimeIntensity.x)
#define Intensity (TimeIntensity.y)

const float SOFTNESS = 0.45;
const float COVERAGE = 0.8;

in vec3 vRay;
in vec2 TexCoord;

out vec4 OutColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));
    float c00 = mix(n000, n100, f.x);
    float c10 = mix(n010, n110, f.x);
    float c01 = mix(n001, n101, f.x);
    float c11 = mix(n011, n111, f.x);
    float c0 = mix(c00, c10, f.y);
    float c1 = mix(c01, c11, f.y);
    return mix(c0, c1, f.z);
}

float fbm3(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p *= 2.0;
        a *= 0.5;
    }
    return v;
}

vec3 bg(in vec3 rd) {
    float sd = dot(normalize(vec3(-0.5, -0.6, 0.9)), rd) * 0.5 + 0.5;
    sd = pow(max(sd, 0.), 5.);
    vec3 col = mix(vec3(0.05, 0.1, 0.2), vec3(0.1, 0.05, 0.2), sd);
    return col * .63;
}

float fullSkyAmount(float skyHeight, float amount) {
    float lowerSky = 1.0 - smoothstep(-0.18, 0.18, skyHeight);
    float enabled = smoothstep(0.02, 0.18, amount);
    return clamp(mix(amount, max(amount, 0.96), lowerSky * enabled), 0.0, 1.0);
}

void main() {
    vec3 dir = normalize(vRay);
    dir.y = abs(dir.y);
    float v = dir.y;

    vec3 col = bg(dir);

    if (v > 0.001) {
        vec2 hd = normalize(dir.xz + vec2(1e-4, 0.0));
        vec2 ring = hd * 2.5;
        float t = time * 0.15;

        float warp = fbm3(vec3(ring, t));
        float rayN = fbm3(vec3(ring * 1.4 + vec2(warp * 1.6, v * 1.4), t * 0.6));
        float rays = clamp(1.0 - abs(2.0 * rayN - 1.0), 0.0, 1.0);
        rays = pow(rays, mix(4.0, 1.2, SOFTNESS));

        float top = mix(0.55, 1.15, COVERAGE);
        float env = smoothstep(0.0, 0.10, v) * (1.0 - smoothstep(top * 0.5, top, v));
        float m = rays * env;

        float h = clamp(v / top, 0.0, 1.0);
        vec3 aur = mix(FirstColor.rgb, SecondColor.rgb, h * h);

        float glow = smoothstep(0.0, 0.08, v) * (1.0 - smoothstep(0.08, 0.45, v));

        col += aur * m;
        col += FirstColor.rgb * glow * 0.12;
    }

    float amount = clamp(max(Intensity, 0.0) * 0.92, 0.0, 1.0);
    OutColor = vec4(col, fullSkyAmount(v, amount));
}
