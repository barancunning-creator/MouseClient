#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec2  TexCoord;
in vec4  VertexColor;
flat in int BatchIndex;

layout(std140) uniform ShimmerParamsArray {
    vec4 params[256];
};

out vec4 OutColor;

void main() {
    int   base      = BatchIndex;
    float center    = params[base].x;
    float halfW     = params[base].y;
    float intensity = params[base].z;

    float dist    = abs(TexCoord.x - center);
    float spot    = smoothstep(halfW, 0.0, dist);
    spot          = spot * spot;

    float alpha = spot * intensity * VertexColor.a;
    if (alpha < 0.001) discard;

    OutColor = vec4(VertexColor.rgb, alpha) * ColorModulator;
}
