package com.littwocrystal;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Tiny settings holder, saved to config/littwocrystal.properties. */
public final class LitTwoCrystalConfig {

	/** Slowest setting. 0 = every tick (fastest). */
	public static final int MAX_DELAY = 10;

	/**
	 * Bumped when the default speed changes. Configs saved by an older version are reset to the
	 * new default once, so an old slow value (the previous default was 2) is not kept forever.
	 */
	private static final int CONFIG_VERSION = 2;

	public static boolean enabled = true;
	/** Ticks to wait between placing crystals. 0 = every tick (20 per second), the fastest possible. */
	public static int delay = 0;

	private LitTwoCrystalConfig() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("littwocrystal.properties");
	}

	public static void load() {
		Path path = file();
		if (!Files.exists(path)) {
			save();
			return;
		}
		boolean upgraded = false;
		try (Reader reader = Files.newBufferedReader(path)) {
			Properties props = new Properties();
			props.load(reader);
			enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
			int version = Integer.parseInt(props.getProperty("version", "1").trim());
			if (version < CONFIG_VERSION) {
				delay = 0;
				upgraded = true;
			} else {
				delay = clamp(Integer.parseInt(props.getProperty("delay", "0").trim()));
			}
		} catch (IOException | NumberFormatException e) {
			enabled = true;
			delay = 0;
			upgraded = true;
		}
		if (upgraded) {
			save();
		}
	}

	public static void save() {
		Properties props = new Properties();
		props.setProperty("version", Integer.toString(CONFIG_VERSION));
		props.setProperty("enabled", Boolean.toString(enabled));
		props.setProperty("delay", Integer.toString(delay));
		try (Writer writer = Files.newBufferedWriter(file())) {
			props.store(writer, "LitTwo Crystal. delay: 0 (fastest) to " + MAX_DELAY + " ticks between placed crystals");
		} catch (IOException ignored) {
		}
	}

	public static int clamp(int value) {
		return Math.max(0, Math.min(MAX_DELAY, value));
	}
}
