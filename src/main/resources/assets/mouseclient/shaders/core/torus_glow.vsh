#version 150

layout(std140) uniform TorusParams {
    mat4 ModelViewMat;
    mat4 ProjMat;
    vec4 CamPosFreq;
    vec4 GlowColorStrength;
};

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;

void main() {
    vertexColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
