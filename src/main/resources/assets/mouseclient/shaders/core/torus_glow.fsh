#version 150

in vec4 vertexColor;

out vec4 OutColor;

void main() {
    // ADDITIVE is (ONE, ONE) in 1.21.11, so the falloff alpha must be premultiplied here
    OutColor = vec4(vertexColor.rgb * vertexColor.a, vertexColor.a);
}
