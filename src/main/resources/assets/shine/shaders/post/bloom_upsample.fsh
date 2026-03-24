#version 330

uniform sampler2D InSampler;
uniform sampler2D DownSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 DownSize;
};

layout(std140) uniform BloomUpsampleConfig {
    float SmallWeight;
    float LargeWeight;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 texel = vec3(1.0, -1.0, 0.0) / OutSize.xyx;
    vec3 small = texture(InSampler, texCoord + texel.xx).rgb;
    small += texture(InSampler, texCoord + texel.xz).rgb * 2.0;
    small += texture(InSampler, texCoord + texel.xy).rgb;
    small += texture(InSampler, texCoord + texel.yz).rgb * 2.0;
    small += texture(InSampler, texCoord).rgb * 4.0;
    small += texture(InSampler, texCoord + texel.zx).rgb * 2.0;
    small += texture(InSampler, texCoord + texel.yy).rgb;
    small += texture(InSampler, texCoord + texel.zy).rgb * 2.0;
    small += texture(InSampler, texCoord + texel.yx).rgb;
    small *= 0.8 / 16.0;

    vec3 large = texture(DownSampler, texCoord).rgb;
    fragColor = vec4(small * SmallWeight + large * LargeWeight, 1.0);
}
