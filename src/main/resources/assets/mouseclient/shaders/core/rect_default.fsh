#version 150

#moj_import <mouseclient:common.glsl>

in vec2 FragCoord;
in vec2 ScreenPos;
flat in int QuadIndex;

layout(std140) uniform RectangleParamsArray {
    vec4 params[4096];
};

layout(std140) uniform RectangleParamsArray2 {
    vec4 params2[4096];
};

vec4 rectParam(int idx) {
    return idx < 4096 ? params[idx] : params2[idx - 4096];
}

layout(std140) uniform PaletteParams {
    vec4 paletteMeta;
    vec4 paletteColors[6];
    vec4 paletteMeta2;
};

out vec4 OutColor;

vec3 paletteColorAt(int idx) {
    if (idx <= 0) return paletteColors[0].rgb;
    if (idx == 1) return paletteColors[1].rgb;
    if (idx == 2) return paletteColors[2].rgb;
    if (idx == 3) return paletteColors[3].rgb;
    if (idx == 4) return paletteColors[4].rgb;
    return paletteColors[5].rgb;
}

vec3 paletteRamp(float t) {
    int count = int(paletteMeta.x + 0.5);
    if (count <= 1) {
        return paletteColors[0].rgb;
    }
    float f = clamp(t, 0.0, 1.0) * float(count - 1);
    int i = clamp(int(floor(f)), 0, count - 1);
    int j = min(i + 1, count - 1);
    float frac = clamp(f - float(i), 0.0, 1.0);
    frac = frac * frac * (3.0 - 2.0 * frac);
    return mix(paletteColorAt(i), paletteColorAt(j), frac);
}

float roundedAlpha(vec2 coord, vec2 size, vec4 radius, float smoothness) {
    return ralpha(size, coord, radius, smoothness);
}

vec4 rectangleColor(vec2 coord, vec4 topLeft, vec4 topRight, vec4 bottomRight, vec4 bottomLeft) {
    vec4 top = mix(topLeft, topRight, coord.x);
    vec4 bottom = mix(bottomLeft, bottomRight, coord.x);
    return mix(top, bottom, coord.y);
}

void main() {
    int base = QuadIndex * 8;
    vec4 radius = rectParam(base);
    vec4 sizeSmooth = rectParam(base + 1);
    vec4 topLeft = rectParam(base + 2);
    vec4 topRight = rectParam(base + 3);
    vec4 bottomRight = rectParam(base + 4);
    vec4 bottomLeft = rectParam(base + 5);
    vec4 scissorPosSize = rectParam(base + 6);
    vec4 scissorRadius = rectParam(base + 7);

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 size = max(sizeSmooth.xy, vec2(1.0));
    float alpha = roundedAlpha(coord, size, max(radius, vec4(0.0)), sizeSmooth.z);

    int paletteMode = int(sizeSmooth.w + 0.5);
    vec4 color;
    if (paletteMode > 0) {

        float t = (paletteMode == 1) ? coord.y : coord.x;
        color = vec4(paletteRamp(t) * topLeft.x, topLeft.y);
    } else {
        color = rectangleColor(coord, topLeft, topRight, bottomRight, bottomLeft);
    }
    color.a *= alpha;

    if (scissorPosSize.z > 0.0 && scissorPosSize.w > 0.0) {
        vec2 scissorCoord = (gl_FragCoord.xy - scissorPosSize.xy) / scissorPosSize.zw;
        float scissorAlpha = ralpha(scissorPosSize.zw, scissorCoord, max(scissorRadius, vec4(0.0)), 1.5);
        color.a *= scissorAlpha;
    }

    if (color.a <= 0.001) {
        discard;
    }

    OutColor = color;
}
