#version 150

layout(std140) uniform SkyPostParams {
    mat4 InvViewProj;
    vec4 FirstColor;
    vec4 SecondColor;
    vec4 TimeIntensity;
    vec4 CameraPosition;
};

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec3 vRay;
in vec2 TexCoord;

out vec4 OutColor;

void main() {
    float depth = texture(Sampler1, TexCoord).r;
    vec4 clip = InvViewProj * vec4(TexCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    float sceneDistance = depth >= 1.0 ? 1.0 : min(length(clip.xyz / clip.w) / SecondColor.x, 1.0);
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    vec2 grid = TexCoord / texel - 0.5;
    vec2 cell = floor(grid);
    vec2 blend = grid - cell;
    float fogSum = 0.0;
    float weightSum = 0.0;
    for (int corner = 0; corner < 4; corner++) {
        vec2 offset = vec2(float(corner & 1), float(corner >> 1));
        vec2 sampled = texture(Sampler0, (cell + offset + 0.5) * texel).rg;
        vec2 bilinear = mix(1.0 - blend, blend, offset);
        float weight = bilinear.x * bilinear.y * exp(-abs(sampled.g - sceneDistance) * 60.0) + 0.0001;
        fogSum += sampled.r * weight;
        weightSum += weight;
    }
    OutColor = vec4(FirstColor.rgb, fogSum / weightSum);
}
