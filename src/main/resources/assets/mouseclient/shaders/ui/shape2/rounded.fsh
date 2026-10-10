#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 quadCoord;
in vec4 radiusData;
in float paramData;
in float typeData;

out vec4 fragColor;

const float SQUIRCLE_N = 4.0;

const float OUTLINE_EDGE_PAD = 1.0;

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

vec3 linearToSrgb(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}

void main() {
    vec2 uvWidth = max(fwidth(quadCoord), vec2(0.000001));
    vec2 rectSize = 1.0 / uvWidth;
    vec2 halfSize = rectSize * 0.5;
    vec2 pos = quadCoord * rectSize - halfSize;

    bool hsvMode = typeData > -0.75 && typeData < -0.25;
    bool shadow = typeData > -0.25 && typeData < 0.5;
    bool hollowShadow = typeData > 0.15 && typeData < 0.45;
    bool outline = typeData >= 0.5;
    bool outlineEdge = typeData >= 1.5;

    vec4 color;
    if (hsvMode) {
        vec2 uv = clamp(quadCoord, vec2(0.0), vec2(1.0));
        vec3 hue = linearToSrgb(vertexColor.rgb);
        vec3 rgb = mix(vec3(1.0), hue, uv.x) * (1.0 - uv.y);
        color = vec4(rgb, vertexColor.a) * ColorModulator;
    } else {
        color = vec4(linearToSrgb(vertexColor.rgb), vertexColor.a) * ColorModulator;
    }
    float value = shadow ? paramData : 0.0;
    float thickness = outline ? paramData : 0.0;

    float d = roundedBox(pos, halfSize - value, radiusData);
    float aa = fwidth(d) * 0.75;

    if (outline) {
        float w = max(length(vec2(dFdx(d), dFdy(d))), 1e-6);
        float centerOffset = outlineEdge ? OUTLINE_EDGE_PAD + thickness * 0.5 : thickness * 0.5;
        float stroke = abs(d + centerOffset) - thickness * 0.5;
        fragColor = vec4(color.rgb, color.a * clamp(0.5 - stroke / w, 0.0, 1.0));
    } else if (hollowShadow) {
        float sigma = max(paramData, 0.0001) * 0.3333333;
        float t = max(d, 0.0) / sigma;
        float outerGlow = exp(-0.5 * t * t);
        float insideMask = smoothstep(-2.0 * aa, 0.0, d);
        float dither = (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
        fragColor = vec4(color.rgb, (color.a * outerGlow + dither) * insideMask);
    } else if (shadow) {
        float shadowAlpha = 1.0 - smoothstep(0.0, paramData, d);
        fragColor = vec4(color.rgb, color.a * shadowAlpha * shadowAlpha);
    } else {
        float alpha = 1.0 - smoothstep(-aa, aa, d);
        fragColor = vec4(color.rgb, color.a * alpha);
    }

    if (fragColor.a <= 0.0005) discard;
}
