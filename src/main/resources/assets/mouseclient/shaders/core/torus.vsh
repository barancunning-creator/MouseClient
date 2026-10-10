#version 150

layout(std140) uniform TorusParams {
    mat4 ModelViewMat;
    mat4 ProjMat;
    vec4 CamPosFreq;
    vec4 GlowColorStrength;
};

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 viewPos;
out vec3 viewNormal;
out vec4 vertexColor;

void main() {
    viewPos = vec3(ModelViewMat * vec4(Position, 1.0));
    viewNormal = mat3(transpose(inverse(ModelViewMat))) * Normal;
    vertexColor = Color;
    gl_Position = ProjMat * vec4(viewPos, 1.0);
}
