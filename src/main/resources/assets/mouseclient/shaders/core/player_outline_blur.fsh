#version 150

uniform sampler2D Sampler0;

layout(std140) uniform PlayerOutlineParams {
    vec4 Color;
    vec4 Glow;
    vec4 Screen;
};

in vec2 TexCoord;

out vec4 OutColor;

//float sourceAt(vec2 uv) {
//    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return 0.0;
//    return step(0.5, texture(Sampler0, uv).a);
//}

float coverageAt(vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return 0.0;
    return texture(Sampler0, uv).a;
}

//void main() {
//    float covered = 0.0;
//    float weightSum = 0.0;
////    for (int x = -6; x <= 6; x++) {
////        float sampleWeight = exp(-float(x * x) / 8.0);
////        covered += sourceAt(TexCoord + vec2(float(x) * 3.0 * Screen.x, 0.0)) * sampleWeight;
////    for (int x = -12; x <= 12; x++) {
////        float sampleWeight = exp(-float(x * x) / 32.0);
////        covered += coverageAt(TexCoord + vec2(float(x) * 1.5 * Screen.x, 0.0)) * sampleWeight;
//    for (int x = -22; x <= 22; x++) {
//        float sampleWeight = exp(-float(x * x) / 110.0);
//        covered += coverageAt(TexCoord + vec2(float(x) * 2.2 * Screen.x, 0.0)) * sampleWeight;
//        weightSum += sampleWeight;
//    }
//    float lineCovered = 0.0;
//    float lineWeightSum = 0.0;
////    for (int x = -5; x <= 5; x++) {
////        float sampleWeight = exp(-float(x * x) / 7.0);
////        lineCovered += coverageAt(TexCoord + vec2(float(x) * 0.85 * Screen.x, 0.0)) * sampleWeight;
////    for (int x = -8; x <= 8; x++) {
////        float sampleWeight = exp(-float(x * x) / 20.0);
////        lineCovered += coverageAt(TexCoord + vec2(float(x) * 1.15 * Screen.x, 0.0)) * sampleWeight;
////    for (int x = -11; x <= 11; x++) {
////        float sampleWeight = exp(-float(x * x) / 42.0);
////        lineCovered += coverageAt(TexCoord + vec2(float(x) * 1.5 * Screen.x, 0.0)) * sampleWeight;
////        lineWeightSum += sampleWeight;
////    }
//    for (int x = -7; x <= 7; x++) {
//        lineCovered = max(lineCovered, coverageAt(TexCoord + vec2(float(x) * Screen.x, 0.0)) * (1.0 - abs(float(x)) / 8.0));
//    }
////    OutColor = vec4(covered / weightSum, 0.0, 0.0, 1.0);
////    OutColor = vec4(covered / weightSum, lineCovered / lineWeightSum, 0.0, 1.0);
//    OutColor = vec4(covered / weightSum, lineCovered, 0.0, 1.0);
//}
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
