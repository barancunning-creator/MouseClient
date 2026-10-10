#version 330

in vec4 vertexColor;
in float edgeOffsetPx;
in float halfWidthPx;

out vec4 fragColor;

void main() {
    // distance from the line centre in pixels drives a one pixel falloff, so wide bolts read as glow
    float coverage = clamp(halfWidthPx - abs(edgeOffsetPx) + 0.5, 0.0, 1.0);
    float alpha = vertexColor.a * coverage;
    if (alpha <= 0.001) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, alpha);
}
