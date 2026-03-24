package com.bloom.mixin.client.sodium;

import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShaderLoader.class, remap = false)
public abstract class SodiumShaderLoaderMixin {
	@Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, require = 0)
	private static void shine$injectBloomOutput(Identifier name, CallbackInfoReturnable<String> cir) {
		String shader = cir.getReturnValue();
		if (shader == null || !"sodium".equals(name.getNamespace()) || !"blocks/block_layer_opaque.fsh".equals(name.getPath())) {
			return;
		}
		if (shader.contains("bloomColor") || !shader.contains("out vec4 fragColor")) {
			return;
		}

		String injected = shader.replace(
			"out vec4 fragColor; // The output fragment for the color framebuffer",
			"out vec4 fragColor; // The output fragment for the color framebuffer\nout vec4 bloomColor;"
		);
		injected = injected.replace(
			"void main() {",
			"""
			float shine_decode_source_strength(uint packedMaterial) {
			    uint sourceCode = (packedMaterial >> 3u) & 0x1Fu;
			    if (sourceCode <= 20u) {
			        return float(sourceCode) * 0.05;
			    }
			    return 1.0 + float(sourceCode - 20u) * (4.0 / 11.0);
			}

			void main() {
			"""
		);
		injected = injected.replace(
			"    fragColor = _linearFog(color, v_FragDistance, u_FogColor, u_EnvironmentFog, u_RenderFog, fadeFactor);\n}",
			"""
			    fragColor = _linearFog(color, v_FragDistance, u_FogColor, u_EnvironmentFog, u_RenderFog, fadeFactor);
			    float bloomStrength = shine_decode_source_strength(v_Material);
			    if (bloomStrength <= 1.0e-5) {
			        bloomColor = vec4(0.0);
			    } else {
			        float fogValue = max(1.0 - fadeFactor, total_fog_value(v_FragDistance.y, v_FragDistance.x, u_EnvironmentFog.x, u_EnvironmentFog.y, u_RenderFog.x, u_RenderFog.y));
			        float fogAttenuation = 1.0 - fogValue;
			        bloomColor = vec4(fragColor.rgb * fragColor.a * fogAttenuation, clamp(bloomStrength / 5.0, 0.0, 1.0));
			    }
			}
			"""
		);
		cir.setReturnValue(injected);
	}
}
