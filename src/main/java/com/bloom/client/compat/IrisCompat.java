package com.bloom.client.compat;

import com.bloom.BloomMod;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

public final class IrisCompat {
	private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");
	private static final String ACTIVE_MESSAGE = "Shine bloom disabled: Iris shader pack is active.";
	private static final String INSTALLED_MESSAGE = "Shine bloom disabled: Iris is installed but shader-pack state could not be verified safely.";

	private static boolean reflectionInitialized;
	private static boolean reflectionAvailable;
	private static boolean loggedReflectionFailure;
	private static Method irisGetInstanceMethod;
	private static Method irisIsShaderPackInUseMethod;
	private static String disableMessage;

	private IrisCompat() {
	}

	public static boolean shouldDisableBloom() {
		disableMessage = null;
		if (!IRIS_LOADED) {
			return false;
		}

		if (!initReflection()) {
			disableMessage = INSTALLED_MESSAGE;
			return true;
		}

		try {
			Object irisApi = irisGetInstanceMethod.invoke(null);
			Object result = irisIsShaderPackInUseMethod.invoke(irisApi);
			boolean shaderPackActive = result instanceof Boolean bool && bool;
			disableMessage = shaderPackActive ? ACTIVE_MESSAGE : null;
			return shaderPackActive;
		} catch (ReflectiveOperationException | RuntimeException e) {
			if (!loggedReflectionFailure) {
				BloomMod.LOGGER.warn("Shine Iris compatibility check failed; keeping bloom disabled while Iris is installed.", e);
				loggedReflectionFailure = true;
			}
			disableMessage = INSTALLED_MESSAGE;
			return true;
		}
	}

	public static String disableMessage() {
		return disableMessage;
	}

	private static boolean initReflection() {
		if (reflectionInitialized) {
			return reflectionAvailable;
		}

		reflectionInitialized = true;
		try {
			Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			irisGetInstanceMethod = irisApiClass.getMethod("getInstance");
			irisIsShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
			reflectionAvailable = true;
			return true;
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			if (!loggedReflectionFailure) {
				BloomMod.LOGGER.warn("Shine could not access Iris API state; keeping bloom disabled while Iris is installed.");
				loggedReflectionFailure = true;
			}
			reflectionAvailable = false;
			return false;
		}
	}
}
