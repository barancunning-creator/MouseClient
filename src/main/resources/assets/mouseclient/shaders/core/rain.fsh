#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec3 vRay;
in vec2 TexCoord;

layout(std140) uniform SkyParams {
    mat4 InvViewProj;
    vec4 Color; //xyz = позиция камеры в мире (переиспользованный свободный слот, имя блока обязано совпадать с core/sky.vsh)
    vec4 Params;
};

out vec4 OutColor;

float hash(float n) {
    return fract(sin(n * 127.1) * 43758.5453);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    vec3 base = texture(Sampler0, TexCoord).rgb;
/*
    vec3 dir = normalize(vRay);
//    vec2 uv = vec2(atan(dir.z, dir.x) * 4.0, TexCoord.y);
    vec2 uv = vec2(atan(dir.z, dir.x), atan(dir.y, length(dir.xz)));
//    float slantX = uv.x + uv.y * 1.5;
    float slantX = uv.x + uv.y * 0.25;
    float rain = 0.0;
    for (float layer = 0.0; layer < 3.0; layer += 1.0) {
//        float density = 40.0 + layer * 25.0;
        float density = 60.0 + layer * 35.0;
//        float colSeed = hash(floor(uv.x * density) + layer * 41.0);
        float colSeed = hash(floor(slantX * density) + layer * 41.0);
        float speedSeed = hash(floor(slantX * density) + layer * 41.0 + 7.3);
//        float head = fract(uv.y * (1.5 + layer * 0.5) + Params.x * (0.9 + layer * 0.6) + colSeed * 23.0);
//        float head = fract(uv.y * (1.5 + layer * 0.5) + Params.x * (0.9 + layer * 0.6) * (0.5 + speedSeed) + colSeed * 23.0);
        float head = fract(uv.y * (3.0 + layer * 1.2) + Params.x * (0.9 + layer * 0.6) * (0.5 + speedSeed) + colSeed * 23.0);
//        rain += pow(1.0 - head, 16.0) * smoothstep(0.5, 0.0, abs(fract(uv.x * density) - 0.5)) * step(1.0 - Params.y, colSeed) * (0.5 - layer * 0.13);
        rain += pow(1.0 - head, 16.0) * smoothstep(0.5, 0.0, abs(fract(slantX * density) - 0.5)) * step(1.0 - Params.y * 0.25, colSeed) * (0.5 - layer * 0.13);
    }
//    rain *= smoothstep(0.92, 0.35, abs(dir.y));
    rain *= smoothstep(0.98, 0.12, abs(dir.y));
    OutColor = vec4(base + vec3(0.6, 0.72, 0.92) * clamp(rain, 0.0, 0.65) * Params.y, 1.0);
*/
    vec3 rd = normalize(vRay);
    float sceneDepth = texture(Sampler1, TexCoord).r;
    vec4 sceneClip = InvViewProj * vec4(TexCoord * 2.0 - 1.0, sceneDepth * 2.0 - 1.0, 1.0);
    float sceneDist = sceneDepth >= 1.0 ? 100000.0 : length(sceneClip.xyz / sceneClip.w);
    float rain = 0.0;
    for (int slab = 0; slab < 14; slab++) {
        float dist = 1.0 + float(slab) * 2.0;
        if (dist > sceneDist) break;
        vec3 wp = Color.xyz + rd * dist;
        vec2 cell = floor(wp.xz * 3.0);
        float active = hash2(cell + float(slab) * 23.0);
        if (active < 1.0 - Params.y * 0.05) continue;
        float dropSpeed = 9.0 * (0.5 + hash2(cell + 4.4));
        float dropHeadY = Color.y + 9.0 - fract(Params.x * dropSpeed * 0.05 + active * 12.0) * 18.0;
        vec2 dropXZ = (cell + vec2(hash2(cell + 1.7), hash2(cell + 9.1))) / 3.0;
        float horizPc = length(wp.xz - dropXZ) * 3.0;
        float alongPc = clamp((wp.y - dropHeadY) / 1.6, 0.0, 1.0);
        rain += smoothstep(0.5, 0.0, horizPc) * (1.0 - alongPc) * step(dropHeadY, wp.y) * (1.0 - float(slab) / 14.0);
    }
    OutColor = vec4(base + vec3(0.6, 0.72, 0.92) * clamp(rain, 0.0, 0.6) * Params.y, 1.0);
}
