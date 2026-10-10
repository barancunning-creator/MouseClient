#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

void main() {
    vec4 color = texture(Sampler0, TexCoord) * FragColor;
    if (color.a <= 0.001) {
        discard;
    }

    OutColor = color * ColorModulator;
}
