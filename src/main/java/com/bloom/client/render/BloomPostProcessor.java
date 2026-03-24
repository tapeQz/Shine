package com.bloom.client.render;

import com.bloom.BloomMod;
import com.bloom.client.compat.IrisCompat;
import com.bloom.client.config.BloomConfig;
import com.bloom.mixin.client.accessor.PostChainAccessor;
import com.bloom.mixin.client.accessor.PostPassAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldTerrainRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;

public final class BloomPostProcessor {
	private static final Identifier EXTRACT_SHADER_ID = Identifier.fromNamespaceAndPath(BloomMod.MOD_ID, "post/bloom_extract");
	private static final Identifier DOWNSAMPLE_SHADER_ID = Identifier.fromNamespaceAndPath(BloomMod.MOD_ID, "post/bloom_downsample");
	private static final Identifier COMPOSITE_SHADER_ID = Identifier.fromNamespaceAndPath(BloomMod.MOD_ID, "post/bloom_composite");
	private static final Identifier SCREEN_QUAD_SHADER_ID = Identifier.withDefaultNamespace("core/screenquad");
	private static final Identifier RUNTIME_CHAIN_ID = Identifier.fromNamespaceAndPath(BloomMod.MOD_ID, "runtime_bloom");
	private static final Set<Identifier> EXTERNAL_TARGETS = Set.of(LevelTargetBundle.MAIN_TARGET_ID, BloomSourceRenderer.SOURCE_TARGET_ID);
	private static final CachedOrthoProjectionMatrixBuffer PROJECTION_BUFFER = new CachedOrthoProjectionMatrixBuffer("shine_runtime_post", 0.1F, 1000.0F, false);
	private static final int MAX_ACTIVE_LEVELS = BloomConfig.MAX_BLUR_PASSES + 3;
	private static final int DYNAMIC_UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE;
	private static final float UNIFORM_EPSILON = 1.0E-5F;
	private static final float DEFAULT_NEAR_PLANE = 0.05F;
	private static final float DISTANCE_FADE_RANGE = 2.0F;
	private static final float SOURCE_STRENGTH_SCALE = 5.0F;
	private static final float FIRST_EXTRA_LEVEL_RADIUS = 100.0F;
	private static final float SECOND_EXTRA_LEVEL_RADIUS = 200.0F;
	private static final float RADIUS_RESPONSE_EXPONENT = 1.5F;
	private static final double WEIGHT_DISTRIBUTION_DENOMINATOR = 1.15D;
	private static final float[] BASE_LEVEL_FACTORS = new float[] { 1.0F, 0.8F, 0.6F, 0.4F, 0.2F, 0.1F };
	private static final Identifier HALF_A_TARGET_ID = runtimeTarget("half_a");
	private static final Identifier QUARTER_A_TARGET_ID = runtimeTarget("quarter_a");
	private static final Identifier EIGHTH_A_TARGET_ID = runtimeTarget("eighth_a");
	private static final Identifier SIXTEENTH_A_TARGET_ID = runtimeTarget("sixteenth_a");
	private static final Identifier THIRTYSECOND_A_TARGET_ID = runtimeTarget("thirtysecond_a");
	private static final Identifier SIXTYFOURTH_A_TARGET_ID = runtimeTarget("sixtyfourth_a");

	private static boolean warnedChainLoadFailure;
	private static boolean warnedUniformWriteFailure;
	private static boolean irisCompatibilityDisabled;
	private static String irisCompatibilityMessage;
	private static boolean uniformsDirty = true;
	private static PostChain runtimeChain;
	private static int runtimeChainWidth = -1;
	private static int runtimeChainHeight = -1;
	private static int runtimeChainLevels = -1;
	private static final float[] lastExtractUniforms = new float[7];
	private static final float[] lastCompositeLevelWeights = new float[MAX_ACTIVE_LEVELS];
	private static float lastCompositeStrength;
	private static float lastCompositeMaxDistance;
	private static float lastCompositeNearPlane;
	private static float lastCompositeFarPlane;

	private BloomPostProcessor() {
	}

	public static boolean toggleFromKeybind() {
		BloomConfig.Data config = BloomConfig.get();
		config.enabled = !config.enabled;
		BloomConfig.save();
		return config.enabled;
	}

	public static void onConfigSaved() {
		warnedChainLoadFailure = false;
		warnedUniformWriteFailure = false;
		uniformsDirty = true;
		closeRuntimeChain();
		BloomSourceRenderer.reset();
	}

	public static void prepareSourceIfEnabled(WorldTerrainRenderContext context) {
		BloomConfig.Data config = BloomConfig.get();
		if (!config.enabled || shouldSkipForIrisCompatibility()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}

		BloomSourceRenderer.prepareSource(context);
	}

	public static void renderIfEnabled(WorldRenderContext context) {
		BloomConfig.Data config = BloomConfig.get();
		if (!config.enabled || shouldSkipForIrisCompatibility()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || config.strength <= 1.0E-4) {
			return;
		}

		RenderTarget bloomSourceTarget = BloomSourceRenderer.getSourceTarget();
		if (bloomSourceTarget == null || !BloomSourceRenderer.hasPreparedSourceThisFrame()) {
			return;
		}

		RenderTarget mainTarget = minecraft.getMainRenderTarget();
		int baseLevels = getBaseLevels(config.blurPassCount);
		int activeLevels = getActiveLevels(config.blurPassCount, config.radius);
		PostChain chain = ensureRuntimeChain(mainTarget.width, mainTarget.height, activeLevels);
		if (chain == null) {
			return;
		}

		applyConfigUniforms(chain, config, activeLevels, baseLevels);
		processChain(chain, mainTarget, bloomSourceTarget);
	}

	private static boolean shouldSkipForIrisCompatibility() {
		boolean disableForIris = IrisCompat.shouldDisableBloom();
		if (!disableForIris) {
			if (irisCompatibilityDisabled) {
				BloomMod.LOGGER.info("Shine bloom re-enabled: Iris compatibility no longer blocks bloom.");
				irisCompatibilityDisabled = false;
				irisCompatibilityMessage = null;
			}
			return false;
		}

		String message = IrisCompat.disableMessage();
		if (!irisCompatibilityDisabled || !Objects.equals(message, irisCompatibilityMessage)) {
			BloomMod.LOGGER.info(message);
			irisCompatibilityDisabled = true;
			irisCompatibilityMessage = message;
			closeRuntimeChain();
			BloomSourceRenderer.reset();
		}
		return true;
	}

	private static PostChain ensureRuntimeChain(int mainWidth, int mainHeight, int activeLevels) {
		if (
			runtimeChain != null
			&& runtimeChainWidth == mainWidth
			&& runtimeChainHeight == mainHeight
			&& runtimeChainLevels == activeLevels
		) {
			return runtimeChain;
		}

		closeRuntimeChain();
		Minecraft minecraft = Minecraft.getInstance();
		try {
			runtimeChain = PostChain.load(
				buildRuntimeConfig(mainWidth, mainHeight, activeLevels),
				minecraft.getTextureManager(),
				EXTERNAL_TARGETS,
				RUNTIME_CHAIN_ID.withSuffix("_" + activeLevels),
				PROJECTION_BUFFER
			);
			runtimeChainWidth = mainWidth;
			runtimeChainHeight = mainHeight;
			runtimeChainLevels = activeLevels;
			uniformsDirty = true;
			warnedChainLoadFailure = false;
			return runtimeChain;
		} catch (ShaderManager.CompilationException e) {
			if (!warnedChainLoadFailure) {
				BloomMod.LOGGER.warn("Shine runtime bloom chain could not be built.", e);
				warnedChainLoadFailure = true;
			}
			return null;
		}
	}

	private static PostChainConfig buildRuntimeConfig(int mainWidth, int mainHeight, int activeLevels) {
		int halfWidth = getScaledDimension(mainWidth, 0.5F);
		int halfHeight = getScaledDimension(mainHeight, 0.5F);
		int quarterWidth = getScaledDimension(mainWidth, 0.25F);
		int quarterHeight = getScaledDimension(mainHeight, 0.25F);
		int eighthWidth = getScaledDimension(mainWidth, 0.125F);
		int eighthHeight = getScaledDimension(mainHeight, 0.125F);
		int sixteenthWidth = getScaledDimension(mainWidth, 0.0625F);
		int sixteenthHeight = getScaledDimension(mainHeight, 0.0625F);
		int thirtysecondWidth = getScaledDimension(mainWidth, 0.03125F);
		int thirtysecondHeight = getScaledDimension(mainHeight, 0.03125F);
		int sixtyfourthWidth = getScaledDimension(mainWidth, 0.015625F);
		int sixtyfourthHeight = getScaledDimension(mainHeight, 0.015625F);

		Map<Identifier, PostChainConfig.InternalTarget> targets = new LinkedHashMap<>();
		targets.put(HALF_A_TARGET_ID, internalTarget(halfWidth, halfHeight));
		targets.put(QUARTER_A_TARGET_ID, internalTarget(quarterWidth, quarterHeight));
		if (activeLevels >= 3) {
			targets.put(EIGHTH_A_TARGET_ID, internalTarget(eighthWidth, eighthHeight));
		}
		if (activeLevels >= 4) {
			targets.put(SIXTEENTH_A_TARGET_ID, internalTarget(sixteenthWidth, sixteenthHeight));
		}
		if (activeLevels >= 5) {
			targets.put(THIRTYSECOND_A_TARGET_ID, internalTarget(thirtysecondWidth, thirtysecondHeight));
		}
		if (activeLevels >= 6) {
			targets.put(SIXTYFOURTH_A_TARGET_ID, internalTarget(sixtyfourthWidth, sixtyfourthHeight));
		}

		List<PostChainConfig.Pass> passes = new java.util.ArrayList<>();
		passes.add(
			postPass(
				EXTRACT_SHADER_ID,
				List.of(
					targetInput("Depth", LevelTargetBundle.MAIN_TARGET_ID, true, false),
					targetInput("Source", BloomSourceRenderer.SOURCE_TARGET_ID, false, true)
				),
				HALF_A_TARGET_ID,
				extractUniformDefaults()
			)
		);
		passes.add(postPass(DOWNSAMPLE_SHADER_ID, List.of(targetInput("In", HALF_A_TARGET_ID, false, true)), QUARTER_A_TARGET_ID, Map.of()));
		if (activeLevels >= 3) {
			passes.add(postPass(DOWNSAMPLE_SHADER_ID, List.of(targetInput("In", QUARTER_A_TARGET_ID, false, true)), EIGHTH_A_TARGET_ID, Map.of()));
		}
		if (activeLevels >= 4) {
			passes.add(postPass(DOWNSAMPLE_SHADER_ID, List.of(targetInput("In", EIGHTH_A_TARGET_ID, false, true)), SIXTEENTH_A_TARGET_ID, Map.of()));
		}
		if (activeLevels >= 5) {
			passes.add(postPass(DOWNSAMPLE_SHADER_ID, List.of(targetInput("In", SIXTEENTH_A_TARGET_ID, false, true)), THIRTYSECOND_A_TARGET_ID, Map.of()));
		}
		if (activeLevels >= 6) {
			passes.add(postPass(DOWNSAMPLE_SHADER_ID, List.of(targetInput("In", THIRTYSECOND_A_TARGET_ID, false, true)), SIXTYFOURTH_A_TARGET_ID, Map.of()));
		}

		passes.add(
			postPass(
				COMPOSITE_SHADER_ID,
				List.of(
					targetInput("Main", LevelTargetBundle.MAIN_TARGET_ID, false, false),
					targetInput("Half", compositeLevelTarget(0, activeLevels), false, true),
					targetInput("Quarter", compositeLevelTarget(1, activeLevels), false, true),
					targetInput("Eighth", compositeLevelTarget(2, activeLevels), false, true),
					targetInput("Sixteenth", compositeLevelTarget(3, activeLevels), false, true),
					targetInput("Thirtysecond", compositeLevelTarget(4, activeLevels), false, true),
					targetInput("Sixtyfourth", compositeLevelTarget(5, activeLevels), false, true),
					targetInput("Depth", LevelTargetBundle.MAIN_TARGET_ID, true, false)
				),
				LevelTargetBundle.MAIN_TARGET_ID,
				compositeUniformDefaults()
			)
		);

		return new PostChainConfig(targets, passes);
	}

	private static void processChain(PostChain chain, RenderTarget mainTarget, RenderTarget bloomSourceTarget) {
		FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
		BloomTargetBundle targetBundle = new BloomTargetBundle(
			frameGraphBuilder.importExternal("main", mainTarget),
			frameGraphBuilder.importExternal("bloom_source", bloomSourceTarget)
		);
		chain.addToFrame(frameGraphBuilder, mainTarget.width, mainTarget.height, targetBundle);
		frameGraphBuilder.execute(GraphicsResourceAllocator.UNPOOLED);
	}

	private static void applyConfigUniforms(PostChain chain, BloomConfig.Data config, int activeLevels, int baseLevels) {
		List<PostPass> passes = ((PostChainAccessor) chain).bloom$getPasses();
		if (passes.isEmpty()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		float farPlane = minecraft.gameRenderer.getDepthFar();
		float nearPlane = DEFAULT_NEAR_PLANE;
		float threshold = (float) config.threshold;
		float highlightClamp = (float) config.highlightClamp;
		float softKnee = (float) config.softKnee;
		float maxDistance = (float) config.bloomDistance;
		if (
			uniformsDirty
			|| changed(lastExtractUniforms[0], threshold)
			|| changed(lastExtractUniforms[1], highlightClamp)
			|| changed(lastExtractUniforms[2], softKnee)
			|| changed(lastExtractUniforms[3], maxDistance)
			|| changed(lastExtractUniforms[4], nearPlane)
			|| changed(lastExtractUniforms[5], farPlane)
			|| changed(lastExtractUniforms[6], SOURCE_STRENGTH_SCALE)
		) {
			writeExtractUniforms(passes.get(0), threshold, highlightClamp, softKnee, maxDistance, nearPlane, farPlane, SOURCE_STRENGTH_SCALE);
			lastExtractUniforms[0] = threshold;
			lastExtractUniforms[1] = highlightClamp;
			lastExtractUniforms[2] = softKnee;
			lastExtractUniforms[3] = maxDistance;
			lastExtractUniforms[4] = nearPlane;
			lastExtractUniforms[5] = farPlane;
			lastExtractUniforms[6] = SOURCE_STRENGTH_SCALE;
		}

		float[] levelWeights = computeLevelWeights(config.radius, activeLevels, baseLevels);
		float strength = (float) config.strength;
		PostPass compositePass = passes.get(passes.size() - 1);
		if (uniformsDirty || changed(lastCompositeStrength, strength)) {
			writeCompositeUniforms(compositePass, strength);
			lastCompositeStrength = strength;
		}
		if (uniformsDirty || weightsChanged(levelWeights, lastCompositeLevelWeights)) {
			writeCompositeWeights(compositePass, levelWeights);
			System.arraycopy(levelWeights, 0, lastCompositeLevelWeights, 0, MAX_ACTIVE_LEVELS);
		}
		if (
			uniformsDirty
			|| changed(lastCompositeMaxDistance, maxDistance)
			|| changed(lastCompositeNearPlane, nearPlane)
			|| changed(lastCompositeFarPlane, farPlane)
		) {
			writeCompositeDistanceUniforms(compositePass, maxDistance, nearPlane, farPlane);
			lastCompositeMaxDistance = maxDistance;
			lastCompositeNearPlane = nearPlane;
			lastCompositeFarPlane = farPlane;
		}

		uniformsDirty = false;
	}

	private static float[] computeLevelWeights(double radius, int activeLevels, int baseLevels) {
		float[] weights = new float[MAX_ACTIVE_LEVELS];
		float radiusNorm = clamp((float) (radius / BloomConfig.MAX_RADIUS), 0.0F, 1.0F);
		float radiusResponse = (float) Math.pow(radiusNorm, RADIUS_RESPONSE_EXPONENT);
		float targetIndex = radiusResponse * Math.max(activeLevels - 1, 0);
		for (int i = 0; i < activeLevels; i++) {
			float distance = i - targetIndex;
			weights[i] = (float) (BASE_LEVEL_FACTORS[i] * Math.exp(-(distance * distance) / WEIGHT_DISTRIBUTION_DENOMINATOR));
		}

		if (activeLevels > baseLevels) {
			float firstExtraNorm = clamp((float) ((radius - FIRST_EXTRA_LEVEL_RADIUS) / (SECOND_EXTRA_LEVEL_RADIUS - FIRST_EXTRA_LEVEL_RADIUS)), 0.0F, 1.0F);
			weights[baseLevels] *= firstExtraNorm;
		}

		if (activeLevels > baseLevels + 1) {
			float secondExtraNorm = clamp((float) ((radius - SECOND_EXTRA_LEVEL_RADIUS) / (BloomConfig.MAX_RADIUS - SECOND_EXTRA_LEVEL_RADIUS)), 0.0F, 1.0F);
			weights[baseLevels + 1] *= secondExtraNorm;
		}

		float total = 0.0F;
		for (float weight : weights) {
			total += weight;
		}
		if (total > UNIFORM_EPSILON) {
			for (int i = 0; i < activeLevels; i++) {
				weights[i] /= total;
			}
		}
		return weights;
	}

	private static void writeExtractUniforms(
		PostPass pass,
		float threshold,
		float highlightClamp,
		float softKnee,
		float maxDistance,
		float nearPlane,
		float farPlane,
		float sourceStrengthScale
	) {
		writeUniform(pass, "BloomExtractConfig", builder -> {
			builder.putFloat(threshold);
			builder.putFloat(highlightClamp);
			builder.putFloat(softKnee);
			builder.putFloat(maxDistance);
			builder.putFloat(nearPlane);
			builder.putFloat(farPlane);
			builder.putFloat(sourceStrengthScale);
			builder.putFloat(DISTANCE_FADE_RANGE);
		});
	}

	private static void writeCompositeUniforms(PostPass pass, float strength) {
		writeUniform(pass, "BloomCompositeConfig", builder -> builder.putFloat(strength));
	}

	private static void writeCompositeWeights(PostPass pass, float[] weights) {
		writeUniform(pass, "BloomCompositeWeights", builder -> {
			for (int i = 0; i < MAX_ACTIVE_LEVELS; i++) {
				builder.putFloat(weights[i]);
			}
			for (int i = MAX_ACTIVE_LEVELS; i % 4 != 0; i++) {
				builder.putFloat(0.0F);
			}
		});
	}

	private static void writeCompositeDistanceUniforms(PostPass pass, float maxDistance, float nearPlane, float farPlane) {
		writeUniform(pass, "BloomCompositeDistanceConfig", builder -> {
			builder.putFloat(maxDistance);
			builder.putFloat(nearPlane);
			builder.putFloat(farPlane);
			builder.putFloat(DISTANCE_FADE_RANGE);
		});
	}

	private static boolean changed(float previous, float current) {
		return Math.abs(previous - current) > UNIFORM_EPSILON;
	}

	private static boolean weightsChanged(float[] current, float[] previous) {
		for (int i = 0; i < MAX_ACTIVE_LEVELS; i++) {
			if (changed(previous[i], current[i])) {
				return true;
			}
		}
		return false;
	}

	private static void writeUniform(PostPass pass, String uniformName, UniformWriter writer) {
		Map<String, GpuBuffer> customUniforms = ((PostPassAccessor) pass).bloom$getCustomUniforms();
		GpuBuffer uniformBuffer = customUniforms.get(uniformName);
		if (uniformBuffer == null) {
			return;
		}

		GpuBuffer writableBuffer = ensureWritableUniformBuffer(customUniforms, uniformName, uniformBuffer);
		if (writableBuffer == null) {
			return;
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView mappedView = encoder.mapBuffer(writableBuffer, false, true)) {
			ByteBuffer data = mappedView.data();
			data.clear();
			Std140Builder builder = Std140Builder.intoBuffer(data);
			writer.write(builder);
		} catch (RuntimeException e) {
			if (!warnedUniformWriteFailure) {
				BloomMod.LOGGER.warn("Shine uniform update failed; continuing with shader defaults.", e);
				warnedUniformWriteFailure = true;
			}
		}
	}

	private static GpuBuffer ensureWritableUniformBuffer(Map<String, GpuBuffer> customUniforms, String uniformName, GpuBuffer uniformBuffer) {
		if ((uniformBuffer.usage() & GpuBuffer.USAGE_MAP_WRITE) != 0) {
			return uniformBuffer;
		}

		try {
			GpuBuffer writableBuffer = RenderSystem.getDevice()
				.createBuffer(() -> "Shine dynamic uniform " + uniformName, DYNAMIC_UNIFORM_USAGE, uniformBuffer.size());
			customUniforms.put(uniformName, writableBuffer);
			uniformBuffer.close();
			return writableBuffer;
		} catch (RuntimeException e) {
			if (!warnedUniformWriteFailure) {
				BloomMod.LOGGER.warn("Shine could not allocate writable uniform buffer '{}'.", uniformName, e);
				warnedUniformWriteFailure = true;
			}
			return null;
		}
	}

	private static void closeRuntimeChain() {
		if (runtimeChain != null) {
			runtimeChain.close();
			runtimeChain = null;
		}
		runtimeChainWidth = -1;
		runtimeChainHeight = -1;
		runtimeChainLevels = -1;
	}

	private static int getBaseLevels(int blurPassCount) {
		int clamped = Math.max(1, Math.min(BloomConfig.MAX_BLUR_PASSES, blurPassCount));
		return Math.min(MAX_ACTIVE_LEVELS - 1, clamped + 1);
	}

	private static int getActiveLevels(int blurPassCount, double radius) {
		int baseLevels = getBaseLevels(blurPassCount);
		int activeLevels = baseLevels;
		if (radius > FIRST_EXTRA_LEVEL_RADIUS) {
			activeLevels++;
		}
		if (radius > SECOND_EXTRA_LEVEL_RADIUS) {
			activeLevels++;
		}
		return Math.min(MAX_ACTIVE_LEVELS, activeLevels);
	}

	private static int getScaledDimension(int mainSize, float scale) {
		return Math.max(1, Math.round(mainSize * scale));
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private static Identifier runtimeTarget(String path) {
		return Identifier.fromNamespaceAndPath(BloomMod.MOD_ID, path);
	}

	private static PostChainConfig.InternalTarget internalTarget(int width, int height) {
		return new PostChainConfig.InternalTarget(Optional.of(width), Optional.of(height), true, 0);
	}

	private static Identifier compositeLevelTarget(int levelIndex, int activeLevels) {
		int resolvedIndex = Math.min(levelIndex, activeLevels - 1);
		return switch (resolvedIndex) {
			case 0 -> HALF_A_TARGET_ID;
			case 1 -> QUARTER_A_TARGET_ID;
			case 2 -> EIGHTH_A_TARGET_ID;
			case 3 -> SIXTEENTH_A_TARGET_ID;
			case 4 -> THIRTYSECOND_A_TARGET_ID;
			case 5 -> SIXTYFOURTH_A_TARGET_ID;
			default -> throw new IllegalArgumentException("Unsupported bloom level index: " + resolvedIndex);
		};
	}

	private static PostChainConfig.Pass postPass(
		Identifier fragmentShaderId,
		List<PostChainConfig.Input> inputs,
		Identifier outputTarget,
		Map<String, List<UniformValue>> uniforms
	) {
		return new PostChainConfig.Pass(SCREEN_QUAD_SHADER_ID, fragmentShaderId, inputs, outputTarget, uniforms);
	}

	private static PostChainConfig.TargetInput targetInput(String samplerName, Identifier targetId, boolean depthBuffer, boolean bilinear) {
		return new PostChainConfig.TargetInput(samplerName, targetId, depthBuffer, bilinear);
	}

	private static Map<String, List<UniformValue>> extractUniformDefaults() {
		return Map.of(
			"BloomExtractConfig",
			List.of(
				new UniformValue.FloatUniform(0.15F),
				new UniformValue.FloatUniform(0.3F),
				new UniformValue.FloatUniform(0.2F),
				new UniformValue.FloatUniform(75.0F),
				new UniformValue.FloatUniform(DEFAULT_NEAR_PLANE),
				new UniformValue.FloatUniform(1024.0F),
				new UniformValue.FloatUniform(SOURCE_STRENGTH_SCALE),
				new UniformValue.FloatUniform(DISTANCE_FADE_RANGE)
			)
		);
	}

	private static Map<String, List<UniformValue>> compositeUniformDefaults() {
		return Map.of(
			"BloomCompositeConfig",
			List.of(new UniformValue.FloatUniform(3.0F)),
			"BloomCompositeWeights",
			List.of(
				new UniformValue.FloatUniform(1.0F),
				new UniformValue.FloatUniform(0.0F),
				new UniformValue.FloatUniform(0.0F),
				new UniformValue.FloatUniform(0.0F),
				new UniformValue.FloatUniform(0.0F),
				new UniformValue.FloatUniform(0.0F),
				new UniformValue.FloatUniform(0.0F),
				new UniformValue.FloatUniform(0.0F)
			),
			"BloomCompositeDistanceConfig",
			List.of(
				new UniformValue.FloatUniform(75.0F),
				new UniformValue.FloatUniform(DEFAULT_NEAR_PLANE),
				new UniformValue.FloatUniform(1024.0F),
				new UniformValue.FloatUniform(DISTANCE_FADE_RANGE)
			)
		);
	}

	@FunctionalInterface
	private interface UniformWriter {
		void write(Std140Builder builder);
	}

	private static final class BloomTargetBundle implements PostChain.TargetBundle {
		private ResourceHandle<RenderTarget> main;
		private ResourceHandle<RenderTarget> source;

		private BloomTargetBundle(ResourceHandle<RenderTarget> main, ResourceHandle<RenderTarget> source) {
			this.main = main;
			this.source = source;
		}

		@Override
		public void replace(Identifier identifier, ResourceHandle<RenderTarget> resourceHandle) {
			if (identifier.equals(LevelTargetBundle.MAIN_TARGET_ID)) {
				this.main = resourceHandle;
			} else if (identifier.equals(BloomSourceRenderer.SOURCE_TARGET_ID)) {
				this.source = resourceHandle;
			} else {
				throw new IllegalArgumentException("No target with id " + identifier);
			}
		}

		@Override
		public ResourceHandle<RenderTarget> get(Identifier identifier) {
			if (identifier.equals(LevelTargetBundle.MAIN_TARGET_ID)) {
				return this.main;
			}

			return identifier.equals(BloomSourceRenderer.SOURCE_TARGET_ID) ? this.source : null;
		}
	}
}
