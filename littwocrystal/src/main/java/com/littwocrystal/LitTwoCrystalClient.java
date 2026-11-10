package com.littwocrystal;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import com.littwocrystal.vanilla.KeptCrystals;
import com.littwocrystal.vanilla.VanillaMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class LitTwoCrystalClient implements ClientModInitializer {

	private static KeyMapping toggleKey;
	private static KeyMapping slowerKey;
	private static KeyMapping fasterKey;

	private static boolean welcomed = false;

	/** Set by the mixin when vanilla already sent a place this tick, so we never double up. */
	public static boolean placedThisTick = false;

	/** Set by the mixin when vanilla already handled a left click this tick. */
	public static boolean attackedThisTick = false;
	private static boolean attackWasDown = false;

	private static int tickCounter = 0;

	/** How many ticks a hit crystal is treated as already gone while waiting for the server to remove it. */
	private static final int BROKEN_TTL_TICKS = 6;

	/** Crystals we have already sent an attack for: entity id -> tick when the memory expires. */
	private static final Map<Integer, Integer> brokenCrystals = new HashMap<>();

	/**
	 * Remember that an attack was sent to this crystal. The server handles packets in order, so the
	 * crystal is gone by the time a place packet sent right after the hit arrives. Treating it as
	 * gone on our side means the next place does not wait a full round trip for the server's
	 * "crystal removed" message, which is what made the break -> place loop feel capped by ping.
	 */
	public static void markBroken(EndCrystal crystal) {
		brokenCrystals.put(crystal.getId(), tickCounter + BROKEN_TTL_TICKS);
	}

	private static boolean isBroken(EndCrystal crystal) {
		Integer until = brokenCrystals.get(crystal.getId());
		return until != null && until >= tickCounter;
	}

	/**
	 * Whether a crystal place on this obsidian/bedrock block can succeed. The mod only places when
	 * your client sees the spot free (air above, no crystal there that is still alive), so what you
	 * see matches what the server has. A crystal we just hit counts as gone, since the server
	 * processes the hit before the place.
	 */
	public static boolean canPlaceAt(Minecraft client, BlockPos pos) {
		if (client.level == null) {
			return false;
		}
		BlockPos above = pos.above();
		if (!client.level.isEmptyBlock(above)) {
			return false;
		}
		for (EndCrystal crystal : client.level.getEntitiesOfClass(EndCrystal.class, new AABB(above))) {
			if (!isBroken(crystal)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void onInitializeClient() {
		LitTwoCrystalConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath("littwocrystal", "main")
		);

		// ";" = on / off, "," = slower, "." = faster (all rebindable in Controls)
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.littwocrystal.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_SEMICOLON, category));
		slowerKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.littwocrystal.slower", InputConstants.Type.KEYSYM, InputConstants.KEY_COMMA, category));
		fasterKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.littwocrystal.faster", InputConstants.Type.KEYSYM, InputConstants.KEY_PERIOD, category));

		ClientTickEvents.END_CLIENT_TICK.register(LitTwoCrystalClient::onTick);
	}

	/**
	 * The optimizer only does anything while an end crystal is the item in use:
	 * in the main hand, or in the offhand when the main hand is empty.
	 */
	public static boolean active(LocalPlayer player) {
		// Vanilla Mode (setting 5) leaves clicking and timing to vanilla; see the vanilla package.
		if (!LitTwoCrystalConfig.enabled || LitTwoCrystalConfig.isVanilla()) {
			return false;
		}
		ItemStack main = player.getMainHandItem();
		if (main.is(Items.END_CRYSTAL)) {
			return true;
		}
		return main.isEmpty() && player.getOffhandItem().is(Items.END_CRYSTAL);
	}

	private static void onTick(Minecraft client) {
		LocalPlayer player = client.player;
		tickCounter++;
		if (client.level != null && !client.isPaused()) {
			KeptCrystals.ticked();
		}
		if (tickCounter % 40 == 0) {
			brokenCrystals.values().removeIf(until -> until < tickCounter);
		}

		if (!welcomed && player != null) {
			welcomed = true;
			player.displayClientMessage(tag().append(divider())
				.append(Component.literal("Loaded").withStyle(ChatFormatting.GREEN))
				.append(divider())
				.append(Component.literal("; toggle   , slower   . faster").withStyle(ChatFormatting.GRAY)), false);
		}

		// One press = one flip. Extra queued presses (key mashing, lag spikes) are drained so a
		// single tap can never flip it twice and look like it did nothing.
		boolean toggled = false;
		while (toggleKey.consumeClick()) {
			toggled = true;
		}
		if (toggled) {
			LitTwoCrystalConfig.enabled = !LitTwoCrystalConfig.enabled;
			placedThisTick = false;
			LitTwoCrystalConfig.save();
			announce(client);
		}
		while (slowerKey.consumeClick()) {
			LitTwoCrystalConfig.delay = LitTwoCrystalConfig.clamp(LitTwoCrystalConfig.delay + 1);
			LitTwoCrystalConfig.save();
			announce(client);
		}
		while (fasterKey.consumeClick()) {
			LitTwoCrystalConfig.delay = LitTwoCrystalConfig.clamp(LitTwoCrystalConfig.delay - 1);
			LitTwoCrystalConfig.save();
			announce(client);
		}

		if (!VanillaMode.enabled()) {
			KeptCrystals.clear();
		}

		// extraBreak / extraPlace no longer run here: this is after the tick's movement packet.
		// They run from MinecraftMixin at the tail of handleKeybinds (see runExtras).
		placedThisTick = false;
		attackedThisTick = false;
	}

	/** Called from MinecraftMixin at the tail of handleKeybinds, so packets leave in vanilla's slot. */
	public static void runExtras(Minecraft client) {
		LocalPlayer player = client.player;
		extraBreak(client, player);
		extraPlace(client, player);
	}

	/** How many ticks a left click is remembered while waiting for a crystal to appear. */
	private static final int BREAK_BUFFER_TICKS = 4;
	private static int breakBuffer = 0;

	/**
	 * Breaking: one hit per click, never more, so low CPS works as well as fast CPS.
	 *  - Safety net: if vanilla did not hit a crystal you are aiming at, hit it.
	 *  - Click buffer: a click made a moment before the crystal exists (just placed, server has not
	 *    told your client yet) is kept for a few ticks and fires the instant the crystal is there.
	 * Every hit is a normal attack (same packet as a vanilla click), so the server registers it.
	 */
	private static void extraBreak(Minecraft client, LocalPlayer player) {
		boolean down = client.options.keyAttack.isDown();
		boolean fresh = down && !attackWasDown;
		attackWasDown = down;

		if (player == null || client.gameMode == null || client.level == null || client.screen != null
			|| !active(player)) {
			breakBuffer = 0;
			return;
		}
		if (attackedThisTick) {
			breakBuffer = 0;
			return;
		}
		if (fresh) {
			breakBuffer = BREAK_BUFFER_TICKS;
		} else if (breakBuffer > 0) {
			breakBuffer--;
		} else {
			return;
		}
		EndCrystal target = crystalInReach(client, player);
		if (target != null && !isBroken(target)) {
			client.gameMode.attack(player, target);
			player.swing(InteractionHand.MAIN_HAND);
			markBroken(target);
			breakBuffer = 0;
		}
	}

	/** Nearest crystal under the crosshair, within reach and not behind a block; otherwise null. */
	private static EndCrystal crystalInReach(Minecraft client, LocalPlayer player) {
		double reach = player.entityInteractionRange();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(reach));
		EndCrystal best = null;
		double bestDist = Double.MAX_VALUE;
		for (EndCrystal crystal : client.level.getEntitiesOfClass(EndCrystal.class,
			player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0))) {
			Optional<Vec3> point = crystal.getBoundingBox().clip(eye, end);
			if (point.isPresent()) {
				double d = eye.distanceToSqr(point.get());
				if (d < bestDist) {
					bestDist = d;
					best = crystal;
				}
			}
		}
		if (best == null) {
			return null;
		}
		HitResult wall = client.level.clip(new ClipContext(eye, end,
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() == HitResult.Type.BLOCK && eye.distanceToSqr(wall.getLocation()) < bestDist) {
			return null;
		}
		return best;
	}

	/**
	 * Second placement chance each tick. Vanilla places at the start of the tick; if it did not
	 * (for example the crystal under the cursor had only just been broken), place again now
	 * instead of waiting a whole tick. Uses the normal useItemOn path, so the server gets the
	 * regular use-item packet and sequence number and registers it like any other placement.
	 * The ray ignores entities, so a crystal standing on the block does not block the aim.
	 */
	private static void extraPlace(Minecraft client, LocalPlayer player) {
		if (placedThisTick || LitTwoCrystalConfig.delay != 0) {
			return;
		}
		if (player == null || client.gameMode == null || client.level == null || client.screen != null) {
			return;
		}
		if (!client.options.keyUse.isDown() || player.isUsingItem() || !active(player)) {
			return;
		}
		HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos pos = blockHit.getBlockPos();
		if (!client.level.getBlockState(pos).is(Blocks.OBSIDIAN)
			&& !client.level.getBlockState(pos).is(Blocks.BEDROCK)) {
			return;
		}
		if (!canPlaceAt(client, pos)) {
			return;
		}
		InteractionHand hand = player.getMainHandItem().is(Items.END_CRYSTAL)
			? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		InteractionResult result = client.gameMode.useItemOn(player, hand, blockHit);
		if (result.consumesAction()) {
			player.swing(hand);
		}
	}

	/** "◆ LitTwo Crystal" tag shared by every message. */
	private static MutableComponent tag() {
		return Component.literal("\u25C6 ").withStyle(ChatFormatting.AQUA)
			.append(Component.literal("LitTwo Crystal").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
	}

	private static MutableComponent divider() {
		return Component.literal("  \u2502  ").withStyle(ChatFormatting.DARK_GRAY);
	}

	private static MutableComponent statusPart() {
		boolean on = LitTwoCrystalConfig.enabled;
		return Component.literal(on ? "\u25CF ON" : "\u25CB OFF")
			.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD);
	}

	private static MutableComponent delayPart() {
		int d = LitTwoCrystalConfig.delay;
		if (LitTwoCrystalConfig.isVanilla()) {
			return Component.literal("Speed ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("Vanilla Mode").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		}
		String speed = d == 0 ? "MAX" : d + (d == 1 ? " tick" : " ticks");
		return Component.literal("Speed ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(speed).withStyle(d == 0 ? ChatFormatting.GOLD : ChatFormatting.YELLOW,
				ChatFormatting.BOLD));
	}

	private static void announce(Minecraft client) {
		if (client.player == null) {
			return;
		}
		MutableComponent message = tag().append(divider()).append(statusPart())
			.append(divider()).append(delayPart());
		client.player.displayClientMessage(message, false);
		client.player.displayClientMessage(message, true);
	}
}
