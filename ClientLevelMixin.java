package com.littwocrystal.mixin;

import com.littwocrystal.vanilla.KeptCrystals;
import com.littwocrystal.vanilla.SequencedLevel;
import com.littwocrystal.vanilla.VanillaMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin implements SequencedLevel {

	@Shadow
	@Final
	private BlockStatePredictionHandler blockStatePredictionHandler;

	@Override
	public int littwocrystal$blockSequence() {
		return this.blockStatePredictionHandler.currentSequence();
	}

	/** A crystal we just hit is not drawn while we wait for the server to remove it. */
	@Inject(method = "entitiesForRendering", at = @At("RETURN"), cancellable = true, require = 0)
	private void littwocrystal$hideKeptCrystals(CallbackInfoReturnable<Iterable<Entity>> cir) {
		if (VanillaMode.enabled()) {
			cir.setReturnValue(KeptCrystals.hide(cir.getReturnValue()));
		}
	}

	/** The server confirmed our latest block interaction, so it also handled the crystal hit before it. */
	@Inject(method = "handleBlockChangedAck", at = @At("HEAD"), require = 0)
	private void littwocrystal$releaseKeptCrystals(int sequence, CallbackInfo ci) {
		KeptCrystals.acknowledged(sequence);
	}
}
