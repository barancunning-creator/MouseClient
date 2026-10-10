#version 150

layout(std140) uniform SkyPostParams {
    mat4 InvViewProj;
    vec4 FirstColor;
    vec4 SecondColor;
    vec4 TimeIntensity;
    vec4 CameraPosition;
};

uniform sampler2D Sampler1;

in vec3 vRay;
in vec2 TexCoord;

out vec4 OutColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1.0, 0.0, 0.0)), f.x), mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), f.x), mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float cloudDensity(vec3 world, vec3 wind) {
    float clump = smoothstep(0.42, 0.78, noise(world * vec3(0.022, 0.05, 0.022) + wind * 0.02));
    if (clump <= 0.0) return 0.0;
    vec3 detailPos = world * 0.09 + wind * 0.06;
    float detail = noise(detailPos) * 0.65 + noise(detailPos * 2.1 + 7.3) * 0.35;
    float height = exp(-max(world.y - CameraPosition.y + 3.0, 0.0) * 0.06);
    return clamp(clump * (0.45 + detail) - 0.3, 0.0, 1.0) * height;
}

void main() {
    float depth = texture(Sampler1, TexCoord).r;
    vec4 clip = InvViewProj * vec4(TexCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 relative = clip.xyz / clip.w;
    float sceneDistance = length(relative);
    vec3 direction = relative / max(sceneDistance, 0.0001);
    float maxDistance = SecondColor.x;
    float startDistance = SecondColor.y;
    float density = SecondColor.z;
    if (depth >= 1.0) sceneDistance = maxDistance;
    float endDistance = min(sceneDistance, maxDistance);
    if (endDistance <= startDistance) {
        OutColor = vec4(0.0, endDistance / maxDistance, 0.0, 1.0);
        return;
    }

    const int STEPS = 10;
    float stepLength = (endDistance - startDistance) / float(STEPS);
    float jitter = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    vec3 wind = vec3(SecondColor.w * 0.8, 0.0, SecondColor.w * 0.3);
    float transmittance = 1.0;
    for (int index = 0; index < STEPS; index++) {
        float travel = startDistance + (float(index) + jitter) * stepLength;
        float approach = smoothstep(startDistance, startDistance * 1.8, travel);
        transmittance *= exp(-cloudDensity(CameraPosition.xyz + direction * travel, wind) * approach * density * stepLength * 0.22);
        if (transmittance < 0.03) break;
    }
    float baseHaze = 1.0 - exp(-max(endDistance - startDistance, 0.0) * density * 0.0025);
    float fogAmount = clamp((1.0 - transmittance * (1.0 - baseHaze)) * FirstColor.a, 0.0, 0.85);
    OutColor = vec4(fogAmount, endDistance / maxDistance, 0.0, 1.0);
}
