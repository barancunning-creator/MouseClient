#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 TexCoord;
in vec4 VertexColor;

out vec4 OutColor;

void main() {
    float alpha = texture(Sampler0, TexCoord).a * VertexColor.a;
    if (alpha <= 0.001) {
        discard;
    }
    OutColor = vec4(VertexColor.rgb, alpha) * ColorModulator;
}
