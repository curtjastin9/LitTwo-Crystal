package com.littwocrystal.mixin;

import com.littwocrystal.vanilla.KeptCrystals;
import com.littwocrystal.vanilla.VanillaMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.EndCrystalItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(EndCrystalItem.class)
public class EndCrystalItemMixin {

	/** A crystal we just hit does not count as blocking the spot, so the next one can be placed straight away. */
	@ModifyVariable(method = "useOn", at = @At("STORE"), ordinal = 0)
	private List<Entity> littwocrystal$skipHiddenCrystals(List<Entity> obstructing) {
		return VanillaMode.enabled() ? KeptCrystals.withoutHidden(obstructing) : obstructing;
	}
}
