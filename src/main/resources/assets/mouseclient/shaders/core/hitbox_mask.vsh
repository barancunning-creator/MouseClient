#version 150

in vec3 Position;

layout(std140) uniform HitboxMaskParams {
    mat4 ModelViewProjection;
    vec4 MaskStrength;
};

out float MaskAlpha;

void main() {
    gl_Position = ModelViewProjection * vec4(Position, 1.0);
    MaskAlpha = MaskStrength.x;
}
