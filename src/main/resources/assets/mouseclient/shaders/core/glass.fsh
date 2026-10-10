#version 150

#moj_import <mouseclient:common.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

uniform sampler2D Sampler0;

layout(std140) uniform GlassParamsArray {
    vec4 params[2560];
};

layout(std140) uniform BlurRegion {
    vec4 Region;
};

out vec4 OutColor;

void main() {
    int base = QuadIndex * 5;
    vec4 radius = max(params[base], vec4(0.0));
    vec4 sizeSmoothCorner = params[base + 1];
    vec4 alphaPowerMix = params[base + 2];
    vec4 fresnelColor = params[base + 3];
    vec4 flagsDistortZ = params[base + 4];

    vec2 size = max(sizeSmoothCorner.xy, vec2(1.0));
    float smoothness = sizeSmoothCorner.z;
    float cornerSmoothness = max(sizeSmoothCorner.w, 0.001);
    float globalAlpha = clamp(alphaPowerMix.x, 0.0, 1.0);
    float fresnelPower = max(alphaPowerMix.y, 0.001);
    float baseAlpha = clamp(alphaPowerMix.z, 0.0, 1.0);
    float fresnelMix = clamp(alphaPowerMix.w, 0.0, 1.0);
    float fresnelInvert = flagsDistortZ.x;
    float distortStrength = flagsDistortZ.y;

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 center = size * 0.5;
    vec2 halfSize = max(center - 1.0, vec2(0.0));
    vec2 pos = center - coord * size;

    float alpha = ralpha(size, coord, radius, smoothness);
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
    vec2 texCoord = (gl_FragCoord.xy - Region.xy) / max(Region.zw, vec2(1.0));

    float cornerRad = (pos.x > 0.0) ? ((pos.y > 0.0) ? radius.x : radius.w)
                                    : ((pos.y > 0.0) ? radius.y : radius.z);
    cornerRad = max(cornerRad, 1.0);
    vec2 edgeXY = halfSize - abs(pos);
    float cornerness = (1.0 - smoothstep(0.0, cornerRad, edgeXY.x)) * (1.0 - smoothstep(0.0, cornerRad, edgeXY.y));

    vec2 ofs = dir * fresnel * distortStrength * (1.0 - 0.5 * cornerness);
    vec2 sampleUv = clamp(texCoord + ofs, vec2(0.0), vec2(1.0));
    vec4 baseCol = texture(Sampler0, sampleUv);

    vec2 tap = vec2(2.2) / max(Region.zw, vec2(1.0));
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

    vec3 finalColor = mix(texColor.rgb, fresnelColor.rgb, fresnel * fresnelMix);
    float finalAlpha = mix(baseAlpha, fresnelColor.a, fresnel) * alpha * globalAlpha;

    if (finalAlpha < 0.001) {
        discard;
    }

    OutColor = vec4(finalColor, finalAlpha);
}
