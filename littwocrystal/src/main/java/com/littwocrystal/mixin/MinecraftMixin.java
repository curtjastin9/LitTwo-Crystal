package com.littwocrystal.mixin;

import com.littwocrystal.LitTwoCrystalClient;
import com.littwocrystal.LitTwoCrystalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
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

	/** Tells the client class that vanilla already handled a left click this tick. */
	@Inject(method = "startAttack", at = @At("RETURN"))
	private void littwocrystal$attackHandled(CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) {
			LitTwoCrystalClient.attackedThisTick = true;
		}
	}

	/**
	 * Right click that cannot place anything is not sent at all: aiming at a crystal (vanilla would
	 * just send useless interact packets) or at a block that already has a crystal on it.
	 */
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void littwocrystal$skipUseless(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		LocalPlayer player = mc.player;
		if (player == null || !LitTwoCrystalClient.active(player) || mc.hitResult == null) {
			return;
		}
		if (mc.hitResult instanceof EntityHitResult e && e.getEntity() instanceof EndCrystal) {
			ci.cancel();
		} else if (mc.hitResult instanceof BlockHitResult b && mc.hitResult.getType() == HitResult.Type.BLOCK
			&& mc.level != null
			&& (mc.level.getBlockState(b.getBlockPos()).is(net.minecraft.world.level.block.Blocks.OBSIDIAN)
				|| mc.level.getBlockState(b.getBlockPos()).is(net.minecraft.world.level.block.Blocks.BEDROCK))
			&& !LitTwoCrystalClient.canPlaceAt(mc, b.getBlockPos())) {
			ci.cancel();
		}
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
	 * Fast placing: after each right click, wait only the configured number of ticks
	 * (0 = place again on the very next tick while right click is held). Spamming right click
	 * is also never throttled, because vanilla handles every queued click each tick.
	 * Only while an end crystal is the item in use, so everything else stays vanilla.
	 */
	@Inject(method = "startUseItem", at = @At("RETURN"))
	private void littwocrystal$setDelay(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		LocalPlayer player = mc.player;
		if (player != null && LitTwoCrystalClient.active(player)) {
			this.rightClickDelay = LitTwoCrystalConfig.delay;
			LitTwoCrystalClient.placedThisTick = true;
		}
	}
}
