package com.bloom.mixin.client.sodium;

import com.bloom.client.selection.BloomSelectionState;
import com.bloom.client.selection.BloomSourceEncoding;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ChunkMeshBufferBuilder.class, remap = false)
public abstract class SodiumChunkMeshBufferBuilderMixin {
	@ModifyVariable(
		method = "push([Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;I)V",
		at = @At("HEAD"),
		argsOnly = true,
		require = 0
	)
	private int bloom$encodeSourceStrengthInMaterialBits(int materialBits) {
		double sourceStrength = Math.max(BloomSelectionState.getBlockStrength(), BloomSelectionState.getFluidStrength());
		return BloomSourceEncoding.encodeMaterialBits(materialBits, sourceStrength);
	}
}
