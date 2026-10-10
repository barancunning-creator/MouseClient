#version 150

in vec3 vRay;

layout(std140) uniform SkyParams {
    mat4 InvViewProj;
    vec4 Color;
    vec4 Params;
};

out vec4 OutColor;

#define TAU 6.28318530718
#define MAX_ITER 5
#define INTENSITY 0.005

void main() {
    vec3 ray = normalize(vRay);

    float denom = max(ray.y + 1.0, 1e-4);
    vec2 uv2 = vec2(ray.x, ray.z) / denom * Params.y * 0.22;

    vec2 pc = mod(uv2 * TAU, TAU) - 250.0;

    vec2 iterPos = pc;
    float value = 1.0;
    float timeOffset = Params.x * 0.5 + 23.0;

    for (int n = 0; n < MAX_ITER; n++) {
        float iterTime = timeOffset * (1.0 - (3.5 / float(n + 1)));

        iterPos = pc + vec2(
        cos(iterTime - iterPos.x) + sin(iterTime + iterPos.y),
        sin(iterTime - iterPos.y) + cos(iterTime + iterPos.x)
        );

        float sinX = sin(iterPos.x + iterTime);
        float cosY = cos(iterPos.y + iterTime);

        float compX = pc.x * INTENSITY / max(abs(sinX), 0.001);
        float compY = pc.y * INTENSITY / max(abs(cosY), 0.001);
        float dist = sqrt(compX * compX + compY * compY);

        value += 1.0 / dist;
    }

    value = 1.17 - pow(value / float(MAX_ITER), 1.4);

    float pwr = value * value;
    pwr = pwr * pwr;
    pwr = pwr * pwr;

    vec3 baseColor = vec3(abs(pwr));
    vec3 enhancedColor = clamp(baseColor + vec3(0.0, 0.35, 0.5), 0.0, 1.0);
    float luminance = dot(enhancedColor, vec3(0.299, 0.587, 0.114));

    OutColor = vec4(vec3(luminance) * Color.rgb * 1.4, 1.0);
}
