#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 TexCoord;
in vec4 VertexColor;

out vec4 OutColor;

void main() {
    float fadeAlpha = clamp(VertexColor.a, 0.0, 1.0);
    float alpha = texture(Sampler0, TexCoord).a * fadeAlpha;
    if (alpha <= 0.001) {
        discard;
    }
    OutColor = vec4(VertexColor.rgb, alpha) * ColorModulator;
}
