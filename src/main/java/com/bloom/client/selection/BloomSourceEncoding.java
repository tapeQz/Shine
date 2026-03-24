package com.bloom.client.selection;

import com.bloom.client.config.BloomConfig;

public final class BloomSourceEncoding {
	private static final int LEGACY_BITS_PER_AXIS = 3;
	private static final int LEGACY_AXIS_MASK = (1 << LEGACY_BITS_PER_AXIS) - 1;
	private static final int LEGACY_MAX = (1 << (LEGACY_BITS_PER_AXIS * 2)) - 1;
	private static final int LOW_NIBBLE_MASK = 0xF;
	private static final int SKY_LOW_NIBBLE_SHIFT = 16;
	private static final int SKY_EXTRA_MODE_SHIFT = 1;
	private static final int EXTENDED_MODE_MAX = 3;
	private static final int EXTENDED_STEPS_PER_MODE = LEGACY_MAX + 1;
	private static final int EXTENDED_TOTAL_STEPS = EXTENDED_MODE_MAX * EXTENDED_STEPS_PER_MODE;
	private static final int MATERIAL_BASE_FLAGS_MASK = 0x7;
	private static final int MATERIAL_SOURCE_CODE_SHIFT = 3;
	private static final int MATERIAL_SOURCE_CODE_MASK = 0x1F;
	private static final int MATERIAL_SOURCE_BITS_MASK = MATERIAL_SOURCE_CODE_MASK << MATERIAL_SOURCE_CODE_SHIFT;
	private static final int LEGACY_SOURCE_CODE_MAX = 20;
	private static final int LEGACY_SOURCE_STEP = 5;
	private static final int EXTENDED_SOURCE_CODE_COUNT = MATERIAL_SOURCE_CODE_MASK - LEGACY_SOURCE_CODE_MAX;
	private static final double EXTENDED_SOURCE_STEP = (BloomConfig.MAX_SOURCE_STRENGTH - 100.0) / EXTENDED_SOURCE_CODE_COUNT;

	private BloomSourceEncoding() {
	}

	public static int encodePackedLight(int packedLight, double sourceStrength) {
		double clamped = Math.max(BloomConfig.MIN_SOURCE_STRENGTH, Math.min(BloomConfig.MAX_SOURCE_STRENGTH, sourceStrength));
		int low6;
		int mode;

		if (clamped <= 100.0) {
			// Preserve legacy behavior exactly for the old range [0, 100].
			low6 = (int) Math.round((clamped / 100.0) * LEGACY_MAX);
			mode = 0;
		} else {
			// Extended range [100, 500] is encoded into 192 extra steps using two mode bits + 6 legacy bits.
			double t = (clamped - 100.0) / (BloomConfig.MAX_SOURCE_STRENGTH - 100.0);
			int code = 1 + (int) Math.ceil(t * (EXTENDED_TOTAL_STEPS - 1));
			mode = ((code - 1) / EXTENDED_STEPS_PER_MODE) + 1;
			low6 = (code - 1) % EXTENDED_STEPS_PER_MODE;
		}

		int blockNibble = (low6 & LEGACY_AXIS_MASK) | ((mode & 1) << 3);
		int skyNibble = ((low6 >>> LEGACY_BITS_PER_AXIS) & LEGACY_AXIS_MASK) | (((mode >>> 1) & 1) << 3);
		int cleared = packedLight & ~LOW_NIBBLE_MASK & ~(LOW_NIBBLE_MASK << SKY_LOW_NIBBLE_SHIFT);
		return cleared | blockNibble | (skyNibble << SKY_LOW_NIBBLE_SHIFT);
	}

	public static int encodeMaterialBits(int materialBits, double sourceStrength) {
		double clamped = Math.max(BloomConfig.MIN_SOURCE_STRENGTH, Math.min(BloomConfig.MAX_SOURCE_STRENGTH, sourceStrength));
		int sourceCode;
		if (clamped <= 100.0) {
			sourceCode = (int) Math.round(clamped / LEGACY_SOURCE_STEP);
			sourceCode = Math.max(0, Math.min(LEGACY_SOURCE_CODE_MAX, sourceCode));
		} else {
			sourceCode = LEGACY_SOURCE_CODE_MAX + (int) Math.round((clamped - 100.0) / EXTENDED_SOURCE_STEP);
			sourceCode = Math.max(LEGACY_SOURCE_CODE_MAX, Math.min(MATERIAL_SOURCE_CODE_MASK, sourceCode));
		}

		int preservedFlags = materialBits & MATERIAL_BASE_FLAGS_MASK;
		int clearedSourceBits = materialBits & ~MATERIAL_SOURCE_BITS_MASK & ~MATERIAL_BASE_FLAGS_MASK;
		return clearedSourceBits | preservedFlags | (sourceCode << MATERIAL_SOURCE_CODE_SHIFT);
	}

	public static int decodeLegacy6Bit(int blockNibble, int skyNibble) {
		return (blockNibble & LEGACY_AXIS_MASK) | ((skyNibble & LEGACY_AXIS_MASK) << LEGACY_BITS_PER_AXIS);
	}

	public static int decodeExtendedMode(int blockNibble, int skyNibble) {
		return ((blockNibble >>> 3) & 1) | (((skyNibble >>> 3) & 1) << SKY_EXTRA_MODE_SHIFT);
	}

	public static int legacyMax() {
		return LEGACY_MAX;
	}

	public static int extendedStepsPerMode() {
		return EXTENDED_STEPS_PER_MODE;
	}

	public static int extendedTotalSteps() {
		return EXTENDED_TOTAL_STEPS;
	}
}
