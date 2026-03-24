package com.bloom.mixin.client;

import com.bloom.client.selection.BloomSelectionState;
import com.bloom.client.selection.BloomSourceEncoding;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
	@Redirect(
		method = "putQuadData",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[II)V"
		)
	)
	private void bloom$forceSelectedBlockLight(
		VertexConsumer vertexConsumer,
		PoseStack.Pose pose,
		BakedQuad bakedQuad,
		float[] brightness,
		float red,
		float green,
		float blue,
		float alpha,
		int[] lightmaps,
		int packedOverlay
	) {
		double sourceStrength = BloomSelectionState.getBlockStrength();
		int[] encoded = lightmaps.clone();
		for (int i = 0; i < encoded.length; i++) {
			encoded[i] = BloomSourceEncoding.encodePackedLight(encoded[i], sourceStrength);
		}
		vertexConsumer.putBulkData(pose, bakedQuad, brightness, red, green, blue, alpha, encoded, packedOverlay);
	}
}
