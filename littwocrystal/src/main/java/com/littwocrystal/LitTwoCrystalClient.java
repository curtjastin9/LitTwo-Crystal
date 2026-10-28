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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class LitTwoCrystalClient implements ClientModInitializer {

	private static KeyMapping toggleKey;
	private static KeyMapping slowerKey;
	private static KeyMapping fasterKey;

	private static boolean welcomed = false;
	/** Ticks left before the next crystal may be broken. */
	private static int breakCooldown = 0;

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

		while (toggleKey.consumeClick()) {
			LitTwoCrystalConfig.enabled = !LitTwoCrystalConfig.enabled;
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

		// Fast breaking: hold left click while holding a crystal and looking at a crystal.
		if (breakCooldown > 0) {
			breakCooldown--;
		}
		if (player == null || client.gameMode == null || client.level == null || client.screen != null) {
			return;
		}
		if (breakCooldown > 0 || !active(player) || !client.options.keyAttack.isDown()) {
			return;
		}
		HitResult hit = client.hitResult;
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof EndCrystal crystal) {
			client.gameMode.attack(player, crystal);
			player.swing(InteractionHand.MAIN_HAND);
			breakCooldown = LitTwoCrystalConfig.delay;
		}
	}

	private static void announce(Minecraft client) {
		if (client.player == null) {
			return;
		}
		String text = "[LitTwo Crystal] " + (LitTwoCrystalConfig.enabled ? "ON" : "OFF")
			+ "  |  delay " + LitTwoCrystalConfig.delay
			+ " tick" + (LitTwoCrystalConfig.delay == 1 ? "" : "s")
			+ " (0 = fastest, " + LitTwoCrystalConfig.MAX_DELAY + " = slowest)";
		client.player.displayClientMessage(Component.literal(text), false);
		client.player.displayClientMessage(Component.literal(text), true);
	}
}
