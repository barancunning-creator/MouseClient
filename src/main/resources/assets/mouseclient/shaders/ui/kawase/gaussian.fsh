#version 150

uniform sampler2D Sampler0;

layout(std140) uniform KawaseParams {
    vec4 SourceRect;
    vec4 HalfPixel;
    vec4 FallbackColor;
    vec4 Boost;
};

in vec2 TexCoord;

out vec4 OutColor;

vec4 sampleSource(vec2 coord) {
    vec4 color = texture(Sampler0, coord);
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    if (color.a < 0.001 && luminance < 0.003) {
        return vec4(FallbackColor.rgb, 1.0);
    }
    return color;
}

void main() {
    vec2 sourceCoord = SourceRect.xy + TexCoord * SourceRect.zw;
    float sigma = max(HalfPixel.z, 0.1);
    int radius = int(HalfPixel.w);
    float twoSigmaSq = 2.0 * sigma * sigma;

    vec4 sum = sampleSource(sourceCoord);
    float weightSum = 1.0;

    for (int i = 1; i <= 32; i += 2) {
        if (i > radius) {
            break;
        }
        float w1 = exp(-float(i) * float(i) / twoSigmaSq);
        float w2 = exp(-float(i + 1) * float(i + 1) / twoSigmaSq);
        float pairWeight = w1 + w2;
        float pairOffset = float(i) + w2 / max(pairWeight, 1.0e-5);
        vec2 delta = HalfPixel.xy * pairOffset;
        sum += sampleSource(sourceCoord + delta) * pairWeight;
        sum += sampleSource(sourceCoord - delta) * pairWeight;
        weightSum += 2.0 * pairWeight;
    }

    vec3 c = (sum / weightSum).rgb;
    float saturation = max(Boost.x, 0.001);
    float brightness = max(Boost.y, 0.001);
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(luma), c, saturation) * brightness;
    vec3 dimTarget = mix(vec3(0.059, 0.055, 0.067), vec3(0.965, 0.965, 0.97), clamp(Boost.w, 0.0, 1.0));
    c = mix(c, dimTarget, clamp(Boost.z, 0.0, 1.0));

    OutColor = vec4(c, FallbackColor.a);
}
