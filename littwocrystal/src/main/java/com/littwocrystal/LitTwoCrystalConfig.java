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

	public static boolean enabled = true;
	/** Ticks to wait between placing / breaking crystals. 20 ticks = 1 second. */
	public static int delay = 2;

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
		try (Reader reader = Files.newBufferedReader(path)) {
			Properties props = new Properties();
			props.load(reader);
			enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
			delay = clamp(Integer.parseInt(props.getProperty("delay", "2").trim()));
		} catch (IOException | NumberFormatException e) {
			enabled = true;
			delay = 2;
		}
	}

	public static void save() {
		Properties props = new Properties();
		props.setProperty("enabled", Boolean.toString(enabled));
		props.setProperty("delay", Integer.toString(delay));
		try (Writer writer = Files.newBufferedWriter(file())) {
			props.store(writer, "LitTwo Crystal. delay: 0 (fastest) to " + MAX_DELAY + " ticks between actions");
		} catch (IOException ignored) {
		}
	}

	public static int clamp(int value) {
		return Math.max(0, Math.min(MAX_DELAY, value));
	}
}
