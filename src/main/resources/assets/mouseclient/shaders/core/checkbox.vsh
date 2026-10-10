#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;

out vec2 FragCoord;
flat out int QuadIndex;

const vec2[4] RECT_VERTICES_COORDS = vec2[] (
    vec2(0.0, 0.0),
    vec2(0.0, 1.0),
    vec2(1.0, 1.0),
    vec2(1.0, 0.0)
);

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    FragCoord = RECT_VERTICES_COORDS[gl_VertexID % 4];
    QuadIndex = gl_VertexID / 4;
}
