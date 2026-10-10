#version 150

in vec3 Position;
in vec2 UV0;

layout(std140) uniform HitboxMaskParams {
    mat4 ModelViewProjection;
    vec4 MaskStrength;
};

out vec2 TexCoord;
out float MaskAlpha;

void main() {
    gl_Position = ModelViewProjection * vec4(Position, 1.0);
    TexCoord = UV0;
    MaskAlpha = MaskStrength.x;
}
