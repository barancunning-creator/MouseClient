#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 quadCoord;
in vec4 radiusData;
in vec2 smoothnessData;
in float valueData;
in float typeData;
in vec2 screenUv;

out vec4 fragColor;

const float SQUIRCLE_N = 4.0;

float roundedBox(vec2 pos, vec2 b, vec4 r) {
    vec2 signPos = step(vec2(0.0), pos);
    vec2 radius = mix(r.zw, r.xy, signPos.x);
    float rSigned = radius.y * (1.0 - signPos.y) + radius.x * signPos.y;
    float rVal = abs(rSigned);
    vec2 q = abs(pos) - b + rVal;
    float qMax = max(q.x, q.y);
    vec2 qClamped = max(q, vec2(0.0));
    float corner = rSigned < 0.0
        ? pow(pow(qClamped.x, SQUIRCLE_N) + pow(qClamped.y, SQUIRCLE_N), 1.0 / SQUIRCLE_N)
        : length(qClamped);
    return min(qMax, 0.0) + corner - rVal;
}

float smootherEdge(float lowerBound, float upperBound, float x) {
    float t = clamp((x - lowerBound) / (upperBound - lowerBound), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

void main() {
    vec4 color = vertexColor * ColorModulator;
    vec2 textureSizePx = vec2(textureSize(Sampler0, 0));

    if (typeData > 0.25 && typeData < 0.75) {
        vec2 regionUvWidth = max(fwidth(quadCoord), vec2(0.000001));
        vec2 regionRectSize = 1.0 / regionUvWidth;
        vec2 regionHalfSize = regionRectSize * 0.5;
        vec2 regionPos = quadCoord * regionRectSize - regionHalfSize;
        float regionDist = roundedBox(regionPos, regionHalfSize, vec4(valueData));
        float regionAa = fwidth(regionDist) * 0.75;
        float regionAlpha = (1.0 - smoothstep(-regionAa, regionAa, regionDist)) * color.a;
        vec4 regionColor = texture(Sampler0, mix(radiusData.xy, radiusData.zw, quadCoord));
        fragColor = vec4(regionColor.rgb * color.rgb, regionColor.a * regionAlpha);
        if (fragColor.a <= 0.0005) discard;
        return;
    }

    if (typeData > 0.5) {
        vec4 texColor = texture(Sampler0, quadCoord);
        vec2 deriv = vec2(dFdx(quadCoord.x) * textureSizePx.x, dFdy(quadCoord.y) * textureSizePx.y);
        float derivatives = dot(deriv, deriv);
        float toPixels = derivatives > 0.000001 ? valueData * inversesqrt(derivatives) : valueData;
        toPixels = max(toPixels * 1.1, 1.0);
        float signedDistance = texColor.a - 0.5;
        float iconAlpha = smootherEdge(smoothnessData.x, smoothnessData.y, signedDistance * toPixels);
        fragColor = vec4(color.rgb, color.a * iconAlpha);
        if (fragColor.a <= 0.0005) discard;
        return;
    }

    vec2 uvWidth = max(fwidth(quadCoord), vec2(0.000001));
    vec2 rectSize = 1.0 / uvWidth;
    vec2 halfSize = rectSize * 0.5;
    vec2 pos = quadCoord * rectSize - halfSize;
    float inset = typeData > -0.5 ? 0.0 : valueData;
    float d = roundedBox(pos, halfSize - inset, radiusData);
    float aa = fwidth(d) * 0.75;
    float shapeAlpha = (1.0 - smoothstep(-aa, aa, d)) * color.a;

    if (typeData > -0.5) {
        vec4 blurColor = texture(Sampler0, screenUv);
        vec3 tinted = mix(blurColor.rgb, color.rgb, clamp(valueData, 0.0, 1.0));
        fragColor = vec4(tinted, blurColor.a * shapeAlpha);
    } else {
        vec4 texColor = texture(Sampler0, quadCoord);
        fragColor = vec4(texColor.rgb * color.rgb, texColor.a * shapeAlpha);
    }

    if (fragColor.a <= 0.0005) discard;
}
