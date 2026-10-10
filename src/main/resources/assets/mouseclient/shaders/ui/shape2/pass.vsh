#version 150

in vec3 Position;
in vec2 UV0;
in float LineWidth;

out vec2 texCoord0;
flat out float blurOffset;

void main() {
    gl_Position = vec4(Position, 1.0);
    texCoord0 = UV0;
    blurOffset = LineWidth;
}
