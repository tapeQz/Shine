package com.bloom.mixin.client;

import com.bloom.client.render.BloomSourceRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.systems.RenderPass;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkSectionsToRender.class)
public abstract class ChunkSectionsToRenderMixin {
	@Shadow
	public abstract GpuTextureView textureView();

	@Shadow
	public abstract EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> drawsPerLayer();

	@Shadow
	public abstract int maxIndicesRequired();

	@Shadow
	public abstract GpuBufferSlice[] chunkSectionInfos();

	@Inject(method = "renderGroup", at = @At("RETURN"))
	private void shine$renderBloomSource(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
		if (group == ChunkSectionLayerGroup.OPAQUE) {
			BloomSourceRenderer.replayVanillaOpaqueGroup(
				group,
				sampler,
				this.textureView(),
				this.drawsPerLayer(),
				this.maxIndicesRequired(),
				this.chunkSectionInfos()
			);
		}
	}
}
