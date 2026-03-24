package com.bloom.mixin.client.accessor;

import com.mojang.blaze3d.buffers.GpuBuffer;
import java.util.Map;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PostPass.class)
public interface PostPassAccessor {
	@Accessor("customUniforms")
	Map<String, GpuBuffer> bloom$getCustomUniforms();
}
