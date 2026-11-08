package com.littwocrystal.vanilla;

import com.littwocrystal.LitTwoCrystalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Vanilla Mode: Marlow-style crystal handling. Vanilla click timing is untouched (no delay changes,
 * no extra clicks, no extra packets). After you hit a crystal, the client acts as if the server has
 * already removed it, so the very next click goes through to what is behind it: the obsidian under
 * it (places a new crystal) or the next crystal in line (hits that one).
 */
public final class VanillaMode {

	/** Crosshair result vanilla had before we pointed it past a kept crystal; restored afterwards. */
	private static HitResult savedCrosshair;
	private static boolean swapped;

	private VanillaMode() {
	}

	public static boolean enabled() {
		return LitTwoCrystalConfig.enabled && LitTwoCrystalConfig.isVanilla();
	}

	/** Right after a normal attack was sent at an end crystal. */
	public static void afterAttack(Minecraft mc, Entity target) {
		if (!enabled() || !(target instanceof EndCrystal crystal)) {
			return;
		}
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		if (!mc.level.getWorldBorder().isWithinBounds(crystal.blockPosition()) || !AttackDamage.breaksCrystal(player)) {
			return;
		}
		KeptCrystals.keep(crystal, ((SequencedLevel) mc.level).littwocrystal$blockSequence());
	}

	/** Start of a left click: if you aim at a kept crystal, aim at the next crystal behind it instead. */
	public static void attackStarted(Minecraft mc) {
		if (!enabled() || mc.player == null || mc.level == null) {
			return;
		}
		if (!(mc.hitResult instanceof EntityHitResult hit) || !KeptCrystals.isHidden(hit.getEntity())) {
			return;
		}
		EntityHitResult behind = entityPastKept(mc, mc.player);
		if (behind != null && behind.getEntity() instanceof EndCrystal) {
			swap(mc, behind);
		}
	}

	/** Start of a right click with a crystal in hand: if you aim at a kept crystal, aim at the block under it. */
	public static void useStarted(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (!enabled() || player == null || mc.level == null || !holdingCrystal(player)) {
			return;
		}
		if (!(mc.hitResult instanceof EntityHitResult hit) || !KeptCrystals.isHidden(hit.getEntity())) {
			return;
		}
		if (entityPastKept(mc, player) != null) {
			return;
		}
		HitResult block = player.pick(player.blockInteractionRange(), 1.0F, false);
		if (!(block instanceof BlockHitResult blockHit) || block.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos pos = blockHit.getBlockPos();
		BlockState state = mc.level.getBlockState(pos);
		if ((state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK)) && mc.level.isEmptyBlock(pos.above())) {
			swap(mc, blockHit);
		}
	}

	/** End of a click: give vanilla its own crosshair result back. */
	public static void clickEnded(Minecraft mc) {
		if (swapped) {
			mc.hitResult = savedCrosshair;
		}
		swapped = false;
		savedCrosshair = null;
	}

	private static void swap(Minecraft mc, HitResult replacement) {
		savedCrosshair = mc.hitResult;
		swapped = true;
		mc.hitResult = replacement;
	}

	/** Like Marlow: a crystal in either hand counts (main-hand sword + offhand crystal is the usual setup). */
	private static boolean holdingCrystal(LocalPlayer player) {
		return player.getMainHandItem().is(Items.END_CRYSTAL) || player.getOffhandItem().is(Items.END_CRYSTAL);
	}

	/**
	 * What vanilla would be aiming at if kept crystals did not exist: the nearest pickable entity
	 * along the look ray (within reach, not behind a block), or null if there is none.
	 */
	private static EntityHitResult entityPastKept(Minecraft mc, LocalPlayer player) {
		double reach = player.entityInteractionRange();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(reach));
		AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);

		Entity best = null;
		Vec3 bestPoint = null;
		double bestDist = reach * reach;
		for (Entity entity : mc.level.getEntities(player, box,
			e -> !e.isSpectator() && e.isPickable() && !KeptCrystals.isHidden(e))) {
			AABB bb = entity.getBoundingBox().inflate(entity.getPickRadius());
			Optional<Vec3> point = bb.clip(eye, end);
			Vec3 hitPoint;
			if (bb.contains(eye)) {
				hitPoint = eye;
			} else if (point.isPresent()) {
				hitPoint = point.get();
			} else {
				continue;
			}
			double d = eye.distanceToSqr(hitPoint);
			if (d < bestDist) {
				bestDist = d;
				best = entity;
				bestPoint = hitPoint;
			}
		}
		if (best == null) {
			return null;
		}
		HitResult wall = mc.level.clip(new ClipContext(eye, end,
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() == HitResult.Type.BLOCK && eye.distanceToSqr(wall.getLocation()) < bestDist) {
			return null;
		}
		return new EntityHitResult(best, bestPoint);
	}
}
