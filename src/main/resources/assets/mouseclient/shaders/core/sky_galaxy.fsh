#version 150

layout(std140) uniform SkyPostParams {
    mat4 InvViewProj;
    vec4 FirstColor;
    vec4 SecondColor;
    vec4 TimeIntensity;
    vec4 CameraPosition;
};

#define time (TimeIntensity.x)
#define Intensity (TimeIntensity.y)

in vec3 vRay;
in vec2 TexCoord;

out vec4 OutColor;

const int ITERATIONS = 7;
const int VOL_STEPS = 7;
const float FORM_PARAM = 0.50;
const float STEP_SIZE = 0.29;
const float TILE = 0.95;
const float BRIGHTNESS = 0.0045;
const float DIST_FADING = 0.66;
const float SATURATION = 0.82;

float fullSkyAmount(float skyHeight, float amount) {
    float lowerSky = 1.0 - smoothstep(-0.18, 0.18, skyHeight);
    float enabled = smoothstep(0.02, 0.18, amount);
    return clamp(mix(amount, max(amount, 0.96), lowerSky * enabled), 0.0, 1.0);
}

float field(vec3 p) {
    float strength = 7.0 + 0.03 * log(1.0e-6 + fract(sin(time) * 4373.11));
    float accum = 0.0;
    float previous = 0.0;
    float totalWeight = 0.0;

    for (int i = 0; i < 6; i++) {
        float mag = dot(p, p);
        p = abs(p) / max(mag, 0.001) + vec3(-0.5, -0.8 + 0.1 * sin(time * 0.7 + 2.0), -1.1 + 0.3 * cos(time * 0.3));
        float weight = exp(-float(i) / 7.0);
        accum += weight * exp(-strength * pow(abs(mag - previous), 2.3));
        totalWeight += weight;
        previous = mag;
    }

    return max(0.0, 5.0 * accum / max(totalWeight, 0.001) - 0.7);
}

vec3 galaxyColor(vec3 worldDirection) {
    float skyHeight = abs(worldDirection.y);
    vec2 uv = worldDirection.xz / max(0.42 + skyHeight, 0.34);
    uv *= 0.82;

    float time2 = time * 0.16;
    vec3 dir = normalize(vec3(uv * 0.9, 1.0));
    vec3 from = vec3(0.0);
    vec3 forward = vec3(0.0, 0.0, 1.0);

    from.x += 1.2 * cos(0.3 * time2) + 0.018 * time2;
    from.y += 1.2 * sin(0.2 * time2) + 0.018 * time2;
    from.z += 0.04 * time2;

    float angleXZ = 0.9;
    float angleYZ = -0.6;
    float angleXY = 0.9 + time2 * 0.22;
    mat2 rotXZ = mat2(cos(angleXZ), sin(angleXZ), -sin(angleXZ), cos(angleXZ));
    mat2 rotYZ = mat2(cos(angleYZ), sin(angleYZ), -sin(angleYZ), cos(angleYZ));
    mat2 rotXY = mat2(cos(angleXY), sin(angleXY), -sin(angleXY), cos(angleXY));

    dir.xy = rotXY * dir.xy;
    forward.xy = rotXY * forward.xy;
    dir.xz = rotXZ * dir.xz;
    forward.xz = rotXZ * forward.xz;
    dir.yz = rotYZ * dir.yz;
    forward.yz = rotYZ * forward.yz;
    from.xy = -(rotXY * from.xy);
    from.xz = rotXZ * from.xz;
    from.yz = rotYZ * from.yz;

    float zoom = time2 * -0.012;
    from += forward * zoom;
    float sampleShift = mod(zoom, STEP_SIZE);
    float zOffset = -sampleShift;
    sampleShift /= STEP_SIZE;

    float s = 0.24;
    float s3 = s + STEP_SIZE * 0.5;
    vec3 volume = vec3(0.0);
    vec3 cloudColor = vec3(0.0);

    for (int r = 0; r < VOL_STEPS; r++) {
        vec3 p = from + (s + zOffset) * dir;
        vec3 pCloud = from + (s3 + zOffset) * dir;

        p = abs(vec3(TILE) - mod(p, vec3(TILE * 2.0)));
        pCloud = abs(vec3(TILE) - mod(pCloud, vec3(TILE * 2.0)));

        float cloud = field(pCloud);
        float previousAmount = 0.0;
        float amount = 0.0;

        for (int i = 0; i < ITERATIONS; i++) {
            p = abs(p) / max(dot(p, p), 0.001) - FORM_PARAM;
            float diff = abs(length(p) - previousAmount);
            amount += i > 5 ? min(12.0, diff) : diff;
            previousAmount = length(p);
        }

        amount *= amount * amount;
        float depth = s + zOffset;
        float fade = pow(DIST_FADING, max(0.0, float(r) - sampleShift));

        if (r == 0) {
            fade *= 1.0 - sampleShift;
        }
        if (r == VOL_STEPS - 1) {
            fade *= sampleShift;
        }

        volume += vec3(depth, depth * depth, depth * depth * depth * depth) * amount * BRIGHTNESS * fade;
        cloudColor += vec3(1.8 * cloud * cloud * cloud, 1.4 * cloud * cloud, cloud) * fade * 0.31;

        s += STEP_SIZE;
        s3 += STEP_SIZE;
    }

    volume = mix(vec3(length(volume)), volume, SATURATION);
    cloudColor.b *= 1.8;
    cloudColor.r *= 0.06;
    cloudColor.g *= 0.12;

    vec3 color = volume * 0.36 + cloudColor;
    float zenithFade = 1.0 - smoothstep(0.98, 1.0, skyHeight);
    vec3 base = mix(vec3(0.015, 0.018, 0.052), vec3(0.035, 0.055, 0.13), smoothstep(0.0, 0.72, skyHeight));
    return base + color * zenithFade;
}

void main() {
    vec3 skyDirection = normalize(vRay);
    vec3 shaderColor = galaxyColor(skyDirection);
    float amount = clamp(max(Intensity, 0.0) * 0.92, 0.0, 1.0);

    OutColor = vec4(shaderColor, fullSkyAmount(skyDirection.y, amount));
}
