#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

out vec4 fragColor;

vec4 four_k(vec3 texel, vec2 uv) {
    return (
        texture(InSampler, uv + texel.xx) +
        texture(InSampler, uv + texel.xy) +
        texture(InSampler, uv + texel.yx) +
        texture(InSampler, uv + texel.yy)
    ) * 0.25;
}

void main() {
    vec2 uv = gl_FragCoord.xy / OutSize;
    vec3 texelIn = vec3(1.0, -1.0, 0.0) / InSize.xyx;
    vec3 texelOut = vec3(1.0, -1.0, 0.0) / OutSize.xyx;

    vec4 color = (four_k(texelIn, uv + texelOut.yy)
        + four_k(texelIn, uv + texelOut.zy)
        + four_k(texelIn, uv + texelOut.yz)
        + four_k(texelIn, uv)) * 0.25 * 0.125;

    color += (four_k(texelIn, uv + texelOut.xy)
        + four_k(texelIn, uv + texelOut.zy)
        + four_k(texelIn, uv + texelOut.xz)
        + four_k(texelIn, uv)) * 0.25 * 0.125;

    color += (four_k(texelIn, uv + texelOut.yx)
        + four_k(texelIn, uv + texelOut.yz)
        + four_k(texelIn, uv + texelOut.zx)
        + four_k(texelIn, uv)) * 0.25 * 0.125;

    color += (four_k(texelIn, uv + texelOut.xx)
        + four_k(texelIn, uv + texelOut.xz)
        + four_k(texelIn, uv + texelOut.zx)
        + four_k(texelIn, uv)) * 0.25 * 0.125;

    color += (four_k(texelIn, uv + texelIn.xx)
        + four_k(texelIn, uv + texelIn.xy)
        + four_k(texelIn, uv + texelIn.yx)
        + four_k(texelIn, uv + texelIn.yy)) * 0.25 * 0.5;

    fragColor = vec4(color.rgb, 1.0);
}
