#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec2  TexCoord;
in vec4  VertexColor;
flat in int BatchIndex;

layout(std140) uniform MenuGridParamsArray {
    vec4 params[512];
};

out vec4 OutColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

void main() {
    int base = BatchIndex * 2;
    vec4 A = params[base];
    vec4 B = params[base + 1];
    vec2  sizePx  = A.xy;
    float cell    = max(A.z, 1.0);
    float lineW   = max(A.w, 0.4);
    vec2  mousePx = B.xy;
    float radius  = max(B.z, 1.0);
    float time    = B.w;

    vec2 px = TexCoord * sizePx;
    vec2 toMouse = px - mousePx;
    float mouseDist = length(toMouse);
    float pullPC = exp(-mouseDist * mouseDist / (2.0 * radius * radius));
    vec2 pushDir = mouseDist > 0.001 ? toMouse / mouseDist : vec2(0.0);

    vec2 warpedPx = px + pushDir * pullPC * cell * 1.45 + vec2(-pushDir.y, pushDir.x) * pullPC * cell * 0.55;
//    vec2 cellId = floor(warpedPx / cell);
//    float cellNoise = hash21(cellId);
//    warpedPx += (vec2(cellNoise, hash21(cellId + 7.13)) - 0.5) * pullPC * cell * 0.9;
//    warpedPx += vec2(sin(time * 0.35 + cellId.y * 0.7), cos(time * 0.28 + cellId.x * 0.6)) * pullPC * cell * 0.22;
    //искажение считается только от координаты, без шума по ячейкам — иначе соседние клетки разъезжаются и сетка рвётся
    warpedPx += vec2(sin(px.y * 0.035 + time * 0.9), cos(px.x * 0.035 + time * 0.75)) * pullPC * cell * 0.3;

    vec2 lineXY = abs(fract(warpedPx / cell) - 0.5) * cell;
    float lineDist = min(lineXY.x, lineXY.y);
    float aa = max(fwidth(lineDist), 0.35);
    float linePC = 1.0 - smoothstep(lineW * 0.5 - aa, lineW * 0.5 + aa, lineDist);
//    linePC *= mix(1.0, step(pullPC * 0.95, cellNoise), smoothstep(0.04, 0.6, pullPC));

    vec2 centered = (TexCoord - 0.5) * vec2(sizePx.x / max(sizePx.y, 1.0), 1.0);
    float vignettePC = smoothstep(0.92, 0.16, length(centered));

    float alpha = linePC * vignettePC * VertexColor.a * mix(1.0, 2.6, pullPC);
    if (alpha < 0.002) discard;

    OutColor = vec4(mix(VertexColor.rgb, vec3(1.0), pullPC * 0.35), alpha) * ColorModulator;
}
