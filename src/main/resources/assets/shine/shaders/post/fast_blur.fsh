#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
    vec2 BlurDir;
    float Radius;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    if (Radius < 0.5 || dot(BlurDir, BlurDir) < 1e-6) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    vec2 oneTexel = 1.0 / InSize;
    vec2 sampleStep = oneTexel * BlurDir;
    float actualRadius = round(Radius);

    vec4 blurred = vec4(0.0);
    for (float a = -actualRadius; a <= actualRadius; a += 1.0) {
        blurred += texture(InSampler, texCoord + sampleStep * a);
    }
    fragColor = blurred / (actualRadius * 2.0 + 1.0);
}
