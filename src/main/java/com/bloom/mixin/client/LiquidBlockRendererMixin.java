package com.bloom.mixin.client;

import com.bloom.client.selection.BloomSelectionState;
import com.bloom.client.selection.BloomSourceEncoding;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererMixin {
	@Redirect(
		method = "tesselate",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
			ordinal = 0
		)
	)
	private int bloom$lightColor0(LiquidBlockRenderer instance, BlockAndTintGetter level, BlockPos blockPos) {
		return bloom$sourceStrengthFluidOrVanilla(level, blockPos);
	}

	@Redirect(
		method = "tesselate",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
			ordinal = 1
		)
	)
	private int bloom$lightColor1(LiquidBlockRenderer instance, BlockAndTintGetter level, BlockPos blockPos) {
		return bloom$sourceStrengthFluidOrVanilla(level, blockPos);
	}

	@Redirect(
		method = "tesselate",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
			ordinal = 2
		)
	)
	private int bloom$lightColor2(LiquidBlockRenderer instance, BlockAndTintGetter level, BlockPos blockPos) {
		return bloom$sourceStrengthFluidOrVanilla(level, blockPos);
	}

	private static int bloom$sourceStrengthFluidOrVanilla(BlockAndTintGetter level, BlockPos blockPos) {
		int vanilla = bloom$vanillaLightColor(level, blockPos);
		double sourceStrength = BloomSelectionState.getFluidStrength();
		return BloomSourceEncoding.encodePackedLight(vanilla, sourceStrength);
	}

	private static int bloom$vanillaLightColor(BlockAndTintGetter level, BlockPos blockPos) {
		int lower = LevelRenderer.getLightColor(level, blockPos);
		int upper = LevelRenderer.getLightColor(level, blockPos.above());
		int blockLower = lower & 0xFF;
		int blockUpper = upper & 0xFF;
		int skyLower = lower >> 16 & 0xFF;
		int skyUpper = upper >> 16 & 0xFF;
		return (blockLower > blockUpper ? blockLower : blockUpper) | (skyLower > skyUpper ? skyLower : skyUpper) << 16;
	}

}
