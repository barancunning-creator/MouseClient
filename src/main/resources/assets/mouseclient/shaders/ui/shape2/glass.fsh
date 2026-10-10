#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0; // размытый фон (сэмплим по screenUv)
uniform sampler2D Sampler1; // LUT кривой затухания тени (Nx1), сэмплим по X в [0,1]

in vec4 tintColor;
in vec2 quadCoord;
in float radiusData;
in float tintAmount;
in vec3 shadowColor;
in float shadowStrength;
in float spreadPx;
in vec2 screenUv;

out vec4 fragColor;

const float SQUIRCLE_N = 4.0;

float roundedBox(vec2 pos, vec2 b, float rSigned) {
    float rVal = abs(rSigned);
    vec2 q = abs(pos) - b + rVal;
    float qMax = max(q.x, q.y);
    vec2 qClamped = max(q, vec2(0.0));
    float corner = rSigned < 0.0
        ? pow(pow(qClamped.x, SQUIRCLE_N) + pow(qClamped.y, SQUIRCLE_N), 1.0 / SQUIRCLE_N)
        : length(qClamped);
    return min(qMax, 0.0) + corner - rVal;
}

void main() {
    vec2 uvWidth = max(fwidth(quadCoord), vec2(0.000001));
    vec2 rectSize = 1.0 / uvWidth;
    vec2 halfSize = rectSize * 0.5;
    vec2 pos = quadCoord * rectSize - halfSize;

    float spread = max(spreadPx, 0.0001);
    float d = roundedBox(pos, halfSize - spread, radiusData);
    float aa = fwidth(d) * 0.75;
    float insideCoverage = 1.0 - smoothstep(-aa, aa, d);

    vec4 blur = texture(Sampler0, screenUv);
    vec3 tinted = mix(blur.rgb, tintColor.rgb, clamp(tintAmount, 0.0, 1.0));
    float blurAlpha = blur.a * insideCoverage * tintColor.a;

    float t = clamp(d / spread, 0.0, 1.0);
    float falloff = texture(Sampler1, vec2(1.0 - t, 0.5)).r;
    float contact = smoothstep(-2.0 * aa, 0.0, d);
    float shadowAlpha = shadowStrength * min(contact, falloff);

    float outAlpha = blurAlpha + shadowAlpha * (1.0 - blurAlpha);
    if (outAlpha <= 0.0005) discard;
    vec3 outRgb = (tinted * blurAlpha + shadowColor * shadowAlpha * (1.0 - blurAlpha)) / outAlpha;

    fragColor = vec4(outRgb, outAlpha) * ColorModulator;
    if (fragColor.a <= 0.0005) discard;
}
