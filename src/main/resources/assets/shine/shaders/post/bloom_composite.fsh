#version 330

uniform sampler2D MainSampler;
uniform sampler2D HalfSampler;
uniform sampler2D QuarterSampler;
uniform sampler2D EighthSampler;
uniform sampler2D SixteenthSampler;
uniform sampler2D ThirtysecondSampler;
uniform sampler2D SixtyfourthSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 MainSize;
    vec2 HalfSize;
    vec2 QuarterSize;
    vec2 EighthSize;
    vec2 SixteenthSize;
    vec2 ThirtysecondSize;
    vec2 SixtyfourthSize;
    vec2 DepthSize;
};

layout(std140) uniform BloomCompositeConfig {
    float Strength;
};

layout(std140) uniform BloomCompositeWeights {
    float Weight0;
    float Weight1;
    float Weight2;
    float Weight3;
    float Weight4;
    float Weight5;
    float WeightPad0;
    float WeightPad1;
};

layout(std140) uniform BloomCompositeDistanceConfig {
    float MaxDistance;
    float NearPlane;
    float FarPlane;
    float DistanceFadeRange;
};

out vec4 fragColor;

float linearize_depth(float depth) {
    float zNdc = depth * 2.0 - 1.0;
    float denominator = FarPlane + NearPlane - zNdc * (FarPlane - NearPlane);
    return (2.0 * NearPlane * FarPlane) / max(denominator, 1.0e-6);
}

vec3 centered_sample(sampler2D sampler, vec2 uv, vec2 levelSize) {
    vec2 halfTexelX = vec2(0.5 / max(levelSize.x, 1.0), 0.0);
    return (texture(sampler, uv - halfTexelX).rgb + texture(sampler, uv + halfTexelX).rgb) * 0.5;
}

void main() {
    vec2 uv = gl_FragCoord.xy / OutSize;
    vec4 sceneColor = texture(MainSampler, uv);
    float depth = texture(DepthSampler, uv).r;
    if (depth >= 0.9999) {
        fragColor = sceneColor;
        return;
    }

    float sceneDistance = linearize_depth(depth);
    float distanceMask = 1.0 - smoothstep(MaxDistance, MaxDistance + DistanceFadeRange, sceneDistance);
    vec3 bloomColor = centered_sample(HalfSampler, uv, HalfSize) * Weight0;
    bloomColor += centered_sample(QuarterSampler, uv, QuarterSize) * Weight1;
    bloomColor += centered_sample(EighthSampler, uv, EighthSize) * Weight2;
    bloomColor += centered_sample(SixteenthSampler, uv, SixteenthSize) * Weight3;
    bloomColor += centered_sample(ThirtysecondSampler, uv, ThirtysecondSize) * Weight4;
    bloomColor += centered_sample(SixtyfourthSampler, uv, SixtyfourthSize) * Weight5;
    bloomColor *= Strength * distanceMask;
    fragColor = vec4(sceneColor.rgb + bloomColor, sceneColor.a);
}
