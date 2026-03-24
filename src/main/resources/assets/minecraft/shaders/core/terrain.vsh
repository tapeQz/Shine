#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out float bloomStrength;

vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

float shine_decode_bloom_strength(ivec2 uv2) {
    int blockNibble = uv2.x & 0xF;
    int skyNibble = uv2.y & 0xF;
    int legacy = (blockNibble & 0x7) | ((skyNibble & 0x7) << 3);
    int mode = ((blockNibble >> 3) & 1) | (((skyNibble >> 3) & 1) << 1);
    if (mode == 0) {
        return float(legacy) / 63.0;
    }

    int code = (mode - 1) * 64 + legacy + 1;
    float t = float(code - 1) / 191.0;
    return 1.0 + t * 4.0;
}

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    bloomStrength = shine_decode_bloom_strength(UV2);
}
