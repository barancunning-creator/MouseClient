#version 150

in vec3 Position;

layout(std140) uniform SkyParams {
    mat4 InvViewProj;
    vec4 Color;
    vec4 Params;
};

out vec3 vRay;
out vec2 TexCoord;

void main() {
    gl_Position = vec4(Position.xy, 1.0, 1.0);
    TexCoord = Position.xy * 0.5 + 0.5;
    vec4 far = InvViewProj * vec4(Position.xy, 1.0, 1.0);
    vRay = far.xyz / far.w;
}
