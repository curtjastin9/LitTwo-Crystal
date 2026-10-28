package com.littwocrystal.mixin;

import com.littwocrystal.LitTwoCrystalClient;
import com.littwocrystal.LitTwoCrystalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

	/** Vanilla's wait (in ticks) between right-click uses while you hold the button. Normally 4. */
	@Shadow
	private int rightClickDelay;

	/**
	 * Fast placing: after each right click, wait only the configured number of ticks.
	 * Only while an end crystal is the item in use, so everything else stays vanilla.
	 */
	@Inject(method = "startUseItem", at = @At("RETURN"))
	private void littwocrystal$setDelay(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		LocalPlayer player = mc.player;
		if (player != null && LitTwoCrystalClient.active(player)) {
			this.rightClickDelay = LitTwoCrystalConfig.delay;
		}
	}
}
