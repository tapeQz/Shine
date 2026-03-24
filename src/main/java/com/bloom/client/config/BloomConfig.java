package com.bloom.client.config;

import com.bloom.BloomMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

public final class BloomConfig {
	public static final int MAX_BLUR_PASSES = 3;
	public static final double MAX_STRENGTH = 10.0;
	public static final double MAX_RADIUS = 500.0;
	public static final double MIN_BLOOM_DISTANCE = 1.0;
	public static final double MAX_BLOOM_DISTANCE = 256.0;
	public static final double MIN_HIGHLIGHT_CLAMP = 0.01;
	public static final double MAX_HIGHLIGHT_CLAMP = 4.0;
	public static final double MIN_SOFT_KNEE = 0.01;
	public static final double MAX_SOFT_KNEE = 1.0;
	public static final double MIN_SOURCE_STRENGTH = 0.0;
	public static final double MAX_SOURCE_STRENGTH = 500.0;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("shine.json");
	private static final Path LEGACY_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("bloom.json");

	private static Data data = Data.defaults();

	private BloomConfig() {
	}

	public static Data get() {
		return data;
	}

	public static Data copy() {
		return data.copy();
	}

	public static Data defaults() {
		return Data.defaults();
	}

	public static void set(Data newData) {
		data = sanitize(newData);
	}

	public static void load() {
		Path pathToLoad = Files.exists(CONFIG_PATH) ? CONFIG_PATH : (Files.exists(LEGACY_CONFIG_PATH) ? LEGACY_CONFIG_PATH : null);
		if (pathToLoad == null) {
			data = Data.defaults();
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(pathToLoad)) {
			Data loaded = GSON.fromJson(reader, Data.class);
			data = sanitize(loaded);
			if (!pathToLoad.equals(CONFIG_PATH)) {
				save();
				BloomMod.LOGGER.info("Migrated legacy bloom.json config to shine.json.");
			}
		} catch (Exception e) {
			BloomMod.LOGGER.error("Failed to read Shine config, using defaults.", e);
			data = Data.defaults();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			BloomMod.LOGGER.error("Failed to write Shine config.", e);
		}
	}

	private static Data sanitize(Data input) {
		Data safe = input == null ? Data.defaults() : input.copy();
		safe.threshold = clamp(safe.threshold, 0.0, 1.0);
		safe.strength = clamp(safe.strength, 0.0, MAX_STRENGTH);
		safe.radius = clamp(safe.radius, 0.0, MAX_RADIUS);
		safe.blurPassCount = (int) clamp(safe.blurPassCount, 1, MAX_BLUR_PASSES);
		if (safe.bloomDistance <= 0.0) {
			// Older config files won't contain this field yet; keep them on a sensible default.
			safe.bloomDistance = Data.defaults().bloomDistance;
		}
		safe.bloomDistance = clamp(safe.bloomDistance, MIN_BLOOM_DISTANCE, MAX_BLOOM_DISTANCE);
		if (safe.highlightClamp <= 0.0) {
			// Older config files won't contain this field yet; keep them on a sensible default.
			safe.highlightClamp = Data.defaults().highlightClamp;
		}
		safe.highlightClamp = clamp(safe.highlightClamp, MIN_HIGHLIGHT_CLAMP, MAX_HIGHLIGHT_CLAMP);
		if (safe.softKnee <= 0.0) {
			// Older config files won't contain this field yet; keep them on a sensible default.
			safe.softKnee = Data.defaults().softKnee;
		}
		safe.softKnee = clamp(safe.softKnee, MIN_SOFT_KNEE, MAX_SOFT_KNEE);
		safe.defaultLightSourceStrength = clamp(safe.defaultLightSourceStrength, MIN_SOURCE_STRENGTH, MAX_SOURCE_STRENGTH);
		safe.defaultNonLightStrength = clamp(safe.defaultNonLightStrength, MIN_SOURCE_STRENGTH, MAX_SOURCE_STRENGTH);
		Map<String, Double> sanitizedOverrides = new LinkedHashMap<>();
		if (safe.blockStrengthOverrides != null) {
			for (Map.Entry<String, Double> entry : safe.blockStrengthOverrides.entrySet()) {
				if (entry == null || entry.getKey() == null || entry.getValue() == null) {
					continue;
				}

				try {
					Identifier.parse(entry.getKey());
				} catch (Exception ignored) {
					continue;
				}

				sanitizedOverrides.put(entry.getKey(), clamp(entry.getValue(), MIN_SOURCE_STRENGTH, MAX_SOURCE_STRENGTH));
			}
		}
		for (Map.Entry<String, Double> baseline : Data.defaultBlockStrengthOverrides().entrySet()) {
			sanitizedOverrides.putIfAbsent(baseline.getKey(), clamp(baseline.getValue(), MIN_SOURCE_STRENGTH, MAX_SOURCE_STRENGTH));
		}
		safe.blockStrengthOverrides = sanitizedOverrides;
		return safe;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static long clamp(long value, long min, long max) {
		return Math.max(min, Math.min(max, value));
	}

	public static final class Data {
		public boolean enabled = true;
		public double strength = 3.0;
		public double threshold = 0.15;
		public double radius = 400.0;
		public int blurPassCount = 2;
		public double bloomDistance = 75.0;
		public double highlightClamp = 0.3;
		public double softKnee = 0.2;
		public double defaultLightSourceStrength = 50.0;
		public double defaultNonLightStrength = 0.0;
		public Map<String, Double> blockStrengthOverrides = defaultBlockStrengthOverrides();

		public static Data defaults() {
			return new Data();
		}

		public static LinkedHashMap<String, Double> defaultBlockStrengthOverrides() {
			LinkedHashMap<String, Double> defaults = new LinkedHashMap<>();
			defaults.put("minecraft:water", 0.0);
			defaults.put("minecraft:sculk", 500.0);
			defaults.put("minecraft:sculk_vein", 150.0);
			defaults.put("minecraft:amethyst_cluster", 100.0);
			defaults.put("minecraft:large_amethyst_bud", 100.0);
			defaults.put("minecraft:medium_amethyst_bud", 100.0);
			defaults.put("minecraft:small_amethyst_bud", 100.0);
			defaults.put("minecraft:glow_lichen", 125.0);
			defaults.put("minecraft:warped_stem", 50.0);
			defaults.put("minecraft:warped_fungus", 75.0);
			defaults.put("minecraft:nether_portal", 75.0);
			defaults.put("minecraft:crimson_stem", 100.0);
			defaults.put("minecraft:twisting_vines", 25.0);
			defaults.put("minecraft:twisting_vines_plant", 25.0);
			defaults.put("minecraft:weeping_vines", 75.0);
			defaults.put("minecraft:weeping_vines_plant", 75.0);
			defaults.put("minecraft:lava", 40.0);
			defaults.put("minecraft:crimson_fungus", 75.0);
			defaults.put("minecraft:nether_wart", 50.0);
			defaults.put("minecraft:crying_obsidian", 75.0);
			defaults.put("minecraft:closed_eyeblossom", 50.0);
			defaults.put("minecraft:open_eyeblossom", 50.0);
			defaults.put("minecraft:resin_clump", 150.0);
			defaults.put("minecraft:chorus_flower", 150.0);
			defaults.put("minecraft:powder_snow", 25.0);
			defaults.put("minecraft:snow", 25.0);
			defaults.put("minecraft:snow_block", 25.0);
			return defaults;
		}

		public Data copy() {
			Data copy = new Data();
			copy.enabled = this.enabled;
			copy.strength = this.strength;
			copy.threshold = this.threshold;
			copy.radius = this.radius;
			copy.blurPassCount = this.blurPassCount;
			copy.bloomDistance = this.bloomDistance;
			copy.highlightClamp = this.highlightClamp;
			copy.softKnee = this.softKnee;
			copy.defaultLightSourceStrength = this.defaultLightSourceStrength;
			copy.defaultNonLightStrength = this.defaultNonLightStrength;
			copy.blockStrengthOverrides = new LinkedHashMap<>(this.blockStrengthOverrides);
			return copy;
		}
	}
}
