#version 150

#moj_import <mouseclient:common.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

const int MAX_OUTLINE_360_RANGES = 1024;

layout(std140) uniform Outline360ParamsArray {
    vec4 params[1024];
};

layout(std140) uniform Outline360RangesArray {
    vec4 ranges[2048];
};

out vec4 OutColor;

float outlineAlpha(vec2 coord, vec2 size, vec4 radius, float thickness, float smoothness) {

    vec2 safeSize = max(size, vec2(1.0));
    vec2 halfSize = safeSize * 0.5;
    vec2 pos = halfSize - (coord * safeSize);
    float maxRadius = min(halfSize.x, halfSize.y);
    vec4 safeRadius = clamp(max(radius, vec4(0.0)), vec4(0.0), vec4(maxRadius));

    float dist = rdist(pos, halfSize, safeRadius);
    float t = max(thickness, 0.0);
    float band = abs(dist + t * 0.5) - t * 0.5;
    float feather = fwidth(dist) * max(smoothness, 0.5);
    return 1.0 - smoothstep(-feather, feather, band);
}

float contourAngleDegrees(vec2 coord, vec2 size, vec4 radius, float thickness, float offsetDegrees) {
    vec2 local = coord * size;
    float halfThickness = thickness * 0.5;
    vec2 samplePoint = local;

    if (radius.y > 0.0 && local.x >= size.x - radius.y && local.y <= radius.y) {
        vec2 center = vec2(size.x - radius.y, radius.y);
        vec2 dir = local - center;
        samplePoint = center + normalize(dir + vec2(0.0001, -0.0001)) * max(radius.y - halfThickness, 0.0);
    } else if (radius.x > 0.0 && local.x <= radius.x && local.y <= radius.x) {
        vec2 center = vec2(radius.x, radius.x);
        vec2 dir = local - center;
        samplePoint = center + normalize(dir + vec2(-0.0001, -0.0001)) * max(radius.x - halfThickness, 0.0);
    } else if (radius.w > 0.0 && local.x <= radius.w && local.y >= size.y - radius.w) {
        vec2 center = vec2(radius.w, size.y - radius.w);
        vec2 dir = local - center;
        samplePoint = center + normalize(dir + vec2(-0.0001, 0.0001)) * max(radius.w - halfThickness, 0.0);
    } else if (radius.z > 0.0 && local.x >= size.x - radius.z && local.y >= size.y - radius.z) {
        vec2 center = vec2(size.x - radius.z, size.y - radius.z);
        vec2 dir = local - center;
        samplePoint = center + normalize(dir + vec2(0.0001, 0.0001)) * max(radius.z - halfThickness, 0.0);
    } else {
        float left = local.x;
        float right = size.x - local.x;
        float top = local.y;
        float bottom = size.y - local.y;
        float nearest = min(min(left, right), min(top, bottom));

        if (nearest == top) {
            samplePoint.y = halfThickness;
        } else if (nearest == right) {
            samplePoint.x = size.x - halfThickness;
        } else if (nearest == bottom) {
            samplePoint.y = size.y - halfThickness;
        } else {
            samplePoint.x = halfThickness;
        }
    }

    vec2 p = samplePoint - size * 0.5;
    float angle = degrees(atan(-p.y, p.x)) + offsetDegrees;
    return mod(angle + 360.0, 360.0);
}

float rangeFactor(float angle, float startDegrees, float endDegrees, float blendDegrees) {
    float start = mod(startDegrees + 360.0, 360.0);
    float finish = mod(endDegrees + 360.0, 360.0);
    float span = mod(finish - start + 360.0, 360.0);
    if (span <= 0.001 || span >= 359.999) {
        return 1.0;
    }

    float delta = mod(angle - start + 360.0, 360.0);
    if (delta > span) {
        return 0.0;
    }

    float blend = min(max(blendDegrees, 0.001), span * 0.5);
    float enter = smoothstep(0.0, blend, delta);
    float exit = smoothstep(0.0, blend, span - delta);
    return enter * exit;
}

vec4 color360(float angle, vec4 baseColor, int rangeOffset, int rangeCount, float blendDegrees) {
    vec4 color = baseColor;
    for (int i = 0; i < MAX_OUTLINE_360_RANGES; i++) {
        if (i >= rangeCount) {
            break;
        }

        int idx = (rangeOffset + i) * 2;
        vec4 range = ranges[idx];
        vec4 rangeColor = ranges[idx + 1];
        float factor = rangeFactor(angle, range.x, range.y, blendDegrees);
        color = mix(color, rangeColor, factor);
    }
    return color;
}

void main() {
    int base = QuadIndex * 4;
    vec4 radius = params[base];
    vec4 sizeThicknessSmooth = params[base + 1];
    vec4 defaultColor = params[base + 2];
    vec4 rangeInfo = params[base + 3];

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 size = max(sizeThicknessSmooth.xy, vec2(1.0));
    float thickness = max(sizeThicknessSmooth.z, 0.0);
    float alpha = outlineAlpha(coord, size, radius, thickness, sizeThicknessSmooth.w);
    float angle = contourAngleDegrees(coord, size, radius, thickness, rangeInfo.w);

    int rangeOffset = int(rangeInfo.x + 0.5);
    int rangeCount = int(rangeInfo.y + 0.5);
    vec4 color = color360(angle, defaultColor, rangeOffset, rangeCount, rangeInfo.z);
    color.a *= alpha;

    if (color.a <= 0.001) {
        discard;
    }

    OutColor = color;
}
