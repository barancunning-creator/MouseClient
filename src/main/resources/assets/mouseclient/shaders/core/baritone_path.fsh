#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in float pathPhase;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    float pulse = 0.985 + sin(pathPhase * 6.2831853) * 0.015;
    float smokeA = sin(gl_FragCoord.x * 0.071 + pathPhase * 2.1);
    float smokeB = sin(gl_FragCoord.y * 0.053 - pathPhase * 1.7);
    float smoke = 0.94 + smokeA * smokeB * 0.06;
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    vec3 saturated = mix(vec3(luma), color.rgb, 1.06);
    float energy = smoothstep(0.0, 0.8, color.a) * pulse * smoke;
    vec3 lit = saturated * (0.84 + energy * 0.12);
    vec4 shaded = vec4(clamp(lit, 0.0, 1.0), clamp(color.a * pulse * smoke, 0.0, 1.0));

    fragColor = apply_fog(shaded, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
