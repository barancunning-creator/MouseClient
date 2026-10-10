#version 150

layout(std140) uniform SkyPostParams {
    mat4 InvViewProj;
    vec4 FirstColor;
    vec4 SecondColor;
    vec4 TimeIntensity;
    vec4 CameraPosition;
};

#define Intensity (TimeIntensity.y)
#define Res (TimeIntensity.w)

uniform sampler2D Sampler0;

in vec3 vRay;
in vec2 TexCoord;

out vec4 OutColor;

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
        if (rn.x <= .0002 + i * i * 0.0004) {
            vec3 q = fract(sp) - 0.5;
            float c2 = 1. - smoothstep(0., .65, length(q));
            c += c2 * (mix(vec3(1.0, 0.49, 0.1), vec3(0.75, 0.9, 1.), rn.y) * 0.1 + 0.9);
        }
        sp *= 1.3;
    }
    return c * c * .8;
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
    vec3 rd = normalize(vRay);
    rd.y = abs(rd.y);

    float fade = smoothstep(0.0, 0.01, rd.y) * 0.1 + 0.9;
    vec3 col = bg(rd) * fade;
    col += stars(rd);

    vec4 aur = texture(Sampler0, TexCoord);
    col = col * (1.0 - aur.a) + aur.rgb;

    float amount = clamp(max(Intensity, 0.0) * 0.92, 0.0, 1.0);
    OutColor = vec4(col, fullSkyAmount(rd.y, amount));
}
