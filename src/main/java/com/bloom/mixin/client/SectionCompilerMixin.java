package com.bloom.mixin.client;

import com.bloom.client.selection.BloomSelection;
import com.bloom.client.selection.BloomSelectionState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
	@Redirect(
		method = "compile",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderLiquid(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"
		)
	)
	private void bloom$markSelectedFluid(
		BlockRenderDispatcher dispatcher,
		BlockPos blockPos,
		BlockAndTintGetter level,
		VertexConsumer vertexConsumer,
		BlockState blockState,
		FluidState fluidState
	) {
		double previous = BloomSelectionState.pushFluidStrength(BloomSelection.getFluidSourceStrength(fluidState));
		try {
			dispatcher.renderLiquid(blockPos, level, vertexConsumer, blockState, fluidState);
		} finally {
			BloomSelectionState.popFluidStrength(previous);
		}
	}

	@Redirect(
		method = "compile",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLjava/util/List;)V"
		)
	)
	private void bloom$markSelectedBlock(
		BlockRenderDispatcher dispatcher,
		BlockState blockState,
		BlockPos blockPos,
		BlockAndTintGetter level,
		PoseStack poseStack,
		VertexConsumer vertexConsumer,
		boolean checkSides,
		List<BlockModelPart> parts
	) {
		double previousStrength = BloomSelectionState.pushBlockStrength(BloomSelection.getBlockSourceStrength(blockState));
		try {
			dispatcher.renderBatched(blockState, blockPos, level, poseStack, vertexConsumer, checkSides, parts);
		} finally {
			BloomSelectionState.popBlockStrength(previousStrength);
		}
	}
}
