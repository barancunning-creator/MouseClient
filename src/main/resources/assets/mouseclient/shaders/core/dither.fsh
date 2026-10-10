#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec2  TexCoord;
in vec4  VertexColor;
flat in int BatchIndex;

layout(std140) uniform DitherParamsArray {
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
    float cell   = max(A.x, 0.0001);
    vec2  sizePx = vec2(A.y, A.z);
    float radius = A.w;
    float time   = B.x;
    float seed   = B.y;
    float spread = B.z;

    vec2 px = TexCoord * sizePx;
    float rows = max(1.0, floor(sizePx.y / cell));
    float colsW = floor(sizePx.x / cell) * cell;
    float y0 = 0.5 * (sizePx.y - rows * cell);
    float flow = time * 10.0;
    float gx = px.x / cell + flow;
    float gy = (px.y - y0) / cell;
    vec2 id = vec2(floor(gx), floor(gy));
    vec2 f  = vec2(fract(gx), fract(gy));
    float inGrid = step(-0.5, id.y) * step(id.y, rows - 0.5) * step(px.x, colsW - 0.001);

    float u = clamp(TexCoord.x, 0.0, 1.0);
    float front = 1.0 - spread * 1.15;
    float ramp = max(0.9 * spread, 0.2);
    float g = smoothstep(front, front + ramp, u);

    vec2 hid = vec2(mod(id.x, 1024.0), id.y);
    float h1 = hash21(hid + seed);
    float h2 = hash21(hid * 1.73 + 17.19 + seed);
    float vis = smoothstep(h1 * 0.9, h1 * 0.9 + 0.25, g);
    float glint = pow(0.5 + 0.5 * sin(time * 4.2 + h1 * 6.2831 + id.x * 0.37), 6.0);
    float spark = step(0.78, h2) * glint;

    vec2 cbox = f - vec2(0.335);
    float dBox = max(abs(cbox.x), abs(cbox.y)) - 0.335;
    float aaB = max(fwidth(gx) * 0.6, 0.02);
    float ink = 1.0 - smoothstep(-aaB, aaB, dBox);
    float a = vis * min(1.0, mix(0.28, 1.0, h2) + spark * 0.7) * mix(0.55, 1.0, g) * ink * inGrid;

    vec2 p = px - 0.5 * sizePx;
    vec2 halfSize = 0.5 * sizePx - vec2(radius);
    vec2 dxy = abs(p) - halfSize;
    float dist = length(max(dxy, vec2(0.0))) + min(max(dxy.x, dxy.y), 0.0) - radius;
    float aa = max(fwidth(dist), 0.001);
    float mask = 1.0 - smoothstep(-aa, aa * 0.5, dist);

    float alpha = a * mask * VertexColor.a;
    if (alpha < 0.003) discard;

    float whiten = min(1.0, max(spark * 0.85, 0.22 * smoothstep(0.8, 1.0, h2) * g)) * vis;
    vec3 rgb = mix(VertexColor.rgb, vec3(1.0), whiten);
    OutColor = vec4(rgb, alpha) * ColorModulator;
}
