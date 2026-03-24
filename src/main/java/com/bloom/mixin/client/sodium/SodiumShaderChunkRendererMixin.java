package com.bloom.mixin.client.sodium;

import com.bloom.client.render.BloomSourceRenderer;
import com.mojang.blaze3d.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShaderChunkRenderer.class, remap = false)
public abstract class SodiumShaderChunkRendererMixin {
	@Inject(method = "begin", at = @At("HEAD"), require = 0)
	private void shine$enableBloomAttachment(TerrainRenderPass pass, FogParameters fogParameters, GpuSampler mipSampler, CallbackInfo ci) {
		if (!pass.isTranslucent()) {
			BloomSourceRenderer.enableBloomDrawBuffers(pass.getTarget());
		}
	}

	@Inject(method = "end", at = @At("RETURN"), require = 0)
	private void shine$disableBloomAttachment(TerrainRenderPass pass, CallbackInfo ci) {
		if (!pass.isTranslucent()) {
			BloomSourceRenderer.disableBloomDrawBuffers(pass.getTarget());
		}
	}

	@Redirect(
		method = "compileProgram",
		at = @At(
			value = "INVOKE",
			target = "Lnet/caffeinemc/mods/sodium/client/gl/shader/GlProgram$Builder;bindFragmentData(Ljava/lang/String;I)Lnet/caffeinemc/mods/sodium/client/gl/shader/GlProgram$Builder;"
		),
		require = 0
	)
	private net.caffeinemc.mods.sodium.client.gl.shader.GlProgram.Builder shine$bindBloomFragmentOutput(
		net.caffeinemc.mods.sodium.client.gl.shader.GlProgram.Builder builder,
		String name,
		int index
	) {
		return builder.bindFragmentData(name, index).bindFragmentData("bloomColor", 1);
	}
}
