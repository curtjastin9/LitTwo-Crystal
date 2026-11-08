package com.littwocrystal.mixin;

import com.littwocrystal.vanilla.KeptCrystal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EndCrystal.class)
public class EndCrystalMixin implements KeptCrystal {

	@Unique
	private boolean littwocrystal$kept;
	@Unique
	private long littwocrystal$keptAt;
	@Unique
	private int littwocrystal$sequence;

	@Override
	public void littwocrystal$keep(long tick, int sequence) {
		this.littwocrystal$kept = true;
		this.littwocrystal$keptAt = tick;
		this.littwocrystal$sequence = sequence;
	}

	@Override
	public void littwocrystal$release() {
		this.littwocrystal$kept = false;
	}

	@Override
	public boolean littwocrystal$isKept() {
		return this.littwocrystal$kept;
	}

	@Override
	public long littwocrystal$keptAt() {
		return this.littwocrystal$keptAt;
	}

	@Override
	public int littwocrystal$sequence() {
		return this.littwocrystal$sequence;
	}
}
