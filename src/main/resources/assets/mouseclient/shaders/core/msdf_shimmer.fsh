#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2  TexCoord;

in vec4  VertexColor;

in float LinePos;

out vec4 OutColor;

const float DISTANCE_RANGE = 6.0;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

float screenPxRange() {
    vec2 unitRange     = vec2(DISTANCE_RANGE) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(TexCoord);
    return max(0.5 * dot(unitRange, screenTexSize), 1.0);
}

float coverageAt(vec2 uv, float spr) {
    vec3 msd = texture(Sampler0, uv).rgb;
    float sd = median(msd.r, msd.g, msd.b);
    return clamp((sd - 0.5) * spr + 0.5, 0.0, 1.0);
}

void main() {

    float spr    = screenPxRange();
    float sprTap = spr * 2.0;
    vec2  dx = dFdx(TexCoord);
    vec2  dy = dFdy(TexCoord);
    vec2  o1 = 0.25 * (dx + dy);
    vec2  o2 = 0.25 * (dx - dy);
    float coverage = 0.25 * (
        coverageAt(TexCoord + o1, sprTap) +
        coverageAt(TexCoord - o1, sprTap) +
        coverageAt(TexCoord + o2, sprTap) +
        coverageAt(TexCoord - o2, sprTap)
    );

    float baseOpacity   = VertexColor.a;
    float progress      = VertexColor.r * 2.0 - 0.5;
    float halfW         = VertexColor.g;
    float shimIntensity = VertexColor.b;

    float packedLine = LinePos;
    float linePos = mod(packedLine, 64.0) / 63.0;
    float tintB   = mod(floor(packedLine / 64.0), 64.0) / 63.0;
    float tintG   = mod(floor(packedLine / 4096.0), 64.0) / 63.0;
    float tintR   = floor(packedLine / 262144.0) / 63.0;

    float dist  = abs(linePos - progress);
    float spot  = smoothstep(halfW, 0.0, dist);
    spot        = spot * spot;

    float alpha = coverage * spot * shimIntensity * baseOpacity;
    if (alpha <= 0.001) discard;

    OutColor = vec4(tintR, tintG, tintB, alpha) * ColorModulator;
}
