#version 150

layout(std140) uniform Uniforms {
    mat4 uInvProjection;
    mat4 uInvView;
    vec4 uCameraPosRadius;
    vec4 uCenterWidth;
    vec4 uOuterColor;
    vec4 uMidColor;
    vec4 uInnerColor;
    vec4 uScanlineColor;
    vec4 uMeta;
};

in vec2 vUV;
out vec4 fragColor;

uniform sampler2D DepthSampler;

float scanlines() {
    return sin(gl_FragCoord.y) * 0.5 + 0.5;
}

vec3 reconstructWorldPos(float depth) {
    float z = depth * 2.0 - 1.0;
    vec4 clipSpacePosition = vec4(vUV * 2.0 - 1.0, z, 1.0);
    vec4 viewSpacePosition = uInvProjection * clipSpacePosition;
    viewSpacePosition /= max(viewSpacePosition.w, 0.0001);
    vec4 worldSpacePosition = uInvView * viewSpacePosition;
    return uCameraPosRadius.xyz + worldSpacePosition.xyz;
}

void main() {
    float depth = texture(DepthSampler, vUV).r;
    if (depth >= 1.0) {
        discard;
    }

    float radius = uCameraPosRadius.w;
    float width = max(uCenterWidth.w, 0.0001);
    float dist = distance(reconstructWorldPos(depth), uCenterWidth.xyz);
    if (dist >= radius || dist <= radius - width) {
        discard;
    }

    float diff = 1.0 - (radius - dist) / width;
    vec4 edge = mix(uMidColor, uOuterColor, pow(diff, uMeta.x));
    vec4 color = mix(uInnerColor, edge, diff) + scanlines() * uScanlineColor;
    color *= diff;
    fragColor = color;
}
