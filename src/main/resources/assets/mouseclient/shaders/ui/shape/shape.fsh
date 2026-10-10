#version 150

#moj_import <mouseclient:releon_common.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

uniform sampler2D Sampler0;

layout(std140) uniform ShapeParamsArray {
    vec4 params[1168];
};

layout(std140) uniform PaletteParams {
    vec4 paletteMeta;
    vec4 paletteColors[6];
    vec4 paletteMeta2;
};

layout(std140) uniform SplitParams {
    vec4 splitData[48];
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

vec3 paletteLoop(float t) {
    int count = int(paletteMeta.x + 0.5);
    if (count <= 1) {
        return paletteColors[0].rgb;
    }
    float f = fract(t) * float(count);
    int i1 = clamp(int(floor(f)), 0, count - 1);
    float u = clamp(f - float(i1), 0.0, 1.0);

    int i0 = i1 - 1; if (i0 < 0) i0 += count;
    int i2 = i1 + 1; if (i2 >= count) i2 -= count;
    int i3 = i1 + 2; if (i3 >= count) i3 -= count;

    vec3 c0 = paletteColorAt(i0);
    vec3 c1 = paletteColorAt(i1);
    vec3 c2 = paletteColorAt(i2);
    vec3 c3 = paletteColorAt(i3);

    float u2 = u * u;
    float u3 = u2 * u;
    vec3 cyclicCol = 0.5 * ((2.0 * c1)
                      + (-c0 + c2) * u
                      + (2.0 * c0 - 5.0 * c1 + 4.0 * c2 - c3) * u2
                      + (-c0 + 3.0 * c1 - 3.0 * c2 + c3) * u3);

    float tri = 0.5 - 0.5 * cos(6.2831853 * fract(t));
    vec3 mirrorCol = paletteRamp(tri);

    float closed = clamp(paletteMeta2.y, 0.0, 1.0);
    return clamp(mix(mirrorCol, cyclicCol, closed), 0.0, 1.0);
}

vec3 boxGradient(vec2 uv, float phase) {
    vec3 tl = paletteLoop(phase);
    vec3 tr = paletteLoop(phase + 0.25);
    vec3 br = paletteLoop(phase + 0.5);
    vec3 bl = paletteLoop(phase + 0.75);
    vec3 top = mix(tl, tr, uv.x);
    vec3 bot = mix(bl, br, uv.x);
    return mix(top, bot, uv.y);
}

vec3 selectStyle(float id, vec3 ramp, vec3 mesh, vec3 box) {
    int s = int(id + 0.5);
    if (s == 1) return mesh;
    if (s == 2) return box;
    return ramp;
}

vec3 meshGradient(vec2 uv, float phase, float aspect) {
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
        sum += paletteRamp(fi / 5.0) * w;
        wsum += w;
    }
    return sum / wsum;
}

float gradientBeam(vec2 uv, float sweep) {
    if (sweep < 0.0) return 0.0;
    float axis = uv.x + (uv.y - 0.5) * 0.30;
    float head = sweep * 1.5 - 0.25;
    float band = smoothstep(0.13, 0.0, abs(axis - head));
    float env = smoothstep(0.0, 0.12, sweep) * smoothstep(1.0, 0.85, sweep);
    return band * env;
}

vec3 hue2rgb(float h) {
    h = fract(h) * 6.0;
    vec3 rgb = clamp(vec3(abs(h - 3.0) - 1.0, 2.0 - abs(h - 2.0), 2.0 - abs(h - 4.0)), 0.0, 1.0);
    return rgb;
}

vec3 rainbowColor(float along, float phase, float spread, float sat) {
    float hue = phase + along * spread;
    vec3 rgb = hue2rgb(hue);
    return mix(vec3(1.0), rgb, clamp(sat, 0.0, 1.0));
}

vec4 getSegment(int base, int i) {
    vec4 span = params[base + 9 + i];
    return vec4(span.x, span.z, span.y - span.x, span.w - span.z);
}

float roundedBoxSDF(vec2 center, vec2 halfSize, vec4 r) {
    r.xy = (center.x > 0.0) ? r.xy : r.zw;
    r.x  = (center.y > 0.0) ? r.x  : r.y;
    vec2 q = abs(center) - halfSize + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

vec4 resolveSegmentRadius(int index, vec4 seg, int count, float r,
                          float leftAligned, float bottomAnchored, int base) {
    float rowX      = seg.x;
    float rowRightX = seg.x + seg.z;

    if (leftAligned > 0.5) {
        if (bottomAnchored > 0.5) {
            float topLeft     = (index == 0)         ? r : 0.0;
            float bottomLeft  = (index == count - 1) ? r : 0.0;
            float topRight    = r;
            float bottomRight = (index == count - 1) ? r : 0.0;
            if (index > 0) {
                vec4 prev = getSegment(base, index - 1);
                float prevRightX = prev.x + prev.z;
                if (rowRightX >= prevRightX) topRight = min(topRight, rowRightX - prevRightX);
            }
            if (index < count - 1) {
                vec4 next = getSegment(base, index + 1);
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
            vec4 prev = getSegment(base, index - 1);
            float prevRightX = prev.x + prev.z;
            if (rowRightX <= prevRightX) topRight = min(topRight, prevRightX - rowRightX);
        }
        if (index < count - 1) {
            vec4 next = getSegment(base, index + 1);
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
            vec4 prev = getSegment(base, index - 1);
            if (rowX <= prev.x) topLeft = min(topLeft, prev.x - rowX);
        }
        if (index < count - 1) {
            vec4 next = getSegment(base, index + 1);
            if (next.x <= rowX) bottomLeft = min(bottomLeft, rowX - next.x);
        }
        return vec4(topRight, bottomRight, topLeft, bottomLeft);
    }

    float topRight    = (index == 0)         ? r : 0.0;
    float bottomRight = (index == count - 1) ? r : 0.0;
    float topLeft     = (index == 0)         ? r : 0.0;
    float bottomLeft  = r;
    if (index > 0) {
        vec4 prev = getSegment(base, index - 1);
        if (rowX >= prev.x) topLeft = min(topLeft, rowX - prev.x);
    }
    if (index < count - 1) {
        vec4 next = getSegment(base, index + 1);
        if (next.x >= rowX) bottomLeft = min(bottomLeft, next.x - rowX);
    }
    return vec4(topRight, bottomRight, topLeft, bottomLeft);
}

float shapeSDF(vec2 p, int base, float spanCount, float innerRadius,
               float leftAligned, float bottomAnchored) {
    float minDist = 1e9;
    int count = int(spanCount + 0.5);
    for (int i = 0; i < 64; i++) {
        if (i >= count) break;
        vec4 seg       = getSegment(base, i);
        vec2 halfSize  = max(seg.zw * 0.5, vec2(0.001));
        vec2 segCenter = seg.xy + halfSize;
        float rad = min(innerRadius, min(halfSize.x, halfSize.y));
        vec4 r = resolveSegmentRadius(i, seg, count, rad, leftAligned, bottomAnchored, base);

        vec2 center = p - segCenter;
        center.y = -center.y;
        float d = roundedBoxSDF(center, halfSize, r);
        minDist = min(minDist, d);
    }
    return minDist;
}

void main() {
    int base = QuadIndex * 73;
    vec4 radius          = max(params[base], vec4(0.0));
    vec4 sizeSmoothCorner = params[base + 1];
    vec4 alphaPowerMix   = params[base + 2];
    vec4 fresnelColor    = params[base + 3];
    vec4 flagsDistortZ   = params[base + 4];
    vec4 primaryColor    = params[base + 5];
    vec4 secondaryColor  = params[base + 6];
    vec4 reg             = params[base + 7];

    vec4 shapeMeta  = params[base + 8];
    float spanCount = shapeMeta.x;
    float innerRadius = shapeMeta.y;
    float leftAligned    = shapeMeta.z;
    float bottomAnchored = shapeMeta.w;

    vec2 size = max(sizeSmoothCorner.xy, vec2(1.0));

    float shapeFresnelScale = sizeSmoothCorner.z;
    float cornerSmoothness = max(sizeSmoothCorner.w, 0.001);
    float globalAlpha  = clamp(alphaPowerMix.x, 0.0, 1.0);
    float fresnelPower = max(alphaPowerMix.y, 0.001);
    float baseAlpha    = clamp(alphaPowerMix.z, 0.0, 1.0);
    float fresnelMix   = clamp(alphaPowerMix.w, 0.0, 1.0);
    float fresnelInvert    = flagsDistortZ.x;
    float distortStrength  = flagsDistortZ.y;
    float rainbowFlag      = flagsDistortZ.z;
    float colorOffset      = flagsDistortZ.w;

    vec2 coord   = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 center  = size * 0.5;
    vec2 halfSize = max(center - 1.0, vec2(0.0));
    vec2 pos     = center - coord * size;

    float alpha;
    float signedEdge;
    vec2 refScale = vec2(1.0);
    vec2 gradCoord = coord;
    float gradAspect = size.x / size.y;

    if (spanCount < 0.5) {

        alpha = ralpha(size, coord, radius, cornerSmoothness);
        signedEdge = rdist(pos, halfSize, radius);
        float softEdge = 1.0 - smoothstep(0.0, 2.0, signedEdge);
        alpha = min(alpha, softEdge);
    } else {

        vec2 p = coord * size;
        signedEdge = shapeSDF(p, base, spanCount, innerRadius, leftAligned, bottomAnchored);

        int splitIdx = int(fresnelColor.x + 0.5) - 1;
        if (splitIdx >= 0) {
            vec4 pr = splitData[splitIdx * 3];
            vec4 cr = splitData[splitIdx * 3 + 1];
            vec4 sm = splitData[splitIdx * 3 + 2];
            float dParent = signedEdge;
            float dC = rdist(cr.xy - p, max(cr.zw - 1.0, vec2(0.0)), vec4(max(sm.x, 0.0)));
            signedEdge = rsmin(signedEdge, dC, max(sm.y, 0.0));

            float gw = clamp(0.5 + 0.5 * (dParent - dC) / max(sm.y, 1.0), 0.0, 1.0);
            float gwShape = clamp(0.5 + 0.5 * (dParent - dC) / clamp(sm.y, 1.0, 6.0), 0.0, 1.0);

            refScale = mix(pr.zw, max(cr.zw, vec2(0.0)), gwShape) / max(size * 0.5, vec2(1.0));
            pos = mix(pr.xy - p, cr.xy - p, gwShape);
            halfSize = mix(max(pr.zw - 1.0, vec2(0.0)), max(cr.zw - 1.0, vec2(0.0)), gwShape);
            gradCoord = mix((p - (pr.xy - pr.zw)) / max(2.0 * pr.zw, vec2(1.0)),
                            (p - (cr.xy - cr.zw)) / max(2.0 * cr.zw, vec2(1.0)), gw);
            gradAspect = pr.z / max(pr.w, 0.001);
        }
        alpha = 1.0 - smoothstep(0.0, cornerSmoothness, signedEdge);
    }

    bool isShape = spanCount >= 0.5;
    float distToEdge  = abs(signedEdge);
    float bboxNorm    = max(min(halfSize.x, halfSize.y), 0.001);
    float maxDistNorm = (isShape && shapeFresnelScale > 0.001) ? shapeFresnelScale : bboxNorm;
    float edgeGradient = 1.0 - clamp(distToEdge / maxDistNorm, 0.0, 1.0);
    float fresnelBase  = (fresnelInvert > 0.5) ? edgeGradient : (1.0 - edgeGradient);

    float effPower = isShape ? min(fresnelPower, 24.0) : fresnelPower;
    float fresnel;
    if (effPower > 20.0) {
        fresnel = exp(effPower * log(clamp(fresnelBase, 0.001, 1.0)));
    } else {
        fresnel = pow(clamp(fresnelBase, 0.0, 1.0), effPower);
    }
    fresnel = clamp(fresnel, 0.0, 1.0);

    vec2 dir = (length(pos) > 0.001) ? normalize(-pos) : vec2(0.0);
    vec2 texCoord = clamp((gl_FragCoord.xy - reg.xy) / max(reg.zw, vec2(1.0)), vec2(0.0), vec2(1.0));
    vec2 ofs = dir * fresnel * distortStrength;
    vec2 sampleUv = clamp(texCoord + ofs, vec2(0.0), vec2(1.0));

    vec2 caUv = ofs * 0.12;
    vec3 refracted;
    refracted.r = texture(Sampler0, clamp(sampleUv + caUv, vec2(0.0), vec2(1.0))).r;
    refracted.g = texture(Sampler0, sampleUv).g;
    refracted.b = texture(Sampler0, clamp(sampleUv - caUv, vec2(0.0), vec2(1.0))).b;
    vec4 texColor = vec4(refracted, 1.0);

    vec3 mixedColor;
    if (rainbowFlag > 0.5) {

        mixedColor = rainbowColor(gradCoord.x, colorOffset, max(secondaryColor.r, 0.001), secondaryColor.g);
    } else if (int(paletteMeta.x + 0.5) >= 2) {

        float tri = abs(fract(gradCoord.x + fract(colorOffset)) * 2.0 - 1.0);

        vec3 rampCol = paletteRamp(smoothstep(0.0, 1.0, tri)) * 0.86;
        vec3 meshCol = meshGradient(gradCoord, paletteMeta.y, gradAspect);
        vec3 boxCol = boxGradient(gradCoord, paletteMeta.y * 20.0);
        vec3 targetCol = selectStyle(paletteMeta.z, rampCol, meshCol, boxCol);

        float sweep = paletteMeta.w;
        if (sweep >= 0.0) {

            vec3 prevCol = selectStyle(paletteMeta2.x, rampCol, meshCol, boxCol);
            float axis = gradCoord.x + (gradCoord.y - 0.5) * 0.30;
            float head = sweep * 1.5 - 0.25;
            float wipe = 1.0 - smoothstep(head - 0.04, head + 0.04, axis);
            mixedColor = mix(prevCol, targetCol, wipe);
        } else {
            mixedColor = targetCol;
        }
    } else {
        mixedColor = primaryColor.rgb;
    }

    float noise = fract(sin(dot(coord * size, vec2(12.9898, 78.233))) * 43758.5453);
    vec3 ditheredColor = mixedColor * (1.0 - (0.5 / 255.0) * noise) + (0.5 / 255.0) * noise;

    float panelAlpha = (rainbowFlag > 0.5) ? primaryColor.a : mix(primaryColor.a, secondaryColor.a, 0.5);
    vec4 panelColor  = vec4(ditheredColor, panelAlpha);

    vec3 dimmedBackdrop    = texColor.rgb * 0.78;
    vec3 tintedBackground  = mix(dimmedBackdrop, panelColor.rgb, clamp(panelColor.a * 0.32, 0.0, 0.75));
    vec3 finalColor        = mix(tintedBackground, panelColor.rgb, fresnel * fresnelMix);
    float edgeAlpha        = max(fresnelColor.a, panelColor.a);
    float finalAlpha       = mix(baseAlpha, edgeAlpha, fresnel) * alpha * globalAlpha;

    float beamAmount = gradientBeam(gradCoord, paletteMeta.w);
    finalColor = mix(finalColor, vec3(1.0), clamp(beamAmount, 0.0, 1.0) * 0.5);

    if (finalAlpha < 0.001) {
        discard;
    }

    OutColor = vec4(finalColor, finalAlpha);
}
