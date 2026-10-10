#version 150

layout(std140) uniform SkyPostParams {
    mat4 InvViewProj;
    vec4 FirstColor;
    vec4 SecondColor;
    vec4 TimeIntensity;
    vec4 CameraPosition;
};

#define time (TimeIntensity.x)

in vec3 vRay;
in vec2 TexCoord;

out vec4 OutColor;

mat2 mm2(in float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, s, -s, c);
}

const mat2 m2 = mat2(0.95534, 0.29552, -0.29552, 0.95534);

float tri(in float x) {
    return clamp(abs(fract(x) - .5), 0.01, 0.49);
}

vec2 tri2(in vec2 p) {
    return vec2(tri(p.x) + tri(p.y), tri(p.y + tri(p.x)));
}

float hash21(in vec2 n) {
    return fract(sin(dot(n, vec2(12.9898, 4.1414))) * 43758.5453);
}

float triNoise2d(in vec2 p, mat2 rot) {
    float z = 1.8;
    float invZ2 = 0.4;
    float rz = 0.;
    p *= mm2(p.x * 0.06);
    vec2 bp = p;
    for (float i = 0.; i < 4.; i++) {
        vec2 dg = tri2(bp * 1.85) * .75;
        dg *= rot;
        p -= dg * invZ2;

        bp *= 1.3;
        invZ2 *= 2.2222222;
        z *= .42;
        p *= 1.21 + (rz - 1.0) * .02;

        rz += tri(p.x + tri(p.y)) * z;
        p *= -m2;
    }
    return clamp(1. / pow(rz * 29., 1.3), 0., .55);
}

const vec3 AURORA_LAYER[25] = vec3[25](
    vec3(0.000000, 0.800000, 0.000000),
    vec3(0.000292, 0.805278, 0.056864),
    vec3(0.001052, 0.813929, 0.132271),
    vec3(0.002112, 0.824572, 0.134904),
    vec3(0.003300, 0.836758, 0.123279),
    vec3(0.004444, 0.850238, 0.112656),
    vec3(0.005376, 0.864846, 0.102949),
    vec3(0.005924, 0.880465, 0.094078),
    vec3(0.006000, 0.897006, 0.085971),
    vec3(0.006000, 0.914396, 0.078563),
    vec3(0.006000, 0.932578, 0.071794),
    vec3(0.006000, 0.951503, 0.065607),
    vec3(0.006000, 0.971130, 0.059954),
    vec3(0.006000, 0.991422, 0.054788),
    vec3(0.006000, 1.012349, 0.050067),
    vec3(0.006000, 1.033884, 0.045753),
    vec3(0.006000, 1.056000, 0.041810),
    vec3(0.006000, 1.078677, 0.038208),
    vec3(0.006000, 1.101893, 0.034915),
    vec3(0.006000, 1.125632, 0.031907),
    vec3(0.006000, 1.149876, 0.029157),
    vec3(0.006000, 1.174610, 0.026645),
    vec3(0.006000, 1.199819, 0.024349),
    vec3(0.006000, 1.225492, 0.022251),
    vec3(0.006000, 1.251614, 0.020333)
);

vec4 aurora(vec3 ro, vec3 rd) {
    vec4 col = vec4(0);
    vec4 avgCol = vec4(0);

    mat2 rot = mm2(time * 0.06);
    float h = hash21(gl_FragCoord.xy);
    float invDen = 1. / (rd.y * 2. + 0.4);

    vec3 layerColor = FirstColor.rgb;
    vec3 colorStep = (SecondColor.rgb - FirstColor.rgb) * (1.0 / 24.0);

    for (int i = 0; i < 25; i++) {
        vec3 layer = AURORA_LAYER[i];
        float pt = (layer.y - ro.y) * invDen - h * layer.x;
        vec3 bpos = ro + pt * rd;
        float rzt = triNoise2d(bpos.zx, rot);
        avgCol = mix(avgCol, vec4(layerColor * rzt, rzt), .5);
        col += avgCol * layer.z;
        layerColor += colorStep;
    }

    col *= (clamp(rd.y * 15. + .4, 0., 1.));
    return col * 3.6;
}

void main() {
    vec3 rd = normalize(vRay);
    rd.y = abs(rd.y);

    float fade = smoothstep(0.0, 0.01, rd.y) * 0.1 + 0.9;
    vec3 ro = vec3(0.0, 0.0, -6.7);
    OutColor = smoothstep(0.0, 1.5, aurora(ro, rd)) * fade;
}
