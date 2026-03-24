package com.bloom.mixin.client.sodium;

import com.bloom.client.selection.BloomSelection;
import com.bloom.client.selection.BloomSelectionState;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultFluidRenderer.class, remap = false)
public abstract class SodiumDefaultFluidRendererMixin {
	@Unique
	private double bloom$previousFluidStrength;

	@Inject(method = "render", at = @At("HEAD"), require = 0)
	private void bloom$pushFluidStrength(
		LevelSlice level,
		BlockState blockState,
		FluidState fluidState,
		BlockPos blockPos,
		BlockPos modelOffset,
		TranslucentGeometryCollector collector,
		ChunkModelBuilder modelBuilder,
		Material material,
		ColorProvider<FluidState> colorProvider,
		TextureAtlasSprite[] sprites,
		CallbackInfo ci
	) {
		this.bloom$previousFluidStrength = BloomSelectionState.pushFluidStrength(BloomSelection.getFluidSourceStrength(fluidState));
	}

	@Inject(method = "render", at = @At("RETURN"), require = 0)
	private void bloom$popFluidStrength(
		LevelSlice level,
		BlockState blockState,
		FluidState fluidState,
		BlockPos blockPos,
		BlockPos modelOffset,
		TranslucentGeometryCollector collector,
		ChunkModelBuilder modelBuilder,
		Material material,
		ColorProvider<FluidState> colorProvider,
		TextureAtlasSprite[] sprites,
		CallbackInfo ci
	) {
		BloomSelectionState.popFluidStrength(this.bloom$previousFluidStrength);
	}
}
