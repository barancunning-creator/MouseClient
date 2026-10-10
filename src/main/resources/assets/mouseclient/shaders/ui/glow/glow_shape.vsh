#version 150

in vec3 Position;

out vec2 FragCoord;

void main() {

    gl_Position = vec4(Position.xy, 0.0, 1.0);

    FragCoord = vec2(Position.x * 0.5 + 0.5, 0.5 - Position.y * 0.5);
}
