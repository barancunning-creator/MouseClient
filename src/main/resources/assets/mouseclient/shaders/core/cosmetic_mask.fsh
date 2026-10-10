#version 150

uniform sampler2D Sampler0;

in vec2 TexCoord;
in float MaskAlpha;

out vec4 OutColor;

void main() {
    if (texture(Sampler0, TexCoord).a < 0.5) discard;
    OutColor = vec4(1.0, 1.0, 1.0, MaskAlpha);
}
