#version 150

out vec2 vUV;

void main() {
    vec2 vertices[4] = vec2[](
        vec2(0.0, 0.0),
        vec2(1.0, 0.0),
        vec2(1.0, 1.0),
        vec2(0.0, 1.0)
    );
    int indices[6] = int[](0, 1, 2, 2, 3, 0);
    vec2 vertex = vertices[indices[gl_VertexID % 6]];
    gl_Position = vec4(vertex.x * 2.0 - 1.0, 1.0 - vertex.y * 2.0, 0.0, 1.0);
    vUV = vec2(vertex.x, 1.0 - vertex.y);
}
