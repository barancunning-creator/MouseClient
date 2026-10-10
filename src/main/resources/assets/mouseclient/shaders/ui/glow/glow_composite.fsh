#version 150

uniform sampler2D Sampler0;

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform GlowParamsArray {
    vec4 params[1120];
};

out vec4 OutColor;

void main() {
    int base = QuadIndex * 5;

    vec4 reg       = params[base + 3];
    vec4 colorVec  = params[base + 2];

    vec2 texCoord = clamp(reg.xy + FragCoord * reg.zw, vec2(0.0), vec2(1.0));
    vec4 blurred  = texture(Sampler0, texCoord);

    float intensity = colorVec.a;

    float a = blurred.a * intensity;
    OutColor = vec4(blurred.rgb * a, a);
}
