#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(std140) uniform PlayerOutlineParams {
    vec4 Color;
    vec4 Glow;
    vec4 Screen;
};

in vec2 TexCoord;

out vec4 OutColor;

float sourceAt(vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return 0.0;
//    return step(0.5, texture(Sampler0, uv).a);
    return step(0.5, texture(Sampler1, uv).a);
}

//float coverageAt(vec2 uv) {
//    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return 0.0;
//    return texture(Sampler0, uv).r;
//}

vec2 coverageAt(vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return vec2(0.0);
    return texture(Sampler0, uv).rg;
}

//float solidAt(vec2 uv) {
//    return max(sourceAt(uv), max(max(sourceAt(uv + vec2(Screen.x, 0.0)), sourceAt(uv - vec2(Screen.x, 0.0))), max(sourceAt(uv + vec2(0.0, Screen.y)), sourceAt(uv - vec2(0.0, Screen.y)))));
//}

//float blurredCoverage(vec2 uv) {
//    float covered = 0.0;
//    float weightSum = 0.0;
//    for (int x = -6; x <= 6; x++) {
//        for (int y = -6; y <= 6; y++) {
//            float sampleWeight = exp(-float(x * x + y * y) / 18.0);
//            covered += sourceAt(uv + vec2(float(x), float(y)) * Screen.xy * 2.4) * sampleWeight;
//            weightSum += sampleWeight;
//        }
//    }
//    return covered / weightSum;
//}

//void main() {
//    float radius = 40.0;
//
//    if (solidAt(TexCoord) > 0.5) {
//        float border = 0.0;
//        for (int i = 0; i < 8; i++) {
//            border = max(border, 1.0 - solidAt(TexCoord + vec2(cos(float(i) * 0.7853982), sin(float(i) * 0.7853982)) * Screen.xy));
//        }
//        float playerAlpha = max(border * Glow.z, Color.a);
//        if (playerAlpha < 0.001) discard;
//        OutColor = vec4(Color.rgb, playerAlpha);
//        return;
//    }
//
//    float nearby = 0.0;
//    for (int i = 0; i < 16; i++) {
////        nearby = max(nearby, sourceAt(TexCoord + vec2(cos(float(i) * 0.3926991), sin(float(i) * 0.3926991)) * Screen.xy * radius));
//        nearby = max(nearby, max(sourceAt(TexCoord + vec2(cos(float(i) * 0.3926991), sin(float(i) * 0.3926991)) * Screen.xy * radius), max(sourceAt(TexCoord + vec2(cos(float(i) * 0.3926991), sin(float(i) * 0.3926991)) * Screen.xy * radius * 0.5), sourceAt(TexCoord + vec2(cos(float(i) * 0.3926991), sin(float(i) * 0.3926991)) * Screen.xy * radius * 0.25))));
//    }
//    if (nearby < 0.5) discard;
//
//    float sigma = max(radius * 0.45, 0.5);
//    float glowAccum = 0.0;
//    for (float r = 1.0; r <= radius; r += 1.0) {
//        vec2 o = Screen.xy * r;
//        glowAccum += exp(-(r * r) / (2.0 * sigma * sigma + 1e-5)) * (sourceAt(TexCoord + vec2(o.x, 0.0)) + sourceAt(TexCoord - vec2(o.x, 0.0)) + sourceAt(TexCoord + vec2(0.0, o.y)) + sourceAt(TexCoord - vec2(0.0, o.y)) + sourceAt(TexCoord + o) + sourceAt(TexCoord + vec2(o.x, -o.y)) + sourceAt(TexCoord + vec2(-o.x, o.y)) + sourceAt(TexCoord - o));
//    }
//    float glowEmit = 1.0 - exp(-(glowAccum / (sigma * 1.75 + 0.15)) * 1000.0 * 0.00055);
//    if (glowEmit < 0.001) discard;
//    OutColor = vec4(Color.rgb, glowEmit);
//}

//void main() {
//    float gate = 0.0;
//    for (int x = -3; x <= 3; x++) {
//        for (int y = -3; y <= 3; y++) {
//            gate = max(gate, sourceAt(TexCoord + vec2(float(x), float(y)) * Screen.xy * 4.8));
//        }
//    }
//    if (gate < 0.5) discard;
//
//    float blurred = blurredCoverage(TexCoord);
//
//    if (sourceAt(TexCoord) > 0.5) {
//        float playerAlpha = max(min(1.0, (1.0 - blurred) * 2.2) * Glow.z, Color.a);
//        if (playerAlpha < 0.001) discard;
//        OutColor = vec4(Color.rgb, playerAlpha);
//        return;
//    }
//
//    float halo = min(1.0, blurred * 2.2) * Glow.z;
//    if (halo < 0.001) discard;
//    OutColor = vec4(Color.rgb, halo);
//}

//void main() {
//    float blurred = 0.0;
//    float weightSum = 0.0;
////    for (int y = -6; y <= 6; y++) {
////        float sampleWeight = exp(-float(y * y) / 8.0);
////        blurred += coverageAt(TexCoord + vec2(0.0, float(y) * 3.0 * Screen.y)) * sampleWeight;
////    for (int y = -12; y <= 12; y++) {
////        float sampleWeight = exp(-float(y * y) / 32.0);
////        blurred += coverageAt(TexCoord + vec2(0.0, float(y) * 1.5 * Screen.y)) * sampleWeight;
//    for (int y = -22; y <= 22; y++) {
//        float sampleWeight = exp(-float(y * y) / 110.0);
//        blurred += coverageAt(TexCoord + vec2(0.0, float(y) * 2.2 * Screen.y)).r * sampleWeight;
//        weightSum += sampleWeight;
//    }
//    blurred /= weightSum;

//    float lineBlurred = 0.0;
//    float lineWeightSum = 0.0;
////    for (int y = -5; y <= 5; y++) {
////        float sampleWeight = exp(-float(y * y) / 7.0);
////        lineBlurred += coverageAt(TexCoord + vec2(0.0, float(y) * 0.85 * Screen.y)).g * sampleWeight;
////    for (int y = -8; y <= 8; y++) {
////        float sampleWeight = exp(-float(y * y) / 20.0);
////        lineBlurred += coverageAt(TexCoord + vec2(0.0, float(y) * 1.15 * Screen.y)).g * sampleWeight;
////    for (int y = -11; y <= 11; y++) {
////        float sampleWeight = exp(-float(y * y) / 42.0);
////        lineBlurred += coverageAt(TexCoord + vec2(0.0, float(y) * 1.5 * Screen.y)).g * sampleWeight;
////        lineWeightSum += sampleWeight;
////    }
////    lineBlurred /= lineWeightSum;
//    for (int y = -7; y <= 7; y++) {
//        lineBlurred = max(lineBlurred, coverageAt(TexCoord + vec2(0.0, float(y) * Screen.y)).g * (1.0 - abs(float(y)) / 8.0));
//    }

//    if (sourceAt(TexCoord) > 0.5) {
////        float innerReach = 0.0;
////        for (int i = 0; i < 8; i++) {
////            innerReach += sourceAt(TexCoord + vec2(cos(float(i) * 0.7853982), sin(float(i) * 0.7853982)) * Screen.xy * 3.0);
////        }
////        for (int i = 0; i < 8; i++) {
////            innerReach += sourceAt(TexCoord + vec2(cos(float(i) * 0.7853982), sin(float(i) * 0.7853982)) * Screen.xy * 6.5) * 0.5;
////            innerReach += sourceAt(TexCoord + vec2(cos(float(i) * 0.7853982), sin(float(i) * 0.7853982)) * Screen.xy * 3.25) * 0.5;
////        }
////        float playerAlpha = max(min(1.0, (1.0 - blurred) * 2.2) * Glow.z, Color.a);
////        float playerAlpha = max((1.0 - innerReach / 8.0) * Glow.z, Color.a);
////        float playerAlpha = max(smoothstep(0.08, 0.46, 1.0 - blurred) * Glow.z, Color.a);
////        float playerAlpha = max(smoothstep(0.62, 0.5, lineBlurred) * Glow.z, Color.a);
////        float playerAlpha = max(smoothstep(0.74, 0.46, lineBlurred) * Glow.z, Color.a);
////        float playerAlpha = max(smoothstep(0.82, 0.44, lineBlurred) * Glow.z, Color.a);
//        if (Color.a < 0.001) discard;
//        OutColor = vec4(Color.rgb, Color.a);
//        return;
//    }

////    float halo = min(1.0, blurred * 2.2) * Glow.z;
////    float halo = (1.0 - exp(-blurred * 5.5)) * Glow.z;
////    float halo = (1.0 - exp(-blurred * blurred * 4.0)) * Glow.z * 0.5;
////    float halo = max(smoothstep(0.06, 0.44, lineBlurred), (1.0 - exp(-blurred * blurred * 3.0)) * 0.22) * Glow.z;
////    float halo = max(smoothstep(0.015, 0.48, lineBlurred), (1.0 - exp(-blurred * blurred * 3.0)) * 0.22) * Glow.z;
////    float halo = max(smoothstep(0.008, 0.5, lineBlurred) * Glow.z, (1.0 - exp(-blurred * blurred * 3.0)) * Glow.w);
//    float halo = max(smoothstep(0.05, 0.74, lineBlurred) * Glow.z, (1.0 - exp(-blurred * blurred * 3.0)) * Glow.w);
//    if (halo < 0.004) discard;
//    OutColor = vec4(Color.rgb, halo);
//}
void main() {
    float wide = 0.0;
    float wideSum = 0.0;
    for (int y = -18; y <= 18; y++) {
        float sampleWeight = exp(-float(y * y) / 90.0);
        wide += coverageAt(TexCoord + vec2(0.0, float(y) * 2.4 * Screen.y)).r * sampleWeight;
        wideSum += sampleWeight;
    }
    wide /= wideSum;

    float rim = 0.0;
    float rimSum = 0.0;
    for (int y = -5; y <= 5; y++) {
        float sampleWeight = exp(-float(y * y) / 7.0);
        rim += coverageAt(TexCoord + vec2(0.0, float(y) * 1.2 * Screen.y)).g * sampleWeight;
        rimSum += sampleWeight;
    }
    rim /= rimSum;

    vec3 tint = Color.rgb;
    vec3 glowTint = tint;
    vec3 rimTint = mix(tint, vec3(1.0), 0.18);

    if (sourceAt(TexCoord) > 0.5) {
        float inner = 1.0 - smoothstep(0.55, 0.98, rim);
        float fill = Color.a * (0.20 + 0.80 * inner);
        if (fill < 0.002) discard;
        OutColor = vec4(mix(tint, rimTint, inner * 0.65), min(fill, 0.85));
        return;
    }

    float band = smoothstep(0.05, 0.48, rim);
    float rimAlpha = band * band * Glow.z * 0.62;
    float glowAlpha = pow(clamp(wide, 0.0, 1.0), 0.60) * Glow.w * 0.80;
    float alpha = glowAlpha + rimAlpha * (1.0 - glowAlpha);
    if (alpha < 0.003) discard;
    OutColor = vec4(mix(glowTint, rimTint, rimAlpha), min(alpha, 0.72));
}
