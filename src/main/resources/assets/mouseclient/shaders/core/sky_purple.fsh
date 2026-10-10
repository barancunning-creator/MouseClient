#version 150

layout(std140) uniform SkyPostParams {
    mat4 InvViewProj;
    vec4 FirstColor;
    vec4 SecondColor;
    vec4 TimeIntensity;
    vec4 CameraPosition;
};

#define PurpleColor FirstColor
#define time (TimeIntensity.x)
#define Intensity (TimeIntensity.y)

in vec3 vRay;
in vec2 TexCoord;

out vec4 OutColor;

const float TAU = 6.28318530718;
const int MAX_ITER = 5;

float fullSkyAmount(float skyHeight, float amount) {
    float lowerSky = 1.0 - smoothstep(-0.18, 0.18, skyHeight);
    float enabled = smoothstep(0.02, 0.18, amount);
    return clamp(mix(amount, max(amount, 0.96), lowerSky * enabled), 0.0, 1.0);
}

vec3 purpleShaderColor(vec3 worldDirection) {
    float skyHeight = abs(worldDirection.y);
    vec2 uv = worldDirection.xz / max(0.36 + skyHeight, 0.32);
    vec2 p = mod(uv * TAU, TAU) - 250.0;
    vec2 i = p;
    float c = 1.0;
    float inten = 0.005;
    float localTime = time * 1.5 + 23.0;

    for (int n = 0; n < MAX_ITER; n++) {
        float t = localTime * (1.0 - (3.5 / float(n + 1)));
        i = p + vec2(cos(t - i.x) + sin(t + i.y), sin(t - i.y) + cos(t + i.x));
        c += 1.0 / length(vec2(p.x / (sin(i.x + t) / inten), p.y / (cos(i.y + t) / inten)));
    }

    c /= float(MAX_ITER);
    c = 1.17 - pow(c, 1.4);

    vec3 shaderColor = vec3(pow(abs(c), 8.0));
    shaderColor = clamp(shaderColor + PurpleColor.rgb, 0.0, 1.0);

    float zenith = 1.0 - smoothstep(0.86, 1.0, skyHeight);
    vec3 baseColor = mix(PurpleColor.rgb * 0.10, PurpleColor.rgb * 0.28, smoothstep(0.0, 0.72, skyHeight));
    return mix(baseColor, shaderColor, zenith);
}

void main() {
    vec3 skyDirection = normalize(vRay);
    vec3 shaderColor = purpleShaderColor(skyDirection);
    float amount = clamp(max(Intensity, 0.0) * PurpleColor.a * 0.84, 0.0, 1.0);

    OutColor = vec4(shaderColor, fullSkyAmount(skyDirection.y, amount));
}
