#version 150

uniform sampler2D Sampler0;

layout(std140) uniform MotionBlurParams {
    vec4 TrailKeep;
};

in vec2 TexCoord;

out vec4 OutColor;

void main() {
    OutColor = vec4(texture(Sampler0, TexCoord).rgb, TrailKeep.x);
}
