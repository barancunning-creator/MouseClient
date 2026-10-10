#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 TexCoord;
in vec4 FragColor;
flat in int QuadIndex;

layout(std140) uniform ItemParamsArray {
    vec4 params[2048];
};

out vec4 OutColor;

void main() {
    vec4 textureColor = texture(Sampler0, TexCoord);
    vec4 color = textureColor * FragColor;

    float glintStrength = params[QuadIndex].x;
    if (glintStrength > 0.001 && color.a > 0.001) {
        float time = params[QuadIndex].y;
        float stripeA = sin((TexCoord.x + TexCoord.y) * 86.0 - time * 5.0);
        float stripeB = sin((TexCoord.x * 1.7 - TexCoord.y) * 54.0 + time * 3.1);
        float mask = smoothstep(0.18, 0.92, stripeA * 0.5 + stripeB * 0.25 + 0.5);
        vec3 glintColor = mix(vec3(0.42, 0.18, 0.95), vec3(0.95, 0.72, 1.0), mask);
        color.rgb = mix(color.rgb, glintColor, mask * glintStrength * color.a);
    }

    if (color.a <= 0.001) {
        discard;
    }

    OutColor = color * ColorModulator;
}
