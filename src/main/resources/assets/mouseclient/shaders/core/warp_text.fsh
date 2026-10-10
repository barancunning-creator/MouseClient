#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 TexCoord;
in vec4 VertexColor;
flat in int BatchIndex;

layout(std140) uniform WarpTextParams {
    vec4 params[256];
};

out vec4 OutColor;

float median(vec3 msdf) {
    return max(min(msdf.r, msdf.g), min(max(msdf.r, msdf.g), msdf.b));
}

void main() {
    int base = BatchIndex * 64;
    int glyphCount = int(params[base].x + 0.5);
    float pxRange = max(params[base].y, 1.0);
    float radius = max(params[base].z, 1.0);
    float cell = max(params[base].w, 1.0);
    vec2 mousePx = params[base + 1].xy;
    vec2 quadPx = params[base + 1].zw;
    float time = params[base + 2].x;

    vec2 px = TexCoord * quadPx;
    vec2 toMouse = px - mousePx;
    float mouseDist = length(toMouse);
    float pullPC = exp(-mouseDist * mouseDist / (2.0 * radius * radius));
    vec2 pushDir = mouseDist > 0.001 ? toMouse / mouseDist : vec2(0.0);
    vec2 sourcePx = px - pushDir * pullPC * cell * 1.45 - vec2(-pushDir.y, pushDir.x) * pullPC * cell * 0.55 - vec2(sin(px.y * 0.035 + time * 0.9), cos(px.x * 0.035 + time * 0.75)) * pullPC * cell * 0.3;

    float coverage = 0.0;
    for (int i = 0; i < glyphCount; i++) {
        vec4 rect = params[base + 3 + i * 2];
        vec4 uvRect = params[base + 4 + i * 2];
        if (sourcePx.x < rect.x || sourcePx.x > rect.z || sourcePx.y < rect.y || sourcePx.y > rect.w) continue;
        vec2 glyphPC = vec2((sourcePx.x - rect.x) / max(rect.z - rect.x, 0.0001), (sourcePx.y - rect.y) / max(rect.w - rect.y, 0.0001));
        vec2 atlasUv = mix(uvRect.xy, uvRect.zw, glyphPC);
        float screenPxRange = max(pxRange * (rect.w - rect.y) / max((uvRect.w - uvRect.y) * float(textureSize(Sampler0, 0).y), 0.0001), 1.0);
        coverage = max(coverage, clamp((median(texture(Sampler0, atlasUv).rgb) - 0.5) * screenPxRange + 0.5, 0.0, 1.0));
    }

    float alpha = coverage * VertexColor.a;
    if (alpha <= 0.0015) discard;

    OutColor = vec4(mix(VertexColor.rgb, vec3(1.0), pullPC * 0.35), alpha) * ColorModulator;
}
