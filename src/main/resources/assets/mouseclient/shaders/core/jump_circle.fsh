#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec3 vRay;
in vec2 TexCoord;

layout(std140) uniform JumpCircleParams {
    mat4 InvViewProj;
    vec4 Params;
    vec4 Accent;
    vec4 Fx;
    vec4 Circles[16];
    vec4 WallDists[128];
};

out vec4 OutColor;

float wallAt(int circle, int dir) {
    int d = dir & 31;
    vec4 row = WallDists[circle * 8 + (d >> 2)];
    int c = d & 3;
    return dot(row, vec4(c == 0, c == 1, c == 2, c == 3));
}

vec4 permute(vec4 x){ return mod(((x*34.0)+1.0)*x, 289.0); }
vec4 taylorInvSqrt(vec4 r){ return 1.79284291400159 - 0.85373472095314 * r; }
float snoise(vec3 v){
    const vec2 C = vec2(1.0/6.0, 1.0/3.0); const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);
    vec3 i  = floor(v + dot(v, C.yyy)); vec3 x0 = v - i + dot(i, C.xxx);
    vec3 g = step(x0.yzx, x0.xyz); vec3 l = 1.0 - g; vec3 i1 = min(g.xyz, l.zxy); vec3 i2 = max(g.xyz, l.zxy);
    vec3 x1 = x0 - i1 + 1.0 * C.xxx; vec3 x2 = x0 - i2 + 2.0 * C.xxx; vec3 x3 = x0 - 1. + 3.0 * C.xxx;
    i = mod(i, 289.0);
    vec4 p = permute(permute(permute( i.z + vec4(0.0, i1.z, i2.z, 1.0)) + i.y + vec4(0.0, i1.y, i2.y, 1.0)) + i.x + vec4(0.0, i1.x, i2.x, 1.0));
    float n_ = 1.0/7.0; vec3 ns = n_ * D.wyz - D.xzx;
    vec4 j = p - 49.0 * floor(p * ns.z *ns.z);
    vec4 x_ = floor(j * ns.z); vec4 y_ = floor(j - 7.0 * x_);
    vec4 x = x_ *ns.x + ns.yyyy; vec4 y = y_ *ns.x + ns.yyyy; vec4 h = 1.0 - abs(x) - abs(y);
    vec4 b0 = vec4(x.xy, y.xy); vec4 b1 = vec4(x.zw, y.zw);
    vec4 s0 = floor(b0)*2.0 + 1.0; vec4 s1 = floor(b1)*2.0 + 1.0; vec4 sh = -step(h, vec4(0.0));
    vec4 a0 = b0.xzyw + s0.xzyw*sh.xxyy; vec4 a1 = b1.xzyw + s1.xzyw*sh.zzww;
    vec3 p0 = vec3(a0.xy, h.x); vec3 p1 = vec3(a0.zw, h.y); vec3 p2 = vec3(a1.xy, h.z); vec3 p3 = vec3(a1.zw, h.w);
    vec4 norm = taylorInvSqrt(vec4(dot(p0, p0), dot(p1, p1), dot(p2, p2), dot(p3, p3)));
    p0 *= norm.x; p1 *= norm.y; p2 *= norm.z; p3 *= norm.w;
    vec4 m = max(0.6 - vec4(dot(x0, x0), dot(x1, x1), dot(x2, x2), dot(x3, x3)), 0.0);
    m = m * m;
    return 42.0 * dot(m*m, vec4(dot(p0, x0), dot(p1, x1), dot(p2, x2), dot(p3, x3)));
}

/*
void main() {
    float sceneDepth = texture(Sampler0, TexCoord).r;
    if (sceneDepth >= 1.0) discard;
    vec4 sceneClip = InvViewProj * vec4(TexCoord * 2.0 - 1.0, sceneDepth * 2.0 - 1.0, 1.0);
    vec3 camRelPos = sceneClip.xyz / sceneClip.w;
    float glow = 0.0;
    for (int i = 0; i < int(Params.z + 0.5); i++) {
        vec3 toCircle = camRelPos - Circles[i].xyz;
        if (abs(toCircle.y) > 2.5) continue;
        float ringDist = abs(length(toCircle.xz) - Params.x);
        glow += (exp(-ringDist * ringDist / (Params.y * Params.y)) + 0.18 * exp(-max(0.0, Params.x - length(toCircle.xz)) * 1.2) * step(length(toCircle.xz), Params.x)) * Circles[i].w;
    }
    if (glow < 0.004) discard;
    OutColor = vec4(Accent.rgb, clamp(glow, 0.0, 0.9));
}
*/
void main() {
    float sceneDepth = texture(Sampler0, TexCoord).r;
    vec4 sceneClip = InvViewProj * vec4(TexCoord * 2.0 - 1.0, sceneDepth * 2.0 - 1.0, 1.0);
    vec3 camRelPos = sceneClip.xyz / sceneClip.w;
    float glow = 0.0;
    float warpField = 0.0;
    float shadowField = 0.0;
    vec3 chromeNormal = vec3(0.0);
//    float glassFill = 0.0;
//    float rimPc = 0.0;
    bool water = Fx.y > 0.5;
    float notSky = step(sceneDepth, 0.9999);
    float invSoft = 1.0 / max(Params.y * Params.y, 1e-6);
    int circleCount = int(Params.z + 0.5);
    //дистанс-поле аналитическое: то, что jump flood собирает пинг-понг пассами, для круга считается точно за один пас
    //цикл без continue — дивергенция потоков ломает dFdx поля искажения
    for (int i = 0; i < circleCount; i++) {
        vec3 toCircle = camRelPos - Circles[i].xyz;
        float agePc = Circles[i].w;
        float vis = (agePc < 0.18 ? agePc / 0.18 : (agePc < 0.58 ? 1.0 : 1.0 - (agePc - 0.58) / 0.42)) * step(0.0001, agePc);
        float effRadius = Params.x * (Fx.x > 0.5 ? 1.0 - pow(1.0 - min(agePc / 0.85, 1.0), 2.0) : 1.0);
//        float band = step(abs(toCircle.y), 2.5) * step(sceneDepth, 0.9999) * vis;
        float lenXZ = length(toCircle.xz);
        //бейк 32 лучей с CPU: волна гаснет за стеной, о которую "разбилась"
        float wallDirPc = (atan(toCircle.z, toCircle.x) / 6.2831853 + 0.5) * 32.0;
        int wallDir = int(wallDirPc);
        float wallDist = mix(wallAt(i, wallDir), wallAt(i, wallDir + 1), fract(wallDirPc));
//        float band = step(abs(toCircle.y), 2.5) * step(sceneDepth, 0.9999) * vis * (1.0 - smoothstep(wallDist - 0.1, wallDist + 0.15, lenXZ));
        float band = step(abs(toCircle.y), 1.25) * notSky * vis * (1.0 - smoothstep(wallDist - 0.1, wallDist + 0.15, lenXZ));
        float sd = lenXZ - effRadius;
        float bump = exp(-sd * sd * invSoft) * band;
        warpField += bump;
        if (water) {
            shadowField += exp(-sd * sd * invSoft * 0.16) * band;
            chromeNormal += vec3(toCircle.x / max(lenXZ, 0.001), 0.6, toCircle.z / max(lenXZ, 0.001)) * bump * sign(sd);
        } else {
            glow += bump + 0.18 * exp(min(sd, 0.0) * 1.2) * step(sd, 0.0) * band;
        }
//        glassFill += (1.0 - smoothstep(effRadius - 0.08, effRadius, lenXZ)) * band;
//        rimPc = max(rimPc, smoothstep(effRadius * 0.45, effRadius, lenXZ) * (1.0 - smoothstep(effRadius, effRadius + 0.02, lenXZ)) * band);
    }
    if (water) {
        //water chrome ring
        if (warpField < 0.01 && shadowField < 0.01) discard;
        float warpNoise = snoise(camRelPos * 1.7) * Params.w;
        vec3 I = normalize(normalize(vRay) + warpNoise);
        vec3 R = normalize(reflect(I, normalize(chromeNormal + vec3(0.0, 0.001, 0.0)) + warpNoise));
        float maxAxis = max(abs(R.x), max(abs(R.y), abs(R.z)));
        vec2 cubeUv = 0.5 * ((abs(R.x) >= maxAxis ? vec2(R.x > 0.0 ? -R.z : R.z, R.y) : (abs(R.y) >= maxAxis ? vec2(R.x, R.y > 0.0 ? -R.z : R.z) : vec2(R.z > 0.0 ? R.x : -R.x, R.y))) / maxAxis + 1.0);
        OutColor = vec4(mix(texture(Sampler1, TexCoord).rgb * (1.0 - 0.45 * clamp(shadowField, 0.0, 1.0)), mix(texture(Sampler1, clamp(cubeUv, vec2(0.001), vec2(0.999))).rgb, Accent.rgb, clamp(0.5 + 0.35 * clamp(warpField, 0.0, 1.0), 0.0, 0.85)), smoothstep(0.12, 0.7, clamp(warpField, 0.0, 1.0))), 1.0);
        return;
    }
    //wave ring
    vec2 warpShift = clamp(vec2(dFdx(warpField), dFdy(warpField)) * Params.w, vec2(-0.03), vec2(0.03));
    if (glow < 0.004 && length(warpShift) < 0.0004) discard;
    OutColor = vec4(mix(texture(Sampler1, clamp(TexCoord - warpShift, vec2(0.001), vec2(0.999))).rgb, Accent.rgb, clamp(glow, 0.0, 0.9)), 1.0);
}
