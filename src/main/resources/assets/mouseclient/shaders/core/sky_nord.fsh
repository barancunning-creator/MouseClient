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
#define Res (TimeIntensity.w)

in vec3 vRay;
in vec2 TexCoord;

out vec4 OutColor;

mat2 mm2(in float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, s, -s, c);
}

const mat2 m2 = mat2(0.95534, 0.29552, -0.29552, 0.95534);

float tri(in float x) {
    return clamp(abs(fract(x) - .5), 0.01, 0.49);
}

vec2 tri2(in vec2 p) {
    return vec2(tri(p.x) + tri(p.y), tri(p.y + tri(p.x)));
}

float hash21(in vec2 n) {
    return fract(sin(dot(n, vec2(12.9898, 4.1414))) * 43758.5453);
}

float triNoise2d(in vec2 p, mat2 rot) {
    float z = 1.8;
    float invZ2 = 0.4;
    float rz = 0.;
    p *= mm2(p.x * 0.06);
    vec2 bp = p;
    for (float i = 0.; i < 4.; i++) {
        vec2 dg = tri2(bp * 1.85) * .75;
        dg *= rot;
        p -= dg * invZ2;

        bp *= 1.3;
        invZ2 *= 2.2222222;
        z *= .42;
        p *= 1.21 + (rz - 1.0) * .02;

        rz += tri(p.x + tri(p.y)) * z;
        p *= -m2;
    }
    return clamp(1. / pow(rz * 29., 1.3), 0., .55);
}

vec3 nmzHash33(vec3 q) {
    uvec3 p = uvec3(ivec3(q));
    p = p * uvec3(374761393U, 1103515245U, 668265263U) + p.zxy + p.yzx;
    p = p.yzx * (p.zxy ^ (p >> 3U));
    return vec3(p ^ (p >> 16U)) * (1.0 / vec3(0xffffffffU));
}

vec3 stars(in vec3 p) {
    vec3 c = vec3(0.);
    vec3 sp = p * (.11 * Res);

    for (float i = 0.; i < 3.; i++) {
        vec3 id = floor(sp);
        vec2 rn = nmzHash33(id).xy;
        if (rn.x <= .0003 + i * i * 0.0005) {
            vec3 q = fract(sp) - 0.5;
            float c2 = 1. - smoothstep(0., .65, length(q));
            c += c2 * (mix(vec3(1.0, 0.75, 0.5), vec3(0.72, 0.85, 1.), rn.y) * 0.1 + 0.9);
        }
        sp *= 1.3;
    }
    return c * c * .9;
}

vec3 nordBg(in vec3 rd) {
    vec3 col = mix(vec3(0.035, 0.060, 0.130), vec3(0.012, 0.020, 0.055), smoothstep(0.0, 0.75, rd.y));
    float patchLeft = pow(clamp(dot(rd, normalize(vec3(-0.65, 0.55, 0.25))), 0.0, 1.0), 3.0);
    float patchRight = pow(clamp(dot(rd, normalize(vec3(0.7, 0.45, -0.3))), 0.0, 1.0), 3.5);
    col += vec3(0.30, 0.08, 0.26) * (patchLeft * 0.55 + patchRight * 0.40) * (0.8 + 0.2 * sin(time * 0.21));
    return col;
}

float fullSkyAmount(float skyHeight, float amount) {
    float lowerSky = 1.0 - smoothstep(-0.18, 0.18, skyHeight);
    float enabled = smoothstep(0.02, 0.18, amount);
    return clamp(mix(amount, max(amount, 0.96), lowerSky * enabled), 0.0, 1.0);
}

vec4 swirlAurora(vec3 ro, vec3 rd) {
    vec4 col = vec4(0.0);
    vec4 avgCol = vec4(0.0);

    mat2 shimmerRot = mm2(time * 0.055);
    float jitter = hash21(gl_FragCoord.xy);
    float invDen = 1.0 / (rd.y * 2.0 + 0.4);
//    vec2 swirlCenter = vec2(-6.2 + sin(time * 0.021) * 0.45, 0.35 + cos(time * 0.016) * 0.45);
    vec2 swirlCenter = vec2(-6.2 + sin(time * 0.021) * 0.30, 0.35 + cos(time * 0.016) * 0.30);

    for (int i = 0; i < 16; i++) {
        float fi = float(i);
        float pt = ((0.8 + fi * 0.032) - ro.y) * invDen - jitter * 0.004;
        vec3 bpos = ro + pt * rd;
        vec2 plane = bpos.zx;
        vec2 fromCenter = plane - swirlCenter + vec2(1e-4, 0.0);
        float r = length(fromCenter);
        float theta = atan(fromCenter.y, fromCenter.x);
//        float armPC = abs(fract(r * 0.42 - atan(fromCenter.y, fromCenter.x) * 0.159155 - time * 0.009) - 0.5) * 2.0;
        float armPC = abs(fract(r * 0.42 - theta * 0.159155 - time * 0.006) - 0.5) * 2.0;
//        float band = (1.0 - smoothstep(0.10, 0.52, armPC)) * smoothstep(0.25, 0.85, r) * (1.0 - smoothstep(2.8, 4.6, r));
        float band = pow(1.0 - smoothstep(0.04, 0.60, armPC), 1.5) * smoothstep(0.18, 0.50, r) * (1.0 - smoothstep(3.0, 5.0, r));
//        float curtain = triNoise2d(plane * 0.9, shimmerRot) * band;
        float curtain = triNoise2d(plane * 0.75, shimmerRot) * band * (0.55 + 0.9 * tri(theta * 4.7746 + r * 1.4));
        curtain *= 1.0 + 1.6 * (1.0 - smoothstep(0.5, 1.3, r));
        float heightPC = fi / 15.0;
//        vec3 layerColor = mix(vec3(0.12, 0.95, 0.40), vec3(0.42, 0.30, 0.95), pow(fi / 15.0, 1.3));
//        layerColor += vec3(0.75, 0.15, 0.55) * smoothstep(0.30, 0.50, armPC) * (1.0 - smoothstep(0.44, 0.62, armPC));
        vec3 layerColor = mix(vec3(0.10, 1.0, 0.42), vec3(0.14, 0.78, 0.66), smoothstep(0.30, 0.72, heightPC));
        layerColor = mix(layerColor, vec3(0.52, 0.30, 1.0), smoothstep(0.66, 0.98, heightPC));
        layerColor += vec3(0.85, 0.20, 0.62) * smoothstep(0.34, 0.55, armPC) * (1.0 - smoothstep(0.50, 0.68, armPC)) * smoothstep(0.40, 0.85, heightPC);
        avgCol = mix(avgCol, vec4(layerColor * curtain, curtain), 0.5);
        col += avgCol * exp2(-fi * 0.065 - 2.3);
    }

    col *= clamp(rd.y * 15.0 + 0.4, 0.0, 1.0);
//    return col * 2.6;
    return col * 3.4;
}

void main() {
    vec3 rd = normalize(vRay);
    rd.y = abs(rd.y);

    float fade = smoothstep(0.0, 0.01, rd.y) * 0.1 + 0.9;
    vec3 col = nordBg(rd) * fade;
    col += stars(rd);

    vec4 aur = smoothstep(0.0, 1.3, swirlAurora(vec3(0.0, 0.0, -6.7), rd)) * fade;
    col = col * (1.0 - aur.a) + aur.rgb;
    col += vec3(0.04, 0.22, 0.12) * (1.0 - smoothstep(0.0, 0.30, rd.y)) * (0.7 + 0.3 * sin(time * 0.35));

    float amount = clamp(max(Intensity, 0.0) * 0.92, 0.0, 1.0);
    OutColor = vec4(col, fullSkyAmount(rd.y, amount));
}
