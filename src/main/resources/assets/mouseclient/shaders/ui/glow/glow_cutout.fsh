#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(std140) uniform KawaseParams {
    vec4 SourceRect;
    vec4 HalfPixel;
    vec4 FallbackColor;
};

in vec2 TexCoord;

out vec4 OutColor;

void main() {
    vec2 c = SourceRect.xy + TexCoord * SourceRect.zw;
    vec4 blurred = texture(Sampler0, c);
    float body   = texture(Sampler1, c).a;

    float keep = 1.0 - smoothstep(0.0, 0.4, body);
    OutColor = vec4(blurred.rgb, blurred.a * keep);
}
