#version 330

uniform sampler2D Sampler0;

in vec4 scanColorHeight;
in vec2 texCoord0;
in vec2 scanControl;
in vec2 scanSecondary;
in vec3 surfaceNormal;

out vec4 fragColor;

float lowByte(float packedValue) {
    return mod(floor(packedValue + 0.5), 256.0);
}

float highByte(float packedValue) {
    return floor((packedValue + 0.5) / 256.0);
}

void main() {
    vec4 skin = texture(Sampler0, texCoord0);
    if (skin.a < 0.1) {
        discard;
    }

    float phase = lowByte(scanControl.x) / 255.0;
    float appear = lowByte(scanControl.y) / 255.0;
    float glowStrength = lowByte(scanSecondary.x) / 127.5;
    float hit = highByte(scanSecondary.y) / 255.0;
    float horizontal = lowByte(scanSecondary.y) / 255.0;

    vec3 secondaryColor = vec3(
        highByte(scanControl.x),
        highByte(scanControl.y),
        highByte(scanSecondary.x)
    ) / 255.0;
    vec3 baseColor = mix(scanColorHeight.rgb, secondaryColor, hit);
    vec3 lineColor = mix(baseColor, vec3(1.0), 0.78 * (1.0 - hit));

    float scanHeight = 1.0 - phase;
    float signedDistance = scanColorHeight.a - scanHeight;
    float distanceToLine = abs(signedDistance);
    float antialias = max(fwidth(scanColorHeight.a) * 1.35, 0.0025);
    float coreWidth = mix(0.012, 0.009, hit);
    float glowWidth = (0.045 + glowStrength * 0.035) * mix(1.0, 0.86, hit);
    float core = 1.0 - smoothstep(coreWidth, coreWidth + antialias, distanceToLine);
    float halo = 1.0 - smoothstep(coreWidth + antialias,
            glowWidth + antialias, distanceToLine);

    float trailLength = 0.22 + glowStrength * 0.055;
    float trail = step(0.0, signedDistance)
            * (1.0 - smoothstep(0.0, trailLength, signedDistance));
    trail *= 1.0 - core;

    const float TWO_PI = 6.28318530718;
    float normalAngle = atan(surfaceNormal.z, surfaceNormal.x) / TWO_PI + 0.5;
    float rotation = fract(normalAngle + horizontal * 0.12 - phase * 2.0);
    float rotatingArc = pow(max(0.0, cos(rotation * TWO_PI)), 10.0);
    float pulse = 0.92 + 0.08 * sin(phase * TWO_PI * 4.0);

    float opacity = (core * 0.98 + halo * (0.22 + glowStrength * 0.12)
            + trail * 0.13) * appear * skin.a;
    if (opacity < 0.002) {
        discard;
    }

    vec3 skinDetail = mix(vec3(1.0), skin.rgb, 0.16);
    vec3 emission = baseColor * skinDetail
            * (halo * (0.72 + glowStrength * 0.16) + trail * 0.28);
    emission += lineColor * core * pulse * (1.2 + rotatingArc * 0.85);
    fragColor = vec4(emission, clamp(opacity, 0.0, 1.0));
}
