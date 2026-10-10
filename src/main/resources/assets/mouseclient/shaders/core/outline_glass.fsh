#version 150

#moj_import <mouseclient:common.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

uniform sampler2D Sampler0;

layout(std140) uniform GlassOutlineParamsArray {
    vec4 params[3072];
};

out vec4 OutColor;

float outlineAlpha(vec2 coord, vec2 size, vec4 radius, float thickness, float smoothness) {

    vec2 safeSize = max(size, vec2(1.0));
    vec2 halfSize = safeSize * 0.5;
    vec2 pos = halfSize - (coord * safeSize);
    float maxRadius = min(halfSize.x, halfSize.y);
    vec4 safeRadius = clamp(max(radius, vec4(0.0)), vec4(0.0), vec4(maxRadius));

    float dist = rdist(pos, halfSize, safeRadius);
    float t = max(thickness, 0.0);
    float band = abs(dist + t * 0.5) - t * 0.5;
    float feather = fwidth(dist) * max(smoothness, 0.5);
    return 1.0 - smoothstep(-feather, feather, band);
}

void main() {
    int base = QuadIndex * 7;
    vec4 radius = max(params[base], vec4(0.0));
    vec4 sizeThicknessSmooth = params[base + 1];
    vec4 alphaPowerMix = params[base + 2];
    vec4 fresnelColor = params[base + 3];
    vec4 flagsDistortZSquirt = params[base + 4];
    vec4 tintColor = params[base + 5];
    vec4 reg = params[base + 6];

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 size = max(sizeThicknessSmooth.xy, vec2(1.0));
    float thickness = max(sizeThicknessSmooth.z, 0.0);
    float smoothness = max(sizeThicknessSmooth.w, 0.001);
    float globalAlpha = clamp(alphaPowerMix.x, 0.0, 1.0);
    float fresnelPower = max(alphaPowerMix.y, 0.001);
    float baseAlpha = clamp(alphaPowerMix.z, 0.0, 1.0);
    float fresnelMix = clamp(alphaPowerMix.w, 0.0, 1.0);
    float fresnelInvert = flagsDistortZSquirt.x;
    float distortStrength = flagsDistortZSquirt.y;

    float alpha = outlineAlpha(coord, size, radius, thickness, smoothness);

    vec2 center = size * 0.5;
    vec2 halfSize = max(center - 1.0, vec2(0.0));
    vec2 pos = center - coord * size;
    float distToEdge = abs(rdist(pos, halfSize, radius));
    float maxDistNorm = max(min(halfSize.x, halfSize.y), 0.001);
    float edgeGradient = 1.0 - clamp(distToEdge / maxDistNorm, 0.0, 1.0);
    float fresnelBase = (fresnelInvert > 0.5) ? edgeGradient : (1.0 - edgeGradient);

    float fresnel;
    if (fresnelPower > 20.0) {
        fresnel = exp(fresnelPower * log(clamp(fresnelBase, 0.001, 1.0)));
    } else {
        fresnel = pow(clamp(fresnelBase, 0.0, 1.0), fresnelPower);
    }
    fresnel = clamp(fresnel, 0.0, 1.0);

    vec2 dir = (length(pos) > 0.001) ? normalize(-pos) : vec2(0.0);
    vec2 texCoord = (gl_FragCoord.xy - reg.xy) / max(reg.zw, vec2(1.0));

    float cornerRad = (pos.x > 0.0) ? ((pos.y > 0.0) ? radius.x : radius.w)
                                    : ((pos.y > 0.0) ? radius.y : radius.z);
    cornerRad = max(cornerRad, 1.0);
    vec2 edgeXY = halfSize - abs(pos);
    float cornerness = (1.0 - smoothstep(0.0, cornerRad, edgeXY.x)) * (1.0 - smoothstep(0.0, cornerRad, edgeXY.y));

    vec2 ofs = dir * fresnel * distortStrength * (1.0 - 0.5 * cornerness);
    vec2 sampleUv = clamp(texCoord + ofs, vec2(0.0), vec2(1.0));
    vec4 baseCol = texture(Sampler0, sampleUv);

    vec2 tap = vec2(2.2) / max(reg.zw, vec2(1.0));
    vec4 smCol = baseCol * 0.20;
    smCol += texture(Sampler0, clamp(sampleUv + vec2(tap.x, 0.0), 0.0, 1.0)) * 0.12;
    smCol += texture(Sampler0, clamp(sampleUv - vec2(tap.x, 0.0), 0.0, 1.0)) * 0.12;
    smCol += texture(Sampler0, clamp(sampleUv + vec2(0.0, tap.y), 0.0, 1.0)) * 0.12;
    smCol += texture(Sampler0, clamp(sampleUv - vec2(0.0, tap.y), 0.0, 1.0)) * 0.12;
    smCol += texture(Sampler0, clamp(sampleUv + tap, 0.0, 1.0)) * 0.08;
    smCol += texture(Sampler0, clamp(sampleUv - tap, 0.0, 1.0)) * 0.08;
    smCol += texture(Sampler0, clamp(sampleUv + vec2(tap.x, -tap.y), 0.0, 1.0)) * 0.08;
    smCol += texture(Sampler0, clamp(sampleUv + vec2(-tap.x, tap.y), 0.0, 1.0)) * 0.08;
    vec4 texColor = mix(baseCol, smCol, cornerness);

    vec3 tintedColor = mix(texColor.rgb, tintColor.rgb, clamp(tintColor.a, 0.0, 1.0) * 0.65);
    vec3 finalColor = mix(tintedColor, fresnelColor.rgb, fresnel * fresnelMix);
    float finalAlpha = mix(baseAlpha, max(fresnelColor.a, tintColor.a), fresnel) * alpha * globalAlpha;

    if (finalAlpha < 0.001) {
        discard;
    }

    OutColor = vec4(finalColor, finalAlpha);
}
