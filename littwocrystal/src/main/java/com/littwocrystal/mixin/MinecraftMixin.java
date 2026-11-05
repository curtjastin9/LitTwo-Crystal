package com.littwocrystal.mixin;

import com.littwocrystal.LitTwoCrystalClient;
import com.littwocrystal.LitTwoCrystalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

	/** Vanilla's wait (in ticks) between right-click uses while you hold the button. Normally 4. */
	@Shadow
	private int rightClickDelay;

	/**
	 * True while the optimizer is active (an end crystal in hand) and you are looking at a block.
	 * Vanilla would start mining that block when left click is held, and while it is mining it
	 * refuses to place anything with right click. So with a crystal in hand, block mining is skipped.
	 */
	private boolean littwocrystal$aimingAtBlock() {
		Minecraft mc = (Minecraft) (Object) this;
		LocalPlayer player = mc.player;
		HitResult hit = mc.hitResult;
		return player != null
			&& LitTwoCrystalClient.active(player)
			&& hit != null
			&& hit.getType() == HitResult.Type.BLOCK;
	}

	/** A single left click on a block: do not start mining it. Clicks on crystals are untouched. */
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void littwocrystal$noMineOnClick(CallbackInfoReturnable<Boolean> cir) {
		if (littwocrystal$aimingAtBlock()) {
			cir.setReturnValue(false);
		}
	}

	/** Holding left click on a block: do not keep mining it, so right click can keep placing. */
	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void littwocrystal$noMineOnHold(boolean leftClick, CallbackInfo ci) {
		if (littwocrystal$aimingAtBlock()) {
			Minecraft mc = (Minecraft) (Object) this;
			if (mc.gameMode != null) {
				// If mining had already started before you swapped to a crystal, end it once.
				mc.gameMode.stopDestroyBlock();
			}
			ci.cancel();
		}
	}

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
