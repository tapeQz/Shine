package com.bloom.client.selection;

public final class BloomSelectionState {
	private static final ThreadLocal<Double> BLOCK_STRENGTH = ThreadLocal.withInitial(() -> 0.0);
	private static final ThreadLocal<Double> FLUID_STRENGTH = ThreadLocal.withInitial(() -> 0.0);

	private BloomSelectionState() {
	}

	public static double pushBlockStrength(double strength) {
		double previous = BLOCK_STRENGTH.get();
		BLOCK_STRENGTH.set(strength);
		return previous;
	}

	public static void popBlockStrength(double previous) {
		BLOCK_STRENGTH.set(previous);
	}

	public static double getBlockStrength() {
		return BLOCK_STRENGTH.get();
	}

	public static double pushFluidStrength(double strength) {
		double previous = FLUID_STRENGTH.get();
		FLUID_STRENGTH.set(strength);
		return previous;
	}

	public static void popFluidStrength(double previous) {
		FLUID_STRENGTH.set(previous);
	}

	public static double getFluidStrength() {
		return FLUID_STRENGTH.get();
	}
}
