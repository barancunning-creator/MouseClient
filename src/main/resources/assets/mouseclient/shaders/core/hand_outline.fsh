#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(std140) uniform HandOutlineParams {
    vec4 Color;
    vec4 Glow;
    vec4 Screen;
};

in vec2 TexCoord;

out vec4 OutColor;

float sourceAt(vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return 0.0;
//    return step(Glow.w, length(texture(Sampler0, uv).rgb - texture(Sampler1, uv).rgb));
//    return 1.0 - step(1.0, texture(Sampler0, uv).r);
    return 1.0 - step(1.0, texture(Sampler1, uv).r);
}

vec2 coverageAt(vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return vec2(0.0);
    return texture(Sampler0, uv).rg;
}

//float solidAt(vec2 uv) {
//    return max(sourceAt(uv), max(max(sourceAt(uv + vec2(Screen.x, 0.0)), sourceAt(uv - vec2(Screen.x, 0.0))), max(sourceAt(uv + vec2(0.0, Screen.y)), sourceAt(uv - vec2(0.0, Screen.y)))));
//}

//void main() {
//    float radius = 40.0;

//    if (solidAt(TexCoord) > 0.5) {
//        float border = 0.0;
//        for (int i = 0; i < 8; i++) {
//            border = max(border, 1.0 - solidAt(TexCoord + vec2(cos(float(i) * 0.7853982), sin(float(i) * 0.7853982)) * Screen.xy));
//        }
//        float handAlpha = max(border * Glow.z, Color.a);
//        if (handAlpha < 0.001) discard;
//        OutColor = vec4(Color.rgb, handAlpha);
//        return;
//    }

//    float nearby = 0.0;
//    for (int i = 0; i < 16; i++) {
//        nearby = max(nearby, sourceAt(TexCoord + vec2(cos(float(i) * 0.3926991), sin(float(i) * 0.3926991)) * Screen.xy * radius));
//    }
//    if (nearby < 0.5) discard;

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
