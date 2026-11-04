package com.littwocrystal;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
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

	/**
	 * Whether a crystal place on this obsidian/bedrock block can succeed. The mod only places when
	 * your client sees the spot free (air above, no crystal there), so what you see always matches
	 * what the server has. It never places "ahead" of the server, which is what causes ghost
	 * crystals you cannot explode.
	 */
	public static boolean canPlaceAt(Minecraft client, BlockPos pos) {
		if (client.level == null) {
			return false;
		}
		BlockPos above = pos.above();
		return client.level.isEmptyBlock(above)
			&& client.level.getEntitiesOfClass(EndCrystal.class, new AABB(above)).isEmpty();
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
		if (!LitTwoCrystalConfig.enabled) {
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

		if (!welcomed && player != null) {
			welcomed = true;
			player.displayClientMessage(Component.literal(
				"[LitTwo Crystal] loaded.  ; = on/off   , = slower   . = faster"), false);
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

		extraPlace(client, player);
		placedThisTick = false;
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

	private static void announce(Minecraft client) {
		if (client.player == null) {
			return;
		}
		String text = "[LitTwo Crystal] " + (LitTwoCrystalConfig.enabled ? "ON (works while holding a crystal)" : "OFF (vanilla)")
			+ "  |  place delay " + LitTwoCrystalConfig.delay
			+ " tick" + (LitTwoCrystalConfig.delay == 1 ? "" : "s")
			+ " (0 = fastest, " + LitTwoCrystalConfig.MAX_DELAY + " = slowest)";
		client.player.displayClientMessage(Component.literal(text), false);
		client.player.displayClientMessage(Component.literal(text), true);
	}
}
