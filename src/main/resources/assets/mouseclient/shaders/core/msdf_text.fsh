#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 TexCoord;
in vec4 VertexColor;
in float PxRange;

out vec4 OutColor;

float median(vec3 m) {
    return max(min(m.r, m.g), min(max(m.r, m.g), m.b));
}

float screenPxRange(vec2 uv) {
    vec2 unitRange = vec2(max(PxRange, 1.0)) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(uv);
    return max(0.5 * dot(unitRange, screenTexSize), 1.0);
}

float coverageAt(vec2 uv, float spr, float thicken) {
    float sd = median(texture(Sampler0, uv).rgb) - 0.5 + thicken;
    return clamp(sd * spr + 0.5, 0.0, 1.0);
}

void main() {
    float spr = screenPxRange(TexCoord);

    float thicken = clamp((2.0 - spr) * 0.12, 0.0, 0.08);

    vec2 dx = dFdx(TexCoord);
    vec2 dy = dFdy(TexCoord);
    vec2 o1 = 0.25 * (dx + dy);
    vec2 o2 = 0.25 * (dx - dy);
    float sprTap = spr * 2.0;

    float coverage = 0.25 * (
        coverageAt(TexCoord + o1, sprTap, thicken) +
        coverageAt(TexCoord - o1, sprTap, thicken) +
        coverageAt(TexCoord + o2, sprTap, thicken) +
        coverageAt(TexCoord - o2, sprTap, thicken)
    );

    float alpha = coverage * VertexColor.a;
    if (alpha <= 0.0015) {
        discard;
    }

    OutColor = vec4(VertexColor.rgb, alpha) * ColorModulator;
}
