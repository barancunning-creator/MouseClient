#version 150

#moj_import <mouseclient:releon_common.glsl>

in vec2 FragCoord;
in vec4 FragColor;
flat in int QuadIndex;

uniform sampler2D Sampler0;

layout(std140) uniform BlurParamsArray {
    vec4 params[2048];
};

out vec4 OutColor;

void main() {
    int idx = QuadIndex;
    vec4 Radius = params[idx * 3];
    vec4 sizeSmooth = params[idx * 3 + 1];
    vec4 reg = params[idx * 3 + 2];
    vec2 Size = sizeSmooth.xy;
    float Smoothness = sizeSmooth.z;

    vec2 texCoord = clamp((gl_FragCoord.xy - reg.xy) / max(reg.zw, vec2(1.0)), vec2(0.0), vec2(1.0));
    vec3 blurred = texture(Sampler0, texCoord).rgb;

    float tintStrength = clamp(FragColor.a * (1.0 - dot(FragColor.rgb, vec3(0.299, 0.587, 0.114))) * 0.72, 0.0, 0.82);
    vec4 color = vec4(mix(blurred, FragColor.rgb, tintStrength), FragColor.a);
    color.a *= ralpha(Size, FragCoord, Radius, Smoothness);

    if (color.a == 0.0) { discard; }
    OutColor = color;
}
