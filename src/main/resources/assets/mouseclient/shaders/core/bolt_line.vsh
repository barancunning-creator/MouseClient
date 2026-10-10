#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;
in float LineWidth;

out vec4 vertexColor;
out float edgeOffsetPx;
out float halfWidthPx;

const float FEATHER_PX = 1.0;

const float VIEW_SHRINK = 1.0 - (1.0 / 256.0);
const mat4 VIEW_SCALE = mat4(
    VIEW_SHRINK, 0.0, 0.0, 0.0,
    0.0, VIEW_SHRINK, 0.0, 0.0,
    0.0, 0.0, VIEW_SHRINK, 0.0,
    0.0, 0.0, 0.0, 1.0
);

void main() {
    vec4 startClip = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position, 1.0);
    vec4 endClip = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position + Normal, 1.0);

    vec3 startNdc = startClip.xyz / startClip.w;
    vec3 endNdc = endClip.xyz / endClip.w;

    vec2 screenDirection = normalize((endNdc.xy - startNdc.xy) * ScreenSize);
    vec2 perpendicular = vec2(-screenDirection.y, screenDirection.x);

    // the quad is grown by FEATHER_PX past the real edge so the fragment stage has room to fade out
    halfWidthPx = LineWidth * 0.5;
    float extentPx = halfWidthPx + FEATHER_PX;

    vec2 ndcOffset = perpendicular * (2.0 * extentPx) / ScreenSize;
    if (ndcOffset.x < 0.0) {
        ndcOffset *= -1.0;
    }

    float side = (gl_VertexID % 2 == 0) ? 1.0 : -1.0;
    edgeOffsetPx = side * extentPx;

    gl_Position = vec4((startNdc + side * vec3(ndcOffset, 0.0)) * startClip.w, startClip.w);
    vertexColor = Color * ColorModulator;
}
