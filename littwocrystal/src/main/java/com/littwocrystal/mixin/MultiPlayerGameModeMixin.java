package com.littwocrystal.mixin;

import com.littwocrystal.vanilla.VanillaMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

	/** After the normal attack packet went out: if it was a crystal, remember it as kept. */
	@Inject(method = "attack", at = @At("RETURN"))
	private void littwocrystal$afterAttack(Player player, Entity entity, CallbackInfo ci) {
		VanillaMode.afterAttack(Minecraft.getInstance(), entity);
	}
}
