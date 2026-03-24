package com.bloom.mixin.client.sodium;

import com.bloom.client.selection.BloomSelection;
import com.bloom.client.selection.BloomSelectionState;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockRenderer.class, remap = false)
public abstract class SodiumBlockRendererMixin {
	@Unique
	private double bloom$previousBlockStrength;

	@Inject(method = "renderModel", at = @At("HEAD"), require = 0)
	private void bloom$pushBlockStrength(BlockStateModel model, BlockState blockState, BlockPos blockPos, BlockPos modelOffset, CallbackInfo ci) {
		this.bloom$previousBlockStrength = BloomSelectionState.pushBlockStrength(BloomSelection.getBlockSourceStrength(blockState));
	}

	@Inject(method = "renderModel", at = @At("RETURN"), require = 0)
	private void bloom$popBlockStrength(BlockStateModel model, BlockState blockState, BlockPos blockPos, BlockPos modelOffset, CallbackInfo ci) {
		BloomSelectionState.popBlockStrength(this.bloom$previousBlockStrength);
	}
}
