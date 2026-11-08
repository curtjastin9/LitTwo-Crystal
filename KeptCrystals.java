package com.littwocrystal.vanilla;

import com.google.common.collect.Iterables;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla Mode bookkeeping. A crystal we just hit is "kept": the server will remove it, so our
 * client treats it as already gone (not rendered, does not block placing, is not aimed at) for up
 * to {@link #HIDDEN_TICKS} ticks, or until the server acknowledges the block-update sequence that
 * the next placement started. If the server did not actually break it, it simply shows again.
 * No timing is changed and no extra packets are sent; this only decides what the client believes.
 */
public final class KeptCrystals {

	/** Longest a hit crystal stays hidden if the server never confirms (1.5 s at 20 TPS). */
	public static final long HIDDEN_TICKS = 30L;

	private static final List<EndCrystal> KEPT = new ArrayList<>();
	private static long lastKeptAt;
	private static volatile long ticks;

	private KeptCrystals() {
	}

	private static boolean isHidden(Entity entity, long cutoff) {
		return entity instanceof KeptCrystal k && k.littwocrystal$isKept() && k.littwocrystal$keptAt() - cutoff > 0;
	}

	public static boolean isHidden(Entity entity) {
		return isHidden(entity, ticks - HIDDEN_TICKS);
	}

	public static void ticked() {
		ticks++;
	}

	/** Hide this crystal until the server confirms (or {@link #HIDDEN_TICKS} pass). */
	public static void keep(EndCrystal crystal, int sequence) {
		long now = ticks;
		long cutoff = now - HIDDEN_TICKS;
		forgetSettled(cutoff);
		if (isHidden(crystal, cutoff)) {
			return;
		}
		((KeptCrystal) crystal).littwocrystal$keep(now, sequence);
		KEPT.add(crystal);
		lastKeptAt = now;
	}

	/** The server acknowledged block updates up to this sequence: crystals kept before it are released. */
	public static void acknowledged(int sequence) {
		forgetSettled(ticks - HIDDEN_TICKS);
		KEPT.removeIf(crystal -> {
			KeptCrystal k = (KeptCrystal) crystal;
			if (k.littwocrystal$sequence() >= sequence) {
				return false;
			}
			k.littwocrystal$release();
			return true;
		});
	}

	/** Release everything (Vanilla Mode switched off). */
	public static void clear() {
		if (KEPT.isEmpty()) {
			return;
		}
		for (EndCrystal crystal : KEPT) {
			((KeptCrystal) crystal).littwocrystal$release();
		}
		KEPT.clear();
	}

	public static Iterable<Entity> hide(Iterable<Entity> entities) {
		long cutoff = ticks - HIDDEN_TICKS;
		if (nothingKept(cutoff)) {
			return entities;
		}
		return Iterables.filter(entities, entity -> !isHidden(entity, cutoff));
	}

	public static List<Entity> withoutHidden(List<Entity> entities) {
		long cutoff = ticks - HIDDEN_TICKS;
		for (Entity entity : entities) {
			if (isHidden(entity, cutoff)) {
				List<Entity> copy = new ArrayList<>(entities);
				copy.removeIf(e -> isHidden(e, cutoff));
				return copy;
			}
		}
		return entities;
	}

	private static void forgetSettled(long cutoff) {
		KEPT.removeIf(crystal -> crystal.isRemoved() || !isHidden(crystal, cutoff));
	}

	private static boolean nothingKept(long cutoff) {
		if (KEPT.isEmpty()) {
			return true;
		}
		if (lastKeptAt - cutoff <= 0) {
			KEPT.clear();
			return true;
		}
		return false;
	}
}
