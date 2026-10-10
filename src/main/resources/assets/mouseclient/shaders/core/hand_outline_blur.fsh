#version 150

uniform sampler2D Sampler0;

layout(std140) uniform HandOutlineParams {
    vec4 Color;
    vec4 Glow;
    vec4 Screen;
};

in vec2 TexCoord;

out vec4 OutColor;

float coverageAt(vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return 0.0;
    return 1.0 - step(1.0, texture(Sampler0, uv).r);
}

void main() {
    float wide = 0.0;
    float wideSum = 0.0;
    for (int x = -18; x <= 18; x++) {
        float sampleWeight = exp(-float(x * x) / 90.0);
        wide += coverageAt(TexCoord + vec2(float(x) * 2.4 * Screen.x, 0.0)) * sampleWeight;
        wideSum += sampleWeight;
    }
    float rim = 0.0;
    float rimSum = 0.0;
    for (int x = -5; x <= 5; x++) {
        float sampleWeight = exp(-float(x * x) / 7.0);
        rim += coverageAt(TexCoord + vec2(float(x) * 1.2 * Screen.x, 0.0)) * sampleWeight;
        rimSum += sampleWeight;
    }
    OutColor = vec4(wide / wideSum, rim / rimSum, 0.0, 1.0);
}
