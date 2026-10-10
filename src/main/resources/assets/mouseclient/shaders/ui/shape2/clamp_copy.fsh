#version 150

uniform sampler2D Sampler0;

layout(std140) uniform MouseClientBlurRect {
    vec4 Bounds;   // minU, minV, maxU, maxV (framebuffer-UV, v-вверх)
    float Radius;  // радиус угла в framebuffer px
};

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec2 sizePx = vec2(textureSize(Sampler0, 0));
    vec2 pMin = Bounds.xy * sizePx;
    vec2 pMax = Bounds.zw * sizePx;
    vec2 center = (pMin + pMax) * 0.5;
    vec2 halfSize = (pMax - pMin) * 0.5;

    vec2 p = texCoord0 * sizePx - center;
    float r = min(Radius, min(halfSize.x, halfSize.y));
    vec2 inner = max(halfSize - r, vec2(0.0));
    vec2 q = clamp(p, -inner, inner);
    vec2 d = p - q;
    float len = length(d);
    if (len > r) d *= r / len;

    vec2 uv = (center + q + d) / sizePx;
    fragColor = vec4(texture(Sampler0, uv).rgb, 1.0);
}
