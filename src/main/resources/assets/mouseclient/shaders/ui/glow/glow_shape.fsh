#version 150

#moj_import <mouseclient:releon_common.glsl>

in vec2 FragCoord;

layout(std140) uniform GlowParamsArray {

    vec4 params[78];
};

layout(std140) uniform SplitParams {
    vec4 splitData[48];
};

out vec4 OutColor;

vec3 glowPaletteColorAt(int idx) {
    if (idx <= 0) return params[71].rgb;
    if (idx == 1) return params[72].rgb;
    if (idx == 2) return params[73].rgb;
    if (idx == 3) return params[74].rgb;
    if (idx == 4) return params[75].rgb;
    return params[76].rgb;
}

vec3 glowPaletteRamp(float t) {
    int count = int(params[70].x + 0.5);
    if (count <= 1) {
        return params[71].rgb;
    }
    float f = clamp(t, 0.0, 1.0) * float(count - 1);
    int i = clamp(int(floor(f)), 0, count - 1);
    int j = min(i + 1, count - 1);
    float frac = clamp(f - float(i), 0.0, 1.0);
    frac = frac * frac * (3.0 - 2.0 * frac);
    return mix(glowPaletteColorAt(i), glowPaletteColorAt(j), frac);
}

vec3 glowPaletteLoop(float t) {
    int count = int(params[70].x + 0.5);
    if (count <= 1) {
        return params[71].rgb;
    }
    float f = fract(t) * float(count);
    int i1 = clamp(int(floor(f)), 0, count - 1);
    float u = clamp(f - float(i1), 0.0, 1.0);

    int i0 = i1 - 1; if (i0 < 0) i0 += count;
    int i2 = i1 + 1; if (i2 >= count) i2 -= count;
    int i3 = i1 + 2; if (i3 >= count) i3 -= count;

    vec3 c0 = glowPaletteColorAt(i0);
    vec3 c1 = glowPaletteColorAt(i1);
    vec3 c2 = glowPaletteColorAt(i2);
    vec3 c3 = glowPaletteColorAt(i3);

    float u2 = u * u;
    float u3 = u2 * u;
    vec3 cyclicCol = 0.5 * ((2.0 * c1)
                      + (-c0 + c2) * u
                      + (2.0 * c0 - 5.0 * c1 + 4.0 * c2 - c3) * u2
                      + (-c0 + 3.0 * c1 - 3.0 * c2 + c3) * u3);

    float tri = 0.5 - 0.5 * cos(6.2831853 * fract(t));
    vec3 mirrorCol = glowPaletteRamp(tri);

    float closed = clamp(params[77].y, 0.0, 1.0);
    return clamp(mix(mirrorCol, cyclicCol, closed), 0.0, 1.0);
}

vec3 glowBox(vec2 uv, float phase) {
    vec3 tl = glowPaletteLoop(phase);
    vec3 tr = glowPaletteLoop(phase + 0.25);
    vec3 br = glowPaletteLoop(phase + 0.5);
    vec3 bl = glowPaletteLoop(phase + 0.75);
    vec3 top = mix(tl, tr, uv.x);
    vec3 bot = mix(bl, br, uv.x);
    return mix(top, bot, uv.y);
}

vec3 glowSelect(float id, vec3 ramp, vec3 mesh, vec3 box) {
    int s = int(id + 0.5);
    if (s == 1) return mesh;
    if (s == 2) return box;
    return ramp;
}

vec3 glowMesh(vec2 uv, float phase, float aspect) {
    float a = 6.2831853 * phase;
    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float kx = mod(fi, 2.0) < 0.5 ? 1.0 : 2.0;
        float ky = mod(fi, 2.0) < 0.5 ? 2.0 : 1.0;
        vec2 pos = vec2(0.5) + 0.34 * vec2(sin(a * kx + fi * 2.3999), cos(a * ky - fi * 1.618));
        vec2 d = (uv - pos) * vec2(aspect, 1.0);
        float w = 1.0 / (dot(d, d) * 5.0 + 0.06);
        sum += glowPaletteRamp(fi / 5.0) * w;
        wsum += w;
    }
    return sum / wsum;
}

vec4 getSegment_glow(int i) {
    vec4 span = params[6 + i];
    return vec4(span.x, span.z, span.y - span.x, span.w - span.z);
}

float roundedBoxSDF_glow(vec2 center, vec2 halfSize, vec4 r) {
    r.xy = (center.x > 0.0) ? r.xy : r.zw;
    r.x  = (center.y > 0.0) ? r.x  : r.y;
    vec2 q = abs(center) - halfSize + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

vec4 resolveSegmentRadius_glow(int index, vec4 seg, int count, float r,
                               float leftAligned, float bottomAnchored) {
    float rowX      = seg.x;
    float rowRightX = seg.x + seg.z;

    if (leftAligned > 0.5) {
        if (bottomAnchored > 0.5) {
            float topLeft     = (index == 0)         ? r : 0.0;
            float bottomLeft  = (index == count - 1) ? r : 0.0;
            float topRight    = r;
            float bottomRight = (index == count - 1) ? r : 0.0;
            if (index > 0) {
                vec4 prev = getSegment_glow(index - 1);
                float prevRightX = prev.x + prev.z;
                if (rowRightX >= prevRightX) topRight = min(topRight, rowRightX - prevRightX);
            }
            if (index < count - 1) {
                vec4 next = getSegment_glow(index + 1);
                float nextRightX = next.x + next.z;
                if (nextRightX >= rowRightX) bottomRight = min(bottomRight, nextRightX - rowRightX);
            }
            return vec4(topRight, bottomRight, topLeft, bottomLeft);
        }
        float topLeft     = (index == 0)         ? r : 0.0;
        float bottomLeft  = (index == count - 1) ? r : 0.0;
        float topRight    = (index == 0)         ? r : 0.0;
        float bottomRight = r;
        if (index > 0) {
            vec4 prev = getSegment_glow(index - 1);
            float prevRightX = prev.x + prev.z;
            if (rowRightX <= prevRightX) topRight = min(topRight, prevRightX - rowRightX);
        }
        if (index < count - 1) {
            vec4 next = getSegment_glow(index + 1);
            float nextRightX = next.x + next.z;
            if (nextRightX <= rowRightX) bottomRight = min(bottomRight, rowRightX - nextRightX);
        }
        return vec4(topRight, bottomRight, topLeft, bottomLeft);
    }

    if (bottomAnchored > 0.5) {
        float topRight    = (index == 0)         ? r : 0.0;
        float bottomRight = (index == count - 1) ? r : 0.0;
        float topLeft     = r;
        float bottomLeft  = (index == count - 1) ? r : 0.0;
        if (index > 0) {
            vec4 prev = getSegment_glow(index - 1);
            if (rowX <= prev.x) topLeft = min(topLeft, prev.x - rowX);
        }
        if (index < count - 1) {
            vec4 next = getSegment_glow(index + 1);
            if (next.x <= rowX) bottomLeft = min(bottomLeft, rowX - next.x);
        }
        return vec4(topRight, bottomRight, topLeft, bottomLeft);
    }

    float topRight    = (index == 0)         ? r : 0.0;
    float bottomRight = (index == count - 1) ? r : 0.0;
    float topLeft     = (index == 0)         ? r : 0.0;
    float bottomLeft  = r;
    if (index > 0) {
        vec4 prev = getSegment_glow(index - 1);
        if (rowX >= prev.x) topLeft = min(topLeft, rowX - prev.x);
    }
    if (index < count - 1) {
        vec4 next = getSegment_glow(index + 1);
        if (next.x >= rowX) bottomLeft = min(bottomLeft, next.x - rowX);
    }
    return vec4(topRight, bottomRight, topLeft, bottomLeft);
}

float boxesSDF(vec2 p, float spanCountF, float innerRadius, out vec2 boxUV) {
    float minDist = 1e9;
    boxUV = vec2(0.5);
    int count = int(spanCountF + 0.5);
    for (int i = 0; i < 64; i++) {
        if (i >= count) break;
        vec4 seg       = getSegment_glow(i);

        vec2 fullHalf  = max(seg.zw * 0.5, vec2(0.001));
        vec2 segCenter = seg.xy + fullHalf;
        vec2 halfSize  = max(fullHalf - 1.0, vec2(0.001));
        float rad = min(innerRadius, min(halfSize.x, halfSize.y));
        vec2 center = p - segCenter;
        center.y = -center.y;
        float d = roundedBoxSDF_glow(center, halfSize, vec4(rad));
        if (d < minDist) {
            minDist = d;
            boxUV = clamp((p - seg.xy) / max(seg.zw, vec2(1.0)), vec2(0.0), vec2(1.0));
        }
    }
    return minDist;
}

float staircaseSDF(vec2 p, float spanCountF, float innerRadius,
                   float leftAligned, float bottomAnchored) {
    float minDist = 1e9;
    int count = int(spanCountF + 0.5);
    for (int i = 0; i < 64; i++) {
        if (i >= count) break;
        vec4 seg       = getSegment_glow(i);

        vec2 fullHalf  = max(seg.zw * 0.5, vec2(0.001));
        vec2 segCenter = seg.xy + fullHalf;
        vec2 halfSize  = max(fullHalf - 1.0, vec2(0.001));
        float rad = min(innerRadius, min(halfSize.x, halfSize.y));
        vec4 r = resolveSegmentRadius_glow(i, seg, count, rad, leftAligned, bottomAnchored);

        vec2 center = p - segCenter;
        center.y = -center.y;
        float d = roundedBoxSDF_glow(center, halfSize, r);
        minDist = min(minDist, d);
    }
    return minDist;
}

void main() {

    vec4 radius   = max(params[0], vec4(0.0));
    vec4 sizeData = params[1];
    vec4 colorVec = params[2];

    vec2 rectSize = max(sizeData.xy, vec2(1.0));
    float pad = max(sizeData.z, 0.0);
    float smoothness = max(sizeData.w, 0.5);

    vec2 targetSize = rectSize + vec2(pad * 2.0);
    vec2 pixel = FragCoord * targetSize;

    float spanCount    = params[5].x;
    float innerRadius  = params[5].y;
    float leftAligned  = params[5].z;
    float bottomAnchored = params[5].w;

    float rawSplit = params[3].x;
    bool glowCut = rawSplit < -0.5;
    int splitIdx = int(abs(rawSplit) + 0.5) - 1;
    float parentDist = -1e9;

    float dist;
    bool boxesMode = false;
    vec2 boxUV = vec2(0.5);
    if (splitIdx >= 0) {

        vec2 local = pixel - vec2(pad);
        local.y = rectSize.y - local.y;
        vec4 pr = splitData[splitIdx * 3];
        vec4 cr = splitData[splitIdx * 3 + 1];
        vec4 sm = splitData[splitIdx * 3 + 2];
        float dP = rdist(pr.xy - local, max(pr.zw - 1.0, vec2(0.0)), radius);
        float dC = rdist(cr.xy - local, max(cr.zw - 1.0, vec2(0.0)), vec4(max(sm.x, 0.0)));
        dist = rsmin(dP, dC, max(sm.y, 0.0));
        parentDist = dP;
    } else if (spanCount >= 0.5) {

        if (leftAligned < -0.5) {
            boxesMode = true;
            dist = boxesSDF(pixel, spanCount, innerRadius, boxUV);
        } else {
            dist = staircaseSDF(pixel, spanCount, innerRadius, leftAligned, bottomAnchored);
        }
    } else {

        vec2 pos = pixel - targetSize * 0.5;
        vec2 halfSize = max(rectSize * 0.5 - 1.0, vec2(0.0));
        dist = rdist(pos, halfSize, radius);
    }

    float a = 1.0 - smoothstep(-smoothness, 0.0, dist);

    if (glowCut) {
        a *= smoothstep(-1.0, 3.0, parentDist);
    }
    if (a < 0.001) discard;

    vec4 secondData = params[4];

    vec2 shapeUV = boxesMode
        ? boxUV
        : clamp((pixel - vec2(pad)) / max(rectSize, vec2(1.0)), vec2(0.0), vec2(1.0));

    shapeUV.y = 1.0 - shapeUV.y;

    vec3 mixedColor;
    if (int(params[70].x + 0.5) >= 2) {
        float tri = abs(fract(shapeUV.x + fract(secondData.w)) * 2.0 - 1.0);

        vec3 rampCol = glowPaletteRamp(smoothstep(0.0, 1.0, tri)) * 0.86;
        vec3 meshCol = glowMesh(shapeUV, params[70].y, rectSize.x / rectSize.y);
        vec3 boxCol = glowBox(shapeUV, params[70].y * 20.0);
        vec3 targetCol = glowSelect(params[70].z, rampCol, meshCol, boxCol);

        float sweep = params[70].w;
        if (sweep >= 0.0) {
            vec3 prevCol = glowSelect(params[77].x, rampCol, meshCol, boxCol);
            float axis = shapeUV.x + (shapeUV.y - 0.5) * 0.30;
            float head = sweep * 1.5 - 0.25;
            float wipe = 1.0 - smoothstep(head - 0.04, head + 0.04, axis);
            mixedColor = mix(prevCol, targetCol, wipe);
        } else {
            mixedColor = targetCol;
        }
    } else {
        mixedColor = colorVec.rgb;
    }

    OutColor = vec4(mixedColor, colorVec.a * a);
}
