package com.bloom.client;

import com.bloom.BloomMod;
import com.bloom.client.config.BloomConfig;
import com.bloom.client.render.BloomPostProcessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class BloomClient implements ClientModInitializer {
	private static final KeyMapping TOGGLE_BLOOM_KEY = KeyBindingHelper.registerKeyBinding(
		new KeyMapping(
			"key.shine.toggle",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_B,
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(BloomMod.MOD_ID, "main"))
		)
	);

	@Override
	public void onInitializeClient() {
		BloomConfig.load();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE_BLOOM_KEY.consumeClick()) {
				boolean enabled = BloomPostProcessor.toggleFromKeybind();
				BloomMod.LOGGER.info("Shine post-processing {}", enabled ? "enabled" : "disabled");
			}
		});

		WorldRenderEvents.START_MAIN.register(BloomPostProcessor::prepareSourceIfEnabled);
		WorldRenderEvents.BEFORE_ENTITIES.register(BloomPostProcessor::renderIfEnabled);
	}
}
