#version 150

uniform sampler2D Sampler0;

layout(std140) uniform TorusParams {
    mat4 ModelViewMat;
    mat4 ProjMat;
    vec4 CamPosFreq;
    vec4 GlowColorStrength;
};

in vec3 viewPos;
in vec3 viewNormal;
in vec4 vertexColor;

out vec4 OutColor;

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

void main() {
    // subtracting world CamPos from the view-space normal is intentional, ported as-is from the reference shader - the mixed-space chaos IS the chrome look
    vec3 normal = normalize(viewNormal - CamPosFreq.xyz);
    vec3 I = normalize(viewPos + snoise(viewPos) * CamPosFreq.w);
    vec3 R = reflect(I, normal + snoise(viewPos) * CamPosFreq.w);
    vec3 cubeDirection = normalize(R);

    float x = cubeDirection.x;
    float y = cubeDirection.y;
    float z = cubeDirection.z;

    float absX = abs(x);
    float absY = abs(y);
    float absZ = abs(z);

    bool isXPositive = x > 0.0;
    bool isYPositive = y > 0.0;
    bool isZPositive = z > 0.0;

    float maxAxis = 0.0, uc = 0.0, vc = 0.0;

    if (isXPositive && absX >= absY && absX >= absZ) {
        maxAxis = absX; uc = -z; vc = y;
    } else if (!isXPositive && absX >= absY && absX >= absZ) {
        maxAxis = absX; uc = z; vc = y;
    } else if (isYPositive && absY >= absX && absY >= absZ) {
        maxAxis = absY; uc = x; vc = -z;
    } else if (!isYPositive && absY >= absX && absY >= absZ) {
        maxAxis = absY; uc = x; vc = z;
    } else if (isZPositive && absZ >= absX && absZ >= absY) {
        maxAxis = absZ; uc = x; vc = y;
    } else if (!isZPositive && absZ >= absX && absZ >= absY) {
        maxAxis = absZ; uc = -x; vc = y;
    }

    vec2 uv = 0.5 * (vec2(uc, vc) / maxAxis + 1.0);

    OutColor = vec4(texture(Sampler0, uv).rgb, vertexColor.a);
}
