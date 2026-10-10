#version 150

uniform sampler2D Sampler0;

layout(std140) uniform ScanParams {
    mat4 InvMvp;
    vec4 CenterRadius;
    vec4 BandTrail;
    vec4 BoundsAlpha;
};

#define BandY (BandTrail.x)
#define DirSign (BandTrail.y)
#define VelPC (BandTrail.z)
#define ScanTime (BandTrail.w)

in vec2 texCoord;
in vec4 vertColor;

out vec4 OutColor;

const float BAND_HALF = 0.02;
const float WAVE_AMP = 0.03;
const float TRAIL_LEN = 0.5;
const float TRAIL_STRENGTH = 0.5;
const float GLOW_LEN = 0.12;
const float GLOW_STRENGTH = 0.6;
const float AMBIENT_LEN = 0.55;
const float AMBIENT_STRENGTH = 0.12;
const float HOT_MIX = 0.7;

void main() {
    float rawDepth = texture(Sampler0, texCoord).r;
    if (rawDepth >= 0.99999) {
        discard;
    }

    vec4 clip = vec4(texCoord * 2.0 - 1.0, rawDepth * 2.0 - 1.0, 1.0);
    vec4 wp = InvMvp * clip;
    vec3 pos = wp.xyz / wp.w;

    vec2 radial = pos.xz - CenterRadius.xz;
    if (dot(radial, radial) > CenterRadius.w * CenterRadius.w || pos.y < BoundsAlpha.x || pos.y > BoundsAlpha.y) {
        discard;
    }

    float theta = atan(radial.y, radial.x);
    float wave = WAVE_AMP * (sin(theta * 3.0 + ScanTime * 2.6) + 0.45 * sin(theta * 5.0 - ScanTime * 3.4));
    float d = pos.y - (BandY + wave);
    float dist = abs(d);

    float core = 1.0 - smoothstep(BAND_HALF * 0.7, BAND_HALF, dist);
    float hot = 1.0 - smoothstep(BAND_HALF * 0.25, BAND_HALF * 0.45, dist);
    float glow = exp(-dist / GLOW_LEN) * GLOW_STRENGTH;
    float ambient = exp(-dist / AMBIENT_LEN) * AMBIENT_STRENGTH;

    float trail = 0.0;
    float behind = -d * DirSign - BAND_HALF;
    if (behind > 0.0) {
        float trailLen = TRAIL_LEN * (0.25 + 0.75 * VelPC);
        trail = (1.0 - clamp(behind / trailLen, 0.0, 1.0)) * TRAIL_STRENGTH;
    }

    vec3 hotColor = mix(vertColor.rgb, vec3(1.0), HOT_MIX);
    vec3 result = vertColor.rgb * (core + glow + ambient + trail) + hotColor * hot;
    OutColor = vec4(result, vertColor.a * BoundsAlpha.z);
}
