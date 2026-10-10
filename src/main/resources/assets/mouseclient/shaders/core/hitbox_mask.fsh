#version 150

in float MaskAlpha;

out vec4 OutColor;

void main() {
    OutColor = vec4(1.0, 1.0, 1.0, MaskAlpha);
}
