package com.littwocrystal.vanilla;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Whether a normal hit from this player will actually break an end crystal (damage above zero). */
final class AttackDamage {

	/** Strength with this few ticks left is not counted: it may run out before the server sees the hit. */
	private static final int STRENGTH_ENDING_TICKS = 30;

	private AttackDamage() {
	}

	static boolean breaksCrystal(LocalPlayer player) {
		// Attack damage attribute already includes the held weapon and the strength / weakness effects.
		double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		MobEffectInstance strength = player.getEffect(MobEffects.STRENGTH);
		if (strength != null && strength.getDuration() >= 0 && strength.getDuration() <= STRENGTH_ENDING_TICKS) {
			damage -= 3.0 * (strength.getAmplifier() + 1);
		}
		return damage > 0.0;
	}
}
