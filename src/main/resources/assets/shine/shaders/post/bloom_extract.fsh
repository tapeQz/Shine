#version 330

uniform sampler2D DepthSampler;
uniform sampler2D SourceSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 DepthSize;
    vec2 SourceSize;
};

layout(std140) uniform BloomExtractConfig {
    float Threshold;
    float HighlightClamp;
    float SoftKnee;
    float MaxDistance;
    float NearPlane;
    float FarPlane;
    float SourceStrengthScale;
    float DistanceFadeRange;
};

out vec4 fragColor;

float linearize_depth(float depth) {
    float zNdc = depth * 2.0 - 1.0;
    float denominator = FarPlane + NearPlane - zNdc * (FarPlane - NearPlane);
    return (2.0 * NearPlane * FarPlane) / max(denominator, 1.0e-6);
}

void main() {
    vec2 uv = gl_FragCoord.xy / OutSize;
    float depth = texture(DepthSampler, uv).r;
    if (depth >= 0.9999) {
        fragColor = vec4(0.0);
        return;
    }

    vec4 source = texture(SourceSampler, uv);
    float encodedStrength = clamp(source.a, 0.0, 1.0);
    if (encodedStrength <= 1.0e-5) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 rawSourceColor = source.rgb;
    float rawBrightness = max(max(rawSourceColor.r, rawSourceColor.g), rawSourceColor.b);
    if (rawBrightness <= 1.0e-6) {
        fragColor = vec4(0.0);
        return;
    }

    float clampedRawBrightness = min(rawBrightness, HighlightClamp);
    float highlightScale = clampedRawBrightness / rawBrightness;
    float bloomMask = smoothstep(Threshold, Threshold + max(SoftKnee, 1.0e-4), clampedRawBrightness);
    float sceneDistance = linearize_depth(depth);
    float distanceMask = 1.0 - smoothstep(MaxDistance, MaxDistance + DistanceFadeRange, sceneDistance);
    vec3 maskedRawBloom = rawSourceColor * highlightScale * bloomMask;
    float sourceStrength = encodedStrength * SourceStrengthScale;
    vec3 bloomColor = maskedRawBloom * sourceStrength * distanceMask;
    fragColor = vec4(bloomColor, 1.0);
}
