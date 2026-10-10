#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 fanCoord;

out vec4 fragColor;

void main() {
    float d = length(fanCoord);
    float aa = max(fwidth(d), 0.0001);
    float m = 1.0 - smoothstep(1.0 - aa, 1.0 + aa, d);
    if (m <= 0.001) discard;
    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * m);
    if (fragColor.a <= 0.0005) discard;
}
