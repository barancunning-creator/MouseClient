#version 150

uniform sampler2D Sampler0;

in vec2 texCoord0;
flat in float blurOffset;

out vec4 fragColor;

void main() {
    vec2 halfpixel = 0.5 / vec2(textureSize(Sampler0, 0));

    vec3 sum = texture(Sampler0, texCoord0 + vec2(-halfpixel.x * 2.0, 0.0) * blurOffset).rgb;
    sum += texture(Sampler0, texCoord0 + vec2(-halfpixel.x, halfpixel.y) * blurOffset).rgb * 2.0;
    sum += texture(Sampler0, texCoord0 + vec2(0.0, halfpixel.y * 2.0) * blurOffset).rgb;
    sum += texture(Sampler0, texCoord0 + vec2(halfpixel.x, halfpixel.y) * blurOffset).rgb * 2.0;
    sum += texture(Sampler0, texCoord0 + vec2(halfpixel.x * 2.0, 0.0) * blurOffset).rgb;
    sum += texture(Sampler0, texCoord0 + vec2(halfpixel.x, -halfpixel.y) * blurOffset).rgb * 2.0;
    sum += texture(Sampler0, texCoord0 + vec2(0.0, -halfpixel.y * 2.0) * blurOffset).rgb;
    sum += texture(Sampler0, texCoord0 + vec2(-halfpixel.x, -halfpixel.y) * blurOffset).rgb * 2.0;

    fragColor = vec4(sum / 12.0, 1.0);
}
