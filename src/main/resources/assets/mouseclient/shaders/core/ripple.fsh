#version 150

#moj_import <mouseclient:common.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec2 FragCoord;
in vec2 TexCoord;
flat in int QuadIndex;

layout(std140) uniform RippleParamsArray {
    vec4 params[2560];
};

uniform sampler2D Sampler0;

out vec4 OutColor;

void main() {
    int base = QuadIndex * 5;

    vec4 radius = params[base];
    vec4 sizeSmooth = params[base + 1];
    vec4 ripple = params[base + 2];
    vec4 sourceColor = params[base + 3];
    vec4 targetColor = params[base + 4];

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 size = max(sizeSmooth.xy, vec2(1.0));

    float alpha = ralpha(size, coord, max(radius, vec4(0.0)), sizeSmooth.z);

    vec2 delta = (coord - ripple.xy) * size;
    float dist = length(delta);

    float rippleRadius = ripple.z;
    float rippleSmooth = max(ripple.w, 0.001);

    float mask = smoothstep(rippleRadius - rippleSmooth, rippleRadius, dist);
    vec4 finalColor = mix(targetColor, sourceColor, mask);

    if (sizeSmooth.w > 0.5) {
        vec4 texColor = texture(Sampler0, TexCoord);
        finalColor *= texColor;
    }

    finalColor.a *= alpha;

    if (finalColor.a <= 0.001) {
        discard;
    }

    OutColor = finalColor * ColorModulator;
}
