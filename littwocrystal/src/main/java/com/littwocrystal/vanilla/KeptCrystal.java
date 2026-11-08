package com.littwocrystal.vanilla;

/** Implemented onto EndCrystal by EndCrystalMixin: remembers that this crystal was hit by us. */
public interface KeptCrystal {

	void littwocrystal$keep(long tick, int sequence);

	void littwocrystal$release();

	boolean littwocrystal$isKept();

	long littwocrystal$keptAt();

	int littwocrystal$sequence();
}
