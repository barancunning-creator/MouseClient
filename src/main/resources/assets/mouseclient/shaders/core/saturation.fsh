#version 150

uniform sampler2D Sampler0;

layout(std140) uniform SaturationParams {
    vec4 Params;
};

in vec2 TexCoord;

out vec4 OutColor;

void main() {
    vec4 color = texture(Sampler0, TexCoord);
    OutColor = vec4(mix(vec3(dot(color.rgb, vec3(0.299, 0.587, 0.114))), color.rgb, Params.x), 1.0);
}
