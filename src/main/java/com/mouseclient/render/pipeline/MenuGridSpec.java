package com.mouseclient.render.pipeline;

public record MenuGridSpec(
   float x, float y, float width, float height, float cellPx, float lineWidthPx, float mouseX, float mouseY, float influenceRadiusPx, int color
) {
}
