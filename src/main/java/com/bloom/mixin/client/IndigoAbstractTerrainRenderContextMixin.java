package com.bloom.mixin.client;

import com.bloom.client.selection.BloomSelection;
import com.bloom.client.selection.BloomSourceEncoding;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractTerrainRenderContext", remap = false)
public abstract class IndigoAbstractTerrainRenderContextMixin {
	@Shadow(remap = false)
	protected BlockRenderInfo blockInfo;

	@Inject(method = "shadeQuad", at = @At("TAIL"), remap = false)
	private void bloom$encodeSourceStrength(MutableQuadViewImpl quad, boolean ao, boolean emissive, boolean vanillaShade, CallbackInfo ci) {
		if (this.blockInfo == null) {
			return;
		}

		BlockState blockState = this.blockInfo.blockState;
		if (blockState == null) {
			return;
		}

		double sourceStrength = BloomSelection.getBlockSourceStrength(blockState);
		for (int i = 0; i < 4; i++) {
			quad.lightmap(i, BloomSourceEncoding.encodePackedLight(quad.lightmap(i), sourceStrength));
		}
	}
}
