package com.bloom.client.selection;

import com.bloom.client.config.BloomConfig;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class BloomSelection {
	private static final Set<Identifier> SELECTED_FLUID_IDS = Set.of(
		Identifier.parse("minecraft:lava"),
		Identifier.parse("minecraft:flowing_lava")
	);

	private BloomSelection() {
	}

	public static double getBlockSourceStrength(BlockState blockState) {
		BloomConfig.Data config = BloomConfig.get();
		Identifier blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
		Double override = config.blockStrengthOverrides.get(blockId.toString());
		if (override != null) {
			return clampSourceStrength(override);
		}

		double fallback = blockState.getLightEmission() > 0 ? config.defaultLightSourceStrength : config.defaultNonLightStrength;
		return clampSourceStrength(fallback);
	}

	public static double getFluidSourceStrength(FluidState fluidState) {
		if (fluidState.isEmpty()) {
			return 0.0;
		}

		BloomConfig.Data config = BloomConfig.get();
		Identifier fluidId = BuiltInRegistries.FLUID.getKey(fluidState.getType());
		Double override = config.blockStrengthOverrides.get(fluidId.toString());
		if (override != null) {
			return clampSourceStrength(override);
		}

		Block legacyFluidBlock = fluidState.createLegacyBlock().getBlock();
		Identifier legacyBlockId = BuiltInRegistries.BLOCK.getKey(legacyFluidBlock);
		if (legacyBlockId != null) {
			Double legacyOverride = config.blockStrengthOverrides.get(legacyBlockId.toString());
			if (legacyOverride != null) {
				return clampSourceStrength(legacyOverride);
			}
		}

		double fallback = SELECTED_FLUID_IDS.contains(fluidId) ? config.defaultLightSourceStrength : config.defaultNonLightStrength;
		return clampSourceStrength(fallback);
	}

	private static double clampSourceStrength(double value) {
		return Math.max(BloomConfig.MIN_SOURCE_STRENGTH, Math.min(BloomConfig.MAX_SOURCE_STRENGTH, value));
	}
}
