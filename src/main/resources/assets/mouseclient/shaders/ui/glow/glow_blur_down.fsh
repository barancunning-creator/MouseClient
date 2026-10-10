#version 150

uniform sampler2D Sampler0;

layout(std140) uniform KawaseParams {
    vec4 SourceRect;
    vec4 HalfPixel;
    vec4 FallbackColor;
};

in vec2 TexCoord;

out vec4 OutColor;

void main() {
    vec2 c = SourceRect.xy + TexCoord * SourceRect.zw;
    vec4 sum = texture(Sampler0, c) * 4.0;
    sum += texture(Sampler0, c - HalfPixel.xy);
    sum += texture(Sampler0, c + HalfPixel.xy);
    sum += texture(Sampler0, c + vec2(HalfPixel.x, -HalfPixel.y));
    sum += texture(Sampler0, c - vec2(HalfPixel.x, -HalfPixel.y));
    OutColor = sum / 8.0;
}
